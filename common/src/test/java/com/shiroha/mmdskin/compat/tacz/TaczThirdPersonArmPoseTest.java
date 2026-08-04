package com.shiroha.mmdskin.compat.tacz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

/** 验证第三人称相对旋转和 JNI 数据布局。 */
class TaczThirdPersonArmPoseTest {
    private static final float EPSILON = 1.0e-5f;

    @Test
    void relativeRotationRemovesTheWeaponHoldBaseline() {
        Quaternionf baseline = new Quaternionf().rotationY(0.25f);
        Quaternionf expectedDelta = new Quaternionf().rotationY(0.4f);
        Quaternionf current = new Quaternionf(baseline).mul(expectedDelta);

        Quaternionf actual = TaczThirdPersonArmPose.relative(baseline, current);

        assertQuaternionEquals(expectedDelta, actual);
    }

    @Test
    void packetKeepsLeftAndRightXyzwSeparate() {
        Quaternionf left = new Quaternionf().rotationX(0.2f);
        Quaternionf right = new Quaternionf().rotationZ(-0.3f);

        float[] packet = new TaczThirdPersonArmPose(left, right, 0b11).toNativePacket();

        assertEquals(left.x, packet[0], EPSILON);
        assertEquals(left.w, packet[3], EPSILON);
        assertEquals(right.z, packet[6], EPSILON);
        assertEquals(right.w, packet[7], EPSILON);
    }

    @Test
    void invalidQuaternionIsRejected() {
        assertNull(TaczThirdPersonArmPose.relative(
                new Quaternionf(), new Quaternionf(Float.NaN, 0.0f, 0.0f, 1.0f)));
        assertEquals(0, new TaczThirdPersonArmPose(new Quaternionf(0.0f, 0.0f, 0.0f, 0.0f),
                null, 0b01).validMask());
    }

    private static void assertQuaternionEquals(Quaternionf expected, Quaternionf actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
        assertEquals(expected.w, actual.w, EPSILON);
    }
}
