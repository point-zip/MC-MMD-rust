// 负责承载从 ImmersiveMelodies 动画类捕获的乐器演奏姿势（vanilla 人形角度约定）。
package com.shiroha.mmdskin.compat.melodies;

/**
 * 一次乐器演奏姿势采样。所有角度为 vanilla ModelPart 约定的弧度值
 * （head/leftArm/rightArm 部件的 xRot/yRot/zRot），
 * 由 ImmersiveMelodies 的乐器 Animator 按当前音符状态实时计算。
 * 仅在实体真正处于演奏态（手持乐器的 PLAYING 标记为真）时非空，
 * 单纯手持乐器不会触发。
 */
public record MelodiesPose(
        float headPitch,
        float headYaw,
        float leftArmPitch,
        float leftArmYaw,
        float leftArmRoll,
        float rightArmPitch,
        float rightArmYaw,
        float rightArmRoll) {
}
