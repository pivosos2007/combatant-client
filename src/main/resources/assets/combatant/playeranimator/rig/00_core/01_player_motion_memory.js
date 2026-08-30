// PlayerMotionMemory: isolated rig component.
(() => {
  const R=globalThis.CombatantPlayerRig;
  const {TAU, clamp, saturate, hypot2, expAlpha, damp, smooth01, wrapPi, smoother01, pulse, upper, elbow, forearm, wrist, hand, thigh, knee, foot, flexElbow}=R;
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
      let waterHash=2166136261;
      const waterKey=String(c.playerId||'');
      for (let i=0;i<waterKey.length;i++) waterHash=Math.imul(waterHash^waterKey.charCodeAt(i),16777619);
      this.waterSeed=((waterHash>>>0)/4294967296)*TAU;
      this.modeWeights=Object.create(null);
      this.modeWeights[mode]=1;
      this.poseWeights=Object.create(null);
      this.previousGround=!!c.onGround;
      this.airTime=0; this.landTime=99;
      this.takeoffPhase=c.walkPhase||0;
      this.airStridePhase=c.walkPhase||0;
      this.landingPhase=c.walkPhase||0;
      this.landingWalkPhase=c.walkPhase||0;
      this.landingPhaseOffset=0;
      this.groundPhaseOffset=0;
      this.jumpLeadRight=Math.cos(c.walkPhase||0)<0;
      this.jumpQueueStrength=0;
      this.maxFallSpeed=0; this.landingFallSpeed=0; this.landingDuration=.34;
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
      // Keep phase unbounded. Water layers intentionally use non-integer harmonics and slow
      // phase modulation; wrapping to TAU would introduce a visible replay seam.
      this[phaseKey]+=TAU*this[rateKey]*this.frameDt;
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
          // The leg that was actually in front at takeoff starts the jump queue. This makes the
          // jump inherit the running step instead of replaying one canned impulse every time.
          this.jumpLeadRight=Math.cos(c.walkPhase)<0;
          this.jumpQueueStrength=smooth01(saturate((this.takeoffSpeed-.035)/.145));
          this.maxFallSpeed=Math.max(0,-this.vertical);
        } else {
          this.airTime+=dt;
          this.maxFallSpeed=Math.max(this.maxFallSpeed,-this.vertical);
          // airStridePhase intentionally does NOT loop. Air motion is a one-shot queue:
          // takeoff leg -> follow leg -> free flight -> landing preparation.
          this.airStridePhase=this.takeoffPhase;
        }
        this.landTime=99;
      } else {
        if (!this.previousGround) {
          this.landTime=0;
          this.landingPhase=this.takeoffPhase;
          this.landingWalkPhase=c.walkPhase;
          this.landingPhaseOffset=wrapPi(this.landingPhase-this.landingWalkPhase);
          this.landingFallSpeed=this.maxFallSpeed;
          this.landingDuration=clamp(.30+this.landingFallSpeed*.34+this.takeoffSpeed*.30,.30,.52);
          // Freeze the effective gait at the actual air landing stance and release that phase bridge
          // over the same interval as the landing pose. This prevents the first resumed run frame
          // from snapping to whatever vanilla walk phase happened to advance to while airborne.
          this.groundPhaseOffset=this.landingPhaseOffset;
          this.maxFallSpeed=0;
        } else {
          this.landTime+=dt;
          const bridgeDuration=Math.max(.20,this.landingDuration||.34);
          if (this.landTime<bridgeDuration) {
            const resume=smoother01(saturate(this.landTime/bridgeDuration));
            this.groundPhaseOffset=this.landingPhaseOffset*(1-resume);
          } else {
            this.groundPhaseOffset=damp(this.groundPhaseOffset,0,dt,7.5);
          }
        }
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
  R.PlayerMotionMemory=PlayerMotionMemory;
})();
