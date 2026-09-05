layout (std140) uniform UIBatch {
    vec4 uScreen; // xy = framebuffer size, zw = logical size
    vec4 uLayer;  // xy = logical origin, zw = logical extent of the active render target
};
