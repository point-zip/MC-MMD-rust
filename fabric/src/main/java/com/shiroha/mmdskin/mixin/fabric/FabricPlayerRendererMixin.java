// 负责在 Fabric Living renderer 入口路由 MMD 主体并保留基类绘制。
package com.shiroha.mmdskin.mixin.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.client.draw.EntityBaseRenderBridge;
import com.shiroha.mmdskin.client.draw.MmdRenderRouter;
import com.shiroha.mmdskin.client.frame.MmdRenderStateExtension;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class FabricPlayerRendererMixin
        extends EntityRenderer<LivingEntity, LivingEntityRenderState>
        implements EntityBaseRenderBridge {

    protected FabricPlayerRendererMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    @Inject(
            method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void mmdskin$routeBody(LivingEntityRenderState state, PoseStack poseStack,
                                   MultiBufferSource buffers, int packedLight, CallbackInfo callback) {
        if (!(state instanceof MmdRenderStateExtension extension)) {
            return;
        }
        MmdRenderRouter.Result result = state instanceof PlayerRenderState playerState
                ? MmdRenderRouter.routePlayer(extension.mmdskin$snapshot(), playerState,
                        poseStack, buffers, packedLight, false)
                : MmdRenderRouter.route(extension.mmdskin$snapshot(), poseStack, packedLight, false);
        if (result == MmdRenderRouter.Result.FALLTHROUGH) {
            return;
        }
        mmdskin$renderEntityBase(state, poseStack, buffers, packedLight);
        callback.cancel();
    }

    @Override
    public void mmdskin$renderEntityBase(EntityRenderState state, PoseStack poseStack,
                                         MultiBufferSource buffers, int packedLight) {
        super.render((LivingEntityRenderState) state, poseStack, buffers, packedLight);
    }
}
