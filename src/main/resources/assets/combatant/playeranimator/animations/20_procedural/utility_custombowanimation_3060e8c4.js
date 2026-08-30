// utility:CustomBowAnimation
(() => {
  const L=globalThis.RigAnimationLibrary;
  const {play:__utilityPlay,arm:__utilityArm,other:__utilityOther,upper:__utilityUpper,elbow:__utilityElbow,thigh:__utilityThigh,knee:__utilityKnee,foot:__utilityFoot}=L.utility;
  class UtilityCustomBowAnimation3060e8c4Animation extends L.ProceduralAnimation { constructor() { super('utility:CustomBowAnimation',(c,r,t,w)=>__utilityPlay('motion:use_bow',c,r,clamp(c.useTimeSeconds,0,1),w)); } }
  L.registerProcedural(new UtilityCustomBowAnimation3060e8c4Animation());
})();
