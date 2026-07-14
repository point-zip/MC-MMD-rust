// 负责定义模型加载阶段一次性提取局部 bounds 的 Port。
package com.shiroha.mmdskin.client.model;

@FunctionalInterface
public interface ModelBoundsLoader {
    ModelBoundsLoader UNAVAILABLE = modelHandle -> null;

    ModelBounds load(long modelHandle);
}
