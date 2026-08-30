// VehicleLayer: isolated player animation/state layer.
(() => {
  const R=globalThis.CombatantPlayerRig;
  const {TAU, clamp, saturate, hypot2, expAlpha, damp, smooth01, wrapPi, smoother01, pulse, upper, elbow, forearm, wrist, hand, thigh, knee, foot, flexElbow}=R;
  class VehicleLayer {
    constructor(rig,d,joints) { this.rig=rig;this.d=d;this.joints=joints; }
    boat(c,w,strength) {
      if (w<=1e-4) return;
      const k=w*strength;
      this.joints.passenger(k);
      this.rig.rotate('chest',3*k,0,0);
      const paddle=(side,active,time) => {
        if (!active) {
          this.rig.rotate(upper(side),-8*k,0,(side==='right'?20:-20)*k);
          flexElbow(this.rig,side,18*k);
          return;
        }
        // getRowingTime() is already continuous/interpolated. Use it directly rather than a
        // separate low-frequency clock.
        const p=time;
        const pull=(Math.sin(p)+1)*.5;
        const sign=side==='right'?1:-1;
        this.rig.rotate(upper(side),(-58+92*pull)*k,sign*(-8+10*pull)*k,sign*(34-14*pull)*k);
        flexElbow(this.rig,side,(48-30*pull)*k);
      };
      paddle('left',c.boatLeft,c.boatLeftTime);
      paddle('right',c.boatRight,c.boatRightTime);
    }
    horse(c,w,strength) {
      if (w<=1e-4) return;
      const k=w*strength;
      this.joints.passenger(k);
      const gait=Math.sin(c.walkPhase)*saturate(c.walkAnimationSpeed/Math.max(.001,c.speedValue));
      this.rig.rotate('chest',2*k,0,0);
      this.rig.rotate('right_upper_arm',(-12-gait*8)*k,0,8*k);
      this.rig.rotate('left_upper_arm',(-12+gait*8)*k,0,-8*k);
    }
    passenger(c,w,strength) {
      if (w<=1e-4) return;
      const k=w*strength;
      this.joints.passenger(k);
      this.rig.rotate('right_upper_arm',-12*k,0,5*k);
      this.rig.rotate('left_upper_arm',-12*k,0,-5*k);
    }
  }
  R.VehicleLayer=VehicleLayer;
})();
