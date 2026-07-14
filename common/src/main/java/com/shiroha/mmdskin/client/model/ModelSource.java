// 负责描述可由 native 解析的模型资源及格式。
package com.shiroha.mmdskin.client.model;

import com.shiroha.mmdskin.asset.catalog.ModelInfo;
import com.shiroha.mmdskin.bridge.runtime.NativeModelLoadPort;

import java.util.Objects;
import java.io.File;
import com.shiroha.mmdskin.config.PathConstants;

public record ModelSource(String modelName, String file, String directory, NativeModelLoadPort.Format format) {
    public ModelSource {
        modelName = Objects.requireNonNull(modelName, "modelName");
        file = Objects.requireNonNull(file, "file");
        directory = Objects.requireNonNull(directory, "directory");
        format = Objects.requireNonNull(format, "format");
    }

    public static ModelSource find(String modelName) {
        ModelInfo info = ModelInfo.findByFolderName(modelName);
        if (info == null) {
            return scanDirectory(modelName, new File(PathConstants.getSkinRootDir(), modelName));
        }
        NativeModelLoadPort.Format format = info.isVRM()
                ? NativeModelLoadPort.Format.VRM
                : info.isPMD() ? NativeModelLoadPort.Format.PMD : NativeModelLoadPort.Format.PMX;
        return new ModelSource(modelName, info.getModelFilePath(), info.getFolderPath(), format);
    }

    private static ModelSource scanDirectory(String modelName, File directory) {
        if (!directory.isDirectory()) {
            return null;
        }
        File[] files = directory.listFiles(file -> file.isFile());
        if (files == null) {
            return null;
        }
        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (NativeModelLoadPort.Format format : NativeModelLoadPort.Format.values()) {
            String extension = "." + format.name().toLowerCase();
            for (File file : files) {
                if (file.getName().toLowerCase().endsWith(extension)) {
                    return new ModelSource(modelName, file.getAbsolutePath(), directory.getAbsolutePath(), format);
                }
            }
        }
        return null;
    }
}
