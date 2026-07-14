// 负责让加载器 Adapter 显式调用 EntityRenderer 基类的名称与拴绳绘制。
package com.shiroha.mmdskin.client.draw;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

public interface EntityBaseRenderBridge {
    void mmdskin$renderEntityBase(EntityRenderState state, PoseStack poseStack,
                                  MultiBufferSource buffers, int packedLight);
}

