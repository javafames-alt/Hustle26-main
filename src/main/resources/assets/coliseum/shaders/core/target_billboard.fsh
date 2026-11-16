#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location=0) in vec2 vUv;
layout(location=1) in vec4 vColor;

layout(location=0) out vec4 fragColor;

uniform sampler2D Sampler0;

void main() {
    vec4 tex = texture(Sampler0, vUv);
    fragColor = tex * vColor;
    if (fragColor.a <= 0.001) {
        discard;
    }
}
