// 负责以规范化绝对路径标识并去重外部模型纹理。
package com.shiroha.mmdskin.client.texture;

import java.nio.file.Path;
import java.util.Objects;

public record TextureKey(Path path) {
    public TextureKey {
        path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    public static TextureKey resolve(Path modelDirectory, String textureReference) {
        Objects.requireNonNull(modelDirectory, "modelDirectory");
        if (textureReference == null || textureReference.isBlank()) {
            throw new IllegalArgumentException("textureReference must not be blank");
        }
        Path reference = Path.of(textureReference.trim());
        return new TextureKey(reference.isAbsolute() ? reference : modelDirectory.resolve(reference));
    }

    public String nativePath() {
        return path.toString();
    }
}
