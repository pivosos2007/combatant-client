// Combatant's centralized anatomical animation graph. Resource packs may append layers through
// combatant:playeranimator/player_rig_addon.js without replacing this base graph.
playerRig.onPose((context, rig) => {
  const strength = rig.clamp(context.strength, 0, 2);
  if (strength <= 0) return;

  const smoothStyle = context.style === 'Smooth';
  const combatStyle = context.style === 'Combat';
  const locomotionWeight = strength * (combatStyle ? 0.78 : 1.0);
  const combatWeight = strength * (smoothStyle ? 0.78 : 1.0);
  const speed = Math.hypot(context.velocity.x, context.velocity.z);
  const movement = rig.clamp(speed * 5.4, 0, 1);
  const running = context.sprinting && movement > 0.12;
  const cycle = context.age * (running ? 0.92 : 0.67);
  const stride = Math.sin(cycle);
  const opposite = Math.sin(cycle + Math.PI);
  const bounce = Math.abs(Math.cos(cycle));
  const breath = Math.sin(context.age * 0.095);
  const yawRadians = context.yaw * Math.PI / 180;
  const localForward = (-Math.sin(yawRadians) * context.velocity.x + Math.cos(yawRadians) * context.velocity.z);
  const backwards = localForward < -0.015;
  const direction = backwards ? -1 : 1;

  const arm = side => side + '_upper_arm';
  const elbow = side => side + '_elbow';
  const forearm = side => side + '_forearm';
  const hand = side => side + '_hand';
  const itemControl = side => side + '_item_control';
  const thigh = side => side + '_thigh';
  const knee = side => side + '_knee';
  const foot = side => side + '_foot';
  const fingers = side => side + '_fingers';
  const dominant = context.mainArm === 'left' ? 'left' : 'right';
  const support = dominant === 'right' ? 'left' : 'right';
  const used = context.useArm === 'left' ? 'left' : context.useArm === 'right' ? 'right' : dominant;
  const usedSupport = used === 'right' ? 'left' : 'right';

  function rotate(sideBone, x, y = 0, z = 0, weight = 1) {
    rig.rotate(sideBone, x * weight, y * weight, z * weight);
  }

  function poseHead(weight = 1) {
    rotate('spine_upper', context.pitch * 0.08, 0, 0, weight);
    rotate('neck_lower', context.pitch * 0.18, 0, 0, weight);
    rotate('neck_upper', context.pitch * 0.14, 0, 0, weight);
    rotate('head', context.pitch * 0.60, 0, 0, weight);
  }

  function idle() {
    const w = locomotionWeight;
    rig.move('pelvis', 0, breath * 0.006 * w, 0);
    rotate('pelvis', 0, breath * 0.35, breath * 0.25, w);
    rotate('spine_lower', breath * 0.45, -breath * 0.2, 0, w);
    rotate('chest', -breath * 0.65, breath * 0.28, 0, w);
    rotate('left_clavicle', 0, 0, -1.2 - breath * 0.35, w);
    rotate('right_clavicle', 0, 0, 1.2 + breath * 0.35, w);
    rotate('left_elbow', -1.6 + breath * 0.5, 0, 0, w);
    rotate('right_elbow', -1.6 - breath * 0.5, 0, 0, w);
  }

  function walk() {
    const w = movement * locomotionWeight;
    const leg = (running ? 48 : 34) * direction;
    const armSwing = (running ? 38 : 25) * direction;
    rig.move('pelvis', 0, -bounce * (running ? 0.055 : 0.025) * w, 0);
    rotate('pelvis', running ? 7 : 2, -stride * (running ? 3.5 : 2.3), -stride * 2.0, w);
    rotate('spine_lower', running ? -2 : 0, stride * 2.0, stride * 1.1, w);
    rotate('chest', running ? -8 : 0, stride * 3.2, -stride * 1.4, w);
    rotate(thigh('left'), stride * leg, 0, -1.5, w);
    rotate(thigh('right'), opposite * leg, 0, 1.5, w);
    rotate(knee('left'), Math.max(0, -stride * direction) * (running ? -55 : -34), 0, 0, w);
    rotate(knee('right'), Math.max(0, -opposite * direction) * (running ? -55 : -34), 0, 0, w);
    rotate(foot('left'), -stride * 9 * direction, 0, 0, w);
    rotate(foot('right'), -opposite * 9 * direction, 0, 0, w);
    rotate(arm('left'), -stride * armSwing, 0, -3, w);
    rotate(arm('right'), -opposite * armSwing, 0, 3, w);
    rotate(elbow('left'), -Math.max(0, stride) * (running ? 34 : 12) - (running ? 12 : 2), 0, 0, w);
    rotate(elbow('right'), -Math.max(0, opposite) * (running ? 34 : 12) - (running ? 12 : 2), 0, 0, w);
  }

  function crouch() {
    const w = locomotionWeight;
    rig.move('pelvis', 0, 0.16 * w, 0.09 * w);
    rotate('pelvis', 19, 0, 0, w);
    rotate('spine_lower', -5, 0, 0, w);
    rotate('spine_upper', -8, 0, 0, w);
    rotate(thigh('left'), -22 + stride * movement * 14, 0, -2, w);
    rotate(thigh('right'), -22 - stride * movement * 14, 0, 2, w);
    rotate(knee('left'), -38, 0, 0, w);
    rotate(knee('right'), -38, 0, 0, w);
    rotate(foot('left'), 18, 0, 0, w);
    rotate(foot('right'), 18, 0, 0, w);
  }

  function climb() {
    const w = locomotionWeight;
    const climbCycle = Math.sin(context.age * 0.72);
    rotate('chest', -5, climbCycle * 3, 0, w);
    rotate(arm('left'), -148 + climbCycle * 36, 0, -8, w);
    rotate(arm('right'), -148 - climbCycle * 36, 0, 8, w);
    rotate(elbow('left'), -38 - climbCycle * 22, 0, 0, w);
    rotate(elbow('right'), -38 + climbCycle * 22, 0, 0, w);
    rotate(thigh('left'), -25 - climbCycle * 31, 0, 0, w);
    rotate(thigh('right'), -25 + climbCycle * 31, 0, 0, w);
    rotate(knee('left'), -50 + climbCycle * 24, 0, 0, w);
    rotate(knee('right'), -50 - climbCycle * 24, 0, 0, w);
    rotate(fingers('left'), -25, 0, 0, w);
    rotate(fingers('right'), -25, 0, 0, w);
  }

  function crawl() {
    const w = locomotionWeight;
    const crawlCycle = Math.sin(context.age * 0.60);
    rotate('motion', 90, 0, 0, w);
    rig.move('motion', 0, 0.12 * w, 0.10 * w);
    rotate('chest', -8, crawlCycle * 5, 0, w);
    rotate(arm('left'), -112 + crawlCycle * 30, 0, -12, w);
    rotate(arm('right'), -112 - crawlCycle * 30, 0, 12, w);
    rotate(elbow('left'), -45 - crawlCycle * 28, 0, 0, w);
    rotate(elbow('right'), -45 + crawlCycle * 28, 0, 0, w);
    rotate(thigh('left'), 12 - crawlCycle * 18, 0, -10, w);
    rotate(thigh('right'), 12 + crawlCycle * 18, 0, 10, w);
    rotate(knee('left'), -30 + crawlCycle * 20, 0, 0, w);
    rotate(knee('right'), -30 - crawlCycle * 20, 0, 0, w);
  }

  function swim() {
    const w = locomotionWeight;
    const swimCycle = Math.sin(context.age * 0.42);
    rotate('motion', 90, 0, 0, w);
    rotate('chest', -8, 0, 0, w);
    rotate(arm('left'), -92 + swimCycle * 68, -18, -12, w);
    rotate(arm('right'), -92 - swimCycle * 68, 18, 12, w);
    rotate(elbow('left'), -34 - Math.max(0, swimCycle) * 55, 0, 0, w);
    rotate(elbow('right'), -34 - Math.max(0, -swimCycle) * 55, 0, 0, w);
    rotate(thigh('left'), swimCycle * 22, 0, 0, w);
    rotate(thigh('right'), -swimCycle * 22, 0, 0, w);
    rotate(knee('left'), -18 - Math.max(0, swimCycle) * 25, 0, 0, w);
    rotate(knee('right'), -18 - Math.max(0, -swimCycle) * 25, 0, 0, w);
  }

  function glideOrFall() {
    const w = locomotionWeight;
    if (context.fallFlying) {
      rotate('motion', 90, 0, 0, w);
      rotate('chest', -11, 0, 0, w);
      rotate(arm('left'), -22, 0, -63, w);
      rotate(arm('right'), -22, 0, 63, w);
      rotate(elbow('left'), -10, 0, 0, w);
      rotate(elbow('right'), -10, 0, 0, w);
      rotate(thigh('left'), 8, 0, -4, w);
      rotate(thigh('right'), 8, 0, 4, w);
      rotate(knee('left'), -12, 0, 0, w);
      rotate(knee('right'), -12, 0, 0, w);
    } else if (!context.onGround && context.fallDistance > 0.3) {
      const fall = rig.clamp(context.fallDistance / 4, 0, 1) * w;
      rotate('chest', -5, 0, 0, fall);
      rotate(arm('left'), -15, 0, -38, fall);
      rotate(arm('right'), -15, 0, 38, fall);
      rotate(thigh('left'), -8, 0, -8, fall);
      rotate(thigh('right'), 12, 0, 8, fall);
      rotate(knee('left'), -20, 0, 0, fall);
      rotate(knee('right'), -8, 0, 0, fall);
    }
  }

  function passenger() {
    const w = locomotionWeight;
    rig.move('pelvis', 0, 0.10 * w, 0);
    rotate('pelvis', -4, 0, 0, w);
    rotate(thigh('left'), -72, 0, -8, w);
    rotate(thigh('right'), -72, 0, 8, w);
    rotate(knee('left'), -64, 0, 0, w);
    rotate(knee('right'), -64, 0, 0, w);
    rotate(foot('left'), 22, 0, 0, w);
    rotate(foot('right'), 22, 0, 0, w);
  }

  function holdMap() {
    const w = combatWeight;
    rotate(arm('left'), -72, -22, -9, w);
    rotate(arm('right'), -72, 22, 9, w);
    rotate(elbow('left'), -34, 8, 0, w);
    rotate(elbow('right'), -34, -8, 0, w);
    rotate(hand('left'), 0, -12, 0, w);
    rotate(hand('right'), 0, 12, 0, w);
  }

  function useItem() {
    if (!context.usingItem) return false;
    const w = combatWeight;
    const action = context.useAction;
    const item = context.useItem;
    const draw = rig.smoothstep(rig.clamp(context.useTicks / 20, 0, 1));

    if (action === 'bow' || (item.includes('bow') && !item.includes('crossbow'))) {
      rotate('chest', -4, used === 'right' ? -12 : 12, 0, w);
      rotate(arm(used), -88, used === 'right' ? -8 : 8, used === 'right' ? 5 : -5, w);
      rotate(arm(usedSupport), -84, usedSupport === 'right' ? -35 : 35, usedSupport === 'right' ? 5 : -5, w);
      rotate(elbow(used), -8 - draw * 32, 0, 0, w);
      rotate(elbow(usedSupport), -12, 0, 0, w);
      rig.move(itemControl(used), 0, 0, draw * 0.12 * w);
      return true;
    }
    if (action === 'crossbow' || item.includes('crossbow')) {
      rotate('chest', -8, used === 'right' ? -8 : 8, 0, w);
      rotate(arm(used), -76, used === 'right' ? -20 : 20, used === 'right' ? 6 : -6, w);
      rotate(arm(usedSupport), -72, usedSupport === 'right' ? -29 : 29, usedSupport === 'right' ? 5 : -5, w);
      rotate(elbow(used), -42, 0, 0, w);
      rotate(elbow(usedSupport), -54 + draw * 18, 0, 0, w);
      return true;
    }
    if (action === 'spear' || item.includes('trident') || item.includes('spear')) {
      rotate('chest', -10, used === 'right' ? -17 : 17, 0, w);
      rotate(arm(used), -142, used === 'right' ? 12 : -12, used === 'right' ? 9 : -9, w);
      rotate(elbow(used), -38, 0, 0, w);
      rotate(itemControl(used), 0, 0, used === 'right' ? -10 : 10, w);
      return true;
    }
    if (action === 'block' || item.includes('shield')) {
      rotate('chest', -5, used === 'right' ? -8 : 8, 0, w);
      rotate(arm(used), -55, used === 'right' ? -28 : 28, used === 'right' ? 16 : -16, w);
      rotate(elbow(used), -58, used === 'right' ? 8 : -8, 0, w);
      rotate(itemControl(used), 0, used === 'right' ? -18 : 18, 0, w);
      return true;
    }
    if (action === 'eat' || action === 'drink') {
      const sip = Math.sin(context.useTicks * 1.9) * 3;
      rotate('head', -5 + sip * 0.25, 0, used === 'right' ? -3 : 3, w);
      rotate(arm(used), -104 + sip, used === 'right' ? -18 : 18, used === 'right' ? 9 : -9, w);
      rotate(elbow(used), -66 + sip * 0.7, 0, 0, w);
      rotate(hand(used), -12, 0, used === 'right' ? -8 : 8, w);
      return true;
    }
    if (action === 'spyglass' || item.includes('spyglass')) {
      rotate(arm(used), -112, used === 'right' ? -21 : 21, used === 'right' ? 8 : -8, w);
      rotate(elbow(used), -72, 0, 0, w);
      rotate('head', context.pitch * -0.15, used === 'right' ? -4 : 4, 0, w);
      return true;
    }
    if (item.includes('map')) {
      holdMap();
      return true;
    }

    rotate(arm(used), -82, used === 'right' ? -10 : 10, used === 'right' ? 5 : -5, w);
    rotate(elbow(used), -48, 0, 0, w);
    return true;
  }

  function heldItemPose() {
    const main = context.mainItem;
    const off = context.offItem;
    if (main.includes('map') || off.includes('map')) {
      holdMap();
      return;
    }
    for (const side of ['left', 'right']) {
      const item = side === dominant ? main : off;
      if (item === 'minecraft:air') continue;
      const lantern = item.includes('lantern');
      const shield = item.includes('shield');
      const twoHanded = /(greatsword|claymore|zweihander|halberd|glaive|scythe|staff|polearm)/.test(item);
      if (lantern) {
        rotate(arm(side), 9, 0, side === 'right' ? 4 : -4, combatWeight);
        rotate(elbow(side), -5, 0, 0, combatWeight);
        rotate(itemControl(side), 0, 0, side === 'right' ? -8 : 8, combatWeight);
      } else if (shield) {
        rotate(arm(side), -18, side === 'right' ? -12 : 12, side === 'right' ? 8 : -8, combatWeight);
        rotate(elbow(side), -35, 0, 0, combatWeight);
      } else if (twoHanded && side === dominant) {
        rotate(arm(dominant), -28, dominant === 'right' ? -7 : 7, dominant === 'right' ? 5 : -5, combatWeight);
        rotate(elbow(dominant), -28, 0, 0, combatWeight);
        rotate(arm(support), -32, support === 'right' ? -18 : 18, support === 'right' ? 4 : -4, combatWeight);
        rotate(elbow(support), -52, 0, 0, combatWeight);
      }
    }
  }

  function combatSwing() {
    const progress = rig.clamp(context.swing, 0, 1);
    if (progress <= 0) return;
    const w = combatWeight;
    const item = context.mainItem;
    const attackSide = dominant;
    const other = support;
    const sign = attackSide === 'right' ? 1 : -1;
    const alternating = (context.swingIndex & 1) === 0 ? 1 : -1;
    const windup = Math.sin(Math.min(progress * 1.7, 1) * Math.PI * 0.5);
    const strike = Math.sin(Math.sqrt(progress) * Math.PI);
    const follow = rig.smoothstep(rig.clamp((progress - 0.58) / 0.42, 0, 1));
    const stab = /(trident|spear|rapier|estoc|pike)/.test(item);
    const heavy = /(axe|mace|hammer|maul|battleaxe)/.test(item);
    const twoHanded = /(greatsword|claymore|zweihander|halberd|glaive|scythe|staff|polearm|katana)/.test(item);

    if (stab) {
      rotate('pelvis', 0, -sign * strike * 9, 0, w);
      rotate('chest', -8 * windup + 7 * follow, -sign * (19 * windup - 31 * strike), sign * 3, w);
      rotate(arm(attackSide), -122 + strike * 49, -sign * 12, sign * 7, w);
      rotate(elbow(attackSide), -55 + strike * 49, 0, 0, w);
      rig.move(itemControl(attackSide), 0, 0, -strike * 0.22 * w);
      rotate(arm(other), -22, sign * 14, -sign * 5, w);
    } else if (heavy) {
      rotate('pelvis', 8 * windup, sign * 10 * windup, 0, w);
      rotate('chest', -20 * windup + 25 * strike, sign * (19 * windup - 16 * strike), -sign * 5, w);
      rotate(arm(attackSide), -160 * windup + 105 * follow, -sign * 13, sign * 10, w);
      rotate(elbow(attackSide), -61 * windup + 30 * follow, 0, 0, w);
      rotate(arm(other), -87 * windup + 44 * follow, sign * 24, -sign * 8, w);
      rotate(elbow(other), -72 * windup + 25 * follow, 0, 0, w);
      rotate(itemControl(attackSide), 0, 0, -sign * 12, w);
    } else if (twoHanded) {
      const slashSign = sign * alternating;
      rotate('pelvis', 0, slashSign * (15 * windup - 18 * strike), slashSign * 3, w);
      rotate('chest', -10 * windup + 9 * follow, slashSign * (31 * windup - 51 * strike), -slashSign * 7, w);
      rotate(arm(attackSide), -115 * windup + 55 * follow, -slashSign * 31, sign * 9, w);
      rotate(elbow(attackSide), -55 * windup + 18 * follow, 0, 0, w);
      rotate(arm(other), -96 * windup + 43 * follow, slashSign * 26, -sign * 8, w);
      rotate(elbow(other), -72 * windup + 25 * follow, 0, 0, w);
      rotate(itemControl(attackSide), 0, 0, -slashSign * 13, w);
    } else if (item === 'minecraft:air') {
      rotate('chest', -5 * windup, -sign * 22 * strike, sign * 3, w);
      rotate(arm(attackSide), -38 * windup - 76 * strike, -sign * 10, sign * 5, w);
      rotate(elbow(attackSide), -45 * windup + 20 * strike, 0, 0, w);
      rotate(hand(attackSide), -10, 0, sign * 8, w);
    } else {
      const slashSign = sign * alternating;
      rotate('pelvis', 0, slashSign * (9 * windup - 13 * strike), 0, w);
      rotate('chest', -7 * windup + 5 * follow, slashSign * (24 * windup - 43 * strike), -slashSign * 5, w);
      rotate(arm(attackSide), -101 * windup + 48 * follow, -slashSign * 25, sign * 7, w);
      rotate(elbow(attackSide), -36 * windup + 18 * follow, 0, 0, w);
      rotate(hand(attackSide), 0, 0, -slashSign * 11, w);
      rotate(itemControl(attackSide), 0, 0, -slashSign * 9, w);
    }

    rig.twist('spine', -sign * alternating * strike * 12 * w, 0.88);
    rig.bend('spine', (heavy ? 12 : 5) * strike * w, 0.82);
  }

  idle();
  if (context.climbing) climb();
  else if (context.crawling) crawl();
  else if (context.swimming && context.inWater) swim();
  else if (context.passenger) passenger();
  else {
    if (movement > 0.02) walk();
    if (context.crouching) crouch();
    glideOrFall();
  }

  poseHead(context.fallFlying || context.crawling || context.swimming ? 0.35 : 1);
  heldItemPose();
  useItem();
  combatSwing();

  if (context.swing <= 0) {
    rig.bend('spine', context.crouching ? 7 * locomotionWeight : stride * movement * 1.8 * locomotionWeight, 0.84);
    rig.twist('spine', stride * movement * 1.5 * locomotionWeight, 0.90);
  }
});
