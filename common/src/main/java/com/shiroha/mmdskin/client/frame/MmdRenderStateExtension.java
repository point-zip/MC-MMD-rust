// 负责在可复用原版 RenderState 上存取并显式清空 MMD 快照。
package com.shiroha.mmdskin.client.frame;

import org.jetbrains.annotations.Nullable;

public interface MmdRenderStateExtension {
    @Nullable
    MmdRenderSnapshot mmdskin$snapshot();

    void mmdskin$setSnapshot(@Nullable MmdRenderSnapshot snapshot);

    default void mmdskin$clearSnapshot() {
        mmdskin$setSnapshot(null);
    }
}

