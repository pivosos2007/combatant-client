// ElytraLayer: isolated player animation/state layer.
(() => {
  const R=globalThis.CombatantPlayerRig;
  const {TAU, clamp, saturate, hypot2, expAlpha, damp, smooth01, wrapPi, smoother01, pulse, upper, elbow, forearm, wrist, hand, thigh, knee, foot, flexElbow}=R;
  class ElytraLayer {
    constructor(rig,d) { this.rig=rig;this.d=d; }
    apply(c,m,w,strength) {
      if (w<=1e-4) return;
      const k=w*strength;
      const speed=saturate(c.horizontalSpeed/.9);
      const sink=saturate(-c.velocity.y/.45);
      const lagX=m.inertiaStrafe*.140*k, lagZ=-m.inertiaForward*.110*k;
      this.rig.rotate('chest',(-2-speed*4+sink*2)*k,-m.inertiaStrafe*6*k,-m.inertiaStrafe*10*k);
      this.rig.rotate('spine_lower',-m.inertiaForward*5*k,0,-m.inertiaStrafe*6*k);
      this.rig.move('right_thigh',lagX,0,lagZ);
      this.rig.move('left_thigh',lagX,0,lagZ);
      this.rig.rotate('right_upper_arm',(5+sink*4)*k,-7*k,16*k);
      this.rig.rotate('left_upper_arm',(5+sink*4)*k,7*k,-16*k);
      flexElbow(this.rig,'right',10*k);
      flexElbow(this.rig,'left',10*k);
      this.rig.rotate('right_thigh',(-5-sink*4)*k,-2*k,7*k+m.inertiaStrafe*8*k);
      this.rig.rotate('left_thigh',(-5-sink*4)*k,2*k,-7*k+m.inertiaStrafe*8*k);
      this.rig.rotate('right_knee',(8+sink*6)*k,0,0);
      this.rig.rotate('left_knee',(8+sink*6)*k,0,0);
    }
  }
  R.ElytraLayer=ElytraLayer;
})();
