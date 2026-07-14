// 负责定义 NeoForge RenderState 使用的 MMD 快照 ContextKey。
package com.shiroha.mmdskin.neoforge.render;

import com.shiroha.mmdskin.client.frame.MmdRenderSnapshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.context.ContextKey;

public final class NeoForgeSnapshotKeys {
    public static final ContextKey<MmdRenderSnapshot> SNAPSHOT = new ContextKey<>(
            ResourceLocation.fromNamespaceAndPath("mmdskin", "render_snapshot"));

    private NeoForgeSnapshotKeys() {
    }
}
