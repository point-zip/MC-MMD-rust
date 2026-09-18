// 负责 ImmersiveMelodies 软依赖探测与乐器姿势采样入口。
// 本类不 import 任何 IM 类；IM 交互全部隔离在 MelodiesHooks（仅探测成功后加载）。
package com.shiroha.mmdskin.compat.melodies;

import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class MelodiesCompat {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String ANIMATOR_CLASS = "immersive_melodies.client.animation.EntityModelAnimator";
    /** 连续失败多少次后判定 IM 不兼容并停用联动。 */
    private static final int FAILURE_STREAK_LIMIT = 100;
    private static volatile Boolean loaded;
    private static volatile boolean hookFailureLogged;
    private static volatile int failureStreak;

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
            failureStreak = 0;
            lastState = pose == null ? "未持乐器" : "持乐器";
            return pose;
        } catch (Throwable failure) {
            // 连续失败才判定为不兼容（IM 版本不符等），避免偶发异常把联动永久关掉
            if (++failureStreak >= FAILURE_STREAK_LIMIT) {
                if (!hookFailureLogged) {
                    hookFailureLogged = true;
                    LOGGER.warn("ImmersiveMelodies 姿势采样连续失败，联动已停用: {}", failure.toString());
                }
                loaded = false;
                lastState = "采样失败，已停用";
            } else {
                lastState = "采样异常 " + failureStreak;
            }
            return null;
        }
    }
}
