// utility:BoatAnimation
(() => {
  const L=globalThis.RigAnimationLibrary;
  const {play:__utilityPlay,arm:__utilityArm,other:__utilityOther,upper:__utilityUpper,elbow:__utilityElbow,thigh:__utilityThigh,knee:__utilityKnee,foot:__utilityFoot}=L.utility;
  class UtilityBoatAnimation50003d5fAnimation extends L.ProceduralAnimation { constructor() { super('utility:BoatAnimation',(c,r,t,w)=>{__utilityPlay('motion:boat_idle',c,r,t,w);if(c.boatLeft&&c.boatRight)__utilityPlay('motion:boat_forward',c,r,(c.boatLeftTime+c.boatRightTime)/(Math.PI*2),w);else if(c.boatLeft)__utilityPlay('motion:boat_left_paddle',c,r,c.boatLeftTime/Math.PI,w);else if(c.boatRight)__utilityPlay('motion:boat_right_paddle',c,r,c.boatRightTime/Math.PI,w);return true;}); } }
  L.registerProcedural(new UtilityBoatAnimation50003d5fAnimation());
})();
