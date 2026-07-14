// 负责验证 Render Metrics 始终读取资源所有者的最新驻留计数。
package com.shiroha.mmdskin.client.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RenderMetricsTest {
    @Test
    void shouldReflectResourceReleaseWithoutMetricSideEffects() {
        MutableSources sources = new MutableSources();
        sources.loadedModels = 2;
        sources.pendingModels = 1;
        sources.queuedDraws = 3;
        sources.textureCount = 4;
        sources.textureBytes = 1024L;
        sources.modelGpuBytes = 4096L;
        sources.uploadedVertexBytes = 8192L;
        sources.uploadCount = 5L;
        RenderMetrics metrics = new RenderMetrics(sources, sources, sources, sources);

        RenderMetrics.Snapshot populated = metrics.snapshot();
        assertEquals(4, populated.textureCount());
        assertEquals(1024L, populated.textureBytes());
        assertEquals(4096L, populated.modelGpuBytes());
        assertEquals(8192L, populated.uploadedVertexBytes());
        assertEquals(5L, populated.uploadCount());

        sources.textureCount = 0;
        sources.textureBytes = 0L;
        sources.modelGpuBytes = 0L;
        RenderMetrics.Snapshot released = metrics.snapshot();
        assertEquals(0, released.textureCount());
        assertEquals(0L, released.textureBytes());
        assertEquals(0L, released.modelGpuBytes());
        assertEquals(8192L, released.uploadedVertexBytes(), "upload totals are cumulative");
    }

    private static final class MutableSources implements ModelLoadMetricsPort, FrameQueueMetricsPort,
            TextureMetricsPort, ModelGpuMetricsPort {
        private int loadedModels;
        private int pendingModels;
        private int queuedDraws;
        private int textureCount;
        private long textureBytes;
        private long modelGpuBytes;
        private long uploadedVertexBytes;
        private long uploadCount;

        @Override
        public int loadedCount() {
            return loadedModels;
        }

        @Override
        public int pendingCount() {
            return pendingModels;
        }

        @Override
        public int size() {
            return queuedDraws;
        }

        @Override
        public int residentCount() {
            return textureCount;
        }

        @Override
        public long estimatedResidentBytes() {
            return textureBytes;
        }

        @Override
        public long estimatedModelGpuBytes() {
            return modelGpuBytes;
        }

        @Override
        public long uploadedVertexBytes() {
            return uploadedVertexBytes;
        }

        @Override
        public long uploadCount() {
            return uploadCount;
        }
    }
}
