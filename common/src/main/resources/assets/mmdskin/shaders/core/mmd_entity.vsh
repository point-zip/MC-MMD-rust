#version 150
// 文件职责：把 CPU 或 palette GPU 蒙皮后的 MMD 顶点转换到实体渲染空间。

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;
#ifdef MMD_GPU_SKINNING
in ivec2 UV1;
in ivec2 UV2;
uniform mat4 MmdBones[48];
#endif

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 MmdModelMat;
uniform int FogShape;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;
uniform float MmdOutlineWidth;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec3 viewNormal;
out vec3 viewPosition;
out vec3 viewLightDirection;
out float outlineFade;

void main() {
    vec3 skinPosition = Position;
    vec3 skinNormal = Normal;
    vec4 lightColor = Color;
#ifdef MMD_GPU_SKINNING
    mat4 skinMatrix = MmdBones[UV1.x] * Color.r
        + MmdBones[UV1.y] * Color.g
        + MmdBones[UV2.x] * Color.b
        + MmdBones[UV2.y] * Color.a;
    skinPosition = (skinMatrix * vec4(Position, 1.0)).xyz;
    skinNormal = normalize(mat3(skinMatrix) * Normal);
    lightColor = vec4(1.0);
#endif
    vec3 transformedNormal = normalize(mat3(MmdModelMat) * skinNormal);
    vec4 modelPosition = MmdModelMat * vec4(skinPosition, 1.0);
    vec4 baseViewPosition = ModelViewMat * modelPosition;
    float viewDepth = length(baseViewPosition.xyz);
    outlineFade = 1.0 - smoothstep(25.0, 40.0, viewDepth);
#ifdef MMD_OUTLINE
    float distanceWidth = mix(0.8, 1.2, smoothstep(0.0, 25.0, viewDepth));
    modelPosition.xyz += transformedNormal * MmdOutlineWidth * distanceWidth;
#endif
    vec4 viewPos = ModelViewMat * modelPosition;
    gl_Position = ProjMat * viewPos;
    vertexDistance = fog_distance(viewPos.xyz, FogShape);
    vertexColor = minecraft_mix_light(
        Light0_Direction,
        Light1_Direction,
        transformedNormal,
        lightColor
    );
    texCoord0 = UV0;
    viewNormal = normalize(mat3(ModelViewMat) * transformedNormal);
    viewPosition = viewPos.xyz;
    viewLightDirection = normalize(mat3(ModelViewMat) * vec3(1.0, 0.75, 0.0));
}
