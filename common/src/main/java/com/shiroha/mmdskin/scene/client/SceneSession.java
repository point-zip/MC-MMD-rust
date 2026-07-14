// 负责场景放置会话、Model Lease 获取与渲染贡献生命周期。
package com.shiroha.mmdskin.scene.client;

import com.shiroha.mmdskin.client.model.ModelKey;
import com.shiroha.mmdskin.client.model.ModelLease;
import com.shiroha.mmdskin.client.model.ModelRepository;

public final class SceneSession implements AutoCloseable {
    private static final String SCENE_OWNER = "local-scene";
    private static final float NATIVE_MODEL_SCALE = 0.09F;

    private final ModelRepository models;
    private final SceneRenderContributor contributor;
    private ScenePlacement placement;
    private ModelLease lease;

    public SceneSession(ModelRepository models, SceneRenderContributor contributor) {
        this.models = models;
        this.contributor = contributor;
    }

    public void place(ScenePlacement newPlacement) {
        remove();
        placement = newPlacement;
        acquireIfReady();
    }

    public void tick() {
        acquireIfReady();
    }

    public boolean isActive() {
        return placement != null && lease != null;
    }

    public boolean isLoading() {
        return placement != null && lease == null;
    }

    public String modelName() {
        return placement == null ? null : placement.modelName();
    }

    public void contribute(double cameraX, double cameraY, double cameraZ, int packedLight,
                           long frameId, float deltaSeconds) {
        acquireIfReady();
        if (placement == null || lease == null) {
            return;
        }
        contributor.contribute(placement, lease.instance(), cameraX, cameraY, cameraZ, packedLight,
                frameId, deltaSeconds);
    }

    public void remove() {
        if (lease != null) {
            lease.close();
            lease = null;
        }
        placement = null;
    }

    private void acquireIfReady() {
        if (placement == null || lease != null) {
            return;
        }
        ModelKey key = new ModelKey(placement.modelName(), SCENE_OWNER, ModelKey.Usage.SCENE);
        lease = models.acquire(key).orElse(null);
        if (lease != null) {
            lease.instance().setModelPositionAndYaw(
                    (float) placement.x() * NATIVE_MODEL_SCALE,
                    (float) placement.y() * NATIVE_MODEL_SCALE,
                    (float) placement.z() * NATIVE_MODEL_SCALE,
                    (float) Math.toRadians(placement.yawDegrees()));
        }
    }

    @Override
    public void close() {
        remove();
    }
}
