// 负责定义 native 帧推进前的动画与模型状态同步边界。
package com.shiroha.mmdskin.client.frame;

@FunctionalInterface
public interface FrameUpdatePreparation {
    FrameUpdatePreparation NONE = (target, snapshot) -> {
    };

    void prepare(FrameUpdateTarget target, MmdRenderSnapshot snapshot);
}
