// 负责为纹理资源提供幂等且可追踪的使用租约。
package com.shiroha.mmdskin.client.texture;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TextureLease implements AutoCloseable {
    private final TextureResource resource;
    private final Runnable release;
    private final AtomicBoolean closed = new AtomicBoolean();

    TextureLease(TextureResource resource, Runnable release) {
        this.resource = Objects.requireNonNull(resource, "resource");
        this.release = Objects.requireNonNull(release, "release");
    }

    public TextureResource resource() {
        if (closed.get()) {
            throw new IllegalStateException("texture lease is closed");
        }
        return resource;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            release.run();
        }
    }
}
