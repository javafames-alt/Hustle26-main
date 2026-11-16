#version 330
#extension GL_ARB_separate_shader_objects : require
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 OutColor;
uniform sampler2D HandDepthSampler;
uniform sampler2D DistanceSampler;
layout(std140) uniform HandFlame {
    vec4 Resolution;
    vec4 FlameColor;
    vec4 Flow;
    vec4 Glow;
};

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}
float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), f.x),
               mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), f.x), f.y);
}
float turbulence(vec2 p) {
    return noise(p) * .57 + noise(p * 2.07 + 8.3) * .28 + noise(p * 4.13 - 3.7) * .15;
}
vec2 nearestSeed(vec2 uv) {
    vec4 seed = texture(DistanceSampler, uv);
    return vec2(dot(seed.rg, vec2(65280.0, 255.0)),
                dot(seed.ba, vec2(65280.0, 255.0))) / 65535.0;
}
float distanceAt(vec2 uv) {
    if (any(lessThan(uv, vec2(0))) || any(greaterThan(uv, vec2(1)))) return 10000.0;
    vec2 seed = nearestSeed(uv);
    if (dot(seed, seed) < 1e-12) return 10000.0;
    // Seeds represent reduced-resolution pixels; measure from their outer boundary.
    float footprint = max(Resolution.x / Resolution.z, Resolution.y / Resolution.w);
    return max(0.0, length((uv - seed) * Resolution.xy) - footprint * .55);
}
void main() {
    vec2 uv = texCoord;
    float hand = step(.0001, texture(HandDepthSampler, uv).r);
    // The held item's texture and shading stay intact, including its interior.
    if (hand > .5 || FlameColor.a <= .001) discard;
    float distanceToHand = distanceAt(uv);
    float scale = max(Resolution.y / 1080.0, .5);
    float height = Flow.y * 70.0 * scale;
    float glowWidth = (10.0 + Glow.x * 13.0) * scale;
    if (distanceToHand > max(height + 14.0 * scale, glowWidth * 4.5)) discard;

    vec2 p = gl_FragCoord.xy / max(scale, .001);
    float t = Flow.x * Flow.z;
    float n = turbulence(p / vec2(34.0, 66.0) - vec2(t * .36, t * 1.65));
    float wave = sin(p.y * .038 - t * 2.4 + n * 4.0);
    float curl = (wave * .65 + (n - .5) * 1.5) * (3.0 + Flow.w * 7.0) * scale;
    float plume = 0.0;
    // Sample the ribbon from its base so thin tips do not produce detached copies.
    float tongue = .25 + .75 * turbulence(vec2(p.x * .031, p.y * .006 - t * 1.2));
    int steps = clamp(int(ceil(height * tongue / (2.0 * scale))), 6, 36);
    for (int i = 0; i <= steps; i++) {
        float k = float(i) / float(steps);
        vec2 source = uv - vec2(curl * k, height * k * tongue) / Resolution.xy;
        float sourceDistance = distanceAt(source);
        float softness = (1.2 + k * 3.7) * scale;
        float body = 1.0 - smoothstep(0.0, softness, sourceDistance);
        float taper = pow(1.0 - k * .86, 1.35);
        plume = max(plume, body * taper);
    }
    float filaments = smoothstep(.28, .82, n + wave * .14);
    plume *= .22 + .78 * filaments;
    // Narrow white-hot edge and a wider pink halo match the supplied references.
    float rim = 1.0 - smoothstep(.65 * scale, 2.8 * scale, distanceToHand);
    vec2 pixel = max(1.0, scale * 1.5) / Resolution.xy;
    for (int i = 0; i < 8; i++) {
        float angle = float(i) * .785398163;
        float neighbor = texture(HandDepthSampler, uv + vec2(cos(angle), sin(angle)) * pixel).r;
        rim = max(rim, step(.0001, neighbor));
    }
    float halo = exp(-distanceToHand / max(glowWidth, .01)) * Glow.x * .55;
    float wisps = plume * (1.0 - smoothstep(height * .42 + scale, height + 8.0 * scale, distanceToHand));
    float energy = rim * 2.2 + wisps * 1.65 + halo;
    if (energy * FlameColor.a < .002) discard;
    vec3 pale = mix(FlameColor.rgb, vec3(1.0), .66);
    vec3 color = mix(FlameColor.rgb, pale, clamp(wisps * .55 + rim, 0.0, 1.0));
    color = mix(color, vec3(1.0, .96, 1.0), rim * .76);
    OutColor = vec4(color, clamp(energy * FlameColor.a, 0.0, 1.0));
}
