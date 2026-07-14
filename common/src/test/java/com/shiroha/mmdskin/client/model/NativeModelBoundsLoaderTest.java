// 负责验证 ABI 3 交错顶点 bounds 提取与无效顶点过滤。
package com.shiroha.mmdskin.client.model;

import com.shiroha.mmdskin.bridge.render.NativeRenderDataPort;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NativeModelBoundsLoaderTest {
    @Test
    void shouldReadPositionFieldsFromInterleavedVerticesOnce() {
        FakeRenderData nativeData = new FakeRenderData(new float[][]{
                {-2.0F, 1.0F, 3.0F},
                {4.0F, -5.0F, 0.5F},
                {Float.NaN, 100.0F, 100.0F}
        });

        ModelBounds bounds = new NativeModelBoundsLoader(nativeData).load(7L);

        assertEquals(new ModelBounds(-2.0F, -5.0F, 0.5F, 4.0F, 1.0F, 3.0F), bounds);
        assertEquals(1, nativeData.describeCalls);
        assertEquals(1, nativeData.copyCalls);
    }

    @Test
    void shouldReturnNoBoundsWhenNoFiniteVertexExists() {
        ByteBuffer vertices = ByteBuffer.allocateDirect(NativeRenderDataPort.INTERLEAVED_VERTEX_STRIDE)
                .order(ByteOrder.LITTLE_ENDIAN);
        vertices.putFloat(0, Float.NaN);
        vertices.putFloat(4, Float.POSITIVE_INFINITY);
        vertices.putFloat(8, 0.0F);

        assertNull(NativeModelBoundsLoader.calculateBounds(
                vertices, 1, NativeRenderDataPort.INTERLEAVED_VERTEX_STRIDE));
    }

    private static final class FakeRenderData implements NativeRenderDataPort {
        private final ByteBuffer vertices;
        private final int vertexCount;
        private int describeCalls;
        private int copyCalls;

        private FakeRenderData(float[][] positions) {
            vertexCount = positions.length;
            vertices = ByteBuffer.allocateDirect(vertexCount * INTERLEAVED_VERTEX_STRIDE)
                    .order(ByteOrder.LITTLE_ENDIAN);
            for (int index = 0; index < positions.length; index++) {
                int offset = index * INTERLEAVED_VERTEX_STRIDE;
                vertices.putFloat(offset, positions[index][0]);
                vertices.putFloat(offset + 4, positions[index][1]);
                vertices.putFloat(offset + 8, positions[index][2]);
            }
        }

        @Override
        public MeshDescription describeMesh(long modelHandle) {
            describeCalls++;
            return new MeshDescription(
                    vertexCount, 0, 0, INTERLEAVED_VERTEX_STRIDE, IndexType.SHORT,
                    vertices.capacity(), 0L, 0L);
        }

        @Override
        public int copyIndices(long modelHandle, ByteBuffer target) {
            return 0;
        }

        @Override
        public int copyFrameVertices(long modelHandle, ByteBuffer target) {
            copyCalls++;
            target.clear();
            target.put(vertices.duplicate().clear());
            return vertices.capacity();
        }

        @Override
        public int copyDrawCommands(long modelHandle, ByteBuffer target) {
            return 0;
        }

        @Override
        public TextureDescription decodeTexture(String normalizedPath) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int copyTexturePixels(long textureHandle, ByteBuffer target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void releaseTexture(long textureHandle) {
            throw new UnsupportedOperationException();
        }
    }
}
