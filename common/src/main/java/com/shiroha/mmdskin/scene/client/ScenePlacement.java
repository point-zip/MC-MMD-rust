// 负责以不可变值记录场景模型的世界放置位置。
package com.shiroha.mmdskin.scene.client;

public record ScenePlacement(String modelName, double x, double y, double z, float yawDegrees) {
    public ScenePlacement {
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("modelName must not be blank");
        }
    }
}

