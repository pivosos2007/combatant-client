// utility:MapHoldingAnimation
(() => {
  const L=globalThis.RigAnimationLibrary;
  const {play:__utilityPlay,arm:__utilityArm,other:__utilityOther,upper:__utilityUpper,elbow:__utilityElbow,thigh:__utilityThigh,knee:__utilityKnee,foot:__utilityFoot}=L.utility;
  class UtilityMapHoldingAnimation98314093Animation extends L.ProceduralAnimation { constructor() { super('utility:MapHoldingAnimation',(c,r,t,w)=>{const pitch=clamp(c.headPitch,-70,70);r.rotate(playerRig.bone('left_upper_arm'),(-55-pitch*.2)*w,-18*w,-8*w);r.rotate(playerRig.bone('right_upper_arm'),(-55-pitch*.2)*w,18*w,8*w);r.rotate(playerRig.bone('left_elbow'),-46*w,8*w,0);r.rotate(playerRig.bone('right_elbow'),-46*w,-8*w,0);return true;}); } }
  L.registerProcedural(new UtilityMapHoldingAnimation98314093Animation());
})();
