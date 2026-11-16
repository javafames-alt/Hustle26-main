#version 330
#extension GL_ARB_separate_shader_objects : require
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 OutColor;
uniform sampler2D TrailSampler;
uniform sampler2D MaskSampler;
layout(std140) uniform FireComposite { vec4 Params; };
void main() {
    vec4 trail = texture(TrailSampler, texCoord);
    float outside = 1.0 - texture(MaskSampler, texCoord).r;
    OutColor = trail * outside * Params.x;
}
