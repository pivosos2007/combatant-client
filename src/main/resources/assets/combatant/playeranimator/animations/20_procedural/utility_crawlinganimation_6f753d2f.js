// utility:CrawlingAnimation
(() => {
  const L=globalThis.RigAnimationLibrary;
  const {play:__utilityPlay,arm:__utilityArm,other:__utilityOther,upper:__utilityUpper,elbow:__utilityElbow,thigh:__utilityThigh,knee:__utilityKnee,foot:__utilityFoot}=L.utility;
  class UtilityCrawlingAnimation6f753d2fAnimation extends L.ProceduralAnimation { constructor() { super('utility:CrawlingAnimation',(c,r,t,w)=>__utilityPlay(c.horizontalSpeed>0.015?'motion:crawling':'motion:crawl_idle',c,r,c.walkTime,w)); } }
  L.registerProcedural(new UtilityCrawlingAnimation6f753d2fAnimation());
})();
