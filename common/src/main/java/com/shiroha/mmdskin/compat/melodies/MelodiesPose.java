// 负责承载从 ImmersiveMelodies 动画类捕获的乐器演奏姿势（vanilla 人形角度约定）。
package com.shiroha.mmdskin.compat.melodies;

/**
 * 一次乐器演奏姿势采样。所有角度为 vanilla ModelPart 约定的弧度值
 * （head/leftArm/rightArm 部件的 xRot/yRot/zRot），
 * 由 ImmersiveMelodies 的乐器 Animator 按当前音符状态实时计算。
 *
 * 与 IM 自身行为一致：**手持**乐器即产生姿势（演奏与否只影响音符调制幅度）。
 *
 * @param instrument 乐器名（IM 的物品注册名，如 flute/lute/handpan；未知时为 "?"），
 *                   仅用于诊断显示，不参与姿势计算
 */
public record MelodiesPose(
        String instrument,
        float headPitch,
        float headYaw,
        float leftArmPitch,
        float leftArmYaw,
        float leftArmRoll,
        float rightArmPitch,
        float rightArmYaw,
        float rightArmRoll) {
}
