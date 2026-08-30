// utility:FreezingAnimation
(() => {
  const L=globalThis.RigAnimationLibrary;
  const {play:__utilityPlay,arm:__utilityArm,other:__utilityOther,upper:__utilityUpper,elbow:__utilityElbow,thigh:__utilityThigh,knee:__utilityKnee,foot:__utilityFoot}=L.utility;
  class UtilityFreezingAnimation3ad36c48Animation extends L.ProceduralAnimation { constructor() { super('utility:FreezingAnimation',(c,r,t,w)=>{const q=Math.sin(c.continuousSeconds*38)*w;r.rotate(playerRig.bone('chest'),0,q*1.5,q*1.2);r.rotate(playerRig.bone('left_upper_arm'),-32*w,10*w,(-54+q*4)*w);r.rotate(playerRig.bone('right_upper_arm'),-32*w,-10*w,(54-q*4)*w);return true;}); } }
  L.registerProcedural(new UtilityFreezingAnimation3ad36c48Animation());
})();
