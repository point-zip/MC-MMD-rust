// 负责定义模型仓库使用的结构化实例身份。
package com.shiroha.mmdskin.client.model;

import java.util.Objects;

public record ModelKey(String modelName, String ownerId, Usage usage) {
    public ModelKey {
        modelName = requireText(modelName, "modelName");
        ownerId = requireText(ownerId, "ownerId");
        usage = Objects.requireNonNull(usage, "usage");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public enum Usage {
        ENTITY,
        FIRST_PERSON,
        INVENTORY,
        SCENE
    }
}

