#version 330
#extension GL_ARB_separate_shader_objects : require
// Procedural sky for Weather «Свой».
// Sky0: time, cloud time, preset (0 night, 1 day, 2 sunset, 3 aurora), cloud amount
// Sky1: stars, meteor rate, moon size, aurora strength
// Sky2: milky way, -, -, -
layout(std140) uniform SkyData { vec4 Sky0; vec4 Sky1; vec4 Sky2; vec4 Sky3; };
layout(location = 0) in vec3 skyDir;
layout(location = 0) out vec4 fragColor;

const float PI = 3.14159265;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}
vec3 hash33(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}
float noise2(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), f.x),
               mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), f.x), f.y);
}
float noise3(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = mix(mix(hash13(i), hash13(i + vec3(1, 0, 0)), f.x), mix(hash13(i + vec3(0, 1, 0)), hash13(i + vec3(1, 1, 0)), f.x), f.y);
    float b = mix(mix(hash13(i + vec3(0, 0, 1)), hash13(i + vec3(1, 0, 1)), f.x), mix(hash13(i + vec3(0, 1, 1)), hash13(i + vec3(1, 1, 1)), f.x), f.y);
    return mix(a, b, f.z);
}
float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    mat2 r = mat2(0.8, 0.6, -0.6, 0.8);
    for (int i = 0; i < 5; i++) { v += a * noise2(p); p = r * p * 2.02 + vec2(1.7, 9.2); a *= 0.5; }
    return v;
}
float fbm3(vec3 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) { v += a * noise3(p); p = p * 2.03 + vec3(1.7, 9.2, 4.1); a *= 0.5; }
    return v;
}

// ---------------------------------------------------------------- stars
// Colour temperature: hot blue-white giants -> white -> yellow -> cool orange.
vec3 starTint(float t) {
    vec3 c = mix(vec3(0.62, 0.74, 1.00), vec3(0.82, 0.88, 1.00), smoothstep(0.00, 0.22, t));
    c = mix(c, vec3(1.00, 0.97, 0.93), smoothstep(0.18, 0.48, t));
    c = mix(c, vec3(1.00, 0.86, 0.64), smoothstep(0.55, 0.80, t));
    return mix(c, vec3(1.00, 0.70, 0.46), smoothstep(0.82, 1.00, t));
}

// One layer of stars. Every cell of a 3D grid that touches the sphere |p| = scale may hold one star, so the
// distribution is seamless in every direction. Stars are gaussians sized in screen pixels (px = view angle
// of one pixel): they never get thinner than ~1px (no shimmering while turning) and keep their total
// brightness at any resolution or FOV. Bright stars of a "flare" layer get a soft halo and four thin rays.
vec3 starLayer(vec3 dir, float scale, float density, float gain, float sizeRef, float time, float px, float flare, float seed) {
    if (density <= 0.0 || gain <= 0.0) return vec3(0.0);
    vec3 p = dir * scale;
    vec3 base = floor(p - 0.5);
    float pixel = px * scale;               // one screen pixel in cell units
    float refPx = 0.00113 / px;             // 1.0 at 1080p with a 70 degree FOV
    // A pixel wider than a third of a cell can only alias: fade such fine layers out instead.
    float lod = 1.0 - smoothstep(0.24, 0.36, pixel);
    if (lod <= 0.0) return vec3(0.0);
    float twinkleAmp = mix(0.42, 0.14, smoothstep(0.0, 0.6, dir.y));
    vec3 col = vec3(0.0);
    for (int i = 0; i < 8; i++) {
        vec3 c = base + vec3(float(i & 1), float((i >> 1) & 1), float((i >> 2) & 1));
        vec3 h = hash33(c + seed);
        if (h.x >= density) continue;
        vec3 q = c + hash33(c.zxy * 1.37 + seed + 17.0);
        float len = length(q);
        if (abs(len - scale) > 0.15) continue;
        vec3 s = q * (scale / len);
        vec3 o = p - s;
        float dist = length(o);
        float window = 1.0 - smoothstep(0.24, 0.35, dist);
        if (window <= 0.0) continue;
        float r = dist / pixel;             // distance in pixels
        float e = h.y * h.y;                // many faint stars, few bright ones
        float sigma = sizeRef * mix(0.55, 1.25, e) * refPx;
        float sigmaR = max(sigma, 0.62);
        float k = fract(h.z * 7.31 + h.y * 3.17);
        float twinkle = 1.0 + twinkleAmp * (0.6 * sin(time * (1.7 + 2.9 * k) + h.z * 40.0)
                                          + 0.4 * sin(time * (4.3 + 3.1 * h.y) + k * 23.0));
        float energy = gain * mix(0.35, 1.0, e) * (sigma * sigma) / (sigmaR * sigmaR) * twinkle * lod;
        vec3 tint = starTint(h.z);
        float core = exp(-0.5 * r * r / (sigmaR * sigmaR));
        vec3 star = mix(tint, vec3(1.0), 0.55 * core) * core;
        if (flare > 0.0) {
            float big = smoothstep(0.30, 0.85, h.y) * flare;
            float halo = exp(-0.5 * r * r / (16.0 * sigmaR * sigmaR)) * 0.14;
            vec3 n = s / scale;
            vec3 t1 = normalize(cross(abs(n.y) < 0.98 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0), n));
            vec3 t2 = cross(n, t1);
            vec2 v = vec2(dot(o, t1), dot(o, t2)) / pixel;
            float rayLength = max(min(mix(5.0, 14.0, e) * refPx, 0.30 / pixel), 1.0);
            vec2 fall = max(vec2(1.0) - abs(v) / rayLength, vec2(0.0));
            float rays = exp(-abs(v.y) * 1.8) * fall.x * fall.x + exp(-abs(v.x) * 1.8) * fall.y * fall.y;
            star += tint * (halo + rays * 0.30) * big;
        }
        col += star * energy * window;
    }
    return col;
}

// ---------------------------------------------------------------- milky way
// A tilted galactic band with a warm bulge, star clouds, dark dust lanes along its middle and faint
// pink / teal nebulae. bandMask (0..1) is returned so the star layers can get denser inside the band.
vec3 milkyWay(vec3 dir, float strength, out float bandMask) {
    vec3 n = normalize(vec3(0.3, 0.55, -0.78));
    vec3 side = normalize(cross(n, vec3(0.0, 1.0, 0.0)));
    vec3 top = cross(side, n);
    vec3 centre = normalize(top * 0.55 + side * 0.835);
    float lat = dot(dir, n);
    float along = dot(dir, centre);
    float bulge = exp(-(1.0 - along) * 4.5);
    float width = 0.14 + 0.12 * bulge;
    float band = exp(-lat * lat / (width * width));
    bandMask = band;
    if (band < 0.004 || strength <= 0.0) return vec3(0.0);
    float clumps = smoothstep(0.28, 0.78, fbm3(dir * 4.0 + 1.3));
    float fine = fbm3(dir * 15.0 + 7.1);
    float lanes = smoothstep(0.42, 0.72, fbm3(dir * 8.0 + 3.0));
    float wobble = (noise3(dir * 3.0 + 9.0) - 0.5) * 0.09;
    float rift = exp(-pow((lat + wobble) / (width * 0.30), 2.0)) * smoothstep(-0.2, 0.6, along);
    float dust = clamp(rift * (0.55 + 0.45 * lanes) + lanes * 0.35 * band, 0.0, 1.0);
    float spine = exp(-lat * lat / (width * width * 0.25));
    float light = band * (0.25 + 1.1 * clumps) * (0.65 + 0.70 * fine) + spine * 0.35;
    light += exp(-(1.0 - along) * 30.0) * band * 1.2;
    light *= 1.0 - 0.80 * dust;
    vec3 cool = vec3(0.30, 0.34, 0.58);
    vec3 warm = vec3(0.70, 0.52, 0.38);
    vec3 colour = mix(cool, warm, clamp(bulge * 0.9 + 0.3 * (clumps - 0.5), 0.0, 1.0));
    float nebula = fbm3(dir * 6.0 + 21.0);
    colour += vec3(0.55, 0.16, 0.30) * smoothstep(0.58, 0.82, nebula) * band * 0.9;
    colour += vec3(0.08, 0.30, 0.34) * smoothstep(0.62, 0.86, 1.0 - nebula * 0.9 - fine * 0.2) * band * 0.6;
    return colour * light * strength * (0.20 + 0.16 * bulge);
}

// ---------------------------------------------------------------- shooting stars
vec3 meteors(vec3 dir, float time, float rate, float px) {
    if (rate <= 0.001) return vec3(0.0);
    vec3 col = vec3(0.0);
    float period = mix(16.0, 2.5, clamp(rate, 0.0, 1.0));
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        float t = time + fi * period * 0.37 + fi * 3.1;
        float slot = floor(t / period);
        float local = t - slot * period;
        float duration = 0.9;
        if (local > duration) continue;
        vec3 r = hash33(vec3(slot, fi, 7.0));
        if (r.z > 0.35 + 0.65 * rate) continue;
        float yaw = r.x * 2.0 * PI;
        vec3 start = normalize(vec3(cos(yaw), 0.45 + 0.45 * r.y, sin(yaw)));
        vec3 travel = normalize(cross(start, normalize(vec3(r.y - 0.5, 1.0, r.x - 0.5))));
        if (travel.y > 0.0) travel = -travel; // always streak downward
        vec3 n = normalize(cross(start, travel));
        float progress = local / duration;
        float along = atan(dot(dir, travel), dot(dir, start));
        float head = progress * 0.55;
        float tail = 0.18 * (1.0 - 0.4 * progress);
        float perp = abs(dot(dir, n));
        float inSeg = smoothstep(head - tail, head, along) * (1.0 - smoothstep(head, head + 0.004, along));
        float width = max(0.0012, px * 1.1);
        float line = exp(-(perp * perp) / (width * width));
        float fade = sin(progress * PI);
        float trail = pow(clamp((along - (head - tail)) / max(tail, 1e-3), 0.0, 1.0), 2.0);
        col += vec3(0.85, 0.92, 1.0) * line * inSeg * trail * fade * 2.2;
        // Bright head glow.
        vec3 headDir = start * cos(head) + travel * sin(head);
        float hd = length(dir - headDir);
        float glowSize = max(0.0018, px * 1.2);
        col += vec3(0.9, 0.95, 1.0) * exp(-0.5 * hd * hd / (glowSize * glowSize)) * fade * 1.6;
    }
    return col;
}

// ---------------------------------------------------------------- moon
vec3 moon(vec3 dir, vec3 moonDir, float size, inout float occlusion) {
    float radius = 0.035 * size;
    vec3 up = abs(moonDir.y) < 0.99 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 right = normalize(cross(up, moonDir));
    vec3 top = cross(moonDir, right);
    float front = dot(dir, moonDir);
    vec2 uv = vec2(dot(dir, right), dot(dir, top)) / radius;
    float r = length(uv);
    vec3 col = vec3(0.0);
    // Wide soft halo and a tighter corona.
    float angle = acos(clamp(front, -1.0, 1.0));
    col += vec3(0.35, 0.45, 0.70) * exp(-angle * angle / (radius * radius * 90.0)) * 0.35;
    col += vec3(0.70, 0.78, 0.95) * exp(-angle * angle / (radius * radius * 6.0)) * 0.35;
    if (front > 0.0 && r < 1.0) {
        float z = sqrt(1.0 - r * r);
        vec3 normal = vec3(uv, z);
        // Waxing gibbous lighting with a soft terminator.
        vec3 light = normalize(vec3(-0.55, 0.25, 0.8));
        float lit = smoothstep(-0.08, 0.25, dot(normal, light));
        vec2 surface = uv * 2.2;
        float maria = smoothstep(0.45, 0.7, fbm(surface * 1.3 + 4.0));
        float craters = 0.0;
        for (int i = 0; i < 6; i++) {
            vec3 h = hash33(vec3(float(i), 3.0, 9.0));
            vec2 c = (h.xy - 0.5) * 1.4;
            float cr = 0.08 + 0.14 * h.z;
            float d = length(uv - c) / cr;
            craters += (smoothstep(1.0, 0.8, d) * 0.25 - smoothstep(1.15, 1.0, d) * smoothstep(0.85, 1.0, d) * 0.25);
        }
        vec3 surfaceColor = vec3(0.93, 0.92, 0.88) * (1.0 - 0.32 * maria) * (1.0 - craters) * (0.88 + 0.12 * fbm(surface * 6.0));
        float limb = pow(z, 0.35);
        float edge = smoothstep(1.0, 0.97, r);
        vec3 disc = surfaceColor * lit * limb + vec3(0.03, 0.035, 0.05) * (1.0 - lit);
        col = mix(col, disc + col * 0.3, edge);
        occlusion = max(occlusion, edge);
    }
    return col;
}

// ---------------------------------------------------------------- clouds
// Returns rgb in .rgb and coverage in .a. sunDir is the main light (sun or moon).
vec4 clouds(vec3 dir, float amount, float time, vec3 sunDir, vec3 lit, vec3 shade, vec3 rim) {
    if (dir.y <= 0.005 || amount <= 0.001) return vec4(0.0);
    vec2 uv = dir.xz / (dir.y + 0.12) * 1.15;
    vec2 wind = vec2(time * 0.012, time * 0.004);
    vec2 warp = vec2(fbm(uv * 0.6 + wind * 0.5), fbm(uv * 0.6 - wind * 0.5 + 5.2));
    vec2 q = uv + wind + (warp - 0.5) * 0.9;
    float base = fbm(q);
    float detail = fbm(q * 3.1 + 11.0);
    float coverage = mix(0.68, 0.38, amount);
    float density = smoothstep(coverage, coverage + 0.22, base * 0.78 + detail * 0.28);
    if (density <= 0.0) return vec4(0.0);
    // One light-direction sample for self shadowing / silver lining.
    vec2 toSun = normalize(sunDir.xz + vec2(1e-4)) * 0.08;
    float towards = fbm(q + toSun) * 0.78 + fbm((q + toSun) * 3.1 + 11.0) * 0.28;
    float shadow = clamp((towards - (base * 0.78 + detail * 0.28)) * 4.0 + 0.5, 0.0, 1.0);
    vec3 col = mix(lit, shade, shadow * 0.85);
    float sunAngle = max(dot(dir, sunDir), 0.0);
    col += rim * pow(sunAngle, 10.0) * (1.0 - density) * 1.6;
    float horizonFade = smoothstep(0.0, 0.12, dir.y);
    return vec4(col, density * horizonFade);
}

// ---------------------------------------------------------------- aurora
vec3 aurora(vec3 dir, float time, float strength) {
    if (dir.y <= 0.02 || strength <= 0.0) return vec3(0.0);
    vec3 col = vec3(0.0);
    for (int i = 0; i < 10; i++) {
        float fi = float(i);
        float height = 0.6 + fi * 0.06;
        vec2 uv = dir.xz / dir.y * height;
        uv.x += time * 0.015;
        float band = sin(uv.x * 1.6 + fbm(uv * 0.7 + time * 0.03) * 4.0) * 0.5 + 0.5;
        float curtain = smoothstep(0.35, 0.0, abs(uv.y + 0.4 + 0.35 * sin(uv.x * 0.8 + time * 0.05)));
        float rays = 0.6 + 0.4 * noise2(vec2(uv.x * 14.0, time * 0.4));
        float a = band * curtain * rays * (1.0 - fi / 10.0);
        vec3 tint = mix(vec3(0.10, 1.0, 0.55), vec3(0.55, 0.25, 1.0), fi / 10.0);
        col += tint * a * 0.16;
    }
    return col * strength * smoothstep(0.02, 0.25, dir.y);
}

void main() {
    vec3 dir = normalize(skyDir);
    // View angle covered by one pixel: drives star sizes and anti-aliasing.
    float px = max(0.5 * (length(dFdx(dir)) + length(dFdy(dir))), 0.00001);
    float time = Sky0.x, cloudTime = Sky0.y, cloudAmount = Sky0.w;
    int preset = int(Sky0.z + 0.5);
    float h = clamp(dir.y, 0.0, 1.0);
    vec3 col;
    vec3 horizon;

    if (preset == 1) {
        // ------------------------------------------------ blue day sky
        vec3 sunDir = normalize(vec3(0.35, 0.72, -0.6));
        vec3 zenith = vec3(0.13, 0.36, 0.82);
        horizon = vec3(0.62, 0.79, 0.96);
        col = mix(horizon, zenith, pow(h, 0.45));
        float s = max(dot(dir, sunDir), 0.0);
        col += vec3(1.0, 0.92, 0.75) * (pow(s, 6.0) * 0.18 + pow(s, 64.0) * 0.45);
        col = mix(col, vec3(1.0, 0.98, 0.92), smoothstep(0.99955, 0.99975, s));
        vec4 c1 = clouds(dir, cloudAmount, cloudTime, sunDir, vec3(1.0, 1.0, 1.0), vec3(0.60, 0.66, 0.76), vec3(1.0, 0.95, 0.85));
        col = mix(col, c1.rgb, c1.a);
        vec4 c2 = clouds(normalize(dir + vec3(0.0, 0.25, 0.0)), cloudAmount * 0.6, cloudTime * 0.6 + 40.0, sunDir,
            vec3(0.96, 0.97, 1.0), vec3(0.72, 0.78, 0.88), vec3(1.0));
        col = mix(col, c2.rgb, c2.a * 0.35 * (1.0 - c1.a));
    } else if (preset == 2) {
        // ------------------------------------------------ sunset
        vec3 sunDir = normalize(vec3(0.85, 0.05, -0.52));
        vec3 zenith = vec3(0.10, 0.12, 0.32);
        vec3 mid = vec3(0.62, 0.30, 0.42);
        horizon = vec3(1.0, 0.52, 0.22);
        col = mix(horizon, mid, smoothstep(0.0, 0.25, h));
        col = mix(col, zenith, smoothstep(0.2, 0.85, h));
        float s = max(dot(dir, sunDir), 0.0);
        col += vec3(1.0, 0.55, 0.20) * (pow(s, 4.0) * 0.45 + pow(s, 40.0) * 0.6);
        col = mix(col, vec3(1.0, 0.85, 0.55), smoothstep(0.9990, 0.9994, s));
        col += starLayer(dir, 70.0, 0.06 * Sky1.x, 0.55, 0.80, time, px, 0.0, 11.0) * smoothstep(0.35, 0.9, h) * 0.6;
        vec4 c = clouds(dir, cloudAmount, cloudTime, sunDir, vec3(1.0, 0.62, 0.42), vec3(0.32, 0.18, 0.32), vec3(1.0, 0.75, 0.45));
        col = mix(col, c.rgb, c.a);
    } else {
        // ------------------------------------------------ night (0) and aurora (3)
        vec3 moonDir = normalize(vec3(-0.45, 0.50, 0.74));
        vec3 zenith = vec3(0.006, 0.012, 0.040);
        horizon = preset == 3 ? vec3(0.030, 0.070, 0.090) : vec3(0.040, 0.065, 0.150);
        col = mix(horizon, zenith, pow(h, 0.55));
        float band = 0.0;
        vec3 milky = vec3(0.0);
        if (dir.y > -0.05) milky = milkyWay(dir, Sky2.x, band);
        float milkyWeight = Sky2.x * smoothstep(-0.02, 0.3, dir.y);
        float occlusion = 0.0;
        vec3 moonLight = moon(dir, moonDir, Sky1.z, occlusion);
        col += milky * smoothstep(0.0, 0.3, h) * (1.0 - occlusion);
        // Stars fade into the moon glare and get dimmer and warmer near the horizon (extinction).
        float glare = smoothstep(0.985, 0.9995, dot(dir, moonDir));
        float extinction = smoothstep(0.0, 0.25, dir.y);
        float starMask = smoothstep(0.0, 0.12, dir.y) * (1.0 - occlusion) * (1.0 - 0.85 * glare);
        vec3 stars = vec3(0.0);
        if (dir.y > -0.01) {
            float dust = clamp(0.06 * Sky1.x * (1.0 + 5.0 * band * milkyWeight), 0.0, 0.9);
            stars += starLayer(dir, 160.0, dust, 0.34, 0.55, time, px, 0.0, 3.0);
            stars += starLayer(dir, 70.0, 0.10 * Sky1.x, 0.80, 0.80, time, px, 0.0, 11.0);
            stars += starLayer(dir, 26.0, 0.06 * Sky1.x, 1.70, 1.10, time, px, 1.0, 29.0);
            // Fine grain of countless faint stars inside the Milky Way.
            if (band * milkyWeight > 0.05) stars += starLayer(dir, 230.0, 0.55 * band * milkyWeight * min(Sky1.x, 1.0), 0.22, 0.50, time, px, 0.0, 41.0);
            stars *= (1.0 + 0.5 * band * milkyWeight) * mix(vec3(1.0, 0.72, 0.52), vec3(1.0), extinction);
        }
        col += stars * starMask;
        col += meteors(dir, time, Sky1.y, px) * smoothstep(0.0, 0.1, dir.y) * (1.0 - occlusion);
        if (preset == 3) col += aurora(dir, time, Sky1.w) * (1.0 - occlusion);
        vec4 c = clouds(dir, cloudAmount * 0.85, cloudTime, moonDir, vec3(0.20, 0.23, 0.33), vec3(0.03, 0.04, 0.07), vec3(0.55, 0.62, 0.85));
        col = mix(col, c.rgb, c.a * 0.9);
        col += moonLight * (1.0 - c.a * 0.85);
    }
    // Below the horizon: fade into the horizon/fog colour so terrain fog blends seamlessly.
    if (dir.y < 0.0) col = mix(horizon, horizon * 0.55, smoothstep(0.0, -0.35, dir.y));
    // Gentle dithering against banding in dark gradients.
    col += (hash12(gl_FragCoord.xy) - 0.5) / 255.0;
    fragColor = vec4(max(col, vec3(0.0)), 1.0);
}
