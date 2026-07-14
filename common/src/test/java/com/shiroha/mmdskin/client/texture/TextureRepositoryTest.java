// 负责验证纹理仓储的去重、渲染线程上传、租约和显存回收语义。
package com.shiroha.mmdskin.client.texture;

import com.shiroha.mmdskin.bridge.texture.NativeTexturePort;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextureRepositoryTest {
    @Test
    void shouldNormalizePathsAndDecodeOnlyOnce() {
        MutableClock clock = new MutableClock();
        CountingDecoder decoder = new CountingDecoder();
        FakeTextureDevice device = new FakeTextureDevice();
        TextureRepository repository = repository(decoder, device, clock, Duration.ofMinutes(1), 1024L);
        Path modelDirectory = Path.of("build", "models", "test");
        TextureKey canonical = TextureKey.resolve(modelDirectory, "textures/body.png");
        TextureKey alias = TextureKey.resolve(modelDirectory, "textures/../textures/body.png");

        assertEquals(canonical, alias);
        assertTrue(repository.acquire(canonical).isEmpty());
        assertTrue(repository.acquire(alias).isEmpty());
        assertEquals(1, decoder.calls.get());
        assertEquals(0, device.uploads.get(), "completed CPU work must wait for a render-thread tick");

        repository.tick();
        try (TextureLease first = repository.acquire(canonical).orElseThrow();
             TextureLease second = repository.acquire(alias).orElseThrow()) {
            assertSame(first.resource(), second.resource());
        }

        assertEquals(1, device.uploads.get());
        assertEquals(1, repository.residentCount());
        repository.close();
    }

    @Test
    void shouldResolveModelTextureIndexThroughInjectedPort() {
        MutableClock clock = new MutableClock();
        CountingDecoder decoder = new CountingDecoder();
        FakeTextureDevice device = new FakeTextureDevice();
        TextureKey expected = new TextureKey(Path.of("build", "resolved", "body.png"));
        TexturePathResolver resolver = (modelHandle, textureIndex) ->
                modelHandle == 17L && textureIndex == 3 ? Optional.of(expected) : Optional.empty();
        TextureRepository repository = new TextureRepository(
                decoder, device, resolver, Runnable::run, clock,
                Duration.ofMinutes(1), Duration.ofSeconds(1), 1024L);

        assertTrue(repository.acquire(17L, 3).isEmpty());
        repository.tick();
        try (TextureLease ignored = repository.acquire(17L, 3).orElseThrow()) {
            assertEquals(expected.nativePath(), decoder.lastPath);
        }
        assertTrue(repository.acquire(17L, 4).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> repository.acquire(0L, 0));
        assertThrows(IllegalArgumentException.class, () -> repository.acquire(1L, -1));
        repository.close();
    }

    @Test
    void shouldKeepLeasedTexturePastTtlAndEvictAfterRelease() {
        MutableClock clock = new MutableClock();
        FakeTextureDevice device = new FakeTextureDevice();
        TextureRepository repository = repository(
                new CountingDecoder(), device, clock, Duration.ofMillis(100), 1024L);
        TextureKey key = new TextureKey(Path.of("build", "texture", "leased.png"));

        repository.acquire(key);
        repository.tick();
        TextureLease lease = repository.acquire(key).orElseThrow();
        FakeTextureResource resource = (FakeTextureResource) lease.resource();

        clock.advance(Duration.ofMillis(200));
        repository.tick();
        assertFalse(resource.isClosed());

        lease.close();
        lease.close();
        clock.advance(Duration.ofMillis(100));
        repository.tick();
        assertTrue(resource.isClosed());
        assertEquals(1, resource.closeCalls.get());
        assertEquals(0L, repository.estimatedResidentBytes());
        assertThrows(IllegalStateException.class, lease::resource);
        repository.close();
    }

    @Test
    void shouldEvictLeastRecentlyUsedTextureToMeetBudget() {
        MutableClock clock = new MutableClock();
        FakeTextureDevice device = new FakeTextureDevice();
        TextureRepository repository = repository(
                new CountingDecoder(), device, clock, Duration.ofHours(1), 16L);
        TextureKey firstKey = new TextureKey(Path.of("build", "texture", "first.png"));
        TextureKey secondKey = new TextureKey(Path.of("build", "texture", "second.png"));

        repository.acquire(firstKey);
        repository.tick();
        FakeTextureResource first;
        try (TextureLease lease = repository.acquire(firstKey).orElseThrow()) {
            first = (FakeTextureResource) lease.resource();
        }
        clock.advance(Duration.ofMillis(1));

        repository.acquire(secondKey);
        repository.tick();

        assertFalse(first.isClosed(), "上一帧仍活跃的纹理不能在绘制前被逐出");
        assertEquals(2, repository.residentCount());

        repository.tick();

        assertTrue(first.isClosed());
        assertEquals(1, repository.residentCount());
        assertEquals(16L, repository.estimatedResidentBytes());
        assertEquals(2, device.uploads.get());
        repository.close();
    }

    @Test
    void shouldCloseResidentResourcesOnlyOnce() {
        MutableClock clock = new MutableClock();
        FakeTextureDevice device = new FakeTextureDevice();
        TextureRepository repository = repository(
                new CountingDecoder(), device, clock, Duration.ofMinutes(1), 1024L);
        TextureKey key = new TextureKey(Path.of("build", "texture", "close.png"));

        repository.acquire(key);
        repository.tick();
        FakeTextureResource resource;
        try (TextureLease lease = repository.acquire(key).orElseThrow()) {
            resource = (FakeTextureResource) lease.resource();
        }

        repository.close();
        repository.close();

        assertTrue(resource.isClosed());
        assertEquals(1, resource.closeCalls.get());
        assertThrows(IllegalStateException.class, () -> repository.acquire(key));
    }

    @Test
    void shouldDeferInvalidatedResourceCloseUntilLeaseRelease() {
        MutableClock clock = new MutableClock();
        FakeTextureDevice device = new FakeTextureDevice();
        TextureRepository repository = repository(
                new CountingDecoder(), device, clock, Duration.ofMinutes(1), 1024L);
        TextureKey key = new TextureKey(Path.of("build", "texture", "invalidate.png"));

        repository.acquire(key);
        repository.tick();
        TextureLease lease = repository.acquire(key).orElseThrow();
        FakeTextureResource resource = (FakeTextureResource) lease.resource();

        repository.invalidate(key);
        assertFalse(resource.isClosed());
        assertEquals(1, repository.residentCount());

        lease.close();
        repository.tick();
        assertTrue(resource.isClosed());
        assertEquals(0, repository.residentCount());
        assertEquals(0L, repository.estimatedResidentBytes());
        repository.close();
    }

    private static TextureRepository repository(NativeTexturePort decoder, TextureDevice device,
                                                Clock clock, Duration ttl, long budgetBytes) {
        return new TextureRepository(
                decoder,
                device,
                (modelHandle, textureIndex) -> Optional.empty(),
                Runnable::run,
                clock,
                ttl,
                Duration.ofSeconds(1),
                budgetBytes);
    }

    private static final class CountingDecoder implements NativeTexturePort {
        private final AtomicInteger calls = new AtomicInteger();
        private volatile String lastPath;

        @Override
        public DecodedTexture decode(String normalizedPath) {
            calls.incrementAndGet();
            lastPath = normalizedPath;
            ByteBuffer pixels = ByteBuffer.allocateDirect(16);
            for (int index = 0; index < 16; index++) {
                pixels.put((byte) index);
            }
            pixels.flip();
            return new DecodedTexture(2, 2, true, pixels);
        }
    }

    private static final class FakeTextureDevice implements TextureDevice {
        private final Thread renderThread = Thread.currentThread();
        private final AtomicInteger uploads = new AtomicInteger();
        private final Map<TextureKey, FakeTextureResource> resources = new HashMap<>();

        @Override
        public void assertRenderThread() {
            if (Thread.currentThread() != renderThread) {
                throw new IllegalStateException("not on fake render thread");
            }
        }

        @Override
        public TextureResource createAndUpload(Upload upload) {
            assertRenderThread();
            uploads.incrementAndGet();
            FakeTextureResource resource = new FakeTextureResource(
                    upload.width(), upload.height(), upload.hasAlpha());
            resources.put(upload.key(), resource);
            return resource;
        }
    }

    private static final class FakeTextureResource implements TextureResource {
        private final int width;
        private final int height;
        private final boolean hasAlpha;
        private final AtomicInteger closeCalls = new AtomicInteger();

        private FakeTextureResource(int width, int height, boolean hasAlpha) {
            this.width = width;
            this.height = height;
            this.hasAlpha = hasAlpha;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public boolean hasAlpha() {
            return hasAlpha;
        }

        @Override
        public long estimatedGpuBytes() {
            return Math.multiplyExact(Math.multiplyExact((long) width, height), 4L);
        }

        @Override
        public boolean isClosed() {
            return closeCalls.get() != 0;
        }

        @Override
        public void close() {
            closeCalls.compareAndSet(0, 1);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.EPOCH;

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
