// 负责 ImmersiveMelodies 软依赖探测与乐器姿势采样入口。
// 本类不 import 任何 IM 类；IM 交互全部隔离在 MelodiesHooks（仅探测成功后加载）。
package com.shiroha.mmdskin.compat.melodies;

import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class MelodiesCompat {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String ANIMATOR_CLASS = "immersive_melodies.client.animation.EntityModelAnimator";
    private static volatile Boolean loaded;
    private static volatile boolean hookFailureLogged;

    /** 最近一次采样的可读状态，供调试 HUD 显示（判断联动是否真的生效）。 */
    private static volatile String lastState = "未采样";

    private MelodiesCompat() {
    }

    public static String lastState() {
        return lastState;
    }

    public static boolean isLoaded() {
        Boolean value = loaded;
        if (value == null) {
            try {
                Class.forName(ANIMATOR_CLASS, false, MelodiesCompat.class.getClassLoader());
                value = true;
            } catch (ClassNotFoundException exception) {
                value = false;
            }
            loaded = value;
            lastState = value ? "已加载，未演奏" : "未安装 IM";
        }
        return value;
    }

    /**
     * 采样实体当前乐器演奏姿势。未安装 IM / 实体未持乐器 / 采样异常时返回 null。
     * 每帧对每个被渲染的 MMD 实体调用（渲染线程）。
     */
    public static MelodiesPose capturePose(LivingEntity entity) {
        if (entity == null || !isLoaded()) {
            return null;
        }
        try {
            MelodiesPose pose = MelodiesHooks.capture(entity);
            lastState = pose == null ? "持乐器未演奏" : "演奏中";
            return pose;
        } catch (Throwable failure) {
            // IM 版本不匹配等场景：记录一次后永久回退，避免每帧刷日志
            if (!hookFailureLogged) {
                hookFailureLogged = true;
                LOGGER.warn("ImmersiveMelodies 姿势采样失败，联动已停用: {}", failure.toString());
            }
            loaded = false;
            lastState = "采样失败，已停用";
            return null;
        }
    }
}
