// 负责把不可变动画意图转换为模型动画层的最小状态变更。
package com.shiroha.mmdskin.client.animation;

import com.shiroha.mmdskin.client.model.MmdModelInstance;

import java.util.Objects;

public final class ModelAnimationController {
    private static final float TRANSITION_SECONDS = 0.25F;

    private final AnimationRepository animations;

    public ModelAnimationController(AnimationRepository animations) {
        this.animations = Objects.requireNonNull(animations, "animations");
    }

    public void apply(MmdModelInstance model, MmdAnimationIntent intent) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(intent, "intent");
        if (!intent.active() || model.animationState().playCustomAnim) {
            return;
        }

        transitionIfChanged(model, 0, intent.baseAnimation(), true);
        applyAction(model, intent.action());
        transitionIfChanged(model, 2, intent.postureAnimation(), true);
    }

    private void applyAction(MmdModelInstance model, MmdAnimationIntent.Action action) {
        if (action.preserveCurrent()) {
            return;
        }
        if (action.clearsLayer()) {
            transitionIfChanged(model, 1, null, true);
            return;
        }

        ResolvedAnimation resolved = firstAvailable(model, action);
        if (resolved == null) {
            transitionIfChanged(model, 1, null, true);
            return;
        }
        transitionIfChanged(model, 1, resolved.key(), resolved.handle(), action.loop());
    }

    private ResolvedAnimation firstAvailable(MmdModelInstance model, MmdAnimationIntent.Action action) {
        ResolvedAnimation resolved = resolve(model, action.primaryAnimation());
        if (resolved != null) return resolved;
        resolved = resolve(model, action.secondaryAnimation());
        if (resolved != null) return resolved;
        return resolve(model, action.fallbackAnimation());
    }

    private ResolvedAnimation resolve(MmdModelInstance model, String animationKey) {
        if (animationKey == null) {
            return null;
        }
        long handle = animations.animationFor(model, animationKey);
        return handle == 0L ? null : new ResolvedAnimation(animationKey, handle);
    }

    private void transitionIfChanged(MmdModelInstance model, int layer, String animationKey, boolean loop) {
        long handle = animationKey == null ? 0L : animations.animationFor(model, animationKey);
        transitionIfChanged(model, layer, animationKey, handle, loop);
    }

    private static void transitionIfChanged(MmdModelInstance model, int layer, String animationKey,
                                            long animationHandle, boolean loop) {
        if (Objects.equals(model.animationState().layerAnimationKeys[layer], animationKey)) {
            return;
        }
        model.animationState().layerAnimationKeys[layer] = animationKey;
        model.animationState().stateLayers[layer] = null;
        model.setLayerLoop(layer, loop);
        model.transitionAnimation(animationHandle, layer, TRANSITION_SECONDS);
    }

    private record ResolvedAnimation(String key, long handle) {
    }
}
