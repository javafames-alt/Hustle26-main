#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

#ifdef BACKDROP_BLUR
layout(std140) uniform Globals {
    ivec3 CameraBlockPos;
    float GlintAlpha;
    vec3 CameraOffset;
    float GameTime;
    vec2 ScreenSize;
    int MenuBlurRadius;
    int UseRgss;
};

// blurred copy of the frame behind the GUI, see Blur.kt
uniform sampler2D Sampler0;
#endif

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec2 localPos;
layout(location = 2) in vec2 halfSize;
layout(location = 3) in vec4 shape;

layout(location = 0) out vec4 fragColor;

// Signed distance to a rounded box centered at the origin (Inigo Quilez).
float roundedBoxSdf(vec2 p, vec2 size, float radius) {
    vec2 q = abs(p) - size + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

// Interleaved gradient noise (Jimenez): per pixel 0..1, no texture. Used to dither away 8-bit banding.
float screenNoise() {
    return fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
}

vec3 hue2rgb(float h) {
    vec3 k = clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
    return k;
}

void main() {
    float radius = min(shape.x, min(halfSize.x, halfSize.y));
    float thickness = shape.y;
    float softness = shape.z;
    // fill mode in the low 3 bits, its parameter above them (see RectRenderState)
    int packed = int(shape.w + 0.5);
    int fill = packed - (packed / 8) * 8;
    float param = float(packed / 8);

    float dist = roundedBoxSdf(localPos, halfSize, radius);
    // exact size of one screen pixel in distance units (fwidth overestimates on diagonals and blurs)
    float aa = max(length(vec2(dFdx(dist), dFdy(dist))), 0.0001);

    float coverage;
    if (thickness > 0.0) {
        float band = abs(dist + thickness * 0.5) - thickness * 0.5;
        coverage = softness > 0.0
            ? 1.0 - smoothstep(-aa * 0.5, aa * 0.5 + softness, band)
            : clamp(0.5 - band / aa, 0.0, 1.0);
    } else if (softness > 0.0) {
        // soft shadow / glow: fades out over `softness` around the edge
        coverage = 1.0 - smoothstep(-softness, softness, dist);
        coverage *= coverage;
        // wide soft falloffs step visibly in 8 bits over dark backgrounds: dither the coverage a little
        coverage = max(coverage + (screenNoise() - 0.5) * (1.5 / 255.0), 0.0);
    } else {
        // box-filtered edge: one pixel wide linear ramp, the cleanest antialiasing for a sharp edge
        coverage = clamp(0.5 - dist / aa, 0.0, 1.0);
    }

    vec4 color = vertexColor * ColorModulator;
    if (fill == 3) {
        // progress ring: the outline band, kept from the top clockwise up to param/1000 of a turn
        float progress = param / 1000.0;
        if (progress < 0.999) {
            float turn = atan(localPos.x, -localPos.y) / 6.2831853;
            if (turn < 0.0) turn += 1.0;
            float around = 6.2831853 * length(localPos);
            float inside = min(turn, progress - turn) * around;
            coverage *= clamp(inside / aa + 0.5, 0.0, 1.0);
        }
    } else if (fill == 4) {
        // dashed outline: dashes of param/16 units along the border, measured from the top middle
        float dash = max(param / 16.0, 0.5);
        vec2 inner = halfSize - radius;
        vec2 q = abs(localPos) - inner;
        float s;
        if (q.x > 0.0 && q.y > 0.0) s = inner.x + atan(q.x, q.y) * radius;
        else if (q.y > q.x) s = abs(localPos.x);
        else s = inner.x + radius * 1.5707963 + (inner.y - abs(localPos.y));
        float m = mod(s, dash * 2.0);
        coverage *= clamp(min(m, dash - m) / aa + 0.5, 0.0, 1.0);
    } else if (fill == 5) {
        // dot grid: a small round dot every param/4 units, in one quad (the main menu's backdrop texture)
        float spacing = max(param / 4.0, 1.0);
        vec2 cell = mod(localPos + halfSize, spacing) - spacing * 0.5;
        float dot = length(cell) - spacing * 0.2;
        coverage *= clamp(0.5 - dot / aa, 0.0, 1.0);
    }
    if (fill == 1) {
        // hue spectrum left to right, computed per pixel: no segments, no seams
        float t = clamp(localPos.x / halfSize.x * 0.5 + 0.5, 0.0, 1.0);
        color.rgb = hue2rgb(t);
    } else if (fill == 2) {
        // checkerboard behind translucent colors, 3 GUI units per cell
        vec2 cell = floor((localPos + halfSize) / 3.0);
        float checker = mod(cell.x + cell.y, 2.0);
        color.rgb = mix(vec3(0.53), vec3(0.80), checker);
    }
#ifdef BACKDROP_BLUR
    // frosted glass: blurred backdrop, half tinted by the vertex color; its alpha is the glass's opacity
    // (so glass fades in and out with the panel it belongs to)
    vec3 backdrop = texture(Sampler0, gl_FragCoord.xy / ScreenSize).rgb;
    // vibrancy: the blur washes colors out, so push the backdrop's saturation back up a little
    float luma = dot(backdrop, vec3(0.2126, 0.7152, 0.0722));
    backdrop = clamp(mix(vec3(luma), backdrop, 1.2), 0.0, 1.0);
    color = vec4(mix(backdrop, color.rgb, 0.5), color.a);
#endif
    // half a step of noise: flat 8-bit colors round back to themselves, gradients and glass lose their bands
    color.rgb += (screenNoise() - 0.5) / 255.0;
    color.a *= coverage;
    if (color.a <= 0.001) {
        discard;
    }
    fragColor = color;
}
