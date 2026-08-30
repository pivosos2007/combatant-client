// utility:FallingAnimation
(() => {
  const L=globalThis.RigAnimationLibrary;
  const {play:__utilityPlay,arm:__utilityArm,other:__utilityOther,upper:__utilityUpper,elbow:__utilityElbow,thigh:__utilityThigh,knee:__utilityKnee,foot:__utilityFoot}=L.utility;
  class UtilityFallingAnimationc419eb47Animation extends L.ProceduralAnimation { constructor() { super('utility:FallingAnimation',(c,r,t,w)=>__utilityPlay('motion:falling',c,r,c.continuousSeconds,w)); } }
  L.registerProcedural(new UtilityFallingAnimationc419eb47Animation());
})();
