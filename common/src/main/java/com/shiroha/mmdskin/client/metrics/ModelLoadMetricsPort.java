// 负责提供模型仓储加载状态的只读计数。
package com.shiroha.mmdskin.client.metrics;

public interface ModelLoadMetricsPort {
    int loadedCount();

    int pendingCount();
}
