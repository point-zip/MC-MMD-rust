// 负责把模型纹理索引解析为可去重的规范化纹理路径。
package com.shiroha.mmdskin.client.texture;

import java.util.Optional;

@FunctionalInterface
public interface TexturePathResolver {
    Optional<TextureKey> resolve(long modelHandle, int textureIndex);
}
