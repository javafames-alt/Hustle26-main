#version 330
#extension GL_ARB_separate_shader_objects : require
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};
uniform sampler2D Sampler0;
layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;
layout(location = 0) out vec4 fragColor;
void main() {
    vec3 field = textureLod(Sampler0, texCoord0, 0.0).rgb;
    float distance = max(min(field.r, field.g), min(max(field.r, field.g), field.b)) - 0.5;
    // Smooth subpixel coverage stays stable at small GUI text sizes.
    float coverage = smoothstep(-0.65, 0.65, distance / max(fwidth(distance), 0.0001));
    vec4 color = vec4(vertexColor.rgb, vertexColor.a * coverage) * ColorModulator;
    if (color.a <= 0.001) discard;
    fragColor = color;
}
