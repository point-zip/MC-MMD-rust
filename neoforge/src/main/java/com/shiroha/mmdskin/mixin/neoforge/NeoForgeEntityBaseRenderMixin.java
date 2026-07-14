// 负责向 NeoForge RenderLivingEvent Adapter 暴露 EntityRenderer 基类绘制。
package com.shiroha.mmdskin.mixin.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.client.draw.EntityBaseRenderBridge;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntityRenderer.class)
public abstract class NeoForgeEntityBaseRenderMixin
        extends EntityRenderer<LivingEntity, LivingEntityRenderState>
        implements EntityBaseRenderBridge {
    protected NeoForgeEntityBaseRenderMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void mmdskin$renderEntityBase(EntityRenderState state, PoseStack poseStack,
                                         MultiBufferSource buffers, int packedLight) {
        super.render((LivingEntityRenderState) state, poseStack, buffers, packedLight);
    }
}
