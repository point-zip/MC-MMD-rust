package com.shiroha.mmdskin.compat.tacz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

/** 验证第三人称绝对姿态和 JNI 数据布局。 */
class TaczThirdPersonArmPoseTest {
    private static final float EPSILON = 1.0e-5f;

    @Test
    void identityIsAValidAbsoluteArmPose() {
        TaczThirdPersonArmPose pose = new TaczThirdPersonArmPose(
                new Quaternionf(), new Quaternionf(), 0b11);

        assertTrue(pose.isValid());
        assertEquals(0b11, pose.validMask());
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
        TaczThirdPersonArmPose pose = new TaczThirdPersonArmPose(
                new Quaternionf(Float.NaN, 0.0f, 0.0f, 1.0f), null, 0b01);
        assertEquals(0, pose.validMask());
        assertEquals(0, new TaczThirdPersonArmPose(new Quaternionf(0.0f, 0.0f, 0.0f, 0.0f),
                null, 0b01).validMask());
    }
}
