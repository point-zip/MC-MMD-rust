// 负责保存模型动画层的 Java 侧状态，不持有 native 资源。
package com.shiroha.mmdskin.player.runtime;

public class EntityAnimState {

    public enum State {
        Idle("idle"), Walk("walk"), Sprint("sprint"), Air("air"),
        OnClimbable("onClimbable"), OnClimbableUp("onClimbableUp"), OnClimbableDown("onClimbableDown"),
        Swim("swim"), Ride("ride"), Ridden("ridden"), Driven("driven"),
        Sleep("sleep"), ElytraFly("elytraFly"), Die("die"),
        SwingRight("swingRight"), SwingLeft("swingLeft"), ItemRight("itemRight"), ItemLeft("itemLeft"),
        Sneak("sneak"), OnHorse("onHorse"), Crawl("crawl"), LieDown("lieDown");

        public final String propertyName;

        State(String propertyName) {
            this.propertyName = propertyName;
        }
    }

    public enum AnimPhase { NONE, ENTERING, LOOPING, EXITING }

    public boolean playCustomAnim;
    public boolean playStageAnim;
    public State[] stateLayers;
    public AnimPhase[] layerPhases;
    public String[] layerAnimationKeys;
    public String[] layerGroupIds;
    public String[] layerExitStacks;
    public String[] layerLoopStacks;
    public boolean layer1BoneMaskSet;

    public EntityAnimState(int layerCount) {
        this.stateLayers = new State[layerCount];
        this.playCustomAnim = false;
        this.layerPhases = new AnimPhase[layerCount];
        this.layerAnimationKeys = new String[layerCount];
        this.layerGroupIds = new String[layerCount];
        this.layerExitStacks = new String[layerCount];
        this.layerLoopStacks = new String[layerCount];
        for (int i = 0; i < layerCount; i++) {
            layerPhases[i] = AnimPhase.NONE;
        }
    }

    public void invalidateStateLayers() {
        for (int i = 0; i < stateLayers.length; i++) {
            stateLayers[i] = null;
            layerPhases[i] = AnimPhase.NONE;
            layerAnimationKeys[i] = null;
            layerGroupIds[i] = null;
            layerExitStacks[i] = null;
            layerLoopStacks[i] = null;
        }
    }

    public void dispose() {
        invalidateStateLayers();
    }

    public static String getPropertyName(State state) {
        return state.propertyName;
    }
}
