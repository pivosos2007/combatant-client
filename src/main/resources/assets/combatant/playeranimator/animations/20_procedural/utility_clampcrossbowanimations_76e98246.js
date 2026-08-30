// utility:ClampCrossbowAnimations
(() => {
  const L=globalThis.RigAnimationLibrary;
  const {play:__utilityPlay,arm:__utilityArm,other:__utilityOther,upper:__utilityUpper,elbow:__utilityElbow,thigh:__utilityThigh,knee:__utilityKnee,foot:__utilityFoot}=L.utility;
  class UtilityClampCrossbowAnimations76e98246Animation extends L.ProceduralAnimation { constructor() { super('utility:ClampCrossbowAnimations',(c,r,t,w)=>__utilityPlay('motion:hold_crossbow',c,r,c.useTimeSeconds,w)); } }
  L.registerProcedural(new UtilityClampCrossbowAnimations76e98246Animation());
})();
