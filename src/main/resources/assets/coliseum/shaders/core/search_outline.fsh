#version 330
#extension GL_ARB_separate_shader_objects : require
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat; mat4 TextureMat; vec4 ColorModulator; vec3 ModelOffset;
};
layout(location=0) in vec2 texCoord0;
layout(location=1) in vec4 vertexColor;
layout(location=0) out vec4 fragColor;
void main(){
    vec2 extent=1.0/max(fwidth(texCoord0),vec2(0.00001));
    float radius=extent.y*(3.0/16.0);
    float stroke=extent.y*(0.5/16.0);
    vec2 p=(texCoord0-0.5)*extent;
    vec2 q=abs(p)-(extent*0.5-vec2(radius+stroke*0.5));
    float distance=length(max(q,vec2(0.0)))+min(max(q.x,q.y),0.0)-radius;
    float aa=max(fwidth(distance),0.001);
    float coverage=1.0-smoothstep(stroke*0.5-aa*0.5,stroke*0.5+aa*0.5,abs(distance));
    fragColor=vertexColor*ColorModulator;
    fragColor.a*=coverage;
    if(fragColor.a<=0.001)discard;
}
