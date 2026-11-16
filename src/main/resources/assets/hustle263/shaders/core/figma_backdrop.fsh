#version 330
#extension GL_ARB_separate_shader_objects : require
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 OutColor;
uniform sampler2D OriginalSampler;
uniform sampler2D BlurredSampler;
layout(std140) uniform SurfaceStyle {
    vec4 Viewport;
    vec4 ThemeTint;
    vec4 Rectangles[32];
    vec4 Radii[8];
    vec4 Angles[8];
};

layout(std140) uniform GlassStyle { vec4 GlassAnimation; };

// Glass reflection equations ported from Coliseum 1.21.11 menu_rays.fsh.
vec4 glassReflection(vec2 uv, vec2 Size, float radius) {
    float Time = GlassAnimation.x;
    float Intensity = -GlassAnimation.y;
    vec4 FragColor = vec4(ThemeTint.rgb, GlassAnimation.z);
    vec2 q = abs((uv - 0.5) * Size) - Size * 0.5 + radius;
    float sdf = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
    float mask = 1.0 - smoothstep(-0.7, 0.7, sdf);
        if (Intensity < -2.5) {
            // Slowly tilting pearlescent glass, with reflections receding in depth.
            // Normalise by panel width so highlights stay smooth on compact HUD chips.
            float t = Time * 0.55;
            float aspect = clamp(Size.y / max(Size.x, 1.0), 0.12, 1.0);
            vec2 p = vec2(uv.x - 0.5, (uv.y - 0.5) * aspect);
            float tilt = 0.22 * sin(t * 0.61);
            mat2 rotation = mat2(cos(tilt), -sin(tilt), sin(tilt), cos(tilt));
            p = rotation * p;
            float depth = 1.0 / (1.28 + p.y * 0.65);
            vec2 projected = p * depth;
            float bend = sin(projected.x * 5.0 + t * 0.8) * 0.055
                       + sin(projected.y * 7.0 - t * 0.65) * 0.025;
            float surface = projected.x * 0.72 + projected.y * 0.95 + bend;
            float travel = sin(t * 0.72) * 0.48;
            float distanceToLight = surface - travel;
            float broad = exp(-distanceToLight * distanceToLight / 0.045);
            float highlight = exp(-distanceToLight * distanceToLight / 0.0028);
            float echoDistance = surface + travel * 0.6 + 0.22;
            float echo = exp(-echoDistance * echoDistance / 0.014);
            float pearl = 0.5 + 0.5 * sin(surface * 8.0 - t * 1.25 + depth * 2.0);
            float hueFlow = 0.5 + 0.5 * sin(projected.x * 4.5 + projected.y * 3.0 + t * 0.9);
            vec3 cool = mix(vec3(0.37, 0.78, 0.91), FragColor.rgb, 0.45);
            vec3 warm = mix(vec3(0.77, 0.60, 0.94), FragColor.rgb, 0.32);
            vec3 tint = mix(cool, warm, hueFlow);
            tint = mix(tint, vec3(0.91, 0.95, 1.0), highlight * 0.58 + pearl * 0.12);
            // A travelling rim catches the light, while the centre stays translucent.
            float rim = exp(-abs(sdf + 1.0) * 0.8);
            float edgeLight = 0.5 + 0.5 * sin(uv.x * 4.0 + uv.y * 3.0 - t * 1.1);
            float breathing = 0.90 + 0.10 * sin(t * 0.85);
            float a = (0.045 + broad * 0.18 + highlight * 0.15 + echo * 0.09
                    + pearl * 0.035 + rim * edgeLight * 0.17) * breathing * mask * FragColor.a;
            return vec4(tint, a);
        }
        // Straight satin reflection, sloping down to the right (~28 degrees).
        // Physical proportions preserve the slope on both the menu and wide HUD chips.
        vec2 p = (uv - 0.5) * Size / max(max(Size.x, Size.y), 1.0);
        vec2 normal = vec2(-0.469472, 0.882948);
        float diagonal = dot(p, normal);
        float t = Time * 0.18;
        float fullSurface = step(1.5, -Intensity);
        // Starts at the lower-left corner, then gently sweeps across the glass.
        float travel = mix(0.28 + sin(t) * 0.12, sin(t + 0.55) * 0.48, fullSurface);
        float d = diagonal - travel;
        float broad = exp(-d * d / 0.018);
        float core = exp(-d * d / 0.0038);
        float echoDistance = d + 0.23;
        float echo = exp(-echoDistance * echoDistance / 0.008);
        float along = dot(p, vec2(0.882948, 0.469472));
        float shimmer = 0.86 + 0.14 * sin(along * 3.0 - t * 1.3);
        float cornerMask = exp(-dot((uv - vec2(0.16, 0.88)) * vec2(1.1, 0.9),
                                   (uv - vec2(0.16, 0.88)) * vec2(1.1, 0.9)) * 2.0);
        float coverage = mix(cornerMask, 1.0, fullSurface);
        // Mostly silver-white like a reflection in frosted glass, lightly theme tinted.
        vec3 tint = mix(vec3(0.80, 0.86, 0.90), FragColor.rgb, 0.16);
        float a = (broad * 0.30 + core * 0.23 + echo * 0.09)
                  * shimmer * coverage * mask * FragColor.a;
        return vec4(tint, a);
}

float roundedBox(vec2 p, vec4 rect, float corner) {
    float radius = min(corner, min(rect.z, rect.w) * 0.5);
    vec2 q = abs(p - rect.xy - rect.zw * 0.5) - rect.zw * 0.5 + radius;
    float distance = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
    return 1.0 - smoothstep(-0.5, 0.5, distance);
}

void main() {
    vec2 point = vec2(texCoord.x, 1.0 - texCoord.y) * Viewport.xy;
    float mask = 0.0;
    for (int i = 0; i < 32; ++i) {
        if (i >= int(Viewport.w)) break;
        vec4 rect = Rectangles[i];
        vec2 centre = rect.xy + rect.zw * 0.5;
        float angle = Angles[i / 4][i % 4];
        // Most HUD panels are axis-aligned. Reject their empty screen area
        // before evaluating trigonometry and the rounded distance function.
        if (angle == 0.0 && any(greaterThan(abs(point - centre), rect.zw * 0.5 + vec2(0.5)))) continue;
        float c = cos(angle), s = sin(angle);
        vec2 delta = point - centre;
        vec2 local = vec2(c * delta.x + s * delta.y, -s * delta.x + c * delta.y) + centre;
        mask = max(mask, roundedBox(local, rect, Radii[i / 4][i % 4]));
    }
    if (mask == 0.0) {
        OutColor = texture(OriginalSampler, texCoord);
        return;
    }
    vec4 glass = texture(BlurredSampler, texCoord);
    glass.rgb = mix(glass.rgb, glass.rgb * ThemeTint.rgb, ThemeTint.a);
    // Only the main ClickGUI panel receives the reflection; popups retain their own finish.
    if (GlassAnimation.y > 0.5 && Viewport.w > 0.0) {
        vec4 rect = Rectangles[0];
        vec2 delta = point - rect.xy - rect.zw * 0.5;
        float angle = Angles[0].x;
        vec2 local = vec2(cos(angle) * delta.x + sin(angle) * delta.y,
                         -sin(angle) * delta.x + cos(angle) * delta.y);
        vec4 reflection = glassReflection(local / max(rect.zw, vec2(1.0)) + 0.5, rect.zw, Radii[0].x);
        glass.rgb = mix(glass.rgb, reflection.rgb, clamp(reflection.a, 0.0, 1.0));
    }
    OutColor = mix(texture(OriginalSampler, texCoord), glass, mask * GlassAnimation.w);
}
