#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

// MTSDF atlas: RGB = multi-channel distance (sharp corners), A = true distance. Edge at 0.5.
uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) in vec2 style;

layout(location = 0) out vec4 fragColor;

float median(float r, float g, float b) {
    return max(min(r, g), min(max(r, g), b));
}

// Screen pixels per distance-field unit from which a glyph is big enough to skip supersampling.
const float LARGE_TEXT_RANGE = 7.0;

// How many screen pixels one unit of the distance field spans here.
float screenPxRange() {
    vec2 unitRange = vec2(PX_RANGE) / vec2(textureSize(Sampler0, 0));
    vec2 screenTexSize = vec2(1.0) / fwidth(texCoord0);
    return max(0.5 * dot(unitRange, screenTexSize), 1.0);
}

float signedDistance(vec2 uv) {
    vec4 field = texture(Sampler0, uv);
    float sd = median(field.r, field.g, field.b);
    // where the multi-channel field and the true distance clearly disagree about inside/outside,
    // the MSDF has an interpolation artifact: trust the single-channel distance there
    if ((sd - 0.5) * (field.a - 0.5) < 0.0 && abs(field.a - 0.5) > 0.2) {
        sd = field.a;
    }
    return sd;
}

void main() {
    float softness = style.x;
    float weight = style.y;
    // derivatives up front: inside branches that differ between neighbouring pixels they're undefined
    float pxRange = screenPxRange();
    vec2 dx = dFdx(texCoord0);
    vec2 dy = dFdy(texCoord0);
    float alpha;
    if (softness > 0.0) {
        // blurry variant for shadows / glows
        float sd = signedDistance(texCoord0);
        alpha = smoothstep(0.5 - weight - softness, 0.5 - weight + softness * 0.5, sd);
    } else if (pxRange >= LARGE_TEXT_RANGE) {
        // big text: one sample's one-pixel ramp is already smooth, supersampling would only cost 3 more fetches
        alpha = clamp(pxRange * (signedDistance(texCoord0) - 0.5 + weight) + 0.5, 0.0, 1.0);
    } else {
        // 4x rotated-grid supersampling: each sample gets a half-pixel ramp, their average
        // gives clean edges on small text where a single sample shows stair steps
        float range = pxRange * 2.0;
        alpha = 0.0;
        alpha += clamp(range * (signedDistance(texCoord0 + dx * -0.125 + dy * -0.375) - 0.5 + weight) + 0.5, 0.0, 1.0);
        alpha += clamp(range * (signedDistance(texCoord0 + dx *  0.375 + dy * -0.125) - 0.5 + weight) + 0.5, 0.0, 1.0);
        alpha += clamp(range * (signedDistance(texCoord0 + dx *  0.125 + dy *  0.375) - 0.5 + weight) + 0.5, 0.0, 1.0);
        alpha += clamp(range * (signedDistance(texCoord0 + dx * -0.375 + dy *  0.125) - 0.5 + weight) + 0.5, 0.0, 1.0);
        alpha *= 0.25;
    }

    vec4 color = vertexColor * ColorModulator;
    color.a *= alpha;
    if (color.a <= 0.001) {
        discard;
    }
    fragColor = color;
}
