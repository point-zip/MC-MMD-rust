// 负责提供进程内唯一的 Native Render Data Adapter。
package com.shiroha.mmdskin.bridge;

import com.shiroha.mmdskin.bridge.render.NativeRenderDataPort;

public final class NativeRenderDataPorts {
    private static final NativeRenderDataPort PORT = new JniNativeRenderDataAdapter();

    private NativeRenderDataPorts() {
    }

    public static NativeRenderDataPort renderDataPort() {
        return PORT;
    }
}

