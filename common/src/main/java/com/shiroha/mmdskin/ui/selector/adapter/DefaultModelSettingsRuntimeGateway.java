// 负责把已保存的模型设置应用到当前本地 Model Instance。
package com.shiroha.mmdskin.ui.selector.adapter;

import com.shiroha.mmdskin.bridge.NativePortAdapters;
import com.shiroha.mmdskin.bridge.runtime.NativeModelPort;
import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import com.shiroha.mmdskin.client.model.ModelKey;
import com.shiroha.mmdskin.config.ModelConfigData;
import com.shiroha.mmdskin.player.model.PlayerModelResolver;
import com.shiroha.mmdskin.ui.config.ModelSelectorConfig;
import com.shiroha.mmdskin.ui.selector.port.ModelSettingsRuntimeGateway;
import net.minecraft.client.Minecraft;

public class DefaultModelSettingsRuntimeGateway implements ModelSettingsRuntimeGateway {
    private final NativeModelPort nativeModels = NativePortAdapters.model();

    @Override
    public void applyConfigIfSelected(String modelName, ModelConfigData config) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        String selectedModel = ModelSelectorConfig.getInstance().getSelectedModel();
        if (!modelName.equals(selectedModel)) {
            return;
        }

        ModelKey key = new ModelKey(selectedModel, PlayerModelResolver.getCacheKey(minecraft.player), ModelKey.Usage.ENTITY);
        var lease = MmdClientRenderRuntime.current().acquire(key).orElse(null);
        if (lease == null) {
            return;
        }
        try (lease) {
            var model = lease.instance();
            model.applyModelConfig(config);
            long handle = model.handle();
            nativeModels.setEyeTrackingEnabled(handle, config.eyeTrackingEnabled);
            nativeModels.setEyeMaxAngle(handle, config.eyeMaxAngle);
        }
    }
}
