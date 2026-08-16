var player = context.player
var useAction = I.getUseAction(context.item)
var isUsingItem = P.isUsingItem(player)
var activeHand = P.getActiveHand(player)
var mat = context.matrices
var pSpeed = P.getSpeed(player)

var motion = context.motion || {}
function motionValue(name, fallback) {
    var value = Number(motion[name])
    return Number.isFinite(value) ? Math.max(0, value) : fallback
}
var motionSwing = motionValue("swing", 1)
var motionSwordSwing = motionValue("swordSwing", 1)
var motionOffhandSwing = motionValue("offhandSwing", 1)
var motionMovement = motionValue("movement", 1)
var motionSwitch = motionValue("switch", 1)
var motionUse = motionValue("use", 1)
var motionImpact = motionValue("impact", 1)
var combatantSwingStyle = String(motion.swingStyle || "HMI")
var combatantRawSwing = Number(context.rawSwingProgress)
if (!Number.isFinite(combatantRawSwing)) combatantRawSwing = 0
combatantRawSwing = M.clamp(combatantRawSwing, 0, 1)

function combatantSwingStrength() {
    var value = motionSwing * (context.mainHand ? 1 : motionOffhandSwing)
    if (I.isIn(context.item, Tags.getVanillaTag("swords"))) value *= motionSwordSwing
    return value
}

function applyCombatantSwingStyle() {
    if (combatantSwingStyle == "HMI" || combatantRawSwing <= 0 || I.isEmpty(context.item)) return

    // Keep every replacement style on the same smooth HMI timebase: no constant Java
    // transforms suddenly appearing at swing start. attack goes 0 -> 1 -> 0 and the
    // secondary wave gives the recovery a small amount of follow-through.
    var raw = combatantRawSwing
    // Run replacements through the same timing curves as upstream HMI instead of the Basic
    // renderer's direct matrix interpolation. That keeps wind-up/impact/recovery consistent with
    // the scripted hand motion and makes the shared arm+item pose settle cleanly back to idle.
    var p = I.isIn(context.item, Tags.getVanillaTag("pickaxes")) ? easeCustom(raw) : easeCustomSec(raw)
    var attack = M.sin(p * M.PI)
    var snap = M.sin(Math.sqrt(M.clamp(p, 0, 1)) * M.PI)
    var follow = M.sin(p * M.PI * 2) * (1 - p)
    var strength = combatantSwingStrength()
    var a = attack * strength
    var s = snap * strength
    var f = follow * strength
    var px = 0.3 * l
    var py = -0.4
    var pz = 0

    switch (combatantSwingStyle) {
        case "Swipe Back":
            M.translate(mat, 0.05 * l * a, 0.08 * a, -0.13 * a)
            M.rotateY(mat, 58 * l * a, px, py, pz)
            M.rotateZ(mat, -54 * l * a, px, py, pz)
            M.rotateX(mat, -72 * a - 9 * f, px, py, pz)
            break
        case "Swipe Back Down":
            M.translate(mat, 0.04 * l * a, -0.26 * a, -0.12 * a)
            M.rotateY(mat, 50 * l * a, px, py, pz)
            M.rotateZ(mat, -48 * l * a, px, py, pz)
            M.rotateX(mat, -36 * a - 8 * f, px, py, pz)
            break
        case "Smooth":
            M.moveZ(mat, -0.14 * a)
            M.moveY(mat, -0.06 * a)
            M.rotateX(mat, -66 * a + 10 * f, px, py, pz)
            M.rotateZ(mat, 8 * l * a, px, py, pz)
            break
        case "Smooth Vanilla":
            M.translate(mat, -0.12 * l * a, -0.17 * a, -0.11 * a)
            M.rotateY(mat, 18 * l * s, px, py, pz)
            M.rotateX(mat, -78 * a, px, py, pz)
            M.rotateZ(mat, -9 * l * a, px, py, pz)
            break
        case "Back":
            M.translate(mat, 0.24 * l * a, 0.07 * a, -0.34 * a)
            M.rotateY(mat, 72 * l * a, px, py, pz)
            M.rotateZ(mat, -50 * l * a, px, py, pz)
            M.rotateX(mat, -104 * a - 8 * f, px, py, pz)
            break
        case "Overhead":
            M.translate(mat, 0, 0.27 * a, -0.17 * a)
            M.rotateX(mat, -132 * a, px, py, pz)
            M.rotateZ(mat, 8 * l * f, px, py, pz)
            break
        case "Chop":
            M.translate(mat, 0, -0.20 * a, -0.28 * a)
            M.rotateX(mat, -102 * a, px, py, pz)
            M.rotateY(mat, 8 * l * f, px, py, pz)
            break
        case "Arc":
            M.translate(mat, 0.05 * l * a, -0.06 * a, -0.18 * a)
            M.rotateY(mat, 78 * l * a, px, py, pz)
            M.rotateX(mat, -76 * a, px, py, pz)
            M.rotateZ(mat, -18 * l * a, px, py, pz)
            break
        case "Stab":
            M.translate(mat, 0.04 * l * a, -0.02 * a, -0.70 * a)
            M.rotateX(mat, -64 * a, px, py, pz)
            M.rotateY(mat, 10 * l * a, px, py, pz)
            break
        case "Slash":
            M.translate(mat, 0.10 * l * a, -0.04 * a, -0.18 * a)
            M.rotateY(mat, 72 * l * a, px, py, pz)
            M.rotateZ(mat, -26 * l * a, px, py, pz)
            M.rotateX(mat, -110 * a - 7 * f, px, py, pz)
            break
        case "Thrust":
            M.translate(mat, 0.03 * l * a, -0.03 * a, -0.78 * a)
            M.rotateX(mat, -22 * a, px, py, pz)
            M.rotateY(mat, 8 * l * a, px, py, pz)
            break
    }
}

if (useAction == "spear") {
    context.equipProgress = 0
}
var l = (context.bl ? 1 : -1)

function easeCustom(t) {
    var t2 = t * t
    var t3 = t2 * t
    return 3 * t * (1 - t) * (1 - t) * 0.44 +
            3 * t2 * (1 - t) * 1 + // 84
            t3
    /*
     * const t2 = t * t;
     * const t3 = t2 * t;
     * const mt = 1 - t;
     * const mt2 = mt * mt;
 * 
     * return 3 * .66 * t * mt2 +
            * 3 * 0.81 * t2 * mt +
            * t3;
     * 
     */
}

function easeCustomSec(t) {
    var t2 = t * t
    var t3 = t2 * t
    return 3 * t * (1 - t) * (1 - t) * 0.44 +
            3 * t2 * (1 - t) * 0.94 +
            t3
}

var GRAVITY = 0.1
var DAMPING = 0.82
var INTENSITY = 0.27

var isChargedM = Object.prototype.hasOwnProperty.call(__hmi_registry, 'isChargedM') ? __hmi_registry['isChargedM'] : (false);
var isChargedO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'isChargedO') ? __hmi_registry['isChargedO'] : (false);

var shootCM = Object.prototype.hasOwnProperty.call(__hmi_registry, 'shootCM') ? __hmi_registry['shootCM'] : (0);
var shootCO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'shootCO') ? __hmi_registry['shootCO'] : (0);


var riptideCounter = Object.prototype.hasOwnProperty.call(__hmi_registry, 'riptideCounter') ? __hmi_registry['riptideCounter'] : (0);
var riptideCounterO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'riptideCounterO') ? __hmi_registry['riptideCounterO'] : (0);

var inWaterCount = Object.prototype.hasOwnProperty.call(__hmi_registry, 'inWaterCount') ? __hmi_registry['inWaterCount'] : (0);


var inspectionCounter = Object.prototype.hasOwnProperty.call(__hmi_registry, 'inspectionCounter') ? __hmi_registry['inspectionCounter'] : (0.0);
var inspectionSpin = Object.prototype.hasOwnProperty.call(__hmi_registry, 'inspectionSpin') ? __hmi_registry['inspectionSpin'] : (0.0);
var isMapHeldBelow = Object.prototype.hasOwnProperty.call(__hmi_registry, 'isMapHeldBelow') ? __hmi_registry['isMapHeldBelow'] : (false);
var mapTransition = Object.prototype.hasOwnProperty.call(__hmi_registry, 'mapTransition') ? __hmi_registry['mapTransition'] : (0.0);
var mapSmoother = Object.prototype.hasOwnProperty.call(__hmi_registry, 'mapSmoother') ? __hmi_registry['mapSmoother'] : (0.0);
var mapZoomer = Object.prototype.hasOwnProperty.call(__hmi_registry, 'mapZoomer') ? __hmi_registry['mapZoomer'] : (0.0);
var shieldDisable = Object.prototype.hasOwnProperty.call(__hmi_registry, 'shieldDisable') ? __hmi_registry['shieldDisable'] : (0.0);
var foodSpeed = Object.prototype.hasOwnProperty.call(__hmi_registry, 'foodSpeed') ? __hmi_registry['foodSpeed'] : (0.0);
var pitchAngleO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'pitchAngleO') ? __hmi_registry['pitchAngleO'] : (0.0);
var yawAngleO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'yawAngleO') ? __hmi_registry['yawAngleO'] : (0.0);
var pitchAngle = Object.prototype.hasOwnProperty.call(__hmi_registry, 'pitchAngle') ? __hmi_registry['pitchAngle'] : (0.0);
var yawAngle = Object.prototype.hasOwnProperty.call(__hmi_registry, 'yawAngle') ? __hmi_registry['yawAngle'] : (0.0);
var brushCounter = Object.prototype.hasOwnProperty.call(__hmi_registry, 'brushCounter') ? __hmi_registry['brushCounter'] : (0.0);
var brushCounterO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'brushCounterO') ? __hmi_registry['brushCounterO'] : (0.0);
var smoothingCrawl = Object.prototype.hasOwnProperty.call(__hmi_registry, 'smoothingCrawl') ? __hmi_registry['smoothingCrawl'] : (0.0);
var crawlDefaulPos = Object.prototype.hasOwnProperty.call(__hmi_registry, 'crawlDefaulPos') ? __hmi_registry['crawlDefaulPos'] : (0.0);
var swimSmoother = Object.prototype.hasOwnProperty.call(__hmi_registry, 'swimSmoother') ? __hmi_registry['swimSmoother'] : (0.0);
var bowWiggle = Object.prototype.hasOwnProperty.call(__hmi_registry, 'bowWiggle') ? __hmi_registry['bowWiggle'] : (0.0);
var bowWiggleO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'bowWiggleO') ? __hmi_registry['bowWiggleO'] : (0.0);
var bowCountO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'bowCountO') ? __hmi_registry['bowCountO'] : (0.0);
var bowCountSecO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'bowCountSecO') ? __hmi_registry['bowCountSecO'] : (0.0);
var bowCount = Object.prototype.hasOwnProperty.call(__hmi_registry, 'bowCount') ? __hmi_registry['bowCount'] : (0.0);
var bowCountSec = Object.prototype.hasOwnProperty.call(__hmi_registry, 'bowCountSec') ? __hmi_registry['bowCountSec'] : (0.0);
var tridentMO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'tridentMO') ? __hmi_registry['tridentMO'] : (0.0);
var trident = Object.prototype.hasOwnProperty.call(__hmi_registry, 'trident') ? __hmi_registry['trident'] : (0.0);
var tridentO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'tridentO') ? __hmi_registry['tridentO'] : (0.0);
var tridentJO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'tridentJO') ? __hmi_registry['tridentJO'] : (0.0);
var tridentM = Object.prototype.hasOwnProperty.call(__hmi_registry, 'tridentM') ? __hmi_registry['tridentM'] : (0.0);
var tridentJ = Object.prototype.hasOwnProperty.call(__hmi_registry, 'tridentJ') ? __hmi_registry['tridentJ'] : (0.0);
var shieldM = Object.prototype.hasOwnProperty.call(__hmi_registry, 'shieldM') ? __hmi_registry['shieldM'] : (0.0);
var shieldO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'shieldO') ? __hmi_registry['shieldO'] : (0.0);
var walk = Object.prototype.hasOwnProperty.call(__hmi_registry, 'walk') ? __hmi_registry['walk'] : (0.0);
var walkSmoother = Object.prototype.hasOwnProperty.call(__hmi_registry, 'walkSmoother') ? __hmi_registry['walkSmoother'] : (0.0);
var fall = Object.prototype.hasOwnProperty.call(__hmi_registry, 'fall') ? __hmi_registry['fall'] : (0.0);
var fallSpeed = Object.prototype.hasOwnProperty.call(__hmi_registry, 'fallSpeed') ? __hmi_registry['fallSpeed'] : (0.0);
var sneak = Object.prototype.hasOwnProperty.call(__hmi_registry, 'sneak') ? __hmi_registry['sneak'] : (0.0);
var a = Object.prototype.hasOwnProperty.call(__hmi_registry, 'a') ? __hmi_registry['a'] : (0.0);
var smoothing = Object.prototype.hasOwnProperty.call(__hmi_registry, 'smoothing') ? __hmi_registry['smoothing'] : (0.0);
var crawler = Object.prototype.hasOwnProperty.call(__hmi_registry, 'crawler') ? __hmi_registry['crawler'] : (0.0);
var offhand = Object.prototype.hasOwnProperty.call(__hmi_registry, 'offhand') ? __hmi_registry['offhand'] : (0.0);
var crossBowM = Object.prototype.hasOwnProperty.call(__hmi_registry, 'crossBowM') ? __hmi_registry['crossBowM'] : (0.0);
var crossBowSecM = Object.prototype.hasOwnProperty.call(__hmi_registry, 'crossBowSecM') ? __hmi_registry['crossBowSecM'] : (0.0);
var crossBowO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'crossBowO') ? __hmi_registry['crossBowO'] : (0.0);
var crossBowSecO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'crossBowSecO') ? __hmi_registry['crossBowSecO'] : (0.0);
var foodCount = Object.prototype.hasOwnProperty.call(__hmi_registry, 'foodCount') ? __hmi_registry['foodCount'] : (0.0);
var foodCountSec = Object.prototype.hasOwnProperty.call(__hmi_registry, 'foodCountSec') ? __hmi_registry['foodCountSec'] : (0.0);
var foodCountO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'foodCountO') ? __hmi_registry['foodCountO'] : (0.0);
var foodCountSecO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'foodCountSecO') ? __hmi_registry['foodCountSecO'] : (0.0);
var drinkCount = Object.prototype.hasOwnProperty.call(__hmi_registry, 'drinkCount') ? __hmi_registry['drinkCount'] : (0.0);
var drinkCountO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'drinkCountO') ? __hmi_registry['drinkCountO'] : (0.0);
var crwl = Object.prototype.hasOwnProperty.call(__hmi_registry, 'crwl') ? __hmi_registry['crwl'] : (0.0);
var mainHandSwitch = Object.prototype.hasOwnProperty.call(__hmi_registry, 'mainHandSwitch') ? __hmi_registry['mainHandSwitch'] : (0.0);
var offHandSwitch = Object.prototype.hasOwnProperty.call(__hmi_registry, 'offHandSwitch') ? __hmi_registry['offHandSwitch'] : (0.0);
var swordAttack = Object.prototype.hasOwnProperty.call(__hmi_registry, 'swordAttack') ? __hmi_registry['swordAttack'] : (false);
var swordAttack2 = Object.prototype.hasOwnProperty.call(__hmi_registry, 'swordAttack2') ? __hmi_registry['swordAttack2'] : (false);
var swimCounter = Object.prototype.hasOwnProperty.call(__hmi_registry, 'swimCounter') ? __hmi_registry['swimCounter'] : (0.0);
var prevSwingM = Object.prototype.hasOwnProperty.call(__hmi_registry, 'prevSwingM') ? __hmi_registry['prevSwingM'] : (false);

var waterWalk = Object.prototype.hasOwnProperty.call(__hmi_registry, 'waterWalk') ? __hmi_registry['waterWalk'] : (0);

var tilting = Object.prototype.hasOwnProperty.call(__hmi_registry, 'tilting') ? __hmi_registry['tilting'] : (0.0);

var usingOffBowPrev = Object.prototype.hasOwnProperty.call(__hmi_registry, 'usingOffBowPrev') ? __hmi_registry['usingOffBowPrev'] : (false);

var spearCounterM = Object.prototype.hasOwnProperty.call(__hmi_registry, 'spearCounterM') ? __hmi_registry['spearCounterM'] : (0);
var spearUsageTime = Object.prototype.hasOwnProperty.call(__hmi_registry, 'spearUsageTime') ? __hmi_registry['spearUsageTime'] : (0);

var canDismountCounter = Object.prototype.hasOwnProperty.call(__hmi_registry, 'canDismountCounter') ? __hmi_registry['canDismountCounter'] : (0);
var canKnockbackCounter = Object.prototype.hasOwnProperty.call(__hmi_registry, 'canKnockbackCounter') ? __hmi_registry['canKnockbackCounter'] : (0);

var spearCounterO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'spearCounterO') ? __hmi_registry['spearCounterO'] : (0);

var canDismountCounterO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'canDismountCounterO') ? __hmi_registry['canDismountCounterO'] : (0);
var canKnockbackCounterO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'canKnockbackCounterO') ? __hmi_registry['canKnockbackCounterO'] : (0);

var hitImpactCounter = Object.prototype.hasOwnProperty.call(__hmi_registry, 'hitImpactCounter') ? __hmi_registry['hitImpactCounter'] : (0);
var hitImpactCounterO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'hitImpactCounterO') ? __hmi_registry['hitImpactCounterO'] : (0);

var regularSwing = Object.prototype.hasOwnProperty.call(__hmi_registry, 'regularSwing') ? __hmi_registry['regularSwing'] : (1);
var swordSwing = Object.prototype.hasOwnProperty.call(__hmi_registry, 'swordSwing') ? __hmi_registry['swordSwing'] : (1);
var pickaxeSwing = Object.prototype.hasOwnProperty.call(__hmi_registry, 'pickaxeSwing') ? __hmi_registry['pickaxeSwing'] : (1);
var shovelSwing = Object.prototype.hasOwnProperty.call(__hmi_registry, 'shovelSwing') ? __hmi_registry['shovelSwing'] : (1);
var generalSwing = Object.prototype.hasOwnProperty.call(__hmi_registry, 'generalSwing') ? __hmi_registry['generalSwing'] : (1);
var axeSwing = Object.prototype.hasOwnProperty.call(__hmi_registry, 'axeSwing') ? __hmi_registry['axeSwing'] : (1);
var tridentSwing = Object.prototype.hasOwnProperty.call(__hmi_registry, 'tridentSwing') ? __hmi_registry['tridentSwing'] : (1);
var bowAnimation = (Object.prototype.hasOwnProperty.call(__hmi_registry, 'bowAnimation') ? __hmi_registry['bowAnimation'] : (1)) * motionUse;
var crossBowAnimation = (Object.prototype.hasOwnProperty.call(__hmi_registry, 'crossBowAnimation') ? __hmi_registry['crossBowAnimation'] : (1)) * motionUse;
var tridentAnimation = (Object.prototype.hasOwnProperty.call(__hmi_registry, 'tridentAnimation') ? __hmi_registry['tridentAnimation'] : (1)) * motionUse;
var drinkingAnimation = (Object.prototype.hasOwnProperty.call(__hmi_registry, 'drinkingAnimation') ? __hmi_registry['drinkingAnimation'] : (1)) * motionUse;
var mainHandSwitchingAnimation = (Object.prototype.hasOwnProperty.call(__hmi_registry, 'mainHandSwitchingAnimation') ? __hmi_registry['mainHandSwitchingAnimation'] : (1)) * motionSwitch;
var offHandSwitchingAnimation = (Object.prototype.hasOwnProperty.call(__hmi_registry, 'offHandSwitchingAnimation') ? __hmi_registry['offHandSwitchingAnimation'] : (1)) * motionSwitch;
var shieldAnimation = (Object.prototype.hasOwnProperty.call(__hmi_registry, 'shieldAnimation') ? __hmi_registry['shieldAnimation'] : (1)) * motionUse;
var brushAnimation = (Object.prototype.hasOwnProperty.call(__hmi_registry, 'brushAnimation') ? __hmi_registry['brushAnimation'] : (1)) * motionUse;
var swimAnimation = (Object.prototype.hasOwnProperty.call(__hmi_registry, 'swimAnimation') ? __hmi_registry['swimAnimation'] : (1)) * motionMovement;
var crawlAnimation = (Object.prototype.hasOwnProperty.call(__hmi_registry, 'crawlAnimation') ? __hmi_registry['crawlAnimation'] : (1)) * motionMovement;
var climbAnimation = (Object.prototype.hasOwnProperty.call(__hmi_registry, 'climbAnimation') ? __hmi_registry['climbAnimation'] : (1)) * motionMovement;
var foodAnimation = (Object.prototype.hasOwnProperty.call(__hmi_registry, 'foodAnimation') ? __hmi_registry['foodAnimation'] : (1)) * motionUse;

if (I.isIn(context.item, Tags.getVanillaTag("pickaxes"))) {
context.swingProgress = easeCustom(context.swingProgress)
} else {
context.swingProgress = easeCustomSec(context.swingProgress)
}

M.moveX(mat, 0.2 * l)

var swing_rot;
if (context.swingProgress < 0.70016) {
swing_rot = M.sin(M.clamp(context.swingProgress, 0, 0.308) * 5.1)
} else {
swing_rot = M.sin(M.clamp(context.swingProgress, 0.70016, 1) * 5.1 - 2)
}

var swing_sword_tilt;
if (context.swingProgress < 0.65245) {
swing_sword_tilt = M.sin(M.clamp(context.swingProgress, 0, 0.16675) * 3.14 * 3)
} else {
swing_sword_tilt = M.sin(M.clamp(context.swingProgress, 0.65245, 1) * 4.4 - 1.2584)
}

swing_rot = swing_rot * swing_rot * swing_rot
var swing = M.clamp(M.sin(context.swingProgress * 4.78), 0, 1)
var swing_hit = M.sin(M.clamp(context.swingProgress, 0.16561, 0.49422) * 4.78 * 2 + 4.7)

var swing_hit_second;
if (context.swingProgress < 0.65594) {
swing_hit_second = M.sin(M.clamp(context.swingProgress, 0.16561, 0.32991) * 4.78 * 2 + 4.7)
} else {
swing_hit_second = M.sin(M.clamp(context.swingProgress, 0.65594, 0.82025) * 4.78 * 2 - 4.7)
}

// Combatant ViewModel tuning composes with HMI's own per-category multipliers.
// Scale amplitudes, not progress, so timing/easing stays identical to upstream HMI.
var swingAmplitude = motionSwing * (context.mainHand ? 1 : motionOffhandSwing)
if (I.isIn(context.item, Tags.getVanillaTag("swords"))) {
    swingAmplitude = swingAmplitude * motionSwordSwing
}
swing_rot = swing_rot * swingAmplitude
swing_sword_tilt = swing_sword_tilt * swingAmplitude
swing = swing * swingAmplitude
swing_hit = swing_hit * swingAmplitude
swing_hit_second = swing_hit_second * swingAmplitude

var usingOffBow;
// -------------------------Offhand Bow Counter (placing the hands in the ready to shoot position)--------
if (isUsingItem && useAction == "bow" && !context.mainHand && activeHand == context.hand) { // Start counting if player is using renderedItem by his offhand & renderedItem useAction is "bow"
    bowCountO = bowCountO + 0.075 * context.deltaTime * 30
    usingOffBow = true;
} else if (!context.mainHand) { // Decrease the counter only if using renderedItem condition is not true. Decreasing starts after pulling counter (bowSecO) reaches zero for better timing
    bowCountO = bowCountO - 0.07 * context.deltaTime * 30
    usingOffBow = false
}
bowCountO = M.clamp(bowCountO, 0, 1) // Limit the counter from 0 to 1
// -------------------------Offhand secondary bow counter (pulling)----------------------------------------------------------
if (isUsingItem && useAction == "bow" && !context.mainHand && activeHand == context.hand && bowCountO == 1) { // Same as bowCountO but starts only when bowCountO (ready to shoot pos) reaches 1
    bowCountSecO = bowCountSecO + 0.025 * context.deltaTime * 30
    bowWiggleO = bowWiggleO + 0.07 * context.deltaTime * 30
} else if (!context.mainHand) { // Same as bowCountO but doesn't rely on other counter
    bowCountSecO = bowCountSecO - 0.11 * context.deltaTime * 30
}
bowCountSecO = M.clamp(bowCountSecO, 0, 1) // Limit the counter from 0 to 1(it's the last time i will say this XD)

// ------------Two exactly same bow counters with only difference being the context.hand (offhand/main)----------

if (isUsingItem && useAction == "bow" && context.mainHand && activeHand == context.hand) {
    bowCount = bowCount + 0.075 * context.deltaTime * 30
} else if (context.mainHand) {
    bowCount = bowCount - 0.07 * context.deltaTime * 30
}
bowCount = M.clamp(bowCount, 0, 1)

if (isUsingItem && useAction == "bow" && context.mainHand && activeHand == context.hand && bowCount == 1) {
    bowCountSec = bowCountSec + 0.025 * context.deltaTime * 30
    bowWiggle = bowWiggle + 0.07 * context.deltaTime * 30
} else if (context.mainHand) {
    bowCountSec = 0
}
bowCountSec = M.clamp(bowCountSec, 0, 1)
// ----------------END END---------------

var ptAngle = (context.mainHand ? pitchAngle : pitchAngleO)
var ywAngle = (context.mainHand ? yawAngle : yawAngleO)


if (pSpeed > 0.05) {
    waterWalk = waterWalk + pSpeed * 2 * context.deltaTime * 30
}

if (P.isTouchingWater(player)) {
    inWaterCount = inWaterCount + 0.07 * context.deltaTime * 30
} else {
    inWaterCount = inWaterCount - 0.07 * context.deltaTime * 30
}
inWaterCount = M.clamp(inWaterCount, 0, 1)


var xOffset = 0
M.translate(mat, 0 * l, 0, 0)
tilting = tilting + context.swingProgress * context.deltaTime * 3

if (!I.isEmpty(context.item)) {
M.moveZ(mat, -0.16)
} else {
M.moveZ(mat, -0.08)
}

if (I.isOf(context.item, Items.get("minecraft:filled_map")) && context.mainHand && I.isEmpty(P.getOffhandItem(player))) {
mapSmoother = mapSmoother + 0.07 * context.deltaTime * 30
} else if (I.isOf(context.item, Items.get("minecraft:filled_map"))) {
mapSmoother = mapSmoother - 0.07 * context.deltaTime * 30
}
mapSmoother = M.clamp(mapSmoother, 0, 1)

if (context.mainHandSwitchEvent && context.mainHand && drinkCount == 0) {
mainHandSwitch = 0
}
mainHandSwitch = mainHandSwitch + 0.015 * context.deltaTime * 30
mainHandSwitch = M.clamp(mainHandSwitch, 0, 1)

// if context.mainHand then
// 	context.equipProgress = context.equipProgress * mainHandSwitch
// end

if (context.mainHand) {
var switchItems = M.sin(M.clamp(mainHandSwitch, 0, 0.5) * 3.14) * mainHandSwitchingAnimation
var switch_fast = M.sin(M.clamp(mainHandSwitch, 0, 0.125) * 12.56) * mainHandSwitchingAnimation

switchItems = Easings.easeInOutBack(switchItems)

if (useAction == "trident" && tridentM > 0.9) {
M.translate(mat, 0, -0.15 * switch_fast, -0.3 * switch_fast)
M.rotateX(mat, 75 * switch_fast, 0.3 * l, -0.4, 0)
M.rotateX(mat, -75 * switchItems, 0.3 * l, -0.4, 0)
M.translate(mat, 0, 0.15 * switch_fast, 0.3 * switch_fast)
} else {
if (useAction == "crossbow") {
M.moveY(mat, -0.25 * switch_fast)
}
M.translate(mat, 0 * switch_fast, 0, -0.2 * switch_fast)
M.rotateY(mat, 25 * l * switch_fast, 0.3 * l, -0.4, 0)
M.rotateX(mat, -55 * switch_fast, 0.3 * l, -0.4, 0)
M.rotateZ(mat, 40 * l * switch_fast, 0.3 * l, -0.4, 0)

M.rotateZ(mat, -40 * l * switchItems, 0.3 * l, -0.4, 0)
M.rotateX(mat, 55 * switchItems, 0.3 * l, -0.4, 0)
M.rotateY(mat, -25 * l * switchItems, 0.3 * l, -0.4, 0)
if (useAction == "crossbow") {
M.moveY(mat, 0.25 * switchItems)
}
M.translate(mat, -0 * switchItems, 0, 0.2 * switchItems)
}
}

if (context.offHandSwitchEvent) {
offHandSwitch = 0
}
offHandSwitch = offHandSwitch + 0.015 * context.deltaTime * 30
offHandSwitch = M.clamp(offHandSwitch, 0, 1)

if (!context.mainHand && foodCountO == 0) {
var switchItems = M.sin(M.clamp(offHandSwitch, 0, 0.5) * 3.14) * offHandSwitchingAnimation
var switch_fast = M.sin(M.clamp(offHandSwitch, 0, 0.125) * 12.56) * offHandSwitchingAnimation

switchItems = Easings.easeInOutBack(switchItems)

if (useAction == "crossbow") {
M.moveY(mat, -0.25 * switch_fast)
}
M.translate(mat, 0 * switch_fast, 0, -0.2 * switch_fast)
M.rotateY(mat, 25 * switch_fast * l, 0.3 * l, -0.4, 0)
M.rotateX(mat, -55 * switch_fast, 0.3 * l, -0.4, 0)
M.rotateZ(mat, 40 * switch_fast * l, 0.3 * l, -0.4, 0)

M.rotateZ(mat, -40 * switchItems * l, 0.3 * l, -0.4, 0)
M.rotateX(mat, 55 * switchItems, 0.3 * l, -0.4, 0)
M.rotateY(mat, -25 * switchItems * l, 0.3 * l, -0.4, 0)
if (useAction == "crossbow") {
M.moveY(mat, 0.25 * switch_fast)
}
M.translate(mat, -0 * switchItems, 0, 0.2 * switchItems)
}

if (!P.isOnGround(player) && context.mainHandSwingProgress == 0 && context.mainHand) {
swordAttack = false
} else if (context.mainHand && context.mainHandSwingProgress == 0) {
swordAttack = true
}
if (prevSwingM != context.swingMHand && context.mainHand) {
swordAttack2 = !swordAttack2
}

if (P.isCrawling(player) && pSpeed > 0.08) {
crwl = crwl + pSpeed * context.deltaTime * 30
}
if (P.getPitch(player) > 40) {
mapZoomer = mapZoomer + 0.05 * context.deltaTime * 30
} else {
mapZoomer = mapZoomer - 0.095 * context.deltaTime * 30
}
mapZoomer = M.clamp(mapZoomer, 0, 1)
var prevSpearUsageTime = spearUsageTime
var prevKnockback = canKnockbackCounter
var prevDismount = canDismountCounter

var prevKnockbackO = canKnockbackCounterO
var prevDismountO = canDismountCounterO
if (isUsingItem && useAction == "spear" && activeHand == context.hand && context.mainHand && I.getSpearData(context.item).canDamage) {
    spearCounterM = spearCounterM + 0.08 * context.deltaTime * 30
    if (!I.getSpearData(context.item).canDismount) {
        canDismountCounter = canDismountCounter + 0.035 * context.deltaTime * 30
    }

    if (!I.getSpearData(context.item).canKnockback) {
        canKnockbackCounter = canKnockbackCounter + 0.055 * context.deltaTime * 30
    }

    if (I.getSpearData(context.item).hitImpact) {
        hitImpactCounter = hitImpactCounter + 0.08 * context.deltaTime * 30
    }
    if (hitImpactCounter > 0) {
        hitImpactCounter = hitImpactCounter + 0.08 * context.deltaTime * 30
    }
} else if (context.mainHand) {
    spearCounterM = spearCounterM - 0.08 * context.deltaTime * 30
    canDismountCounter = 0
    canKnockbackCounter = 0
    spearUsageTime = 0;
    hitImpactCounter = 0
}
if (hitImpactCounter >= 1) {
    hitImpactCounter = 0
}
spearCounterM = M.clamp(spearCounterM, 0, 1) *  M.clamp(1 - M.clamp(swing * 8, 0, 1), 0, 1)
canDismountCounter = M.clamp(canDismountCounter, 0, 1)
canKnockbackCounter = M.clamp(canKnockbackCounter, 0, 1)
//---------------------
if (isUsingItem && useAction == "spear" && activeHand == context.hand  && !context.mainHand && I.getSpearData(context.item).canDamage) {
    spearCounterO = spearCounterO + 0.08 * context.deltaTime * 30
    if (!I.getSpearData(context.item).canDismount) {
        canDismountCounterO = canDismountCounterO + 0.035 * context.deltaTime * 30
    }

    if (!I.getSpearData(context.item).canKnockback) {
        canKnockbackCounterO = canKnockbackCounterO + 0.055 * context.deltaTime * 30
    }
} else if (!context.mainHand) {
    spearCounterO = spearCounterO - 0.08 * context.deltaTime * 30
    canDismountCounterO = 0
    canKnockbackCounterO = 0
    spearUsageTime = 0;
}
spearCounterO = M.clamp(spearCounterO, 0, 1) * M.clamp(1 - M.clamp(swing * 8, 0, 1), 0, 1)
canDismountCounterO = M.clamp(canDismountCounterO, 0, 1)
canKnockbackCounterO = M.clamp(canKnockbackCounterO, 0, 1)

if (canDismountCounter == 0) {
    canDismountCounter = M.lerp(0.5 * context.deltaTime * 30, prevDismount, canDismountCounter)
}
if (canKnockbackCounter == 0) {
    canKnockbackCounter = M.lerp(0.5 * context.deltaTime * 30, prevKnockback, canKnockbackCounter)
}

if (canDismountCounterO == 0) {
    canDismountCounterO = M.lerp(0.5 * context.deltaTime * 30, prevDismountO, canDismountCounterO)
}
if (canKnockbackCounterO == 0) {
    canKnockbackCounterO = M.lerp(0.5 * context.deltaTime * 30, prevKnockbackO, canKnockbackCounterO)
}

//------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
if (isUsingItem && (useAction == "eat" || useAction == "drink" || useAction == "toot_horn") && context.mainHand && activeHand == context.hand) {
foodCount = foodCount + 0.1 * context.deltaTime * 30
} else if (context.mainHand) {
foodCount = foodCount - 0.1 * context.deltaTime * 30
}
foodCount = M.clamp(foodCount, 0, 1)

if (isUsingItem && (useAction == "eat" || useAction == "drink" || useAction == "toot_horn" || useAction == "brush") && context.mainHand && activeHand == context.hand && (foodCount == 1 || brushCounter > 0.4)) {
foodCountSec = foodCountSec + 0.1 * context.deltaTime * 30
}

if (isUsingItem && (useAction == "eat" || useAction == "drink" || useAction == "toot_horn") && !context.mainHand && activeHand == context.hand) {
foodCountO = foodCountO + 0.1 * context.deltaTime * 30
} else if (!context.mainHand) {
foodCountO = foodCountO - 0.1 * context.deltaTime * 30
}
foodCountO = M.clamp(foodCountO, 0, 1)

if (isUsingItem && (useAction == "eat" || useAction == "drink" || useAction == "toot_horn" || useAction == "brush") && !context.mainHand && activeHand == context.hand && (foodCountO == 1 || brushCounterO > 0.4)) {
foodCountSecO = foodCountSecO + 0.1 * context.deltaTime * 30
}
//-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
if (isUsingItem && (useAction == "drink") && context.mainHand && activeHand == context.hand && foodCount == 1) {
drinkCount = drinkCount + 0.04 * context.deltaTime * 30
} else if (context.mainHand) {
drinkCount = drinkCount - 0.1 * context.deltaTime * 30
}
drinkCount = M.clamp(drinkCount, 0, 1)

if (isUsingItem && useAction == "drink" && !context.mainHand && activeHand == context.hand && foodCountO == 1) {
drinkCountO = drinkCountO + 0.04 * context.deltaTime * 30
} else if (!context.mainHand) {
drinkCountO = drinkCountO - 0.1 * context.deltaTime * 30
}
drinkCountO = M.clamp(drinkCountO, 0, 1)

if (isUsingItem && useAction == "crossbow" && !context.mainHand && activeHand == context.hand) {
crossBowO = crossBowO + 0.1 * context.deltaTime * 30
} else if (!context.mainHand) {
crossBowO = crossBowO - 0.1 * context.deltaTime * 30
}
crossBowO = M.clamp(crossBowO, 0, 1)

if (isUsingItem && useAction == "crossbow" && !context.mainHand && activeHand == context.hand && crossBowO == 1) {
crossBowSecO = crossBowSecO + 0.02 * context.deltaTime * 30
} else if (!context.mainHand) {
crossBowSecO = crossBowSecO - 0.1 * context.deltaTime * 30
}
crossBowSecO = M.clamp(crossBowSecO, 0, 1)
// --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
if (isUsingItem && useAction == "crossbow" && context.mainHand && activeHand == context.hand && !I.isChargedCrossbow(context.item)) {
crossBowM = crossBowM + 0.1 * context.deltaTime * 30
} else if (context.mainHand) {
crossBowM = crossBowM - 0.1 * context.deltaTime * 30
}
crossBowM = M.clamp(crossBowM, 0, 1)

if (isUsingItem && useAction == "crossbow" && context.mainHand && activeHand == context.hand && crossBowM == 1 && !I.isChargedCrossbow(context.item)) {
crossBowSecM = crossBowSecM + 0.02 * context.deltaTime * 30
} else if (context.mainHand) {
crossBowSecM = crossBowSecM - 0.1 * context.deltaTime * 30
}
crossBowSecM = M.clamp(crossBowSecM, 0, 1)

// -------------------------Counter for hiding your offhand-------------------------
if (!context.mainHand && I.isEmpty(context.item) && !(isUsingItem && !I.isChargedCrossbow(P.getMainItem(player)) && I.getUseAction(P.getMainItem(player)) != "block" && I.getUseAction(P.getMainItem(player)) != "eat" && I.getUseAction(P.getMainItem(player)) != "toot_horn" && I.getUseAction(P.getMainItem(player)) != "drink" && I.getUseAction(P.getMainItem(player)) != "brush" && I.getUseAction(P.getMainItem(player)) != "spear") && !P.isClimbing(player) && !P.isSwimming(player) && !P.isCrawling(player)) { // Start counting if renderedItem is empty & players is not using any items
offhand = offhand + 0.08 * context.deltaTime * 30
} else if (!context.mainHand || (isUsingItem && !I.isChargedCrossbow(P.getMainItem(player)) && I.getUseAction(P.getMainItem(player)) != "block" && I.getUseAction(P.getMainItem(player)) != "eat" && I.getUseAction(P.getMainItem(player)) != "drink" && I.getUseAction(P.getMainItem(player)) != "toot_horn" && I.getUseAction(P.getMainItem(player)) != "brush" && I.getUseAction(P.getMainItem(player)) != "spear") || P.isClimbing(player) || P.isCrawling(player)) { // Decrease the counter if one of the conditions above is true
offhand = offhand - 0.08 * context.deltaTime * 30
}
offhand = M.clamp(offhand, 0, 1) // Limit the counter from 0 to 1




if (isUsingItem && useAction == "brush" && context.mainHand && activeHand == context.hand) {
brushCounter = brushCounter + 0.1 * context.deltaTime * 30
} else if (context.mainHand) {
brushCounter = brushCounter - 0.1 * context.deltaTime * 30
}
brushCounter = M.clamp(brushCounter, 0, 1) * brushAnimation

if (isUsingItem && useAction == "brush" && !context.mainHand && activeHand == context.hand) {
brushCounterO = brushCounterO + 0.1 * context.deltaTime * 30
} else if (!context.mainHand) {
brushCounterO = brushCounterO - 0.1 * context.deltaTime * 30
}
brushCounterO = M.clamp(brushCounterO, 0, 1) * brushAnimation

// --------------------------------------Offhand trident counters-------------------------------------------
if (isUsingItem && useAction == "trident" && !context.mainHand && activeHand == context.hand) { // Start is the same as bow counter. The only difference being renderedItem use action "trident"
tridentMO = tridentMO + 0.07 * context.deltaTime * 30 // Main counter for lifting the trident up
tridentO = tridentO + 0.045 * context.deltaTime * 30 // Same
tridentJO = tridentJO + 0.1 * context.deltaTime * 30 // Secondary one for jiggling when it's ready
} else if (!context.mainHand) {
if (P.isUsingRiptide(player)) {
tridentMO = tridentMO - 0.57 * context.deltaTime * 30
tridentO = tridentO - 0.53 * context.deltaTime * 30 // Same

} else {
tridentMO = tridentMO - 0.1 * context.deltaTime * 30
tridentO = tridentO - 0.07 * context.deltaTime * 30 // Same

}
tridentJO = tridentJO - 0.1 * context.deltaTime * 30
}
tridentMO = M.clamp(tridentMO, 0, 1)
tridentO = M.clamp(tridentO, 0, 1)

// -------------------------------------Main context.hand trident counters-----------------------------------------
if (isUsingItem && useAction == "trident" && context.mainHand && activeHand == context.hand) { // Same but for "context.mainHand"
tridentM = tridentM + 0.07 * context.deltaTime * 30 // Same
trident = trident + 0.05 * context.deltaTime * 30 // Same
tridentJ = tridentJ + 0.1 * context.deltaTime * 30 // Same
} else if (context.mainHand) {
if (P.isUsingRiptide(player)) {
tridentM = tridentM - 0.57 * context.deltaTime * 30
trident = trident - 0.53 * context.deltaTime * 30 // Same

} else {
tridentM = tridentM - 0.1 * context.deltaTime * 30
trident = trident - 0.07 * context.deltaTime * 30 // Same

}

tridentJ = tridentJ - 0.1 * context.deltaTime * 30
}
trident = trident * M.pow(0.95, context.deltaTime * 30)
tridentM = M.clamp(tridentM, 0, 1)
trident = M.clamp(trident, 0, 1)

// -------------------------------------Main context.hand shield counter-------------------------------------------
if (isUsingItem && useAction == "block" && context.mainHand && activeHand == context.hand) { // Start is the same as trident counter. The only difference being renderedItem use action "shield"
if (I.isIn(context.item, Tags.getVanillaTag("swords"))) {
shieldM = shieldM + 0.12 * context.deltaTime * 30
} else {
shieldM = shieldM + 0.07 * context.deltaTime * 30
}
} else if (context.mainHand) {
if (I.isIn(context.item, Tags.getVanillaTag("swords"))) {
shieldM = shieldM - 0.12 * context.deltaTime * 30
} else {
shieldM = shieldM - 0.07 * context.deltaTime * 30
}
}
shieldM = shieldM - shieldDisable * 0.04 * context.deltaTime * 30
shieldM = M.clamp(shieldM, 0, 1)

// --------------------------------------Off context.hand shield counter--------------------------------------------
if (isUsingItem && useAction == "block" && !context.mainHand && activeHand == context.hand) { // Start is the same as shield counter. The only difference being renderedItem use action "context.mainHand"
shieldO = shieldO + 0.07 * context.deltaTime * 30
} else if (!context.mainHand) {
shieldO = shieldO - 0.07 * context.deltaTime * 30
}
shieldO = shieldO - shieldDisable * 0.04 * context.deltaTime * 30
shieldO = M.clamp(shieldO, 0, 1)

if (pSpeed > 0.08 && M.abs(P.getYSpeed(player)) < 0.08 && P.isOnGround(player)) {
walk = walk + pSpeed * context.deltaTime * 30
walkSmoother = walkSmoother + 0.1 * context.deltaTime * 30
} else {
walkSmoother = walkSmoother - 0.1 * context.deltaTime * 30
}
walkSmoother = M.clamp(walkSmoother, 0, 1)

fallSpeed = fallSpeed + (-1 * P.getYSpeed(player) + M.sin(sneak * 3.14) * 0.14 + M.sin(bowCount * 3.14) * 0.12 + M.sin(bowCountO * 3.14) * 0.12) * INTENSITY * context.deltaTime * 30
fallSpeed = fallSpeed - GRAVITY * fall * context.deltaTime * 30
fallSpeed = fallSpeed * M.pow(DAMPING, context.deltaTime * 30)
fall = fall + fallSpeed * context.deltaTime * 30

if (P.isSneaking(player)) {
sneak = sneak + 0.1 * context.deltaTime * 30
} else {
sneak = sneak - 0.1 * context.deltaTime * 30
}
sneak = M.clamp(sneak, 0, 1)
M.moveY(mat, -0.08 * sneak)
M.rotateX(mat, 4 * M.sin(sneak * 3.14), 0, -0.4, 0)

a = a + 0.04 * context.deltaTime * 30

if (P.isClimbing(player)) {
smoothing = smoothing + 0.1 * context.deltaTime * 30
} else {
smoothing = smoothing - 0.1 * context.deltaTime * 30
}
if (smoothing > 1) {
smoothing = 1
}
if (smoothing < 0) {
smoothing = 0
}

if (P.isCrawling(player)) {
smoothingCrawl = smoothingCrawl + 0.1 * context.deltaTime * 30
} else {
smoothingCrawl = smoothingCrawl - 0.1 * context.deltaTime * 30
}
smoothingCrawl = M.clamp(smoothingCrawl, 0, 1)

if (P.isCrawling(player) && pSpeed > 0.08) {
crawlDefaulPos = crawlDefaulPos + 0.1 * context.deltaTime * 30
} else {
crawlDefaulPos = crawlDefaulPos - 0.06 * context.deltaTime * 30
}
crawlDefaulPos = M.clamp(crawlDefaulPos, 0, 1)

if (P.isClimbing(player) && M.abs(P.getYSpeed(player)) > 0.08) {
if (P.getYSpeed(player) > 0) {
crawler = crawler + P.getYSpeed(player) * context.deltaTime * 30
} else {
crawler = crawler + P.getYSpeed(player) / 2 * context.deltaTime * 30
}
}

if (P.isSwimming(player) && !isUsingItem) {
swimCounter = swimCounter + pSpeed * context.deltaTime * 30
swimSmoother = swimSmoother + 0.1 * context.deltaTime * 30
} else {
swimSmoother = swimSmoother - 0.1 * context.deltaTime * 30
}
swimSmoother = M.clamp(swimSmoother, 0, 1)

M.moveZ(mat, 0.3 * M.sin(swimCounter * 0.55) * swimSmoother * swimAnimation)

if (I.isIn(context.item, Tags.getVanillaTag("axes")) || I.isOf(context.item, Items.get("minecraft:mace"))) {
// M:rotateZ(mat, ywAngle * -0.1, 0.2 * l, -0.3, 0)
M.rotateX(mat, (P.getPitch(player) * -0.03) + ptAngle * 0.1, 0, -0.4, 0)
} else { // if (not I:isEmpty(renderedItem))
// M:rotateZ(mat, ywAngle * -0.05, 0.2 * l, -0.3, 0)
M.rotateX(mat, (P.getPitch(player) * -0.03) + ptAngle * 0.1, 0, -0.4, 0)
}

if (I.isEmpty(context.item)) {
M.translate(mat, 0, -0.15 * -M.cos(swimCounter * 0.55) * swimSmoother + 0.25 * swimSmoother * swimAnimation, 0)
M.rotateY(mat, -15 * l * M.cos(swimCounter * 0.55) * swimSmoother * swimAnimation, 0.3 * l, -0.4, 0)
M.rotateX(mat, -10 * M.cos(swimCounter * 0.55) * swimSmoother * swimAnimation, 0.3 * l, -0.4, 0)
M.rotateX(mat, -30 * swimSmoother * swimAnimation, 0.3 * l, -0.4, 0)
M.rotateZ(mat, -20 * l * M.sin(swimCounter * 0.55) * swimSmoother * swimAnimation, 0.3 * l, -0.4, 0)
} else {
M.rotateY(mat, -15 * l * M.cos(swimCounter * 0.55) * swimSmoother * swimAnimation, 0.3 * l, -0.4, 0)
M.rotateX(mat, -10 * M.cos(swimCounter * 0.55) * swimSmoother * swimAnimation, 0.3 * l, -0.4, 0)
}

if (I.isOf(context.item, Items.get("minecraft:bell")) || I.isLantern(context.item) || I.isIn(context.item, Tags.getVanillaTag("hanging_signs")) || I.isOf(context.item, Items.get("minecraft:pink_petals")) || I.isOf(context.item, Items.get("minecraft:leaf_litter")) || I.isOf(context.item, Items.get("minecraft:wildflowers")) || I.isOf(context.item, Items.get("minecraft:end_crystal")) || I.isOf(context.item, Items.get("minecraft:painting")) || I.isOf(context.item, Items.get("minecraft:item_frame"))) {
if (I.isOf(context.item, Items.get("minecraft:pink_petals")) || I.isOf(context.item, Items.get("minecraft:leaf_litter")) || I.isOf(context.item, Items.get("minecraft:wildflowers"))) {
M.translate(mat, 0, 0.25, -0.05)
} else if (I.isOf(context.item, Items.get("minecraft:end_crystal"))) {
M.moveZ(mat, -0.12)
M.rotateX(mat, -10)
} else {
M.translate(mat, 0, -0.1, 0.05)
M.rotateX(mat, 25)
}
} else if (!I.isEmpty(context.item) && useAction != "crossbow") {
M.moveY(mat, -0.12)
M.rotateZ(mat, -6 * l)
M.rotateX(mat, 6)
}

M.moveY(mat, 0.01 * M.sin(a)) // Idle animation example
M.rotateX(mat, 1.1 * l * M.cos(a), 0.3 * l, -0.4, 0) // Idle animation example
M.rotateY(mat, 0.5 * l * M.sin(a) * l, 0.3 * l, -0.4, 0) // Idle animation example
M.rotateZ(mat, 2 * l * M.sin(a * 0.3) * l, 0.3 * l, -0.4, 0) // Idle animation example

if (I.isEmpty(context.item) && !context.mainHand) {
M.translate(mat, 0, -1 * Easings.easeInOutExpo(offhand), 0.5 * Easings.easeInOutExpo(offhand))
}

var fallMul;
if (I.isEmpty(context.item) || I.isBlock(context.item)) {
fallMul = 0.7
} else {
fallMul = 1
}

if (I.isEmpty(context.item)) {
M.moveZ(mat, 0.06 * (fall * fallMul))
}
M.rotateX(mat, 2 * (fall * fallMul), 0, -0.4, 0)
M.moveY(mat, 0.06 * fall * fallMul)

var walk_val = (context.bl ? walk : (walk - 0.5 * 1.5))
M.rotateX(mat, 1.5 * M.sin(walk_val) * walkSmoother, 0, -0.4, 0)
M.rotateY(mat, -0.5 * M.cos(walk * 1.5) * walkSmoother * l, 0, -0.4, 0)
M.rotateZ(mat, 1 * M.cos(walk * 1.5) * walkSmoother * l, 0, -0.4, 0)

if (useAction == "block" && !I.isIn(context.item, Tags.getVanillaTag("swords"))) {
M.translate(mat, 0.2 * l, 0, 0.1)
M.rotateY(mat, 20 * l, 0.3 * l, -0.4, 0)
}
if (useAction == "block" && context.mainHand) {
M.translate(mat, (-xOffset) * l * Easings.easeInOutBack(shieldM) + 0.1 * l * Easings.easeInOutBack(shieldM) * shieldAnimation, 0, 0)
if (I.isIn(context.item, Tags.getVanillaTag("swords"))) {
M.rotateY(mat, 50 * Easings.easeInOutBack(shieldM) * l * shieldAnimation, 0.3 * l, -0.4, 0)
} else {
M.rotateY(mat, 70 * Easings.easeInOutBack(shieldM) * l * shieldAnimation, 0.3 * l, -0.4, 0)
}
M.rotateX(mat, 13 * M.clamp(M.sin(shieldM * 4.14), 0, 1) * shieldAnimation, 0.3 * l, -0.4, 0)
M.rotateX(mat, -15 * Easings.easeInOutBack(shieldM) * shieldAnimation, 0.3 * l, -0.4, 0)
}
if (useAction == "block" && !context.mainHand) {
M.translate(mat, (-xOffset) * l * Easings.easeInOutBack(shieldO) + 0.1 * l * Easings.easeInOutBack(shieldO) * shieldAnimation, 0, 0)
if (I.isIn(context.item, Tags.getVanillaTag("swords"))) {
M.rotateY(mat, 50 * Easings.easeInOutBack(shieldO) * l * shieldAnimation, 0.3 * l, -0.4, 0)
} else {
M.rotateY(mat, 70 * Easings.easeInOutBack(shieldO) * l * shieldAnimation, 0.3 * l, -0.4, 0)
}
M.rotateX(mat, 13 * M.clamp(M.sin(shieldO * 4.14), 0, 1) * shieldAnimation, 0.3 * l, -0.4, 0)
M.rotateX(mat, -15 * Easings.easeInOutBack(shieldO) * shieldAnimation, 0.3 * l, -0.4, 0)
}

var tridentDraw = Easings.easeInOutBack(tridentM)
var tridentD = Easings.easeInOutBack(trident)
var tridentDrawS = Easings.easeOutBack(tridentM)
if (useAction == "trident" && context.mainHand) {
M.translate(mat, 0, -0.15 * tridentDrawS * tridentAnimation, -0.3 * tridentDrawS * tridentAnimation)
M.rotateX(mat, 65 * tridentDrawS, 0.3 * l * tridentAnimation, -0.4, 0)
M.rotateX(mat, 55 * trident * tridentAnimation, 0.3 * l, -0.4, 0)
M.rotateZ(mat, 10 * l * tridentDrawS * tridentAnimation, 0.3 * l, -0.4, 0)
M.rotateX(mat, 0.3 * M.sin(tridentJ * tridentDrawS * 9.14) * tridentAnimation, 0.3 * l, -0.4, 0)

//M:rotateZ(mat, 10 * M:sin(tridentM * 3.14), 0.3 * l, -0.4, 0)
}
if (!context.mainHand) {
if (I.isEmpty(context.item)) {
M.moveY(mat, 0.6 * tridentDrawS * tridentAnimation)
} else {
M.moveY(mat, 0.2 * tridentDrawS * tridentAnimation)
}
M.translate(mat, 0.25 * l * tridentDrawS * tridentAnimation, 0, -0.15 * tridentDrawS * tridentAnimation)
//M:moveZ(mat, 0.1525 * M:sin(tridentM * 3.14))

M.rotateY(mat, 25 * l * tridentDrawS * tridentAnimation, 0.3 * l, -0.4, 0)
M.rotateX(mat, -30 * tridentDrawS * tridentAnimation, 0.3 * l, -0.4, 0)
//M:rotateY(mat, 10 * M:sin(tridentM * 3.14), 0.3 * l, -0.4, 0)
}

var tridentDrawO = Easings.easeInOutBack(tridentMO)
var tridentDrawSO = Easings.easeOutBack(tridentMO)
if (useAction == "trident" && !context.mainHand) {
M.translate(mat, 0, -0.15 * tridentDrawSO * tridentAnimation, -0.3 * tridentDrawSO * tridentAnimation)
M.rotateX(mat, 65 * tridentDrawSO * tridentAnimation, 0.3 * l, -0.4, 0)
M.rotateZ(mat, 10 * l * tridentDrawSO * tridentAnimation, 0.3 * l, -0.4, 0)
M.rotateX(mat, 0.3 * M.sin(tridentJO * tridentDrawSO * 9.14) * tridentAnimation, 0.3 * l, -0.4, 0)
}
if (context.mainHand) {
if (I.isEmpty(context.item)) {
M.moveY(mat, 0.6 * tridentDrawSO * tridentAnimation)
} else {
M.moveY(mat, 0.2 * tridentDrawSO * tridentAnimation)
}
M.translate(mat, 0.25 * l * tridentDrawSO * tridentAnimation, 0, -0.15 * tridentDrawSO * tridentAnimation)
//M:moveZ(mat, 0.1525 * M:sin(tridentM * 3.14))

M.rotateY(mat, 25 * l * tridentDrawSO * tridentAnimation, 0.3 * l, -0.4, 0)
M.rotateX(mat, -30 * tridentDrawSO * tridentAnimation, 0.3 * l, -0.4, 0)
//M:rotateY(mat, 10 * M:sin(tridentM * 3.14), 0.3 * l, -0.4, 0)
}
// M:moveX(mat, 0.1 * yawTiltingAngle);
// M:moveY(mat, 0.1 * yawTiltingAngle);
// M:rotateZ(mat,  yawTiltingAngle);
// if(not I:isIn(renderedItem, Tags:getVanillaTag("swords")))


var swingOverall = M.sin(context.swingProgress * 3.14) * swingAmplitude
var swingRise = M.clamp(M.sin(context.swingProgress * 6.28), 0, 1) * swingAmplitude
var swingRiseS = M.sin(context.swingProgress * 6.28) * swingAmplitude
if (I.isEmpty(context.item)) {
M.translate(mat, -0.15 * l * swing * regularSwing, 0.1 * swingRiseS + 0.33 * swing + 0.05 * swing_rot + 0.14 * swingRise * regularSwing, -0.1 * swingRiseS - 0.4 * swing_hit - 0.2 * swing * regularSwing)
M.rotateX(mat, -10 * swingRise * regularSwing)
M.moveZ(mat, 0.15 * swing_rot * regularSwing)
M.rotateX(mat, -20 * swing * regularSwing, 0.3 * l, -0.4, 0)
M.rotateX(mat, -7 * swing_hit, 0.3 * l * regularSwing, -0.4, 0)
M.rotateX(mat, 10 * swing_rot, 0.3 * l * regularSwing, -0.4, 0)
M.rotateZ(mat, 20 * l * swing, 0.3 * l * regularSwing, -0.4, 0)
M.rotateY(mat, 5 * l * swing, 0.3 * l * regularSwing, -0.4, 0)
M.rotateX(mat, -5 * swingRiseS, 0.3 * l * regularSwing, -0.4, 0)
M.rotateZ(mat, 10 * l * swingRiseS, 0.3 * l * regularSwing, -0.4, 0)
M.rotateY(mat, 5 * l * swingRiseS, 0.3 * l * regularSwing, -0.4, 0)
M.rotateY(mat, 15 * l * swing_hit, 0.3 * l * regularSwing, -0.4, 0)
// M:scale(mat, 1 - 0.1 * swingRise, 1 - 0.1 * swingRise, 1 - 0.1 * swingRise)
} else if (I.isIn(context.item, Tags.getVanillaTag("pickaxes"))) {
M.moveZ(mat, -0.1 * swing_sword_tilt * pickaxeSwing)
M.moveZ(mat, -0 * swing_hit * pickaxeSwing)
M.moveZ(mat, -0 * swing_hit_second * pickaxeSwing)
M.moveY(mat, 0 * swing_hit * pickaxeSwing)
M.moveY(mat, -0.1 * swingRiseS * pickaxeSwing)
M.moveX(mat, -0.15 * l * swing_hit * pickaxeSwing)
M.moveX(mat, 0.15 * l * swing_hit_second * pickaxeSwing)
M.moveZ(mat, -0.3 * swingOverall * pickaxeSwing)
M.moveY(mat, 0.2 * swingOverall * pickaxeSwing)
M.moveX(mat, -0.15 * l * swingOverall * pickaxeSwing)
M.rotateX(mat, 20 * swing_sword_tilt * pickaxeSwing, 0.3 * l, -0.4, 0)
//M:rotateZ(mat, -20 * l * swing_sword_tilt, 0.3 * l, -0.4, 0)
M.rotateX(mat, 40 * swing_sword_tilt * pickaxeSwing, 0.3 * l, -0.4, 0)
M.rotateX(mat, 10 * swingRise * pickaxeSwing, 0.3 * l, -0.4, 0)
M.rotateX(mat, 10 * swing_rot * pickaxeSwing, 0.3 * l, -0.4, 0)
M.rotateX(mat, -45 * swing_hit * pickaxeSwing, 0.3 * l, -0.4, 0)
M.rotateX(mat, -10 * swing_hit_second * pickaxeSwing, 0.3 * l, -0.4, 0)
M.rotateX(mat, -95 * swingOverall * pickaxeSwing, 0.3 * l, -0.4, 0)
M.rotateY(mat, 20 * swingOverall * l * pickaxeSwing, 0.3 * l, -0.4, 0)
M.rotateZ(mat, M.clamp(20 * l * M.sin(tilting) * swing, -60, 60) * pickaxeSwing, 0.5 * l, -0.5, 0)
} else if ((I.isIn(context.item, Tags.getVanillaTag("swords")) || I.isOf(context.item, Items.get("minecraft:mace")) || useAction == "trident" || useAction == "spear" || I.isIn(context.item, Tags.getVanillaTag("axes")))) {
if (swordAttack && swordAttack2 && !context.blockBreaking && (I.isIn(context.item, Tags.getVanillaTag("swords")))) {
M.moveZ(mat, 0.2 * swing_sword_tilt * swordSwing)
M.moveX(mat, -0.5 * l * swing_sword_tilt * swordSwing)
M.moveY(mat, -0.5 * swing_sword_tilt * swordSwing)
M.moveZ(mat, -0.2 * swing_hit * swordSwing)
M.moveZ(mat, -0 * swing_hit_second * swordSwing)
M.moveY(mat, 0 * swing_hit * swordSwing)
M.moveY(mat, -0.1 * swingRiseS * swordSwing)
M.moveX(mat, -0.15 * l * swing_hit * swordSwing)
M.moveX(mat, 0.15 * l * swing_hit_second * swordSwing)
M.moveZ(mat, -0.3 * swingOverall * swordSwing)
M.moveY(mat, 0.2 * swingOverall * swordSwing)
M.moveX(mat, 0.15 * l * swingOverall * swordSwing)
M.rotateX(mat, 20 * swing_sword_tilt * swordSwing, 0.3 * l, -0.4, 0)
M.rotateZ(mat, 70 * l * swing_sword_tilt * swordSwing, 0.3 * l, -0.4, 0)
M.rotateX(mat, 30 * swing_sword_tilt * swordSwing, 0.3 * l, -0.4, 0)
M.rotateX(mat, 10 * swingRise * swordSwing, 0.3 * l, -0.4, 0)
M.rotateX(mat, 10 * swing_rot * swordSwing, 0.3 * l, -0.4, 0)
M.rotateX(mat, -25 * swing_hit * swordSwing, 0.3 * l, -0.4, 0)
M.rotateX(mat, -10 * swing_hit_second * swordSwing, 0.3 * l, -0.4, 0)
M.rotateX(mat, -75 * swingOverall * swordSwing, 0.3 * l, -0.4, 0)
} else if (swordAttack && !context.blockBreaking && (I.isIn(context.item, Tags.getVanillaTag("swords")))  || (useAction == "trident" || useAction == "spear")) {
if (I.isIn(context.item, Tags.getVanillaTag("swords"))) {
M.moveZ(mat, -0.2 * swing_sword_tilt * swordSwing)
M.moveZ(mat, -0.15 * swing_hit * swordSwing)
M.moveZ(mat, -0.25 * swing_hit_second * swordSwing)
M.moveY(mat, 0.2 * swing_hit * swordSwing)
M.moveY(mat, -0.1 * swingRiseS * swordSwing)
M.moveX(mat, -0.15 * l * swing_hit * swordSwing)
M.moveX(mat, 0.15 * l * swing_hit_second * swordSwing)
M.moveZ(mat, -0.45 * swingOverall * swordSwing)
M.moveY(mat, 0.2 * swingOverall * swordSwing)
M.moveX(mat, -0.15 * l * swingOverall * swordSwing)
M.rotateX(mat, 20 * swing_sword_tilt * swordSwing, 0.3 * l, -0.4, 0)
M.rotateZ(mat, -70 * l * swing_sword_tilt * swordSwing, 0.3 * l, -0.4, 0)
M.rotateX(mat, 30 * swing_sword_tilt * swordSwing, 0.3 * l, -0.4, 0)
M.rotateX(mat, 10 * swingRise * swordSwing, 0.3 * l, -0.4, 0)
M.rotateX(mat, 10 * swing_rot * swordSwing, 0.3 * l, -0.4, 0)
M.rotateX(mat, -55 * swing_hit * swordSwing, 0.3 * l, -0.4, 0)
M.rotateX(mat, -15 * swing_hit_second * swordSwing, 0.3 * l, -0.4, 0)
M.rotateX(mat, -95 * swingOverall * swordSwing, 0.3 * l, -0.4, 0)
} else {
    if (useAction == "trident") {
        M.moveY(mat, -0.5 * swing_sword_tilt * tridentSwing)
        M.moveZ(mat, 0.1 * swing_sword_tilt * tridentSwing)
    } else {
        M.moveY(mat, 0 * swing_sword_tilt * tridentSwing)
        M.moveZ(mat, 0.37 * swing_sword_tilt * tridentSwing)
    }
M.moveX(mat, -0.15 * l * swing_sword_tilt * tridentSwing)

 if (useAction == "trident") {
M.moveZ(mat, -0.25 * swing_hit_second * tridentSwing)
 } else {
M.moveZ(mat, -0.35 * swing_hit_second * tridentSwing)
M.moveZ(mat, -0.25 * Easings.easeOutQuart(swing) * tridentSwing)

 }
//M:moveZ(mat, -0.05 * swing_rot)
M.moveZ(mat, 0.14 * swingOverall * tridentSwing)
M.moveY(mat, -0.2 * swingOverall * tridentSwing)
M.moveX(mat, -0.1 * l * swing_hit * tridentSwing)
M.moveX(mat, -0.1 * l * swingOverall * tridentSwing)
M.moveX(mat, -0.1 * l * swing_hit_second * tridentSwing)

if (useAction == "trident") {
M.rotateZ(mat, 80 * swing_sword_tilt * l * tridentSwing)
} else {
M.rotateZ(mat, 20 * swing_sword_tilt * l * tridentSwing)
M.rotateY(mat, 6 * swing_sword_tilt * l * tridentSwing)
}

M.rotateZ(mat, 5 * swingOverall * l * tridentSwing)
M.rotateX(mat, -40 * swing_sword_tilt * tridentSwing)
M.rotateX(mat, 15 * swing_hit_second * tridentSwing)
M.rotateX(mat, 15 * swing_hit * tridentSwing)
M.rotateX(mat, 20 * swingOverall * tridentSwing)
M.rotateX(mat, 10 * swingOverall * tridentSwing)
M.rotateX(mat, -10 * swingRise * tridentSwing)
M.rotateZ(mat, M.clamp(10 * l * M.sin(tilting * 2) * swing, 0, 30) * tridentSwing)
//M:rotateX(mat, 20 * swingOverall * l)

M.moveY(mat, -0.1 * M.sin(tilting * 2) * swing * tridentSwing)
}
} else if ((!swordAttack || context.blockBreaking) && (I.isIn(context.item, Tags.getVanillaTag("swords"))) || I.isOf(context.item, Items.get("minecraft:mace")) || I.isIn(context.item, Tags.getVanillaTag("axes")) || (context.blockBreaking && useAction == "trident")) {
M.moveZ(mat, -0.2 * swing_sword_tilt)
M.moveZ(mat, -0 * swing_hit)
M.moveZ(mat, -0 * swing_hit_second)
M.moveY(mat, 0 * swing_hit)
M.moveY(mat, -0.1 * swingRiseS)
M.moveX(mat, -0.15 * l * swing_hit)
M.moveX(mat, 0.15 * l * swing_hit_second)
M.moveZ(mat, -0.3 * swingOverall)
M.moveY(mat, 0.2 * swingOverall)
M.moveX(mat, -0.15 * l * swingOverall)
M.rotateX(mat, 20 * swing_sword_tilt, 0.3 * l, -0.4, 0)
if (I.isIn(context.item, Tags.getVanillaTag("axes")) && context.blockBreaking) {
M.rotateZ(mat, -60 * l * swing_sword_tilt, 0.3 * l, -0.4, 0)
} else {
M.rotateZ(mat, -40 * l * swing_sword_tilt, 0.3 * l, -0.4, 0)
}
M.rotateX(mat, 30 * swing_sword_tilt, 0.3 * l, -0.4, 0)
M.rotateX(mat, 10 * swingRise, 0.3 * l, -0.4, 0)
M.rotateX(mat, 10 * swing_rot, 0.3 * l, -0.4, 0)
M.rotateX(mat, -45 * swing_hit, 0.3 * l, -0.4, 0)
M.rotateX(mat, -10 * swing_hit_second, 0.3 * l, -0.4, 0)
M.rotateX(mat, -95 * swingOverall, 0.3 * l, -0.4, 0)
M.rotateZ(mat, M.clamp(30 * l * M.sin(tilting * 2) * swing, 0, 30))
M.moveY(mat, -0.2 * M.sin(tilting * 2) * swing)
} else {
M.moveZ(mat, -0.2 * swing)
M.moveX(mat, -0.15 * l * swing)
M.moveZ(mat, -0.1 * swingRise)
M.moveZ(mat, -0.15 * swing_hit)
M.moveY(mat, 0.1 * swing_hit)
M.moveY(mat, -0.3 * swing)
M.rotateX(mat, 20 * swing_rot, 0.3 * l, -0.4, 0)
M.rotateX(mat, -40 * swing_hit, 0.3 * l, -0.4, 0)
M.rotateX(mat, -20 * swing_hit, 0.3 * l, -0.4, 0)
M.rotateX(mat, 5 * swingRise, 0.3 * l, -0.4, 0)
M.rotateZ(mat, -5 * l * swingOverall)
M.rotateY(mat, 15 * l * swingOverall)
if (I.isIn(context.item, Tags.getVanillaTag("swords"))) {
// M:moveZ(mat, -0.15 * swing_hit);
M.moveZ(mat, -0.15 * swing_hit_second)
M.moveZ(mat, -0.15 * swingOverall)
// M:moveZ(mat, -0.15 * swing_sword_tilt);
M.rotateY(mat, -10 * l * swingOverall)
M.rotateX(mat, -25 * swing_hit_second)
M.rotateX(mat, -20 * swingOverall)
M.rotateX(mat, 20 * swing_sword_tilt)
}
if (!I.isIn(context.item, Tags.getVanillaTag("swords"))) {
M.rotateX(mat, -5 * swingRiseS, 0.3 * l, -0.4, 0)
}
M.rotateZ(mat, 10 * l * swingRiseS, 0.3 * l, -0.4, 0)
M.rotateY(mat, 5 * l * swingRiseS, 0.3 * l, -0.4, 0)
M.rotateZ(mat, M.clamp(30 * l * M.sin(tilting * 2) * swing, 0, 30))
M.moveY(mat, -0.2 * M.sin(tilting * 2) * swing)
}
// M:rotateZ(mat, M:clamp(30 * l * M:sin(tilting * 2) * swing, 0, 30));
// M:moveY(mat, -0.2 * l * M:sin(tilting * 2) * swing);
} else if (I.isIn(context.item, Tags.getVanillaTag("shovels"))) {
M.moveY(mat, 0.6 * swing_sword_tilt)
M.moveZ(mat, -0.5 * swing_sword_tilt)
M.moveZ(mat, -0.3 * swingOverall)
M.moveY(mat, -0.3 * swingOverall)
M.moveY(mat, -0.4 * swing_rot)
M.moveZ(mat, 0.1 * swing_rot)
M.moveY(mat, -0.1 * swing_hit_second)
M.rotateX(mat, -130 * swing_sword_tilt)
M.rotateX(mat, 70 * swingOverall)
M.rotateX(mat, 40 * swing_rot)
M.rotateX(mat, 20 * swing_hit_second)
M.rotateX(mat, 20 * swingRise)
M.rotateX(mat, -10 * swingRiseS)
} else {
M.moveZ(mat, -0.1 * swing)
M.moveX(mat, -0.1 * l * swing)
M.moveZ(mat, -0.1 * swingRise)
M.moveZ(mat, -0.05 * swing_hit)
M.moveY(mat, 0.25 * swing_hit)
M.moveY(mat, -0 * swing)
M.rotateX(mat, 5 * swing_rot, 0.3 * l, -0.4, 0)
M.rotateX(mat, -25 * swing_hit, 0.3 * l, -0.4, 0)
M.rotateX(mat, 5 * swingRise, 0.3 * l, -0.4, 0)
M.rotateZ(mat, -5 * l * swingOverall)
M.rotateY(mat, 15 * l * swingOverall)
M.rotateX(mat, -2 * swingRiseS, 0.3 * l, -0.4, 0)
M.rotateZ(mat, 5 * l * swingRiseS, 0.3 * l, -0.4, 0)
M.rotateY(mat, 5 * l * swingRiseS, 0.3 * l, -0.4, 0)
// M:rotateZ(mat, M:clamp(30 * l * M:sin(tilting * 2) * swing, 0, 30));
// M:moveY(mat, -0.2 * M:sin(tilting * 2) * swing);
}

// Replacement ViewModel swings are authored in the HMI script layer so the arm and item
// share the same scene transform, pivot and timing as the native HMI motion.
applyCombatantSwingStyle()

if (isUsingItem && activeHand == context.hand && useAction == "block") {
if (context.mainHand) {
M.moveX(mat, 0 - 0.25 * (M.sin(context.equipProgress * context.equipProgress * context.equipProgress) + 4 * M.sin(shieldDisable * shieldDisable * shieldDisable * 3.14)) * l * shieldM * shieldAnimation)
M.rotateZ(mat, 10 * l * (M.sin(context.equipProgress * context.equipProgress * context.equipProgress) + 4 * M.sin(shieldDisable * shieldDisable * shieldDisable * 3.14)) * shieldM * shieldAnimation, 0.3 * l, -0.4, 0)
} else {
M.moveX(mat, 0 - 0.25 * (M.sin(context.equipProgress * context.equipProgress * context.equipProgress) + 4 * M.sin(shieldDisable * shieldDisable * shieldDisable * 3.14)) * l * shieldO * shieldAnimation)
M.rotateZ(mat, 10 * l * (M.sin(context.equipProgress * context.equipProgress * context.equipProgress) + 4 * M.sin(shieldDisable * shieldDisable * shieldDisable * 3.14)) * shieldO * shieldAnimation, 0.3 * l, -0.4, 0)
}
}
if (useAction == "crossbow" && crossBowM + crossBowO == 0) {
M.moveZ(mat, 0 + 0.25 * M.sin(context.equipProgress * context.equipProgress * context.equipProgress) * crossBowAnimation)
M.rotateX(mat, 20 * M.sin(context.equipProgress * context.equipProgress * context.equipProgress) * crossBowAnimation, 0.3 * l, -0.4, 0)
} else if (foodCount == 0 && useAction != "bow" && useAction != "block" && (tridentM == 0 && tridentMO == 0)) {
M.moveZ(mat, 0 - 0.25 * M.sin(context.equipProgress * context.equipProgress * context.equipProgress))
M.rotateX(mat, -20 * M.sin(context.equipProgress * context.equipProgress * context.equipProgress), 0.3 * l, -0.4, 0)
if (I.isOf(context.item, Items.get("minecraft:mace"))) {
M.moveZ(mat, 0 - 0.25 * M.sin(context.equipProgress * context.equipProgress * context.equipProgress))
M.rotateZ(mat, 6 * l  * M.sin(context.equipProgress * context.equipProgress * context.equipProgress))
M.rotateX(mat, -10 * M.sin(context.equipProgress * context.equipProgress * context.equipProgress), 0.3 * l, -0.4, 0)
}
if (!I.isBlock(context.item)) {
M.rotateX(mat, (pitchAngle * 0.35 * swing))
}
}
var al = 0
if (P.getPitch(player) != 0) {
al = 90 / P.getPitch(player) / 2.5
} else {
al = 1
}
if (al > 1) {
al = 1
}
if (al < 0) {
al = 1
}

// if(P:isClimbing(player)) then -- Crawling event detection
var multiplier = (I.isLantern(context.item) ? 0.2 : 1)
M.moveZ(mat, 0.2 * smoothing * climbAnimation)
M.moveY(mat, -0.2 * M.cos(crawler) * l * al * smoothing * multiplier * climbAnimation)
M.rotateX(mat, -30 * l * M.sin(crawler) * al * smoothing * multiplier * climbAnimation)
M.rotateX(mat, P.getPitch(player) * smoothing * climbAnimation)
M.moveZ(mat, 0.01 * P.getPitch(player) * smoothing * climbAnimation)
M.moveY(mat, 0.003 * P.getPitch(player) * smoothing * climbAnimation)
M.moveX(mat, -0.0025 * l * P.getPitch(player) * smoothing * climbAnimation)
if (!I.isEmpty(context.item)) {
M.moveX(mat, -0.05 * l * smoothing * climbAnimation)
M.moveZ(mat, -0.2 * smoothing * climbAnimation)
M.moveY(mat, -0.1 * smoothing * climbAnimation)
}
M.moveZ(mat, 0.2 * smoothingCrawl * crawlAnimation)
M.moveZ(mat, -0.2 * l * M.sin(crwl) * smoothingCrawl * al * multiplier * crawlDefaulPos * crawlAnimation)
M.rotateY(mat, 10 * M.sin(crwl) * smoothingCrawl * multiplier * crawlDefaulPos * crawlAnimation)
M.rotateX(mat, M.clamp(20 * l * M.cos(crwl) * smoothingCrawl * multiplier * crawlDefaulPos, 0, 20) * crawlAnimation)
if (I.isEmpty(context.item)) {
M.moveY(mat, 0.3 * smoothingCrawl * crawlAnimation)
M.moveZ(mat, -0.55 * smoothingCrawl * crawlAnimation)
M.rotateX(mat, -45 * smoothingCrawl * crawlAnimation)
M.rotateZ(mat, M.clamp(16 * M.sin(crwl) * smoothingCrawl * multiplier * crawlDefaulPos, 0, 20) * crawlAnimation, 0.3, -0.4, 0)
}
M.rotateX(mat, P.getPitch(player) * smoothingCrawl * crawlAnimation)
M.rotateX(mat, -7 * smoothingCrawl * crawlAnimation)
M.moveZ(mat, 0.01 * P.getPitch(player) * smoothingCrawl * crawlAnimation)
if (I.isEmpty(context.item)) {
M.moveZ(mat, 0.005 * P.getPitch(player) * smoothingCrawl * crawlAnimation)
}
M.moveY(mat, 0.003 * P.getPitch(player) * smoothingCrawl * crawlAnimation)
M.moveX(mat, -0.0025 * l * P.getPitch(player) * smoothingCrawl * crawlAnimation)
if (!I.isEmpty(context.item)) {
M.moveX(mat, -0.1 * l * smoothingCrawl * crawlAnimation)
M.moveZ(mat, -0.2 * smoothingCrawl * crawlAnimation)
M.moveY(mat, -0.1 * smoothingCrawl * crawlAnimation)
}

var easedBow = Easings.easeInOutBack(bowCount) * bowAnimation
var easedBowO = Easings.easeInOutBack(bowCountO) * bowAnimation
var bowShoot = M.sin(easedBow * 3.14) * M.pow((1-(easedBow / 2)), 6) * 0.2 * bowAnimation

if (bowCount > 0) {
offhand = (easedBow == 1 ? -0.1 : 1 - easedBow)
}
if (useAction == "bow" && context.mainHand) {
M.moveX(mat, 0.15 * l)
M.moveZ(mat, -0.085)
M.moveX(mat, 0.1 * l * easedBow)
M.moveZ(mat, 0.085 * easedBow)
M.moveZ(mat, -0.1 * easedBow)
M.moveX(mat, -0.2 * l * easedBow)
M.moveY(mat, 0.15  * easedBow)
M.rotateX(mat, 5 * M.sin(easedBow * 3.14), 0.3 * l, -0.4, 0)
M.rotateY(mat, 15 * l, 0.3 * l, -0.4, 0)
M.rotateY(mat, 0 * l * easedBow, 0.3 * l, -0.4, 0)

M.moveZ(mat, 0.0015 * M.sin(bowWiggle * 15.14) * bowCountSec)
}
if (useAction == "bow" && !context.mainHand) {
M.moveX(mat, 0.15 * l)
M.moveZ(mat, -0.085)
M.moveX(mat, 0.1 * l * easedBowO)
M.moveZ(mat, 0.085 * easedBowO)
M.moveZ(mat, -0.1 * easedBowO)
M.moveX(mat, -0.2 * l * easedBowO)
M.moveY(mat, 0.15 * easedBowO)
M.rotateX(mat, 5 * M.sin(easedBowO * 3.14), 0.3 * l, -0.4, 0)
M.rotateY(mat, 15 * l, 0.3 * l, -0.4, 0)
M.rotateY(mat, 0 * l * easedBowO, 0.3 * l, -0.4, 0)

M.moveZ(mat, 0.0015 * M.sin(bowWiggleO * 15.14) * bowCountSecO)
}

if (!context.mainHand && isUsingItem || (!context.mainHand && I.isEmpty(context.item))) {
var easedBowSec = Easings.easeOutBack(bowCountSec) * bowAnimation
M.moveX(mat, (-xOffset - (xOffset / 1.5)) * l * easedBow)
M.moveX(mat, 0.27 * l * easedBow)
if (!I.isEmpty(context.item)) {
M.moveY(mat, M.sin(easedBow * 1.56 + 3.14))
}
M.moveZ(mat, -0.65 * easedBow)
M.rotateX(mat, 10 * M.sin(easedBow * 3.14) * l, 0.3 * l, -0.4, 0)

M.rotateY(mat, 70 * easedBow * l, 0.3 * l, -0.4, 0)
// if(not I:isEmpty(renderedItem)) then
// 	M:rotateY(mat, -15 * easedBowSec * l, 0.3 * l, -0.4, 0)
// end
M.rotateY(mat, 25 * easedBowSec * l, 0.3 * l, -0.4, 0)
M.rotateY(mat, 0.25 * l * M.sin(bowWiggle * 15.14) * easedBowSec)

M.rotateY(mat, 25 * easedBow * l, 0.3 * l, -0.4, 0)
M.moveY(mat, -0.5 * M.sin(easedBow * 3.14))
}
if (context.mainHand && isUsingItem || (context.mainHand && I.isEmpty(context.item))) {
var easedBowSecO = Easings.easeOutBack(bowCountSecO)
M.moveX(mat, (-xOffset - (xOffset / 1.5)) * l * easedBowO)
M.moveX(mat, 0.27 * l * easedBowO)
// if(not I:isEmpty(renderedItem)) then
// 	M:moveX(mat, 0.4 * easedBowO)
// 	M:moveY(mat, -0.65 * easedBowO)
// 	M:rotateX(mat, 40 * easedBowO, 0.3, -0.4, 0)
// end
if (!I.isEmpty(context.item)) {
M.moveY(mat, M.sin(easedBowO * 1.56 + 3.14))
}
M.moveZ(mat, -0.65 * easedBowO)
M.rotateX(mat, 10 * M.sin(easedBowO * 3.14) * l, 0.3 * l, -0.4, 0)
M.rotateY(mat, 70 * easedBowO * l, 0.3 * l, -0.4, 0)
M.rotateY(mat, 25 * easedBowSecO * l, 0.3 * l, -0.4, 0)
M.rotateY(mat, 0.25 * l * M.sin(bowWiggleO * 15.14) * easedBowSecO)

M.rotateY(mat, 25 * easedBowO * l, 0.3 * l, -0.4, 0)
M.moveY(mat, -0.5 * M.sin(easedBowO * 3.14))
}

var easedCrossBowM = Easings.easeOutBack(crossBowM) * crossBowAnimation
var easedCrossBowSecM = Easings.easeOutBack(crossBowSecM) * crossBowAnimation
var easedCrossBowO = Easings.easeOutBack(crossBowO) * crossBowAnimation
var easedCrossBowSecO = Easings.easeOutBack(crossBowSecO) * crossBowAnimation

if (useAction == "crossbow" && context.mainHand) {
M.moveY(mat, -0.15 * easedCrossBowM)
M.moveZ(mat, 0.3 * easedCrossBowM)
M.rotateZ(mat, 20 * l * easedCrossBowM, -0.3 * l, -0.4, 0)
M.rotateY(mat, 15 * l * easedCrossBowM, -0.3 * l, -0.4, 0)
}
if (!context.mainHand && isUsingItem || (!context.mainHand && I.isEmpty(context.item))) {
M.moveX(mat, (-xOffset - (xOffset / 1.5)) * l * easedCrossBowM)
M.moveX(mat, 0.25 * l * easedCrossBowM)
M.moveZ(mat, -0.1 * easedCrossBowM)
M.moveY(mat, 0.55 * easedCrossBowM)
if (!I.isEmpty(context.item)) {
M.moveY(mat, M.sin(easedCrossBowM * 1.56 + 3.14))
}
M.rotateZ(mat, 15 * l * easedCrossBowM, 0.3 * l, -0.4, 0)
M.rotateY(mat, 80 * l * easedCrossBowM, 0.3 * l, -0.4, 0)
M.rotateY(mat, 15 * l * easedCrossBowSecM, 0.3 * l, -0.4, 0)
M.rotateX(mat, -7 * easedCrossBowSecM, 0.3 * l, -0.4, 0)
}

if (useAction == "crossbow" && !context.mainHand) {
M.moveY(mat, -0.15 * easedCrossBowO)
M.moveZ(mat, 0.3 * easedCrossBowO)
M.rotateZ(mat, 20 * l * easedCrossBowO, -0.3 * l, -0.4, 0)
M.rotateY(mat, 15 * l * easedCrossBowO, -0.3 * l, -0.4, 0)
}
if (context.mainHand && isUsingItem || (context.mainHand && I.isEmpty(context.item))) {
M.moveX(mat, (-xOffset - (xOffset / 1.5)) * l * easedCrossBowO)
M.moveX(mat, 0.25 * l * easedCrossBowO)
M.moveZ(mat, -0.1 * easedCrossBowO)
M.moveY(mat, 0.55 * easedCrossBowO)
if (!I.isEmpty(context.item)) {
M.moveY(mat, M.sin(easedCrossBowO * 1.56 + 3.14))
}
M.rotateZ(mat, 15 * l * easedCrossBowO, 0.3 * l, -0.4, 0)
M.rotateY(mat, 80 * l * easedCrossBowO, 0.3 * l, -0.4, 0)
M.rotateY(mat, 15 * l * easedCrossBowSecO, 0.3 * l, -0.4, 0)
M.rotateX(mat, -7 * easedCrossBowO, 0.3 * l, -0.4, 0)
}

if (context.mainHand) {
foodCount = foodCount * foodAnimation
foodCountO = foodCountO * foodAnimation
foodCountSecO = foodCountSecO * foodAnimation
foodCountSec = foodCountSec * foodAnimation
var easedFoodCount = foodCount * foodCount
if ((useAction == "eat" || useAction == "toot_horn") && context.mainHand) {
M.moveZ(mat, 0.155 * easedFoodCount)
M.moveX(mat, 0.135 * l * easedFoodCount)
M.moveY(mat, -0.27 * easedFoodCount)
M.moveY(mat, -0 * drinkCount)
// M:moveZ(mat, 0.15 * drinkCount)
M.rotateX(mat, 30 * easedFoodCount)
M.rotateX(mat, 20 * drinkCount)
if (useAction == "eat") {
M.rotateX(mat, 3 * Easings.easeInOutBack(M.abs(M.sin(foodCountSec * 3))) * easedFoodCount)
M.rotateY(mat, 4 * l * Easings.easeInOutBack(M.abs(M.sin(foodCountSec * 2))) * easedFoodCount)
M.rotateZ(mat, 6 * l * Easings.easeInOutBack(M.abs(M.sin(foodCountSec * 2))) * easedFoodCount)
} else {
M.rotateX(mat, 2 * Easings.easeInOutSine(M.sin(foodCountSec * 2)) * easedFoodCount)
M.rotateY(mat, 3 * l * Easings.easeInOutSine(M.sin(foodCountSec)) * easedFoodCount)
M.rotateZ(mat, 5 * l * Easings.easeInOutSine(M.sin(foodCountSec)) * easedFoodCount)
}
M.rotateY(mat, 60 * easedFoodCount * l, 0.3 * l, -0.4, 0)
M.rotateX(mat, 25 * M.sin(easedFoodCount * 3.14), 0.3 * l, -0.4, 0)
}
if ((useAction == "drink" || I.isEmpty(context.item) || I.isOf(context.item, Items.get("minecraft:glass_bottle"))) && context.mainHand) {
M.moveZ(mat, 0.1 * easedFoodCount)
M.moveX(mat, 0.11 * l * easedFoodCount)
M.moveY(mat, -0.5 * easedFoodCount)
M.moveY(mat, -0 * drinkCount)
// M:moveZ(mat, 0.15 * drinkCount)
M.rotateX(mat, 50 * easedFoodCount)
M.rotateX(mat, 20 * drinkCount)
M.rotateX(mat, 2 * M.sin(foodCountSec * 6) * drinkCount)
M.rotateY(mat, l * 60 * easedFoodCount, 0.3 * l, -0.4, 0)
M.rotateX(mat, 25 * M.sin(easedFoodCount * 3.14), 0.3 * l, -0.4, 0)
}
} else {
var easedFoodCount = foodCountO * foodCountO
if ((useAction == "eat" || useAction == "toot_horn") && !context.mainHand) {
M.moveZ(mat, 0.155 * easedFoodCount)
M.moveX(mat, 0.135 * l * easedFoodCount)
M.moveY(mat, -0.27 * easedFoodCount)
M.moveY(mat, -0 * drinkCountO)
// M:moveZ(mat, 0.15 * drinkCount)
M.rotateX(mat, 30 * easedFoodCount)
M.rotateX(mat, 20 * drinkCountO)
if (useAction == "eat") {
M.rotateX(mat, 3 * Easings.easeInOutBack(M.abs(M.sin(foodCountSecO * 3))) * easedFoodCount)
M.rotateY(mat, 4 * l * Easings.easeInOutBack(M.abs(M.sin(foodCountSecO * 2))) * easedFoodCount)
M.rotateZ(mat, 6 * l * Easings.easeInOutBack(M.abs(M.sin(foodCountSecO * 2))) * easedFoodCount)
} else {
M.rotateX(mat, 2 * Easings.easeInOutSine(M.sin(foodCountSecO * 2)) * easedFoodCount)
M.rotateY(mat, 3 * l * Easings.easeInOutSine(M.sin(foodCountSecO)) * easedFoodCount)
M.rotateZ(mat, 5 * l * Easings.easeInOutSine(M.sin(foodCountSecO)) * easedFoodCount)
}
M.rotateY(mat, 60 * l * easedFoodCount, 0.3 * l, -0.4, 0)
M.rotateX(mat, 25 * M.sin(easedFoodCount * 3.14), 0.3 * l, -0.4, 0)
}
if ((useAction == "drink") && !context.mainHand) {
M.moveZ(mat, 0.1 * easedFoodCount)
M.moveX(mat, 0.11 * l * easedFoodCount)
M.moveY(mat, -0.5 * easedFoodCount)
M.moveY(mat, -0 * drinkCountO)
// M:moveZ(mat, 0.15 * drinkCount)
M.rotateX(mat, 50 * easedFoodCount)
M.rotateX(mat, 20 * drinkCountO)
M.rotateX(mat, 2 * M.sin(foodCountSecO * 6) * drinkCountO)
M.rotateY(mat, 60 * l * easedFoodCount, 0.3 * l, -0.4, 0)
M.rotateX(mat, 25 * M.sin(easedFoodCount * 3.14), 0.3 * l, -0.4, 0)
}
}

var bsc = Easings.easeInOutBack(brushCounter)
var bscO = Easings.easeInOutBack(brushCounterO)
if (useAction == "brush" && context.mainHand) {
M.moveZ(mat, -0.2 * bsc)
M.moveX(mat, -0.2 * l * bsc)
M.moveY(mat, -0.3 * bsc)
M.moveX(mat, -0.2 * l * M.sin(foodCountSec * 4.14) * bsc)
M.moveY(mat, -0.3 * M.sin(foodCountSec * 4.14) * bsc)
M.rotateX(mat, 10 * M.sin(bsc * 3.14))
M.rotateY(mat, 20 * l * bsc)
M.rotateY(mat, 10 * l * M.sin(foodCountSec * 4.14) * bsc)
M.rotateZ(mat, 30 * l * bsc)
M.rotateZ(mat, 30 * l * M.sin(foodCountSec * 4.14) * bsc)
}
if (useAction == "brush" && !context.mainHand) {
M.moveZ(mat, -0.2 * bscO)
M.moveX(mat, -0.2 * l * bscO)
M.moveY(mat, -0.3 * bscO)
M.moveX(mat, -0.2 * l * M.sin(foodCountSecO * 4.14) * bscO)
M.moveY(mat, -0.3 * M.sin(foodCountSecO * 4.14) * bscO)
M.rotateX(mat, 10 * M.sin(bscO * 3.14))
M.rotateY(mat, 20 * l * bscO)
M.rotateY(mat, 10 * l * M.sin(foodCountSecO * 4.14) * bscO)
M.rotateZ(mat, 30 * l * bscO)
M.rotateZ(mat, 30 * l * M.sin(foodCountSecO * 4.14) * bscO)
}
if (I.isIn(context.item, Tags.getVanillaTag("doors"))) {
M.moveX(mat, 0.2 * l)
M.rotateX(mat, 6, 0.3 * l, -0.4, 0)
M.rotateY(mat, 20 * l, 0.3 * l, -0.4, 0)
}

if (P.isItemCoolingDown(context.item, player) && useAction == 'block') {
shieldDisable = shieldDisable + 0.04 * context.deltaTime * 30
} else if (useAction == "block") {
shieldDisable = shieldDisable - 0.06 * context.deltaTime * 30
}
shieldDisable = M.clamp(shieldDisable, 0, 1)

var easedDisable = shieldDisable * shieldDisable
if (useAction == "block") {
M.moveZ(mat, -0.4 * easedDisable)
M.moveY(mat, 0.15 * easedDisable)
M.moveX(mat, -0.1 * l * easedDisable)
M.rotateX(mat, -30 * easedDisable)
M.rotateX(mat, -10 * M.sin(easedDisable * 3.14))
M.rotateY(mat, -20 * l * easedDisable)
M.rotateZ(mat, -6 * l * easedDisable)
}

prevSwingM = context.swingMHand

var sinalFoodSpeed = M.sin(M.clamp(foodCount, 0.80041, 1) * 3.14 * 5) * 0.45
foodSpeed = foodSpeed + sinalFoodSpeed * context.deltaTime * 30
foodSpeed = foodSpeed * M.pow(0.8, context.deltaTime * 30)
var foodCamera = ((0.25 * M.sin(foodCountSec * 3) * foodCount) + (0.25 * M.sin(foodCountSecO * 3) * foodCountO) + (foodSpeed * 1.5))
var drinkCamera = (0.25 * M.sin(foodCountSec * 3) * drinkCount + (drinkCount * drinkCount * 2.75)) + (0.25 * M.sin(foodCountSecO * 3) * drinkCountO + (drinkCountO * drinkCountO * 2.75))
// C.setCamRot(foodCamera + drinkCamera, 0, 0);
// C.setCamRot((-0.8 * walkSmoother) + fall + ptAngle * 0.02 + (0.2 * M:sin(foodCountSecO * 3) * drinkCountO + (drinkCountO * 4)) + (0.2 * M:sin(foodCountSec * 3) * drinkCount + (drinkCount * 4)) + foodCamera, 0, (ywAngle * 0.08) + (0.2 * M:cos(foodCountSecO * 4) * drinkCountO + (drinkCountO * 4)) + (0.2 * M:cos(foodCountSec * 4) * drinkCount + foodCamera))
// C.setCamPos(0.002 * ywAngle * walkSmoother, 0.05 * math.abs(M:pow(M:sin(walk * 0.8), 3)) * walkSmoother, 0)

// local switchAnimationVariable = Easings:easeInBack(M:sin(M:clamp(mainHandSwitch,0.09723, 0.60632) * 3.24 * 1.65 - 0.1));
// if(I:isIn(renderedItem, Tags:getVanillaTag("bundles"))) then
// 	M:rotateX(mat, 10 * switchAnimationVariable);
// end

var musicDiscHandTilt;
if (mainHandSwitch < 0.65245) {
musicDiscHandTilt = M.sin(M.clamp(mainHandSwitch, 0, 0.16675) * 3.14 * 3)
} else {
musicDiscHandTilt = M.sin(M.clamp(mainHandSwitch, 0.65245, 1) * 4.4 - 1.3)
}
var musicDiscHandJump = M.sin(M.clamp(mainHandSwitch, 0.52459, 0.85809) * 3.14 * 3 - 1.8)
// if(I:isIn(renderedItem, ConventionalItemTags.MUSIC_DISCS)) then
// 	M:rotateX(mat, 45 * musicDiscHandTilt);
// end

if (I.isEmpty(context.item) && drinkCount > 0) {
M.rotateZ(mat, -6 * l)
M.moveY(mat, -0.35)
// M:moveZ(mat, -0.2);
}

var easedMapTransition = Easings.easeInOutBack(mapTransition)
var easedMapSmoother = Easings.easeInOutBack(mapSmoother)
var easedMapZoomer = Easings.easeOutBack(mapZoomer)

if (I.isOf(context.item, Items.get("minecraft:filled_map"))) { //[[ and context.mainHand and I:isEmpty(P:getOffhandItem(player))]]
M.moveX(mat, (0.3 - (0.1 * easedMapZoomer)) * l * easedMapSmoother)
M.moveY(mat, 0.18 * easedMapSmoother)
M.moveZ(mat, 0.12 * easedMapZoomer * easedMapSmoother)
M.rotateX(mat, M.clamp(P.getPitch(player), 0, 50) * easedMapSmoother)
M.rotateX(mat, -40 * easedMapSmoother)
M.rotateY(mat, (40 + (30 * easedMapZoomer)) * l * easedMapSmoother, 0.3 * l, -0.4, 0)
}

if (I.isOf(context.item, Items.get("minecraft:filled_map"))) {
var smoother = 1 - easedMapSmoother
M.moveX(mat, 0.1 * l * smoother)
M.moveY(mat, -0.35 * smoother)
M.moveZ(mat, 0.22 * smoother)
M.rotateX(mat, 24 * smoother)
M.rotateY(mat, 10 * l * smoother)
}

if (useAction == "crossbow") {
M.moveX(mat, 0.1 * l)
M.moveZ(mat, 0.2)
M.rotateX(mat, -5, 0.3 * l, -0.4, 0)
M.rotateY(mat, 20 * l, 0.3 * l, -0.4, 0)
}

if (KeyBindManager.isKeyPressed(74)) {
inspectionCounter = inspectionCounter + 0.04 * context.deltaTime * 30
} else {
inspectionCounter = inspectionCounter - 0.04 * context.deltaTime * 30
}
inspectionCounter = M.clamp(inspectionCounter, 0, 1)

if ((I.isIn(context.item, Tags.getVanillaTag("swords")) || I.isIn(context.item, Tags.getVanillaTag("pickaxes")) || I.isIn(context.item, Tags.getVanillaTag("axes")) || useAction == "trident") && context.mainHand) {
M.moveX(mat, 0.35 * l * Easings.easeInOutBack(inspectionCounter))
M.moveZ(mat, -0.15 * Easings.easeInOutBack(inspectionCounter))
M.rotateY(mat, 40 * Easings.easeInOutBack(inspectionCounter) * l, 0.3 * l, -0.4, 0)
M.rotateX(mat, 13 * M.clamp(M.sin(inspectionCounter * 4.14), 0, 1), 0.3 * l, -0.4, 0)
// M:rotateX(mat, -15 * Easings:easeInOutBack(inspectionCounter), 0.3 * l, -0.4, 0);
M.rotateX(mat, 10 * M.sin(Easings.easeInOutBack(inspectionSpin) * 6.28))
}

if (I.isOf(context.item, Items.get("minecraft:mace")) && context.mainHand) {
M.moveX(mat, 0.35 * l * Easings.easeInOutBack(inspectionCounter))
M.moveZ(mat, -0.15 * Easings.easeInOutBack(inspectionCounter))
M.rotateY(mat, 40 * Easings.easeInOutBack(inspectionCounter) * l, 0.3 * l, -0.4, 0)
M.rotateX(mat, 17 * M.clamp(M.sin(inspectionCounter * 3.14), 0, 1), 0.3 * l, -0.4, 0)
// M:rotateX(mat, -15 * Easings:easeInOutBack(inspectionCounter), 0.3 * l, -0.4, 0);
}





if (context.mainHand) {
isChargedM = I.isChargedCrossbow(context.item)
} else {
isChargedO = I.isChargedCrossbow(context.item)
}


if (P.isUsingRiptide(player) && useAction == "trident" && activeHand == context.hand) {
if (context.mainHand) {
riptideCounter = riptideCounter + 0.08 * context.deltaTime * 30
} else {
riptideCounterO = riptideCounterO + 0.08 * context.deltaTime * 30
}
} else {
if (context.mainHand) {
riptideCounter = riptideCounter - 0.025 * context.deltaTime * 30
} else {
riptideCounterO = riptideCounterO - 0.025 * context.deltaTime * 30
}
}


riptideCounter = M.clamp(riptideCounter, 0, 1)
riptideCounterO = M.clamp(riptideCounterO, 0, 1)
riptideCounter = riptideCounter * M.pow(0.95, context.deltaTime * 30) * (1 - M.clamp(context.swingProgress * 4, 0, 1))  * (1 - M.clamp(tridentM * 6, 0, 1))
riptideCounterO = riptideCounterO * M.pow(0.95, context.deltaTime * 30) * (1 - M.clamp(context.swingProgress * 4, 0, 1))  * M.clamp(1 - tridentMO * 6, 0, 1)


if (useAction == "trident") {
var easedRiptide = (context.mainHand ? Easings.easeOutBack(riptideCounter) : Easings.easeOutBack(riptideCounterO))
var rp = (context.mainHand ? M.clamp(riptideCounter * 3, 0, 1) : M.clamp(riptideCounterO * 3, 0, 1))
M.moveZ(mat, 0.2 * easedRiptide * rp)
M.moveX(mat, -1.25 * l * easedRiptide * rp)
M.moveY(mat, -0 * easedRiptide * rp)
M.rotateY(mat, 5 * l * easedRiptide * rp)
M.rotateX(mat, -30 * Easings.easeOutBack(M.sin((context.mainHand ? riptideCounter : riptideCounterO * 3.14)))  * rp)
M.rotateZ(mat, 70 * Easings.easeOutBack(M.sin((context.mainHand ? riptideCounter : riptideCounterO * 3.14))) * rp * l, 0.7 * l, 0.7, 0)
}


var sc = (context.mainHand ? spearCounterM : spearCounterO)
var scd = (context.mainHand ? canDismountCounter : canDismountCounterO)
var sck = (context.mainHand ? canKnockbackCounter : canKnockbackCounterO)
var sw = (context.mainHand ? mainHandSwitch : offHandSwitch)
var hic = (context.mainHand ? Easings.easeInOutSine(hitImpactCounter) : hitImpactCounterO)
if (useAction == "spear") {
    M.moveZ(mat, -0.25)
    M.moveY(mat, 0.1)
    M.rotateX(mat, -20)
M.moveZ(mat, 0.75 * M.sin(Easings.easeInOutSine(hic) * 3.14) * motionImpact)

M.moveZ(mat, -0.25 * Easings.easeInOutBack(sc) * motionUse)
M.moveZ(mat, 0.25 * Easings.easeOutBack(sck) * sck * motionUse)

M.rotateY(mat, 8 * Easings.easeInOutBack(sc) * l * motionUse)

M.rotateX(mat, 40 * M.sin(sc * 3.14) * motionUse, 0, -0.2, -0.35)
M.rotateX(mat, -8 * Easings.easeInOutBack(scd) * motionUse, 0.5 * l, -0.5, -0.35)
M.rotateY(mat, -1.5 * M.sin(a * 1.5) * Easings.easeInOutBack(scd) * l * motionUse, 0.5 * l, -0.5, -0)
M.rotateX(mat, -1.5 * M.sin(a * 3) * Easings.easeInOutBack(scd) * motionUse, 0.5 * l, -0.5, -0)

M.rotateX(mat, 5 * M.sin(hic * 3.14) * motionImpact)
//M:rotateZ(mat, 10 * M:sin(hic * 3.14) * l, 0.5 * l, -0.5, -0.35)


M.rotateZ(mat, -30 * Easings.easeOutBack(sck) * sck * l * motionUse, 0.5 * l, -0.5, -0.35)
//M:rotateX(mat, -10 * Easings:easeOutBack(canKnockbackCounter) * canKnockbackCounter * l, 0.5 * l, -0.5, -0.35)

    M.rotateZ(mat, -8 * M.sin(M.clamp(sw * 2, 0, 1) * 6.28) * M.sin(M.clamp(sw * 2, 0, 1) * 6.28) * l * motionSwitch, 0.5 * l, -0.5, 0)
    M.rotateX(mat, 20 * M.sin(M.clamp(sw * 2, 0, 1) * 3.14) * M.sin(M.clamp(sw * 2, 0, 1) * 3.14) * motionSwitch, 0.5 * l, -0.5, 0)
}

// if(useAction == "spear") then
// debugger:out("hitImpact: " .. tostring(I:getSpearData(context.item).hitImpact))
// end

// Persist the original HMI global.* state between frames.
__hmi_registry['isChargedM'] = isChargedM;
__hmi_registry['isChargedO'] = isChargedO;
__hmi_registry['shootCM'] = shootCM;
__hmi_registry['shootCO'] = shootCO;
__hmi_registry['riptideCounter'] = riptideCounter;
__hmi_registry['riptideCounterO'] = riptideCounterO;
__hmi_registry['inWaterCount'] = inWaterCount;
__hmi_registry['inspectionCounter'] = inspectionCounter;
__hmi_registry['inspectionSpin'] = inspectionSpin;
__hmi_registry['isMapHeldBelow'] = isMapHeldBelow;
__hmi_registry['mapTransition'] = mapTransition;
__hmi_registry['mapSmoother'] = mapSmoother;
__hmi_registry['mapZoomer'] = mapZoomer;
__hmi_registry['shieldDisable'] = shieldDisable;
__hmi_registry['foodSpeed'] = foodSpeed;
__hmi_registry['pitchAngleO'] = pitchAngleO;
__hmi_registry['yawAngleO'] = yawAngleO;
__hmi_registry['pitchAngle'] = pitchAngle;
__hmi_registry['yawAngle'] = yawAngle;
__hmi_registry['brushCounter'] = brushCounter;
__hmi_registry['brushCounterO'] = brushCounterO;
__hmi_registry['smoothingCrawl'] = smoothingCrawl;
__hmi_registry['crawlDefaulPos'] = crawlDefaulPos;
__hmi_registry['swimSmoother'] = swimSmoother;
__hmi_registry['bowWiggle'] = bowWiggle;
__hmi_registry['bowWiggleO'] = bowWiggleO;
__hmi_registry['bowCountO'] = bowCountO;
__hmi_registry['bowCountSecO'] = bowCountSecO;
__hmi_registry['bowCount'] = bowCount;
__hmi_registry['bowCountSec'] = bowCountSec;
__hmi_registry['tridentMO'] = tridentMO;
__hmi_registry['trident'] = trident;
__hmi_registry['tridentO'] = tridentO;
__hmi_registry['tridentJO'] = tridentJO;
__hmi_registry['tridentM'] = tridentM;
__hmi_registry['tridentJ'] = tridentJ;
__hmi_registry['shieldM'] = shieldM;
__hmi_registry['shieldO'] = shieldO;
__hmi_registry['walk'] = walk;
__hmi_registry['walkSmoother'] = walkSmoother;
__hmi_registry['fall'] = fall;
__hmi_registry['fallSpeed'] = fallSpeed;
__hmi_registry['sneak'] = sneak;
__hmi_registry['a'] = a;
__hmi_registry['smoothing'] = smoothing;
__hmi_registry['crawler'] = crawler;
__hmi_registry['offhand'] = offhand;
__hmi_registry['crossBowM'] = crossBowM;
__hmi_registry['crossBowSecM'] = crossBowSecM;
__hmi_registry['crossBowO'] = crossBowO;
__hmi_registry['crossBowSecO'] = crossBowSecO;
__hmi_registry['foodCount'] = foodCount;
__hmi_registry['foodCountSec'] = foodCountSec;
__hmi_registry['foodCountO'] = foodCountO;
__hmi_registry['foodCountSecO'] = foodCountSecO;
__hmi_registry['drinkCount'] = drinkCount;
__hmi_registry['drinkCountO'] = drinkCountO;
__hmi_registry['crwl'] = crwl;
__hmi_registry['mainHandSwitch'] = mainHandSwitch;
__hmi_registry['offHandSwitch'] = offHandSwitch;
__hmi_registry['swordAttack'] = swordAttack;
__hmi_registry['swordAttack2'] = swordAttack2;
__hmi_registry['swimCounter'] = swimCounter;
__hmi_registry['prevSwingM'] = prevSwingM;
__hmi_registry['waterWalk'] = waterWalk;
__hmi_registry['tilting'] = tilting;
__hmi_registry['usingOffBowPrev'] = usingOffBowPrev;
__hmi_registry['spearCounterM'] = spearCounterM;
__hmi_registry['spearUsageTime'] = spearUsageTime;
__hmi_registry['canDismountCounter'] = canDismountCounter;
__hmi_registry['canKnockbackCounter'] = canKnockbackCounter;
__hmi_registry['spearCounterO'] = spearCounterO;
__hmi_registry['canDismountCounterO'] = canDismountCounterO;
__hmi_registry['canKnockbackCounterO'] = canKnockbackCounterO;
__hmi_registry['hitImpactCounter'] = hitImpactCounter;
__hmi_registry['hitImpactCounterO'] = hitImpactCounterO;
__hmi_registry['regularSwing'] = regularSwing;
__hmi_registry['swordSwing'] = swordSwing;
__hmi_registry['pickaxeSwing'] = pickaxeSwing;
__hmi_registry['shovelSwing'] = shovelSwing;
__hmi_registry['generalSwing'] = generalSwing;
__hmi_registry['axeSwing'] = axeSwing;
__hmi_registry['tridentSwing'] = tridentSwing;
__hmi_registry['bowAnimation'] = bowAnimation;
__hmi_registry['crossBowAnimation'] = crossBowAnimation;
__hmi_registry['tridentAnimation'] = tridentAnimation;
__hmi_registry['drinkingAnimation'] = drinkingAnimation;
__hmi_registry['mainHandSwitchingAnimation'] = mainHandSwitchingAnimation;
__hmi_registry['offHandSwitchingAnimation'] = offHandSwitchingAnimation;
__hmi_registry['shieldAnimation'] = shieldAnimation;
__hmi_registry['brushAnimation'] = brushAnimation;
__hmi_registry['swimAnimation'] = swimAnimation;
__hmi_registry['crawlAnimation'] = crawlAnimation;
__hmi_registry['climbAnimation'] = climbAnimation;
__hmi_registry['foodAnimation'] = foodAnimation;
