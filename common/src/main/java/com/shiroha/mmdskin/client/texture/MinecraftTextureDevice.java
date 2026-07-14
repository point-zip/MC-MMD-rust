// 负责使用 Minecraft 1.21.5 GpuTexture API 创建并上传 RGBA8 纹理。
package com.shiroha.mmdskin.client.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

public final class MinecraftTextureDevice implements TextureDevice {
    @Override
    public void assertRenderThread() {
        RenderSystem.assertOnRenderThread();
    }

    @Override
    public TextureResource createAndUpload(Upload upload) {
        assertRenderThread();
        GpuTexture texture = RenderSystem.getDevice().createTexture(
                () -> "MMD texture " + upload.key().nativePath(),
                TextureFormat.RGBA8,
                upload.width(),
                upload.height(),
                1);
        try {
            texture.setTextureFilter(FilterMode.LINEAR, false);
            ByteBuffer bytes = upload.rgbaPixels().duplicate().order(ByteOrder.nativeOrder());
            IntBuffer pixels = bytes.asIntBuffer();
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(
                    texture, pixels, NativeImage.Format.RGBA, 0, 0, 0, upload.width(), upload.height());
            return new MinecraftTextureResource(texture, upload.width(), upload.height(), upload.hasAlpha());
        } catch (RuntimeException exception) {
            texture.close();
            throw exception;
        }
    }
}
