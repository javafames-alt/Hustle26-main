#version 330
#extension GL_ARB_separate_shader_objects : require

// Copied uniform blocks (like vanilla gui.vsh) instead of #include.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;   // position relative to the shape center, GUI units
layout(location = 3) in vec2 UV3;   // shape half size, GUI units
layout(location = 4) in ivec2 UV1;  // radius, outline thickness (1/16 GUI unit fixed point)
layout(location = 5) in ivec2 UV2;  // softness (1/16 GUI unit), fill mode (0 color, 1 hue spectrum, 2 checkerboard)

layout(location = 0) out vec4 vertexColor;
layout(location = 1) out vec2 localPos;
layout(location = 2) out vec2 halfSize;
layout(location = 3) out vec4 shape; // radius, thickness, softness, fill mode

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexColor = Color;
    localPos = UV0;
    halfSize = UV3;
    shape = vec4(float(UV1.x) / 16.0, float(UV1.y) / 16.0, float(UV2.x) / 16.0, float(UV2.y));
}
