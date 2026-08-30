// utility:RiptideAnimation
(() => {
  const L=globalThis.RigAnimationLibrary;
  const {play:__utilityPlay,arm:__utilityArm,other:__utilityOther,upper:__utilityUpper,elbow:__utilityElbow,thigh:__utilityThigh,knee:__utilityKnee,foot:__utilityFoot}=L.utility;
  class UtilityRiptideAnimationd6da3cedAnimation extends L.ProceduralAnimation { constructor() { super('utility:RiptideAnimation',(c,r,t,w)=>{for(const s of ['left','right']){const sign=s==='right'?1:-1;r.rotate(__utilityUpper(s),-164*w,sign*5*w,sign*3*w);r.rotate(__utilityElbow(s),-8*w,0,0);}r.rotate(playerRig.bone('chest'),-12*w,0,0);return true;}); } }
  L.registerProcedural(new UtilityRiptideAnimationd6da3cedAnimation());
})();
