// 负责 Fabric 侧 YSM 反射绑定与失败回退。
package com.shiroha.mmdskin.fabric.compat;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class YsmCompat {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Bindings BINDINGS = Bindings.load();

    private YsmCompat() {
    }

    public static boolean isYsmActive(LivingEntity entity) {
        if (!isYsmModelActive(entity)) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean local = minecraft.player != null && minecraft.player.getUUID().equals(entity.getUUID());
        return local ? !isDisableSelfModel() : !isDisableOtherModel();
    }

    public static boolean isYsmModelActive(LivingEntity entity) {
        return BINDINGS.isModelActive();
    }

    public static boolean isDisableSelfModel() {
        return BINDINGS.booleanValue(BINDINGS.disableSelfModel());
    }

    public static boolean isDisableOtherModel() {
        return BINDINGS.booleanValue(BINDINGS.disableOtherModel());
    }

    public static boolean isDisableSelfHands() {
        return BINDINGS.booleanValue(BINDINGS.disableSelfHands());
    }

    private record Bindings(
            Method modelActiveMethod,
            Object disableSelfModel,
            Object disableOtherModel,
            Object disableSelfHands,
            Method booleanValueGet) {

        private static final Bindings UNAVAILABLE = new Bindings(null, null, null, null, null);

        private static Bindings load() {
            if (!FabricLoader.getInstance().isModLoaded("yes_steve_model")) {
                return UNAVAILABLE;
            }
            try {
                Class<?> config = Class.forName("com.elfmcys.yesstevemodel.OOO0OOOOo0O0Oo00oOOooo0O");
                Object selfModel = config.getDeclaredField("Oo00OoO0o000oOOooOoOOoO0").get(null);
                Object otherModel = config.getDeclaredField("oOO0000ooo0oOOo0OO0oo0Oo").get(null);
                Object selfHands = config.getDeclaredField("O0Oo0000OoOoOOOo0oo0o000").get(null);
                Method booleanGet = selfModel.getClass().getMethod("get");
                Class<?> main = Class.forName("com.elfmcys.yesstevemodel.YesSteveModel");
                return new Bindings(main.getMethod("isAvailable"), selfModel, otherModel, selfHands, booleanGet);
            } catch (ReflectiveOperationException | LinkageError exception) {
                LOGGER.warn("YSM Fabric 兼容签名不可用，已回退原版/MMD 路由", exception);
                return UNAVAILABLE;
            }
        }

        private boolean isModelActive() {
            if (modelActiveMethod == null) {
                return false;
            }
            try {
                return Boolean.TRUE.equals(modelActiveMethod.invoke(null));
            } catch (ReflectiveOperationException exception) {
                LOGGER.debug("读取 YSM Fabric 模型状态失败", exception);
                return false;
            }
        }

        private boolean booleanValue(Object value) {
            if (value == null || booleanValueGet == null) {
                return false;
            }
            try {
                return Boolean.TRUE.equals(booleanValueGet.invoke(value));
            } catch (ReflectiveOperationException exception) {
                LOGGER.debug("读取 YSM Fabric 配置失败", exception);
                return false;
            }
        }
    }
}
