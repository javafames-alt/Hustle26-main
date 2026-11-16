#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform SnowMatrices { mat4 View; mat4 Projection; };
layout(std140) uniform Hustle 26.3Data {
    mat4 InverseView;
    vec4 Origin4;
    vec4 SunDir4;
    vec4 SunColor4;
    vec4 SkyColor4;
    float GrainScale;
    float WrapPeriod;
    float GrainStrength;
    float CrustAmount;
    float SparkleAmount;
    float Wetness;
    float Sheen;
};
layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 0) out vec3 WorldPos;
layout(location = 1) out vec3 ViewPos;
layout(location = 2) out vec4 FragColor;
void main() {
    gl_Position = Projection * View * vec4(Position, 1.0);
    vec3 relative = Position;
    WorldPos = relative + Origin4.xyz;
    ViewPos = relative;
    FragColor = Color;
}
