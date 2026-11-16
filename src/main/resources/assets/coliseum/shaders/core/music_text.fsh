#version 330
#extension GL_ARB_separate_shader_objects : require
layout(location=0) in vec2 vUv;
layout(location=1) in vec4 vColor;
layout(location=0) out vec4 fragColor;
uniform sampler2D Sampler0;
void main() {
    vec3 field=textureLod(Sampler0,vUv,0.0).rgb;
    float distance=max(min(field.r,field.g),min(max(field.r,field.g),field.b))-.5;
    float coverage=clamp(distance/max(fwidth(distance),.0001)+.5,0.0,1.0);
    fragColor=vec4(vColor.rgb,vColor.a*coverage);
    if(fragColor.a<.001)discard;
}
