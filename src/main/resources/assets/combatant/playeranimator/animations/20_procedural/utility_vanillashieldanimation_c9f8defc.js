// utility:VanillaShieldAnimation
(() => {
  const L=globalThis.RigAnimationLibrary;
  const {play:__utilityPlay,arm:__utilityArm,other:__utilityOther,upper:__utilityUpper,elbow:__utilityElbow,thigh:__utilityThigh,knee:__utilityKnee,foot:__utilityFoot}=L.utility;
  class UtilityVanillaShieldAnimationc9f8defcAnimation extends L.ProceduralAnimation { constructor() { super('utility:VanillaShieldAnimation',(c,r,t,w)=>__utilityPlay('motion:use_shield',c,r,c.useTimeSeconds,w)); } }
  L.registerProcedural(new UtilityVanillaShieldAnimationc9f8defcAnimation());
})();
