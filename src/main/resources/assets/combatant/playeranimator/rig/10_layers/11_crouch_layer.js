// CrouchLayer: isolated player animation/state layer.
(() => {
  const R=globalThis.CombatantPlayerRig;
  const {TAU, clamp, saturate, hypot2, expAlpha, damp, smooth01, wrapPi, smoother01, pulse, upper, elbow, forearm, wrist, hand, thigh, knee, foot, flexElbow}=R;
  class CrouchLayer {
    constructor(rig,d,joints) { this.rig=rig;this.d=d;this.joints=joints; }
    apply(c,m,w,strength) {
      if (w<=1e-4) return;
      const k=w*strength;
      const moving=smooth01(saturate(Math.max(
        c.walkAnimationSpeed/Math.max(.001,c.speedValue)*1.75,
        m.speed/.075,
        c.horizontalSpeed/.075
      )));
      // Slow deliberate stepping, but with enough travel to actually read as locomotion.
      const sneakHz=.46+.34*moving;
      const sneakPhase=m.advanceCycle('sneak',sneakHz,2.6);
      // Tactical crouch: center of mass goes down/forward, but the spine remains nearly vertical.
      // Foot height is solved by AnatomicalJoints.crouch() instead of blindly lowering the pelvis.
      this.rig.move('pelvis',0,(1.45/16)*k,-.018*k);
      this.rig.rotate('pelvis',5*k,0,0);
      this.rig.rotate('spine_lower',-1.4*k,0,0);
      this.rig.rotate('spine_mid',-.8*k,0,0);
      this.rig.rotate('spine_upper',-.4*k,0,0);
      this.rig.rotate('chest',.6*k,m.strafe*4*k,m.inertiaStrafe*2.2*k);
      this.rig.rotate('right_upper_arm',-3*k,0,2*k);
      this.rig.rotate('left_upper_arm',-3*k,0,-2*k);
      this.joints.crouch(k,moving,sneakPhase);
    }
  }
  R.CrouchLayer=CrouchLayer;
})();
