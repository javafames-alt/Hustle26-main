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
    vec4 tex = texture(Sampler0, texCoord0);
    vec2 local = mod(texCoord0 * vec2(textureSize(Sampler0, 0)), 8.0) / 8.0;
    float radius = 0.23;
    vec2 q = abs(local - 0.5) - (0.5 - radius);
    float dist = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
    float aa = max(fwidth(dist), 1e-4);
    float mask = 1.0 - smoothstep(-aa * 0.5, aa * 0.5, dist);
    vec4 color = tex * vertexColor * ColorModulator;
    color.a *= mask;
    if (color.a <= 0.001) {
        discard;
    }
    fragColor = color;
}
