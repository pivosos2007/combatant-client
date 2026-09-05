// ItemUseLayer: isolated player animation/state layer.
(() => {
  const R=globalThis.CombatantPlayerRig;
  const {TAU, clamp, saturate, hypot2, expAlpha, damp, smooth01, wrapPi, smoother01, pulse, upper, elbow, forearm, wrist, hand, thigh, knee, foot, flexElbow}=R;
  class ItemUseLayer {
    constructor(rig,d) { this.rig=rig;this.d=d; }
    armPose(c,side) { return side==='right'?c.rightArmPose:c.leftArmPose; }
    apply(c,m,weight) {
      const active=(c.usingItem||c.vanillaUsingItem) && weight>1e-4;
      const item=active?c.useItem:m.lastUseItem;
      if (active) {
        m.lastUseArm=(c.useArm==='left'||c.useArm==='right')?c.useArm:c.mainArm;
        m.lastUseAction=c.useAction;
        m.lastUseItem=c.useItem;
      }
      const useArm=active?((c.useArm==='left'||c.useArm==='right')?c.useArm:c.mainArm):m.lastUseArm;
      const other=useArm==='right'?'left':'right';
      const sign=useArm==='right'?1:-1;
      const pose=this.armPose(c,useArm);
      const otherPose=this.armPose(c,other);
      const action=active?c.useAction:'none';

      const spearTarget=active&&(pose==='spear'||pose==='throw_trident'||action==='spear'||item.includes('trident')||item.includes('spear'));
      const shieldTarget=active&&(pose==='block'||action==='block'||item.includes('shield'));
      const bowTarget=active&&(pose==='bow_and_arrow'||action==='bow'||(item.includes('bow')&&!item.includes('crossbow')));
      const crossTarget=active&&(pose==='crossbow_charge'||pose==='crossbow_hold'||action==='crossbow'||item.includes('crossbow'));
      const eatTarget=active&&action==='eat';
      const drinkTarget=active&&action==='drink';

      // One independent smoothed channel per action. No extra global "ramp" is multiplied on top,
      // avoiding the old double-envelope and the sudden pose switch at release.
      const spear=m.blend('use_spear',spearTarget?1:0,3.2,3.8)*weight;
      const shield=m.blend('use_shield',shieldTarget?1:0,5.0,4.5)*weight;
      const bow=m.blend('use_bow',bowTarget?1:0,6.0,5.0)*weight;
      const crossbow=m.blend('use_crossbow',crossTarget?1:0,7.5,5.0)*weight;
      const eat=m.blend('use_eat',eatTarget?1:0,7.0,5.0)*weight;
      const drink=m.blend('use_drink',drinkTarget?1:0,7.0,5.0)*weight;

      const aimYaw=clamp(c.headYaw-m.lookBodyYaw,-70,70);
      const aimPitch=clamp(c.headPitch-m.lookBodyPitch,-85,85);

      if (bow>1e-4) {
        const k=bow;
        // Vanilla BOW_AND_ARROW basis, expressed in degrees. Negative X is forward.
        const drawY=aimYaw-sign*5.73;
        const supportY=aimYaw+sign*28.65;
        const x=-90+aimPitch;
        this.rig.rotate(upper(useArm),x*k,drawY*k,0);
        this.rig.rotate(upper(other),x*k,supportY*k,0);
        // Keep the item-bearing arm straight so the hand socket follows the same line as vanilla.
        this.rig.setRotation(elbow(useArm),0,0,0);
        this.rig.setRotation(elbow(other),0,0,0);
        this.rig.rotate(wrist(useArm),0,0,0);
      }

      if (shield>1e-4) {
        const k=shield;
        // Vanilla poseBlockingArm: stable in relation to the head, not a guessed sideways elbow pose.
        const x=-54+clamp(aimPitch,-80,25);
        const y=-sign*30+clamp(aimYaw,-30,30);
        this.rig.rotate(upper(useArm),x*k,y*k,0);
        this.rig.setRotation(elbow(useArm),0,0,0);
      }

      if (spear>1e-4) {
        const k=spear;
        const trident=pose==='throw_trident'||item.includes('trident');
        let x;
        if (trident) {
          x=-170+clamp(aimPitch,-45,45)*.25;
        } else {
          // Minecraft 26.2 SpearAnimations: -90 + headPitch + 0.8rad, then clamped.
          x=clamp(-44.16+aimPitch-((c.fallFlying||c.vanillaFallFlying||c.swimAmount>.05)?55:0),-120,30);
        }
        const y=clamp(aimYaw-sign*5.73,-60,60);
        this.rig.rotate(upper(useArm),x*k,y*k,-sign*2*k);
        this.rig.setRotation(elbow(useArm),0,0,0);
        this.rig.rotate(wrist(useArm),-4*k,0,0);
      }

      if (crossbow>1e-4) {
        const k=crossbow;
        const charging=pose==='crossbow_charge';
        this.rig.setRotation(elbow(useArm),0,0,0);
        this.rig.setRotation(forearm(useArm),0,0,0);
        this.rig.setRotation(wrist(useArm),0,0,0);
        if (charging) {
          const p=smoother01(saturate(c.vanillaUseTicks/Math.max(1,c.maxCrossbowChargeDuration)));
          const mainY=-sign*45.84;
          this.rig.rotate(upper(useArm),-55.62*k,mainY*k,0);
          this.rig.rotate(useArm+'_clavicle',0,-sign*2.8*k,sign*1.5*k);
          this.rig.rotate(other+'_clavicle',0,sign*(4.0+2.0*p)*k,-sign*2.0*k);

          // The draw/support hand now reaches an actual point on the item-bearing hand chain. The
          // target moves with the crossbow arm, so charging cannot desync into a free-floating hand.
          const targetX=(other==='left'?1:-1)*(1.15-.45*p)/16;
          const targetY=(.30+.60*p)/16;
          const targetZ=(-.45+.70*p)/16;
          this.rig.reachHand(other,useArm+'_item_control',targetX,targetY,targetZ,
            other==='right'?-1:1,.45,.18,k);
          this.rig.rotate(wrist(other),(-6-12*p)*k,0,(other==='right'?-5:5)*k);
        } else {
          // Charged/aiming: item arm is the sight line and the support hand remains physically on
          // the crossbow body rather than merely posing somewhere near the opposite shoulder.
          this.rig.rotate(upper(useArm),(-90+aimPitch)*k,(aimYaw-sign*9)*k,-sign*2*k);
          this.rig.setRotation(elbow(useArm),0,0,0);
          this.rig.setRotation(forearm(useArm),0,0,0);
          const targetX=(other==='left'?1:-1)*1.25/16;
          this.rig.reachHand(other,useArm+'_item_control',targetX,.55/16,-.2/16,
            other==='right'?-1:1,.35,.18,k);
          this.rig.rotate(wrist(other),-8*k,0,(other==='right'?-4:4)*k);
        }
      }

      if (eat>1e-4 || drink>1e-4) {
        const drinkMode=drink>eat;
        const k=Math.max(eat,drink);
        const usePhase=Math.max(0,c.vanillaUseTicks);
        const consumeWave=Math.sin(usePhase*(drinkMode?.34:.44));
        const consume=(consumeWave*.5+.5);

        // Torso/head move toward the action, but the mouth itself is a HEAD-local target. Both hand
        // IK and the held-item socket are solved after these rotations, so head yaw/pitch cannot
        // leave the food behind or make the grip chase a world-space point.
        this.rig.rotate('spine_upper',(drinkMode?1.4:2.1)*k,-sign*(drinkMode?1.0:1.7)*k,0);
        this.rig.rotate('chest',(drinkMode?2.8:3.8)*k,-sign*(drinkMode?2.2:3.2)*k,sign*.7*k);
        this.rig.rotate('neck_lower',(drinkMode?.35:.7)*k,sign*.45*k,0);
        this.rig.rotate('head',(drinkMode?.7:1.2)*k,sign*.55*k,0);
        this.rig.rotate(useArm+'_scapula',0,-sign*1.8*k,sign*1.2*k);
        this.rig.rotate(useArm+'_clavicle',0,-sign*3.2*k,sign*1.8*k);

        // The reach solver owns the complete distal arm orientation during consumption. Clear
        // locomotion/inertia twist accumulated before IK; otherwise strafing can leave the hand at
        // the mouth while corkscrewing the forearm around the target.
        this.rig.setRotation(useArm+'_forearm',0,0,0);
        this.rig.setRotation(useArm+'_forearm_twist',0,0,0);
        this.rig.setRotation(useArm+'_wrist',0,0,0);

        // Aim the visible hand a little behind/below the actual mouth. The item gets its own solved
        // mouth socket below; wrist twist is not used to compensate for reach.
        const handX=-sign*(drinkMode?1.05:1.20)/16;
        const handY=(drinkMode?-1.25:-1.55)/16;
        const handZ=(drinkMode?-2.55:-2.75)/16;
        this.rig.reachHand(useArm,'head',handX,handY,handZ,
          useArm==='right'?-1:1,.58,.08,k);

        // Place the *rendered item socket* in a stable head-local mouth frame. This is independent
        // of the elbow/wrist solution, so looking around cannot roll the food or rotate it away from
        // the lips. Only a very small depth pulse remains for the bite/sip motion.
        const mouthX=-sign*(drinkMode?.18:.28)/16;
        const mouthY=(drinkMode?-2.00:-2.20)/16;
        const mouthZ=(-4.12-(drinkMode?.15:.24)*consume)/16;
        const itemPitch=drinkMode?-64:-18;
        const itemYaw=sign*(drinkMode?6:10);
        const itemRoll=-sign*(drinkMode?7:16);
        this.rig.placeItem(useArm,'head',mouthX,mouthY,mouthZ,itemPitch,itemYaw,itemRoll,k);
      }

      if (active && item.includes('map')) {
        this.d.play('utility:MapHoldingAnimation',c.continuousSeconds,weight);
        return true;
      }
      return Math.max(spear,shield,bow,crossbow,eat,drink)>1e-3;
    }
  }
  R.ItemUseLayer=ItemUseLayer;
})();
