// 负责定义动画句柄加载、播放与释放的 native Interface。
package com.shiroha.mmdskin.bridge.runtime;

public interface NativeAnimationPort {
    long loadAnimation(long modelHandle, String path);

    void deleteAnimation(long animationHandle);

    void changeAnimation(long modelHandle, long animationHandle, int layer);

    void transitionAnimation(long modelHandle, int layer, long animationHandle, float transitionSeconds);

    void setLayerLoop(long modelHandle, int layer, boolean loop);

    boolean isLayerAnimationFinished(long modelHandle, int layer);

    void seekLayer(long modelHandle, int layer, float frame);
}

