// utility:NarutoRunningAnimation
(() => {
  const L=globalThis.RigAnimationLibrary;
  const {play:__utilityPlay,arm:__utilityArm,other:__utilityOther,upper:__utilityUpper,elbow:__utilityElbow,thigh:__utilityThigh,knee:__utilityKnee,foot:__utilityFoot}=L.utility;
  class UtilityNarutoRunningAnimation7629e9edAnimation extends L.ProceduralAnimation { constructor() { super('utility:NarutoRunningAnimation',(c,r,t,w)=>{r.rotate(playerRig.bone('left_upper_arm'),52*w,0,-8*w);r.rotate(playerRig.bone('right_upper_arm'),52*w,0,8*w);r.rotate(playerRig.bone('left_elbow'),-8*w,0,0);r.rotate(playerRig.bone('right_elbow'),-8*w,0,0);return true;}); } }
  L.registerProcedural(new UtilityNarutoRunningAnimation7629e9edAnimation());
})();
