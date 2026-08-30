// InertiaLayer: isolated player animation/state layer.
(() => {
  const R=globalThis.CombatantPlayerRig;
  const {TAU, clamp, saturate, hypot2, expAlpha, damp, smooth01, wrapPi, smoother01, pulse, upper, elbow, forearm, wrist, hand, thigh, knee, foot, flexElbow}=R;
  class InertiaLayer {
    constructor(rig) { this.rig=rig; }
    apply(c,m,mode,strength) {
      if (strength<=1e-4 || mode==='surface_swim' || mode==='swim' || mode==='boat' || mode==='horse' || mode==='passenger') return;
      const flight=!!c.creativeFlying || mode==='elytra' || mode==='air';
      const k=clamp(strength,0,1.35)*(c.creativeFlying?1.35:(flight?1.12:.82));
      const f=m.inertiaForward*k, s=m.inertiaStrafe*k, v=m.inertiaVertical*k;
      if (Math.abs(f)+Math.abs(s)+Math.abs(v)<1e-4) return;

      // Root responds first; distal chains carry the delayed velocity farther. This makes braking
      // readable in legs/arms instead of expressing all inertia as one chest tilt.
      this.rig.move('pelvis',s*.018,-v*.012,-f*.014);
      this.rig.rotate('pelvis',-f*2.5,0,s*3.4);
      this.rig.rotate('spine_lower',-f*4.0,0,s*5.0);
      this.rig.rotate('spine_mid',-f*3.2,0,s*4.4);
      this.rig.rotate('chest',-f*3.0,s*1.8,s*5.8);

      const armX=-f*(flight?10.5:7.0);
      const legX=-f*(flight?12.5:7.5);
      const verticalFlex=Math.abs(v)*(flight?7.0:3.5);
      this.rig.move('right_upper_arm',s*.030,-v*.010,-f*.022);
      this.rig.move('left_upper_arm',s*.030,-v*.010,-f*.022);
      this.rig.rotate('right_upper_arm',armX,0,s*8.5);
      this.rig.rotate('left_upper_arm',armX,0,s*8.5);
      this.rig.rotate('right_forearm',-f*4.5,s*3.5,s*3.0);
      this.rig.rotate('left_forearm',-f*4.5,s*3.5,s*3.0);

      this.rig.move('right_thigh',s*.052,-v*.014,-f*.040);
      this.rig.move('left_thigh',s*.052,-v*.014,-f*.040);
      this.rig.rotate('right_thigh',legX,0,s*10.0);
      this.rig.rotate('left_thigh',legX,0,s*10.0);
      this.rig.rotate('right_knee',verticalFlex,0,0);
      this.rig.rotate('left_knee',verticalFlex,0,0);
      this.rig.rotate('right_foot',-legX*.38-verticalFlex*.45,0,-s*4.0);
      this.rig.rotate('left_foot',-legX*.38-verticalFlex*.45,0,-s*4.0);
    }
  }
  R.InertiaLayer=InertiaLayer;
})();
