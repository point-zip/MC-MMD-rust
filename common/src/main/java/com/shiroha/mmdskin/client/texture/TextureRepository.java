// 负责纹理异步解码、渲染线程上传、租约计数及 TTL/LRU 显存回收。
package com.shiroha.mmdskin.client.texture;

import com.shiroha.mmdskin.bridge.texture.NativeTexturePort;
import com.shiroha.mmdskin.client.metrics.TextureMetricsPort;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class TextureRepository implements TextureMetricsPort, AutoCloseable {
    private static final Logger LOGGER = LogManager.getLogger();

    private final NativeTexturePort decoder;
    private final TextureDevice device;
    private final TexturePathResolver pathResolver;
    private final Executor decodeExecutor;
    private final Clock clock;
    private final long timeToLiveMillis;
    private final long retryDelayMillis;
    private final Map<TextureKey, Entry> entries = new ConcurrentHashMap<>();
    private final Queue<Entry> retiredEntries = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile long budgetBytes;
    private volatile long residentBytes;
    private long tickId;

    public TextureRepository(NativeTexturePort decoder, TextureDevice device, TexturePathResolver pathResolver,
                             Executor decodeExecutor,
                             Clock clock, Duration timeToLive, Duration retryDelay, long budgetBytes) {
        this.decoder = Objects.requireNonNull(decoder, "decoder");
        this.device = Objects.requireNonNull(device, "device");
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver");
        this.decodeExecutor = Objects.requireNonNull(decodeExecutor, "decodeExecutor");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.timeToLiveMillis = requireNonNegative(timeToLive, "timeToLive");
        this.retryDelayMillis = requireNonNegative(retryDelay, "retryDelay");
        setBudgetBytes(budgetBytes);
    }

    public Optional<TextureLease> acquire(TextureKey key) {
        Objects.requireNonNull(key, "key");
        ensureOpen();
        while (true) {
            Entry entry = entries.computeIfAbsent(key, ignored -> new Entry(clock.millis()));
            synchronized (entry) {
                if (entry.retired) {
                    entries.remove(key, entry);
                    continue;
                }
                entry.lastAccessMillis = clock.millis();
                if (entry.resource != null) {
                    entry.lastUsedTick = tickId;
                    entry.leases.incrementAndGet();
                    return Optional.of(new TextureLease(entry.resource, () -> release(entry)));
                }
                startDecodeIfAllowed(key, entry);
                return Optional.empty();
            }
        }
    }

    public Optional<TextureLease> acquire(Path modelDirectory, String textureReference) {
        return acquire(TextureKey.resolve(modelDirectory, textureReference));
    }

    public Optional<TextureLease> acquire(long modelHandle, int textureIndex) {
        if (modelHandle <= 0L) {
            throw new IllegalArgumentException("modelHandle must be positive");
        }
        if (textureIndex < 0) {
            throw new IllegalArgumentException("textureIndex must be non-negative");
        }
        Optional<TextureKey> resolved = Objects.requireNonNull(
                pathResolver.resolve(modelHandle, textureIndex), "texture path resolver returned null");
        return resolved.flatMap(this::acquire);
    }

    public void tick() {
        device.assertRenderThread();
        ensureOpen();
        tickId++;
        closeReleasedRetiredEntries();
        entries.forEach(this::completeDecodeIfReady);
        evictExpired();
        evictOverBudget();
    }

    public void invalidate(TextureKey key) {
        Objects.requireNonNull(key, "key");
        device.assertRenderThread();
        Entry entry = entries.remove(key);
        if (entry != null) {
            retireEntry(entry);
        }
    }

    public void setBudgetBytes(long budgetBytes) {
        if (budgetBytes < 0L) {
            throw new IllegalArgumentException("budgetBytes must be non-negative");
        }
        this.budgetBytes = budgetBytes;
    }

    public long budgetBytes() {
        return budgetBytes;
    }

    @Override
    public long estimatedResidentBytes() {
        return residentBytes;
    }

    @Override
    public int residentCount() {
        long active = entries.values().stream().filter(entry -> entry.resource != null).count();
        long retired = retiredEntries.stream().filter(entry -> entry.resource != null).count();
        return Math.toIntExact(active + retired);
    }

    public int pendingCount() {
        return (int) entries.values().stream().filter(entry -> entry.pending != null).count();
    }

    private void startDecodeIfAllowed(TextureKey key, Entry entry) {
        if (entry.pending != null || clock.millis() < entry.retryAfterMillis) {
            return;
        }
        entry.pending = CompletableFuture.supplyAsync(() -> decoder.decode(key.nativePath()), decodeExecutor);
    }

    private void completeDecodeIfReady(TextureKey key, Entry entry) {
        CompletableFuture<NativeTexturePort.DecodedTexture> pending = entry.pending;
        if (pending == null || !pending.isDone()) {
            return;
        }
        synchronized (entry) {
            if (entry.retired || entry.pending != pending) {
                return;
            }
            entry.pending = null;
            TextureResource uploaded = null;
            try {
                NativeTexturePort.DecodedTexture decoded = pending.join();
                uploaded = device.createAndUpload(new TextureDevice.Upload(
                        key, decoded.width(), decoded.height(), decoded.hasAlpha(), decoded.rgbaPixels()));
                validateResource(decoded, uploaded);
                long updatedResidentBytes = Math.addExact(residentBytes, uploaded.estimatedGpuBytes());
                entry.resource = uploaded;
                entry.loadedTick = tickId;
                entry.lastUsedTick = tickId;
                entry.lastAccessMillis = clock.millis();
                residentBytes = updatedResidentBytes;
                uploaded = null;
            } catch (CompletionException exception) {
                entry.retryAfterMillis = clock.millis() + retryDelayMillis;
                LOGGER.warn("纹理解码失败: {}", key.path(), exception.getCause());
            } catch (RuntimeException exception) {
                entry.retryAfterMillis = clock.millis() + retryDelayMillis;
                LOGGER.warn("纹理上传失败: {}", key.path(), exception);
            } finally {
                if (uploaded != null && !uploaded.isClosed()) {
                    uploaded.close();
                }
            }
        }
    }

    private static void validateResource(NativeTexturePort.DecodedTexture decoded, TextureResource resource) {
        Objects.requireNonNull(resource, "texture device returned null");
        if (resource.isClosed()) {
            throw new IllegalStateException("texture device returned a closed resource");
        }
        if (resource.width() != decoded.width() || resource.height() != decoded.height()
                || resource.hasAlpha() != decoded.hasAlpha()
                || resource.estimatedGpuBytes() != decoded.estimatedGpuBytes()) {
            throw new IllegalStateException("texture device returned inconsistent metadata");
        }
    }

    private void evictExpired() {
        long now = clock.millis();
        for (Map.Entry<TextureKey, Entry> candidate : entries.entrySet()) {
            Entry entry = candidate.getValue();
            if (entry.resource == null || entry.loadedTick >= tickId
                    || now - entry.lastAccessMillis < timeToLiveMillis) {
                continue;
            }
            evictIfUnused(candidate.getKey(), entry);
        }
    }

    private void evictOverBudget() {
        if (residentBytes <= budgetBytes) {
            return;
        }
        ArrayList<Map.Entry<TextureKey, Entry>> candidates = new ArrayList<>(entries.entrySet());
        candidates.removeIf(candidate -> {
            Entry entry = candidate.getValue();
            return entry.resource == null || entry.loadedTick >= tickId
                    || entry.lastUsedTick >= tickId - 1L || entry.leases.get() != 0;
        });
        candidates.sort(Comparator.comparingLong(candidate -> candidate.getValue().lastAccessMillis));
        for (Map.Entry<TextureKey, Entry> candidate : candidates) {
            if (residentBytes <= budgetBytes) {
                break;
            }
            evictIfUnused(candidate.getKey(), candidate.getValue());
        }
    }

    private void evictIfUnused(TextureKey key, Entry entry) {
        synchronized (entry) {
            if (entry.retired || entry.resource == null || entry.leases.get() != 0) {
                return;
            }
            if (entries.remove(key, entry)) {
                forceDisposeEntryLocked(entry);
            }
        }
    }

    private void release(Entry entry) {
        entry.leases.updateAndGet(value -> Math.max(0, value - 1));
        entry.lastAccessMillis = clock.millis();
    }

    private void retireEntry(Entry entry) {
        synchronized (entry) {
            if (entry.retired) {
                return;
            }
            entry.retired = true;
            cancelPending(entry);
            if (entry.leases.get() == 0) {
                closeResource(entry);
            } else {
                retiredEntries.add(entry);
            }
        }
    }

    private void forceDisposeEntry(Entry entry) {
        synchronized (entry) {
            forceDisposeEntryLocked(entry);
        }
    }

    private void forceDisposeEntryLocked(Entry entry) {
        entry.retired = true;
        cancelPending(entry);
        closeResource(entry);
    }

    private static void cancelPending(Entry entry) {
        CompletableFuture<NativeTexturePort.DecodedTexture> pending = entry.pending;
        entry.pending = null;
        if (pending != null) {
            pending.cancel(false);
        }
    }

    private void closeResource(Entry entry) {
        TextureResource resource = entry.resource;
        entry.resource = null;
        if (resource != null) {
            residentBytes = Math.max(0L, residentBytes - resource.estimatedGpuBytes());
            resource.close();
        }
    }

    private void closeReleasedRetiredEntries() {
        int candidates = retiredEntries.size();
        for (int index = 0; index < candidates; index++) {
            Entry entry = retiredEntries.poll();
            if (entry == null) {
                return;
            }
            synchronized (entry) {
                if (entry.leases.get() == 0) {
                    closeResource(entry);
                } else {
                    retiredEntries.add(entry);
                }
            }
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("texture repository is closed");
        }
    }

    private static long requireNonNegative(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
        return duration.toMillis();
    }

    @Override
    public void close() {
        device.assertRenderThread();
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        entries.values().forEach(this::forceDisposeEntry);
        entries.clear();
        retiredEntries.forEach(this::forceDisposeEntry);
        retiredEntries.clear();
    }

    private static final class Entry {
        private final AtomicInteger leases = new AtomicInteger();
        private volatile CompletableFuture<NativeTexturePort.DecodedTexture> pending;
        private volatile TextureResource resource;
        private volatile long lastAccessMillis;
        private volatile long retryAfterMillis;
        private volatile long loadedTick;
        private volatile long lastUsedTick = Long.MIN_VALUE;
        private volatile boolean retired;

        private Entry(long nowMillis) {
            this.lastAccessMillis = nowMillis;
        }
    }
}
