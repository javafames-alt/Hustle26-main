#version 330
#extension GL_ARB_separate_shader_objects : require

// GlowESP composite (GlowRenderer.kt). MaskSampler holds the entity silhouettes (colour where an entity is, zero
// elsewhere), GlowSampler the same mask blurred at half resolution. Draws a soft halo outside the silhouette, a crisp
// rim along it and an optional tint over it, premultiplied.
layout(std140) uniform GlowInfo {
    vec4 ColorA;
    vec4 ColorB;
    // x: halo strength, y: fill alpha, z: rim alpha, w: time in seconds
    vec4 Params;
    // x: theme gradient instead of the entities' own colours, y: rim width in pixels, z: ColorA instead of the mask's colours
    vec4 Extra;
};

uniform sampler2D MaskSampler;
uniform sampler2D GlowSampler;

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

const vec2 DIRS[8] = vec2[](
    vec2(1.0, 0.0), vec2(-1.0, 0.0), vec2(0.0, 1.0), vec2(0.0, -1.0),
    vec2(0.7071, 0.7071), vec2(-0.7071, 0.7071), vec2(0.7071, -0.7071), vec2(-0.7071, -0.7071)
);

void main() {
    vec4 mask = texture(MaskSampler, texCoord);
    vec4 glow = texture(GlowSampler, texCoord);
    float inside = mask.a;
    // Most of the screen is far from any glowing entity: nothing would come out under half an 8-bit step there, so
    // skip the work and, by discarding, the blend into the frame as well (this pass covers the whole screen).
    if (inside <= 0.0 && glow.a * Params.x < 0.0007) {
        discard;
    }

    // rim: how much silhouette there is within Extra.y pixels, only where the pixel itself is outside it; its 16
    // taps are skipped entirely while the rim is off
    float ring = 0.0;
    if (Params.z > 0.0) {
        vec2 texel = 1.0 / vec2(textureSize(MaskSampler, 0));
        for (int i = 0; i < 8; i++) {
            ring = max(ring, texture(MaskSampler, texCoord + DIRS[i] * texel * Extra.y).a);
            ring = max(ring, texture(MaskSampler, texCoord + DIRS[i] * texel * Extra.y * 0.5).a);
        }
    }
    // ghosts (Ghosts.kt) mark themselves with red = green = 0 and carry their own fill in blue
    float fill = mask.r < 0.002 && mask.g < 0.002 && inside > 0.0 ? mask.b : Params.y;
    float rim = ring * (1.0 - inside) * Params.z;

    float wave = 0.5 + 0.5 * sin((texCoord.x * 1.6 + texCoord.y) * 4.0 - Params.w * 2.2);
    vec3 own = glow.a > 0.0005 ? glow.rgb / glow.a : (inside > 0.0 ? mask.rgb / inside : ColorA.rgb);
    vec3 base = Extra.x > 0.5 ? mix(ColorA.rgb, ColorB.rgb, wave) : (Extra.z > 0.5 ? ColorA.rgb : own);

    // the halo saturates smoothly instead of clipping, and stays off the entity itself so the model isn't washed out
    float halo = (1.0 - exp(-glow.a * Params.x * 2.5)) * (1.0 - inside);
    float a = halo;
    a += (1.0 - a) * rim;
    a += (1.0 - a) * inside * fill;
    vec3 rgb = mix(base, vec3(1.0), rim * 0.25);
    // 8-bit targets band on wide soft gradients: a little noise hides it
    float noise = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715)))) - 0.5;
    a = clamp(a + noise / 255.0, 0.0, 1.0);
    fragColor = vec4(rgb * a, a);
}
