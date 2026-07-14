// 负责隔离只读 OpenGL 显存扩展探测并清理查询错误状态。
package com.shiroha.mmdskin.bridge.graphics;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL46C;

public final class OpenGlMemoryProbe {
    private static final int GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX = 0x9048;
    private static final int GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX = 0x9049;
    private static final int TEXTURE_FREE_MEMORY_ATI = 0x87FC;

    private OpenGlMemoryProbe() {
    }

    public static MemorySample sample() {
        try {
            String vendor = GL11.glGetString(GL11.GL_VENDOR);
            if (vendor == null) {
                return MemorySample.unavailable();
            }
            String normalized = vendor.toLowerCase();
            if (normalized.contains("nvidia")) {
                return new MemorySample(
                        kilobytesToBytes(GL46C.glGetInteger(GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX)),
                        kilobytesToBytes(GL46C.glGetInteger(GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX)));
            }
            if (normalized.contains("amd") || normalized.contains("ati")) {
                int[] values = new int[4];
                GL46C.glGetIntegerv(TEXTURE_FREE_MEMORY_ATI, values);
                return new MemorySample(0L, kilobytesToBytes(values[0]));
            }
            return MemorySample.unavailable();
        } catch (RuntimeException exception) {
            return MemorySample.unavailable();
        } finally {
            clearErrors();
        }
    }

    private static long kilobytesToBytes(int kilobytes) {
        return kilobytes > 0 ? (long) kilobytes * 1024L : 0L;
    }

    private static void clearErrors() {
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
            // 查询扩展不应污染后续渲染命令的错误状态。
        }
    }

    public record MemorySample(long totalBytes, long availableBytes) {
        public static MemorySample unavailable() {
            return new MemorySample(0L, 0L);
        }
    }
}

