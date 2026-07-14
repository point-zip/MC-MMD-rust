// 负责提供模型 GPU 资源与动态顶点上传的只读计数。
package com.shiroha.mmdskin.client.metrics;

public interface ModelGpuMetricsPort {
    long estimatedModelGpuBytes();

    long uploadedVertexBytes();

    long uploadCount();
}
