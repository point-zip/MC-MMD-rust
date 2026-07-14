// 文件职责：验证物品栏局部队列的嵌套刷新、租约释放与中止语义。
package com.shiroha.mmdskin.client.inventory;

import com.shiroha.mmdskin.client.draw.DrawModelRef;
import com.shiroha.mmdskin.client.draw.MmdDrawRequest;
import com.shiroha.mmdskin.client.draw.PipelineVariant;
import com.shiroha.mmdskin.client.frame.MmdFrameUpdaterTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryRenderSessionTest {
    @Test
    void shouldFlushOnlyWhenOutermostScopeFinishes() {
        AtomicInteger draws = new AtomicInteger();
        AtomicInteger releases = new AtomicInteger();
        InventoryRenderSession session = new InventoryRenderSession(
                requests -> draws.addAndGet(requests.size()));

        session.begin();
        session.begin();
        session.enqueue(request(), releases::incrementAndGet);
        session.finish();

        assertTrue(session.active());
        assertEquals(0, draws.get());
        assertEquals(0, releases.get());

        session.finish();

        assertFalse(session.active());
        assertEquals(1, draws.get());
        assertEquals(1, releases.get());
    }

    @Test
    void shouldReleaseWithoutDrawingWhenAborted() {
        AtomicInteger draws = new AtomicInteger();
        AtomicInteger releases = new AtomicInteger();
        InventoryRenderSession session = new InventoryRenderSession(
                requests -> draws.addAndGet(requests.size()));

        session.begin();
        session.enqueue(request(), releases::incrementAndGet);
        session.abort();

        assertFalse(session.active());
        assertEquals(0, draws.get());
        assertEquals(1, releases.get());
    }

    private static MmdDrawRequest request() {
        return new MmdDrawRequest(new TestModel(), MmdFrameUpdaterTest.snapshot(0.0D),
                PipelineVariant.OPAQUE, false);
    }

    private record TestModel() implements DrawModelRef {
        @Override
        public long modelInstanceId() {
            return 1L;
        }

        @Override
        public long nativeHandle() {
            return 1L;
        }

        @Override
        public long nativeRevision() {
            return 0L;
        }
    }
}
