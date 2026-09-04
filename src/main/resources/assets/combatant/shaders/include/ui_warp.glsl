/* Shared projective interpolation helpers for RenderPipelineContract.UI_WARPED. */
float uiSourceInvW(vec4 local) {
    return abs(local.z) > 0.000001 ? local.z : 1.0;
}

vec4 uiProjectiveClipPosition(vec2 mappedPosition, vec2 logicalSize, vec4 local) {
    vec2 size = max(logicalSize, vec2(1.0));
    vec2 clip = mappedPosition / size * 2.0 - 1.0;
    float projectiveW = 1.0 / uiSourceInvW(local);
    return vec4(clip.x * projectiveW, -clip.y * projectiveW, 0.0, projectiveW);
}

vec4 uiProjectiveSourceLocal(vec4 local) {
    float invW = uiSourceInvW(local);
    return vec4(local.xy / invW, 1.0, local.w);
}
