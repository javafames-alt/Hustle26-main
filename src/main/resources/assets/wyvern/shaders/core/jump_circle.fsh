#version 330
#extension GL_ARB_separate_shader_objects : require

#include <wyvern:jump_circle.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in vec3 localPos;
layout(location = 1) in vec2 surface;
layout(location = 2) in vec4 centreClip;

layout(location = 0) out vec4 fragColor;

void main() {
    JumpSample s = jumpSample(localPos, surface);
    if (s.coverage < 0.004) {
        discard;
    }

    vec4 tint = jumpTint(s.dir);
    vec4 color = texture(Sampler0, s.texCoord) * vec4(tint.rgb, tint.a * s.coverage);
    if (color.a < 0.004) {
        discard;
    }
    fragColor = color;
}
