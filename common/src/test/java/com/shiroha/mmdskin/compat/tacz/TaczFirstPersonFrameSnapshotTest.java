package com.shiroha.mmdskin.compat.tacz;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaczFirstPersonFrameSnapshotTest {
    @AfterEach
    void cleanUp() {
        TaczFirstPersonFrameSnapshot.clearCurrentThreadFrame();
    }

    @Test
    void shouldCopyMatricesAndClearStateWhenFrameFinishes() {
        Object player = new Object();
        Object stack = new Object();
        Matrix4f pose = new Matrix4f().translate(1, 2, 3);
        Matrix3f normal = new Matrix3f();
        Matrix4f entryPose = new Matrix4f().translate(4, 5, 6);

        TaczFirstPersonFrameSnapshot.beginFrame(player, stack, entryPose, true);
        assertTrue(TaczFirstPersonFrameSnapshot.captureHand(player, TaczFirstPersonFrameSnapshot.Hand.RIGHT, pose, normal, true));
        pose.m30(99);
        entryPose.m30(99);

        var snapshot = TaczFirstPersonFrameSnapshot.finishFrame(true);
        assertTrue(snapshot.isPresent());
        assertEquals(4.0f, snapshot.get().entryPose().m30());
        assertEquals(1.0f, snapshot.get().capturedHand(TaczFirstPersonFrameSnapshot.Hand.RIGHT).orElseThrow().pose().m30());
        var matrices = snapshot.get().consume(player, stack, TaczFirstPersonFrameSnapshot.Hand.RIGHT);
        assertTrue(matrices.isPresent());
        assertEquals(1.0f, matrices.get().pose().m30());
        assertFalse(snapshot.get().consume(player, stack, TaczFirstPersonFrameSnapshot.Hand.RIGHT).isPresent());
        assertFalse(TaczFirstPersonFrameSnapshot.shouldSuppressOriginalArm(player, TaczFirstPersonFrameSnapshot.Hand.RIGHT, true));
        assertFalse(TaczFirstPersonFrameSnapshot.finishFrame(true).isPresent());
    }

    @Test
    void shouldRejectWrongPlayerNonFiniteMatrixAndOffRenderThread() {
        Object player = new Object();
        TaczFirstPersonFrameSnapshot.beginFrame(player, new Object(), new Matrix4f(), true);
        assertFalse(TaczFirstPersonFrameSnapshot.captureHand(new Object(), TaczFirstPersonFrameSnapshot.Hand.LEFT, new Matrix4f(), new Matrix3f(), true));
        assertFalse(TaczFirstPersonFrameSnapshot.captureHand(player, TaczFirstPersonFrameSnapshot.Hand.LEFT, new Matrix4f().m00(Float.NaN), new Matrix3f(), true));
        assertFalse(TaczFirstPersonFrameSnapshot.captureHand(player, TaczFirstPersonFrameSnapshot.Hand.LEFT, new Matrix4f(), new Matrix3f(), false));
        assertFalse(TaczFirstPersonFrameSnapshot.shouldSuppressOriginalArm(player, TaczFirstPersonFrameSnapshot.Hand.LEFT, true));
    }

    @Test
    void shouldRejectConsumptionForAnotherPlayerOrGun() {
        Object player = new Object();
        Object stack = new Object();
        TaczFirstPersonFrameSnapshot.beginFrame(player, stack, new Matrix4f(), true);
        assertTrue(TaczFirstPersonFrameSnapshot.captureHand(player, TaczFirstPersonFrameSnapshot.Hand.LEFT, new Matrix4f(), new Matrix3f(), true));

        var snapshot = TaczFirstPersonFrameSnapshot.finishFrame(true).orElseThrow();
        assertFalse(snapshot.consume(new Object(), stack, TaczFirstPersonFrameSnapshot.Hand.LEFT).isPresent());
        assertTrue(snapshot.consume(player, stack, TaczFirstPersonFrameSnapshot.Hand.LEFT).isPresent());
    }

    @Test
    void shouldDiscardAnUnfinishedPreviousFrameBeforeStartingAnother() {
        Object player = new Object();
        TaczFirstPersonFrameSnapshot.beginFrame(player, new Object(), new Matrix4f(), true);
        assertTrue(TaczFirstPersonFrameSnapshot.captureHand(player, TaczFirstPersonFrameSnapshot.Hand.LEFT, new Matrix4f(), new Matrix3f(), true));

        TaczFirstPersonFrameSnapshot.beginFrame(player, new Object(), new Matrix4f(), true);
        assertFalse(TaczFirstPersonFrameSnapshot.shouldSuppressOriginalArm(player, TaczFirstPersonFrameSnapshot.Hand.LEFT, true));
        assertTrue(TaczFirstPersonFrameSnapshot.finishFrame(true).isPresent());
    }

    @Test
    void shouldKeepStrictIdentityFallbackForNonMinecraftTestTokens() {
        Object token = new Object();
        assertTrue(TaczFirstPersonFrameSnapshot.matchesGunStack(token, token));
        assertFalse(TaczFirstPersonFrameSnapshot.matchesGunStack(token, new Object()));
    }
}
