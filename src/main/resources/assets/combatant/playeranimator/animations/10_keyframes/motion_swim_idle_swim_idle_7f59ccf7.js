(() => {
  const L=globalThis.RigAnimationLibrary;
  L.registerProcedural(new L.ProceduralAnimation('motion:swim_idle/swim_idle',(c,r,t,w)=>{
    const seconds=Number.isFinite(c.continuousSeconds)?c.continuousSeconds:(Number.isFinite(t)?t:0);
    const phase=seconds*Math.PI*2*.48+Math.sin(seconds*.29)*.17+Math.sin(seconds*.113)*.07;
    const water=globalThis.CombatantPlayerRig?.WaterPoseMath;
    if (water?.applySwim) { water.applySwim(c,r,phase,w*.72,4.083); return true; }
    const arm=Math.sin(phase);
    r.rotate('right_upper_arm',(-82-arm*30)*w,0,12*w);
    r.rotate('left_upper_arm',(-82+arm*30)*w,0,-12*w);
    return true;
  }));
})();
