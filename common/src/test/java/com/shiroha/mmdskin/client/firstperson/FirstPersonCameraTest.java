// 负责验证第一人称相机的纯坐标变换与视线计算。
package com.shiroha.mmdskin.client.firstperson;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FirstPersonCameraTest {
    @Test
    void rotatesModelEyeOffsetAroundBodyYaw() {
        Vec3 actual = FirstPersonCamera.rotateEyeOffset(
                new Vec3(10.0D, 20.0D, 30.0D),
                90.0F,
                new Vec3(0.09D, 0.18D, 0.27D));

        assertVector(new Vec3(9.73D, 20.18D, 30.09D), actual);
    }

    @Test
    void appliesForwardAndVerticalOffsetsInViewSpace() {
        Vec3 actual = FirstPersonCamera.applyViewOffsets(
                new Vec3(1.0D, 2.0D, 3.0D),
                90.0F,
                0.0F,
                0.5D,
                0.25D);

        assertVector(new Vec3(0.5D, 2.25D, 3.0D), actual);
    }

    @Test
    void derivesViewVectorFromCameraRotation() {
        Vec3 actual = FirstPersonCamera.viewVector(90.0F, 0.0F);

        assertVector(new Vec3(-1.0D, 0.0D, 0.0D), actual);
    }

    private static void assertVector(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, 1.0E-6D);
        assertEquals(expected.y, actual.y, 1.0E-6D);
        assertEquals(expected.z, actual.z, 1.0E-6D);
    }
}
