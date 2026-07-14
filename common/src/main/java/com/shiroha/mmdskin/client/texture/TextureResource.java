// 负责描述仓储拥有且可确定释放的后端纹理资源。
package com.shiroha.mmdskin.client.texture;

public interface TextureResource extends AutoCloseable {
    int width();

    int height();

    boolean hasAlpha();

    long estimatedGpuBytes();

    boolean isClosed();

    @Override
    void close();
}
