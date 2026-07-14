/* 文件职责：定义模型运行时写入相关的 native 能力边界。 */
package com.shiroha.mmdskin.bridge.runtime;

public interface NativeModelPort {

    default boolean setGpuSkinningEnabled(long modelHandle, boolean enabled) { return false; }

    boolean setLayerBoneMask(long modelHandle, int layer, String rootBoneName);

    boolean setLayerBoneExclude(long modelHandle, int layer, String rootBoneName);

    long getModelMemoryUsage(long modelHandle);

    void setFirstPersonMode(long modelHandle, boolean enabled);

    void getEyeBonePosition(long modelHandle, float[] output);

    void setEyeTrackingEnabled(long modelHandle, boolean enabled);

    void setEyeMaxAngle(long modelHandle, float maxAngle);

    void setAutoBlinkEnabled(long modelHandle, boolean enabled);

    int getMaterialCount(long modelHandle);

    void setMaterialVisible(long modelHandle, int materialIndex, boolean visible);

    void setAllMaterialsVisible(long modelHandle, boolean visible);

    void deleteModel(long modelHandle);
}
