// 文件职责：定义 VR 追踪输入与 IK 配置的 native 边界。
package com.shiroha.mmdskin.bridge.runtime;

public interface NativeVrPort {
    void applyTrackingInput(long modelHandle, float[] trackingData);

    void setEnabled(long modelHandle, boolean enabled);

    void setIkParams(long modelHandle, float armIkStrength);
}
