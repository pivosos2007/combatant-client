// utility:ElytraAnimation
(() => {
  const L=globalThis.RigAnimationLibrary;
  const {play:__utilityPlay,arm:__utilityArm,other:__utilityOther,upper:__utilityUpper,elbow:__utilityElbow,thigh:__utilityThigh,knee:__utilityKnee,foot:__utilityFoot}=L.utility;
  class UtilityElytraAnimationb3b3f2e6Animation extends L.ProceduralAnimation { constructor() { super('utility:ElytraAnimation',(c,r,t,w)=>__utilityPlay('motion:elytra_fly_mit',c,r,c.continuousSeconds,w)); } }
  L.registerProcedural(new UtilityElytraAnimationb3b3f2e6Animation());
})();
