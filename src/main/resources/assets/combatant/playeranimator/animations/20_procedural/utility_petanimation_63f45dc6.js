// utility:PetAnimation
(() => {
  const L=globalThis.RigAnimationLibrary;
  const {play:__utilityPlay,arm:__utilityArm,other:__utilityOther,upper:__utilityUpper,elbow:__utilityElbow,thigh:__utilityThigh,knee:__utilityKnee,foot:__utilityFoot}=L.utility;
  class UtilityPetAnimation63f45dc6Animation extends L.ProceduralAnimation { constructor() { super('utility:PetAnimation',(c,r,t,w)=>{const s=__utilityArm(c),sign=s==='right'?1:-1;r.rotate(__utilityUpper(s),(-78-c.headPitch*.18)*w,sign*-20*w,sign*12*w);r.rotate(__utilityElbow(s),-58*w,0,0);return true;}); } }
  L.registerProcedural(new UtilityPetAnimation63f45dc6Animation());
})();
