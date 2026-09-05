(() => {
  const L=globalThis.RigAnimationLibrary;
  L.registerProcedural(new L.ProceduralAnimation('motion:swimming/swimming',(c,r,t,w)=>{
    const seconds=Number.isFinite(c.continuousSeconds)?c.continuousSeconds:(Number.isFinite(t)?t:0);
    const speed=Math.max(0,Number(c.horizontalSpeed)||0);
    const phase=seconds*Math.PI*2*(.61+Math.min(1,speed/.18)*.37)
      +Math.sin(seconds*.37)*.21+Math.sin(seconds*.149)*.08;
    const water=globalThis.CombatantPlayerRig?.WaterPoseMath;
    if (water?.applySwim) { water.applySwim(c,r,phase,w,2.417); return true; }
    const arm=Math.sin(phase), kick=Math.sin(phase*1.73);
    r.rotate('right_upper_arm',(-88-arm*48)*w,0,10*w);
    r.rotate('left_upper_arm',(-88+arm*48)*w,0,-10*w);
    r.rotate('right_thigh',(-4+kick*18)*w,0,4*w);
    r.rotate('left_thigh',(-4-kick*18)*w,0,-4*w);
    return true;
  }));
})();
