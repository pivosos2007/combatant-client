// utility:BurningAnimation
(() => {
  const L=globalThis.RigAnimationLibrary;
  const {play:__utilityPlay,arm:__utilityArm,other:__utilityOther,upper:__utilityUpper,elbow:__utilityElbow,thigh:__utilityThigh,knee:__utilityKnee,foot:__utilityFoot}=L.utility;
  class UtilityBurningAnimation8a417b51Animation extends L.ProceduralAnimation { constructor() { super('utility:BurningAnimation',(c,r,t,w)=>{const q=Math.sin(c.continuousSeconds*20),q2=Math.cos(c.continuousSeconds*17);r.rotate(playerRig.bone('head'),0,q*6*w,q2*3*w);r.rotate(playerRig.bone('left_upper_arm'),(-72+q*10)*w,0,-42*w);r.rotate(playerRig.bone('right_upper_arm'),(-72-q*10)*w,0,42*w);return true;}); } }
  L.registerProcedural(new UtilityBurningAnimation8a417b51Animation());
})();
