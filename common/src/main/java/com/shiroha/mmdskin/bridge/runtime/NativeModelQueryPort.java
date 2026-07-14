/* 文件职责：定义模型查询与调试读取相关的 native 能力边界。 */
package com.shiroha.mmdskin.bridge.runtime;

import java.nio.ByteBuffer;

public interface NativeModelQueryPort {

    NativeModelQueryPort NOOP = new NativeModelQueryPort() {
        @Override
        public int getMaterialCount(long modelHandle) {
            return 0;
        }

        @Override
        public int getBoneCount(long modelHandle) {
            return 0;
        }

        @Override
        public long getVertexCount(long modelHandle) {
            return 0L;
        }

        @Override
        public long getIndexCount(long modelHandle) {
            return 0L;
        }

        @Override
        public String getBoneNames(long modelHandle) {
            return "[]";
        }

        @Override
        public int copyBonePositionsToBuffer(long modelHandle, ByteBuffer targetBuffer) {
            return 0;
        }

        @Override
        public int copyRealtimeUvsToBuffer(long modelHandle, ByteBuffer targetBuffer) {
            return 0;
        }

        @Override
        public int getMorphCount(long modelHandle) {
            return 0;
        }

        @Override
        public String getMorphName(long modelHandle, int morphIndex) {
            return "";
        }

        @Override
        public String getMaterialName(long modelHandle, int materialIndex) {
            return "";
        }

        @Override
        public boolean isMaterialVisible(long modelHandle, int materialIndex) {
            return false;
        }
    };

    static NativeModelQueryPort noop() {
        return NOOP;
    }

    int getMaterialCount(long modelHandle);

    int getBoneCount(long modelHandle);

    long getVertexCount(long modelHandle);

    long getIndexCount(long modelHandle);

    String getBoneNames(long modelHandle);

    int copyBonePositionsToBuffer(long modelHandle, ByteBuffer targetBuffer);

    int copyRealtimeUvsToBuffer(long modelHandle, ByteBuffer targetBuffer);

    int getMorphCount(long modelHandle);

    String getMorphName(long modelHandle, int morphIndex);

    String getMaterialName(long modelHandle, int materialIndex);

    boolean isMaterialVisible(long modelHandle, int materialIndex);
}
