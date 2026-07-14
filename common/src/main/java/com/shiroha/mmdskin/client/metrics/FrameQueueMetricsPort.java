// 负责提供当前帧绘制队列的只读计数。
package com.shiroha.mmdskin.client.metrics;

@FunctionalInterface
public interface FrameQueueMetricsPort {
    int size();
}
