#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

layout(std140) uniform MenuBackgroundData {
    vec4 menuData;
};

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash21(i), hash21(i + vec2(1.0, 0.0)), f.x),
        mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), f.x),
        f.y
    );
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.52;
    mat2 rotation = mat2(0.82, -0.57, 0.57, 0.82);
    for (int i = 0; i < 5; i++) {
        value += amplitude * valueNoise(p);
        p = rotation * p * 2.03 + 7.17;
        amplitude *= 0.48;
    }
    return value;
}

void main() {
    vec2 resolution = max(menuData.xy, vec2(1.0));
    float timeValue = menuData.z;
    float intensity = menuData.w;

    // texCoord follows framebuffer coordinates; GUI coordinates point downwards.
    vec2 uv = vec2(texCoord.x, 1.0 - texCoord.y);
    float aspect = resolution.x / resolution.y;
    vec2 p = vec2((uv.x - 0.5) * aspect, uv.y - 0.5);
    float time = timeValue * 0.045;

    vec2 warp = vec2(
        fbm(p * 1.15 + vec2(time, -time * 0.36)),
        fbm(p * 1.15 + vec2(-time * 0.29, time * 0.42) + 13.7)
    );
    vec2 drifted = p + (warp - 0.5) * 0.72;

    float broadFog = fbm(drifted * 1.85 + vec2(time * 0.35, 0.0));
    float fineFog = fbm(drifted * 3.10 - vec2(time * 0.18, time * 0.12));
    float fog = smoothstep(0.42, 0.82, broadFog * 0.72 + fineFog * 0.36);

    vec2 glowPoint = vec2(0.0, 0.10);
    float centerGlow = exp(-dot(p - glowPoint, p - glowPoint) * 3.2);
    float upperMist = smoothstep(0.92, 0.08, uv.y) * 0.18;
    float vignette = smoothstep(1.05, 0.24, length(p * vec2(0.72, 1.0)));
    float breathing = 0.93 + 0.07 * sin(timeValue * 0.16);
    float rayAlpha = clamp((fog * 0.62 + centerGlow * 0.26 + upperMist)
            * vignette * intensity * breathing, 0.0, 1.0);

    vec3 topColor = vec3(7.0, 7.0, 8.0) / 255.0;
    vec3 bottomColor = vec3(20.0, 20.0, 24.0) / 255.0;
    vec3 baseColor = mix(topColor, bottomColor, uv.y);
    vec3 rayColor = vec3(214.0, 216.0, 224.0) / 255.0;
    fragColor = vec4(mix(baseColor, rayColor, rayAlpha), 1.0);
}
