// 负责把 vanilla 人形角度（MelodiesPose）转换为 MMD 骨骼姿势覆盖并写入 native。
// 覆盖走 vpd 管线：动画评估后、物理前应用（头发裙摆物理不受影响）。
package com.shiroha.mmdskin.compat.melodies;

import com.shiroha.mmdskin.bridge.NativePortAdapters;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 角度约定（P1 初版，方向对齐法）：
 * - 头：pitch/yaw 欧拉直映（MMD 标准骨骼局部轴 ≈ 模型轴）。
 * - 臂：以"vanilla 臂静息方向 (0,-1,0) 经 pitch/yaw/roll 旋转后的目标方向"
 *   与"MMD T-pose 臂静息方向（左 +X / 右 -X）"做最短弧对齐。
 *
 * vanilla 模型空间与 MMD 模型空间存在镜像/朝向差异，残余符号问题用调参开关校准：
 * -Dmmdskin.melodies.signs=&lt;0-15&gt;（bit0 flipX, bit1 flipZ, bit2 headPitch 反号, bit3 headYaw 反号）
 * -Dmmdskin.melodies.debug=true 打印每帧角度与目标方向。
 */
public final class MmdArmPoseMapper {
    private static final boolean DEBUG = Boolean.getBoolean("mmdskin.melodies.debug");
    // 默认 0b1001：X 镜像（vanilla 与 MMD 模型左右镜像）+ 头 pitch 反号（vanilla 低头为正）。
    // 校准：-Dmmdskin.melodies.signs=<0-15>（bit0 flipX, bit1 flipZ, bit2 headPitch, bit3 headYaw）
    private static final int SIGNS = Integer.getInteger("mmdskin.melodies.signs", 0b1001);

    private static final Vector3f LEFT_ARM_REST = new Vector3f(1.0F, 0.0F, 0.0F);
    private static final Vector3f RIGHT_ARM_REST = new Vector3f(-1.0F, 0.0F, 0.0F);

    // 标准 MMD 骨骼名 + 常见英文别名（与手骨矩阵 JNI 的回退列表同模式）
    private static final String[] HEAD_BONES = {"頭", "头", "head", "Head"};
    private static final String[] LEFT_ARM_BONES = {"左腕", "腕L", "arm_L", "arm_l", "LeftArm"};
    private static final String[] RIGHT_ARM_BONES = {"右腕", "腕R", "arm_R", "arm_r", "RightArm"};

    private static volatile boolean debugLogged;

    private MmdArmPoseMapper() {
    }

    public static void apply(long modelHandle, MelodiesPose pose) {
        if (pose == null) {
            NativePortAdapters.poseOverride().clearBoneOverrides(modelHandle);
            return;
        }

        float headPitch = pose.headPitch() * (bit(2) ? -1.0F : 1.0F);
        float headYaw = pose.headYaw() * (bit(3) ? -1.0F : 1.0F);
        Quaternionf headRotation = new Quaternionf()
                .rotationY(headYaw)
                .mul(new Quaternionf().rotationX(headPitch));
        setFirstBone(modelHandle, HEAD_BONES, headRotation);

        applyArm(modelHandle, LEFT_ARM_BONES, LEFT_ARM_REST,
                pose.leftArmPitch(), pose.leftArmYaw(), pose.leftArmRoll());
        applyArm(modelHandle, RIGHT_ARM_BONES, RIGHT_ARM_REST,
                pose.rightArmPitch(), pose.rightArmYaw(), pose.rightArmRoll());

        if (DEBUG && !debugLogged) {
            debugLogged = true;
            System.out.printf("[MMD melodies] pose head=(%.3f,%.3f) left=(%.3f,%.3f,%.3f) right=(%.3f,%.3f,%.3f) signs=%d%n",
                    pose.headPitch(), pose.headYaw(),
                    pose.leftArmPitch(), pose.leftArmYaw(), pose.leftArmRoll(),
                    pose.rightArmPitch(), pose.rightArmYaw(), pose.rightArmRoll(), SIGNS);
        }
    }

    public static void clear(long modelHandle) {
        NativePortAdapters.poseOverride().clearBoneOverrides(modelHandle);
    }

    private static void applyArm(long modelHandle, String[] boneNames, Vector3f restDirection,
                                 float pitch, float yaw, float roll) {
        // vanilla ModelPart 旋转次序（translateAndRotate → rotateZYX）：v' = Rz·Ry·Rx·v
        Vector3f target = new Vector3f(0.0F, -1.0F, 0.0F)
                .rotateX(pitch)
                .rotateY(yaw)
                .rotateZ(roll);
        // vanilla → MMD 模型空间镜像
        if (bit(0)) {
            target.x = -target.x;
        }
        if (bit(1)) {
            target.z = -target.z;
        }
        if (target.lengthSquared() < 1.0E-6F) {
            return;
        }
        Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(restDirection), target);
        setFirstBone(modelHandle, boneNames, rotation);
        if (DEBUG && !debugLogged) {
            System.out.printf("[MMD melodies] arm %s -> target=(%.3f,%.3f,%.3f)%n",
                    boneNames[0], target.x, target.y, target.z);
        }
    }

    private static void setFirstBone(long modelHandle, String[] boneNames, Quaternionf rotation) {
        for (String boneName : boneNames) {
            if (NativePortAdapters.poseOverride().setBoneOverride(modelHandle, boneName,
                    0.0F, 0.0F, 0.0F,
                    rotation.x, rotation.y, rotation.z, rotation.w)) {
                return;
            }
        }
    }

    private static boolean bit(int index) {
        return (SIGNS & (1 << index)) != 0;
    }
}
