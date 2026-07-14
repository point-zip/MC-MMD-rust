// 文件职责：定义受检模型骨骼矩阵复制边界。
package com.shiroha.mmdskin.bridge.runtime;

import java.nio.ByteBuffer;

public interface NativeModelMatrixPort {
    boolean copyHandMatrix(long modelHandle, Hand hand, ByteBuffer destination);

    enum Hand {
        RIGHT(0),
        LEFT(1);

        private final int nativeId;

        Hand(int nativeId) {
            this.nativeId = nativeId;
        }

        public int nativeId() {
            return nativeId;
        }
    }
}
