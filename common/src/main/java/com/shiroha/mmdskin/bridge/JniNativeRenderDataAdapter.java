// 负责解析 ABI 4 descriptor 并实现受检 Native Render Data Interface。
package com.shiroha.mmdskin.bridge;

import com.shiroha.mmdskin.bridge.render.NativeRenderDataPort;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;

final class JniNativeRenderDataAdapter implements NativeRenderDataPort {
    private static final int MESH_DESCRIPTOR_BYTES = 88;
    private static final int TEXTURE_DESCRIPTOR_BYTES = 16;

    @Override
    public MeshDescription describeMesh(long modelHandle) {
        ByteBuffer descriptor = directBuffer(MESH_DESCRIPTOR_BYTES);
        requireWritten("describeMesh", NativeBindings.describeMesh(modelHandle, descriptor), MESH_DESCRIPTOR_BYTES);
        int abi = descriptor.getInt(0);
        if (abi != NativeBridgeBootstrap.REQUIRED_ABI_VERSION) {
            throw new IllegalStateException("mesh descriptor ABI mismatch: " + abi);
        }
        int indexWidth = descriptor.getInt(20);
        IndexType indexType = switch (indexWidth) {
            case Short.BYTES -> IndexType.SHORT;
            case Integer.BYTES -> IndexType.INT;
            default -> throw new IllegalStateException("unsupported native index width: " + indexWidth);
        };
        int flags = descriptor.getInt(48);
        return new MeshDescription(
                nonNegative(descriptor.getInt(4), "vertexCount"),
                nonNegative(descriptor.getInt(8), "indexCount"),
                nonNegative(descriptor.getInt(12), "drawCommandCount"),
                descriptor.getInt(16),
                indexType,
                descriptor.getLong(24),
                descriptor.getLong(32),
                descriptor.getLong(40),
                (flags & 2) != 0,
                (flags & 1) != 0,
                descriptor.getInt(52),
                nonNegative(descriptor.getInt(56), "paletteEntryCount"),
                descriptor.getInt(60),
                descriptor.getLong(64),
                descriptor.getLong(72),
                descriptor.getLong(80));
    }

    @Override
    public int copyIndices(long modelHandle, ByteBuffer target) {
        requireDirect(target);
        return requireSuccess("copyIndices", NativeBindings.copyIndices(modelHandle, target));
    }

    @Override
    public int copyFrameVertices(long modelHandle, ByteBuffer target) {
        requireDirect(target);
        return requireSuccess("copyFrameVertices", NativeBindings.copyFrameVertices(modelHandle, target));
    }

    @Override
    public int copyDrawCommands(long modelHandle, ByteBuffer target) {
        requireDirect(target);
        return requireSuccess("copyDrawCommands", NativeBindings.copyDrawCommands(modelHandle, target));
    }

    @Override
    public int copyBonePaletteMatrices(long modelHandle, ByteBuffer target) {
        requireDirect(target);
        return requireSuccess("copyBonePaletteMatrices", NativeBindings.copyBonePaletteMatrices(modelHandle, target));
    }

    @Override
    public Optional<String> resolveTexturePath(long modelHandle, int textureIndex) {
        if (modelHandle <= 0L || textureIndex < 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(NativeBindings.getTexturePath(modelHandle, textureIndex))
                .filter(path -> !path.isBlank());
    }

    @Override
    public TextureDescription decodeTexture(String normalizedPath) {
        if (normalizedPath == null || normalizedPath.isBlank()) {
            throw new IllegalArgumentException("texture path must not be blank");
        }
        long handle = NativeBindings.decodeTexture(normalizedPath);
        if (handle <= 0L) {
            throw new IllegalStateException("decodeTexture returned no handle for " + normalizedPath);
        }
        boolean release = true;
        try {
            ByteBuffer descriptor = directBuffer(TEXTURE_DESCRIPTOR_BYTES);
            requireWritten("describeTexture", NativeBindings.describeTexture(handle, descriptor), TEXTURE_DESCRIPTOR_BYTES);
            int alphaStatus = NativeBindings.textureHasAlpha(handle);
            if (alphaStatus < 0) {
                throw new NativeBridgeException("textureHasAlpha", alphaStatus);
            }
            TextureDescription description = new TextureDescription(
                    handle,
                    nonNegative(descriptor.getInt(0), "texture width"),
                    nonNegative(descriptor.getInt(4), "texture height"),
                    descriptor.getLong(8),
                    alphaStatus != 0);
            release = false;
            return description;
        } finally {
            if (release) {
                int ignored = NativeBindings.releaseTexture(handle);
            }
        }
    }

    @Override
    public int copyTexturePixels(long textureHandle, ByteBuffer target) {
        requireDirect(target);
        return requireSuccess("copyTexturePixels", NativeBindings.copyTexturePixels(textureHandle, target));
    }

    @Override
    public void releaseTexture(long textureHandle) {
        requireSuccess("releaseTexture", NativeBindings.releaseTexture(textureHandle));
    }

    private static ByteBuffer directBuffer(int capacity) {
        return ByteBuffer.allocateDirect(capacity).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static void requireDirect(ByteBuffer target) {
        if (target == null || !target.isDirect()) {
            throw new IllegalArgumentException("native copy target must be a direct ByteBuffer");
        }
    }

    private static int requireSuccess(String operation, int status) {
        if (status < 0) {
            throw new NativeBridgeException(operation, status);
        }
        return status;
    }

    private static void requireWritten(String operation, int status, int expected) {
        int written = requireSuccess(operation, status);
        if (written != expected) {
            throw new IllegalStateException(operation + " wrote " + written + " bytes, expected " + expected);
        }
    }

    private static int nonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalStateException(name + " must be non-negative");
        }
        return value;
    }
}
