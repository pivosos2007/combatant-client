// utility:SwimAnimation - continuous procedural fallback, never delegates to a baked loop.
(() => {
  const L=globalThis.RigAnimationLibrary;
  class UtilitySwimAnimation27e349eeAnimation extends L.ProceduralAnimation {
    constructor() {
      super('utility:SwimAnimation',(c,r,t,w)=>{
        const R=globalThis.CombatantPlayerRig;
        const water=R?.WaterPoseMath;
        const seconds=Number.isFinite(c.continuousSeconds)?c.continuousSeconds:(Number.isFinite(t)?t:0);
        const speed=Math.max(0,Number(c.horizontalSpeed)||0);
        const phase=seconds*Math.PI*2*(.61+Math.min(1,speed/.18)*.37)
          +Math.sin(seconds*.41)*.18+Math.sin(seconds*.173)*.09;
        if (water?.applySwim) {
          water.applySwim(c,r,phase,w,1.731);
          return true;
        }
        // Minimal fallback for direct use before the rig module tree has been evaluated.
        const kick=Math.sin(phase*1.73), arm=Math.sin(phase);
        r.rotate('right_upper_arm',(-88-arm*48)*w,0,10*w);
        r.rotate('left_upper_arm',(-88+arm*48)*w,0,-10*w);
        r.rotate('right_thigh',(-4+kick*18)*w,0,4*w);
        r.rotate('left_thigh',(-4-kick*18)*w,0,-4*w);
        return true;
      });
    }
  }
  L.registerProcedural(new UtilitySwimAnimation27e349eeAnimation());
})();
