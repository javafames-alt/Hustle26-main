#version 330
#extension GL_ARB_separate_shader_objects : require

// Quick's particle shader: the sprite times the color, added to the scene (the color comes packed as a light
// amount, see ParticleManager.encode, hence the doubling).
#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

const float FUSED_ADDITIVE_SCALE = 2.0;

void main() {
#ifdef HALO_SCALE
    // The quad is HALO_SCALE times the sprite: the sprite in the middle, and around it a soft radial halo worked
    // out here instead of drawn as two more, larger sprites (one quad, a third less area to fill, no edges).
    vec2 p = texCoord0 * 2.0 - 1.0;
    vec2 spriteUv = p * HALO_SCALE * 0.5 + 0.5;
    vec4 texel = all(greaterThanEqual(spriteUv, vec2(0.0))) && all(lessThanEqual(spriteUv, vec2(1.0)))
        ? texture(Sampler0, spriteUv) : vec4(0.0);
    // distance in sprite radii: the halo reads like the old 2x and 4x layers, a brighter inner glow and a faint wide one
    float r = length(p) * HALO_SCALE;
    float inner = max(1.0 - r * 0.5, 0.0);
    float outer = max(1.0 - r * 0.25, 0.0);
    float halo = 0.25 * inner * inner + 0.125 * outer * outer;
#else
    vec4 texel = texture(Sampler0, texCoord0);
    float halo = 0.0;
#endif
    float strength = vertexColor.a * ColorModulator.a;
    float coverage = texel.a * strength;
    float glow = halo * strength;
    if (coverage + glow <= 0.0) {
        discard;
    }
    vec3 tint = (texel.rgb * coverage + vec3(glow)) * vertexColor.rgb * ColorModulator.rgb;
    fragColor = vec4(tint * FUSED_ADDITIVE_SCALE, min(coverage + glow, 1.0));
}
