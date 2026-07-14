// 负责聚合渲染运行时计数并提供不可变调试快照。
package com.shiroha.mmdskin.client.metrics;

import java.util.Objects;

public final class RenderMetrics {
    private final ModelLoadMetricsPort models;
    private final FrameQueueMetricsPort queue;
    private final TextureMetricsPort textures;
    private final ModelGpuMetricsPort modelGpu;

    public RenderMetrics(ModelLoadMetricsPort models, FrameQueueMetricsPort queue,
                         TextureMetricsPort textures, ModelGpuMetricsPort modelGpu) {
        this.models = Objects.requireNonNull(models, "models");
        this.queue = Objects.requireNonNull(queue, "queue");
        this.textures = Objects.requireNonNull(textures, "textures");
        this.modelGpu = Objects.requireNonNull(modelGpu, "modelGpu");
    }

    public Snapshot snapshot() {
        return new Snapshot(
                models.loadedCount(),
                models.pendingCount(),
                queue.size(),
                textures.residentCount(),
                textures.estimatedResidentBytes(),
                modelGpu.estimatedModelGpuBytes(),
                modelGpu.uploadedVertexBytes(),
                modelGpu.uploadCount());
    }

    public record Snapshot(
            int loadedModels,
            int pendingModels,
            int queuedDraws,
            int textureCount,
            long textureBytes,
            long modelGpuBytes,
            long uploadedVertexBytes,
            long uploadCount) {
    }
}
