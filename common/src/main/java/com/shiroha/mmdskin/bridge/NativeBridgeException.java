// 负责把稳定 native 状态码转换为带操作上下文的 Java 异常。
package com.shiroha.mmdskin.bridge;

public final class NativeBridgeException extends IllegalStateException {
    private final int status;

    NativeBridgeException(String operation, int status) {
        super(operation + " failed with native status " + status + " (" + describe(status) + ")");
        this.status = status;
    }

    public int status() {
        return status;
    }

    private static String describe(int status) {
        return switch (status) {
            case -1 -> "invalid handle";
            case -2 -> "buffer is not direct";
            case -3 -> "buffer capacity is too small";
            case -4 -> "invalid data";
            case -5 -> "size overflow";
            case -6 -> "native lock poisoned";
            case -127 -> "native panic";
            default -> "unknown error";
        };
    }
}

