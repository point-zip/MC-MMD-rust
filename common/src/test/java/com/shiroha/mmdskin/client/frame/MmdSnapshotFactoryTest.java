// 文件职责：验证各渲染上下文映射到隔离的模型仓库用途。
package com.shiroha.mmdskin.client.frame;

import com.shiroha.mmdskin.client.model.ModelKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MmdSnapshotFactoryTest {
    @Test
    void shouldMapEveryRenderContextToItsRepositoryUsage() {
        assertEquals(ModelKey.Usage.ENTITY,
                MmdSnapshotFactory.usageFor(MmdRenderSnapshot.Context.WORLD));
        assertEquals(ModelKey.Usage.FIRST_PERSON,
                MmdSnapshotFactory.usageFor(MmdRenderSnapshot.Context.FIRST_PERSON));
        assertEquals(ModelKey.Usage.INVENTORY,
                MmdSnapshotFactory.usageFor(MmdRenderSnapshot.Context.INVENTORY));
        assertEquals(ModelKey.Usage.SCENE,
                MmdSnapshotFactory.usageFor(MmdRenderSnapshot.Context.SCENE));
    }
}
