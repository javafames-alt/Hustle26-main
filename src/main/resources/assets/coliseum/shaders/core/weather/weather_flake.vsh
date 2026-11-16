#version 330
#extension GL_ARB_separate_shader_objects : require
layout(std140) uniform SnowfallMatrices { mat4 ViewProjection; };
layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;
layout(location = 0) out vec2 texCoord;
layout(location = 1) out vec4 vertexColor;
void main() {
    gl_Position = ViewProjection * vec4(Position, 1.0);
    texCoord = UV0;
    vertexColor = Color;
}
