// utility:EatDrinkAnimation
(() => {
  const L=globalThis.RigAnimationLibrary;
  const {play:__utilityPlay,arm:__utilityArm,other:__utilityOther,upper:__utilityUpper,elbow:__utilityElbow,thigh:__utilityThigh,knee:__utilityKnee,foot:__utilityFoot}=L.utility;
  class UtilityEatDrinkAnimation452fc4ddAnimation extends L.ProceduralAnimation { constructor() { super('utility:EatDrinkAnimation',(c,r,t,w)=>__utilityPlay(c.useAction==='drink'?'motion:drinking':'motion:eating',c,r,c.useTimeSeconds,w)); } }
  L.registerProcedural(new UtilityEatDrinkAnimation452fc4ddAnimation());
})();
