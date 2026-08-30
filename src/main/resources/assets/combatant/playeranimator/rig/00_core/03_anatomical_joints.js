// AnatomicalJoints: isolated rig component.
(() => {
  const R=globalThis.CombatantPlayerRig;
  const {TAU, clamp, saturate, hypot2, expAlpha, damp, smooth01, wrapPi, smoother01, pulse, upper, elbow, forearm, wrist, hand, thigh, knee, foot, flexElbow}=R;
  class AnatomicalJoints {
    constructor(rig) { this.rig=rig; }
    gaitLeg(side,swing,weight,run) {
      const back=saturate(-swing);
      const kneeFlex=(4+back*(run?28:22))*weight;
      this.rig.rotate(knee(side),kneeFlex,0,0);
      this.rig.rotate(foot(side),-kneeFlex*.58,0,0);
    }
    static groundCorrection(pelvisDeg,thighDeg,kneeDeg,pelvisDropPx) {
      const r=Math.PI/180;
      const upper=6*Math.cos((pelvisDeg+thighDeg)*r);
      const lower=6*Math.cos((pelvisDeg+thighDeg+kneeDeg)*r);
      return (12-(upper+lower)-pelvisDropPx)/16;
    }
    crouch(weight,moving,phase) {
      if (weight<=1e-4) return;
      const motion=saturate(moving);
      const gait=Math.sin(phase), gait90=Math.cos(phase);
      // A sneak is slow, not static. Give each leg a readable transfer phase while preserving the
      // tactical stagger and keeping the feet compensated against the full hip/knee chain.
      const stride=gait*29.0*motion;
      const pelvis=5.0;
      const rightThigh=10-stride*1.00;
      const leftThigh=-15+stride*1.04;
      const rightForward=saturate((-rightThigh+4)/28);
      const leftForward=saturate((-leftThigh+4)/28);
      const rightKnee=22+rightForward*24+Math.max(0,rightThigh)*.20;
      const leftKnee=42+leftForward*22+Math.max(0,leftThigh)*.18;
      const stepTravel=gait*.094*motion;
      const sideTravel=gait90*.022*motion;
      const rightGround=clamp(AnatomicalJoints.groundCorrection(pelvis,rightThigh,rightKnee,1.45),-.04,.04);
      const leftGround=clamp(AnatomicalJoints.groundCorrection(pelvis,leftThigh,leftKnee,1.45),-.04,.04);

      this.rig.move('right_thigh',(-.028+sideTravel)*weight,rightGround*weight,(.070+stepTravel)*weight);
      this.rig.move('left_thigh',( .020+sideTravel)*weight,leftGround*weight,(-.058-stepTravel)*weight);
      this.rig.rotate('right_thigh',rightThigh*weight,(-6-gait90*5*motion)*weight,(9+gait90*4*motion)*weight);
      this.rig.rotate('left_thigh',leftThigh*weight,(5-gait90*5*motion)*weight,(-8+gait90*4*motion)*weight);
      this.rig.rotate('right_knee',rightKnee*weight,0,0);
      this.rig.rotate('left_knee',leftKnee*weight,0,0);
      this.rig.rotate('right_foot',-(pelvis+rightThigh+rightKnee)*weight,0,-gait90*4.0*motion*weight);
      this.rig.rotate('left_foot',-(pelvis+leftThigh+leftKnee)*weight,0,-gait90*4.0*motion*weight);
    }
    combatStance(attackArm,weight,unarmed) {
      if (weight<=1e-4) return;
      const rightDominant=attackArm!=='left';
      const rear=rightDominant?'right':'left';
      const front=rightDominant?'left':'right';
      const rearSign=rear==='right'?1:-1;
      const frontSign=-rearSign;
      const rearHip=unarmed?12:8;
      const frontHip=unarmed?-9:-6;
      const rearKnee=unarmed?20:14;
      const frontKnee=unarmed?15:11;
      this.rig.move(thigh(rear),-.020*rearSign*weight,0,.050*weight);
      this.rig.move(thigh(front),-.012*frontSign*weight,0,-.040*weight);
      this.rig.rotate(thigh(rear),rearHip*weight,0,rearSign*7*weight);
      this.rig.rotate(thigh(front),frontHip*weight,0,frontSign*6*weight);
      this.rig.rotate(knee(rear),rearKnee*weight,0,0);
      this.rig.rotate(knee(front),frontKnee*weight,0,0);
      this.rig.rotate(foot(rear),-(rearHip+rearKnee)*.62*weight,0,0);
      this.rig.rotate(foot(front),-(frontHip+frontKnee)*.62*weight,0,0);
    }
    passenger(weight) {
      if (weight<=1e-4) return;
      this.rig.rotate('right_thigh',-74*weight,18*weight,8*weight);
      this.rig.rotate('left_thigh',-74*weight,-18*weight,-8*weight);
      this.rig.rotate('right_knee',68*weight,0,0);
      this.rig.rotate('left_knee',68*weight,0,0);
      this.rig.rotate('right_foot',6*weight,0,0);
      this.rig.rotate('left_foot',6*weight,0,0);
    }
  }
  R.AnatomicalJoints=AnatomicalJoints;
})();
