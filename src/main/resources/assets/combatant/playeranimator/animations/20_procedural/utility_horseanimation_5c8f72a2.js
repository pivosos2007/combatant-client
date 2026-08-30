// utility:HorseAnimation
(() => {
  const L=globalThis.RigAnimationLibrary;
  const {play:__utilityPlay,arm:__utilityArm,other:__utilityOther,upper:__utilityUpper,elbow:__utilityElbow,thigh:__utilityThigh,knee:__utilityKnee,foot:__utilityFoot}=L.utility;
  class UtilityHorseAnimation5c8f72a2Animation extends L.ProceduralAnimation { constructor() { super('utility:HorseAnimation',(c,r,t,w)=>__utilityPlay(c.horizontalSpeed>.02?'motion:horse_riding':'motion:horse_riding_idle',c,r,c.walkTime,w)); } }
  L.registerProcedural(new UtilityHorseAnimation5c8f72a2Animation());
})();
