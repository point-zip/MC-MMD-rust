// 负责定义单次帧推进所需的最小模型 Interface。
package com.shiroha.mmdskin.client.frame;

@FunctionalInterface
public interface FrameUpdateTarget {
    void update(MmdRenderSnapshot snapshot, float deltaSeconds);
}

