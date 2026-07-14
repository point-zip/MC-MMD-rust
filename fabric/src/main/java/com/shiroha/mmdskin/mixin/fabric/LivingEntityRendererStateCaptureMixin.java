// 负责在 Fabric 完整实体状态提取返回点创建 MMD Render Snapshot。
package com.shiroha.mmdskin.mixin.fabric;

import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import com.shiroha.mmdskin.client.frame.MmdRenderSnapshot;
import com.shiroha.mmdskin.client.frame.MmdRenderStateExtension;
import com.shiroha.mmdskin.client.frame.MmdSnapshotFactory;
import com.shiroha.mmdskin.fabric.compat.YsmCompat;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class LivingEntityRendererStateCaptureMixin {
    @Inject(method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;", at = @At("RETURN"))
    private void mmdskin$captureSnapshot(Entity entity, float partialTick,
                                         CallbackInfoReturnable<EntityRenderState> callback) {
        EntityRenderState state = callback.getReturnValue();
        if (!(state instanceof MmdRenderStateExtension extension)) {
            return;
        }
        extension.mmdskin$clearSnapshot();
        if (!(entity instanceof LivingEntity living) || !(state instanceof LivingEntityRenderState livingState)) {
            return;
        }
        if (living instanceof Player player && YsmCompat.isYsmActive(player)) {
            return;
        }
        MmdRenderSnapshot.Context context = MmdClientRenderRuntime.currentIfInstalled()
                .filter(runtime -> runtime.inventory().active())
                .map(runtime -> MmdRenderSnapshot.Context.INVENTORY)
                .orElse(MmdRenderSnapshot.Context.WORLD);
        extension.mmdskin$setSnapshot(MmdSnapshotFactory.capture(living, livingState, context));
    }
}
