// 负责在本地模型选择完成后同步网络状态并失效旧实例。
package com.shiroha.mmdskin.ui.selector.adapter;

import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import com.shiroha.mmdskin.player.model.PlayerModelResolver;
import com.shiroha.mmdskin.ui.network.ModelSelectorNetworkHandler;
import com.shiroha.mmdskin.ui.selector.port.ModelSelectionRuntimeGateway;
import net.minecraft.client.Minecraft;

public class DefaultModelSelectionRuntimeGateway implements ModelSelectionRuntimeGateway {
    @Override
    public void afterLocalModelSelection(String modelName) {
        ModelSelectorNetworkHandler.getInstance().syncModelSelection(modelName);

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        MmdClientRenderRuntime.current().models()
                .invalidateOwner(PlayerModelResolver.getCacheKey(minecraft.player));
    }
}
