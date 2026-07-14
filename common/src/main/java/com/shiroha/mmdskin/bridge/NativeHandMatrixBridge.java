// 负责校验并复制 native 左右手骨骼矩阵。
package com.shiroha.mmdskin.bridge;

import com.shiroha.mmdskin.bridge.runtime.NativeModelMatrixPort;
import java.nio.ByteBuffer;

public final class NativeHandMatrixBridge {
    public static final int MATRIX_BYTES = 16 * Float.BYTES;

    private NativeHandMatrixBridge() {
    }

    public static boolean copy(long modelHandle, NativeModelMatrixPort.Hand hand, ByteBuffer destination) {
        if (modelHandle <= 0L) {
            throw new IllegalArgumentException("modelHandle must be positive");
        }
        if (destination == null || !destination.isDirect()) {
            throw new IllegalArgumentException("hand matrix destination must be a direct ByteBuffer");
        }
        if (destination.capacity() < MATRIX_BYTES) {
            throw new IllegalArgumentException("hand matrix destination must contain at least 64 bytes");
        }
        int status = NativeBindings.copyHandMatrix(modelHandle, hand.nativeId(), destination);
        if (status < 0) {
            throw new NativeBridgeException("copyHandMatrix", status);
        }
        if (status != MATRIX_BYTES) {
            throw new IllegalStateException("copyHandMatrix wrote " + status + " bytes, expected " + MATRIX_BYTES);
        }
        return true;
    }
}
