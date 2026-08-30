// utility:SleepAnimation
(() => {
  const L=globalThis.RigAnimationLibrary;
  const {play:__utilityPlay,arm:__utilityArm,other:__utilityOther,upper:__utilityUpper,elbow:__utilityElbow,thigh:__utilityThigh,knee:__utilityKnee,foot:__utilityFoot}=L.utility;
  class UtilitySleepAnimation1ac09564Animation extends L.ProceduralAnimation { constructor() { super('utility:SleepAnimation',(c,r,t,w)=>{r.rotate(playerRig.bone('left_upper_arm'),0,0,-8*w);r.rotate(playerRig.bone('right_upper_arm'),0,0,8*w);r.rotate(playerRig.bone('left_knee'),-8*w,0,0);r.rotate(playerRig.bone('right_knee'),-8*w,0,0);return true;}); } }
  L.registerProcedural(new UtilitySleepAnimation1ac09564Animation());
})();
