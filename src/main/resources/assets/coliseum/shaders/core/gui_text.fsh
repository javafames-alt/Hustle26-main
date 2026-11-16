#version 330
#extension GL_ARB_separate_shader_objects : require

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

float textCoverage(vec2 uv, float pixelRange) {
    vec3 field = textureLod(Sampler0, uv, 0.0).rgb;
    float signedDistance = max(min(field.r, field.g), min(max(field.r, field.g), field.b)) - 0.5;
    return clamp(signedDistance * pixelRange + 0.5, 0.0, 1.0);
}

void main() {
    // Both Inter MSDF atlases encode a ten-texel distance range. Compute its
    // physical pixel footprint from UV derivatives, independent of edge angle.
    vec2 unitRange = vec2(10.0) / vec2(textureSize(Sampler0, 0));
    vec2 screenTexSize = 1.0 / max(fwidth(texCoord0), vec2(1e-7));
    float screenPxRange = max(0.5 * dot(unitRange, screenTexSize), 1.0);
    float coverage;
    if (screenPxRange < 2.0) {
        // Integrate four subpixel positions only for small text. This stabilizes
        // thin strokes while the menu moves or uses a fractional GUI scale.
        vec2 dx = dFdx(texCoord0) * 0.25, dy = dFdy(texCoord0) * 0.25;
        coverage = 0.25 * (textCoverage(texCoord0 - dx - dy, screenPxRange)
            + textCoverage(texCoord0 + dx - dy, screenPxRange)
            + textCoverage(texCoord0 - dx + dy, screenPxRange)
            + textCoverage(texCoord0 + dx + dy, screenPxRange));
    } else coverage = textCoverage(texCoord0, screenPxRange);
    vec4 color = vec4(vertexColor.rgb, vertexColor.a * coverage) * ColorModulator;
    if (color.a <= 0.001) discard;
    fragColor = color;
}
