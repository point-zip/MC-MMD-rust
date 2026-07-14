// 负责协调玩家模型的自定义动画、舞台动画与重置行为。
package com.shiroha.mmdskin.player.runtime;

import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import com.shiroha.mmdskin.client.model.MmdModelInstance;
import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.player.animation.AnimationStateManager;
import com.shiroha.mmdskin.player.animation.PendingAnimSignalCache;
import com.shiroha.mmdskin.player.model.PlayerModelResolver;
import com.shiroha.mmdskin.stage.client.sync.StageAnimSyncHelper;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 玩家动画控制辅助类。
 */
public final class MmdSkinRendererPlayerHelper {
    private static final float STAGE_TRANSITION_TIME = 0.3f;

    public static boolean isUsingMmdModel(Player player) {
        if (player == null) return false;
        String playerName = player.getName().getString();
        Minecraft mc = Minecraft.getInstance();
        boolean isLocalPlayer = mc.player != null && mc.player.getUUID().equals(player.getUUID());
        String selectedModel = PlayerModelSyncManager.getPlayerModel(player.getUUID(), playerName, isLocalPlayer);
        return selectedModel != null && !selectedModel.isEmpty() && !selectedModel.equals(UIConstants.DEFAULT_MODEL_NAME);
    }

    private MmdSkinRendererPlayerHelper() {
    }

    public static void ResetPhysics(Player player) {
        try (PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(player)) {
            if (resolved != null) {
                resetModelAnimationState(player, resolved.model());
            }
        }
    }

    public static void CustomAnim(Player player, String id) {
        try (PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(player)) {
            if (resolved == null) return;

            MmdModelInstance model = resolved.model();
            model.animationState().playCustomAnim = true;
            model.animationState().invalidateStateLayers();
            model.changeAnimation(animation(model, id), 0);
            model.setLayerLoop(1, true);
            model.changeAnimation(0, 1);
            model.changeAnimation(0, 2);
        }
    }

    public static void startStageAnimation(MmdModelInstance model, long animHandle) {
        if (model == null || animHandle == 0) return;

        clearOverlayLayers(model);
        model.resetPhysics();
        model.animationState().invalidateStateLayers();
        model.transitionAnimation(animHandle, 0, STAGE_TRANSITION_TIME);
        model.animationState().playCustomAnim = true;
        model.animationState().playStageAnim = true;
    }

    public static void resetModelAnimationState(MmdModelInstance model) {
        resetModelAnimationState(null, model);
    }

    public static void resetModelAnimationState(Player player, MmdModelInstance model) {
        if (model == null) return;

        model.animationState().playCustomAnim = false;
        model.animationState().playStageAnim = false;
        model.changeAnimation(animation(model, "idle"), 0);
        clearOverlayLayers(model);
        model.resetPhysics();
        model.animationState().invalidateStateLayers();

        if (player instanceof AbstractClientPlayer clientPlayer) {
            AnimationStateManager.updateAnimationState(clientPlayer, model);
        }
    }

    public static void suppressDefaultAnimationState(MmdModelInstance model) {
        if (model == null) {
            return;
        }

        model.animationState().playCustomAnim = false;
        model.animationState().playStageAnim = false;
        model.changeAnimation(0, 0);
        clearOverlayLayers(model);
        model.resetPhysics();
        model.animationState().invalidateStateLayers();
    }

    private static void clearOverlayLayers(MmdModelInstance model) {
        model.setLayerLoop(1, true);
        model.changeAnimation(0, 1);
        model.changeAnimation(0, 2);
    }

    private static long animation(MmdModelInstance model, String name) {
        return MmdClientRenderRuntime.current().animations().animationFor(model, name);
    }

    public static void onDisconnect() {
        StageAnimSyncHelper.onDisconnect();
        PendingAnimSignalCache.onDisconnect();
    }
}
