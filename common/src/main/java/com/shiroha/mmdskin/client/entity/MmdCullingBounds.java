// 负责用已加载模型 bounds 扩大实体剔除盒，并在不可用时保留原始 AABB。
package com.shiroha.mmdskin.client.entity;

import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import com.shiroha.mmdskin.client.model.ModelBounds;
import com.shiroha.mmdskin.config.ModelConfigManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.Objects;

public final class MmdCullingBounds {
    private static final float BASE_MODEL_SCALE = 0.09F;

    private MmdCullingBounds() {
    }

    public static AABB expand(LivingEntity entity, AABB original) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(original, "original");
        String modelName = EntityModelSelection.resolveModelName(entity);
        if (modelName == null) {
            return original;
        }

        ModelBounds bounds;
        try {
            bounds = MmdClientRenderRuntime.current().models().loadedBounds(modelName);
        } catch (IllegalStateException ignored) {
            return original;
        }
        if (bounds == null) {
            return original;
        }

        float configuredScale = ModelConfigManager.getLiveConfig(modelName).modelScale;
        float scale = BASE_MODEL_SCALE * configuredScale;
        if (!Float.isFinite(scale) || scale <= 0.0F) {
            return original;
        }
        return bounds.expandCullingBox(
                original, entity.getX(), entity.getY(), entity.getZ(), scale);
    }
}
