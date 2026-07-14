// 负责保证每个模型实例在一个渲染帧内最多推进一次。
package com.shiroha.mmdskin.client.frame;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

public final class MmdFrameUpdater {
    private final FrameUpdatePreparation preparation;
    private final Map<FrameUpdateTarget, Boolean> updatedTargets = new IdentityHashMap<>();
    private long currentFrame = Long.MIN_VALUE;

    public MmdFrameUpdater() {
        this(FrameUpdatePreparation.NONE);
    }

    public MmdFrameUpdater(FrameUpdatePreparation preparation) {
        this.preparation = Objects.requireNonNull(preparation, "preparation");
    }

    public boolean updateOnce(long frameId, FrameUpdateTarget target,
                              MmdRenderSnapshot snapshot, float deltaSeconds) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(snapshot, "snapshot");
        if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0.0F) {
            throw new IllegalArgumentException("deltaSeconds must be finite and non-negative");
        }
        beginFrame(frameId);
        if (updatedTargets.put(target, Boolean.TRUE) != null) {
            return false;
        }
        preparation.prepare(target, snapshot);
        target.update(snapshot, deltaSeconds);
        return true;
    }

    private void beginFrame(long frameId) {
        if (currentFrame == frameId) {
            return;
        }
        currentFrame = frameId;
        updatedTargets.clear();
    }
}
