#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 OutColor;
uniform sampler2D MaskSampler;
uniform sampler2D FireSampler;
uniform sampler2D GlowSampler;

layout(std140) uniform HandFireStyle {
    vec4 Resolution;
    vec4 MainColor;
    vec4 SecondColor;
    vec4 Clock;
    vec4 Fire;
};

float hand(vec2 uv) {
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) return 0.0;
    return texture(MaskSampler, uv).r;
}

void main() {
    vec2 uv = texCoord;
    vec2 px = 1.0 / max(Resolution.zw, vec2(1.0));
    float m = hand(uv);
    vec4 field = texture(FireSampler, uv);
    vec4 blurred = texture(GlowSampler, uv);
    float a = hand(uv + vec2(px.x * 1.15, 0.0));
    float b = hand(uv - vec2(px.x * 1.15, 0.0));
    float c = hand(uv + vec2(0.0, px.y * 1.15));
    float d = hand(uv - vec2(0.0, px.y * 1.15));
    float outer = max(0.0, max(max(a, b), max(c, d)) - m);
    float inner = max(0.0, m - min(min(a, b), min(c, d)));
    float edge = outer * 0.85 + inner * 0.34;
    float outside = 1.0 - m;
    float plume = field.a * outside;
    float halo = blurred.a * outside * Fire.w;

    float fill = m * field.a * 0.075;
    float energy = plume * 0.82 + halo * 0.78 + edge * 0.70 + fill;
    if (energy * MainColor.a < 0.001) discard;

    vec3 fireColor = field.rgb / max(field.a, 0.00001);
    vec3 glowColor = blurred.rgb / max(blurred.a, 0.00001);
    vec3 color = mix(glowColor, fireColor, smoothstep(0.04, 0.50, field.a));
    color = mix(color, vec3(1.0), clamp(edge * 0.16, 0.0, 0.16));
    OutColor = vec4(color, MainColor.a * clamp(energy, 0.0, 1.35));
}
