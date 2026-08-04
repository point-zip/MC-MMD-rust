package com.shiroha.mmdskin.compat.tacz;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
            ArmParts baseline = ArmParts.createNeutral();
            ArmParts current = ArmParts.createCurrent(entity, tickDelta);
            Object display = active.getDisplay(entity.getMainHandItem());
            if (display == null) return Optional.empty();
            // PlayerAnimator 使用独立骨骼动画，不会写入临时 ModelPart；此时保持 VMD 原姿态。
            if (active.hasPlayerAnimatorThirdPerson(entity, display)) return Optional.empty();

            Object animation = active.getAnimation(display);
            active.animateHold(animation, entity, baseline);
            active.animateCurrent(entity, current);

            Quaternionf left = TaczThirdPersonArmPose.relative(baseline.leftQuaternion(), current.leftQuaternion());
            Quaternionf right = TaczThirdPersonArmPose.relative(baseline.rightQuaternion(), current.rightQuaternion());
            TaczThirdPersonArmPose pose = new TaczThirdPersonArmPose(left, right,
                    (left != null ? 0b01 : 0) | (right != null ? 0b10 : 0));
            return pose.isValid() ? Optional.of(pose) : Optional.empty();
        } catch (ReflectiveOperationException | RuntimeException error) {
            // 枪包或 TaCZ 版本接口变化时只回退 VMD，不能中断玩家渲染。
            LOGGER.debug("TaCZ 第三人称姿态采样失败，已回退 VMD", error);
            return Optional.empty();
        }
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
        static ArmParts createNeutral() {
            return createParts();
        }

        static ArmParts createCurrent(LivingEntity entity, float tickDelta) {
            ArmParts parts = createParts();
            float bodyYaw = Mth.rotLerp(tickDelta, entity.yBodyRotO, entity.yBodyRot);
            float headYaw = Mth.rotLerp(tickDelta, entity.yHeadRotO, entity.yHeadRot);
            parts.head.xRot = entity.getXRot() * Mth.DEG_TO_RAD;
            parts.head.yRot = (headYaw - bodyYaw) * Mth.DEG_TO_RAD;
            return parts;
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

        Quaternionf leftQuaternion() {
            return new Quaternionf().rotationZYX(left.zRot, left.yRot, left.xRot);
        }

        Quaternionf rightQuaternion() {
            return new Quaternionf().rotationZYX(right.zRot, right.yRot, right.xRot);
        }
    }

    private record Bindings(Method getGunDisplayMethod, Method getAnimationMethod,
                            Method getThirdPersonAnimationMethod, Method animateGunHoldMethod,
                            Method animateCurrentMethod, Method hasPlayerAnimatorThirdPersonMethod) {
        static Bindings load() throws ReflectiveOperationException {
            ClassLoader loader = TaczThirdPersonPoseSampler.class.getClassLoader();
            Class<?> timelessApi = Class.forName("com.tacz.guns.api.TimelessAPI", false, loader);
            Class<?> thirdPersonManager = Class.forName("com.tacz.guns.api.client.other.ThirdPersonManager", false, loader);
            Class<?> animationType = Class.forName("com.tacz.guns.api.client.other.IThirdPersonAnimation", false, loader);
            Class<?> displayType = Class.forName("com.tacz.guns.client.resource.GunDisplayInstance", false, loader);
            Class<?> innerManager = Class.forName("com.tacz.guns.client.animation.third.InnerThirdPersonManager", false, loader);
            Class<?> playerAnimatorCompat = Class.forName("com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat",
                    false, loader);
            return new Bindings(
                    timelessApi.getMethod("getGunDisplay", ItemStack.class),
                    thirdPersonManager.getMethod("getAnimation", String.class),
                    displayType.getMethod("getThirdPersonAnimation"),
                    animationType.getMethod("animateGunHold", LivingEntity.class, ModelPart.class,
                            ModelPart.class, ModelPart.class, ModelPart.class),
                    innerManager.getMethod("setRotationAnglesHead", LivingEntity.class, ModelPart.class,
                            ModelPart.class, ModelPart.class, ModelPart.class, float.class),
                    playerAnimatorCompat.getMethod("hasPlayerAnimator3rd", LivingEntity.class, displayType));
        }

        Object getDisplay(ItemStack stack) throws ReflectiveOperationException {
            Object result = getGunDisplayMethod.invoke(null, stack);
            return result instanceof Optional<?> optional ? optional.orElse(null) : null;
        }

        Object getAnimation(Object display) throws ReflectiveOperationException {
            return getAnimationMethod.invoke(null, getThirdPersonAnimationMethod.invoke(display));
        }

        boolean hasPlayerAnimatorThirdPerson(LivingEntity entity, Object display)
                throws ReflectiveOperationException {
            return Boolean.TRUE.equals(hasPlayerAnimatorThirdPersonMethod.invoke(null, entity, display));
        }

        void animateHold(Object animation, LivingEntity entity, ArmParts parts) throws ReflectiveOperationException {
            animateGunHoldMethod.invoke(animation, entity, parts.right, parts.left, parts.body, parts.head);
        }

        void animateCurrent(LivingEntity entity, ArmParts parts) throws ReflectiveOperationException {
            animateCurrentMethod.invoke(null, entity, parts.right, parts.left, parts.body, parts.head, 0.0f);
        }
    }
}
