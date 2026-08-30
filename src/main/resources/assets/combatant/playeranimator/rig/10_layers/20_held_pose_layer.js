// HeldPoseLayer: isolated player animation/state layer.
(() => {
  const R=globalThis.CombatantPlayerRig;
  const {TAU, clamp, saturate, hypot2, expAlpha, damp, smooth01, wrapPi, smoother01, pulse, upper, elbow, forearm, wrist, hand, thigh, knee, foot, flexElbow}=R;
  class HeldPoseLayer {
    constructor(rig,d) { this.rig=rig;this.d=d; }
    apply(c,m,weight,suppressed=false) {
      const main=c.mainItem, off=c.offItem;
      if (weight<=1e-4 || suppressed) return;
      const mainArm=c.mainArm;
      const offArm=mainArm==='right'?'left':'right';
      const spearArm=main.includes('trident')||main.includes('spear')?mainArm:(off.includes('trident')||off.includes('spear')?offArm:null);
      const shieldArm=main.includes('shield')?mainArm:(off.includes('shield')?offArm:null);
      const useSuppression=saturate(Math.max(
        m.poseWeights.use_spear||0,m.poseWeights.use_shield||0,m.poseWeights.use_bow||0,
        m.poseWeights.use_crossbow||0,m.poseWeights.use_eat||0,m.poseWeights.use_drink||0
      ));
      const heldGate=1-useSuppression;
      const spear=m.blend('hold_spear',!!spearArm?1:0,4.5,4.5)*weight*heldGate;
      const shield=m.blend('hold_shield',!!shieldArm?1:0,5,5)*weight*heldGate;
      const crossArm=main.includes('crossbow')?mainArm:(off.includes('crossbow')?offArm:null);
      const chargedArm=c.rightArmPose==='crossbow_hold'?'right':(c.leftArmPose==='crossbow_hold'?'left':null);
      const cross=m.blend('hold_crossbow',!!crossArm?1:0,5,5)*weight*heldGate;

      if (main.includes('map')||off.includes('map')) {
        this.d.play('utility:MapHoldingAnimation',c.continuousSeconds,weight*heldGate);
        return;
      }
      if (spearArm && spear>1e-4) {
        const sign=spearArm==='right'?1:-1, k=spear;
        this.rig.rotate(upper(spearArm),-34*k,-sign*6*k,-sign*3*k);
        flexElbow(this.rig,spearArm,4*k);
      }
      if (shieldArm && shield>1e-4) {
        const sign=shieldArm==='right'?1:-1, k=shield;
        this.rig.rotate(upper(shieldArm),-16*k,-sign*3*k,-sign*10*k);
        flexElbow(this.rig,shieldArm,16*k);
      }
      if (crossArm && cross>1e-4) {
        const aimArm=chargedArm||crossArm;
        const support=aimArm==='right'?'left':'right';
        const sign=aimArm==='right'?1:-1, k=cross;
        if (chargedArm) {
          const aimYaw=clamp(c.headYaw-m.lookBodyYaw,-65,65);
          const aimPitch=clamp(c.headPitch-m.lookBodyPitch,-70,55);
          this.rig.rotate(upper(aimArm),(-90+aimPitch)*k,(aimYaw-sign*8)*k,-sign*2*k);
          this.rig.setRotation(elbow(aimArm),0,0,0);
          this.rig.setRotation(forearm(aimArm),0,0,0);
          this.rig.setRotation(wrist(aimArm),0,0,0);
          this.rig.reachHand(support,aimArm+'_item_control',
            (support==='left'?1:-1)*1.25/16,.55/16,-.2/16,
            support==='right'?-1:1,.35,.18,k);
          this.rig.rotate(wrist(support),-8*k,0,(support==='right'?-4:4)*k);
        } else {
          this.rig.rotate(upper(crossArm),-28*k,-sign*7*k,-sign*4*k);
          flexElbow(this.rig,crossArm,12*k);
        }
      }
    }
  }
  R.HeldPoseLayer=HeldPoseLayer;
})();
