#version 330
#extension GL_ARB_separate_shader_objects : require

#include <hustle263:coliseimvisual/depth.glsl>
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

uniform sampler2D DepthSampler;
layout(std140) uniform WetSurfaceData {
    mat4 InvProjection;
    mat4 InvView;
    vec4 WetnessCoverageDistance;
    vec4 CameraOrigin;
};

float hashCell(vec2 cell) {
    cell = fract(cell * vec2(.1031, .1099));
    cell += dot(cell, cell.yx + 19.19);
    return fract(cell.x * cell.y);
}

float groundMask(vec2 position) {
    vec2 cell = floor(position);
    vec2 f = smoothstep(vec2(0.0), vec2(1.0), fract(position));
    return mix(mix(hashCell(cell), hashCell(cell + vec2(1, 0)), f.x),
               mix(hashCell(cell + vec2(0, 1)), hashCell(cell + vec2(1, 1)), f.x), f.y);
}

void main() {
    float depth = texture(DepthSampler, texCoord).r;
    vec4 clip = vec4(texCoord * 2.0 - 1.0, HUSTLE_DEPTH_TO_CLIP(depth), 1.0);
    vec4 viewH = InvProjection * clip;
    vec3 view = viewH.xyz / max(abs(viewH.w), .000001);
    vec3 normal = cross(dFdx(view), dFdy(view));
    if (normal.z < 0.0) normal = -normal;
    normal = (InvView * vec4(normal, 0.0)).xyz;

    float distance = length(view);
    float limit = WetnessCoverageDistance.z;
    if (depth <= .00001 || depth >= .99999 || distance >= limit) discard;
    float up = normal.y / max(length(normal), .000001);
    float flatness = smoothstep(.75, .96, up);
    if (flatness <= 0.0) discard;

    float coverage = WetnessCoverageDistance.y;
    float mask = 1.0;
    if (coverage < 1.0) {
        vec3 world = (InvView * vec4(view, 1.0)).xyz + CameraOrigin.xyz;
        float threshold = 1.0 - coverage;
        mask = smoothstep(threshold - .09, threshold + .09, groundMask(world.xz * .5));
    }
    float fade = 1.0 - smoothstep(limit * .7, limit, distance);
    float wet = clamp(WetnessCoverageDistance.x, 0.0, 1.0) * mask * flatness * fade;
    fragColor = vec4(.046, .064, .076, wet * .29);
}
