// 负责集中探测 Iris 激活状态与阴影 pass 兼容回退条件。
package com.shiroha.mmdskin.compat.iris;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;

public final class IrisCompatibility {
    private static final Logger LOGGER = LogManager.getLogger();
    private static volatile Detection detection;

    private IrisCompatibility() {
    }

    public static boolean isShaderPackActive() {
        Detection current = detection();
        if (current.apiInstance() == null || current.shaderPackMethod() == null) {
            return false;
        }
        return invokeBoolean(current.shaderPackMethod(), current.apiInstance());
    }

    public static boolean isShadowPass() {
        Method shadowMethod = detection().shadowMethod();
        return shadowMethod != null && invokeBoolean(shadowMethod, null);
    }

    private static Detection detection() {
        Detection current = detection;
        if (current != null) {
            return current;
        }
        synchronized (IrisCompatibility.class) {
            if (detection == null) {
                detection = detect();
            }
            return detection;
        }
    }

    private static Detection detect() {
        Object api = null;
        Method shaderPack = null;
        try {
            Class<?> irisApi = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = irisApi.getMethod("getInstance").invoke(null);
            shaderPack = irisApi.getMethod("isShaderPackInUse");
        } catch (ClassNotFoundException ignored) {
            return new Detection(null, null, null);
        } catch (ReflectiveOperationException exception) {
            LOGGER.warn("Iris API 探测失败，将使用原版回退", exception);
        }

        Method shadow = null;
        for (String className : new String[]{
                "net.irisshaders.iris.shadows.ShadowRenderingState",
                "net.coderbot.iris.shadows.ShadowRenderingState"}) {
            try {
                shadow = Class.forName(className).getMethod("areShadowsCurrentlyBeingRendered");
                break;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return new Detection(api, shaderPack, shadow);
    }

    private static boolean invokeBoolean(Method method, Object receiver) {
        try {
            return Boolean.TRUE.equals(method.invoke(receiver));
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    private record Detection(Object apiInstance, Method shaderPackMethod, Method shadowMethod) {
    }
}

