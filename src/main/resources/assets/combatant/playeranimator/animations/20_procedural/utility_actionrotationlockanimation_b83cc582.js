/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

// utility:ActionRotationLockAnimation
(() => {
  const L=globalThis.RigAnimationLibrary;
  const {play:__utilityPlay,arm:__utilityArm,other:__utilityOther,upper:__utilityUpper,elbow:__utilityElbow,thigh:__utilityThigh,knee:__utilityKnee,foot:__utilityFoot}=L.utility;
  class UtilityActionRotationLockAnimationb83cc582Animation extends L.ProceduralAnimation { constructor() { super('utility:ActionRotationLockAnimation',(c,r,t,w)=>{r.rotate(playerRig.bone('chest'),0,-c.headYaw*.12*w,0);return true;}); } }
  L.registerProcedural(new UtilityActionRotationLockAnimationb83cc582Animation());
})();
