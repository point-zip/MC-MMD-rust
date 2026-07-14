// 负责通知模型仓储之外的资源拥有者释放同一 Model Instance 的资源。
package com.shiroha.mmdskin.client.model;

@FunctionalInterface
public interface ModelDisposalListener {
    ModelDisposalListener NOOP = modelInstanceId -> {
    };

    void onDisposed(long modelInstanceId);
}
