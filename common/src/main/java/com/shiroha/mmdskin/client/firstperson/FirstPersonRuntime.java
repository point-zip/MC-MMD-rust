// 负责组合第一人称会话、相机计算与本地模型贡献模块。
package com.shiroha.mmdskin.client.firstperson;

import com.shiroha.mmdskin.bridge.runtime.NativeModelPort;
import com.shiroha.mmdskin.client.draw.FrameRenderQueue;
import com.shiroha.mmdskin.client.frame.MmdFrameUpdater;
import com.shiroha.mmdskin.client.model.ModelRepository;
import com.shiroha.mmdskin.player.port.VrRuntimePort;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.Objects;
import java.util.UUID;

public final class FirstPersonRuntime implements AutoCloseable {
    private final FirstPersonSession session;
    private final FirstPersonCamera camera;
    private final LocalPlayerRenderContributor localModels;

    public FirstPersonRuntime(ModelRepository models, MmdFrameUpdater frameUpdater,
                              FrameRenderQueue frameQueue, NativeModelPort modelPort,
                              VrRuntimePort vrRuntime) {
        Objects.requireNonNull(models, "models");
        this.session = new FirstPersonSession(models, modelPort, vrRuntime);
        this.camera = new FirstPersonCamera(session);
        this.localModels = new LocalPlayerRenderContributor(models, frameUpdater, frameQueue, session);
    }

    public FirstPersonSession session() {
        return session;
    }

    public FirstPersonCamera camera() {
        return camera;
    }

    public LocalPlayerRenderContributor localModels() {
        return localModels;
    }

    public boolean defersWorldRoute(UUID entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        return player != null && player.getUUID().equals(entityId) && session.shouldContribute(player);
    }

    public void beginFrame() {
        session.beginFrame();
    }

    public void reset() {
        session.reset();
    }

    @Override
    public void close() {
        session.close();
    }
}
