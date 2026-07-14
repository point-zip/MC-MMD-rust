// 负责保存 native 物理惯性所需的不可变模型世界位置与朝向。
package com.shiroha.mmdskin.client.frame;

public record MmdModelMotion(
        float x,
        float y,
        float z,
        float yawRadians,
        boolean synchronizeNative) {
    public MmdModelMotion {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                || !Float.isFinite(yawRadians)) {
            throw new IllegalArgumentException("model motion must be finite");
        }
    }

    public static MmdModelMotion stationary() {
        return new MmdModelMotion(0.0F, 0.0F, 0.0F, 0.0F, false);
    }
}
