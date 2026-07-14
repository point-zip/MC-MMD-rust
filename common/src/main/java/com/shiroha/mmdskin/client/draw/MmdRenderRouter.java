// 负责决定 MMD 接管或原版回退并把模型提交到 Frame Queue。
package com.shiroha.mmdskin.client.draw;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import com.shiroha.mmdskin.client.frame.MmdRenderSnapshot;
import com.shiroha.mmdskin.client.frame.ModelTransform;
import com.shiroha.mmdskin.client.gpu.MmdRenderPipelines;
import com.shiroha.mmdskin.compat.iris.IrisCompatibility;
import com.shiroha.mmdskin.config.ConfigManager;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.joml.Matrix4f;

public final class MmdRenderRouter {
    private MmdRenderRouter() {
    }

    public static Result route(MmdRenderSnapshot extractedSnapshot, PoseStack poseStack,
                               int packedLight, boolean compatibilityConflict) {
        return route(extractedSnapshot, poseStack, null, null, packedLight, compatibilityConflict);
    }

    public static Result routePlayer(MmdRenderSnapshot extractedSnapshot, PlayerRenderState playerState,
                                     PoseStack poseStack, MultiBufferSource buffers,
                                     int packedLight, boolean compatibilityConflict) {
        return route(extractedSnapshot, poseStack, playerState, buffers, packedLight, compatibilityConflict);
    }

    private static Result route(MmdRenderSnapshot extractedSnapshot, PoseStack poseStack,
                                PlayerRenderState playerState, MultiBufferSource buffers,
                                int packedLight, boolean compatibilityConflict) {
        if (extractedSnapshot == null || compatibilityConflict || IrisCompatibility.isShadowPass()
                || !MmdRenderPipelines.isReady()) {
            return Result.FALLTHROUGH;
        }
        if (!extractedSnapshot.rendersBody() && !extractedSnapshot.glowing()) {
            return Result.FALLTHROUGH;
        }

        MmdClientRenderRuntime runtime = MmdClientRenderRuntime.current();
        var lease = runtime.acquire(extractedSnapshot.modelKey()).orElse(null);
        if (lease == null) {
            return Result.FALLTHROUGH;
        }
        if (runtime.firstPerson().defersWorldRoute(extractedSnapshot.entityId())) {
            lease.close();
            return Result.DEFERRED_LOCAL;
        }
        boolean queued = false;
        try {
            var model = lease.instance();
            Matrix4f modelMatrix = new Matrix4f(poseStack.last().pose())
                    .rotateY((float) Math.toRadians(-extractedSnapshot.pose().bodyYaw()));
            MmdRenderSnapshot snapshot = extractedSnapshot.withRenderTransform(
                    ModelTransform.from(modelMatrix), packedLight);
            runtime.frameUpdater().updateOnce(
                    runtime.frameId(), model, snapshot, runtime.frameDeltaSeconds());
            PipelineVariant pipeline = choosePipeline(snapshot);
            MmdDrawRequest request = new MmdDrawRequest(
                    model, snapshot, pipeline,
                    snapshot.visibility() == MmdRenderSnapshot.Visibility.TRANSLUCENT);
            if (snapshot.context() == MmdRenderSnapshot.Context.INVENTORY) {
                runtime.inventory().enqueue(request, lease);
            } else {
                runtime.frameQueue().enqueue(request, lease);
            }
            queued = true;
            if (playerState != null && buffers != null) {
                MmdHeldItemRenderer.render(playerState, model, poseStack, buffers,
                        packedLight, snapshot.pose().bodyYaw());
            }
            return Result.QUEUED;
        } finally {
            if (!queued) {
                lease.close();
            }
        }
    }

    private static PipelineVariant choosePipeline(MmdRenderSnapshot snapshot) {
        if (snapshot.glowing() && !snapshot.rendersBody()) {
            return PipelineVariant.GLOWING;
        }
        if (ConfigManager.isToonRenderingEnabled()) {
            return PipelineVariant.TOON;
        }
        return snapshot.visibility() == MmdRenderSnapshot.Visibility.TRANSLUCENT
                ? PipelineVariant.TRANSLUCENT
                : PipelineVariant.OPAQUE;
    }

    public enum Result {
        FALLTHROUGH,
        QUEUED,
        DEFERRED_LOCAL
    }
}
