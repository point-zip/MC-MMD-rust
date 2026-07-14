/* 文件职责：注册 Fabric 客户端生命周期、HUD 与按键运行时钩子。 */
package com.shiroha.mmdskin.fabric.register;

import com.shiroha.mmdskin.bonesync.BoneSyncManager;
import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import com.mojang.blaze3d.systems.RenderSystem;
import com.shiroha.mmdskin.client.gpu.MmdRenderPipelines;
import com.shiroha.mmdskin.compat.iris.IrisCompatibility;
import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.debug.client.PerformanceHud;
import com.shiroha.mmdskin.fabric.network.MmdSkinNetworkPack;
import com.shiroha.mmdskin.fabric.compat.YsmCompat;
import com.shiroha.mmdskin.player.runtime.MmdSkinRendererPlayerHelper;
import com.shiroha.mmdskin.stage.application.StageSessionService;
import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
import com.shiroha.mmdskin.stage.client.sync.StageAnimSyncHelper;
import com.shiroha.mmdskin.ui.QuickModelSwitcher;
import com.shiroha.mmdskin.ui.config.ModelSelectorConfig;
import com.shiroha.mmdskin.ui.network.NetworkOpCode;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import com.shiroha.mmdskin.ui.wheel.ConfigWheelScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

final class FabricClientRuntimeHooks {
    private final KeyMapping keyConfigWheel;
    private final KeyMapping[] keyQuickModels;

    private boolean configWheelKeyWasDown;

    FabricClientRuntimeHooks(KeyMapping keyConfigWheel, KeyMapping[] keyQuickModels) {
        this.keyConfigWheel = keyConfigWheel;
        this.keyQuickModels = keyQuickModels;
    }

    void register(Minecraft minecraft) {
        ClientTickEvents.END_CLIENT_TICK.register(client -> onClientTick(minecraft));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> onJoin(client)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onDisconnect());
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> PerformanceHud.render(graphics));
        registerWorldRendering();
        registerPipelineReload();
    }

    private static void registerWorldRendering() {
        WorldRenderEvents.START.register(context -> MmdClientRenderRuntime.current().beginFrame(
                context.tickCounter().getGameTimeDeltaTicks()));
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (IrisCompatibility.isShadowPass()) {
                MmdClientRenderRuntime.current().frameQueue().clear();
                return;
            }
            MmdClientRenderRuntime runtime = MmdClientRenderRuntime.current();
            LocalPlayer player = Minecraft.getInstance().player;
            float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(false);
            runtime.firstPerson().localModels().contribute(
                    Minecraft.getInstance(), context.camera(), partialTick,
                    runtime.frameId(), runtime.frameDeltaSeconds(),
                    player != null && YsmCompat.isYsmActive(player),
                    context.matrixStack(), context.consumers());
            var camera = context.camera().getPosition();
            runtime.flushWorldFrame(
                    camera.x, camera.y, camera.z, LightTexture.FULL_BRIGHT);
        });
    }

    private static void registerPipelineReload() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
                            "mmdskin", "render_pipelines");

                    @Override
                    public ResourceLocation getFabricId() {
                        return ID;
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager resourceManager) {
                        MmdRenderPipelines.deactivate();
                        Minecraft minecraft = Minecraft.getInstance();
                        MmdRenderPipelines.validateAndActivate(
                                RenderSystem.getDevice(), minecraft.getShaderManager()::getShader);
                    }
                });
    }

    private void onClientTick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        MmdClientRenderRuntime.current().tick();
        StageAnimSyncHelper.tickPending();
        BoneSyncManager.tickLocal();

        if (!player.isAlive()) {
            MMDCameraController controller = MMDCameraController.getInstance();
            if (controller.isInStageMode()) {
                controller.exitStageMode();
            }
        }

        if (minecraft.screen == null || minecraft.screen instanceof ConfigWheelScreen) {
            boolean keyDown = keyConfigWheel.isDown();
            if (keyDown && !configWheelKeyWasDown) {
                minecraft.setScreen(new ConfigWheelScreen(keyConfigWheel));
            }
            configWheelKeyWasDown = keyDown;
        } else {
            configWheelKeyWasDown = false;
        }

        if (minecraft.screen == null) {
            for (int i = 0; i < keyQuickModels.length; i++) {
                while (keyQuickModels[i].consumeClick()) {
                    QuickModelSwitcher.switchToSlot(i);
                }
            }
        }
    }

    private void onJoin(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        String selectedModel = ModelSelectorConfig.getInstance().getPlayerModel(player.getName().getString());
        if (selectedModel != null
            && !selectedModel.isEmpty()
            && !selectedModel.equals(UIConstants.DEFAULT_MODEL_NAME)) {
            PlayerModelSyncManager.broadcastLocalModelSelection(player.getUUID(), selectedModel);
        }
        MmdSkinNetworkPack.sendToServer(NetworkOpCode.REQUEST_ALL_MODELS, player.getUUID(), "");
    }

    private void onDisconnect() {
        MMDCameraController.getInstance().exitStageMode();
        MmdClientRenderRuntime.current().firstPerson().reset();
        PlayerModelSyncManager.onDisconnect();
        MmdSkinRendererPlayerHelper.onDisconnect();
        BoneSyncManager.onDisconnect();
        StageSessionService.getInstance().onDisconnect();
    }
}
