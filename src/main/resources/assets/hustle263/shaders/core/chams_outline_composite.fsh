#version 330
#extension GL_ARB_separate_shader_objects : require
// Chams «Обводка», final pass: colours the ring (solid / flowing gradient / rainbow), adds a soft neon halo
// from the blurred ring and an optional translucent fill of the model. Premultiplied output.
uniform sampler2D RingSampler;
uniform sampler2D GlowSampler;
layout(std140) uniform ChamsOutline {
    vec4 ColorA;   // main colour (rgba)
    vec4 ColorB;   // second gradient colour
    vec4 Damage;   // damage colour, a = indication on
    vec4 Params;   // fill, halo strength, time, style (0 colour, 1 gradient, 2 rainbow)
    vec4 Params2;  // aspect, line width (px)
};
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

vec3 hsv(float h, float s, float v) {
    vec3 k = clamp(abs(fract(h + vec3(0.0, 2.0, 1.0) / 3.0) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
    return v * mix(vec3(1.0), k, s);
}

vec3 palette(vec2 uv) {
    float t = Params.z;
    vec2 q = vec2(uv.x * Params2.x, uv.y);
    if (Params.w > 1.5) {
        return hsv(fract(dot(q, vec2(0.55, 0.35)) * 1.8 - t * 0.22), 0.68, 1.0);
    }
    if (Params.w > 0.5) {
        float wave = 0.5 + 0.5 * sin(dot(q, vec2(0.83, 0.56)) * 7.0 - t * 2.4);
        return mix(ColorA.rgb, ColorB.rgb, smoothstep(0.08, 0.92, wave));
    }
    return ColorA.rgb;
}

void main() {
    vec4 ring = texture(RingSampler, texCoord);
    vec4 glow = texture(GlowSampler, texCoord);
    float line = ring.r;
    float body = ring.g;
    float halo = glow.r * Params.y;
    halo = halo / (1.0 + 0.4 * halo);
    if (line < 0.002 && body * Params.x < 0.002 && halo < 0.004) discard;

    vec3 base = palette(texCoord);
    float lineDamage = clamp(ring.b / max(line + body, 0.001), 0.0, 1.0) * Damage.a;
    float glowDamage = clamp(glow.b / max(glow.r + glow.g, 0.001), 0.0, 1.0) * Damage.a;
    vec3 lineColor = mix(base, Damage.rgb, lineDamage);
    vec3 glowColor = mix(base, Damage.rgb, glowDamage);

    // Thick lines get a bright tube core, thin ones stay a clean solid stroke.
    float tube = smoothstep(1.5, 3.5, Params2.y);
    float core = ring.a;
    vec3 neon = mix(lineColor * (0.82 + 0.30 * core), vec3(1.0), core * core * 0.28 * tube);

    float lineA = line * ColorA.a;
    float fillA = body * Params.x * ColorA.a;
    float light = (1.0 - lineA) * halo * mix(0.9, 0.45, body) * 0.55;
    vec3 rgb = neon * lineA + lineColor * fillA * (1.0 - lineA) + glowColor * light;
    float alpha = clamp(lineA + fillA * (1.0 - lineA) + light * 0.3, 0.0, 1.0);
    fragColor = vec4(rgb, alpha);
}
