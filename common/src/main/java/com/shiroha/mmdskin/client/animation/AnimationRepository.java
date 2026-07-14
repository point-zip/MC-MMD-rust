// 负责按 Model Instance 加载、缓存并释放动画句柄。
package com.shiroha.mmdskin.client.animation;

import com.shiroha.mmdskin.bridge.runtime.NativeAnimationPort;
import com.shiroha.mmdskin.client.model.MmdModelInstance;
import com.shiroha.mmdskin.config.ModelAnimConfig;
import com.shiroha.mmdskin.config.PathConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class AnimationRepository implements AutoCloseable {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String[] EXTENSIONS = {".vmd", ".fbx"};

    private final NativeAnimationPort nativeAnimations;
    private final Map<MmdModelInstance, Map<String, Long>> handles = new IdentityHashMap<>();
    private final Set<String> warnedMissing = ConcurrentHashMap.newKeySet();
    private final File defaultDirectory;
    private final File customDirectory;

    public AnimationRepository(NativeAnimationPort nativeAnimations) {
        this.nativeAnimations = nativeAnimations;
        this.defaultDirectory = PathConstants.getDefaultAnimDir();
        this.customDirectory = PathConstants.getCustomAnimDir();
        PathConstants.ensureDirectoryExists(defaultDirectory);
        PathConstants.ensureDirectoryExists(customDirectory);
    }

    public synchronized long animationFor(MmdModelInstance model, String animationName) {
        if (model == null || animationName == null || animationName.isBlank()) {
            return 0L;
        }
        Map<String, Long> modelHandles = handles.computeIfAbsent(model, ignored -> new ConcurrentHashMap<>());
        return modelHandles.computeIfAbsent(animationName, ignored -> load(model, animationName));
    }

    public synchronized void invalidate(MmdModelInstance model) {
        Map<String, Long> removed = handles.remove(model);
        if (removed != null) {
            removed.values().stream().filter(handle -> handle != 0L).forEach(nativeAnimations::deleteAnimation);
        }
    }

    private long load(MmdModelInstance model, String animationName) {
        File modelDirectory = new File(model.modelDirectory());
        String mappedFile = ModelAnimConfig.getMappedFile(model.modelDirectory(), animationName);
        long handle = loadMapped(model, animationName, mappedFile, modelDirectory);
        if (handle == 0L) {
            handle = loadFromDirectory(model, PathConstants.getModelAnimsDirByPath(model.modelDirectory()), animationName);
        }
        if (handle == 0L) {
            handle = loadFromDirectory(model, modelDirectory, animationName);
        }
        if (handle == 0L) {
            handle = loadFromDirectory(model, customDirectory, animationName);
        }
        if (handle == 0L) {
            handle = loadFromDirectory(model, defaultDirectory, animationName);
        }
        if (handle == 0L && warnedMissing.add(animationName)) {
            LOGGER.warn("未找到动画: {}", animationName);
        }
        return handle;
    }

    private long loadMapped(MmdModelInstance model, String animationName,
                            String mappedFile, File modelDirectory) {
        if (mappedFile == null) {
            return 0L;
        }
        if (mappedFile.contains("..") || mappedFile.contains("/") || mappedFile.contains("\\")) {
            LOGGER.warn("忽略不安全的动画映射: {} -> {}", animationName, mappedFile);
            return 0L;
        }
        File animationDirectory = PathConstants.getModelAnimsDirByPath(model.modelDirectory());
        File target = new File(animationDirectory, mappedFile);
        if (!target.isFile()) {
            target = new File(modelDirectory, mappedFile);
        }
        return target.isFile() ? nativeAnimations.loadAnimation(model.handle(), target.getAbsolutePath()) : 0L;
    }

    private long loadFromDirectory(MmdModelInstance model, File directory, String animationName) {
        for (String extension : EXTENSIONS) {
            File candidate = new File(directory, animationName + extension);
            if (candidate.isFile()) {
                return nativeAnimations.loadAnimation(model.handle(), candidate.getAbsolutePath());
            }
        }

        File[] fbxFiles = directory.listFiles((ignored, name) -> name.toLowerCase().endsWith(".fbx"));
        if (fbxFiles == null) {
            return 0L;
        }
        for (File fbx : fbxFiles) {
            long handle = nativeAnimations.loadAnimation(model.handle(), fbx.getAbsolutePath() + "#" + animationName);
            if (handle != 0L) {
                return handle;
            }
        }
        return 0L;
    }

    @Override
    public synchronized void close() {
        handles.values().forEach(modelHandles -> modelHandles.values().stream()
                .filter(handle -> handle != 0L)
                .forEach(nativeAnimations::deleteAnimation));
        handles.clear();
        warnedMissing.clear();
    }
}

