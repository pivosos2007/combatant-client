// utility:LookAtItemAnimation
(() => {
  const L=globalThis.RigAnimationLibrary;
  const {play:__utilityPlay,arm:__utilityArm,other:__utilityOther,upper:__utilityUpper,elbow:__utilityElbow,thigh:__utilityThigh,knee:__utilityKnee,foot:__utilityFoot}=L.utility;
  class UtilityLookAtItemAnimationb8dad954Animation extends L.ProceduralAnimation { constructor() { super('utility:LookAtItemAnimation',(c,r,t,w)=>{const s=c.useArm==='left'?'left':c.useArm==='right'?'right':__utilityArm(c);const inv=s==='right'?1:-1;r.rotate(__utilityUpper(s),(-72-c.headPitch*.35)*w,(-c.headYaw*.35*inv)*w,inv*16*w);r.rotate(__utilityElbow(s),-42*w,0,0);return true;}); } }
  L.registerProcedural(new UtilityLookAtItemAnimationb8dad954Animation());
})();
