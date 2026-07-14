// 负责拥有单个模型的 native 句柄、动画状态和确定性生命周期。
package com.shiroha.mmdskin.client.model;

import com.shiroha.mmdskin.bridge.runtime.NativeAnimationPort;
import com.shiroha.mmdskin.bridge.runtime.NativeModelLoadPort;
import com.shiroha.mmdskin.bridge.runtime.NativeModelMatrixPort;
import com.shiroha.mmdskin.bridge.runtime.NativeModelPort;
import com.shiroha.mmdskin.client.frame.FrameUpdateTarget;
import com.shiroha.mmdskin.client.frame.MmdRenderSnapshot;
import com.shiroha.mmdskin.client.draw.DrawModelRef;
import com.shiroha.mmdskin.config.ModelConfigData;
import com.shiroha.mmdskin.config.ModelConfigManager;
import com.shiroha.mmdskin.player.runtime.EntityAnimState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class MmdModelInstance implements FrameUpdateTarget, DrawModelRef, AutoCloseable {
    private static final float BASE_RENDER_SCALE = 0.09F;

    private final ModelKey key;
    private final ModelSource source;
    private final NativeModelLoadPort modelRuntime;
    private final NativeAnimationPort animations;
    private final NativeModelPort modelAccess;
    private final NativeModelMatrixPort modelMatrices;
    private final EntityAnimState animationState = new EntityAnimState(3);
    private final Properties properties = new Properties();
    private final ByteBuffer rightHandStaging = handMatrixStaging();
    private final ByteBuffer leftHandStaging = handMatrixStaging();
    private final Matrix4f rightHandTransform = new Matrix4f();
    private final Matrix4f leftHandTransform = new Matrix4f();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong revision = new AtomicLong();
    private volatile long handle;
    private volatile boolean propertiesLoaded;
    private volatile float renderScale = BASE_RENDER_SCALE;
    private volatile float heldItemScale = ModelConfigData.DEFAULT_HELD_ITEM_SCALE;
    private volatile boolean gpuSkinningRequested;
    private volatile boolean gpuSkinningEnabled;

    MmdModelInstance(ModelKey key, ModelSource source, long handle,
                     NativeModelLoadPort modelRuntime, NativeAnimationPort animations,
                     NativeModelPort modelAccess, NativeModelMatrixPort modelMatrices) {
        if (handle <= 0L) {
            throw new IllegalArgumentException("handle must be positive");
        }
        this.key = key;
        this.source = source;
        this.handle = handle;
        this.modelRuntime = modelRuntime;
        this.animations = animations;
        this.modelAccess = modelAccess;
        this.modelMatrices = modelMatrices;
        loadProperties(false);
        modelRuntime.resetPhysics(handle);
    }

    public ModelKey key() {
        return key;
    }

    public String modelName() {
        return source.modelName();
    }

    public String modelDirectory() {
        return source.directory();
    }

    public long handle() {
        return handle;
    }

    public long revision() {
        return revision.get();
    }

    @Override
    public long modelInstanceId() {
        return handle();
    }

    @Override
    public long nativeHandle() {
        return handle();
    }

    @Override
    public long nativeRevision() {
        return revision();
    }

    @Override
    public float renderScale() {
        return renderScale;
    }

    public float heldItemScale() {
        return heldItemScale;
    }

    public EntityAnimState animationState() {
        return animationState;
    }

    public Properties properties() {
        return properties;
    }

    public synchronized void loadProperties(boolean forceReload) {
        if (propertiesLoaded && !forceReload) {
            return;
        }
        ModelConfigData config = ModelConfigManager.getLiveConfig(modelName());
        applyModelConfig(config);
        propertiesLoaded = true;
    }

    public synchronized void applyModelConfig(ModelConfigData config) {
        ModelConfigData normalized = config == null ? new ModelConfigData() : config.normalizedCopy();
        properties.setProperty("size", Float.toString(normalized.modelScale));
        renderScale = BASE_RENDER_SCALE * normalized.modelScale;
        heldItemScale = normalized.heldItemScale;
        propertiesLoaded = true;
    }

    public Matrix4fc updateHandTransform(NativeModelMatrixPort.Hand hand) {
        ByteBuffer staging = hand == NativeModelMatrixPort.Hand.RIGHT ? rightHandStaging : leftHandStaging;
        Matrix4f transform = hand == NativeModelMatrixPort.Hand.RIGHT ? rightHandTransform : leftHandTransform;
        staging.clear();
        if (!modelMatrices.copyHandMatrix(requireOpenHandle(), hand, staging)) {
            return null;
        }
        staging.position(0);
        transform.set(staging.asFloatBuffer());
        return transform;
    }

    @Override
    public void update(MmdRenderSnapshot snapshot, float deltaSeconds) {
        long currentHandle = requireOpenHandle();
        modelRuntime.updateModel(currentHandle, Math.min(deltaSeconds, 0.25F));
        revision.incrementAndGet();
    }

    public void update(float deltaSeconds) {
        long currentHandle = requireOpenHandle();
        modelRuntime.updateModel(currentHandle, Math.min(deltaSeconds, 0.25F));
        revision.incrementAndGet();
    }

    public void changeAnimation(long animationHandle, int layer) {
        animations.changeAnimation(requireOpenHandle(), animationHandle, layer);
    }

    public void transitionAnimation(long animationHandle, int layer, float transitionSeconds) {
        animations.transitionAnimation(requireOpenHandle(), layer, animationHandle, transitionSeconds);
    }

    public void setLayerLoop(int layer, boolean loop) {
        animations.setLayerLoop(requireOpenHandle(), layer, loop);
    }

    public boolean isLayerAnimationFinished(int layer) {
        return animations.isLayerAnimationFinished(requireOpenHandle(), layer);
    }

    public void seekLayer(int layer, float frame) {
        animations.seekLayer(requireOpenHandle(), layer, frame);
    }

    public void resetPhysics() {
        modelRuntime.resetPhysics(requireOpenHandle());
    }

    public void setPhysicsEnabled(boolean enabled) {
        modelRuntime.setPhysicsEnabled(requireOpenHandle(), enabled);
    }

    public boolean setGpuSkinningEnabled(boolean enabled) {
        if (gpuSkinningRequested == enabled) {
            return !enabled || gpuSkinningEnabled;
        }
        gpuSkinningRequested = enabled;
        boolean accepted = modelAccess.setGpuSkinningEnabled(requireOpenHandle(), enabled);
        gpuSkinningEnabled = enabled && accepted;
        return accepted;
    }

    public void setModelPositionAndYaw(float x, float y, float z, float yawRadians) {
        modelRuntime.setModelPositionAndYaw(requireOpenHandle(), x, y, z, yawRadians);
    }

    public long ramUsage() {
        return modelAccess.getModelMemoryUsage(requireOpenHandle());
    }

    public void setMaterialVisible(int index, boolean visible) {
        modelAccess.setMaterialVisible(requireOpenHandle(), index, visible);
    }

    private long requireOpenHandle() {
        long currentHandle = handle;
        if (closed.get() || currentHandle == 0L) {
            throw new IllegalStateException("model instance is closed");
        }
        return currentHandle;
    }

    private static ByteBuffer handMatrixStaging() {
        return ByteBuffer.allocateDirect(16 * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        long currentHandle = handle;
        handle = 0L;
        animationState.dispose();
        if (currentHandle != 0L) {
            modelAccess.deleteModel(currentHandle);
        }
    }
}
