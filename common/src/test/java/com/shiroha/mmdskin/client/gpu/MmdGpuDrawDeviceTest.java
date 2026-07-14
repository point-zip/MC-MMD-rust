// 负责验证纹理 Alpha 与材质透明管线的分类不会引入深度闪烁。
package com.shiroha.mmdskin.client.gpu;

import com.shiroha.mmdskin.bridge.render.NativeDrawCommand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MmdGpuDrawDeviceTest {
    @Test
    void shouldKeepOpaqueMaterialInDepthWritingPipeline() {
        NativeDrawCommand opaque = command(NativeDrawCommand.FLAG_VISIBLE);

        assertFalse(MmdGpuDrawDevice.requiresTransparentPipeline(false, opaque));
    }

    @Test
    void shouldUseTransparentPipelineOnlyForExplicitVisibilityOrMaterialAlpha() {
        NativeDrawCommand transparent = command(
                NativeDrawCommand.FLAG_VISIBLE | NativeDrawCommand.FLAG_TRANSPARENT);

        assertTrue(MmdGpuDrawDevice.requiresTransparentPipeline(false, transparent));
        assertTrue(MmdGpuDrawDevice.requiresTransparentPipeline(true, command(NativeDrawCommand.FLAG_VISIBLE)));
    }

    private static NativeDrawCommand command(int flags) {
        return new NativeDrawCommand(0, 0, 3, 1.0F, 0, flags,
                0.0F, 0.0F, 0.0F, 0xFFFFFFFF);
    }
}
