package com.shiroha.mmdskin.compat.tacz;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

/** 文件职责：修正 TaCZ 第三人称枪模相对 MMD 右手挂点的局部朝向。 */
public final class TaczThirdPersonGunTransform {
    static final float LOCAL_Z_CORRECTION_RADIANS = (float) Math.toRadians(45.0);

    private TaczThirdPersonGunTransform() {
    }

    public static void apply(PoseStack poseStack, InteractionHand hand, ItemStack itemStack) {
        if (!shouldApply(hand == InteractionHand.MAIN_HAND, TaczGunDetector.isGun(itemStack))) {
            return;
        }

        // 必须紧跟挂点矩阵后乘，确保补偿始终绕 Hand_Attach_R 的局部 Z 轴。
        poseStack.mulPose(correction());
    }

    static boolean shouldApply(boolean mainHand, boolean taczGun) {
        return mainHand && taczGun;
    }

    static Quaternionf correction() {
        return new Quaternionf().rotateZ(LOCAL_Z_CORRECTION_RADIANS);
    }
}
