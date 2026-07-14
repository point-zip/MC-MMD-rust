// 负责枚举 1.21.5 MMD 绘制所需的不可变 pipeline 变体。
package com.shiroha.mmdskin.client.draw;

public enum PipelineVariant {
    OPAQUE,
    OPAQUE_DOUBLE_SIDED,
    TRANSLUCENT,
    TRANSLUCENT_DOUBLE_SIDED,
    TOON,
    TOON_DOUBLE_SIDED,
    TOON_TRANSLUCENT,
    TOON_TRANSLUCENT_DOUBLE_SIDED,
    TOON_OUTLINE,
    GLOWING
}
