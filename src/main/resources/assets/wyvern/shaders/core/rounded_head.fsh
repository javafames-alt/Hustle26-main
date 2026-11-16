#version 330
#extension GL_ARB_separate_shader_objects : require

// vanilla position_tex_color, with the face's corners rounded off (see WyvernPipelines.ROUNDED_HEAD)
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

float roundedBoxSdf(vec2 p, vec2 size, float radius) {
    vec2 q = abs(p) - size + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

void main() {
    // where we are on the 8x8 face of a 64x64 skin: the face is at u 8..16, the hat layer at u 40..48,
    // both at v 8..16. Each from its own left edge, no wrap-around: the derivatives at the quad's edge take
    // in neighbours just outside it, and a wrapped one far across the face blew up the edge smoothing
    // (a see-through stripe down the left side)
    vec2 px = texCoord0 * 64.0;
    float fx = px.x >= 28.0 ? px.x - 40.0 : px.x - 8.0;
    vec2 local = vec2(fx, px.y - 8.0) / 8.0 * 2.0 - 1.0;
    float dist = roundedBoxSdf(local, vec2(1.0), RADIUS);
    float aa = max(length(vec2(dFdx(dist), dFdy(dist))), 0.0001);
    float coverage = clamp(0.5 - dist / aa, 0.0, 1.0);

    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    color.a *= coverage;
    if (color.a <= 0.001) {
        discard;
    }
    fragColor = color * ColorModulator;
}
