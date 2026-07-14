// 负责在 Fabric 可复用 Living RenderState 上保存并清空不可变快照。
package com.shiroha.mmdskin.mixin.fabric;

import com.shiroha.mmdskin.client.frame.MmdRenderSnapshot;
import com.shiroha.mmdskin.client.frame.MmdRenderStateExtension;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements MmdRenderStateExtension {
    @Unique
    private MmdRenderSnapshot mmdskin$snapshot;

    @Override
    public MmdRenderSnapshot mmdskin$snapshot() {
        return mmdskin$snapshot;
    }

    @Override
    public void mmdskin$setSnapshot(MmdRenderSnapshot snapshot) {
        mmdskin$snapshot = snapshot;
    }
}
