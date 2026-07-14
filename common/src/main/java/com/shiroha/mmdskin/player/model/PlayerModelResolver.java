// 负责把玩家模型选择解析为带租约的 Model Instance。
package com.shiroha.mmdskin.player.model;

import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import com.shiroha.mmdskin.client.model.MmdModelInstance;
import com.shiroha.mmdskin.client.model.ModelKey;
import com.shiroha.mmdskin.client.model.ModelLease;
import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * 玩家模型解析工具。
 */
public final class PlayerModelResolver {

    private PlayerModelResolver() {
    }

    public record Result(ModelLease modelLease, String playerName) implements AutoCloseable {
        public MmdModelInstance model() {
            return modelLease.instance();
        }

        @Override
        public void close() {
            modelLease.close();
        }
    }

    public static String getCacheKey(Player player) {
        if (player == null) return "unknown";

        String uuid = player.getStringUUID();
        if (uuid != null && !uuid.isEmpty()) {
            return uuid;
        }

        return player.getName().getString();
    }

    public static Result resolve(Player player) {
        if (player == null) return null;

        String playerName = player.getName().getString();
        Minecraft mc = Minecraft.getInstance();
        boolean isLocalPlayer = mc.player != null && mc.player.getUUID().equals(player.getUUID());
        String selectedModel = PlayerModelSyncManager.getPlayerModel(player.getUUID(), playerName, isLocalPlayer);

        if (selectedModel == null || selectedModel.isEmpty()
                || selectedModel.equals(UIConstants.DEFAULT_MODEL_NAME)) {
            return null;
        }

        ModelKey key = new ModelKey(selectedModel, getCacheKey(player), ModelKey.Usage.ENTITY);
        return MmdClientRenderRuntime.current().acquire(key)
                .map(lease -> new Result(lease, playerName))
                .orElse(null);
    }
}
