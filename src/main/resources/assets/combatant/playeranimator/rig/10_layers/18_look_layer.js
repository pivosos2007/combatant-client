// LookLayer: isolated player animation/state layer.
(() => {
  const R=globalThis.CombatantPlayerRig;
  const {TAU, clamp, saturate, hypot2, expAlpha, damp, smooth01, wrapPi, smoother01, pulse, upper, elbow, forearm, wrist, hand, thigh, knee, foot, flexElbow}=R;
  class LookLayer {
    constructor(rig) { this.rig=rig; }
    apply(c,m,strength) {
      const yaw=clamp(c.headYaw,-95,95);
      const pitch=clamp(c.headPitch,-90,90);
      const aiming=(c.usingItem||c.vanillaUsingItem) &&
        /^(bow|crossbow|spear)$/.test(c.useAction) ||
        c.leftArmPose==='bow_and_arrow' || c.rightArmPose==='bow_and_arrow' ||
        c.leftArmPose==='spear' || c.rightArmPose==='spear' ||
        c.leftArmPose==='throw_trident' || c.rightArmPose==='throw_trident';

      // The neck keeps the normal vanilla range; only the excess is progressively transferred to
      // the torso. Aiming starts that transfer earlier, but never snaps the whole body to the head.
      const threshold=aiming?12:38;
      const excess=Math.max(0,Math.abs(yaw)-threshold);
      const yawTarget=Math.sign(yaw)*Math.min(aiming?48:34,excess*(aiming?.72:.58));
      const pitchExcess=Math.max(0,Math.abs(pitch)-(aiming?42:62));
      const pitchTarget=Math.sign(pitch)*Math.min(aiming?9:6,pitchExcess*(aiming?.28:.20));

      m.lookBodyYaw=damp(m.lookBodyYaw,yawTarget,m.frameDt,aiming?5.6:3.8);
      m.lookBodyPitch=damp(m.lookBodyPitch,pitchTarget,m.frameDt,aiming?4.0:3.0);
      const y=m.lookBodyYaw*clamp(strength,0,1.25);
      const x=m.lookBodyPitch*clamp(strength,0,1.25);

      if (Math.abs(y)>1e-4) {
        // Curved spine rather than one rigid body yaw.
        this.rig.rotate('spine_lower',0,y*.18,0);
        this.rig.rotate('spine_mid',0,y*.29,0);
        this.rig.rotate('chest',0,y*.53,0);
        // Remove exactly the transferred local yaw from the anatomical neck/head chain.
        this.rig.rotate('neck_lower',0,-y*.12,0);
        this.rig.rotate('neck_upper',0,-y*.18,0);
        this.rig.rotate('head',0,-y*.70,0);
      }
      if (Math.abs(x)>1e-4) {
        // Keep vertical look mostly in the head so looking up/down does not visibly lower the skull.
        this.rig.rotate('spine_lower',x*.14,0,0);
        this.rig.rotate('spine_mid',x*.22,0,0);
        this.rig.rotate('chest',x*.64,0,0);
        this.rig.rotate('neck_lower',-x*.10,0,0);
        this.rig.rotate('neck_upper',-x*.16,0,0);
        this.rig.rotate('head',-x*.74,0,0);
      }
    }
  }
  R.LookLayer=LookLayer;
})();
