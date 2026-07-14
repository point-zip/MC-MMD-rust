// 负责解释 native 批量输出的固定宽度材质绘制命令。
package com.shiroha.mmdskin.bridge.render;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public record NativeDrawCommand(
        int materialId,
        int firstIndex,
        int indexCount,
        float alpha,
        int textureIndex,
        int flags,
        float centerX,
        float centerY,
        float centerZ,
        int materialColor,
        int paletteOffset,
        int paletteCount) {
    public static final int BYTES = 48;
    public static final int FLAG_VISIBLE = 1;
    public static final int FLAG_DOUBLE_SIDED = 1 << 1;
    public static final int FLAG_TRANSPARENT = 1 << 2;
    public static final int FLAG_OUTLINE = 1 << 3;

    public NativeDrawCommand {
        if (materialId < 0 || firstIndex < 0 || indexCount < 0 || textureIndex < -1
                || !Float.isFinite(alpha) || !Float.isFinite(centerX)
                || !Float.isFinite(centerY) || !Float.isFinite(centerZ)
                || paletteOffset < 0 || paletteCount < 0 || paletteCount > NativeRenderDataPort.MAX_PALETTE_BONES) {
            throw new IllegalArgumentException("invalid native draw command");
        }
        alpha = Math.clamp(alpha, 0.0F, 1.0F);
    }

    public NativeDrawCommand(int materialId, int firstIndex, int indexCount, float alpha,
                             int textureIndex, int flags, float centerX, float centerY,
                             float centerZ, int materialColor) {
        this(materialId, firstIndex, indexCount, alpha, textureIndex, flags,
                centerX, centerY, centerZ, materialColor, 0, 0);
    }

    public boolean visible() {
        return (flags & FLAG_VISIBLE) != 0 && alpha > 0.0F;
    }

    public static NativeDrawCommand read(ByteBuffer source, int commandIndex) {
        if (source == null || commandIndex < 0) {
            throw new IllegalArgumentException("source and commandIndex must be valid");
        }
        int offset = Math.multiplyExact(commandIndex, BYTES);
        if (offset > source.capacity() - BYTES) {
            throw new IndexOutOfBoundsException("draw command exceeds source capacity");
        }
        ByteBuffer view = source.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        return new NativeDrawCommand(
                view.getInt(offset),
                view.getInt(offset + 4),
                view.getInt(offset + 8),
                view.getFloat(offset + 16),
                view.getInt(offset + 12),
                view.getInt(offset + 20),
                view.getFloat(offset + 24),
                view.getFloat(offset + 28),
                view.getFloat(offset + 32),
                view.getInt(offset + 36),
                view.getInt(offset + 40),
                view.getInt(offset + 44));
    }
}
