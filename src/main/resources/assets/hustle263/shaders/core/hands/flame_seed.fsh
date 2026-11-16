#version 330
#extension GL_ARB_separate_shader_objects : require
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 OutColor;
uniform sampler2D HandDepthSampler;

// Two 16-bit UV coordinates packed into RGBA8. Zero marks an empty pixel.
vec4 encodeSeed(vec2 uv) {
    vec2 q = floor(clamp(uv, vec2(1.0 / 65535.0), vec2(1.0)) * 65535.0 + .5);
    return vec4(floor(q.x / 256.0), mod(q.x, 256.0), floor(q.y / 256.0), mod(q.y, 256.0)) / 255.0;
}
void main() {
    vec2 texel = 1.0 / vec2(textureSize(HandDepthSampler, 0));
    vec2 seed = texCoord;
    bool found = texture(HandDepthSampler, seed).r > .0001;
    // Preserve thin edges when reducing the depth silhouette.
    for (int i = 0; i < 4 && !found; i++) {
        vec2 offset = vec2((i & 1) == 0 ? -.5 : .5, (i & 2) == 0 ? -.5 : .5);
        seed = texCoord + offset * texel;
        found = texture(HandDepthSampler, seed).r > .0001;
    }
    OutColor = found ? encodeSeed(seed) : vec4(0.0);
}
