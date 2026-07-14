/* 文件职责：初始化客户端侧 MMD 运行时与舞台子系统。 */
package com.shiroha.mmdskin;

import com.shiroha.mmdskin.api.MmdSkinApi;
import com.shiroha.mmdskin.bridge.NativePortAdapters;
import com.shiroha.mmdskin.bridge.NativeBridgeBootstrap;
import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import com.shiroha.mmdskin.stage.client.bootstrap.StageClientBootstrap;
import com.shiroha.mmdskin.util.VectorParseUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3f;

public class MmdSkinClient {
    public static final Logger logger = LogManager.getLogger();
    public static void initClient() {
        NativeBridgeBootstrap.verifyAbi();
        MmdSkinApi.configureRuntimeCollaborators(
                NativePortAdapters.model(),
                NativePortAdapters.modelQuery()
        );
        StageClientBootstrap.initialize();
        MmdClientResourceBootstrap.initialize();
        MmdClientRenderRuntime.install(MmdClientRenderRuntime.createDefault());
    }

    public static String calledFrom(int i){
        StackTraceElement[] steArray = Thread.currentThread().getStackTrace();
        if (steArray.length <= i) {
            return "";
        }
        return steArray[i].getClassName();
    }

    public static Vector3f str2Vec3f(String arg){
        return VectorParseUtil.parseVec3f(arg);
    }

}
