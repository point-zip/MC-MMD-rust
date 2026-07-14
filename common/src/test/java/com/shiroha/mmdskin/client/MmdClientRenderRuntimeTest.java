// 负责验证 Minecraft tick delta 到 native 秒增量的稳定换算。
package com.shiroha.mmdskin.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MmdClientRenderRuntimeTest {
    @Test
    void shouldConvertPerFrameDeltaTicksToSeconds() {
        assertEquals(0.05F, MmdClientRenderRuntime.deltaTicksToSeconds(1.0F), 0.000001F);
        assertEquals(1.0F / 60.0F,
                MmdClientRenderRuntime.deltaTicksToSeconds(1.0F / 3.0F), 0.000001F);
    }

    @Test
    void shouldRejectInvalidOrClampStalledFrameDelta() {
        assertEquals(0.0F, MmdClientRenderRuntime.deltaTicksToSeconds(Float.NaN));
        assertEquals(0.0F, MmdClientRenderRuntime.deltaTicksToSeconds(-1.0F));
        assertEquals(0.25F, MmdClientRenderRuntime.deltaTicksToSeconds(20.0F));
    }
}
