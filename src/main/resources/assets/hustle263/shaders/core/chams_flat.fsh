#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexPerFaceColorBack;
layout(location = 3) in vec4 vertexPerFaceColorFront;
layout(location = 4) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

void main() {
    float textureAlpha = texture(Sampler0, texCoord0).a;
    if (textureAlpha < 0.1) discard;

    
    vec4 faceColor = gl_FrontFacing ? vertexPerFaceColorBack : vertexPerFaceColorFront;
    vec4 color = vec4(faceColor.rgb, faceColor.a * textureAlpha) * ColorModulator;
    
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd,
            FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
