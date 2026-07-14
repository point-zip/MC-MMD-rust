// 负责生成使用正常背面剔除绘制 Toon 描边的反向 winding 索引。
package com.shiroha.mmdskin.client.gpu;

import com.shiroha.mmdskin.bridge.render.NativeRenderDataPort;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class OutlineIndexBuilder {
    private OutlineIndexBuilder() {
    }

    public static ByteBuffer reverseTriangles(ByteBuffer source, int indexCount,
                                              NativeRenderDataPort.IndexType indexType) {
        if (indexCount < 0 || indexCount % 3 != 0) {
            throw new IllegalArgumentException("triangle index count must be non-negative and divisible by three");
        }
        int bytes = Math.multiplyExact(indexCount, indexType.bytes());
        if (source == null || source.capacity() < bytes) {
            throw new IllegalArgumentException("source index buffer is too small");
        }
        ByteBuffer input = source.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer output = ByteBuffer.allocateDirect(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int index = 0; index < indexCount; index += 3) {
            copyIndex(input, output, index, index, indexType);
            copyIndex(input, output, index + 2, index + 1, indexType);
            copyIndex(input, output, index + 1, index + 2, indexType);
        }
        output.clear();
        return output;
    }

    private static void copyIndex(ByteBuffer source, ByteBuffer target, int sourceIndex,
                                  int targetIndex, NativeRenderDataPort.IndexType type) {
        int sourceOffset = sourceIndex * type.bytes();
        int targetOffset = targetIndex * type.bytes();
        if (type == NativeRenderDataPort.IndexType.SHORT) {
            target.putShort(targetOffset, source.getShort(sourceOffset));
        } else {
            target.putInt(targetOffset, source.getInt(sourceOffset));
        }
    }
}
