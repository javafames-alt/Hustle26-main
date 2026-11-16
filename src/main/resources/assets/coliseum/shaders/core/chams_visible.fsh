#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D BodySampler;
uniform sampler2D BodyDepthSampler;
uniform sampler2D ArmorDepthSampler;
uniform sampler2D TerrainDepthSampler;
uniform sampler2D SceneDepthSampler;
layout(std140) uniform ChamsVisibility { vec4 Options; };
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
void main() {
    vec4 body = texture(BodySampler, texCoord);
    if (body.a < 0.001) { fragColor = vec4(0.0); return; }
    float depth = texture(BodyDepthSampler, texCoord).r;
    
    if (Options.x < 0.5) {
        if (depth < texture(TerrainDepthSampler, texCoord).r - 0.000001) {
            fragColor = vec4(0.0); return;
        }
        
        if (Options.y < 0.5 && depth < texture(SceneDepthSampler, texCoord).r - 0.000001) {
            fragColor = vec4(0.0); return;
        }
    }
    if (Options.y < 0.5 && texture(ArmorDepthSampler, texCoord).r > depth + 0.0000001) {
        fragColor = vec4(0.0); return;
    }
    fragColor = body;
}
