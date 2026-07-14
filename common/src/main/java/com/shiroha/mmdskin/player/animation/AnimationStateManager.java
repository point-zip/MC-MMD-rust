// 负责保留玩家动画入口，并把实时状态委托给不可变意图与模型控制器。
package com.shiroha.mmdskin.player.animation;

import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import com.shiroha.mmdskin.client.model.MmdModelInstance;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.item.ItemUseAnimation;

import java.util.ArrayList;
import java.util.List;

public final class AnimationStateManager {
    static final String DRINK_ANIMATION = PlayerAnimationIntentExtractor.DRINK_ANIMATION;

    private AnimationStateManager() {
    }

    public static void updateAnimationState(AbstractClientPlayer player, MmdModelInstance model) {
        if (player == null || model == null) {
            return;
        }
        MmdClientRenderRuntime.current().animationController()
                .apply(model, PlayerAnimationIntentExtractor.capture(player));
    }

    static String resolveUseTriggerAnimationName(ItemUseAnimation useAnimation) {
        return PlayerAnimationIntentExtractor.resolveUseTriggerAnimationName(useAnimation);
    }

    static List<String> resolveItemAnimationKeys(String itemName, String activeHand,
                                                 ItemUseAnimation useAnimation, String handState) {
        List<String> animationKeys = new ArrayList<>(useAnimation == ItemUseAnimation.BOW ? 2 : 1);
        animationKeys.add(PlayerAnimationIntentExtractor.buildItemAnimationKey(
                itemName, activeHand, handState));
        if (useAnimation == ItemUseAnimation.BOW) {
            String alternateHand = "Right".equals(activeHand) ? "Left" : "Right";
            animationKeys.add(PlayerAnimationIntentExtractor.buildItemAnimationKey(
                    itemName, alternateHand, handState));
        }
        return List.copyOf(animationKeys);
    }
}
