// utility:PassengerAnimation
(() => {
  const L=globalThis.RigAnimationLibrary;
  const {play:__utilityPlay,arm:__utilityArm,other:__utilityOther,upper:__utilityUpper,elbow:__utilityElbow,thigh:__utilityThigh,knee:__utilityKnee,foot:__utilityFoot}=L.utility;
  class UtilityPassengerAnimationc3025aa0Animation extends L.ProceduralAnimation { constructor() { super('utility:PassengerAnimation',(c,r,t,w)=>{r.move(playerRig.bone('pelvis'),0,.08*w,0);for(const s of ['left','right']){const sign=s==='right'?1:-1;r.rotate(__utilityThigh(s),-72*w,sign*8*w,sign*5*w);r.rotate(__utilityKnee(s),72*w,0,0);r.rotate(__utilityFoot(s),-22*w,0,0);}return true;}); } }
  L.registerProcedural(new UtilityPassengerAnimationc3025aa0Animation());
})();
