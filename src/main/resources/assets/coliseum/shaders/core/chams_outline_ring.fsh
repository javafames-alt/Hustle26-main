#version 330
#extension GL_ARB_separate_shader_objects : require
// Chams «Обводка», pass 2: exact euclidean distance to the silhouette (rows + vertical scan) turned into an
// anti-aliased ring. Depth tested against the scene unless «Сквозь стены» is on.
// Output: r = line coverage, g = visible body (for the fill), b = damage tint, a = tube profile of the line.
uniform sampler2D RowSampler;
uniform sampler2D MaskSampler;
uniform sampler2D MaskDepthSampler;
uniform sampler2D SceneDepthSampler;
// Ring: line width (px), search radius (px), through walls (0/1), relative depth tolerance.
layout(std140) uniform ChamsOutlineRing { vec4 Ring; };
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

float shown(float scene, float depth) {
    if (Ring.z > 0.5) return 1.0;
    // Reversed depth: a larger value is nearer to the camera.
    return scene > depth * (1.0 + Ring.w) + 0.0000001 ? 0.0 : 1.0;
}

void main() {
    ivec2 size = textureSize(MaskSampler, 0);
    ivec2 p = clamp(ivec2(gl_FragCoord.xy), ivec2(0), size - 1);
    float scene = texelFetch(SceneDepthSampler, p, 0).r;
    vec4 own = texelFetch(MaskSampler, p, 0);
    if (own.a > 0.5) {
        float visible = shown(scene, texelFetch(MaskDepthSampler, p, 0).r);
        fragColor = vec4(0.0, visible, (1.0 - own.g) * visible, 0.0);
        return;
    }
    float lineWidth = max(Ring.x, 1.0);
    int radius = clamp(int(Ring.y + 0.5), 1, 24);
    float best = 1.0e9;
    ivec2 hit = p;
    for (int dy = -24; dy <= 24; dy++) {
        if (dy < -radius || dy > radius) continue;
        int y = p.y + dy;
        if (y < 0 || y >= size.y) continue;
        vec4 row = texelFetch(RowSampler, ivec2(p.x, y), 0);
        if (row.b < 0.5) continue;
        float dx = floor(row.r * 255.0 + 0.5);
        float d2 = dx * dx + float(dy * dy);
        if (d2 < best) {
            best = d2;
            hit = ivec2(p.x + (row.g > 0.5 ? int(dx) : -int(dx)), y);
        }
    }
    if (best > 1.0e8) {
        fragColor = vec4(0.0);
        return;
    }
    float d = sqrt(best);
    // The silhouette edge sits half a pixel from the nearest covered pixel centre.
    float coverage = clamp(lineWidth + 1.0 - d, 0.0, 1.0);
    if (coverage <= 0.0) {
        fragColor = vec4(0.0);
        return;
    }
    ivec2 q = clamp(hit, ivec2(0), size - 1);
    float visible = shown(scene, texelFetch(MaskDepthSampler, q, 0).r);
    float damage = 1.0 - texelFetch(MaskSampler, q, 0).g;
    float u = (d - (lineWidth + 1.0) * 0.5) / max(lineWidth * 0.5, 0.5);
    float tube = clamp(1.0 - u * u, 0.0, 1.0);
    float line = coverage * visible;
    fragColor = vec4(line, 0.0, damage * line, tube);
}
