// 负责把 vanilla 人形角度（MelodiesPose）换算为 MMD 骨骼旋转并写入 native。
//
// 坐标推导（实测数据，非假设）：
// - vanilla 模型空间：面朝 -Z、+Y 向下、角色左臂在 +X
//   （HumanoidModel.createMesh：right_arm 偏移 x=-5、left_arm x=+5；手臂立方体向 +Y 下垂）
// - MMD 引擎空间：面朝 +Z、+Y 向上、角色左臂在 +X
//   （骨骼实测：脚趾在脚踝 +Z 侧、眼睛在頭基准 +Z 侧；左腕 x>0、右腕 x<0）
//   PMX 加载时 Z 取反（engine = (x, y, -z_pmx)），左右轴不变。
// 两空间仅差绕 X 轴 180°（M = diag(1,-1,-1)）：x 同号、y/z 反号。
//
// 故 vanilla 部件旋转 R（组合次序 Rz·Ry·Rx，与 ModelPart.translateAndRotate 一致）
// 在引擎空间即 M·R·M —— 四元数表示下等价于 y、z 分量取反、x/w 不变。
//
// 手臂还需补正静息朝向：MMD 腕骨骼静息多为 A-pose（实测左腕→左ひじ =
// (0.74, -0.67, -0.02)，约 42° 斜下），而 vanilla 手臂静息为垂直向下。
// 做法是在引擎空间内先做最短弧对齐（静息方向 → 正下方），再叠加换算后的
// vanilla 旋转：q = R_engine · S。T-pose 与 A-pose 由同一公式统一处理。
//
// 调试：-Dmmdskin.melodies.debug=true 输出静息方向、目标方向与原始角度。
package com.shiroha.mmdskin.compat.melodies;

import com.shiroha.mmdskin.bridge.NativePortAdapters;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class MmdArmPoseMapper {
    private static final boolean DEBUG = Boolean.getBoolean("mmdskin.melodies.debug");

    /** 引擎空间中 vanilla 手臂的静息朝向：自然下垂。 */
    private static final Vector3f ARM_REST_ENGINE = new Vector3f(0.0F, -1.0F, 0.0F);

    /** 静息方向查询失败时的兜底（常见 A-pose 形状），仅影响缺骨骼的极端模型。 */
    private static final Vector3f FALLBACK_LEFT_ARM_REST = new Vector3f(0.74F, -0.67F, -0.02F);
    private static final Vector3f FALLBACK_RIGHT_ARM_REST = new Vector3f(-0.74F, -0.67F, -0.02F);

    // 标准 MMD 骨骼名 + 常见英文别名（与手骨矩阵 JNI 的回退列表同模式）
    private static final String[] HEAD_BONES = {"頭", "头", "head", "Head"};
    private static final String[] LEFT_UPPER_ARM_BONES = {"左腕", "腕L", "arm_L", "LeftArm"};
    private static final String[] RIGHT_UPPER_ARM_BONES = {"右腕", "腕R", "arm_R", "RightArm"};
    private static final String[] LEFT_ARM_REST_TARGETS = {"左ひじ", "左手首", "elbow_L", "LeftElbow"};
    private static final String[] RIGHT_ARM_REST_TARGETS = {"右ひじ", "右手首", "elbow_R", "RightElbow"};

    // 手臂内段关节（肘/腕捩/手捩/手首）：
    // vanilla 人形的手臂是单段刚体（HumanoidModel 的 leftArm/rightArm 一个部件直接挂到手上），
    // 乐器 Animator 的角度也只描述"整条手臂指向哪里"。MMD 手臂则由 腕→腕捩→ひじ→手捩→手首
    // 多段构成，若不把这些内段关节一并归零，VMD 自带的肘部弯曲会在这个新朝向下把前臂折向
    // 胸口（实测 idle 动画的 ひじ 有约 25° 弯曲）。归零后手臂与 vanilla 一样是直的，
    // 才能忠实还原 Animator 的目标姿势。
    private static final String[] LEFT_INNER_ARM_BONES = {
            "左腕捩", "左ひじ", "左手捩", "左手首",
            "leftLowerArm", "LeftLowerArm", "leftHand", "LeftHand"};
    private static final String[] RIGHT_INNER_ARM_BONES = {
            "右腕捩", "右ひじ", "右手捩", "右手首",
            "rightLowerArm", "RightLowerArm", "rightHand", "RightHand"};
    /** 颈部：vanilla 头部同样是单关节（head 直接挂到 body），MMD 的首/頭 两段需合一。 */
    private static final String[] NECK_BONES = {"首", "neck", "Neck"};

    private static volatile long debugLoggedHandle = Long.MIN_VALUE;
    private static volatile String lastReport = "未触发";
    private static volatile String lastTargets = "-";
    private static volatile long poseHandle = Long.MIN_VALUE;
    private static volatile Quaternionf leftItemCorrection;
    private static volatile Quaternionf rightItemCorrection;

    private MmdArmPoseMapper() {
    }

    public static String lastReport() {
        return lastReport;
    }

    public static String lastTargets() {
        return lastTargets;
    }

    /**
     * 物品挂点的朝向修正（左手/右手），需要在该手骨变换之后、物品朝向之前乘上。
     *
     * 原因：骨骼覆盖 = vanilla 手臂角度 × 静息姿态对齐（把 A-pose 掰到垂臂），
     * 于是手骨帧比 vanilla 的手臂帧多出一个对齐扭转（本模型实测约 48°）。
     * 物品若直接挂在这个手骨上会跟着歪掉，故用对齐的逆把它抵消掉，
     * 使物品朝向与原版一致（原版物品只受"手臂角度 + POST"影响）。
     *
     * @return 该模型当前没有演奏姿势时返回 null
     */
    public static Quaternionf itemOrientationCorrection(long modelHandle, boolean left) {
        if (poseHandle != modelHandle) {
            return null;
        }
        return left ? leftItemCorrection : rightItemCorrection;
    }

    public static void apply(long modelHandle, MelodiesPose pose) {
        if (pose == null) {
            clear(modelHandle);
            return;
        }

        // 头：MMD 頭的静息面朝向在引擎空间即 +Z（与 vanilla 面部同向），无需静息补正。
        boolean head = setFirstBone(modelHandle, HEAD_BONES,
                toEngineSpace(vanillaRotation(pose.headPitch(), pose.headYaw(), 0.0F)));
        setFirstBone(modelHandle, NECK_BONES, new Quaternionf());

        ArmPose leftArm = applyArm(modelHandle, LEFT_UPPER_ARM_BONES, LEFT_ARM_REST_TARGETS,
                FALLBACK_LEFT_ARM_REST, LEFT_INNER_ARM_BONES,
                toEngineSpace(vanillaRotation(pose.leftArmPitch(), pose.leftArmYaw(), pose.leftArmRoll())));
        ArmPose rightArm = applyArm(modelHandle, RIGHT_UPPER_ARM_BONES, RIGHT_ARM_REST_TARGETS,
                FALLBACK_RIGHT_ARM_REST, RIGHT_INNER_ARM_BONES,
                toEngineSpace(vanillaRotation(pose.rightArmPitch(), pose.rightArmYaw(), pose.rightArmRoll())));

        poseHandle = modelHandle;
        leftItemCorrection = leftArm.itemCorrection();
        rightItemCorrection = rightArm.itemCorrection();
        lastReport = pose.instrument() + " 頭" + mark(head) + " 左" + mark(leftArm.applied())
                + " 右" + mark(rightArm.applied());
        lastTargets = "左" + describe(leftArm.engineRotation()) + " 右" + describe(rightArm.engineRotation());
        if (DEBUG) {
            logOnce(modelHandle, pose);
        }
    }

    public static void clear(long modelHandle) {
        lastReport = "未演奏";
        lastTargets = "-";
        poseHandle = Long.MIN_VALUE;
        leftItemCorrection = null;
        rightItemCorrection = null;
        NativePortAdapters.poseOverride().clearBoneOverrides(modelHandle);
    }

    /** 该旋转把"手臂自然下垂"指向哪个引擎空间方向（+Z 面前 / +X 角色左 / +Y 上）。 */
    private static String describe(Quaternionf engineRotation) {
        Vector3f direction = engineRotation.transform(new Vector3f(ARM_REST_ENGINE));
        return String.format("(%+.2f,%+.2f,%+.2f)", direction.x, direction.y, direction.z);
    }

    private static String mark(boolean applied) {
        return applied ? "✓" : "✗";
    }

    /** 单侧手臂的施加结果。 */
    private record ArmPose(boolean applied, Quaternionf engineRotation, Quaternionf itemCorrection) {
    }

    private static ArmPose applyArm(long modelHandle, String[] boneNames, String[] restTargets,
                                    Vector3f fallbackRest, String[] innerBones,
                                    Quaternionf engineRotation) {
        Vector3f rest = queryRestDirection(modelHandle, boneNames, restTargets);
        if (!isUsable(rest)) {
            rest = fallbackRest;
        }
        // 骨骼覆盖 = 静息姿态对齐（把 A-pose 掰到 vanilla 的垂臂）× vanilla 手臂角度
        Quaternionf restFix = new Quaternionf().rotationTo(rest, ARM_REST_ENGINE);
        Quaternionf rotation = new Quaternionf(engineRotation).mul(restFix);
        boolean applied = setFirstBone(modelHandle, boneNames, rotation);
        // 内段关节归零：让 MMD 手臂与 vanilla 一样保持单段伸直
        for (String innerBone : innerBones) {
            setFirstBone(modelHandle, new String[]{innerBone}, new Quaternionf());
        }
        if (DEBUG) {
            Vector3f target = rotation.transform(new Vector3f(rest));
            System.out.printf("[MMD melodies] arm %s rest=(%.3f,%.3f,%.3f) -> target=(%.3f,%.3f,%.3f)%n",
                    boneNames[0], rest.x, rest.y, rest.z, target.x, target.y, target.z);
        }
        // 物品需要抵消 restFix（否则乐器跟着 A-pose 对齐扭转歪掉）
        return new ArmPose(applied, engineRotation, restFix.conjugate(new Quaternionf()));
    }

    /** 与 ModelPart.translateAndRotate 相同的组合次序：Rz(z)·Ry(y)·Rx(x)。 */
    private static Quaternionf vanillaRotation(float pitch, float yaw, float roll) {
        return new Quaternionf().rotationZYX(roll, yaw, pitch);
    }

    /** vanilla 模型空间 → MMD 引擎空间：绕 X 轴 180°，四元数即 y/z 分量取反。 */
    private static Quaternionf toEngineSpace(Quaternionf vanilla) {
        return new Quaternionf(vanilla.x, -vanilla.y, -vanilla.z, vanilla.w);
    }

    private static Vector3f queryRestDirection(long modelHandle, String[] boneNames, String[] restTargets) {
        for (String boneName : boneNames) {
            for (String targetName : restTargets) {
                Vector3f direction = NativePortAdapters.poseOverride()
                        .boneRestDirection(modelHandle, boneName, targetName);
                if (isUsable(direction)) {
                    return direction;
                }
            }
        }
        return null;
    }

    private static boolean isUsable(Vector3f vector) {
        return vector != null && vector.lengthSquared() > 1.0E-6F
                && Float.isFinite(vector.x) && Float.isFinite(vector.y) && Float.isFinite(vector.z);
    }

    private static boolean setFirstBone(long modelHandle, String[] boneNames, Quaternionf rotation) {
        for (String boneName : boneNames) {
            if (NativePortAdapters.poseOverride().setBoneOverride(modelHandle, boneName,
                    0.0F, 0.0F, 0.0F,
                    rotation.x, rotation.y, rotation.z, rotation.w)) {
                return true;
            }
        }
        return false;
    }

    private static void logOnce(long modelHandle, MelodiesPose pose) {
        if (debugLoggedHandle == modelHandle) {
            return;
        }
        debugLoggedHandle = modelHandle;
        System.out.printf("[MMD melodies] pose head=(pitch=%.3f,yaw=%.3f) left=(%.3f,%.3f,%.3f) right=(%.3f,%.3f,%.3f)%n",
                pose.headPitch(), pose.headYaw(),
                pose.leftArmPitch(), pose.leftArmYaw(), pose.leftArmRoll(),
                pose.rightArmPitch(), pose.rightArmYaw(), pose.rightArmRoll());
    }
}
