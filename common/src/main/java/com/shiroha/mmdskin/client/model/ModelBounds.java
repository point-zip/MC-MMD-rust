// 负责保存模型局部空间 bounds，并生成覆盖任意水平朝向的世界剔除盒。
package com.shiroha.mmdskin.client.model;

import net.minecraft.world.phys.AABB;

public record ModelBounds(float minX, float minY, float minZ,
                          float maxX, float maxY, float maxZ) {
    public ModelBounds {
        if (!Float.isFinite(minX) || !Float.isFinite(minY) || !Float.isFinite(minZ)
                || !Float.isFinite(maxX) || !Float.isFinite(maxY) || !Float.isFinite(maxZ)) {
            throw new IllegalArgumentException("model bounds must be finite");
        }
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("model bounds minimum must not exceed maximum");
        }
    }

    public AABB expandCullingBox(AABB original, double x, double y, double z, float scale) {
        if (original == null) {
            throw new NullPointerException("original");
        }
        if (!Float.isFinite(scale) || scale <= 0.0F) {
            throw new IllegalArgumentException("scale must be finite and positive");
        }
        double horizontalRadius = Math.max(
                Math.max(Math.abs(minX), Math.abs(maxX)),
                Math.max(Math.abs(minZ), Math.abs(maxZ))) * scale;
        double scaledMinY = minY * (double) scale;
        double scaledMaxY = maxY * (double) scale;
        return new AABB(
                Math.min(original.minX, x - horizontalRadius),
                Math.min(original.minY, y + scaledMinY),
                Math.min(original.minZ, z - horizontalRadius),
                Math.max(original.maxX, x + horizontalRadius),
                Math.max(original.maxY, y + scaledMaxY),
                Math.max(original.maxZ, z + horizontalRadius));
    }
}
