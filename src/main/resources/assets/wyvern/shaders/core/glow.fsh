#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

layout(location = 0) in vec2 local;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

// Soft radial glow drawn procedurally (no texture): wide falloff plus a hot core.
// Blended additively, so overlapping glows brighten like light does.
void main() {
    float d = length(local);
    if (d >= 1.0) {
        discard;
    }
    float halo = pow(1.0 - d, 2.4);
    float core = smoothstep(0.32, 0.0, d);
    vec4 color = vertexColor * ColorModulator;
    // the core drifts towards white for a light-source look
    vec3 rgb = mix(color.rgb, vec3(1.0), core * 0.55);
    // additive light piles up in 8 bits: a little noise keeps wide halos from showing rings
    float noise = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715)))) - 0.5;
    fragColor = vec4(rgb, max(color.a * (halo + core * 0.6) + noise / 255.0, 0.0));
}
