// Shared player-rig math and semantic bone helpers.
(() => {
  const R=globalThis.CombatantPlayerRig=globalThis.CombatantPlayerRig||Object.create(null);
  const TAU = Math.PI * 2;
  const clamp = (v,a=0,b=1) => Math.max(a,Math.min(b,v));
  const saturate = v => clamp(v,0,1);
  const hypot2 = (x,z) => Math.sqrt(x*x+z*z);
  const expAlpha = (dt,hz) => 1-Math.exp(-Math.max(0,dt)*hz);
  const damp = (current,target,dt,hz) => current+(target-current)*expAlpha(dt,hz);
  const smooth01 = v => { v=saturate(v); return v*v*(3-2*v); };
  const wrapPi = v => { while (v > Math.PI) v -= TAU; while (v < -Math.PI) v += TAU; return v; };
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
  Object.assign(R,{TAU,clamp,saturate,hypot2,expAlpha,damp,smooth01,wrapPi,smoother01,pulse,upper,elbow,forearm,wrist,hand,thigh,knee,foot,flexElbow});
})();
