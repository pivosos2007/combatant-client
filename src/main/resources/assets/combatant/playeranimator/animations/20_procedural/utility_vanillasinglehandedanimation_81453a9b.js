// utility:VanillaSingleHandedAnimation
(() => {
  const L=globalThis.RigAnimationLibrary;
  const {play:__utilityPlay,arm:__utilityArm,other:__utilityOther,upper:__utilityUpper,elbow:__utilityElbow,thigh:__utilityThigh,knee:__utilityKnee,foot:__utilityFoot}=L.utility;
  class UtilityVanillaSingleHandedAnimation81453a9bAnimation extends L.ProceduralAnimation { constructor() { super('utility:VanillaSingleHandedAnimation',(c,r,t,w)=>{const s=__utilityArm(c);r.rotate(__utilityUpper(s),-18*w,0,(s==='right'?5:-5)*w);r.rotate(__utilityElbow(s),-12*w,0,0);return true;}); } }
  L.registerProcedural(new UtilityVanillaSingleHandedAnimation81453a9bAnimation());
})();
