// utility:ItemSwapAnimation
(() => {
  const L=globalThis.RigAnimationLibrary;
  const {play:__utilityPlay,arm:__utilityArm,other:__utilityOther,upper:__utilityUpper,elbow:__utilityElbow,thigh:__utilityThigh,knee:__utilityKnee,foot:__utilityFoot}=L.utility;
  class UtilityItemSwapAnimation13c62e6bAnimation extends L.ProceduralAnimation { constructor() { super('utility:ItemSwapAnimation',(c,r,t,w)=>{const s=__utilityArm(c),p=clamp(t/.5,0,1),a=Math.sin(p*Math.PI);r.rotate(__utilityUpper(s),-28*a*w,0,(s==='right'?-38:38)*a*w);return true;}); } }
  L.registerProcedural(new UtilityItemSwapAnimation13c62e6bAnimation());
})();
