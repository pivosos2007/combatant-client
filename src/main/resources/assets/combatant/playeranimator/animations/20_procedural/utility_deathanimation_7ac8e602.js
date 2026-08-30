// utility:DeathAnimation
(() => {
  const L=globalThis.RigAnimationLibrary;
  const {play:__utilityPlay,arm:__utilityArm,other:__utilityOther,upper:__utilityUpper,elbow:__utilityElbow,thigh:__utilityThigh,knee:__utilityKnee,foot:__utilityFoot}=L.utility;
  class UtilityDeathAnimation7ac8e602Animation extends L.ProceduralAnimation { constructor() { super('utility:DeathAnimation',(c,r,t,w)=>{const p=clamp(t/1.0,0,1);r.rotate(playerRig.bone('chest'),18*p*w,0,8*p*w);r.rotate(playerRig.bone('head'),10*p*w,0,-12*p*w);return true;}); } }
  L.registerProcedural(new UtilityDeathAnimation7ac8e602Animation());
})();
