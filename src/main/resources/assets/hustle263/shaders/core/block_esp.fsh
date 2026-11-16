#version 330
#extension GL_ARB_separate_shader_objects : require
layout(location = 0) in vec2 texCoord;
layout(location = 1) in vec4 vertexColor;
layout(location = 0) out vec4 fragColor;
void main() {
    vec2 edge = min(texCoord, 1.0 - texCoord);
    vec2 width = max(fwidth(texCoord), vec2(0.00001));
    float border = 1.0 - smoothstep(0.8, 1.8, min(edge.x / width.x, edge.y / width.y));
    fragColor = vec4(vertexColor.rgb, vertexColor.a * mix(0.07, 0.8, border));
}
