// 负责提供纹理仓储当前驻留资源的只读计数。
package com.shiroha.mmdskin.client.metrics;

public interface TextureMetricsPort {
    int residentCount();

    long estimatedResidentBytes();
}
