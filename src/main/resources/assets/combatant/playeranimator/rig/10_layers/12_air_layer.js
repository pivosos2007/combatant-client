// AirLayer: isolated player animation/state layer.
(() => {
  const R=globalThis.CombatantPlayerRig;
  const {TAU, clamp, saturate, hypot2, expAlpha, damp, smooth01, wrapPi, smoother01, pulse, upper, elbow, forearm, wrist, hand, thigh, knee, foot, flexElbow}=R;
  class AirLayer {
    constructor(rig,d,joints) { this.rig=rig;this.d=d;this.joints=joints; }
    apply(c,m,w,strength) {
      if (w<=1e-4 || c.onGround) return;
      const k=w*strength;
      const vy=m.vertical;
      const rising=smooth01(saturate((vy+.015)/.22));
      const falling=smooth01(saturate((-vy-.02)/.30));
      const hover=saturate(1-Math.max(rising,falling));
      const sprintJump=m.takeoffSprinting && m.takeoffSpeed>.13;
      const launch=saturate(m.takeoffSpeed/.20);
      const queueStrength=m.jumpQueueStrength*(sprintJump?1:.82);
      const queueDuration=clamp(.44-launch*.13,.29,.44);
      const q=saturate(m.airTime/queueDuration);
      const leadPulse=pulse(q,0,.15,.48,.78)*queueStrength;
      const followPulse=pulse(q,.12,.31,.70,1.0)*queueStrength;
      const free=smooth01(saturate((q-.48)/.52));
      const takeoffGait=Math.cos(m.takeoffPhase);
      const takeoffCarry=Math.exp(-m.airTime*3.0)*launch;
      const leadRight=!!m.jumpLeadRight;
      const landingLeadRight=!leadRight;

      // Start exactly from the ground stride that launched us. No phase oscillator runs in air.
      let rHip=takeoffGait*(sprintJump?27:18)*takeoffCarry;
      let lHip=-takeoffGait*(sprintJump?27:18)*takeoffCarry;
      let rKnee=(6+Math.max(0,-takeoffGait)*(sprintJump?24:18))*takeoffCarry;
      let lKnee=(6+Math.max(0,takeoffGait)*(sprintJump?24:18))*takeoffCarry;

      // Explicit one-two jump queue. The front leg drives first; the other leg follows once the body
      // has left the ground. At low horizontal speed this naturally fades toward a small two-leg hop.
      const firstHip=-36, firstKnee=52, rearHip=15, rearKnee=8;
      const secondHip=-31, secondKnee=46, releasedHip=8, releasedKnee=24;
      if (leadRight) {
        rHip+=firstHip*leadPulse + releasedHip*followPulse;
        rKnee+=firstKnee*leadPulse + releasedKnee*followPulse;
        lHip+=rearHip*leadPulse + secondHip*followPulse;
        lKnee+=rearKnee*leadPulse + secondKnee*followPulse;
      } else {
        lHip+=firstHip*leadPulse + releasedHip*followPulse;
        lKnee+=firstKnee*leadPulse + releasedKnee*followPulse;
        rHip+=rearHip*leadPulse + secondHip*followPulse;
        rKnee+=rearKnee*leadPulse + secondKnee*followPulse;
      }

      // Once the queue is spent, transition into a velocity-driven free-flight pose rather than
      // replaying the same kick. Which leg remains forward still depends on the real takeoff step.
      const freeLeadHip=(-7-7*rising+3*falling)*free;
      const freeTrailHip=(5-3*rising+2*falling)*free;
      const freeLeadKnee=(22+8*rising+7*hover)*free;
      const freeTrailKnee=(29+12*rising+9*hover)*free;
      if (leadRight) {
        rHip+=freeLeadHip; rKnee+=freeLeadKnee;
        lHip+=freeTrailHip; lKnee+=freeTrailKnee;
      } else {
        lHip+=freeLeadHip; lKnee+=freeLeadKnee;
        rHip+=freeTrailHip; rKnee+=freeTrailKnee;
      }

      const landingPrep=smooth01(falling*saturate((m.airTime-.12)/.32));
      if (landingPrep>1e-4) {
        const targetRHip=landingLeadRight?-14:8;
        const targetLHip=landingLeadRight?8:-14;
        const targetRKnee=landingLeadRight?27:38;
        const targetLKnee=landingLeadRight?38:27;
        rHip+=(targetRHip-rHip)*landingPrep;
        lHip+=(targetLHip-lHip)*landingPrep;
        rKnee+=(targetRKnee-rKnee)*landingPrep;
        lKnee+=(targetLKnee-lKnee)*landingPrep;
      }

      const lateralLaunch=clamp(m.takeoffStrafe/.16,-1,1);
      const outward=(sprintJump?12:9)+hover*4+Math.abs(lateralLaunch)*3;
      const rFoot=-(rHip+rKnee)*.52-hover*2.5;
      const lFoot=-(lHip+lKnee)*.52-hover*2.5;
      const legLagX=(m.inertiaStrafe*.145-lateralLaunch*.018*Math.exp(-m.airTime*2.2))*k;
      const legLagZ=-m.inertiaForward*.105*k;
      this.rig.move('right_thigh',legLagX,0,legLagZ);
      this.rig.move('left_thigh',legLagX,0,legLagZ);
      this.rig.rotate('right_thigh',rHip*k,-4*k,(outward+m.inertiaStrafe*8)*k);
      this.rig.rotate('left_thigh',lHip*k,4*k,(-outward+m.inertiaStrafe*8)*k);
      this.rig.rotate('right_knee',rKnee*k,0,0);
      this.rig.rotate('left_knee',lKnee*k,0,0);
      this.rig.rotate('right_foot',rFoot*k,0,-2*k);
      this.rig.rotate('left_foot',lFoot*k,0,2*k);

      const takeoffImpulse=saturate((m.takeoffVertical+.02)/.34)*Math.exp(-m.airTime*4.0);
      this.rig.move('pelvis',-m.inertiaStrafe*.034*k,-.018*takeoffImpulse*k,m.inertiaForward*.026*k);
      this.rig.rotate('spine_lower',(-m.inertiaForward*5+takeoffImpulse*4)*k,0,-m.inertiaStrafe*6*k);
      this.rig.rotate('chest',((sprintJump?8:3)*takeoffImpulse+2*rising)*k-m.inertiaForward*5*k,0,-m.inertiaStrafe*8*k);

      const armGate=1-m.attackBlend;
      if (armGate>1e-4) {
        // Arms inherit the takeoff counter-swing once, then settle forward with the jump. They no
        // longer oscillate on a hidden repeating air cadence.
        const carryArm=(sprintJump?25:17)*takeoffCarry;
        const forwardFold=(sprintJump?26:18)*takeoffImpulse+11*rising+7*hover;
        this.rig.rotate('right_upper_arm',(-takeoffGait*carryArm-forwardFold)*k*armGate,0,(8+hover*3)*k*armGate);
        this.rig.rotate('left_upper_arm',(takeoffGait*carryArm-forwardFold)*k*armGate,0,-(8+hover*3)*k*armGate);
        flexElbow(this.rig,'right',(18+takeoffImpulse*22+hover*10+leadPulse*9)*k*armGate);
        flexElbow(this.rig,'left',(18+takeoffImpulse*22+hover*10+followPulse*9)*k*armGate);
      }
    }
    landing(c,m,w,strength) {
      const duration=Math.max(.20,m.landingDuration||.34);
      if (m.landTime>=duration || w<=1e-4) return;
      const u=saturate(m.landTime/duration);
      const recovery=1-smoother01(u);
      const severity=saturate(.35+(m.landingFallSpeed||0)/.42);
      const pose=recovery*w*strength;
      const impact=pose*severity;
      const rightLead=!m.jumpLeadRight;
      const rHip=rightLead?-10:6, lHip=rightLead?6:-10;
      const rKnee=rightLead?22:31, lKnee=rightLead?31:22;
      this.rig.move('pelvis',0,.038*impact,0);
      this.rig.rotate('spine_lower',2.2*impact,0,0);
      this.rig.rotate('chest',4.5*impact,0,0);
      // Leg pose is not severity-scaled. It crossfades 1:1 against resumed locomotion, while only
      // body compression depends on impact speed. This removes the landing -> run leg snap.
      this.rig.rotate('right_thigh',rHip*pose,0,6*pose);
      this.rig.rotate('left_thigh',lHip*pose,0,-6*pose);
      this.rig.rotate('right_knee',rKnee*pose,0,0);
      this.rig.rotate('left_knee',lKnee*pose,0,0);
      this.rig.rotate('right_foot',-(rHip+rKnee)*pose,0,0);
      this.rig.rotate('left_foot',-(lHip+lKnee)*pose,0,0);
    }
  }
  R.AirLayer=AirLayer;
})();
