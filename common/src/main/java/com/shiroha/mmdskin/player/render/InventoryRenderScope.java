package com.shiroha.mmdskin.player.render;

/** 文件职责：标记当前线程是否正在执行背包人物预览 Draw。 */
public final class InventoryRenderScope {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private InventoryRenderScope() {
    }

    public static void enter() {
        DEPTH.set(DEPTH.get() + 1);
    }

    public static void exit() {
        int depth = DEPTH.get();
        if (depth <= 1) {
            DEPTH.remove();
            return;
        }
        DEPTH.set(depth - 1);
    }

    public static boolean isActive() {
        return DEPTH.get() > 0;
    }

    /**
     * 无条件清空当前线程的作用域标记。背包 GUI 的 enter/exit 由 Mixin 在
     * HEAD/RETURN 注入，目标方法抛异常时 RETURN 不执行会导致深度残留；
     * 每帧渲染开始前调用一次可保证作用域只在背包预览窗口内有意义。
     */
    public static void reset() {
        DEPTH.remove();
    }
}
