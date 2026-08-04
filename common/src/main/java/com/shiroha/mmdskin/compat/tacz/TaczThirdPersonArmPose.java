package com.shiroha.mmdskin.compat.tacz;

import org.joml.Quaternionf;

/** TaCZ 第三人称左右上臂的绝对局部姿态。 */
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

    static float angleDegrees(Quaternionf rotation) {
        Quaternionf normalized = copyNormalized(rotation);
        if (normalized == null) return Float.NaN;
        float absoluteW = Math.min(1.0f, Math.abs(normalized.w));
        return (float) Math.toDegrees(2.0 * Math.acos(absoluteW));
    }

    /** JNI 布局为左、右各 xyzw 四项。 */
    public float[] toNativePacket() {
        float[] output = new float[8];
        write(output, 0, left);
        write(output, 4, right);
        return output;
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
