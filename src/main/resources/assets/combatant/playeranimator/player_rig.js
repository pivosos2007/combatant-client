// Combatant default player-rig graph.
// The imported animation library remains available to addons, but the core locomotion/state graph
// is procedural and render-state driven so foreign absolute poses cannot desync the anatomical rig.
(() => {
  const TAU = Math.PI * 2;
  const clamp = (v,a=0,b=1) => Math.max(a,Math.min(b,v));
  const saturate = v => clamp(v,0,1);
  const hypot2 = (x,z) => Math.sqrt(x*x+z*z);
  const expAlpha = (dt,hz) => 1-Math.exp(-Math.max(0,dt)*hz);
  const damp = (current,target,dt,hz) => current+(target-current)*expAlpha(dt,hz);
  const smooth01 = v => { v=saturate(v); return v*v*(3-2*v); };
  const smoother01 = v => { v=saturate(v); return v*v*v*(v*(v*6-15)+10); };
  const pulse = (u,a,b,c,d) => smooth01((u-a)/Math.max(1e-5,b-a)) * (1-smooth01((u-c)/Math.max(1e-5,d-c)));
  const upper = side => side+'_upper_arm';
  const elbow = side => side+'_elbow';
  const forearm = side => side+'_forearm';
  const wrist = side => side+'_wrist';
  const hand = side => side+'_hand';
  const thigh = side => side+'_thigh';
  const knee = side => side+'_knee';
  const foot = side => side+'_foot';
  // Forearm chains extend downward in bind space; anatomical flexion is negative local X.
  // Keeping this convention in one helper prevents left/right layers from reintroducing hyperextension.
  const flexElbow = (rig,side,degrees,y=0,z=0) => rig.rotate(elbow(side),-Math.max(0,degrees),y,z);

  class PlayerMotionMemory {
    constructor(time,mode,c) {
      this.lastSeen=time;
      this.lastUpdate=time;
      this.initialized=false;
      this.frameDt=0;
      this.forward=0; this.strafe=0; this.speed=0; this.vertical=0;
      // Two velocity tracks form a physically intuitive secondary-motion lag. The root track follows
      // Minecraft velocity quickly; the loose track follows it slowly. Their difference is inertia.
      // This avoids differentiating interpolated positions, which caused saw-tooth impulses at 20 TPS.
      this.rootForward=0; this.rootStrafe=0; this.rootVertical=0;
      this.looseForward=0; this.looseStrafe=0; this.looseVertical=0;
      this.inertiaForward=0; this.inertiaStrafe=0; this.inertiaVertical=0;
      this.surfaceSwimPhase=0; this.swimPhase=0; this.sneakPhase=0;
      this.surfaceSwimRate=.62; this.swimRate=.68; this.sneakRate=.58;
      this.modeWeights=Object.create(null);
      this.modeWeights[mode]=1;
      this.poseWeights=Object.create(null);
      this.previousGround=!!c.onGround;
      this.airTime=0; this.landTime=99;
      this.takeoffPhase=c.walkPhase||0;
      this.airStridePhase=c.walkPhase||0;
      this.landingPhase=c.walkPhase||0;
      this.takeoffForward=0; this.takeoffStrafe=0; this.takeoffSpeed=0; this.takeoffVertical=0;
      this.takeoffSprinting=!!c.sprinting;
      this.attackBlend=0;
      this.lookBodyYaw=0; this.lookBodyPitch=0;
      this.lastUseArm=c.mainArm||'right'; this.lastUseAction='none'; this.lastUseItem='minecraft:air';
    }
    begin(c) {
      let dt;
      if (!this.initialized) {
        dt=clamp(c.deltaSeconds,0,.05);
        this.initialized=true;
      } else {
        dt=clamp(c.continuousSeconds-this.lastUpdate,0,.1);
      }
      this.lastUpdate=c.continuousSeconds;
      this.frameDt=dt;
      return dt;
    }
    updateVector(c,dt) {
      const vx=c.velocity.x, vy=c.velocity.y, vz=c.velocity.z;
      const yaw=c.bodyYaw*Math.PI/180;
      const sin=Math.sin(yaw), cos=Math.cos(yaw);
      const measuredForward=-sin*vx+cos*vz;
      const measuredStrafe=cos*vx+sin*vz;

      // Creative flight has abrupt velocity changes by design. Filter the root just enough to hide
      // 20-TPS stepping, then let a slower loose-body velocity keep moving through acceleration/braking.
      const flight=!!c.creativeFlying || !!c.fallFlying || !!c.vanillaFallFlying;
      const rootHz=c.creativeFlying?8.0:(flight?10.0:14.0);
      const looseHz=c.creativeFlying?4.15:(flight?3.7:(c.onGround?4.8:3.6));
      this.rootForward=damp(this.rootForward,measuredForward,dt,rootHz);
      this.rootStrafe=damp(this.rootStrafe,measuredStrafe,dt,rootHz);
      this.rootVertical=damp(this.rootVertical,vy,dt,rootHz);
      this.looseForward=damp(this.looseForward,this.rootForward,dt,looseHz);
      this.looseStrafe=damp(this.looseStrafe,this.rootStrafe,dt,looseHz);
      this.looseVertical=damp(this.looseVertical,this.rootVertical,dt,looseHz);

      const targetForward=clamp((this.looseForward-this.rootForward)/.22,-1,1);
      const targetStrafe=clamp((this.looseStrafe-this.rootStrafe)/.20,-1,1);
      const targetVertical=clamp((this.looseVertical-this.rootVertical)/.24,-1,1);
      const inertiaHz=c.creativeFlying?6.0:(flight?6.0:7.0);
      this.inertiaForward=damp(this.inertiaForward,targetForward,dt,inertiaHz);
      this.inertiaStrafe=damp(this.inertiaStrafe,targetStrafe,dt,inertiaHz);
      this.inertiaVertical=damp(this.inertiaVertical,targetVertical,dt,inertiaHz);

      this.forward=this.rootForward;
      this.strafe=this.rootStrafe;
      this.vertical=this.rootVertical;
      this.speed=damp(this.speed,hypot2(this.rootForward,this.rootStrafe),dt,10);
      return this;
    }
    advanceCycle(kind,targetHz,responseHz=3.0) {
      const rateKey=kind+'Rate', phaseKey=kind+'Phase';
      this[rateKey]=damp(this[rateKey]||targetHz,targetHz,this.frameDt,responseHz);
      this[phaseKey]=(this[phaseKey]+TAU*this[rateKey]*this.frameDt)%TAU;
      return this[phaseKey];
    }
    updateMode(mode,c,dt) {
      const modes=['ground','crouch','air','surface_swim','swim','crawl','elytra','climb','boat','horse','passenger'];
      for (const key of modes) {
        const target=key===mode?1:0;
        this.modeWeights[key]=damp(this.modeWeights[key]||0,target,dt,target?12:16);
      }

      const grounded=!!c.onGround;
      if (!grounded) {
        if (this.previousGround) {
          this.airTime=0;
          this.takeoffPhase=c.walkPhase;
          this.airStridePhase=c.walkPhase;
          this.takeoffForward=this.forward;
          this.takeoffStrafe=this.strafe;
          this.takeoffSpeed=this.speed;
          this.takeoffVertical=this.vertical;
          this.takeoffSprinting=!!c.sprinting;
        } else {
          this.airTime+=dt;
          // Carry the actual running cadence into the air instead of freezing the takeoff frame.
          // Cadence gradually loses energy, while the landing phase remains coherent with the legs.
          const launch=saturate(this.takeoffSpeed/.18);
          const cadenceHz=(this.takeoffSprinting?2.05:1.35)*(0.38+0.62*Math.exp(-this.airTime*1.25))*launch;
          this.airStridePhase=(this.airStridePhase+TAU*cadenceHz*dt)%TAU;
        }
        this.landTime=99;
      } else {
        if (!this.previousGround) {
          this.landTime=0;
          this.landingPhase=this.airStridePhase;
        } else this.landTime+=dt;
        this.airTime=0;
      }
      this.previousGround=grounded;
      const attackNow=!!c.attackActive || c.vanillaAttackTime>1e-4;
      this.attackBlend=damp(this.attackBlend,attackNow?1:0,dt,attackNow?14:7);
      this.lastSeen=c.continuousSeconds;
    }
    blend(key,target,onHz=12,offHz=9) {
      target=saturate(target);
      const current=this.poseWeights[key]||0;
      const next=damp(current,target,this.frameDt,target>current?onHz:offHz);
      this.poseWeights[key]=next;
      return next;
    }
    weight(mode) { return this.modeWeights[mode]||0; }
  }

  class AnimationDelegate {
    constructor(rig) { this.rig=rig; this.lib=globalThis.RigAnimationLibrary; }
    get(name) { return this.lib?.get(name) || null; }
    play(name,time,weight=1,options=null) {
      if (weight<=1e-4) return false;
      return this.lib?.play(name,this.rig,time,weight,options) || false;
    }
    phaseTime(name,phase) {
      const clip=this.get(name); if (!clip) return 0;
      const normalized=((phase/TAU)%1+1)%1;
      return normalized*clip.length;
    }
    phase(name,phase,weight=1,options=null) { return this.play(name,this.phaseTime(name,phase),weight,options); }
  }

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

  class LocomotionLayer {
    constructor(rig,d,joints) { this.rig=rig; this.d=d; this.joints=joints; }
    apply(c,m,w,style,strength) {
      if (w<=1e-4) return;
      const amp=saturate(Math.max(c.walkAnimationSpeed/Math.max(.001,c.speedValue),m.speed/.22));
      const moving=smooth01(amp*1.15)*w*strength;
      const idle=w*strength*(1-smooth01(amp*2.2));
      const phase=c.walkPhase;
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

  class CrouchLayer {
    constructor(rig,d,joints) { this.rig=rig;this.d=d;this.joints=joints; }
    apply(c,m,w,strength) {
      if (w<=1e-4) return;
      const k=w*strength;
      const moving=smooth01(saturate(Math.max(
        c.walkAnimationSpeed/Math.max(.001,c.speedValue)*1.75,
        m.speed/.075,
        c.horizontalSpeed/.075
      )));
      // Slow deliberate stepping, but with enough travel to actually read as locomotion.
      const sneakHz=.46+.34*moving;
      const sneakPhase=m.advanceCycle('sneak',sneakHz,2.6);
      // Tactical crouch: center of mass goes down/forward, but the spine remains nearly vertical.
      // Foot height is solved by AnatomicalJoints.crouch() instead of blindly lowering the pelvis.
      this.rig.move('pelvis',0,(1.45/16)*k,-.018*k);
      this.rig.rotate('pelvis',5*k,0,0);
      this.rig.rotate('spine_lower',-1.4*k,0,0);
      this.rig.rotate('spine_mid',-.8*k,0,0);
      this.rig.rotate('spine_upper',-.4*k,0,0);
      this.rig.rotate('chest',.6*k,m.strafe*4*k,m.inertiaStrafe*2.2*k);
      this.rig.rotate('right_upper_arm',-3*k,0,2*k);
      this.rig.rotate('left_upper_arm',-3*k,0,-2*k);
      this.joints.crouch(k,moving,sneakPhase);
    }
  }

  class AirLayer {
    constructor(rig,d,joints) { this.rig=rig;this.d=d;this.joints=joints; }
    apply(c,m,w,strength) {
      if (w<=1e-4) return;
      const k=w*strength;
      const vy=m.vertical;
      const rising=smooth01(saturate((vy+.01)/.22));
      const falling=smooth01(saturate((-vy-.015)/.30));
      const hover=saturate(1-Math.max(rising,falling));
      const sprintJump=m.takeoffSprinting && m.takeoffSpeed>.13;
      const launchSpeed=saturate(m.takeoffSpeed/.20);
      const runCarry=Math.exp(-m.airTime*(sprintJump?1.10:2.5))*launchSpeed;
      const landingPrep=smooth01(falling*saturate((m.airTime-.10)/.34));
      const gaitWeight=saturate((sprintJump?.34:.14)+runCarry*(sprintJump?.92:.58))*(1-landingPrep*.58);
      const gait=Math.cos(m.airStridePhase);
      const gait90=Math.sin(m.airStridePhase);
      const rightSwing=gait;
      const leftSwing=-gait;

      // Preserve the takeoff running cycle in the air. A sprint jump continues to scissor the legs,
      // then progressively converges into a landing-ready split instead of freezing one frame.
      let rHip=rightSwing*(sprintJump?31:22)*gaitWeight;
      let lHip=leftSwing*(sprintJump?31:22)*gaitWeight;
      let rKnee=(9+Math.max(0,-rightSwing)*(sprintJump?33:25))*gaitWeight;
      let lKnee=(9+Math.max(0,-leftSwing)*(sprintJump?33:25))*gaitWeight;

      const takeoffImpulse=saturate((m.takeoffVertical+.02)/.34)*Math.exp(-m.airTime*4.2);
      // On ascent, compress the forward leg and extend the rear one. The impulse is strongest just
      // after leaving the ground and therefore visually connects the jump to the running step.
      rHip+=(-rightSwing*7-6)*rising*takeoffImpulse;
      lHip+=(-leftSwing*7-6)*rising*takeoffImpulse;
      rKnee+=(18+Math.max(0,-rightSwing)*16)*rising*takeoffImpulse;
      lKnee+=(18+Math.max(0,-leftSwing)*16)*rising*takeoffImpulse;

      if (hover>1e-4) {
        const free=Math.sin(c.continuousSeconds*TAU*.28);
        rHip+=(-5+free*2.0)*hover;
        lHip+=(4-free*2.0)*hover;
        rKnee+=(20+free*3)*hover;
        lKnee+=(25-free*3)*hover;
      }

      if (landingPrep>1e-4) {
        const rightLead=Math.cos(m.airStridePhase)>=0;
        const targetRHip=rightLead?-13:8;
        const targetLHip=rightLead?8:-13;
        const targetRKnee=rightLead?24:36;
        const targetLKnee=rightLead?36:24;
        rHip=rHip+(targetRHip-rHip)*landingPrep;
        lHip=lHip+(targetLHip-lHip)*landingPrep;
        rKnee=rKnee+(targetRKnee-rKnee)*landingPrep;
        lKnee=lKnee+(targetLKnee-lKnee)*landingPrep;
      }

      const outward=(sprintJump?12:10)+hover*4+Math.abs(gait90)*2*gaitWeight;
      const rFoot=-(rHip+rKnee)*.50-hover*3-landingPrep*2;
      const lFoot=-(lHip+lKnee)*.50-hover*3-landingPrep*2;
      this.rig.move('right_thigh',m.inertiaStrafe*.130*k,0,-m.inertiaForward*.095*k);
      this.rig.move('left_thigh',m.inertiaStrafe*.130*k,0,-m.inertiaForward*.095*k);
      this.rig.rotate('right_thigh',rHip*k,-4*k,outward*k+m.inertiaStrafe*8*k);
      this.rig.rotate('left_thigh',lHip*k,4*k,-outward*k+m.inertiaStrafe*8*k);
      this.rig.rotate('right_knee',rKnee*k,0,0);
      this.rig.rotate('left_knee',lKnee*k,0,0);
      this.rig.rotate('right_foot',rFoot*k,0,-2*k);
      this.rig.rotate('left_foot',lFoot*k,0,2*k);

      this.rig.move('pelvis',-m.inertiaStrafe*.032*k,-.018*takeoffImpulse*k,m.inertiaForward*.024*k);
      this.rig.rotate('spine_lower',(-m.inertiaForward*5+takeoffImpulse*4)*k,0,-m.inertiaStrafe*6*k);
      this.rig.rotate('chest',((sprintJump?8:3)*takeoffImpulse+2*rising)*k-m.inertiaForward*5*k,0,-m.inertiaStrafe*8*k);

      // Arms carry the running counter-swing into takeoff and then fold forward with the jump
      // impulse. They do not drop into a static hanging pose the moment onGround becomes false.
      const armGate=1-m.attackBlend;
      if (armGate>1e-4) {
        const armCycle=(sprintJump?27:18)*gaitWeight;
        const forwardFold=(sprintJump?28:20)*takeoffImpulse+12*rising+8*hover;
        this.rig.rotate('right_upper_arm',(-rightSwing*armCycle-forwardFold)*k*armGate,0,(8+hover*3)*k*armGate);
        this.rig.rotate('left_upper_arm',(-leftSwing*armCycle-forwardFold)*k*armGate,0,-(8+hover*3)*k*armGate);
        flexElbow(this.rig,'right',(20+takeoffImpulse*24+hover*10+Math.max(0,rightSwing)*10*gaitWeight)*k*armGate);
        flexElbow(this.rig,'left',(20+takeoffImpulse*24+hover*10+Math.max(0,leftSwing)*10*gaitWeight)*k*armGate);
      }
    }
    landing(c,m,w,strength) {
      if (m.landTime>.20 || w<=1e-4) return;
      const t=1-m.landTime/.20;
      const impact=smoother01(t)*w*strength*saturate(Math.max(.3,c.fallDistance/4));
      const rightLead=Math.cos(m.landingPhase)>=0;
      this.rig.move('pelvis',0,.035*impact,0);
      this.rig.rotate('chest',4*impact,0,0);
      this.rig.rotate('right_thigh',(rightLead?-6:5)*impact,0,6*impact);
      this.rig.rotate('left_thigh',(rightLead?5:-6)*impact,0,-6*impact);
      this.rig.rotate('right_knee',(rightLead?17:25)*impact,0,0);
      this.rig.rotate('left_knee',(rightLead?25:17)*impact,0,0);
      this.rig.rotate('right_foot',-(rightLead?12:19)*impact,0,0);
      this.rig.rotate('left_foot',-(rightLead?19:12)*impact,0,0);
    }
  }

  class WaterLayer {
    constructor(rig,d,joints) { this.rig=rig;this.d=d;this.joints=joints; }
    surface(c,m,w,strength) {
      if (w<=1e-4) return;
      const k=w*strength;
      const speed=saturate(m.speed/.13);
      const p=m.advanceCycle('surfaceSwim',.58+speed*.38,2.4);
      const stroke=Math.sin(p), kick=Math.sin(p*1.08+Math.PI*.5);
      this.rig.move('pelvis',0,.025*k,0);
      this.rig.rotate('chest',-4*k,0,0);
      this.rig.rotate('right_upper_arm',(20+stroke*28)*k,0,(34+stroke*10)*k);
      this.rig.rotate('left_upper_arm',(20-stroke*28)*k,0,-(34-stroke*10)*k);
      flexElbow(this.rig,'right',(35-stroke*20)*k);
      flexElbow(this.rig,'left',(35+stroke*20)*k);
      this.rig.rotate('right_thigh',(8+kick*11)*k,-6*k,8*k);
      this.rig.rotate('left_thigh',(8-kick*11)*k,6*k,-8*k);
      this.rig.rotate('right_knee',(35-kick*17)*k,0,0);
      this.rig.rotate('left_knee',(35+kick*17)*k,0,0);
      this.rig.rotate('right_foot',(-15+kick*5)*k,0,0);
      this.rig.rotate('left_foot',(-15-kick*5)*k,0,0);
    }
    swim(c,m,w,strength) {
      if (w<=1e-4) return;
      const k=w*strength;
      const speed=saturate(Math.max(c.swimAmount,m.speed/.18));
      const p=m.advanceCycle('swim',.66+speed*.40,2.2);
      const cycle=(Math.sin(p)+1)*.5;
      const opposite=(Math.sin(p+Math.PI)+1)*.5;
      this.rig.rotate('chest',-3*k,0,0);
      this.rig.rotate('right_upper_arm',(-34-96*cycle)*k,0,8*k);
      this.rig.rotate('left_upper_arm',(-34-96*opposite)*k,0,-8*k);
      flexElbow(this.rig,'right',(18+45*cycle)*k);
      flexElbow(this.rig,'left',(18+45*opposite)*k);
      const kick=Math.sin(p*1.7);
      this.rig.rotate('right_thigh',(-5+kick*14)*k,0,5*k);
      this.rig.rotate('left_thigh',(-5-kick*14)*k,0,-5*k);
      this.rig.rotate('right_knee',(12+Math.max(0,-kick)*24)*k,0,0);
      this.rig.rotate('left_knee',(12+Math.max(0,kick)*24)*k,0,0);
      this.rig.rotate('right_foot',-9*k,0,0);
      this.rig.rotate('left_foot',-9*k,0,0);
    }
    crawl(c,m,w,strength) {
      if (w<=1e-4) return;
      const k=w*strength;
      const moving=smooth01(saturate(m.speed/.12));
      const gait=Math.cos(c.walkPhase);
      const pushR=Math.max(0,-gait), pushL=Math.max(0,gait);
      this.rig.rotate('chest',1.5*k,0,gait*2.2*moving*k);
      this.rig.move('pelvis',gait*.010*moving*k,0,0);

      this.rig.rotate('right_upper_arm',(-54-gait*33*moving)*k,0,8*k);
      this.rig.rotate('left_upper_arm',(-54+gait*33*moving)*k,0,-8*k);
      flexElbow(this.rig,'right',(42+pushR*42*moving)*k);
      flexElbow(this.rig,'left',(42+pushL*42*moving)*k);
      // Opposite knee/arm drive, with outward hip signs fixed.
      this.rig.move('right_thigh',0,0,-gait*.028*moving*k);
      this.rig.move('left_thigh',0,0,gait*.028*moving*k);
      this.rig.rotate('right_thigh',(-2-gait*26*moving)*k,-7*k,12*k);
      this.rig.rotate('left_thigh',(-2+gait*26*moving)*k,7*k,-12*k);
      this.rig.rotate('right_knee',(24+pushR*52*moving)*k,0,0);
      this.rig.rotate('left_knee',(24+pushL*52*moving)*k,0,0);
      this.rig.rotate('right_foot',(-12-pushR*24*moving)*k,0,0);
      this.rig.rotate('left_foot',(-12-pushL*24*moving)*k,0,0);
      this.rig.rotate('right_toe',(7+pushR*12*moving)*k,0,0);
      this.rig.rotate('left_toe',(7+pushL*12*moving)*k,0,0);
    }
  }

  class ElytraLayer {
    constructor(rig,d) { this.rig=rig;this.d=d; }
    apply(c,m,w,strength) {
      if (w<=1e-4) return;
      const k=w*strength;
      const speed=saturate(c.horizontalSpeed/.9);
      const sink=saturate(-c.velocity.y/.45);
      const lagX=m.inertiaStrafe*.140*k, lagZ=-m.inertiaForward*.110*k;
      this.rig.rotate('chest',(-2-speed*4+sink*2)*k,-m.inertiaStrafe*6*k,-m.inertiaStrafe*10*k);
      this.rig.rotate('spine_lower',-m.inertiaForward*5*k,0,-m.inertiaStrafe*6*k);
      this.rig.move('right_thigh',lagX,0,lagZ);
      this.rig.move('left_thigh',lagX,0,lagZ);
      this.rig.rotate('right_upper_arm',(5+sink*4)*k,-7*k,16*k);
      this.rig.rotate('left_upper_arm',(5+sink*4)*k,7*k,-16*k);
      flexElbow(this.rig,'right',10*k);
      flexElbow(this.rig,'left',10*k);
      this.rig.rotate('right_thigh',(-5-sink*4)*k,-2*k,7*k+m.inertiaStrafe*8*k);
      this.rig.rotate('left_thigh',(-5-sink*4)*k,2*k,-7*k+m.inertiaStrafe*8*k);
      this.rig.rotate('right_knee',(8+sink*6)*k,0,0);
      this.rig.rotate('left_knee',(8+sink*6)*k,0,0);
    }
  }

  class InertiaLayer {
    constructor(rig) { this.rig=rig; }
    apply(c,m,mode,strength) {
      if (strength<=1e-4 || mode==='surface_swim' || mode==='swim' || mode==='boat' || mode==='horse' || mode==='passenger') return;
      const flight=!!c.creativeFlying || mode==='elytra' || mode==='air';
      const k=clamp(strength,0,1.35)*(c.creativeFlying?1.35:(flight?1.12:.82));
      const f=m.inertiaForward*k, s=m.inertiaStrafe*k, v=m.inertiaVertical*k;
      if (Math.abs(f)+Math.abs(s)+Math.abs(v)<1e-4) return;

      // Root responds first; distal chains carry the delayed velocity farther. This makes braking
      // readable in legs/arms instead of expressing all inertia as one chest tilt.
      this.rig.move('pelvis',s*.018,-v*.012,-f*.014);
      this.rig.rotate('pelvis',-f*2.5,0,s*3.4);
      this.rig.rotate('spine_lower',-f*4.0,0,s*5.0);
      this.rig.rotate('spine_mid',-f*3.2,0,s*4.4);
      this.rig.rotate('chest',-f*3.0,s*1.8,s*5.8);

      const armX=-f*(flight?10.5:7.0);
      const legX=-f*(flight?12.5:7.5);
      const verticalFlex=Math.abs(v)*(flight?7.0:3.5);
      this.rig.move('right_upper_arm',s*.030,-v*.010,-f*.022);
      this.rig.move('left_upper_arm',s*.030,-v*.010,-f*.022);
      this.rig.rotate('right_upper_arm',armX,0,s*8.5);
      this.rig.rotate('left_upper_arm',armX,0,s*8.5);
      this.rig.rotate('right_forearm',-f*4.5,s*3.5,s*3.0);
      this.rig.rotate('left_forearm',-f*4.5,s*3.5,s*3.0);

      this.rig.move('right_thigh',s*.052,-v*.014,-f*.040);
      this.rig.move('left_thigh',s*.052,-v*.014,-f*.040);
      this.rig.rotate('right_thigh',legX,0,s*10.0);
      this.rig.rotate('left_thigh',legX,0,s*10.0);
      this.rig.rotate('right_knee',verticalFlex,0,0);
      this.rig.rotate('left_knee',verticalFlex,0,0);
      this.rig.rotate('right_foot',-legX*.38-verticalFlex*.45,0,-s*4.0);
      this.rig.rotate('left_foot',-legX*.38-verticalFlex*.45,0,-s*4.0);
    }
  }

  class VehicleLayer {
    constructor(rig,d,joints) { this.rig=rig;this.d=d;this.joints=joints; }
    boat(c,w,strength) {
      if (w<=1e-4) return;
      const k=w*strength;
      this.joints.passenger(k);
      this.rig.rotate('chest',3*k,0,0);
      const paddle=(side,active,time) => {
        if (!active) {
          this.rig.rotate(upper(side),-8*k,0,(side==='right'?20:-20)*k);
          flexElbow(this.rig,side,18*k);
          return;
        }
        // getRowingTime() is already continuous/interpolated. Use it directly rather than a
        // separate low-frequency clock.
        const p=time;
        const pull=(Math.sin(p)+1)*.5;
        const sign=side==='right'?1:-1;
        this.rig.rotate(upper(side),(-58+92*pull)*k,sign*(-8+10*pull)*k,sign*(34-14*pull)*k);
        flexElbow(this.rig,side,(48-30*pull)*k);
      };
      paddle('left',c.boatLeft,c.boatLeftTime);
      paddle('right',c.boatRight,c.boatRightTime);
    }
    horse(c,w,strength) {
      if (w<=1e-4) return;
      const k=w*strength;
      this.joints.passenger(k);
      const gait=Math.sin(c.walkPhase)*saturate(c.walkAnimationSpeed/Math.max(.001,c.speedValue));
      this.rig.rotate('chest',2*k,0,0);
      this.rig.rotate('right_upper_arm',(-12-gait*8)*k,0,8*k);
      this.rig.rotate('left_upper_arm',(-12+gait*8)*k,0,-8*k);
    }
    passenger(c,w,strength) {
      if (w<=1e-4) return;
      const k=w*strength;
      this.joints.passenger(k);
      this.rig.rotate('right_upper_arm',-12*k,0,5*k);
      this.rig.rotate('left_upper_arm',-12*k,0,-5*k);
    }
  }

  class ClimbLayer {
    constructor(rig,d,joints) { this.rig=rig;this.d=d;this.joints=joints; }
    apply(c,m,w,strength) {
      if (w<=1e-4) return;
      const k=w*strength;
      const active=Math.abs(m.vertical)>.006||m.speed>.006;
      const p=c.continuousSeconds*TAU*(active?.8:.2);
      const g=Math.sin(p);
      this.rig.rotate('right_upper_arm',(-112+g*42)*k,0,8*k);
      this.rig.rotate('left_upper_arm',(-112-g*42)*k,0,-8*k);
      flexElbow(this.rig,'right',(36-g*18)*k);
      flexElbow(this.rig,'left',(36+g*18)*k);
      this.rig.rotate('right_thigh',(12-g*25)*k,0,-4*k);
      this.rig.rotate('left_thigh',(12+g*25)*k,0,4*k);
      this.rig.rotate('right_knee',(35+g*20)*k,0,0);
      this.rig.rotate('left_knee',(35-g*20)*k,0,0);
    }
  }

  class LookLayer {
    constructor(rig) { this.rig=rig; }
    apply(c,m,strength) {
      const yaw=clamp(c.headYaw,-95,95);
      const pitch=clamp(c.headPitch,-90,90);
      const aiming=(c.usingItem||c.vanillaUsingItem) &&
        /^(bow|crossbow|spear)$/.test(c.useAction) ||
        c.leftArmPose==='bow_and_arrow' || c.rightArmPose==='bow_and_arrow' ||
        c.leftArmPose==='spear' || c.rightArmPose==='spear' ||
        c.leftArmPose==='throw_trident' || c.rightArmPose==='throw_trident';

      // The neck keeps the normal vanilla range; only the excess is progressively transferred to
      // the torso. Aiming starts that transfer earlier, but never snaps the whole body to the head.
      const threshold=aiming?12:38;
      const excess=Math.max(0,Math.abs(yaw)-threshold);
      const yawTarget=Math.sign(yaw)*Math.min(aiming?48:34,excess*(aiming?.72:.58));
      const pitchExcess=Math.max(0,Math.abs(pitch)-(aiming?42:62));
      const pitchTarget=Math.sign(pitch)*Math.min(aiming?9:6,pitchExcess*(aiming?.28:.20));

      m.lookBodyYaw=damp(m.lookBodyYaw,yawTarget,m.frameDt,aiming?5.6:3.8);
      m.lookBodyPitch=damp(m.lookBodyPitch,pitchTarget,m.frameDt,aiming?4.0:3.0);
      const y=m.lookBodyYaw*clamp(strength,0,1.25);
      const x=m.lookBodyPitch*clamp(strength,0,1.25);

      if (Math.abs(y)>1e-4) {
        // Curved spine rather than one rigid body yaw.
        this.rig.rotate('spine_lower',0,y*.18,0);
        this.rig.rotate('spine_mid',0,y*.29,0);
        this.rig.rotate('chest',0,y*.53,0);
        // Remove exactly the transferred local yaw from the anatomical neck/head chain.
        this.rig.rotate('neck_lower',0,-y*.12,0);
        this.rig.rotate('neck_upper',0,-y*.18,0);
        this.rig.rotate('head',0,-y*.70,0);
      }
      if (Math.abs(x)>1e-4) {
        // Keep vertical look mostly in the head so looking up/down does not visibly lower the skull.
        this.rig.rotate('spine_lower',x*.14,0,0);
        this.rig.rotate('spine_mid',x*.22,0,0);
        this.rig.rotate('chest',x*.64,0,0);
        this.rig.rotate('neck_lower',-x*.10,0,0);
        this.rig.rotate('neck_upper',-x*.16,0,0);
        this.rig.rotate('head',-x*.74,0,0);
      }
    }
  }

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
        const biteWave=Math.sin(usePhase*(drinkMode?.36:.48));
        const bite=(biteWave*.5+.5);

        // The torso meets the hand slightly, but the target itself is attached to HEAD. Therefore
        // looking up/down/sideways moves the mouth target first and the arm IK follows it exactly.
        this.rig.rotate('spine_upper',(drinkMode?2.0:3.0)*k,-sign*(drinkMode?1.4:2.2)*k,0);
        this.rig.rotate('chest',(drinkMode?3.6:4.8)*k,-sign*(drinkMode?3.0:4.4)*k,sign*1.0*k);
        this.rig.rotate('neck_lower',(drinkMode?.5:1.0)*k,sign*.7*k,0);
        this.rig.rotate('head',(drinkMode?1.0:1.8)*k,sign*1.0*k,0);
        this.rig.rotate(useArm+'_scapula',0,-sign*3.5*k,sign*2.5*k);
        this.rig.rotate(useArm+'_clavicle',0,-sign*6.5*k,sign*4.5*k);

        // Mouth point in head-local model pixels: slightly toward the active side, near the lower
        // front face. A tiny bite/drink depth pulse moves the hand/item into the mouth, not the elbow.
        const mouthX=-sign*(drinkMode?.45:.70)/16;
        const mouthY=(drinkMode?-2.0:-2.35)/16;
        const mouthZ=(-4.05-(drinkMode?.25:.42)*bite)/16;
        this.rig.reachHand(useArm,'head',mouthX,mouthY,mouthZ,
          useArm==='right'?-1:1,.62,.10,k);
        this.rig.rotate(wrist(useArm),(drinkMode?-31:-13)*k,sign*(drinkMode?3:5)*k,-sign*(drinkMode?5:8)*k);
        this.rig.move(useArm+'_item_control',0,-(drinkMode?.004:.006)*k,-(drinkMode?.010:.014)*k);
      }

      if (active && item.includes('map')) {
        this.d.play('utility:MapHoldingAnimation',c.continuousSeconds,weight);
        return true;
      }
      return Math.max(spear,shield,bow,crossbow,eat,drink)>1e-3;
    }
  }

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

  class CombatLayer {
    constructor(rig,d,joints) { this.rig=rig;this.d=d;this.joints=joints; }
    apply(c,m,weight) {
      if (weight<=1e-4) return false;
      const arm=(c.attackArm==='left'||c.attackArm==='right')?c.attackArm:c.mainArm;
      const other=arm==='right'?'left':'right';
      const sign=arm==='right'?1:-1;
      const held=arm===c.mainArm?c.mainItem:c.offItem;
      const unarmed=!held||held==='minecraft:air';
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

  class PlayerRigMotionGraph {
    constructor(rig) {
      this.rig=rig;
      this.delegate=new AnimationDelegate(rig);
      this.joints=new AnatomicalJoints(rig);
      this.locomotion=new LocomotionLayer(rig,this.delegate,this.joints);
      this.crouch=new CrouchLayer(rig,this.delegate,this.joints);
      this.air=new AirLayer(rig,this.delegate,this.joints);
      this.water=new WaterLayer(rig,this.delegate,this.joints);
      this.elytra=new ElytraLayer(rig,this.delegate);
      this.inertia=new InertiaLayer(rig);
      this.vehicle=new VehicleLayer(rig,this.delegate,this.joints);
      this.climb=new ClimbLayer(rig,this.delegate,this.joints);
      this.look=new LookLayer(rig);
      this.items=new ItemUseLayer(rig,this.delegate);
      this.held=new HeldPoseLayer(rig,this.delegate);
      this.combat=new CombatLayer(rig,this.delegate,this.joints);
      this.memory=new Map();
      this.lastPrune=0;
    }
    mode(c) {
      if (c.passenger||c.vanillaPassenger) {
        if (c.vehicleType.includes('boat')||c.vehicleType.includes('raft')) return 'boat';
        if (/(horse|donkey|mule|camel|pig|strider)/.test(c.vehicleType)) return 'horse';
        return 'passenger';
      }
      if (c.fallFlying||c.vanillaFallFlying) return 'elytra';
      if (c.crawling) return 'crawl';
      if (c.inWater||c.vanillaInWater) {
        if (c.swimming||c.vanillaSwimming||c.swimAmount>.06) return 'swim';
        return 'surface_swim';
      }
      if (c.climbing) return 'climb';
      if (c.crouching||c.vanillaCrouching) return 'crouch';
      if (!c.onGround) return 'air';
      return 'ground';
    }
    state(c,mode) {
      let state=this.memory.get(c.playerId);
      if (!state) { state=new PlayerMotionMemory(c.continuousSeconds,mode,c); this.memory.set(c.playerId,state); }
      const dt=state.begin(c);
      state.updateVector(c,dt); state.updateMode(mode,c,dt);
      if (c.continuousSeconds-this.lastPrune>5) {
        this.lastPrune=c.continuousSeconds;
        for (const [id,value] of this.memory) if (c.continuousSeconds-value.lastSeen>20) this.memory.delete(id);
      }
      return state;
    }
    apply(c) {
      const strength=clamp(c.strength,0,2);
      if (strength<=1e-4) return;
      const mode=this.mode(c), m=this.state(c,mode);
      const style=c.style||'Hybrid';

      this.locomotion.apply(c,m,m.weight('ground'),style,strength);
      this.crouch.apply(c,m,m.weight('crouch'),strength);
      this.air.apply(c,m,m.weight('air'),strength);
      this.water.surface(c,m,m.weight('surface_swim'),strength);
      this.water.swim(c,m,m.weight('swim'),strength);
      this.water.crawl(c,m,m.weight('crawl'),strength);
      this.elytra.apply(c,m,m.weight('elytra'),strength);
      this.climb.apply(c,m,m.weight('climb'),strength);
      this.vehicle.boat(c,m.weight('boat'),strength);
      this.vehicle.horse(c,m.weight('horse'),strength);
      this.vehicle.passenger(c,m.weight('passenger'),strength);
      this.air.landing(c,m,m.weight('ground'),strength);
      this.inertia.apply(c,m,mode,strength);
      this.look.apply(c,m,strength);

      const overlay=clamp(strength,0,1.35);
      const using=this.items.apply(c,m,overlay);
      this.held.apply(c,m,overlay*.72,!!c.attackActive || c.vanillaAttackTime>1e-4);
      const useBlock=saturate(Math.max(m.poseWeights.use_spear||0,m.poseWeights.use_shield||0,m.poseWeights.use_bow||0,m.poseWeights.use_crossbow||0,m.poseWeights.use_eat||0,m.poseWeights.use_drink||0));
      this.combat.apply(c,m,overlay*(1-useBlock*.96));
    }
  }

  globalThis.CombatantPlayerAnimations=Object.freeze({
    PlayerRigMotionGraph, PlayerMotionMemory, AnimationDelegate, AnatomicalJoints,
    LocomotionLayer, CrouchLayer, AirLayer, WaterLayer, ElytraLayer, InertiaLayer, VehicleLayer,
    ClimbLayer, LookLayer, ItemUseLayer, HeldPoseLayer, CombatLayer
  });

  const graph=new PlayerRigMotionGraph(playerRig);
  playerRig.onPose(context=>graph.apply(context));
})();
