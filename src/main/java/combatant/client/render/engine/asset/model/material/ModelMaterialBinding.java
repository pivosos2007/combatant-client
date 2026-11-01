/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.model.material;

import combatant.client.render.engine.asset.model.data.AlphaMode;
import combatant.client.render.engine.asset.model.data.ModelTextureInfo;
import combatant.client.render.engine.material.MaterialSurfaceDescriptor;

/**
 * Canonical Combatant material plus model factors that are not yet part of the shared terrain material ABI.
 * Shaderpack adapters consume this contract; import code never encodes Photon/LabPBR channels itself.
 */
public record ModelMaterialBinding(
        MaterialSurfaceDescriptor surface,
        float[] baseColorFactor,
        float[] emissiveFactor,
        float emissiveStrength,
        float normalScale,
        float occlusionStrength,
        AlphaMode alphaMode,
        float alphaCutoff,
        boolean doubleSided,
        boolean unlit,
        ModelTextureInfo baseColorTexture,
        ModelTextureInfo metallicRoughnessTexture,
        ModelTextureInfo normalTexture,
        ModelTextureInfo occlusionTexture,
        ModelTextureInfo emissiveTexture
) {
    public ModelMaterialBinding {
        baseColorFactor = baseColorFactor.clone();
        emissiveFactor = emissiveFactor.clone();
    }

    @Override public float[] baseColorFactor() { return baseColorFactor.clone(); }
    @Override public float[] emissiveFactor() { return emissiveFactor.clone(); }
}
