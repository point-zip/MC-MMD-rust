// 负责验证 SHORT/INT 描边索引的三角形 winding 反转。
package com.shiroha.mmdskin.client.gpu;

import com.shiroha.mmdskin.bridge.render.NativeRenderDataPort;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OutlineIndexBuilderTest {
    @Test
    void shouldReverseShortTriangleWindingWithoutChangingTriangleRanges() {
        ByteBuffer source = ByteBuffer.allocateDirect(12).order(ByteOrder.LITTLE_ENDIAN);
        for (short value : new short[]{0, 1, 2, 3, 4, 5}) {
            source.putShort(value);
        }

        ByteBuffer reversed = OutlineIndexBuilder.reverseTriangles(
                source, 6, NativeRenderDataPort.IndexType.SHORT);

        assertEquals(0, reversed.getShort(0));
        assertEquals(2, reversed.getShort(2));
        assertEquals(1, reversed.getShort(4));
        assertEquals(3, reversed.getShort(6));
        assertEquals(5, reversed.getShort(8));
        assertEquals(4, reversed.getShort(10));
    }

    @Test
    void shouldReverseIntTriangleWinding() {
        ByteBuffer source = ByteBuffer.allocateDirect(12).order(ByteOrder.LITTLE_ENDIAN);
        source.putInt(70_000).putInt(80_000).putInt(90_000);

        ByteBuffer reversed = OutlineIndexBuilder.reverseTriangles(
                source, 3, NativeRenderDataPort.IndexType.INT);

        assertEquals(70_000, reversed.getInt(0));
        assertEquals(90_000, reversed.getInt(4));
        assertEquals(80_000, reversed.getInt(8));
    }

    @Test
    void shouldRejectNonTriangleIndexCount() {
        ByteBuffer source = ByteBuffer.allocateDirect(4);
        assertThrows(IllegalArgumentException.class, () -> OutlineIndexBuilder.reverseTriangles(
                source, 2, NativeRenderDataPort.IndexType.SHORT));
    }
}
