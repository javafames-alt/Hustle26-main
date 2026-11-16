#version 330
#extension GL_ARB_separate_shader_objects : require
// Chams «Обводка», pass 1: for every pixel find the nearest silhouette pixel in the same row.
// r = horizontal distance / 255, g = 1 when it lies to the right, b = 1 when something was found.
uniform sampler2D MaskSampler;
layout(std140) uniform ChamsOutlineRows { vec4 Rows; };
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

bool covered(ivec2 q, ivec2 size) {
    if (q.x < 0 || q.x >= size.x) return false;
    return texelFetch(MaskSampler, q, 0).a > 0.5;
}

void main() {
    ivec2 size = textureSize(MaskSampler, 0);
    ivec2 p = clamp(ivec2(gl_FragCoord.xy), ivec2(0), size - 1);
    int radius = clamp(int(Rows.x + 0.5), 1, 24);
    for (int k = 0; k <= 24; k++) {
        if (k > radius) break;
        bool right = covered(p + ivec2(k, 0), size);
        bool left = k > 0 && covered(p - ivec2(k, 0), size);
        if (right || left) {
            fragColor = vec4(float(k) / 255.0, right ? 1.0 : 0.0, 1.0, 1.0);
            return;
        }
    }
    fragColor = vec4(1.0, 0.0, 0.0, 1.0);
}
