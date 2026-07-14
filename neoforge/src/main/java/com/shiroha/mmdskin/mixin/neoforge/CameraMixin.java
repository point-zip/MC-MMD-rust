// 负责把 NeoForge 相机入口适配到舞台相机与实例化第一人称相机模块。
package com.shiroha.mmdskin.mixin.neoforge;

import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import com.shiroha.mmdskin.neoforge.compat.YsmCompat;
import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(
            method = "setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V",
            at = @At("TAIL"))
    private void onSetup(BlockGetter level, Entity entity, boolean detached, boolean mirrored,
                         float partialTick, CallbackInfo callback) {
        MMDCameraController stageCamera = MMDCameraController.getInstance();
        if (stageCamera.isActive()) {
            stageCamera.checkEscapeKey();
            if (stageCamera.isActive()) {
                stageCamera.updateCamera();
                if (stageCamera.isActive()) {
                    setPosition(stageCamera.getCameraX(), stageCamera.getCameraY(), stageCamera.getCameraZ());
                    setRotation(stageCamera.getCameraYaw(), stageCamera.getCameraPitch());
                }
            }
            return;
        }

        if (entity instanceof LivingEntity living
                && YsmCompat.isYsmModelActive(living)
                && !YsmCompat.isDisableSelfModel()) {
            return;
        }
        var runtime = MmdClientRenderRuntime.currentIfInstalled().orElse(null);
        if (runtime == null) {
            return;
        }
        runtime.firstPerson().camera()
                .resolve(entity, partialTick, detached)
                .ifPresent(pose -> {
                    setPosition(pose.position().x, pose.position().y, pose.position().z);
                    if (pose.applyRotation()) {
                        setRotation(pose.yaw(), pose.pitch());
                    }
                });
    }
}
