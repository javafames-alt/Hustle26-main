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

float median(vec3 c) {
    return max(min(c.r, c.g), min(max(c.r, c.g), c.b));
}

void main() {
    float dist = median(textureLod(Sampler0, texCoord0, 0.0).rgb) - 0.5;
    vec2 h = vec2(dFdx(texCoord0.x), dFdy(texCoord0.y)) * vec2(textureSize(Sampler0, 0));
    float pixels = max(10.0 * inversesqrt(max(h.x * h.x + h.y * h.y, 1e-8)), 1.0);
    float alpha = smoothstep(-0.5, 0.5, dist * pixels);
    vec4 color = vec4(vertexColor.rgb, vertexColor.a * alpha) * ColorModulator;
    if (color.a <= 0.001) {
        discard;
    }
    fragColor = color;
}
