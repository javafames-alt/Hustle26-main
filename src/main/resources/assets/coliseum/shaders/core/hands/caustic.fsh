#version 330
#extension GL_ARB_separate_shader_objects : require
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 OutColor;
uniform sampler2D HandDepth;
layout(std140) uniform HandShaderUniforms { vec4 TintColor; float Time; };
float mask(vec2 uv){return smoothstep(0.0001,0.0004,texture(HandDepth,uv).r);} void main(){float m=mask(texCoord);if(m<.01)discard;vec2 p=texCoord*18.;float a=abs(sin(p.x+sin(p.y+Time))*cos(p.y+cos(p.x-Time*.7)));float glow=pow(a,5.);vec3 c=mix(TintColor.rgb,vec3(1.),glow*.7);OutColor=vec4(c,m*TintColor.a*(.28+glow*.9));}
