#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D VisibleSampler;
uniform sampler2D GlowSampler;
uniform sampler2D WideGlowSampler;
uniform sampler2D BodyDepthSampler;
uniform sampler2D SceneDepthSampler;
layout(std140) uniform ChamsComposite { vec4 Settings; };
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
void main() {
    vec4 body = texture(VisibleSampler, texCoord);
    vec3 closeGlow = texture(GlowSampler, texCoord).rgb;
    vec3 wideGlow = texture(WideGlowSampler, texCoord).rgb;
    
    
    float occluded = step(texture(BodyDepthSampler, texCoord).r + 0.000001,
            texture(SceneDepthSampler, texCoord).r);
    float alpha = body.a * Settings.x * Settings.z * occluded;
    
    
    vec3 light = (closeGlow * 1.25 + wideGlow * 1.35) * Settings.y;
    
    
    float peak = max(light.r, max(light.g, light.b));
    vec3 halo = light / (1.0 + peak);
    vec3 emission = mix(halo, light * 0.06, body.a) * Settings.x;
    fragColor = vec4(body.rgb * alpha + emission, alpha);
}
