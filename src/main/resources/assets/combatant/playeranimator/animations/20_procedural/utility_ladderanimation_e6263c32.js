// utility:LadderAnimation
(() => {
  const L=globalThis.RigAnimationLibrary;
  const {play:__utilityPlay,arm:__utilityArm,other:__utilityOther,upper:__utilityUpper,elbow:__utilityElbow,thigh:__utilityThigh,knee:__utilityKnee,foot:__utilityFoot}=L.utility;
  class UtilityLadderAnimatione6263c32Animation extends L.ProceduralAnimation { constructor() { super('utility:LadderAnimation',(c,r,t,w)=>__utilityPlay(c.horizontalSpeed>.01||Math.abs(c.velocity.y)>.01?'motion:climbing':'motion:climbing_idle_mit',c,r,c.continuousSeconds*1.4,w)); } }
  L.registerProcedural(new UtilityLadderAnimatione6263c32Animation());
})();
