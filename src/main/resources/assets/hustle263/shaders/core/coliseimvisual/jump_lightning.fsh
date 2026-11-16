#version 330
#extension GL_ARB_separate_shader_objects : require
layout(location = 0) in vec2 texCoord;
layout(location = 1) in vec4 vertexColor;
layout(location = 0) out vec4 fragColor;
void main() {
    float crossDist = abs(texCoord.y * 2.0 - 1.0);
    float core = 1.0 - smoothstep(0.0, 0.22, crossDist);
    float glow = exp(-crossDist * 7.5);
    float sideFade = 1.0 - smoothstep(0.50, 0.96, crossDist);
    float along = sin(clamp(texCoord.x, 0.0, 1.0) * 3.14159265);
    float endFade = smoothstep(0.0, 0.08, texCoord.x)
        * (1.0 - smoothstep(0.92, 1.0, texCoord.x));
    float intensity = clamp(core * 1.5 + glow * 0.72, 0.0, 1.0)
        * sideFade * mix(0.68, 1.0, along) * endFade * vertexColor.a;
    if (intensity <= 0.002) discard;
    vec3 color = mix(vertexColor.rgb, vec3(1.0), core * 0.9);
    fragColor = vec4(color * intensity, intensity);
}
