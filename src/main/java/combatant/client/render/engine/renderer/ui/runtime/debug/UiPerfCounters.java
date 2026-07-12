/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.debug;

public final class UiPerfCounters {
    private int nodeCount;
    private long styleNanos;
    private long reconcileNanos;
    private long layoutNanos;
    private long renderNanos;
    private long patchNanos;
    private long hitTestNanos;
    private int propPatchCount;
    private int boundsPatchCount;
    private String lastError = "";

    public int nodeCount() {
        return nodeCount;
    }

    public long styleNanos() {
        return styleNanos;
    }

    public long reconcileNanos() {
        return reconcileNanos;
    }

    public long layoutNanos() {
        return layoutNanos;
    }

    public long renderNanos() {
        return renderNanos;
    }

    public long patchNanos() {
        return patchNanos;
    }

    public long hitTestNanos() {
        return hitTestNanos;
    }

    public int propPatchCount() {
        return propPatchCount;
    }

    public int boundsPatchCount() {
        return boundsPatchCount;
    }

    public String lastError() {
        return lastError;
    }

    public void setNodeCount(int nodeCount) {
        this.nodeCount = Math.max(0, nodeCount);
    }

    public void setStyleNanos(long styleNanos) {
        this.styleNanos = Math.max(0L, styleNanos);
    }

    public void setReconcileNanos(long reconcileNanos) {
        this.reconcileNanos = Math.max(0L, reconcileNanos);
    }

    public void setLayoutNanos(long layoutNanos) {
        this.layoutNanos = Math.max(0L, layoutNanos);
    }

    public void setRenderNanos(long renderNanos) {
        this.renderNanos = Math.max(0L, renderNanos);
    }

    public void setPatchNanos(long patchNanos) {
        this.patchNanos = Math.max(0L, patchNanos);
    }

    public void addPatchNanos(long patchNanos) {
        this.patchNanos += Math.max(0L, patchNanos);
    }

    public void setHitTestNanos(long hitTestNanos) {
        this.hitTestNanos = Math.max(0L, hitTestNanos);
    }

    public void setPropPatchCount(int propPatchCount) {
        this.propPatchCount = Math.max(0, propPatchCount);
    }

    public void setBoundsPatchCount(int boundsPatchCount) {
        this.boundsPatchCount = Math.max(0, boundsPatchCount);
    }

    public void setLastError(String lastError) {
        this.lastError = lastError != null ? lastError : "";
    }
}
