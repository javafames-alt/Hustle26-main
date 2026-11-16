#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
layout(std140) uniform Fog { vec4 FogColor; float FogEnvironmentalStart; float FogEnvironmentalEnd; float FogRenderDistanceStart; float FogRenderDistanceEnd; float FogSkyEnd; float FogCloudsEnd; };
uniform sampler2D Sampler0;
layout(location = 0) in float sphericalVertexDistance;
layout(location = 0) in float cylindricalVertexDistance;
layout(location = 0) in vec4 vertexPerFaceColorBack;
layout(location = 0) in vec4 vertexPerFaceColorFront;
layout(location = 0) in vec4 overlayColor;
layout(location = 1) in vec2 texCoord0;
layout(location = 0) out vec4 fragColor;
float fogValue(float d,float s,float e){return d<=s?0.0:d>=e?1.0:(d-s)/max(e-s,0.000001);}
void main(){
    float a=texture(Sampler0,texCoord0).a; if(a<0.1)discard;
    vec4 v=gl_FrontFacing?vertexPerFaceColorFront:vertexPerFaceColorBack;
    vec4 c=vec4(v.rgb,v.a*a)*ColorModulator; c.rgb=mix(overlayColor.rgb,c.rgb,overlayColor.a);
    float f=max(fogValue(sphericalVertexDistance,FogEnvironmentalStart,FogEnvironmentalEnd),fogValue(cylindricalVertexDistance,FogRenderDistanceStart,FogRenderDistanceEnd));
    fragColor=vec4(mix(c.rgb,FogColor.rgb,f*FogColor.a),c.a);
}
