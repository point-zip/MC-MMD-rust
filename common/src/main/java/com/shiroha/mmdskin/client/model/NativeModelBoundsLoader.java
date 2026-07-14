// 负责从 ABI 4 当前交错顶点快照一次性计算模型局部 bounds。
package com.shiroha.mmdskin.client.model;

import com.shiroha.mmdskin.bridge.render.NativeRenderDataPort;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

public final class NativeModelBoundsLoader implements ModelBoundsLoader {
    private final NativeRenderDataPort nativeData;

    public NativeModelBoundsLoader(NativeRenderDataPort nativeData) {
        this.nativeData = Objects.requireNonNull(nativeData, "nativeData");
    }

    @Override
    public ModelBounds load(long modelHandle) {
        if (modelHandle <= 0L) {
            throw new IllegalArgumentException("modelHandle must be positive");
        }
        NativeRenderDataPort.MeshDescription description = nativeData.describeMesh(modelHandle);
        if (description.vertexCount() == 0) {
            return null;
        }
        ByteBuffer vertices = ByteBuffer.allocateDirect(description.vertexBufferBytes())
                .order(ByteOrder.LITTLE_ENDIAN);
        int written = nativeData.copyFrameVertices(modelHandle, vertices);
        if (written != description.vertexBufferBytes()) {
            throw new IllegalStateException(
                    "copyFrameVertices wrote " + written + " bytes, expected "
                            + description.vertexBufferBytes());
        }
        return calculateBounds(vertices, description.vertexCount(), description.vertexStride());
    }

    static ModelBounds calculateBounds(ByteBuffer vertices, int vertexCount, int vertexStride) {
        Objects.requireNonNull(vertices, "vertices");
        if (vertexCount <= 0 || vertexStride < Float.BYTES * 3
                || (long) vertexCount * vertexStride > vertices.capacity()) {
            throw new IllegalArgumentException("invalid interleaved vertex range");
        }
        ByteBuffer source = vertices.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (int index = 0; index < vertexCount; index++) {
            int offset = index * vertexStride;
            float x = source.getFloat(offset);
            float y = source.getFloat(offset + Float.BYTES);
            float z = source.getFloat(offset + Float.BYTES * 2);
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
                continue;
            }
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
        if (!Float.isFinite(minX)) {
            return null;
        }
        return new ModelBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
