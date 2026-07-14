// 负责定义受检 native 网格、帧顶点、绘制命令和纹理复制 Interface。
package com.shiroha.mmdskin.bridge.render;

import java.nio.ByteBuffer;
import java.util.Optional;

public interface NativeRenderDataPort {
    int INTERLEAVED_VERTEX_STRIDE = 28;
    int GPU_SKINNING_VERTEX_STRIDE = 36;
    int MAX_PALETTE_BONES = 48;

    MeshDescription describeMesh(long modelHandle);

    int copyIndices(long modelHandle, ByteBuffer target);

    int copyFrameVertices(long modelHandle, ByteBuffer target);

    int copyDrawCommands(long modelHandle, ByteBuffer target);

    default int copyBonePaletteMatrices(long modelHandle, ByteBuffer target) { return 0; }

    default Optional<String> resolveTexturePath(long modelHandle, int textureIndex) {
        return Optional.empty();
    }

    TextureDescription decodeTexture(String normalizedPath);

    int copyTexturePixels(long textureHandle, ByteBuffer target);

    void releaseTexture(long textureHandle);

    record MeshDescription(
            int vertexCount,
            int indexCount,
            int drawCommandCount,
            int vertexStride,
            IndexType indexType,
            long vertexBytes,
            long indexBytes,
            long vertexRevision,
            boolean gpuSkinningSupported,
            boolean gpuSkinningActive,
            int drawCommandStride,
            int paletteEntryCount,
            int maxPaletteBones,
            long paletteBytes,
            long poseRevision,
            long materialRevision) {
        public MeshDescription {
            if (vertexCount < 0 || indexCount < 0 || drawCommandCount < 0 || vertexRevision < 0L
                    || poseRevision < 0L || materialRevision < 0L || paletteEntryCount < 0) {
                throw new IllegalArgumentException("mesh counts must be non-negative");
            }
            int expectedStride = gpuSkinningActive ? GPU_SKINNING_VERTEX_STRIDE : INTERLEAVED_VERTEX_STRIDE;
            if (vertexStride != expectedStride) {
                throw new IllegalArgumentException("unsupported vertex stride: " + vertexStride);
            }
            if (gpuSkinningActive && !gpuSkinningSupported || drawCommandStride != NativeDrawCommand.BYTES
                    || maxPaletteBones != MAX_PALETTE_BONES) {
                throw new IllegalArgumentException("inconsistent GPU skinning descriptor");
            }
            if (indexType == null) {
                throw new NullPointerException("indexType");
            }
            if (vertexBytes != Math.multiplyExact((long) vertexCount, vertexStride)
                    || indexBytes != Math.multiplyExact((long) indexCount, indexType.bytes())) {
                throw new IllegalArgumentException("native mesh byte counts do not match counts");
            }
            if (paletteBytes != Math.multiplyExact((long) paletteEntryCount, 16L * Float.BYTES)) {
                throw new IllegalArgumentException("native palette byte count is inconsistent");
            }
        }

        public MeshDescription(int vertexCount, int indexCount, int drawCommandCount,
                               int vertexStride, IndexType indexType, long vertexBytes,
                               long indexBytes, long revision) {
            this(vertexCount, indexCount, drawCommandCount, vertexStride, indexType,
                    vertexBytes, indexBytes, revision, false, false,
                    NativeDrawCommand.BYTES, 0, MAX_PALETTE_BONES, 0L, revision, revision);
        }

        public int vertexBufferBytes() {
            return Math.toIntExact(vertexBytes);
        }

        public int indexBufferBytes() {
            return Math.toIntExact(indexBytes);
        }

        public int paletteBufferBytes() {
            return Math.toIntExact(paletteBytes);
        }
    }

    record TextureDescription(long handle, int width, int height, long byteCount, boolean hasAlpha) {
        public TextureDescription {
            if (handle <= 0L || width <= 0 || height <= 0 || byteCount <= 0L) {
                throw new IllegalArgumentException("texture handle and dimensions must be positive");
            }
            if (byteCount != Math.multiplyExact(Math.multiplyExact((long) width, height), 4L)) {
                throw new IllegalArgumentException("texture byte count must describe RGBA8 pixels");
            }
        }

        public int rgbaBytes() {
            return Math.toIntExact(byteCount);
        }
    }

    enum IndexType {
        SHORT(Short.BYTES),
        INT(Integer.BYTES);

        private final int bytes;

        IndexType(int bytes) {
            this.bytes = bytes;
        }

        public int bytes() {
            return bytes;
        }
    }
}
