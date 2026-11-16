#version 330
#extension GL_ARB_separate_shader_objects : require
// Halloween billboards. vertexColor.b selects the material: > .75 ember, .3–.75 bat (g = wing flap), else wisp (g = hue).
layout(location = 0) in vec2 texCoord;
layout(location = 1) in vec4 vertexColor;
layout(location = 0) out vec4 fragColor;

float ellipse(vec2 p, vec2 r) { return (length(p / r) - 1.0) * min(r.x, r.y); }

void main() {
    vec2 p = texCoord * 2.0 - 1.0;
    float radius = length(p);
    vec3 color;
    float alpha;
    if (vertexColor.b > 0.75) {
        float core = exp(-radius * radius * 18.0);
        alpha = exp(-radius * radius * 4.5) * (1.0 - smoothstep(0.7, 1.0, radius)) * vertexColor.r;
        color = mix(vec3(0.8, 0.14, 0.015), vec3(1.0, 0.8, 0.27), core);
    } else if (vertexColor.b > 0.3) {
        // Bat silhouette, y up.
        vec2 q = vec2(abs(p.x), -p.y);
        float flap = vertexColor.g * 2.0 - 1.0;
        float body = ellipse(q - vec2(0.0, -0.02), vec2(0.13, 0.24));
        float head = ellipse(q - vec2(0.0, 0.24), vec2(0.09, 0.09));
        float ear = max(abs(q.x - 0.06) - 0.03 * (0.42 - q.y) / 0.12, max(0.30 - q.y, q.y - 0.42));
        // Wing: rises and falls with the flap; scalloped trailing edge.
        float lift = flap * 0.35 * q.x;
        vec2 w = vec2(q.x, q.y - lift);
        float wingTop = w.y - (0.18 - 0.10 * w.x);
        float scallop = 0.05 * abs(sin(w.x * 9.4));
        float wingBottom = (-0.10 + 0.18 * w.x * w.x + scallop) - w.y;
        float wing = max(max(wingTop, wingBottom), max(w.x - 0.95, 0.08 - w.x));
        float d = min(min(body, head), min(ear, wing));
        float aa = max(fwidth(d), 0.002);
        alpha = 1.0 - smoothstep(-aa, aa, d);
        color = vec3(0.025, 0.018, 0.03);
        // Tiny glowing eyes.
        float eyes = 1.0 - smoothstep(0.0, 0.025, length(q - vec2(0.04, 0.25)));
        color = mix(color, vec3(1.0, 0.3, 0.05), eyes);
    } else {
        // Will-o'-wisp: bright core, soft halo, hue from green-cyan to violet.
        vec3 tint = mix(vec3(0.35, 1.0, 0.75), vec3(0.65, 0.45, 1.0), vertexColor.g);
        float core = exp(-radius * radius * 30.0);
        float halo = exp(-radius * radius * 5.0) * (1.0 - smoothstep(0.75, 1.0, radius));
        color = mix(tint, vec3(1.0), core * 0.8) * (0.6 + 0.4 * vertexColor.r);
        alpha = max(core, halo * 0.55);
    }
    alpha *= vertexColor.a;
    if (alpha < 0.005) discard;
    fragColor = vec4(color, alpha);
}
