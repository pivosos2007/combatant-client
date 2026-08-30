// LocomotionLayer: isolated player animation/state layer.
(() => {
  const R=globalThis.CombatantPlayerRig;
  const {TAU, clamp, saturate, hypot2, expAlpha, damp, smooth01, wrapPi, smoother01, pulse, upper, elbow, forearm, wrist, hand, thigh, knee, foot, flexElbow}=R;
  class LocomotionLayer {
    constructor(rig,d,joints) { this.rig=rig; this.d=d; this.joints=joints; }
    apply(c,m,w,style,strength) {
      if (w<=1e-4) return;
      const amp=saturate(Math.max(c.walkAnimationSpeed/Math.max(.001,c.speedValue),m.speed/.22));
      const moving=smooth01(amp*1.15)*w*strength;
      const idle=w*strength*(1-smooth01(amp*2.2));
      const phase=c.walkPhase+m.groundPhaseOffset;
      const gait=Math.cos(phase);
      const gait90=Math.sin(phase);
      const run=c.sprinting && m.forward>-.03;
      const legAmp=(run?40:28)*moving;
      const armAim=/^(bow_and_arrow|crossbow_hold|crossbow_charge|spear|throw_trident)$/.test(c.leftArmPose)
        || /^(bow_and_arrow|crossbow_hold|crossbow_charge|spear|throw_trident)$/.test(c.rightArmPose);
      const useGate=(c.usingItem||c.vanillaUsingItem||armAim)?0.03:1;
      const armAmp=(run?36:24)*moving*(1-m.attackBlend*.96)*useGate;
      const mag=Math.max(1e-5,hypot2(m.forward,m.strafe));
      const f=m.forward/mag, s=m.strafe/mag;
      const backwards=f<-.18;
      const dir=backwards?-1:1;

      if (idle>1e-4) {
        const breath=Math.sin(c.continuousSeconds*TAU*.24);
        this.rig.rotate('chest',breath*.35*idle,0,0);
        this.rig.rotate('left_scapula',0,0,-.32*breath*idle);
        this.rig.rotate('right_scapula',0,0,.32*breath*idle);
      }
      if (moving<=1e-4) return;

      const side=saturate(Math.abs(s));
      const sideSign=Math.sign(s);
      // The visual lag is deliberately visible: loose limbs trail root acceleration while the
      // chest leans into it. This remains a secondary motion, never a replacement gait.
      const lagX=m.inertiaStrafe*.095*moving;
      const lagZ=-m.inertiaForward*.072*moving;
      this.rig.rotate('pelvis',gait90*1.6*moving,-s*7*moving,-s*2.2*moving-m.inertiaStrafe*4.0*moving);
      this.rig.rotate('spine_lower',(run?4.5:1.5)*Math.max(0,f)*moving+m.inertiaForward*5.0*moving,s*3*moving,m.inertiaStrafe*3.5*moving);
      this.rig.rotate('chest',-gait90*1.0*moving,s*6*moving+m.inertiaStrafe*5.5*moving,s*2.5*moving+m.inertiaStrafe*4.0*moving);
      this.rig.move('pelvis',0,-Math.abs(gait90)*(run?.018:.011)*moving,0);

      const rightSwing=gait*dir;
      const leftSwing=-rightSwing;
      this.rig.move('right_thigh',lagX,0,lagZ);
      this.rig.move('left_thigh',lagX,0,lagZ);
      // RIGHT +Z / LEFT -Z is outward. The old signs folded the legs toward each other.
      this.rig.rotate('right_thigh',rightSwing*legAmp,sideSign*side*5*moving,5*moving+gait90*s*10*moving+m.inertiaStrafe*7*moving);
      this.rig.rotate('left_thigh',leftSwing*legAmp,sideSign*side*5*moving,-5*moving-gait90*s*10*moving+m.inertiaStrafe*7*moving);
      this.joints.gaitLeg('right',rightSwing,moving,run);
      this.joints.gaitLeg('left',leftSwing,moving,run);

      this.rig.rotate('right_upper_arm',-rightSwing*armAmp+m.inertiaForward*8*moving,0,3*moving+m.inertiaStrafe*6*moving);
      this.rig.rotate('left_upper_arm',-leftSwing*armAmp+m.inertiaForward*8*moving,0,-3*moving+m.inertiaStrafe*6*moving);
      flexElbow(this.rig,'right',(6+Math.max(0,rightSwing)*12)*moving*(1-m.attackBlend));
      flexElbow(this.rig,'left',(6+Math.max(0,leftSwing)*12)*moving*(1-m.attackBlend));
    }
  }
  R.LocomotionLayer=LocomotionLayer;
})();
