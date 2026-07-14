// 负责以不可变值保存一次模型绘制的局部变换矩阵。
package com.shiroha.mmdskin.client.frame;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public record ModelTransform(
        float m00, float m01, float m02, float m03,
        float m10, float m11, float m12, float m13,
        float m20, float m21, float m22, float m23,
        float m30, float m31, float m32, float m33) {

    public static ModelTransform identity() {
        return from(new Matrix4f());
    }

    public static ModelTransform from(Matrix4fc matrix) {
        return new ModelTransform(
                matrix.m00(), matrix.m01(), matrix.m02(), matrix.m03(),
                matrix.m10(), matrix.m11(), matrix.m12(), matrix.m13(),
                matrix.m20(), matrix.m21(), matrix.m22(), matrix.m23(),
                matrix.m30(), matrix.m31(), matrix.m32(), matrix.m33());
    }

    public Matrix4f toMatrix() {
        return new Matrix4f(
                m00, m01, m02, m03,
                m10, m11, m12, m13,
                m20, m21, m22, m23,
                m30, m31, m32, m33);
    }
}

