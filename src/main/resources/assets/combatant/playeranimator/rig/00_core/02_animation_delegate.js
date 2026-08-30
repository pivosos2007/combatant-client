// AnimationDelegate: isolated rig component.
(() => {
  const R=globalThis.CombatantPlayerRig;
  const {TAU, clamp, saturate, hypot2, expAlpha, damp, smooth01, wrapPi, smoother01, pulse, upper, elbow, forearm, wrist, hand, thigh, knee, foot, flexElbow}=R;
  class AnimationDelegate {
    constructor(rig) { this.rig=rig; this.lib=globalThis.RigAnimationLibrary; }
    get(name) { return this.lib?.get(name) || null; }
    play(name,time,weight=1,options=null) {
      if (weight<=1e-4) return false;
      return this.lib?.play(name,this.rig,time,weight,options) || false;
    }
    phaseTime(name,phase) {
      const clip=this.get(name); if (!clip) return 0;
      const normalized=((phase/TAU)%1+1)%1;
      return normalized*clip.length;
    }
    phase(name,phase,weight=1,options=null) { return this.play(name,this.phaseTime(name,phase),weight,options); }
  }
  R.AnimationDelegate=AnimationDelegate;
})();
