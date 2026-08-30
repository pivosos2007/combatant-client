// utility:HugAnimation
(() => {
  const L=globalThis.RigAnimationLibrary;
  const {play:__utilityPlay,arm:__utilityArm,other:__utilityOther,upper:__utilityUpper,elbow:__utilityElbow,thigh:__utilityThigh,knee:__utilityKnee,foot:__utilityFoot}=L.utility;
  class UtilityHugAnimation7f724cb9Animation extends L.ProceduralAnimation { constructor() { super('utility:HugAnimation',(c,r,t,w)=>{r.rotate(playerRig.bone('left_upper_arm'),-72*w,-18*w,-20*w);r.rotate(playerRig.bone('right_upper_arm'),-72*w,18*w,20*w);r.rotate(playerRig.bone('left_elbow'),-46*w,0,0);r.rotate(playerRig.bone('right_elbow'),-46*w,0,0);return true;}); } }
  L.registerProcedural(new UtilityHugAnimation7f724cb9Animation());
})();
