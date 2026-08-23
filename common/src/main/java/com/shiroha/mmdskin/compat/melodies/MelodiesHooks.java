// 负责 ImmersiveMelodies 姿势采样实现：用离屏假 ModelPart 接住 IM 动画类的角度输出。
// 本文件引用 IM 类（compileOnly），只允许在 MelodiesCompat.isLoaded() 为 true 后加载。
package com.shiroha.mmdskin.compat.melodies;

import immersive_melodies.client.animation.EntityModelAnimator;
import immersive_melodies.client.animation.accessors.ModelAccessor;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

final class MelodiesHooks {
    private static final ModelPart HEAD = new ModelPart(List.of(), Map.of());
    private static final ModelPart HAT = new ModelPart(List.of(), Map.of());
    private static final ModelPart LEFT_ARM = new ModelPart(List.of(), Map.of());
    private static final ModelPart RIGHT_ARM = new ModelPart(List.of(), Map.of());

    private MelodiesHooks() {
    }

    static MelodiesPose capture(LivingEntity entity) {
        reset(HEAD);
        reset(HAT);
        reset(LEFT_ARM);
        reset(RIGHT_ARM);

        // IM 自动完成：乐器识别（主/副手 InstrumentItem）、音符时间轴推进、
        // 分发到对应乐器 Animator（含左右撇子翻转，写入我们的假部件）
        EntityModelAnimator.setAngles(new Adapter(entity));

        return new MelodiesPose(
                HEAD.xRot, HEAD.yRot,
                LEFT_ARM.xRot, LEFT_ARM.yRot, LEFT_ARM.zRot,
                RIGHT_ARM.xRot, RIGHT_ARM.yRot, RIGHT_ARM.zRot);
    }

    private static void reset(ModelPart part) {
        part.xRot = 0.0F;
        part.yRot = 0.0F;
        part.zRot = 0.0F;
    }

    private static final class Adapter implements ModelAccessor<LivingEntity> {
        private final LivingEntity entity;

        Adapter(LivingEntity entity) {
            this.entity = entity;
        }

        @Override
        public LivingEntity getEntity() {
            return entity;
        }

        @Override
        public Optional<ModelPart> getHead() {
            return Optional.of(HEAD);
        }

        @Override
        public Optional<ModelPart> getHat() {
            return Optional.of(HAT);
        }

        @Override
        public Optional<ModelPart> getLeftArm() {
            return Optional.of(LEFT_ARM);
        }

        @Override
        public Optional<ModelPart> getRightArm() {
            return Optional.of(RIGHT_ARM);
        }
    }
}
