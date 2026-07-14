// 负责保存动画推进需要的不可变实体姿态数据。
package com.shiroha.mmdskin.client.frame;

import net.minecraft.world.entity.Pose;

import java.util.Objects;

public record MmdEntityPose(
        float bodyYaw,
        float headYaw,
        float pitch,
        float ageInTicks,
        float walkPosition,
        float walkSpeed,
        float deathTime,
        boolean baby,
        boolean inWater,
        boolean autoSpinAttack,
        Pose pose) {

    public MmdEntityPose {
        pose = Objects.requireNonNull(pose, "pose");
    }

    public static MmdEntityPose standing() {
        return new MmdEntityPose(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
                false, false, false, Pose.STANDING);
    }
}

