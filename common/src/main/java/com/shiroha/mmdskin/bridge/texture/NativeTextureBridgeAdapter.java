// 负责将纹理解码、像素复制和 native handle 释放收口为一次事务。
package com.shiroha.mmdskin.bridge.texture;

import com.shiroha.mmdskin.bridge.render.NativeRenderDataPort;

import java.nio.ByteBuffer;
import java.util.Objects;

public final class NativeTextureBridgeAdapter implements NativeTexturePort {
    private final NativeRenderDataPort renderData;

    public NativeTextureBridgeAdapter(NativeRenderDataPort renderData) {
        this.renderData = Objects.requireNonNull(renderData, "renderData");
    }

    @Override
    public DecodedTexture decode(String normalizedPath) {
        if (normalizedPath == null || normalizedPath.isBlank()) {
            throw new IllegalArgumentException("texture path must not be blank");
        }
        NativeRenderDataPort.TextureDescription description = renderData.decodeTexture(normalizedPath);
        try (TextureRelease ignored = () -> renderData.releaseTexture(description.handle())) {
            ByteBuffer pixels = ByteBuffer.allocateDirect(description.rgbaBytes());
            int written = renderData.copyTexturePixels(description.handle(), pixels);
            if (written != description.rgbaBytes()) {
                throw new IllegalStateException(
                        "copyTexturePixels wrote " + written + " bytes, expected " + description.rgbaBytes());
            }
            pixels.position(0).limit(written);
            return new DecodedTexture(description.width(), description.height(), description.hasAlpha(), pixels);
        }
    }

    @FunctionalInterface
    private interface TextureRelease extends AutoCloseable {
        @Override
        void close();
    }
}
