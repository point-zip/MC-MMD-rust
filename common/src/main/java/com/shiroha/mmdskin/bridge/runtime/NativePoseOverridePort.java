/* 文件职责：定义程序化骨骼姿势覆盖的 native 写入边界。 */
package com.shiroha.mmdskin.bridge.runtime;

import org.joml.Vector3f;

/**
 * 程序化骨骼姿势覆盖通道（乐器演奏等）。
 *
 * 与 VPD 表情通道相互独立：VPD 先应用、本通道在其后覆盖，故演奏姿势生效
 * 而其余骨骼仍按 VPD/动画表现；清除本通道不会影响已应用的 VPD 姿势。
 */
public interface NativePoseOverridePort {
    /**
     * 按骨骼名设置姿势覆盖（动画评估后、物理前应用，会压过 VMD 动画层，
     * 并禁用控制该骨骼的 IK 解算器以免被拉回）。
     * rotation 为骨骼局部空间四元数（xyzw），与 VMD 旋转同一坐标系约定。
     *
     * @return 骨骼是否存在并成功写入
     */
    boolean setBoneOverride(long modelHandle, String boneName,
                            float tx, float ty, float tz,
                            float qx, float qy, float qz, float qw);

    /** 清除本通道的全部骨骼姿势覆盖（不影响 VPD 通道）。 */
    void clearBoneOverrides(long modelHandle);

    /**
     * 查询"骨骼A → 骨骼B"的静息方向（模型空间单位向量）。
     * 用于程序化姿势映射：静息朝向因模型而异（T-pose / A-pose），不能硬编码。
     *
     * @return 骨骼缺失或两点重合时返回 null
     */
    Vector3f boneRestDirection(long modelHandle, String fromBone, String toBone);
}
