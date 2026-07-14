// 负责用 NeoForge 事件提取快照、路由主体并保留 EntityRenderer 基类逻辑。
package com.shiroha.mmdskin.neoforge.render;

import com.shiroha.mmdskin.client.draw.EntityBaseRenderBridge;
import com.shiroha.mmdskin.client.draw.MmdRenderRouter;
import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import com.shiroha.mmdskin.client.frame.MmdRenderSnapshot;
import com.shiroha.mmdskin.compat.iris.IrisCompatibility;
import com.shiroha.mmdskin.neoforge.compat.YsmCompat;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class NeoForgeRenderAdapter {
    @SubscribeEvent
    public void onLivingPre(RenderLivingEvent.Pre<?, ?, ?> event) {
        route(event.getRenderState(), event, event);
    }

    @SubscribeEvent
    public void onPlayerPre(RenderPlayerEvent.Pre event) {
        route(event.getRenderState(), event, event);
    }

    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            MmdClientRenderRuntime.current().beginFrame(
                    event.getPartialTick().getGameTimeDeltaTicks());
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        if (IrisCompatibility.isShadowPass()) {
            MmdClientRenderRuntime.current().frameQueue().clear();
            return;
        }
        MmdClientRenderRuntime runtime = MmdClientRenderRuntime.current();
        var player = Minecraft.getInstance().player;
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        runtime.firstPerson().localModels().contribute(
                Minecraft.getInstance(), event.getCamera(), partialTick,
                runtime.frameId(), runtime.frameDeltaSeconds(),
                player != null && YsmCompat.isYsmActive(player),
                event.getPoseStack(), Minecraft.getInstance().renderBuffers().bufferSource());
        var camera = event.getCamera().getPosition();
        runtime.flushWorldFrame(
                camera.x, camera.y, camera.z, LightTexture.FULL_BRIGHT);
    }

    private static void route(LivingEntityRenderState state, RenderLivingEvent<?, ?, ?> event,
                              ICancellableEvent cancellable) {
        MmdRenderSnapshot snapshot = state.getRenderData(NeoForgeSnapshotKeys.SNAPSHOT);
        MmdRenderRouter.Result result = state instanceof PlayerRenderState playerState
                ? MmdRenderRouter.routePlayer(snapshot, playerState, event.getPoseStack(),
                        event.getMultiBufferSource(), event.getPackedLight(), false)
                : MmdRenderRouter.route(snapshot, event.getPoseStack(), event.getPackedLight(), false);
        if (result == MmdRenderRouter.Result.FALLTHROUGH) {
            return;
        }
        state.setRenderData(NeoForgeSnapshotKeys.SNAPSHOT, null);
        if (event.getRenderer() instanceof EntityBaseRenderBridge bridge) {
            bridge.mmdskin$renderEntityBase(state, event.getPoseStack(),
                    event.getMultiBufferSource(), event.getPackedLight());
        }
        cancellable.setCanceled(true);
    }
}
