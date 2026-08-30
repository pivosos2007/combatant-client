// utility:VanillaTwoHandedAnimation
(() => {
  const L=globalThis.RigAnimationLibrary;
  const {play:__utilityPlay,arm:__utilityArm,other:__utilityOther,upper:__utilityUpper,elbow:__utilityElbow,thigh:__utilityThigh,knee:__utilityKnee,foot:__utilityFoot}=L.utility;
  class UtilityVanillaTwoHandedAnimation07e9037fAnimation extends L.ProceduralAnimation { constructor() { super('utility:VanillaTwoHandedAnimation',(c,r,t,w)=>{const a=__utilityArm(c),b=__utilityOther(a);r.rotate(__utilityUpper(a),-30*w,(a==='right'?-8:8)*w,0);r.rotate(__utilityElbow(a),-28*w,0,0);r.rotate(__utilityUpper(b),-32*w,(b==='right'?-18:18)*w,0);r.rotate(__utilityElbow(b),-48*w,0,0);return true;}); } }
  L.registerProcedural(new UtilityVanillaTwoHandedAnimation07e9037fAnimation());
})();
