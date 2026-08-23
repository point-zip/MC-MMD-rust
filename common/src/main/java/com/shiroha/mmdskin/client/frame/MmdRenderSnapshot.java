// 负责承载完成原版状态提取后的不可变 MMD 帧快照。
package com.shiroha.mmdskin.client.frame;

import com.shiroha.mmdskin.client.animation.MmdAnimationIntent;
import com.shiroha.mmdskin.client.model.ModelKey;
import com.shiroha.mmdskin.compat.melodies.MelodiesPose;

import java.util.Objects;
import java.util.UUID;

public record MmdRenderSnapshot(
        UUID entityId,
        ModelKey modelKey,
        MmdEntityPose pose,
        MmdAnimationIntent animationIntent,
        MmdModelMotion motion,
        ModelTransform transform,
        Visibility visibility,
        boolean glowing,
        int packedLight,
        double cameraDistanceSquared,
        Context context,
        MelodiesPose instrumentPose) {

    public MmdRenderSnapshot {
        entityId = Objects.requireNonNull(entityId, "entityId");
        modelKey = Objects.requireNonNull(modelKey, "modelKey");
        pose = Objects.requireNonNull(pose, "pose");
        animationIntent = Objects.requireNonNull(animationIntent, "animationIntent");
        motion = Objects.requireNonNull(motion, "motion");
        transform = Objects.requireNonNull(transform, "transform");
        visibility = Objects.requireNonNull(visibility, "visibility");
        context = Objects.requireNonNull(context, "context");
        if (!Double.isFinite(cameraDistanceSquared) || cameraDistanceSquared < 0.0D) {
            throw new IllegalArgumentException("cameraDistanceSquared must be finite and non-negative");
        }
    }

    public boolean rendersBody() {
        return visibility != Visibility.HIDDEN;
    }

    public MmdRenderSnapshot withRenderTransform(ModelTransform renderTransform, int renderLight) {
        return new MmdRenderSnapshot(entityId, modelKey, pose, animationIntent, motion, renderTransform, visibility,
                glowing, renderLight, cameraDistanceSquared, context, instrumentPose);
    }

    public MmdRenderSnapshot withContext(Context renderContext) {
        return new MmdRenderSnapshot(entityId, modelKey, pose, animationIntent, motion, transform, visibility,
                glowing, packedLight, cameraDistanceSquared, renderContext, instrumentPose);
    }

    public MmdRenderSnapshot withAnimationIntent(MmdAnimationIntent intent) {
        return new MmdRenderSnapshot(entityId, modelKey, pose, intent, motion, transform, visibility,
                glowing, packedLight, cameraDistanceSquared, context, instrumentPose);
    }

    public enum Visibility {
        OPAQUE,
        TRANSLUCENT,
        HIDDEN
    }

    public enum Context {
        WORLD,
        FIRST_PERSON,
        INVENTORY,
        SCENE
    }
}
