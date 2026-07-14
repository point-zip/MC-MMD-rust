// 文件职责：扫描本地舞台包并通过动画检查端口识别动作与相机数据。
package com.shiroha.mmdskin.stage.client.asset;

import com.shiroha.mmdskin.bridge.NativePortAdapters;
import com.shiroha.mmdskin.bridge.runtime.NativeAnimationPort;
import com.shiroha.mmdskin.bridge.runtime.NativeStagePort;
import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.config.StagePack;

import java.util.List;

public final class LocalStagePackRepository {
    private static final LocalStagePackRepository INSTANCE = new LocalStagePackRepository();
    private static final NativeAnimationPort ANIMATIONS = NativePortAdapters.animation();
    private static final NativeStagePort STAGE = NativePortAdapters.stage();

    private LocalStagePackRepository() {
    }

    public static LocalStagePackRepository getInstance() {
        return INSTANCE;
    }

    public List<StagePack> loadStagePacks() {
        PathConstants.ensureStageAnimDir();
        return StagePack.scan(PathConstants.getStageAnimDir(), path -> {
            long tempAnim = ANIMATIONS.loadAnimation(0, path);
            if (tempAnim == 0) {
                return null;
            }
            boolean[] result = {
                    STAGE.hasCameraData(tempAnim),
                    STAGE.hasBoneData(tempAnim),
                    STAGE.hasMorphData(tempAnim)
            };
            ANIMATIONS.deleteAnimation(tempAnim);
            return result;
        });
    }
}
