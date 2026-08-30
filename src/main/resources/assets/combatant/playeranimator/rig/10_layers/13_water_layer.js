// WaterLayer: continuous procedural swimming/treading/crawling without baked loop seams.
(() => {
  const R=globalThis.CombatantPlayerRig;
  const {TAU, saturate, smooth01, smoother01, flexElbow}=R;
  const fract=v=>v-Math.floor(v);
  const phase01=p=>fract(p/TAU);
  const mix=(a,b,t)=>a+(b-a)*t;
  const periodic=(q,points)=>{
    q=fract(q);
    let prev=points[points.length-1], next=points[0], span=(next[0]+1)-prev[0], local=(q+1-prev[0])/span;
    for(let i=0;i<points.length-1;i++){
      if(q>=points[i][0]&&q<points[i+1][0]){prev=points[i];next=points[i+1];span=next[0]-prev[0];local=(q-prev[0])/span;break;}
    }
    return mix(prev[1],next[1],smoother01(local));
  };
  const drift=(phase,seed)=>
    Math.sin(phase*.173+seed)*.065+
    Math.sin(phase*.071+seed*1.73)*.035+
    Math.sin(phase*.037-seed*.61)*.018;

  const swimArm=(rig,side,q,k,variation)=>{
    const sign=side==='right'?1:-1;
    // Closed, asymmetric power/recovery curve. The end value equals the start value, while the
    // low-frequency variation keeps consecutive strokes from reading as a replayed clip.
    const upperX=periodic(q,[[0,-132],[.16,-150],[.34,-78],[.56,18],[.74,-42],[1,-132]])+variation*7;
    const upperZ=sign*(periodic(q,[[0,11],[.20,17],[.43,29],[.62,15],[.82,7],[1,11]])+variation*4);
    const upperY=sign*(periodic(q,[[0,2],[.28,-8],[.53,-18],[.75,8],[1,2]])+variation*3);
    const elbow=periodic(q,[[0,18],[.18,12],[.36,58],[.57,76],[.76,42],[1,18]])+Math.abs(variation)*8;
    rig.rotate(side+'_upper_arm',upperX*k,upperY*k,upperZ*k);
    flexElbow(rig,side,Math.max(0,elbow)*k);
    rig.rotate(side+'_wrist',(-5+periodic(q,[[0,-5],[.45,12],[.7,-9],[1,-5]])*.35)*k,0,sign*variation*3*k);
  };

  const applySwim=(c,rig,phase,k,seed=0)=>{
    const speed=saturate(Math.max(c.swimAmount||0,(c.horizontalSpeed||0)/.18));
    const slow=drift(phase,seed);
    const qR=phase01(phase+slow*1.9);
    const qL=fract(qR+.5+Math.sin(phase*.113+seed)*.018);
    rig.rotate('chest',(-3.5-slow*4.0)*k,0,Math.sin(phase*.23+seed)*2.2*k);
    rig.rotate('spine_mid',(-1.5+Math.sin(phase*.31+seed)*1.8)*k,0,Math.sin(phase*.19-seed)*1.5*k);
    swimArm(rig,'right',qR,k,slow);
    swimArm(rig,'left',qL,k,-slow*.82);

    // Flutter kick is intentionally not phase-locked 1:1 to the arm stroke. This removes the
    // obvious whole-body repetition while keeping a stable swimming cadence.
    const kickPhase=phase*1.73+Math.sin(phase*.091+seed)*.24;
    const kick=Math.sin(kickPhase), kick2=Math.sin(kickPhase+Math.PI+.16*Math.sin(phase*.067+seed));
    const kickAmp=9+15*speed;
    rig.rotate('right_thigh',(-4+kick*kickAmp)*k,0,(4+slow*2)*k);
    rig.rotate('left_thigh',(-4+kick2*kickAmp)*k,0,(-4-slow*2)*k);
    rig.rotate('right_knee',(10+Math.max(0,-kick)*30)*k,0,0);
    rig.rotate('left_knee',(10+Math.max(0,-kick2)*30)*k,0,0);
    rig.rotate('right_foot',(-8+kick*5)*k,0,0);
    rig.rotate('left_foot',(-8+kick2*5)*k,0,0);
    rig.move('pelvis',Math.sin(phase*.41+seed)*.007*k,Math.sin(phase*.5+seed)*.006*k,0);
  };

  const treadArm=(rig,side,q,k,variation)=>{
    const sign=side==='right'?1:-1;
    const upperX=periodic(q,[[0,20],[.28,-8],[.56,32],[.78,8],[1,20]])+variation*5;
    const upperZ=sign*(periodic(q,[[0,33],[.28,46],[.55,24],[.78,39],[1,33]])+variation*5);
    const upperY=sign*periodic(q,[[0,4],[.35,-9],[.7,8],[1,4]]);
    const elbow=periodic(q,[[0,34],[.25,58],[.55,29],[.8,48],[1,34]]);
    rig.rotate(side+'_upper_arm',upperX*k,upperY*k,upperZ*k);
    flexElbow(rig,side,elbow*k);
  };

  const applySurface=(c,rig,phase,k,seed=0)=>{
    const speed=saturate((c.horizontalSpeed||0)/.13);
    const slow=drift(phase,seed);
    const qR=phase01(phase+slow*1.4), qL=fract(qR+.47+Math.sin(phase*.129+seed)*.025);
    rig.move('pelvis',Math.sin(phase*.53+seed)*.008*k,.02*k,0);
    rig.rotate('chest',(-3+Math.sin(phase*.37+seed)*1.8)*k,0,Math.sin(phase*.29+seed)*2*k);
    treadArm(rig,'right',qR,k,slow);
    treadArm(rig,'left',qL,k,-slow);
    const kickPhase=phase*1.21+Math.sin(phase*.083+seed)*.21;
    const kr=Math.sin(kickPhase), kl=Math.sin(kickPhase+Math.PI*.91);
    rig.rotate('right_thigh',(7+kr*(10+8*speed))*k,-5*k,(8+slow*2)*k);
    rig.rotate('left_thigh',(7+kl*(10+8*speed))*k,5*k,(-8-slow*2)*k);
    rig.rotate('right_knee',(31+Math.max(0,-kr)*23)*k,0,0);
    rig.rotate('left_knee',(31+Math.max(0,-kl)*23)*k,0,0);
    rig.rotate('right_foot',(-14+kr*5)*k,0,0);
    rig.rotate('left_foot',(-14+kl*5)*k,0,0);
  };

  R.WaterPoseMath=Object.freeze({applySwim,applySurface});

  class WaterLayer {
    constructor(rig,d,joints) { this.rig=rig;this.d=d;this.joints=joints; }
    surface(c,m,w,strength) {
      if (w<=1e-4) return;
      const speed=saturate(m.speed/.13);
      const modulation=1+Math.sin(m.surfaceSwimPhase*.061+m.waterSeed)*.07;
      const p=m.advanceCycle('surfaceSwim',(.54+speed*.34)*modulation,1.8);
      applySurface(c,this.rig,p,w*strength,m.waterSeed);
    }
    swim(c,m,w,strength) {
      if (w<=1e-4) return;
      const speed=saturate(Math.max(c.swimAmount,m.speed/.18));
      const modulation=1+Math.sin(m.swimPhase*.053+m.waterSeed)*.075;
      const p=m.advanceCycle('swim',(.61+speed*.37)*modulation,1.7);
      applySwim(c,this.rig,p,w*strength,m.waterSeed);
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
  R.WaterLayer=WaterLayer;
})();
