package com.shiroha.mmdskin.render.backend;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

/** 验证 TaCZ 第三人称始终回退到现有 VMD。 */
class BaseModelInstanceTaczThirdPersonTest {
    @Test
    void thirdPersonPoseSubmissionRemainsDisabled() {
        assertFalse(BaseModelInstance.shouldSubmitTaczThirdPersonPose());
    }
}
