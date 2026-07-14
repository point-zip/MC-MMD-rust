// 负责为 Model Instance 提供幂等、可追踪的使用租约。
package com.shiroha.mmdskin.client.model;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ModelLease implements AutoCloseable {
    private final MmdModelInstance instance;
    private final Runnable release;
    private final AtomicBoolean closed = new AtomicBoolean();

    ModelLease(MmdModelInstance instance, Runnable release) {
        this.instance = Objects.requireNonNull(instance, "instance");
        this.release = Objects.requireNonNull(release, "release");
    }

    public MmdModelInstance instance() {
        if (closed.get()) {
            throw new IllegalStateException("model lease is closed");
        }
        return instance;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            release.run();
        }
    }
}

