/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.core;

import org.joml.Matrix4f;

/**
 * Dense solved socket matrices for a rig instance.
 */
public final class RigSocketState {
    private final RigDefinition definition;
    private final Matrix4f[] modelMatrices;
    private final Matrix4f localScratch = new Matrix4f();

    RigSocketState(RigDefinition definition) {
        this.definition = definition;
        this.modelMatrices = new Matrix4f[definition.socketCount()];
        for (int i = 0; i < modelMatrices.length; i++) modelMatrices[i] = new Matrix4f();
    }

    public int size() {
        return modelMatrices.length;
    }

    public Matrix4f modelMatrix(int socketIndex, Matrix4f destination) {
        checkSocketIndex(socketIndex);
        return destination.set(modelMatrices[socketIndex]);
    }

    public Matrix4f modelMatrix(String socketName, Matrix4f destination) {
        return modelMatrix(definition.requireSocketIndex(socketName), destination);
    }

    void solve(Matrix4f[] boneModelMatrices) {
        for (int i = 0; i < modelMatrices.length; i++) {
            RigSocket socket = definition.socketRef(i);
            socket.local().matrix(localScratch);
            modelMatrices[i].set(boneModelMatrices[socket.boneIndex()]).mul(localScratch);
        }
    }

    private void checkSocketIndex(int socketIndex) {
        if (socketIndex < 0 || socketIndex >= modelMatrices.length) {
            throw new IndexOutOfBoundsException("Socket index " + socketIndex + " outside [0, " + (modelMatrices.length - 1) + "]");
        }
    }
}
