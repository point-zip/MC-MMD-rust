// 负责定义并验证 Minecraft 1.21.5 MMD RenderPipeline 变体。
package com.shiroha.mmdskin.client.gpu;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.shiroha.mmdskin.bridge.render.NativeRenderDataPort;
import com.shiroha.mmdskin.client.draw.PipelineVariant;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public final class MmdRenderPipelines {
    private static final ResourceLocation SHADER = id("core/mmd_entity");
    private static final int MAX_PALETTE_BONES = NativeRenderDataPort.MAX_PALETTE_BONES;
    private static final RenderPipeline.Snippet UNFORMATTED_BASE = RenderPipeline.builder()
            .withVertexShader(SHADER)
            .withFragmentShader(SHADER)
            .withUniform("ModelViewMat", UniformType.MATRIX4X4)
            .withUniform("ProjMat", UniformType.MATRIX4X4)
            .withUniform("ColorModulator", UniformType.VEC4)
            .withUniform("FogStart", UniformType.FLOAT)
            .withUniform("FogEnd", UniformType.FLOAT)
            .withUniform("FogShape", UniformType.INT)
            .withUniform("FogColor", UniformType.VEC4)
            .withUniform("Light0_Direction", UniformType.VEC3)
            .withUniform("Light1_Direction", UniformType.VEC3)
            .withUniform("MmdModelMat", UniformType.MATRIX4X4)
            .withUniform("MmdMaterialColor", UniformType.VEC4)
            .withUniform("MmdAlpha", UniformType.FLOAT)
            .withUniform("MmdLightFactor", UniformType.FLOAT)
            .withUniform("MmdOutlineWidth", UniformType.FLOAT)
            .withUniform("MmdOutlineColor", UniformType.VEC3)
            .withUniform("MmdToonLevels", UniformType.INT)
            .withUniform("MmdToonShadowColor", UniformType.VEC3)
            .withUniform("MmdRimPower", UniformType.FLOAT)
            .withUniform("MmdRimIntensity", UniformType.FLOAT)
            .withUniform("MmdSpecularPower", UniformType.FLOAT)
            .withUniform("MmdSpecularIntensity", UniformType.FLOAT)
            .withUniform("MmdEmission", UniformType.FLOAT)
            .buildSnippet();
    private static final RenderPipeline.Snippet CPU_BASE = RenderPipeline.builder(UNFORMATTED_BASE)
            .withSampler("Sampler0")
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL, VertexFormat.Mode.TRIANGLES)
            .buildSnippet();
    private static final RenderPipeline.Snippet GPU_BASE = buildGpuBase();

    private static final Map<PipelineVariant, RenderPipeline> CPU_PIPELINES = createPipelines(false);
    private static final Map<PipelineVariant, RenderPipeline> GPU_PIPELINES = createPipelines(true);
    private static volatile boolean ready;
    private static volatile boolean gpuReady;

    private MmdRenderPipelines() {
    }

    public static RenderPipeline pipeline(PipelineVariant variant) {
        return pipeline(variant, false);
    }

    public static RenderPipeline pipeline(PipelineVariant variant, boolean gpuSkinning) {
        RenderPipeline pipeline = (gpuSkinning ? GPU_PIPELINES : CPU_PIPELINES).get(variant);
        if (pipeline == null) {
            throw new IllegalArgumentException("unsupported MMD pipeline variant: " + variant);
        }
        return pipeline;
    }

    public static List<RenderPipeline> all() {
        java.util.ArrayList<RenderPipeline> pipelines = new java.util.ArrayList<>(CPU_PIPELINES.values());
        pipelines.addAll(GPU_PIPELINES.values());
        return List.copyOf(pipelines);
    }

    public static boolean validateAndActivate(GpuDevice device) {
        return validateAndActivate(device, null);
    }

    public static boolean validateAndActivate(
            GpuDevice device,
            BiFunction<ResourceLocation, ShaderType, String> shaderSource) {
        boolean valid = validate(device, shaderSource, CPU_PIPELINES.values());
        boolean validGpu = validate(device, shaderSource, GPU_PIPELINES.values());
        ready = valid;
        gpuReady = validGpu;
        return valid;
    }

    private static boolean validate(GpuDevice device,
                                    BiFunction<ResourceLocation, ShaderType, String> shaderSource,
                                    java.util.Collection<RenderPipeline> pipelines) {
        boolean valid = true;
        for (RenderPipeline pipeline : pipelines) {
            CompiledRenderPipeline compiled = device.precompilePipeline(pipeline, shaderSource);
            valid &= compiled.isValid()
                    && compiled.containsUniform("MmdModelMat")
                    && compiled.containsUniform("MmdAlpha");
        }
        return valid;
    }

    public static void activateRegisteredPipelines() {
        ready = true;
        gpuReady = true;
    }

    public static void deactivate() {
        ready = false;
        gpuReady = false;
    }

    public static boolean isReady() {
        return ready;
    }

    public static boolean isGpuReady() {
        return ready && gpuReady;
    }

    private static Map<PipelineVariant, RenderPipeline> createPipelines(boolean gpu) {
        EnumMap<PipelineVariant, RenderPipeline> pipelines = new EnumMap<>(PipelineVariant.class);
        pipelines.put(PipelineVariant.OPAQUE, build("opaque", true, true, true, gpu));
        pipelines.put(PipelineVariant.OPAQUE_DOUBLE_SIDED, build("opaque_double_sided", false, true, true, gpu));
        pipelines.put(PipelineVariant.TRANSLUCENT, build("translucent", true, true, false, gpu));
        pipelines.put(PipelineVariant.TRANSLUCENT_DOUBLE_SIDED, build("translucent_double_sided", false, true, false, gpu));
        pipelines.put(PipelineVariant.TOON, buildToon("toon", true, true, true, gpu));
        pipelines.put(PipelineVariant.TOON_DOUBLE_SIDED, buildToon("toon_double_sided", false, true, true, gpu));
        pipelines.put(PipelineVariant.TOON_TRANSLUCENT, buildToon("toon_translucent", true, true, false, gpu));
        pipelines.put(PipelineVariant.TOON_TRANSLUCENT_DOUBLE_SIDED,
                buildToon("toon_translucent_double_sided", false, true, false, gpu));
        RenderPipeline.Snippet base = gpu ? GPU_BASE : CPU_BASE;
        String prefix = gpu ? "mmd_gpu_" : "mmd_";
        pipelines.put(PipelineVariant.TOON_OUTLINE, RenderPipeline.builder(base)
                .withLocation(id("pipeline/" + prefix + "toon_outline"))
                .withShaderDefine("MMD_OUTLINE")
                .withBlend(BlendFunction.TRANSLUCENT)
                .withCull(true)
                .withDepthWrite(false)
                .withVertexFormat(gpu ? DefaultVertexFormat.NEW_ENTITY : DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL, VertexFormat.Mode.TRIANGLES)
                .build());
        pipelines.put(PipelineVariant.GLOWING, RenderPipeline.builder(base)
                .withLocation(id("pipeline/" + prefix + "glowing"))
                .withShaderDefine("MMD_EMISSIVE")
                .withBlend(BlendFunction.TRANSLUCENT)
                .withCull(false)
                .withDepthWrite(false)
                .withVertexFormat(gpu ? DefaultVertexFormat.NEW_ENTITY : DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL, VertexFormat.Mode.TRIANGLES)
                .build());
        return pipelines;
    }

    private static RenderPipeline build(String name, boolean cull, boolean blend, boolean depthWrite, boolean gpu) {
        RenderPipeline.Builder builder = RenderPipeline.builder(gpu ? GPU_BASE : CPU_BASE)
                .withLocation(id("pipeline/" + (gpu ? "mmd_gpu_" : "mmd_") + name))
                .withCull(cull)
                .withDepthWrite(depthWrite)
                .withVertexFormat(gpu ? DefaultVertexFormat.NEW_ENTITY : DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL, VertexFormat.Mode.TRIANGLES);
        if (blend) {
            builder.withBlend(BlendFunction.TRANSLUCENT);
        }
        return builder.build();
    }

    private static RenderPipeline buildToon(String name, boolean cull, boolean blend, boolean depthWrite, boolean gpu) {
        RenderPipeline.Builder builder = RenderPipeline.builder(gpu ? GPU_BASE : CPU_BASE)
                .withLocation(id("pipeline/" + (gpu ? "mmd_gpu_" : "mmd_") + name))
                .withShaderDefine("MMD_TOON")
                .withCull(cull)
                .withDepthWrite(depthWrite)
                .withVertexFormat(gpu ? DefaultVertexFormat.NEW_ENTITY : DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL, VertexFormat.Mode.TRIANGLES);
        if (blend) {
            builder.withBlend(BlendFunction.TRANSLUCENT);
        }
        return builder.build();
    }

    private static RenderPipeline.Snippet buildGpuBase() {
        RenderPipeline.Builder builder = RenderPipeline.builder(UNFORMATTED_BASE)
                .withSampler("Sampler0")
                .withShaderDefine("MMD_GPU_SKINNING")
                .withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES);
        for (int index = 0; index < MAX_PALETTE_BONES; index++) {
            builder.withUniform("MmdBones[" + index + "]", UniformType.MATRIX4X4);
        }
        return builder.buildSnippet();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("mmdskin", path);
    }
}
