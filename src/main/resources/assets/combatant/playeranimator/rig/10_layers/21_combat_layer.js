// CombatLayer: isolated player animation/state layer.
(() => {
  const R=globalThis.CombatantPlayerRig;
  const {TAU, clamp, saturate, hypot2, expAlpha, damp, smooth01, wrapPi, smoother01, pulse, upper, elbow, forearm, wrist, hand, thigh, knee, foot, flexElbow}=R;
  class CombatLayer {
    constructor(rig,d,joints) { this.rig=rig;this.d=d;this.joints=joints; }
    apply(c,m,weight) {
      if (weight<=1e-4) return false;
      const arm=(c.attackArm==='left'||c.attackArm==='right')?c.attackArm:c.mainArm;
      const other=arm==='right'?'left':'right';
      const sign=arm==='right'?1:-1;
      const held=arm===c.mainArm?c.mainItem:c.offItem;
      const unarmed=!held||held==='minecraft:air';
      const sword=!unarmed&&String(held).includes('sword');
      // Swing progression follows the weapon's vanilla attack-strength delay, not the short visual
      // attackTime pulse. Sword/axe recovery therefore occupies the same cooldown the gameplay uses.
      const active=!!c.attackActive || c.vanillaAttackTime>1e-4;
      if (!active) return false;
      const duration=Math.max(.05,c.attackDuration||.62);
      const u=!!c.attackActive ? saturate(c.attackTime/duration) : saturate(c.vanillaAttackTime);
      // Respect the complete gameplay cooldown even for fast weapons. The forward commitment has
      // an absolute minimum duration, so fists do not collapse into a ~50 ms twitch; heavier items
      // still commit sharply while spending most of their cooldown in controlled recovery.
      const commitSeconds=clamp(duration*.24,.105,.17);
      const commitFrac=clamp(commitSeconds/duration,.16,.42);
      const commit=smoother01(saturate(u/commitFrac));
      const recover=1-smooth01(saturate((u-commitFrac)/Math.max(1e-4,1-commitFrac)));
      const envelope=commit*recover*weight;
      if (envelope<=1e-4) return false;
      const strike=commit;
      const alternate=(c.swingIndex&1)!==0?-1:1;
      const direction=alternate*(2*commit-1);

      this.joints.combatStance(arm,envelope,unarmed);

      // Whole-body kinetic chain: stance -> pelvis -> curved spine -> shoulder -> hand.
      this.rig.move('pelvis',0,(unarmed?.020:.012)*envelope,-(unarmed?.045:.032)*envelope);
      this.rig.rotate('pelvis',(unarmed?7:5)*envelope,-sign*direction*(unarmed?11:10)*envelope,-sign*direction*2.5*envelope);
      this.rig.rotate('spine_lower',(unarmed?11:9)*envelope,-sign*direction*(unarmed?14:13)*envelope,-sign*direction*3.5*envelope);
      this.rig.rotate('spine_mid',(unarmed?9:8)*envelope,-sign*direction*(unarmed?12:11)*envelope,-sign*direction*3*envelope);
      this.rig.rotate('chest',(unarmed?16:14)*envelope,-sign*direction*(unarmed?28:31)*envelope,-sign*direction*7*envelope);
      this.rig.rotate(arm+'_clavicle',0,-sign*7*envelope,sign*5*envelope);
      this.rig.rotate(arm+'_scapula',0,-sign*6*envelope,sign*4*envelope);

      if (unarmed) {
        // Boxing-style punch: rear/dominant side rotates through, non-striking arm guards the head.
        const wind=1-strike;
        this.rig.rotate(upper(arm),(-52-48*strike)*envelope,-sign*(8+12*strike)*envelope,-sign*(8+8*wind)*envelope);
        flexElbow(this.rig,arm,(72-58*strike)*envelope,0,sign*3*wind*envelope);
        this.rig.rotate(forearm(arm),0,sign*5*strike*envelope,0);
        this.rig.rotate(wrist(arm),-4*strike*envelope,0,0);

        this.rig.rotate(upper(other),-58*envelope,sign*10*envelope,(other==='right'?-18:18)*envelope);
        flexElbow(this.rig,other,78*envelope);
        this.rig.rotate(forearm(other),0,(other==='right'?5:-5)*envelope,0);
      } else {
        const stab=c.swingAnimationType==='stab';
        if (stab) {
          this.rig.rotate(upper(arm),(-58-48*strike)*envelope,-sign*(10+18*strike)*envelope,-sign*6*envelope);
          flexElbow(this.rig,arm,(34-24*strike)*envelope);
        } else if (sword) {
          // One-handed sword slash stays outside the head volume. The old path lifted the arm past
          // overhead while sweeping yaw through the center line, so one variant intersected the skull.
          // Torso rotation now carries the cross-body motion while the arm remains on its own side.
          const wind=1-strike;
          const variant=(c.swingIndex&1)!==0?1:-1;
          const armX=-58-40*strike;
          const armY=-sign*(28*wind-12*strike+variant*5*strike);
          const armZ=sign*(28-12*strike);
          this.rig.rotate(upper(arm),armX*envelope,armY*envelope,armZ*envelope);
          flexElbow(this.rig,arm,(48-27*strike)*envelope,0,sign*2*variant*envelope);
          this.rig.rotate(forearm(arm),0,sign*(6+8*strike)*envelope,sign*3*variant*envelope);
          this.rig.rotate(wrist(arm),-4*strike*envelope,sign*(5+7*strike)*envelope,sign*4*variant*envelope);
          this.rig.rotate(arm+'_clavicle',0,-sign*(4+5*strike)*envelope,sign*(7-3*strike)*envelope);
        } else {
          this.rig.rotate(upper(arm),(-46-76*strike)*envelope,-sign*(14+38*direction)*envelope,sign*(10+10*strike)*envelope);
          flexElbow(this.rig,arm,(42-28*strike)*envelope,0,sign*6*direction*envelope);
          this.rig.rotate(forearm(arm),0,sign*12*direction*envelope,sign*4*strike*envelope);
          this.rig.rotate(wrist(arm),-6*strike*envelope,sign*14*direction*envelope,sign*7*strike*envelope);
        }
        this.rig.rotate(upper(other),-20*envelope,0,(other==='right'?10:-10)*envelope);
        flexElbow(this.rig,other,20*envelope);
      }
      return true;
    }
  }
  R.CombatLayer=CombatLayer;
})();
