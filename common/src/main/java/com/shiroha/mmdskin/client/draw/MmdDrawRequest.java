// 负责描述不触发模型更新的不可变 GPU 绘制请求。
package com.shiroha.mmdskin.client.draw;

import com.shiroha.mmdskin.client.frame.MmdRenderSnapshot;

import java.util.Objects;

public record MmdDrawRequest(
        DrawModelRef model,
        MmdRenderSnapshot snapshot,
        PipelineVariant pipeline,
        boolean transparent) {

    public MmdDrawRequest {
        model = Objects.requireNonNull(model, "model");
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
        pipeline = Objects.requireNonNull(pipeline, "pipeline");
        if (model.modelInstanceId() <= 0L || model.nativeHandle() <= 0L || model.nativeRevision() < 0L) {
            throw new IllegalArgumentException("draw model identity is invalid");
        }
    }
}
