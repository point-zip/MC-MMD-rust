// 负责向 GPU Draw Adapter 暴露不含 GPU handle 的模型绘制身份。
package com.shiroha.mmdskin.client.draw;

public interface DrawModelRef {
    long modelInstanceId();

    long nativeHandle();

    long nativeRevision();

    default float renderScale() {
        return 1.0F;
    }
}
