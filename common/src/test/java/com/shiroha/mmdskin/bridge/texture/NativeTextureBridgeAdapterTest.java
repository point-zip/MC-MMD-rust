// 负责验证 native 纹理 handle 在复制成功或失败时都能确定释放。
package com.shiroha.mmdskin.bridge.texture;

import com.shiroha.mmdskin.bridge.render.NativeRenderDataPort;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeTextureBridgeAdapterTest {
    @Test
    void shouldCopyRgba8PixelsAndReleaseNativeHandle() {
        FakeRenderDataPort renderData = new FakeRenderDataPort(8);
        NativeTextureBridgeAdapter adapter = new NativeTextureBridgeAdapter(renderData);

        NativeTexturePort.DecodedTexture decoded = adapter.decode("C:\\models\\body.png");

        assertEquals(2, decoded.width());
        assertEquals(1, decoded.height());
        assertEquals(8, decoded.rgbaPixels().remaining());
        assertTrue(decoded.rgbaPixels().isReadOnly());
        assertEquals(1, renderData.releases.get());
    }

    @Test
    void shouldReleaseNativeHandleWhenCopyWritesTooFewBytes() {
        FakeRenderDataPort renderData = new FakeRenderDataPort(4);
        NativeTextureBridgeAdapter adapter = new NativeTextureBridgeAdapter(renderData);

        assertThrows(IllegalStateException.class, () -> adapter.decode("C:\\models\\body.png"));
        assertEquals(1, renderData.releases.get());
    }

    private static final class FakeRenderDataPort implements NativeRenderDataPort {
        private final int bytesWritten;
        private final AtomicInteger releases = new AtomicInteger();

        private FakeRenderDataPort(int bytesWritten) {
            this.bytesWritten = bytesWritten;
        }

        @Override
        public MeshDescription describeMesh(long modelHandle) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int copyIndices(long modelHandle, ByteBuffer target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int copyFrameVertices(long modelHandle, ByteBuffer target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int copyDrawCommands(long modelHandle, ByteBuffer target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TextureDescription decodeTexture(String normalizedPath) {
            return new TextureDescription(7L, 2, 1, 8L, true);
        }

        @Override
        public int copyTexturePixels(long textureHandle, ByteBuffer target) {
            for (int index = 0; index < bytesWritten; index++) {
                target.put(index, (byte) index);
            }
            return bytesWritten;
        }

        @Override
        public void releaseTexture(long textureHandle) {
            releases.incrementAndGet();
        }
    }
}
