// PlayerRigMotionGraph: state/layer composition only.
(() => {
  const R=globalThis.CombatantPlayerRig;
  const {TAU, clamp, saturate, hypot2, expAlpha, damp, smooth01, wrapPi, smoother01, pulse, upper, elbow, forearm, wrist, hand, thigh, knee, foot, flexElbow, PlayerMotionMemory, AnimationDelegate, AnatomicalJoints, LocomotionLayer, CrouchLayer, AirLayer, WaterLayer, ElytraLayer, InertiaLayer, VehicleLayer, ClimbLayer, LookLayer, ItemUseLayer, HeldPoseLayer, CombatLayer}=R;
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

      const landingGate=m.landTime<(m.landingDuration||.34)
        ? smoother01(saturate(m.landTime/Math.max(.001,m.landingDuration||.34))) : 1;
      this.locomotion.apply(c,m,m.weight('ground')*landingGate,style,strength);
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
      this.air.landing(c,m,c.onGround?1:0,strength);
      this.inertia.apply(c,m,mode,strength);
      this.look.apply(c,m,strength);

      const overlay=clamp(strength,0,1.35);
      const using=this.items.apply(c,m,overlay);
      this.held.apply(c,m,overlay*.72,!!c.attackActive || c.vanillaAttackTime>1e-4);
      const useBlock=saturate(Math.max(m.poseWeights.use_spear||0,m.poseWeights.use_shield||0,m.poseWeights.use_bow||0,m.poseWeights.use_crossbow||0,m.poseWeights.use_eat||0,m.poseWeights.use_drink||0));
      this.combat.apply(c,m,overlay*(1-useBlock*.96));
    }
  }
  R.PlayerRigMotionGraph=PlayerRigMotionGraph;
})();
