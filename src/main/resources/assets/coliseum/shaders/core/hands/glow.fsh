#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 OutColor;
uniform sampler2D HandDepth;

layout(std140) uniform HandShaderUniforms {
    vec4 TintColor;
    float Time;
    float Intensity;
    float Speed;
    float Length;
    float Smoke;
    float Fade;
    float Softness;
    float Blur;
};

float handMask(vec2 uv) {
    return smoothstep(0.0001, 0.0004, texture(HandDepth, uv).r);
}

float octagonMask(vec2 uv, vec2 radius) {
    const float D = 0.70710678;
    float result = 0.0;
    result = max(result, handMask(uv + vec2( radius.x, 0.0)));
    result = max(result, handMask(uv + vec2(-radius.x, 0.0)));
    result = max(result, handMask(uv + vec2(0.0,  radius.y)));
    result = max(result, handMask(uv + vec2(0.0, -radius.y)));
    result = max(result, handMask(uv + vec2( radius.x * D,  radius.y * D)));
    result = max(result, handMask(uv + vec2(-radius.x * D,  radius.y * D)));
    result = max(result, handMask(uv + vec2( radius.x * D, -radius.y * D)));
    result = max(result, handMask(uv + vec2(-radius.x * D, -radius.y * D)));
    return result;
}

void main() {
    vec2 uv = texCoord;
    vec2 px = 1.0 / vec2(textureSize(HandDepth, 0));
    float widthScale = clamp(Blur, 0.45, 2.0);
    float strength = clamp(Intensity, 0.4, 2.0);
    float body = handMask(uv);

    float farMask = max(body, octagonMask(uv, px * (8.5 * widthScale)));
    if (farMask <= 0.001) discard;

    float nearMask = max(body, octagonMask(uv, px * (2.35 * widthScale)));
    float whiteEdge = max(nearMask - body, 0.0);
    float halo = max(farMask - nearMask, 0.0);
    float innerEdge = body * clamp(fwidth(body) * 3.5, 0.0, 1.0);

    float pulse = 0.95 + 0.05 * sin(Time * 2.4);

    float waveSpeed = clamp(Speed, 0.0, 3.0);
    float waveAmount = clamp(Smoke, 0.0, 1.5);
    float wave = 0.0;
    if (waveAmount > 0.001) {
        float path = uv.y * 1.9 + uv.x * 0.55;
        float phase = fract(path - Time * waveSpeed * 0.35);
        float crest = pow(max(1.0 - abs(phase * 2.0 - 1.0), 0.0), 7.0);
        float trail = pow(max(1.0 - abs(fract(phase + 0.14) * 2.0 - 1.0), 0.0), 2.0) * 0.26;
        wave = (crest + trail) * waveAmount;
    }
    float edgeBand = clamp(innerEdge * 1.15 + whiteEdge * 1.60 + halo * 0.55, 0.0, 1.0);
    float edgeWave = wave * edgeBand;

    vec3 electricBlue = vec3(0.24, 0.62, 1.0);
    vec3 auraColor = mix(TintColor.rgb, electricBlue, 0.52);
    vec3 edgeColor = mix(auraColor, vec3(1.0), 0.88);
    float edgeEnergy = clamp(innerEdge * 0.86 + whiteEdge * 1.35, 0.0, 1.0);
    vec3 color = mix(auraColor, edgeColor, clamp(edgeEnergy + edgeWave * 0.75, 0.0, 1.0));

    float alpha = TintColor.a * strength * pulse
            * (body * 0.045 + innerEdge * 0.72
            + whiteEdge * 1.08 + halo * 0.30)
            * (1.0 + edgeWave * 1.35);
    alpha += TintColor.a * strength * edgeWave * (whiteEdge * 0.30 + halo * 0.16);
    alpha = clamp(alpha, 0.0, 1.0);
    if (alpha <= 0.001) discard;
    OutColor = vec4(color, alpha);
}
