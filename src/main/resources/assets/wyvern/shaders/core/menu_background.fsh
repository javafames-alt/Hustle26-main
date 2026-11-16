#version 330
#extension GL_ARB_separate_shader_objects : require

// The main menu's backdrop image (MenuBackdrop.kt), drawn into the frame before the GUI: it covers the screen
// (scaled to fill it, cropped around the middle, never stretched) and shifts a little with the mouse (parallax),
// zoomed in just enough that the shift never shows an edge.
layout(std140) uniform Globals {
    ivec3 CameraBlockPos;
    float GlintAlpha;
    vec3 CameraOffset;
    float GameTime;
    vec2 ScreenSize;
    int MenuBlurRadius;
    int UseRgss;
};

layout(std140) uniform MenuInfo {
    // the mouse, smoothed: -1..1 across the screen on each axis
    vec2 Mouse;
};

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec2 image = vec2(textureSize(InSampler, 0));
    float scale = max(ScreenSize.x / image.x, ScreenSize.y / image.y);
    // share of the image that fits on each axis, centered; the image's first row is its top
    vec2 shown = ScreenSize / (image * scale);
    vec2 uv = (vec2(texCoord.x, 1.0 - texCoord.y) - 0.5) * shown + 0.5;
    uv = (uv - 0.5) / ZOOM + 0.5 + Mouse * SHIFT * shown;
    fragColor = vec4(texture(InSampler, uv).rgb, 1.0);
}
