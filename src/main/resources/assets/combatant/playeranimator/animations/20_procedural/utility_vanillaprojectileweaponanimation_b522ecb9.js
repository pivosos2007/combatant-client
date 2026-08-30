// utility:VanillaProjectileWeaponAnimation
(() => {
  const L=globalThis.RigAnimationLibrary;
  const {play:__utilityPlay,arm:__utilityArm,other:__utilityOther,upper:__utilityUpper,elbow:__utilityElbow,thigh:__utilityThigh,knee:__utilityKnee,foot:__utilityFoot}=L.utility;
  class UtilityVanillaProjectileWeaponAnimationb522ecb9Animation extends L.ProceduralAnimation { constructor() { super('utility:VanillaProjectileWeaponAnimation',(c,r,t,w)=>{if(c.useItem.includes('bow'))return __utilityPlay('motion:use_bow',c,r,c.useTimeSeconds,w);if(c.useItem.includes('crossbow'))return __utilityPlay('motion:hold_crossbow',c,r,c.useTimeSeconds,w);return __utilityPlay('motion:hold_spear',c,r,c.useTimeSeconds,w);}); } }
  L.registerProcedural(new UtilityVanillaProjectileWeaponAnimationb522ecb9Animation());
})();
