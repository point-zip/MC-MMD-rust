// 文件职责：定义舞台动画合并、数据探测与相机帧读取的 native 边界。
package com.shiroha.mmdskin.bridge.runtime;

import java.nio.ByteBuffer;

public interface NativeStagePort {
    boolean hasCameraData(long animationHandle);

    boolean hasBoneData(long animationHandle);

    boolean hasMorphData(long animationHandle);

    float getAnimationMaxFrame(long animationHandle);

    void copyCameraTransform(long animationHandle, float frame, ByteBuffer destination);

    void mergeAnimation(long targetHandle, long sourceHandle);
}
