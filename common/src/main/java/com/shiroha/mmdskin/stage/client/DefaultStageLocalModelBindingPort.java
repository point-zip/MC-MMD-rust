// 负责把本地玩家 Model Instance 租约适配为舞台播放绑定。
package com.shiroha.mmdskin.stage.client;

import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import com.shiroha.mmdskin.client.model.ModelKey;
import com.shiroha.mmdskin.player.model.PlayerModelResolver;
import com.shiroha.mmdskin.player.runtime.MmdSkinRendererPlayerHelper;
import com.shiroha.mmdskin.stage.client.playback.port.StageLocalModelBindingPort;
import com.shiroha.mmdskin.ui.config.ModelSelectorConfig;
import net.minecraft.client.Minecraft;

public final class DefaultStageLocalModelBindingPort implements StageLocalModelBindingPort {
    public static final DefaultStageLocalModelBindingPort INSTANCE = new DefaultStageLocalModelBindingPort();

    private DefaultStageLocalModelBindingPort() {
    }

    @Override
    public StageLocalModelBinding bindLocalModel(long mergedAnim) {
        Minecraft mc = StageClientContext.minecraft();
        if (mc.player == null) {
            return StageLocalModelBinding.empty();
        }

        String modelName = ModelSelectorConfig.getInstance().getSelectedModel();
        if (modelName == null || modelName.isEmpty()) {
            return StageLocalModelBinding.empty();
        }

        ModelKey key = new ModelKey(modelName, PlayerModelResolver.getCacheKey(mc.player), ModelKey.Usage.ENTITY);
        var lease = MmdClientRenderRuntime.current().acquire(key).orElse(null);
        if (lease == null) {
            return StageLocalModelBinding.empty();
        }
        try (lease) {
            long modelHandle = lease.instance().handle();
            MmdSkinRendererPlayerHelper.startStageAnimation(lease.instance(), mergedAnim);
            return new StageLocalModelBinding(modelHandle, modelName);
        }
    }
}
