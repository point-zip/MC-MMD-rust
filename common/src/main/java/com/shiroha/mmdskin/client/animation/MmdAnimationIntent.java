// 负责以不可变值描述一次模型帧更新需要的基础、动作与姿态动画。
package com.shiroha.mmdskin.client.animation;

import java.util.Objects;

public record MmdAnimationIntent(
        String baseAnimation,
        Action action,
        String postureAnimation) {
    private static final MmdAnimationIntent NONE = new MmdAnimationIntent(null, Action.clear(), null);

    public MmdAnimationIntent {
        baseAnimation = normalize(baseAnimation);
        action = Objects.requireNonNull(action, "action");
        postureAnimation = normalize(postureAnimation);
    }

    public static MmdAnimationIntent none() {
        return NONE;
    }

    public boolean active() {
        return baseAnimation != null;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record Action(
            String primaryAnimation,
            String secondaryAnimation,
            String fallbackAnimation,
            boolean loop,
            boolean preserveCurrent) {
        private static final Action CLEAR = new Action(null, null, null, true, false);
        private static final Action PRESERVE = new Action(null, null, null, true, true);

        public Action {
            primaryAnimation = normalize(primaryAnimation);
            secondaryAnimation = normalize(secondaryAnimation);
            fallbackAnimation = normalize(fallbackAnimation);
        }

        public static Action clear() {
            return CLEAR;
        }

        public static Action preserve() {
            return PRESERVE;
        }

        public static Action play(String primaryAnimation, String secondaryAnimation,
                                  String fallbackAnimation, boolean loop) {
            return new Action(primaryAnimation, secondaryAnimation, fallbackAnimation, loop, false);
        }

        public boolean clearsLayer() {
            return !preserveCurrent && primaryAnimation == null
                    && secondaryAnimation == null && fallbackAnimation == null;
        }
    }
}
