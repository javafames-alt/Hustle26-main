#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
layout(std140) uniform ChamsBlurDirection { vec4 Direction; };
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
void main() {
    vec4 sum = vec4(0.0);
    float weightSum = 0.0;
    vec2 sourceRadius = abs(Direction.xy) * Direction.z * vec2(textureSize(InSampler, 0));
    int steps = clamp(int(ceil(max(sourceRadius.x, sourceRadius.y))), 8, 64);
    for (int tap = -steps; tap <= steps; tap++) {
        float distance = float(tap) / float(steps);
        float weight = exp(-distance * distance * 4.5);
        vec2 uv = texCoord + Direction.xy * Direction.z * distance;
        
        vec4 sampleColor = any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))
                ? vec4(0.0) : texture(InSampler, uv);
        sum += sampleColor * weight;
        weightSum += weight;
    }
    fragColor = sum / weightSum;
}
