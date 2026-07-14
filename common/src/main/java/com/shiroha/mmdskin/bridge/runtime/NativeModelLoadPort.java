// 负责定义模型解析与基础帧更新的 native Interface。
package com.shiroha.mmdskin.bridge.runtime;

public interface NativeModelLoadPort {
    long loadModel(String modelFile, String modelDirectory, Format format, int animationLayers);

    boolean isVrm(long modelHandle);

    void updateModel(long modelHandle, float deltaSeconds);

    void resetPhysics(long modelHandle);

    void setPhysicsEnabled(long modelHandle, boolean enabled);

    void setModelPositionAndYaw(long modelHandle, float x, float y, float z, float yawRadians);

    enum Format {
        PMX,
        PMD,
        VRM
    }
}

