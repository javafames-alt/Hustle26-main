#version 330
#extension GL_ARB_separate_shader_objects : require
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 OutColor;
uniform sampler2D SeedSampler;
layout(std140) uniform FlameField { vec4 Field; };

vec2 decodeSeed(vec4 packedSeed) {
    return vec2(dot(packedSeed.rg, vec2(65280.0, 255.0)),
                dot(packedSeed.ba, vec2(65280.0, 255.0))) / 65535.0;
}
void main() {
    float bestDistance = 1e20;
    vec4 best = vec4(0.0);
    for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
        vec2 uv = texCoord + vec2(x, y) * Field.z / Field.xy;
        if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) continue;
        vec4 candidate = texture(SeedSampler, uv);
        vec2 seed = decodeSeed(candidate);
        if (dot(seed, seed) < 1e-12) continue;
        vec2 delta = (seed - texCoord) * Field.xy;
        float distanceSquared = dot(delta, delta);
        if (distanceSquared < bestDistance) { bestDistance = distanceSquared; best = candidate; }
    }
    OutColor = best;
}
