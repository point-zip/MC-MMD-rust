/** 文件职责：完成 NeoForge 客户端配置、运行时与平台事件入口初始化。 */
package com.shiroha.mmdskin.neoforge;

import com.shiroha.mmdskin.neoforge.compat.YsmCompat;

import com.google.common.reflect.TypeToken;
import com.shiroha.mmdskin.MmdSkin;
import com.shiroha.mmdskin.MmdSkinClient;
import com.shiroha.mmdskin.neoforge.config.MmdSkinConfig;
import com.shiroha.mmdskin.neoforge.register.MmdSkinRegisterClient;
import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import com.shiroha.mmdskin.client.frame.MmdRenderSnapshot;
import com.shiroha.mmdskin.client.frame.MmdSnapshotFactory;
import com.shiroha.mmdskin.client.gpu.MmdRenderPipelines;
import com.shiroha.mmdskin.neoforge.render.NeoForgeSnapshotKeys;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD, modid = MmdSkin.MOD_ID)
public final class MmdSkinNeoForgeClient {
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        MmdSkinRegisterClient.onRegisterKeyMappings(event);
    }

    @SubscribeEvent
    public static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        MmdSkinRegisterClient.onRegisterEntityRenderers(event);
    }

    @SubscribeEvent
    public static void onRegisterRenderPipelines(RegisterRenderPipelinesEvent event) {
        MmdRenderPipelines.all().forEach(event::registerPipeline);
        MmdRenderPipelines.activateRegisteredPipelines();
    }

    @SubscribeEvent
    public static void onRegisterRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(
                new TypeToken<LivingEntityRenderer<LivingEntity, LivingEntityRenderState,
                        ?>>() {},
                (entity, state) -> {
                    state.setRenderData(NeoForgeSnapshotKeys.SNAPSHOT, null);
                    if (YsmCompat.isYsmActive(entity)) {
                        return;
                    }
                    MmdRenderSnapshot.Context context = MmdClientRenderRuntime.currentIfInstalled()
                            .filter(runtime -> runtime.inventory().active())
                            .map(runtime -> MmdRenderSnapshot.Context.INVENTORY)
                            .orElse(MmdRenderSnapshot.Context.WORLD);
                    state.setRenderData(NeoForgeSnapshotKeys.SNAPSHOT,
                            MmdSnapshotFactory.capture(entity, state, context));
                });
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        MmdSkinConfig.init();
        MmdSkinClient.initClient();
        MmdSkinRegisterClient.Register();
    }
}
