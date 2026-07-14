// 负责通过受检 Native Render Data Interface 解析模型纹理路径。
package com.shiroha.mmdskin.client.texture;

import com.shiroha.mmdskin.bridge.render.NativeRenderDataPort;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public final class NativeTexturePathResolver implements TexturePathResolver {
    private final NativeRenderDataPort nativeData;

    public NativeTexturePathResolver(NativeRenderDataPort nativeData) {
        this.nativeData = Objects.requireNonNull(nativeData, "nativeData");
    }

    @Override
    public Optional<TextureKey> resolve(long modelHandle, int textureIndex) {
        return nativeData.resolveTexturePath(modelHandle, textureIndex)
                .map(Path::of)
                .map(TextureKey::new);
    }
}
