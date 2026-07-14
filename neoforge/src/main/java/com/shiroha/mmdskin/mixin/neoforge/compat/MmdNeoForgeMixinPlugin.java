// 负责按已安装模组裁剪 NeoForge 可选兼容 Mixin。
package com.shiroha.mmdskin.mixin.neoforge.compat;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class MmdNeoForgeMixinPlugin implements IMixinConfigPlugin {
    private static final String VIVECRAFT_MIXIN_SUFFIX = ".VivecraftVRArmHelperMixin";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.endsWith(VIVECRAFT_MIXIN_SUFFIX)) {
            return true;
        }
        var loadingMods = FMLLoader.getLoadingModList();
        return loadingMods != null && loadingMods.getModFileById("vivecraft") != null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
