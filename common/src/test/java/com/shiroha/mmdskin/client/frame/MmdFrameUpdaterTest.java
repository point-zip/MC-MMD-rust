// 负责验证模型在同一帧只推进一次且新帧恢复推进。
package com.shiroha.mmdskin.client.frame;

import com.shiroha.mmdskin.client.animation.MmdAnimationIntent;
import com.shiroha.mmdskin.client.model.ModelKey;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MmdFrameUpdaterTest {
    @Test
    void shouldUpdateEachTargetAtMostOncePerFrame() {
        MmdFrameUpdater updater = new MmdFrameUpdater();
        AtomicInteger updates = new AtomicInteger();
        FrameUpdateTarget target = (snapshot, deltaSeconds) -> updates.incrementAndGet();
        MmdRenderSnapshot snapshot = snapshot(2.0D);

        assertTrue(updater.updateOnce(10L, target, snapshot, 0.05F));
        assertFalse(updater.updateOnce(10L, target, snapshot, 0.05F));
        assertTrue(updater.updateOnce(11L, target, snapshot, 0.05F));
        assertEquals(2, updates.get());
    }

    @Test
    void shouldPrepareAnimationAndMotionBeforeNativeUpdateOnlyOnce() {
        List<String> calls = new ArrayList<>();
        MmdFrameUpdater updater = new MmdFrameUpdater(
                (target, snapshot) -> calls.add("prepare"));
        FrameUpdateTarget target = (snapshot, deltaSeconds) -> calls.add("update");
        MmdRenderSnapshot snapshot = snapshot(1.0D);

        updater.updateOnce(7L, target, snapshot, 0.05F);
        updater.updateOnce(7L, target, snapshot, 0.05F);

        assertEquals(List.of("prepare", "update"), calls);
    }

    public static MmdRenderSnapshot snapshot(double distanceSquared) {
        return new MmdRenderSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                new ModelKey("test", "owner", ModelKey.Usage.ENTITY),
                MmdEntityPose.standing(),
                MmdAnimationIntent.none(),
                MmdModelMotion.stationary(),
                ModelTransform.identity(),
                MmdRenderSnapshot.Visibility.OPAQUE,
                false,
                0,
                distanceSquared,
                MmdRenderSnapshot.Context.WORLD);
    }
}
