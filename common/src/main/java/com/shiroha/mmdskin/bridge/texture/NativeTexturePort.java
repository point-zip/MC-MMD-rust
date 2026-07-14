// 负责定义已解码 RGBA8 纹理数据的 native 边界。
package com.shiroha.mmdskin.bridge.texture;

import java.nio.ByteBuffer;
import java.util.Objects;

@FunctionalInterface
public interface NativeTexturePort {
    DecodedTexture decode(String normalizedPath);

    record DecodedTexture(int width, int height, boolean hasAlpha, ByteBuffer rgbaPixels) {
        public DecodedTexture {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("texture dimensions must be positive");
            }
            Objects.requireNonNull(rgbaPixels, "rgbaPixels");
            if (!rgbaPixels.isDirect()) {
                throw new IllegalArgumentException("texture pixels must use a direct buffer");
            }
            int expectedBytes = Math.toIntExact(Math.multiplyExact(Math.multiplyExact((long) width, height), 4L));
            if (rgbaPixels.remaining() != expectedBytes) {
                throw new IllegalArgumentException(
                        "RGBA8 byte count mismatch: " + rgbaPixels.remaining() + ", expected " + expectedBytes);
            }
            rgbaPixels = rgbaPixels.slice().asReadOnlyBuffer();
        }

        public long estimatedGpuBytes() {
            return Math.multiplyExact(Math.multiplyExact((long) width, height), 4L);
        }
    }
}
