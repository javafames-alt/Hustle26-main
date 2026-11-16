#version 330
#extension GL_ARB_separate_shader_objects : require
// Jack-o'-lantern material shader. vertexColor: r = candle glow, g = baked diffuse shade,
// b = material (0 body, .45 stem, .62 vine, .85 leaf), a = fade. Body uv: x = angle/2pi (0 = front), y = 0 top .. 1 bottom.
layout(location = 0) in vec2 texCoord;
layout(location = 1) in vec4 vertexColor;
layout(location = 0) out vec4 fragColor;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), f.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), f.x), f.y);
}
float triSdf(vec2 p, vec2 a, vec2 b, vec2 c) {
    vec2 e0 = b - a, e1 = c - b, e2 = a - c;
    vec2 v0 = p - a, v1 = p - b, v2 = p - c;
    vec2 pq0 = v0 - e0 * clamp(dot(v0, e0) / dot(e0, e0), 0.0, 1.0);
    vec2 pq1 = v1 - e1 * clamp(dot(v1, e1) / dot(e1, e1), 0.0, 1.0);
    vec2 pq2 = v2 - e2 * clamp(dot(v2, e2) / dot(e2, e2), 0.0, 1.0);
    float s = sign(e0.x * e2.y - e0.y * e2.x);
    vec2 d = min(min(vec2(dot(pq0, pq0), s * (v0.x * e0.y - v0.y * e0.x)),
                     vec2(dot(pq1, pq1), s * (v1.x * e1.y - v1.y * e1.x))),
                     vec2(dot(pq2, pq2), s * (v2.x * e2.y - v2.y * e2.x)));
    return -sqrt(d.x) * sign(d.y);
}
// Signed distance to the carved face (negative inside a hole).
float faceSdf(vec2 p) {
    vec2 e = vec2(abs(p.x), p.y);
    float eye = triSdf(e, vec2(0.10, 0.18), vec2(0.42, 0.20), vec2(0.27, 0.48));
    float nose = triSdf(p, vec2(-0.07, -0.02), vec2(0.07, -0.02), vec2(0.0, 0.12));
    // Crescent mouth: inside a lower ellipse, outside a shifted one.
    float outer = (length(vec2(p.x / 0.50, (p.y + 0.12) / 0.30)) - 1.0) * 0.30;
    float inner = (length(vec2(p.x / 0.56, (p.y + 0.02) / 0.27)) - 1.0) * 0.27;
    float mouth = max(outer, -inner);
    mouth = max(mouth, -0.62 - p.y);
    // Two teeth: one hanging from the top lip, one rising from the bottom.
    float toothTop = max(abs(p.x - 0.16) - 0.045, -(p.y + 0.33));
    float toothBottom = max(abs(p.x + 0.14) - 0.045, (p.y + 0.36));
    mouth = max(mouth, -min(toothTop, toothBottom));
    return min(min(eye, nose), mouth);
}

void main() {
    float material = vertexColor.b;
    float shade = vertexColor.g;
    float glow = vertexColor.r;
    vec3 color;
    if (material > 0.75) {
        // Leaf: midrib, side veins, darker edges.
        float across = texCoord.x * 2.0 - 1.0;
        float rib = 1.0 - smoothstep(0.0, 0.08, abs(across));
        float veins = 1.0 - smoothstep(0.0, 0.10, abs(fract(texCoord.y * 6.0 - abs(across) * 1.4) - 0.5) * 2.0 - 0.85);
        vec3 leaf = mix(vec3(0.10, 0.26, 0.05), vec3(0.20, 0.40, 0.08), noise(texCoord * vec2(8.0, 14.0)));
        leaf = mix(leaf, vec3(0.30, 0.46, 0.14), max(rib, veins * 0.5));
        leaf *= 0.75 + 0.25 * (1.0 - abs(across));
        color = leaf * shade;
    } else if (material > 0.55) {
        // Vine: thin, slightly twisted green-brown.
        float twist = 0.85 + 0.15 * sin(texCoord.x * 6.2831853 + texCoord.y * 40.0);
        color = mix(vec3(0.20, 0.30, 0.07), vec3(0.32, 0.30, 0.12), texCoord.y) * twist * shade;
    } else if (material > 0.3) {
        // Stem: dried, fibrous, lighter at the cut tip.
        float fibres = 0.82 + 0.18 * sin(texCoord.x * 6.2831853 * 5.0) + 0.06 * (noise(vec2(texCoord.x * 40.0, texCoord.y * 6.0)) - 0.5);
        vec3 stem = mix(vec3(0.16, 0.20, 0.06), vec3(0.42, 0.36, 0.18), smoothstep(0.55, 1.0, texCoord.y));
        color = stem * fibres * shade;
    } else {
        float angle = texCoord.x * 6.2831853;
        float v = texCoord.y;
        // Lobes and grooves (8 lobes => cos(angle * 4) per half).
        float lobe = pow(abs(cos(angle * 4.0)), 0.55);
        float groove = 1.0 - smoothstep(0.0, 0.22, lobe);
        vec3 skin = mix(vec3(0.78, 0.25, 0.02), vec3(0.98, 0.46, 0.05), lobe);
        skin = mix(skin, vec3(0.45, 0.13, 0.01), groove * 0.65);
        // Fine vertical streaks, speckles and a greenish tint near the stem.
        float streak = noise(vec2(angle * 22.0, v * 3.0));
        float speck = smoothstep(0.82, 0.9, noise(vec2(angle * 60.0, v * 40.0)));
        skin *= 0.90 + 0.12 * streak;
        skin = mix(skin, vec3(0.55, 0.32, 0.06), speck * 0.35);
        skin = mix(skin, vec3(0.34, 0.30, 0.05), (1.0 - smoothstep(0.02, 0.14, v)) * 0.6);
        skin *= shade;

        float front = cos(angle);
        if (front > 0.2) {
            vec2 p = vec2(sin(angle), (0.5 - v) * 2.0);
            float d = faceSdf(p);
            float aa = max(fwidth(d), 0.0008);
            float hole = 1.0 - smoothstep(-aa, aa, d);
            float edgeFade = smoothstep(0.2, 0.45, front);
            hole *= edgeFade;
            // Inside the hole: hot candle gradient, brighter low (near the flame) and in the centre.
            float flame = clamp(1.0 - length(p - vec2(0.0, -0.15)) * 0.9, 0.0, 1.0);
            vec3 inner = mix(vec3(0.85, 0.25, 0.02), vec3(1.0, 0.86, 0.38), flame) * glow * 1.35;
            inner = mix(vec3(0.10, 0.03, 0.01), inner, clamp(glow * 1.2, 0.0, 1.0));
            // Lit flesh rim around the cuts.
            float rim = (1.0 - smoothstep(0.0, 0.035, d)) * (1.0 - hole) * edgeFade;
            vec3 flesh = vec3(1.0, 0.70, 0.30) * (0.35 + 0.65 * glow);
            // Warm spill on the skin around the face.
            float spill = exp(-max(d, 0.0) * 9.0) * glow * 0.35 * edgeFade;
            skin += vec3(1.0, 0.45, 0.08) * spill;
            skin = mix(skin, flesh, rim * 0.8);
            color = mix(skin, inner, hole);
        } else {
            color = skin;
        }
    }
    if (vertexColor.a < 0.005) discard;
    fragColor = vec4(color, vertexColor.a);
}
