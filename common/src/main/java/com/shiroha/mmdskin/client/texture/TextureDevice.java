// 负责隔离渲染线程上的纹理创建和 RGBA8 上传。
package com.shiroha.mmdskin.client.texture;

import java.nio.ByteBuffer;
import java.util.Objects;

public interface TextureDevice {
    void assertRenderThread();

    TextureResource createAndUpload(Upload upload);

    record Upload(TextureKey key, int width, int height, boolean hasAlpha, ByteBuffer rgbaPixels) {
        public Upload {
            Objects.requireNonNull(key, "key");
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("texture dimensions must be positive");
            }
            Objects.requireNonNull(rgbaPixels, "rgbaPixels");
            if (!rgbaPixels.isDirect()) {
                throw new IllegalArgumentException("texture upload must use a direct buffer");
            }
            int expectedBytes = Math.toIntExact(Math.multiplyExact(Math.multiplyExact((long) width, height), 4L));
            if (rgbaPixels.remaining() != expectedBytes) {
                throw new IllegalArgumentException(
                        "RGBA8 byte count mismatch: " + rgbaPixels.remaining() + ", expected " + expectedBytes);
            }
            rgbaPixels = rgbaPixels.slice().asReadOnlyBuffer();
        }
    }
}
