/* 文件职责：定义程序化骨骼姿势覆盖的 native 写入边界。 */
package com.shiroha.mmdskin.bridge.runtime;

public interface NativePoseOverridePort {
    /**
     * 按骨骼名设置姿势覆盖（动画评估后、物理前应用，会压过 VMD 动画层）。
     * rotation 为骨骼局部空间四元数（xyzw）。
     *
     * @return 骨骼是否存在并成功写入
     */
    boolean setBoneOverride(long modelHandle, String boneName,
                            float tx, float ty, float tz,
                            float qx, float qy, float qz, float qw);

    /** 清除全部骨骼姿势覆盖。 */
    void clearBoneOverrides(long modelHandle);
}
