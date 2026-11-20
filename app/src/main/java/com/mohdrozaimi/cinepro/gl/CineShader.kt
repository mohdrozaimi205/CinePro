package com.mohdrozaimi.cinepro.gl

/**
 * Shader GPU yang buat semua kesan cinematic dalam SATU laluan:
 * kesan lensa (Black Mist / Tiffen / CineBloom) -> halation -> warna (ARRI, Canon, Harcourt...)
 * -> vignette -> film grain -> letterbox.
 */
object CineShader {

    const val VERTEX = """
attribute vec4 aPosition;
attribute vec4 aTexCoord;
uniform mat4 uTexMatrix;
varying vec2 vTex;
varying vec2 vUv;
void main() {
    gl_Position = aPosition;
    vTex = (uTexMatrix * aTexCoord).xy;
    vUv = aTexCoord.xy;
}
"""

    const val FRAGMENT = """
#extension GL_OES_EGL_image_external : require
precision highp float;

varying vec2 vTex;
varying vec2 vUv;

uniform samplerExternalOES sTex;
uniform int uLook;
uniform float uLookAmt;
uniform int uLens;
uniform float uLensAmt;
uniform float uHal;
uniform float uVig;
uniform float uGrain;
uniform float uLetterbox;
uniform float uOutAspect;
uniform vec2 uOutSize;
uniform vec2 uTexel;
uniform float uTime;

float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
vec3 sat(vec3 c, float s) { return mix(vec3(luma(c)), c, s); }
vec3 sCurve(vec3 x, float k) {
    vec3 y = clamp(x, 0.0, 1.0);
    return mix(y, y * y * (3.0 - 2.0 * y), k);
}
vec3 shoulder(vec3 x, float k) { return x / (1.0 + k * x) * (1.0 + k); }

vec3 lookArri(vec3 c) {
    c = shoulder(c, 0.25);
    c = sCurve(c, 0.22);
    c = sat(c, 0.9);
    c *= vec3(1.03, 1.0, 0.96);
    return c * 0.96 + 0.02;
}
vec3 lookCanon(vec3 c) {
    c = sCurve(c, 0.3);
    c *= vec3(1.05, 0.985, 0.95);
    return sat(c, 1.08);
}
vec3 lookHarcourt(vec3 c) {
    float l = dot(c, vec3(0.38, 0.52, 0.10));
    l = sCurve(vec3(l), 0.75).x;
    l = pow(max(l, 0.0), 1.15);
    return vec3(l) * vec3(1.0, 0.99, 0.97);
}
vec3 lookTealOrange(vec3 c) {
    c = sCurve(c, 0.35);
    float l = luma(c);
    c = mix(c, c * vec3(0.85, 1.0, 1.12), (1.0 - l) * 0.6);
    c = mix(c, c * vec3(1.12, 1.0, 0.85), l * 0.6);
    return sat(c, 1.1);
}
vec3 lookKodak(vec3 c) {
    c = sCurve(c, 0.4);
    c *= vec3(1.06, 1.0, 0.9);
    float l = luma(c);
    c = mix(c + vec3(0.0, 0.02, 0.035), c, smoothstep(0.0, 0.4, l));
    c = c * 0.94 + 0.04;
    return sat(c, 0.95);
}
vec3 applyLook(vec3 c) {
    if (uLook == 1) return lookArri(c);
    if (uLook == 2) return lookCanon(c);
    if (uLook == 3) return lookHarcourt(c);
    if (uLook == 4) return lookTealOrange(c);
    if (uLook == 5) return lookKodak(c);
    return c;
}

float hash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }

void main() {
    vec3 col = texture2D(sTex, vTex).rgb;

    bool useGlow = uLens > 0 || uHal > 0.5;
    if (useGlow) {
        float radius = 8.0;
        float thr = 0.7;
        if (uLens == 1) { radius = 10.0; thr = 0.6; }
        if (uLens == 2) { radius = 14.0; thr = 0.45; }
        if (uLens == 3) { radius = 22.0; thr = 0.5; }

        vec3 glow = vec3(0.0);
        vec3 blur = vec3(0.0);
        for (int i = 0; i < 8; i++) {
            float a = float(i) * 0.785398;
            vec2 d = vec2(cos(a), sin(a));
            vec3 s1 = texture2D(sTex, vTex + d * uTexel * radius).rgb;
            vec3 s2 = texture2D(sTex, vTex + vec2(d.y, -d.x) * uTexel * radius * 0.5).rgb;
            glow += max(s1 - thr, 0.0) + max(s2 - thr, 0.0);
            blur += s1 + s2;
        }
        glow = glow / (16.0 * (1.0 - thr));
        blur = blur / 16.0;

        float a = uLensAmt;
        if (uLens == 1) {
            col = mix(col, blur, a * 0.15 * smoothstep(0.5, 1.0, luma(col)));
            col += glow * 0.9 * a;
        } else if (uLens == 2) {
            col = mix(col, blur, 0.25 * a);
            col += glow * 0.7 * a;
            col += (1.0 - col) * 0.06 * a * (1.0 - luma(col));
        } else if (uLens == 3) {
            col = mix(col, blur, 0.2 * a);
            col += glow * 1.6 * a;
        }
        if (uHal > 0.5) {
            col += glow * vec3(1.0, 0.3, 0.1) * 0.6;
        }
    }

    col = clamp(col, 0.0, 1.0);
    col = mix(col, applyLook(col), uLookAmt);

    if (uVig > 0.5) {
        vec2 p = vUv - 0.5;
        col *= 1.0 - 0.35 * smoothstep(0.3, 0.75, length(p));
    }

    if (uGrain > 0.0) {
        float n = hash(floor(vUv * uOutSize) + vec2(uTime, uTime * 1.7)) - 0.5;
        float w = 1.0 - abs(luma(col) * 2.0 - 1.0) * 0.5;
        col += n * uGrain * 0.12 * w;
    }

    if (uLetterbox > 0.0) {
        if (uOutAspect < uLetterbox) {
            float bar = (1.0 - uOutAspect / uLetterbox) * 0.5;
            if (vUv.y < bar || vUv.y > 1.0 - bar) col = vec3(0.0);
        } else {
            float bar = (1.0 - uLetterbox / uOutAspect) * 0.5;
            if (vUv.x < bar || vUv.x > 1.0 - bar) col = vec3(0.0);
        }
    }

    gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
"""
}
