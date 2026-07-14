// 负责加载 native 库并在运行时安装前强校验渲染 ABI。
package com.shiroha.mmdskin.bridge;

import com.shiroha.mmdskin.NativeLibraryLoader;

public final class NativeBridgeBootstrap {
    public static final int REQUIRED_ABI_VERSION = 4;

    private NativeBridgeBootstrap() {
    }

    public static void verifyAbi() {
        NativeLibraryLoader.load();
        String version = NativeBindings.GetVersion();
        if (!NativeLibraryLoader.LIBRARY_VERSION.equals(version)) {
            throw new UnsatisfiedLinkError("MMD native 版本不匹配: Java="
                    + NativeLibraryLoader.LIBRARY_VERSION + ", native=" + version);
        }
        int actual = NativeBindings.getAbiVersion();
        if (actual != REQUIRED_ABI_VERSION) {
            throw new UnsatisfiedLinkError("MMD native ABI 不匹配: Java="
                    + REQUIRED_ABI_VERSION + ", native=" + actual);
        }
    }
}
