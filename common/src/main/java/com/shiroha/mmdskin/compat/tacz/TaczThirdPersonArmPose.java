package com.shiroha.mmdskin.compat.tacz;

import org.joml.Quaternionf;

/** TaCZ 第三人称手臂相对腰射基线的局部旋转增量。 */
public record TaczThirdPersonArmPose(Quaternionf left, Quaternionf right, int validMask) {
    public TaczThirdPersonArmPose {
        left = copyNormalized(left);
        right = copyNormalized(right);
        validMask &= 0b11;
        if ((validMask & 0b01) != 0 && left == null) validMask &= ~0b01;
        if ((validMask & 0b10) != 0 && right == null) validMask &= ~0b10;
    }

    public boolean isValid() {
        return validMask != 0;
    }

    /** JNI 布局为左、右各 xyzw 四项。 */
    public float[] toNativePacket() {
        float[] output = new float[8];
        write(output, 0, left);
        write(output, 4, right);
        return output;
    }

    static Quaternionf relative(Quaternionf baseline, Quaternionf current) {
        Quaternionf base = copyNormalized(baseline);
        Quaternionf value = copyNormalized(current);
        if (base == null || value == null) return null;
        Quaternionf delta = base.conjugate(new Quaternionf()).mul(value).normalize();
        // q 与 -q 表达同一旋转，统一到同一半球减少帧间跳变。
        if (delta.w < 0.0f) delta.mul(-1.0f);
        return copyNormalized(delta);
    }

    private static Quaternionf copyNormalized(Quaternionf value) {
        if (value == null || !Float.isFinite(value.x) || !Float.isFinite(value.y)
                || !Float.isFinite(value.z) || !Float.isFinite(value.w)
                || value.lengthSquared() < 1.0e-8f) {
            return null;
        }
        return new Quaternionf(value).normalize();
    }

    private static void write(float[] output, int offset, Quaternionf value) {
        if (value == null) return;
        output[offset] = value.x;
        output[offset + 1] = value.y;
        output[offset + 2] = value.z;
        output[offset + 3] = value.w;
    }
}
