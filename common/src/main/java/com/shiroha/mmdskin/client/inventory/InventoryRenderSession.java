// 文件职责：隔离物品栏模型的局部绘制队列，并在 GUI 实体边界内完成提交。
package com.shiroha.mmdskin.client.inventory;

import com.shiroha.mmdskin.client.draw.FrameRenderQueue;
import com.shiroha.mmdskin.client.draw.MmdDrawDevice;
import com.shiroha.mmdskin.client.draw.MmdDrawRequest;

import java.util.Objects;

public final class InventoryRenderSession implements AutoCloseable {
    private final FrameRenderQueue queue = new FrameRenderQueue();
    private final MmdDrawDevice drawDevice;
    private int depth;

    public InventoryRenderSession(MmdDrawDevice drawDevice) {
        this.drawDevice = Objects.requireNonNull(drawDevice, "drawDevice");
    }

    public void begin() {
        if (depth == 0) {
            queue.clear();
        }
        depth++;
    }

    public boolean active() {
        return depth > 0;
    }

    public void enqueue(MmdDrawRequest request, AutoCloseable frameLease) {
        if (!active()) {
            throw new IllegalStateException("inventory render session is not active");
        }
        queue.enqueue(request, frameLease);
    }

    public void finish() {
        if (depth == 0) {
            return;
        }
        depth--;
        if (depth == 0) {
            queue.flush(drawDevice);
        }
    }

    public void abort() {
        depth = 0;
        queue.clear();
    }

    @Override
    public void close() {
        abort();
    }
}
