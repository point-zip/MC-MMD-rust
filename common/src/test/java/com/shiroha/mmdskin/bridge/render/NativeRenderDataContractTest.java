// 负责验证 native 渲染数据的尺寸计算与绘制命令边界。
package com.shiroha.mmdskin.bridge.render;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeRenderDataContractTest {
    @Test
    void shouldUseTwentyEightByteInterleavedVertices() {
        NativeRenderDataPort.MeshDescription mesh = new NativeRenderDataPort.MeshDescription(
                3, 6, 1, 28, NativeRenderDataPort.IndexType.SHORT, 84L, 12L, 7L);

        assertEquals(84, mesh.vertexBufferBytes());
        assertEquals(12, mesh.indexBufferBytes());
    }

    @Test
    void shouldReadLittleEndianDrawCommandAndClampAlpha() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(NativeDrawCommand.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(2).putInt(6).putInt(12).putInt(3).putFloat(1.5F)
                .putInt(NativeDrawCommand.FLAG_VISIBLE | NativeDrawCommand.FLAG_DOUBLE_SIDED)
                .putFloat(1.0F).putFloat(2.0F).putFloat(3.0F).putInt(0);

        NativeDrawCommand command = NativeDrawCommand.read(buffer, 0);

        assertEquals(2, command.materialId());
        assertEquals(6, command.firstIndex());
        assertEquals(12, command.indexCount());
        assertEquals(1.0F, command.alpha());
        assertTrue(command.visible());
    }

    @Test
    void shouldRejectCommandOutsideBuffer() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(NativeDrawCommand.BYTES);

        assertThrows(IndexOutOfBoundsException.class, () -> NativeDrawCommand.read(buffer, 1));
    }
}
