// 负责在单次 native 帧推进前同步模型动画意图与世界运动状态。
package com.shiroha.mmdskin.client.frame;

import com.shiroha.mmdskin.client.animation.ModelAnimationController;
import com.shiroha.mmdskin.client.model.MmdModelInstance;
import com.shiroha.mmdskin.compat.melodies.MelodiesCompat;
import com.shiroha.mmdskin.compat.melodies.MmdArmPoseMapper;
import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.client.gpu.MmdRenderPipelines;

import java.util.Objects;

public final class MmdModelFramePreparation implements FrameUpdatePreparation {
    private static final float NATIVE_WORLD_SCALE = 0.09F;

    private final ModelAnimationController animations;

    public MmdModelFramePreparation(ModelAnimationController animations) {
        this.animations = Objects.requireNonNull(animations, "animations");
    }

    @Override
    public void prepare(FrameUpdateTarget target, MmdRenderSnapshot snapshot) {
        if (!(target instanceof MmdModelInstance model)) {
            return;
        }
        model.setGpuSkinningEnabled(ConfigManager.isGpuSkinningEnabled() && MmdRenderPipelines.isGpuReady());
        animations.apply(model, snapshot.animationIntent());
        // 乐器演奏姿势覆盖（在 native UpdateModel 之前设置，vpd 管线动画后/物理前应用）
        var instrumentPose = snapshot.instrumentPose();
        if (instrumentPose != null) {
            MmdArmPoseMapper.apply(model.nativeHandle(), instrumentPose);
        } else if (MelodiesCompat.isLoaded()) {
            MmdArmPoseMapper.clear(model.nativeHandle());
        }
        MmdModelMotion motion = snapshot.motion();
        if (motion.synchronizeNative()) {
            model.setModelPositionAndYaw(
                    motion.x() * NATIVE_WORLD_SCALE,
                    motion.y() * NATIVE_WORLD_SCALE,
                    motion.z() * NATIVE_WORLD_SCALE,
                    motion.yawRadians());
        }
    }
}
