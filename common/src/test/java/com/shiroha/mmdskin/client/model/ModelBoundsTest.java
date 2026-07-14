// 负责验证模型局部 bounds 对任意水平朝向的保守世界剔除盒。
package com.shiroha.mmdskin.client.model;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelBoundsTest {
    @Test
    void shouldUseHorizontalRadiusSoYawCannotEscapeCullingBox() {
        ModelBounds bounds = new ModelBounds(-2.0F, -1.0F, -4.0F, 3.0F, 10.0F, 1.0F);

        AABB original = new AABB(7.8D, 64.0D, -2.2D, 8.2D, 65.8D, -1.8D);

        AABB box = bounds.expandCullingBox(original, 8.0D, 64.0D, -2.0D, 0.1F);

        assertEquals(7.6D, box.minX, 1.0E-6D);
        assertEquals(63.9D, box.minY, 1.0E-6D);
        assertEquals(-2.4D, box.minZ, 1.0E-6D);
        assertEquals(8.4D, box.maxX, 1.0E-6D);
        assertEquals(65.8D, box.maxY, 1.0E-6D);
        assertEquals(-1.6D, box.maxZ, 1.0E-6D);
    }
}
