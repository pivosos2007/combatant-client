// ClimbLayer: isolated player animation/state layer.
(() => {
  const R=globalThis.CombatantPlayerRig;
  const {TAU, clamp, saturate, hypot2, expAlpha, damp, smooth01, wrapPi, smoother01, pulse, upper, elbow, forearm, wrist, hand, thigh, knee, foot, flexElbow}=R;
  class ClimbLayer {
    constructor(rig,d,joints) { this.rig=rig;this.d=d;this.joints=joints; }
    apply(c,m,w,strength) {
      if (w<=1e-4) return;
      const k=w*strength;
      const active=Math.abs(m.vertical)>.006||m.speed>.006;
      const p=c.continuousSeconds*TAU*(active?.8:.2);
      const g=Math.sin(p);
      this.rig.rotate('right_upper_arm',(-112+g*42)*k,0,8*k);
      this.rig.rotate('left_upper_arm',(-112-g*42)*k,0,-8*k);
      flexElbow(this.rig,'right',(36-g*18)*k);
      flexElbow(this.rig,'left',(36+g*18)*k);
      // Ladder is in front of the player (-Z). A positive thigh X rotation sends the knee
      // toward +Z (behind the body), which made both legs fold away from the ladder. Keep the
      // normal positive knee flexion, but drive the hips forward so the planted and stepping feet
      // remain on the ladder side of the pelvis throughout the cycle.
      this.rig.rotate('right_thigh',(-34-g*25)*k,0,-4*k);
      this.rig.rotate('left_thigh',(-34+g*25)*k,0,4*k);
      this.rig.rotate('right_knee',(35+g*20)*k,0,0);
      this.rig.rotate('left_knee',(35-g*20)*k,0,0);
    }
  }
  R.ClimbLayer=ClimbLayer;
})();
