package com.shiroha.mmdskin.compat.tacz;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Quaternionf;

/** 无 TaCZ 编译期依赖的第三人称姿态采样器。 */
public final class TaczThirdPersonPoseSampler {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final ThreadLocal<ArmParts> SCRATCH_PARTS = ThreadLocal.withInitial(ArmParts::createParts);
    private static final Set<String> REPORTED_STATES = ConcurrentHashMap.newKeySet();
    private static volatile Bindings bindings;
    private static volatile boolean bindingAttempted;

    private TaczThirdPersonPoseSampler() {}

    public static Optional<TaczThirdPersonArmPose> sample(LivingEntity entity, float tickDelta) {
        if (entity == null || !TaczGunDetector.isGun(entity.getMainHandItem())
                || entity.getPose() == Pose.SLEEPING || entity.onClimbable()
                || entity.isSwimming() || entity.getPose() == Pose.FALL_FLYING) {
            return Optional.empty();
        }
        Bindings active = bindings();
        if (active == null) return Optional.empty();
        try {
            ArmParts current = SCRATCH_PARTS.get();
            current.reset(entity, tickDelta);
            Object display = active.getDisplay(entity.getMainHandItem());
            if (display == null) {
                reportOnce("missing-display", "TaCZ 第三人称上臂修正已回退 VMD：当前枪械没有 GunDisplay 数据");
                return Optional.empty();
            }

            boolean playerAnimator = active.hasPlayerAnimatorThirdPerson(entity, display);
            if (playerAnimator) {
                // PlayerAnimator 路径不会写入临时 ModelPart；单独重建同枪械的原版腰射/ADS 上臂姿态。
                if (!active.animatePlayerAnimatorFallback(entity, display, current)) {
                    reportOnce("player-animator-fallback-missing",
                            "TaCZ 第三人称上臂修正已回退 VMD：枪包使用 PlayerAnimator，但当前 TaCZ 版本缺少原版姿态旁路接口");
                    return Optional.empty();
                }
                reportOnce("player-animator-fallback",
                        "TaCZ 第三人称双臂 IK 已启用 PlayerAnimator 枪包姿态旁路");
            } else {
                // TaCZ 会在此入口内部按同步状态选择腰射或 ADS。
                active.animateCurrent(entity, current);
                reportOnce("vanilla-path", "TaCZ 第三人称双臂 IK 已启用原版 ModelPart 绝对姿态采样");
            }

            // 单位旋转也是合法的绝对姿态，不能再按“相对腰射增量”过滤。
            Quaternionf left = current.leftQuaternion();
            Quaternionf right = current.rightQuaternion();
            TaczThirdPersonArmPose pose = new TaczThirdPersonArmPose(left, right,
                    (left != null ? 0b01 : 0) | (right != null ? 0b10 : 0));
            reportPoseOnce(playerAnimator ? "player-animator-absolute" : "vanilla-absolute",
                    "TaCZ 第三人称绝对上臂姿态已提交双骨 IK", left, right);
            return pose.isValid() ? Optional.of(pose) : Optional.empty();
        } catch (ReflectiveOperationException | RuntimeException error) {
            // 枪包或 TaCZ 版本接口变化时只回退 VMD，且必须留下可见的一次性原因。
            if (REPORTED_STATES.add("sample-failure")) {
                LOGGER.warn("TaCZ 第三人称姿态采样失败，已回退 VMD", error);
            }
            return Optional.empty();
        }
    }

    private static void reportOnce(String key, String message) {
        if (REPORTED_STATES.add(key)) LOGGER.info(message);
    }

    private static void reportPoseOnce(String key, String message, Quaternionf left, Quaternionf right) {
        if (!REPORTED_STATES.add(key)) return;
        LOGGER.info("{}：left={}°, right={}°", message,
                String.format("%.2f", TaczThirdPersonArmPose.angleDegrees(left)),
                String.format("%.2f", TaczThirdPersonArmPose.angleDegrees(right)));
    }

    private static Bindings bindings() {
        if (bindingAttempted) return bindings;
        synchronized (TaczThirdPersonPoseSampler.class) {
            if (bindingAttempted) return bindings;
            bindingAttempted = true;
            try {
                bindings = Bindings.load();
            } catch (ReflectiveOperationException | LinkageError error) {
                LOGGER.info("TaCZ 第三人称程序化手臂不可用，继续使用 VMD: {}", error.toString());
            }
            return bindings;
        }
    }

    private record ArmParts(ModelPart right, ModelPart left, ModelPart body, ModelPart head) {
        void reset(LivingEntity entity, float tickDelta) {
            resetPart(right);
            resetPart(left);
            resetPart(body);
            resetPart(head);
            right.x = -5.0f;
            left.x = 5.0f;
            float bodyYaw = Mth.rotLerp(tickDelta, entity.yBodyRotO, entity.yBodyRot);
            float headYaw = Mth.rotLerp(tickDelta, entity.yHeadRotO, entity.yHeadRot);
            head.xRot = entity.getXRot() * Mth.DEG_TO_RAD;
            head.yRot = (headYaw - bodyYaw) * Mth.DEG_TO_RAD;
        }

        private static ArmParts createParts() {
            ModelPart right = emptyPart();
            ModelPart left = emptyPart();
            ModelPart body = emptyPart();
            ModelPart head = emptyPart();
            right.x = -5.0f;
            left.x = 5.0f;
            return new ArmParts(right, left, body, head);
        }

        private static ModelPart emptyPart() {
            return new ModelPart(List.of(), Map.of());
        }

        private static void resetPart(ModelPart part) {
            part.x = 0.0f;
            part.y = 0.0f;
            part.z = 0.0f;
            part.xRot = 0.0f;
            part.yRot = 0.0f;
            part.zRot = 0.0f;
            part.xScale = 1.0f;
            part.yScale = 1.0f;
            part.zScale = 1.0f;
            part.visible = true;
            part.skipDraw = false;
        }

        Quaternionf leftQuaternion() {
            return new Quaternionf().rotationZYX(left.zRot, left.yRot, left.xRot);
        }

        Quaternionf rightQuaternion() {
            return new Quaternionf().rotationZYX(right.zRot, right.yRot, right.xRot);
        }
    }

    private record Bindings(Method getGunDisplayMethod, Method animateCurrentMethod,
                            Method hasPlayerAnimatorThirdPersonMethod,
                            PlayerAnimatorFallback playerAnimatorFallback) {
        static Bindings load() throws ReflectiveOperationException {
            ClassLoader loader = TaczThirdPersonPoseSampler.class.getClassLoader();
            Class<?> timelessApi = Class.forName("com.tacz.guns.api.TimelessAPI", false, loader);
            Class<?> displayType = Class.forName("com.tacz.guns.client.resource.GunDisplayInstance", false, loader);
            Class<?> innerManager = Class.forName("com.tacz.guns.client.animation.third.InnerThirdPersonManager", false, loader);
            return new Bindings(
                    timelessApi.getMethod("getGunDisplay", ItemStack.class),
                    innerManager.getMethod("setRotationAnglesHead", LivingEntity.class, ModelPart.class,
                            ModelPart.class, ModelPart.class, ModelPart.class, float.class),
                    optionalPlayerAnimatorMethod(loader, displayType),
                    PlayerAnimatorFallback.load(loader, displayType));
        }

        private static Method optionalPlayerAnimatorMethod(ClassLoader loader, Class<?> displayType) {
            try {
                Class<?> compat = Class.forName("com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat",
                        false, loader);
                return compat.getMethod("hasPlayerAnimator3rd", LivingEntity.class, displayType);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // 部分 TaCZ 版本可能不带此附加兼容类；核心原版手臂采样仍可继续。
                return null;
            }
        }

        Object getDisplay(ItemStack stack) throws ReflectiveOperationException {
            Object result = getGunDisplayMethod.invoke(null, stack);
            return result instanceof Optional<?> optional ? optional.orElse(null) : null;
        }

        boolean hasPlayerAnimatorThirdPerson(LivingEntity entity, Object display)
                throws ReflectiveOperationException {
            if (hasPlayerAnimatorThirdPersonMethod == null) return false;
            return Boolean.TRUE.equals(hasPlayerAnimatorThirdPersonMethod.invoke(null, entity, display));
        }

        boolean animatePlayerAnimatorFallback(LivingEntity entity, Object display, ArmParts parts)
                throws ReflectiveOperationException {
            return playerAnimatorFallback != null && playerAnimatorFallback.animate(entity, display, parts);
        }

        void animateCurrent(LivingEntity entity, ArmParts parts) throws ReflectiveOperationException {
            animateCurrentMethod.invoke(null, entity, parts.right, parts.left, parts.body, parts.head, 0.0f);
        }
    }

    /** 只为 PlayerAnimator 枪包重建 TaCZ 原版双臂几何，不改变其实际动画播放状态。 */
    private record PlayerAnimatorFallback(Method getAnimationMethod, Method getAnimationNameMethod,
                                          Method getOperatorMethod, Method getAimProgressMethod,
                                          Method animateHoldMethod, Method animateAimMethod) {
        static PlayerAnimatorFallback load(ClassLoader loader, Class<?> displayType) {
            try {
                Class<?> manager = Class.forName("com.tacz.guns.api.client.other.ThirdPersonManager", false, loader);
                Class<?> animation = Class.forName("com.tacz.guns.api.client.other.IThirdPersonAnimation", false, loader);
                Class<?> operator = Class.forName("com.tacz.guns.api.entity.IGunOperator", false, loader);
                return new PlayerAnimatorFallback(
                        manager.getMethod("getAnimation", String.class),
                        displayType.getMethod("getThirdPersonAnimation"),
                        operator.getMethod("fromLivingEntity", LivingEntity.class),
                        operator.getMethod("getSynAimingProgress"),
                        animation.getMethod("animateGunHold", LivingEntity.class, ModelPart.class,
                                ModelPart.class, ModelPart.class, ModelPart.class),
                        animation.getMethod("animateGunAim", LivingEntity.class, ModelPart.class,
                                ModelPart.class, ModelPart.class, ModelPart.class, float.class));
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return null;
            }
        }

        boolean animate(LivingEntity entity, Object display, ArmParts parts)
                throws ReflectiveOperationException {
            Object operator = getOperatorMethod.invoke(null, entity);
            Object animation = animation(display);
            if (operator == null || animation == null) return false;

            Object progressValue = getAimProgressMethod.invoke(operator);
            float progress = progressValue instanceof Number number ? number.floatValue() : 0.0f;
            if (!Float.isFinite(progress)) return false;
            progress = Mth.clamp(progress, 0.0f, 1.0f);
            if (progress > 0.0f) {
                animateAimMethod.invoke(animation, entity, parts.right, parts.left, parts.body, parts.head, progress);
            } else {
                animateHoldMethod.invoke(animation, entity, parts.right, parts.left, parts.body, parts.head);
            }
            return true;
        }

        private Object animation(Object display) throws ReflectiveOperationException {
            Object animationName = getAnimationNameMethod.invoke(display);
            return animationName instanceof String name ? getAnimationMethod.invoke(null, name) : null;
        }
    }
}
