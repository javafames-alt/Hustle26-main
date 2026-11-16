#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 OutColor;
uniform sampler2D MaskSampler;
uniform sampler2D HistorySampler;
uniform sampler2D AppearanceSampler;

layout(std140) uniform HandFireStyle {
    vec4 Resolution;
    vec4 MainColor;
    vec4 SecondColor;
    vec4 Clock;
    vec4 Fire;
};

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0)), f.x), f.y);
}

bool outside(vec2 uv) {
    return any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)));
}

float hand(vec2 uv) {
    return outside(uv) ? 0.0 : texture(MaskSampler, uv).r;
}

vec4 past(vec2 uv) {
    return outside(uv) ? vec4(0.0) : texture(HistorySampler, uv);
}

void main() {
    vec2 uv = texCoord;
    vec2 px = 1.0 / max(Resolution.zw, vec2(1.0));
    float t = Clock.x * Fire.z;
    float dt = Clock.y;
    float height = Fire.y;
    float m = hand(uv);
    float turbulence = noise(uv * vec2(42.0, 28.0) - vec2(t * 0.8, t * 2.7));
    float detail = noise(uv * vec2(91.0, 63.0) + vec2(t * 0.5, -t * 4.1));

    float flame = 0.0;
    vec2 colorUV = uv;
    for (int i = 1; i <= 7; ++i) {
        float k = float(i) / 7.0;
        float lift = k * 0.047 * height * (0.42 + turbulence * 0.85);
        float curl = sin(uv.y * 55.0 + t * 3.1 + k * 3.5)
                   * 0.006 * height * k * (0.4 + detail);
        float tip = pow(1.0 - k * 0.90, 1.45);
        vec2 sourceUV = uv - vec2(curl, lift);
        float candidate = hand(sourceUV) * tip;
        if (candidate > flame) colorUV = sourceUV;
        flame = max(flame, candidate);
    }
    float fresh = max(m * 0.92, flame * (0.68 + detail * 0.16));

    vec3 sampled = texture(AppearanceSampler, clamp(colorUV, vec2(0.0), vec2(1.0))).rgb;
    float sampledLuma = dot(sampled, vec3(0.2126, 0.7152, 0.0722));
    vec3 lifted = sampled * (0.72 / max(sampledLuma, 0.16));
    lifted = clamp(lifted, vec3(0.035), vec3(1.35));
    vec3 base = SecondColor.a > 0.5
            ? mix(MainColor.rgb, lifted, 0.90)
            : MainColor.rgb;
    base *= 0.92 + turbulence * 0.18;

    vec4 old = vec4(0.0);
    if (Clock.z > 0.5 && Fire.x > 0.001) {
        vec2 velocity = vec2((turbulence - 0.5) * 0.025 * height,
                              0.055 * height) * Fire.z;
        vec2 sampleUV = uv - velocity * dt;
        vec4 center = past(sampleUV);
        vec4 soft = (past(sampleUV + vec2(px.x, 0.0))
                   + past(sampleUV - vec2(px.x, 0.0))
                   + past(sampleUV + vec2(0.0, px.y))
                   + past(sampleUV - vec2(0.0, px.y))) * 0.25;
        old = mix(center, soft, clamp(dt * 13.0, 0.0, 0.65));

        float faded = max(0.0, old.a - max(dt / Fire.x, 1.0 / 255.0));
        old.rgb *= faded / max(old.a, 0.00001);
        old.a = faded;
    }
    float alpha = max(fresh, old.a);
    vec3 oldColor = old.rgb / max(old.a, 0.00001);
    float sourceWeight = fresh / max(fresh + old.a * (1.0 - m), 0.00001);
    vec3 color = mix(oldColor, base, sourceWeight);
    if (alpha < 0.004) {
        OutColor = vec4(0.0);
    } else {
        OutColor = vec4(color * alpha, alpha);
    }
}
