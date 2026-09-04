/* Shared signed-distance geometry for UI shape and material shaders. */
float roundedBoxSdf(vec2 p, vec2 halfSize, float radius) {
    float r = clamp(radius, 0.0, min(halfSize.x, halfSize.y));
    vec2 q = abs(p) - halfSize + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

vec4 normalizeRadii(vec4 radii, vec2 size) {
    float maxR = 0.5 * min(size.x, size.y);
    vec4 r = clamp(radii, 0.0, maxR);
    float scale = 1.0;
    if (r.x + r.y > size.x) scale = min(scale, size.x / max(r.x + r.y, 0.0001));
    if (r.w + r.z > size.x) scale = min(scale, size.x / max(r.w + r.z, 0.0001));
    if (r.x + r.w > size.y) scale = min(scale, size.y / max(r.x + r.w, 0.0001));
    if (r.y + r.z > size.y) scale = min(scale, size.y / max(r.y + r.z, 0.0001));
    return r * scale;
}

float roundedCornersSdf(vec2 p, vec2 halfSize, vec4 radii) {
    vec2 corner = p.x < 0.0 ? vec2(radii.x, radii.w) : vec2(radii.y, radii.z);
    float radius = p.y < 0.0 ? corner.x : corner.y;
    vec2 q = abs(p) - halfSize + radius;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
}

float squircleSdf(vec2 p, vec2 halfSize, float exponent) {
    vec2 h = max(halfSize, vec2(0.0001));
    float n = clamp(exponent, 2.0, 16.0);
    vec2 q = abs(p) / h;
    vec2 qn = pow(q, vec2(n));
    float implicit = qn.x + qn.y - 1.0;
    vec2 gradient = n * vec2(
        pow(max(q.x, 0.000001), n - 1.0) / h.x,
        pow(max(q.y, 0.000001), n - 1.0) / h.y
    );
    float radial = (pow(max(qn.x + qn.y, 0.000001), 1.0 / n) - 1.0) * min(h.x, h.y);
    return length(gradient) > 0.00001 ? implicit / length(gradient) : radial;
}

float cornerCut(float u, float v, float cutX, float cutY) {
    if (cutX <= 0.0001 || cutY <= 0.0001) return -1.0;
    return (1.0 - u / cutX - v / cutY) / length(vec2(1.0 / cutX, 1.0 / cutY));
}

float chamferSdf(vec2 p, vec2 halfSize, vec4 cutX, vec4 cutY) {
    vec2 q = abs(p);
    float d = max(q.x - halfSize.x, q.y - halfSize.y);
    float left = p.x + halfSize.x;
    float right = halfSize.x - p.x;
    float top = p.y + halfSize.y;
    float bottom = halfSize.y - p.y;
    float tl = cornerCut(left, top, cutX.x, cutY.x);
    float tr = cornerCut(right, top, cutX.y, cutY.y);
    float br = cornerCut(right, bottom, cutX.z, cutY.z);
    float bl = cornerCut(left, bottom, cutX.w, cutY.w);
    return max(d, max(max(tl, tr), max(br, bl)));
}
