// 负责验证帧队列的材质顺序、透明排序和异常后清理语义。
package com.shiroha.mmdskin.client.draw;

import com.shiroha.mmdskin.client.frame.MmdFrameUpdaterTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrameRenderQueueTest {
    @Test
    void shouldDrawOpaqueFirstAndTransparentBackToFront() {
        FrameRenderQueue queue = new FrameRenderQueue();
        queue.enqueue(request(1L, false, 4.0D));
        queue.enqueue(request(2L, true, 9.0D));
        queue.enqueue(request(3L, true, 25.0D));
        List<Long> order = new ArrayList<>();

        queue.flush(requests -> requests.forEach(request -> order.add(request.model().modelInstanceId())));

        assertEquals(List.of(1L, 3L, 2L), order);
        assertEquals(0, queue.size());
    }

    @Test
    void shouldClearBeforeInvokingDevice() {
        FrameRenderQueue queue = new FrameRenderQueue();
        queue.enqueue(request(1L, false, 0.0D));

        assertThrows(IllegalStateException.class,
                () -> queue.flush(requests -> { throw new IllegalStateException("failed"); }));
        assertEquals(0, queue.size());
    }

    private static MmdDrawRequest request(long id, boolean transparent, double distanceSquared) {
        return new MmdDrawRequest(
                new TestDrawModel(id),
                MmdFrameUpdaterTest.snapshot(distanceSquared),
                transparent ? PipelineVariant.TRANSLUCENT : PipelineVariant.OPAQUE,
                transparent);
    }

    private record TestDrawModel(long modelInstanceId) implements DrawModelRef {
        @Override
        public long nativeHandle() {
            return modelInstanceId;
        }

        @Override
        public long nativeRevision() {
            return 0L;
        }
    }
}
