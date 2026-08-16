var GRAVITY = 0.04
var DAMPING = 0.85
var INTENSITY = 0.15

var bowGRAVITY = 0.25
var bowDAMPING = 0.8
var bowINTENSITY = 0.28
var l = (context.bl ? 1 : -1)

// Original HMI renders held items from its own first-person basis instead of stacking
// Minecraft's applyItemArmTransform on top of the scripted pose. Keep that basis in
// the resource-pack script so item placement and custom swing packs stay editable.
if (!I.isEmpty(context.item)) {
    M.translate(context.matrices, 0.5 * l, -0.15, -0.85)
    M.rotateX(context.matrices, 15, 0.5 * l, 0.5, 0.5)
    M.scale(context.matrices, 0.9, 0.9, 0.9)
}

var motion = context.motion || {}
function motionValue(name, fallback) {
    var value = Number(motion[name])
    return Number.isFinite(value) ? Math.max(0, value) : fallback
}
var motionSwing = motionValue("swing", 1)
var motionSwordSwing = motionValue("swordSwing", 1)
var motionOffhandSwing = motionValue("offhandSwing", 1)
var motionMovement = motionValue("movement", 1)
var motionLook = motionValue("look", 1)
var motionSwitch = motionValue("switch", 1)
var motionUse = motionValue("use", 1)
var motionImpact = motionValue("impact", 1)


function easeCustom(t) {
    var t2 = t * t
    var t3 = t2 * t
    return 3 * t * (1 - t) * (1 - t) * 0.44 +
            3 * t2 * (1 - t) * 1 +
            t3
}

function easeCustomSec(t) {
    var t2 = t * t
    var t3 = t2 * t
    return 3 * t * (1 - t) * (1 - t) * 0.44 +
            3 * t2 * (1 - t) * 0.94 +
            t3
}

var crossBowM = Object.prototype.hasOwnProperty.call(__hmi_registry, 'crossBowM') ? __hmi_registry['crossBowM'] : (0.0);
var swordAttack2 = Object.prototype.hasOwnProperty.call(__hmi_registry, 'swordAttack2') ? __hmi_registry['swordAttack2'] : (0);
var swordAttack = Object.prototype.hasOwnProperty.call(__hmi_registry, 'swordAttack') ? __hmi_registry['swordAttack'] : (0);
var crossBowSecM = Object.prototype.hasOwnProperty.call(__hmi_registry, 'crossBowSecM') ? __hmi_registry['crossBowSecM'] : (0.0);
var crossBowO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'crossBowO') ? __hmi_registry['crossBowO'] : (0.0);
var crossBowSecO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'crossBowSecO') ? __hmi_registry['crossBowSecO'] : (0.0);
var walk = Object.prototype.hasOwnProperty.call(__hmi_registry, 'walk') ? __hmi_registry['walk'] : (0.0);
var blockRender = Object.prototype.hasOwnProperty.call(__hmi_registry, 'blockRender') ? __hmi_registry['blockRender'] : (true);
var walkSmoother = Object.prototype.hasOwnProperty.call(__hmi_registry, 'walkSmoother') ? __hmi_registry['walkSmoother'] : (0.0);
var swimSmoother = Object.prototype.hasOwnProperty.call(__hmi_registry, 'swimSmoother') ? __hmi_registry['swimSmoother'] : (0.0);
var swimCounter = Object.prototype.hasOwnProperty.call(__hmi_registry, 'swimCounter') ? __hmi_registry['swimCounter'] : (0.0);
var mainHandSwitch = Object.prototype.hasOwnProperty.call(__hmi_registry, 'mainHandSwitch') ? __hmi_registry['mainHandSwitch'] : (0.0);
var offHandSwitch = Object.prototype.hasOwnProperty.call(__hmi_registry, 'offHandSwitch') ? __hmi_registry['offHandSwitch'] : (0.0);
var swingCountPrev = Object.prototype.hasOwnProperty.call(__hmi_registry, 'swingCountPrev') ? __hmi_registry['swingCountPrev'] : (0);
var swingOHandPrev = Object.prototype.hasOwnProperty.call(__hmi_registry, 'swingOHandPrev') ? __hmi_registry['swingOHandPrev'] : (false);
var swingMHandPrev = Object.prototype.hasOwnProperty.call(__hmi_registry, 'swingMHandPrev') ? __hmi_registry['swingMHandPrev'] : (false);
var inspectionCounter = Object.prototype.hasOwnProperty.call(__hmi_registry, 'inspectionCounter') ? __hmi_registry['inspectionCounter'] : (0.0);
var inspectionSpin = Object.prototype.hasOwnProperty.call(__hmi_registry, 'inspectionSpin') ? __hmi_registry['inspectionSpin'] : (0.0);
var prevAge = Object.prototype.hasOwnProperty.call(__hmi_registry, 'prevAge') ? __hmi_registry['prevAge'] : (0.0);
var bowCountO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'bowCountO') ? __hmi_registry['bowCountO'] : (0.0);
var bowCountSecO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'bowCountSecO') ? __hmi_registry['bowCountSecO'] : (0.0);
var bowCount = Object.prototype.hasOwnProperty.call(__hmi_registry, 'bowCount') ? __hmi_registry['bowCount'] : (0.0);
var bowCountSec = Object.prototype.hasOwnProperty.call(__hmi_registry, 'bowCountSec') ? __hmi_registry['bowCountSec'] : (0.0);
var bowPullSpeed = Object.prototype.hasOwnProperty.call(__hmi_registry, 'bowPullSpeed') ? __hmi_registry['bowPullSpeed'] : (0.0);
var bowPullAngle = Object.prototype.hasOwnProperty.call(__hmi_registry, 'bowPullAngle') ? __hmi_registry['bowPullAngle'] : (0.0);
var bowPullSpeedO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'bowPullSpeedO') ? __hmi_registry['bowPullSpeedO'] : (0.0);
var bowPullAngleO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'bowPullAngleO') ? __hmi_registry['bowPullAngleO'] : (0.0);
var mapSmoother = Object.prototype.hasOwnProperty.call(__hmi_registry, 'mapSmoother') ? __hmi_registry['mapSmoother'] : (0.0);
var mapTransition = Object.prototype.hasOwnProperty.call(__hmi_registry, 'mapTransition') ? __hmi_registry['mapTransition'] : (0.0);
var mapZoomer = Object.prototype.hasOwnProperty.call(__hmi_registry, 'mapZoomer') ? __hmi_registry['mapZoomer'] : (0.0);
var fall = Object.prototype.hasOwnProperty.call(__hmi_registry, 'fall') ? __hmi_registry['fall'] : (0.0);
var a = Object.prototype.hasOwnProperty.call(__hmi_registry, 'a') ? __hmi_registry['a'] : (0.0);
var prevPitch = Object.prototype.hasOwnProperty.call(__hmi_registry, 'prevPitch') ? __hmi_registry['prevPitch'] : P.getPitch(context.player);
var prevPitchO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'prevPitchO') ? __hmi_registry['prevPitchO'] : P.getPitch(context.player);
var pitchSpeed = Object.prototype.hasOwnProperty.call(__hmi_registry, 'pitchSpeed') ? __hmi_registry['pitchSpeed'] : (0.0);
var pitchAngle = Object.prototype.hasOwnProperty.call(__hmi_registry, 'pitchAngle') ? __hmi_registry['pitchAngle'] : (0.0);

var pitchSpeedO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'pitchSpeedO') ? __hmi_registry['pitchSpeedO'] : (0.0);
var pitchAngleO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'pitchAngleO') ? __hmi_registry['pitchAngleO'] : (0.0);

var yawSpeedO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'yawSpeedO') ? __hmi_registry['yawSpeedO'] : (0.0);
var yawAngleO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'yawAngleO') ? __hmi_registry['yawAngleO'] : (0.0);

var prevYaw = Object.prototype.hasOwnProperty.call(__hmi_registry, 'prevYaw') ? __hmi_registry['prevYaw'] : P.getYaw(context.player);
var prevYawO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'prevYawO') ? __hmi_registry['prevYawO'] : P.getYaw(context.player);
var yawSpeed = Object.prototype.hasOwnProperty.call(__hmi_registry, 'yawSpeed') ? __hmi_registry['yawSpeed'] : (0.0);
var yawAngle = Object.prototype.hasOwnProperty.call(__hmi_registry, 'yawAngle') ? __hmi_registry['yawAngle'] : (0.0);
var mainHandSwitch = Object.prototype.hasOwnProperty.call(__hmi_registry, 'mainHandSwitch') ? __hmi_registry['mainHandSwitch'] : (0.0);
var offHandSwitch = Object.prototype.hasOwnProperty.call(__hmi_registry, 'offHandSwitch') ? __hmi_registry['offHandSwitch'] : (0.0);

var foodCount = Object.prototype.hasOwnProperty.call(__hmi_registry, 'foodCount') ? __hmi_registry['foodCount'] : (0.0);
var foodCountSec = Object.prototype.hasOwnProperty.call(__hmi_registry, 'foodCountSec') ? __hmi_registry['foodCountSec'] : (0.0);
var foodCountSecO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'foodCountSecO') ? __hmi_registry['foodCountSecO'] : (0.0);
var foodCountO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'foodCountO') ? __hmi_registry['foodCountO'] : (0.0);
var brushCounter = Object.prototype.hasOwnProperty.call(__hmi_registry, 'brushCounter') ? __hmi_registry['brushCounter'] : (0.0);
var brushCounterO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'brushCounterO') ? __hmi_registry['brushCounterO'] : (0.0);
var shieldDisable = Object.prototype.hasOwnProperty.call(__hmi_registry, 'shieldDisable') ? __hmi_registry['shieldDisable'] : (0.0);
var shieldM = Object.prototype.hasOwnProperty.call(__hmi_registry, 'shieldM') ? __hmi_registry['shieldM'] : (0.0);
var shieldO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'shieldO') ? __hmi_registry['shieldO'] : (0.0);
var sneak = Object.prototype.hasOwnProperty.call(__hmi_registry, 'sneak') ? __hmi_registry['sneak'] : (0.0);

var bundleCounter = Object.prototype.hasOwnProperty.call(__hmi_registry, 'bundleCounter') ? __hmi_registry['bundleCounter'] : (0.0);

var brushSpeedM = Object.prototype.hasOwnProperty.call(__hmi_registry, 'brushSpeedM') ? __hmi_registry['brushSpeedM'] : (0);
var brushSpeedO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'brushSpeedO') ? __hmi_registry['brushSpeedO'] : (0);
var brushAngleM = Object.prototype.hasOwnProperty.call(__hmi_registry, 'brushAngleM') ? __hmi_registry['brushAngleM'] : (0);
var brushAngleO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'brushAngleO') ? __hmi_registry['brushAngleO'] : (0);

var tridentM = Object.prototype.hasOwnProperty.call(__hmi_registry, 'tridentM') ? __hmi_registry['tridentM'] : (0);
var tridentMO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'tridentMO') ? __hmi_registry['tridentMO'] : (0);
var tridentJ = Object.prototype.hasOwnProperty.call(__hmi_registry, 'tridentJ') ? __hmi_registry['tridentJ'] : (0);
var tridentJO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'tridentJO') ? __hmi_registry['tridentJO'] : (0);
var spearCounterM = Object.prototype.hasOwnProperty.call(__hmi_registry, 'spearCounterM') ? __hmi_registry['spearCounterM'] : (0);
var spearUsageTime = Object.prototype.hasOwnProperty.call(__hmi_registry, 'spearUsageTime') ? __hmi_registry['spearUsageTime'] : (0);
var canDismountCounter = Object.prototype.hasOwnProperty.call(__hmi_registry, 'canDismountCounter') ? __hmi_registry['canDismountCounter'] : (0);
var canKnockbackCounter = Object.prototype.hasOwnProperty.call(__hmi_registry, 'canKnockbackCounter') ? __hmi_registry['canKnockbackCounter'] : (0);

var spearCounterO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'spearCounterO') ? __hmi_registry['spearCounterO'] : (0);
var canDismountCounterO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'canDismountCounterO') ? __hmi_registry['canDismountCounterO'] : (0);
var canKnockbackCounterO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'canKnockbackCounterO') ? __hmi_registry['canKnockbackCounterO'] : (0);


var hitImpactCounter = Object.prototype.hasOwnProperty.call(__hmi_registry, 'hitImpactCounter') ? __hmi_registry['hitImpactCounter'] : (0);
var hitImpactCounterO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'hitImpactCounterO') ? __hmi_registry['hitImpactCounterO'] : (0);

brushSpeedM = brushSpeedM + (M.sin(foodCountSec * 4.14) * brushCounter) * context.deltaTime * 30
brushSpeedM = brushSpeedM - GRAVITY * brushAngleM * context.deltaTime * 30
brushSpeedM = brushSpeedM * M.pow(DAMPING, context.deltaTime * 30)
brushAngleM = brushAngleM + brushSpeedM * context.deltaTime * 30

brushSpeedO = brushSpeedO + (M.sin(foodCountSecO * 4.14) * brushCounterO) * context.deltaTime * 30
brushSpeedO = brushSpeedO - GRAVITY * brushAngleO * context.deltaTime * 30
brushSpeedO = brushSpeedO * M.pow(DAMPING, context.deltaTime * 30)
brushAngleO = brushAngleO + brushSpeedO * context.deltaTime * 30


var swingHandPrev = (context.mainHand ? swingMHandPrev : swingOHandPrev)
// local easedBowSec = Easings:easeOutBack(bowCountSec);
// bowPullSpeed = bowPullSpeed + easedBowSec * bowINTENSITY * context.deltaTime * 30;
// bowPullSpeed = bowPullSpeed - bowGRAVITY * bowPullAngle * context.deltaTime * 30;
// bowPullSpeed = bowPullSpeed * M:pow(bowDAMPING, context.deltaTime * 30);

// bowPullAngle = bowPullAngle + bowPullSpeed * context.deltaTime * 30;

// if(I:getUseAction(renderedItem) == "bow") then
// 	M:scale(mat, 1, 1, 1 + bowPullAngle * 0.125);
// end

renderAsBlock.put("minecraft:string", false)
renderAsBlock.put("minecraft:resin_clump", false)
renderAsBlock.put("minecraft:vine", false)
renderAsBlock.put("minecraft:bamboo", false)

var sp = (I.getUseAction(P.getMainItem(context.player)) == "spear" ? 1 : 0);
var spo = (I.getUseAction(P.getOffhandItem(context.player)) == "spear" ? 1 : 0);
var sc = (context.mainHand ? spearCounterM : spearCounterO)
var scd = (context.mainHand ? canDismountCounter : canDismountCounterO)
var sck = (context.mainHand ? canKnockbackCounter : canKnockbackCounterO)
var sw = (context.mainHand ? mainHandSwitch : offHandSwitch)

var mat = context.matrices

var hic = (context.mainHand ? Easings.easeInOutSine(hitImpactCounter) : hitImpactCounterO)
var frameStep = context.deltaTime * 30
var swordTag = Tags.getVanillaTag("swords")
var mainSwordMotion = I.isIn(P.getMainItem(context.player), swordTag) ? motionSwordSwing : 1
var offSwordMotion = I.isIn(P.getOffhandItem(context.player), swordTag) ? motionSwordSwing : 1
var mainSwingMotion = motionSwing * mainSwordMotion
var offSwingMotion = motionSwing * motionOffhandSwing * offSwordMotion

// Update only the hand rendered by this invocation. The layer-port executes item_pose once per
// rendered hand; updating both springs here would advance both simulations twice per frame.
if (context.mainHand) {
    var mainMovementPitch = ((P.getSpeed(context.player) * 22 * walkSmoother * -1) + fall * 3 + M.sin(sneak * 3.14) * 0.3) * motionMovement
    var mainSwingPitch = -(M.sin(context.mainHandSwingProgress * 3.14)) * 8 * mainSwingMotion
    var mainLookPitch = (P.getPitch(context.player) - prevPitch) * motionLook
    pitchSpeed = pitchSpeed + (mainMovementPitch + mainSwingPitch + mainLookPitch) * INTENSITY * frameStep

    if (I.getUseAction(context.item) == "block" && !I.isIn(context.item, swordTag)) {
        pitchSpeed = pitchSpeed + 10 * M.sin(shieldDisable * 3.14) * motionUse * INTENSITY * frameStep
        pitchSpeed = pitchSpeed + 12 * M.sin(shieldM * 3.14) * motionUse * INTENSITY * frameStep
    }

    var mainUseImpulse = ((-20 * M.sin(canDismountCounter * 3.14) * spearCounterM)
            + (20 * M.sin(canKnockbackCounter * 3.14) * spearCounterM)
            + (12 * M.sin(inspectionCounter * 3.14))
            + (15 * M.sin(spearCounterM * 3.14))) * motionUse
    var mainImpactImpulse = (-10 * M.clamp(M.sin(Easings.easeInBack(hitImpactCounter) * 6.28), 0, 1)) * motionImpact
    var mainSwitchImpulse = (40 * M.clamp(M.sin(M.clamp(mainHandSwitch * 1.5 * sp, 0, 1) * 6.28), 0, 1)) * motionSwitch
    pitchSpeed = pitchSpeed + (mainUseImpulse + mainImpactImpulse + mainSwitchImpulse) * INTENSITY * frameStep
    pitchSpeed = pitchSpeed - GRAVITY * pitchAngle * frameStep
    pitchSpeed = pitchSpeed * M.pow(DAMPING, frameStep)
    pitchAngle = pitchAngle + pitchSpeed * frameStep

    var mainMovementYaw = (M.sin(walk) * 3 * walkSmoother + M.sin(swimCounter * swimSmoother) * 3) * motionMovement
    var mainSwingYaw = M.sin(context.mainHandSwingProgress * 3.14) * 8 * mainSwingMotion
    var mainSwitchYaw = M.sin(mainHandSwitch * 6.28) * 3 * motionSwitch
    var mainLookYaw = (P.getYaw(context.player) - prevYaw) * motionLook
    yawSpeed = yawSpeed + (mainMovementYaw + mainSwingYaw + mainSwitchYaw + mainLookYaw) * INTENSITY * frameStep
    yawSpeed = yawSpeed - GRAVITY * yawAngle * frameStep
    yawSpeed = yawSpeed * M.pow(DAMPING, frameStep)
    yawAngle = yawAngle + yawSpeed * frameStep
} else {
    var offMovementPitch = ((P.getSpeed(context.player) * 22 * walkSmoother * -1) + fall * 3 + M.sin(sneak * 3.14) * 0.3) * motionMovement
    var offSwingPitch = -(M.sin(context.offHandSwingProgress * 3.14)) * 8 * offSwingMotion
    var offLookPitch = (P.getPitch(context.player) - prevPitchO) * motionLook
    pitchSpeedO = pitchSpeedO + (offMovementPitch + offSwingPitch + offLookPitch) * INTENSITY * frameStep

    if (I.getUseAction(context.item) == "block" && !I.isIn(context.item, swordTag)) {
        pitchSpeedO = pitchSpeedO + 10 * M.sin(shieldDisable * 3.14) * motionUse * INTENSITY * frameStep
        pitchSpeedO = pitchSpeedO + 12 * M.sin(shieldO * 3.14) * motionUse * INTENSITY * frameStep
    }

    var offUseImpulse = ((-20 * M.sin(canDismountCounterO * 3.14) * spearCounterO)
            + (20 * M.sin(canKnockbackCounterO * 3.14) * spearCounterO)
            + (15 * M.sin(spearCounterO * 3.14))) * motionUse
    var offSwitchImpulse = (40 * M.clamp(M.sin(M.clamp(offHandSwitch * 1.5 * spo, 0, 1) * 6.28), 0, 1)) * motionSwitch
    pitchSpeedO = pitchSpeedO + (offUseImpulse + offSwitchImpulse) * INTENSITY * frameStep
    pitchSpeedO = pitchSpeedO - GRAVITY * pitchAngleO * frameStep
    pitchSpeedO = pitchSpeedO * M.pow(DAMPING, frameStep)
    pitchAngleO = pitchAngleO + pitchSpeedO * frameStep

    var offMovementYaw = (M.sin(walk) * 3 * walkSmoother + M.sin(swimCounter * swimSmoother) * 3) * motionMovement
    var offSwingYaw = M.sin(context.offHandSwingProgress * 3.14) * 8 * offSwingMotion
    var offSwitchYaw = M.sin(offHandSwitch * 6.28) * 3 * motionSwitch
    var offLookYaw = (P.getYaw(context.player) - prevYawO) * motionLook
    yawSpeedO = yawSpeedO + (offMovementYaw + offSwingYaw + offSwitchYaw + offLookYaw) * INTENSITY * frameStep
    yawSpeedO = yawSpeedO - GRAVITY * yawAngleO * frameStep
    yawSpeedO = yawSpeedO * M.pow(DAMPING, frameStep)
    yawAngleO = yawAngleO + yawSpeedO * frameStep
}

var ywAngle = (context.mainHand ? yawAngle : yawAngleO)
var ptAngle = (context.mainHand ? pitchAngle : pitchAngleO)
// local swing = M:sin(context.swingProgress * 3.14);
// 		swing = swing * swing * swing;
// 		M:moveY(mat, -0.2 * swing);
// 		M:moveZ(mat, -0.1 * swing);

// 		M:rotateX(mat, -50 * swing);

if (I.isIn(context.item, Tags.getVanillaTag("pickaxes"))) {
    context.swingProgress = easeCustom(context.swingProgress)
} else {
    context.swingProgress = easeCustomSec(context.swingProgress)
}

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
    swing_sword_tilt = M.sin(M.clamp(context.swingProgress, 0.65245, 1) * 4.4 - 1.3)
}

swing_rot = swing_rot * swing_rot * swing_rot
var swing = M.clamp(M.sin(context.swingProgress * 4.78), 0, 1)
var swing_hit = M.sin(M.clamp(context.swingProgress, 0.16561, 0.49422) * 4.78 * 2 + 4.7)
var swingOverall = M.sin(context.swingProgress * 3.14)
var swingRise = M.clamp(M.sin(context.swingProgress * 6.28), 0, 1)
var swingRiseS = M.sin(context.swingProgress * 6.28)

var swing_hit_second;
if (context.swingProgress < 0.65594) {
    swing_hit_second = M.sin(M.clamp(context.swingProgress, 0.16561, 0.32991) * 4.78 * 2 + 4.7)
} else {
    swing_hit_second = M.sin(M.clamp(context.swingProgress, 0.65594, 0.82025) * 4.78 * 2 - 4.7)
}

var swingAmplitude = motionSwing * (context.mainHand ? 1 : motionOffhandSwing)
if (I.isIn(context.item, swordTag)) {
    swingAmplitude = swingAmplitude * motionSwordSwing
}
swing_rot = swing_rot * swingAmplitude
swing_sword_tilt = swing_sword_tilt * swingAmplitude
swing = swing * swingAmplitude
swing_hit = swing_hit * swingAmplitude
swingOverall = swingOverall * swingAmplitude
swingRise = swingRise * swingAmplitude
swingRiseS = swingRiseS * swingAmplitude
swing_hit_second = swing_hit_second * swingAmplitude
if (I.getUseAction(context.item) == "spear") {
   M.rotateZ(mat, 180 * l)

   M.rotateZ(mat, -180 * Easings.easeInOutBack(M.clamp(sw * 2, 0, 1)) * l)
   M.moveZ(mat, -0.2 * Easings.easeInOutSine(Easings.easeInOutBack(sc * 0.8)))

   M.moveY(mat, -0.05 * Easings.easeInOutBack(scd))

   M.rotateX(mat, -70 * Easings.easeInOutBack(sc * 0.8))
   M.rotateX(mat, -8 * Easings.easeInOutBack(scd))
   M.rotateY(mat, 60 * Easings.easeInOutBack(sc * 0.8) * l)
   M.rotateY(mat, -30 * Easings.easeInOutBack(scd) * l)

   //M:rotateY(mat, 10 * M:sin(canKnockbackCounter * 3.14) * l)
   M.rotateY(mat, -60 * Easings.easeOutBack(sck) * sck * l)
   //M:rotateZ(mat, -20 * Easings:easeInOutBack(M:sin(spearCounterM * 3.14) * 0.8))

    M.moveY(mat, -0.25 * M.clamp(M.sin(Easings.easeInOutSine(hic) * 6.28), 0, 1) * motionImpact)

}
if ((I.getUseAction(context.item) != "block" && I.getUseAction(context.item) != "crossbow") || I.isIn(context.item, Tags.getVanillaTag("swords"))) {
    // if(not I:isIn(renderedItem, Tags:getVanillaTag("swords"))) then
    M.moveZ(mat, -0.05 * swing_rot)
    M.moveY(mat, -0.05 * swing_rot)
    M.rotateX(mat, 10 * swing_rot)
    M.rotateX(mat, -30 * swing_rot)
    M.rotateX(mat, -10 * swing_hit)

    if (!I.isIn(context.item, Tags.getVanillaTag("swords"))) {
        if (I.getUseAction(context.item) == "trident" || I.getUseAction(context.item) == "spear") {
            // if not swordAttack then

            // M:moveZ(mat, 0.1 * swing_rot)
            // M:moveZ(mat, -0.25 * swing_hit)
            // M:moveZ(mat, -0.25 * swingOverall)
            // M:moveY(mat, -0.1 * swing_rot)
            // end
            if (I.getUseAction(context.item) == "spear") {
                //M:moveZ(mat, -0.1 * swing_hit)

            }
            M.moveZ(mat, -0.1 * swing_rot)
            //M:moveZ(mat, -0.3 * swingOverall)
            //M:moveZ(mat, -0.3 * swing_hit)
            //M:moveX(mat, 0.05 * l * swingOverall)
            M.moveY(mat, -0.05 * swing_rot)
            if (I.getUseAction(context.item) == "spear") {
                M.moveY(mat, -0.15 * swing_hit)

                M.rotateX(mat, -5 * swing_hit)
            }
            M.rotateX(mat, -10 * swing_rot)
            M.rotateX(mat, -15 * swing_hit)
            if (I.getUseAction(context.item) == "trident") {
            M.rotateX(mat, -45 * swingOverall)
            } else {
            M.rotateX(mat, -45 * swing_sword_tilt)
            }

            M.moveY(mat, 0.05 * swing_hit)
            M.moveY(mat, 0.3 * swingOverall)
            //if not swordAttack then

            // M:rotateX(mat, -50 * M:clamp(swing_rot * 20, 0, 1))
            // -- M:rotateZ(mat, -180 * M:clamp(swing_rot * 20, 0, 1))
            // end

        } else {
            M.moveZ(mat, -0.05 * swing_rot)
            M.moveY(mat, -0.05 * swing_rot)
            M.rotateX(mat, -10 * swing_rot)
            M.rotateX(mat, -25 * swing_hit)
        }
    }
    // end

    if (I.isIn(context.item, Tags.getVanillaTag("shovels"))) {
        M.moveY(mat, 0.12 * swing_sword_tilt)
        M.moveZ(mat, 0.05 * swing_sword_tilt)
        M.rotateX(mat, 10 * swing_sword_tilt)
        M.rotateX(mat, -30 * swingOverall)
        M.rotateX(mat, 20 * swing_rot)
        M.rotateX(mat, 10 * swing_hit_second)
    }
    if (I.isIn(context.item, Tags.getVanillaTag("swords"))) {
        swing = M.sin(context.swingProgress * 3.14)
        M.moveY(mat, -0.1 * Easings.easeInOutBack(swing))
        if (I.isIn(context.item, Tags.getVanillaTag("swords"))) {
            M.rotateX(mat, -60 * Easings.easeInOutBack(swing))
        } else {
            M.rotateX(mat, -30 * Easings.easeInOutBack(swing))
        }
    }
    if (I.getUseAction(context.item) == "bow") {
        M.moveX(mat, -0.065 * l)
    }
}

if (I.isIn(context.item, Tags.getVanillaTag("beds"))) {
    M.moveZ(mat, 0.2)
    M.rotateY(mat, 180 * l, -0.15 * l, -0.4, 0)
}

if (I.isOf(context.item, Items.get("minecraft:bell")) || I.isLantern(context.item) || I.isOf(context.item, Items.get("minecraft:end_crystal")) || I.isIn(context.item, Tags.getVanillaTag("hanging_signs")) || I.isOf(context.item, Items.get("minecraft:pink_petals")) || I.isOf(context.item, Items.get("minecraft:leaf_litter")) || I.isOf(context.item, Items.get("minecraft:wildflowers"))) {
    if (!I.isOf(context.item, Items.get("minecraft:end_crystal"))) {
        M.moveY(mat, -0.62)
    }
    if (I.isIn(context.item, Tags.getVanillaTag("hanging_signs"))) {
        M.moveY(mat, -0.07)
    }
    if (I.isOf(context.item, Items.get("minecraft:pink_petals")) || I.isOf(context.item, Items.get("minecraft:leaf_litter")) || I.isOf(context.item, Items.get("minecraft:wildflowers"))) {
        M.moveY(mat, 0.4)
        M.rotateX(mat, -70)
    } else {
        M.moveZ(mat, 0.2)
        M.rotateX(mat, -25)
    }
    if (I.isOf(context.item, Items.get("minecraft:pink_petals")) || I.isOf(context.item, Items.get("minecraft:wildflowers")) || I.isOf(context.item, Items.get("minecraft:leaf_litter"))) {
        // M:moveZ(mat, (M:clamp(P:getPitch(context.player) / 2.5, -20, 90) + pitchAngle) / -100)
        // M:moveY(mat, (M:clamp(P:getPitch(context.player) / 2.5, -20, 90) + pitchAngle) / -100)
        M.rotateX(mat, M.clamp(P.getPitch(context.player) / 2.5, -20, 90) + ptAngle + ywAngle * 0.5, 0, -0.13, 0)
    }
    if (I.isOf(context.item, Items.get("minecraft:bell")) || I.isLantern(context.item) || I.isOf(context.item, Items.get("minecraft:end_crystal"))) {
        if (I.isOf(context.item, Items.get("minecraft:end_crystal"))) {
            M.scale(mat, 1 + 0.01 * M.sin(a * 15), 1 + 0.01 * M.sin(a * 15), 1 + 0.01 * M.sin(a * 8))
            M.moveY(mat, 0.03 * M.sin(a * 2))
            M.moveY(mat, 0.25)
            M.moveY(mat, ptAngle / 150)
            M.moveX(mat, ywAngle / 150 * l * -1)
            M.rotateZ(mat, 5 * M.sin(a))
            M.scale(mat, 0.7, 0.7, 0.7)
        } else if (I.isOf(context.item, Items.get("minecraft:bell"))) {
            M.moveX(mat, 0.15 * l)
            M.moveY(mat, -0.05)
            M.moveZ(mat, -0.1)
            M.scale(mat, 1.2, 1.2, 1.2)
            M.rotateX(mat, M.clamp(P.getPitch(context.player) / 2.5, -20, 90) + ptAngle, -0.1 * l, 0.4, 0.1)
            M.rotateZ(mat, ywAngle * -1, -0.1 * l, 0.4, 0.1)
        } else {
            M.rotateX(mat, M.clamp(P.getPitch(context.player) / 2.5, -20, 90) + ptAngle, 0, 0.4, 0)
            M.rotateZ(mat, ywAngle * -1, 0, 0.4, 0)
        }
    }
    if (I.isIn(context.item, Tags.getVanillaTag("hanging_signs"))) {
        M.rotateX(mat, M.clamp(P.getPitch(context.player) / 2.5, -35, 90) + ptAngle, 0, 0.55, 0)
        M.rotateZ(mat, ywAngle * -1, 0, 0.55, 0)
    }
} else if (I.isOf(context.item, Items.get("minecraft:painting")) || I.isOf(context.item, Items.get("minecraft:item_frame"))) {
    context.swingProgress = 0
    M.rotateX(mat, -25)
    M.moveY(mat, -0.65)
    M.rotateX(mat, M.clamp(P.getPitch(context.player) / 2.5, -25, 90) + ptAngle, 0, 0.45, 0)
    M.rotateZ(mat, ywAngle * -1, 0, 0.55, 0)
} else if (I.isBlock(context.item)) {
    M.moveY(mat, -0.025)
    M.moveZ(mat, -0.025)
    M.rotateX(mat, -5)
} else {
    if (!I.isBlock(context.item) && !I.isEmpty(context.item) && I.getUseAction(context.item) == "none" && I.getUseAction(context.item) != "crossbow") {
        if (I.isIn(context.item, Tags.getVanillaTag("axes")) || I.isOf(context.item, Items.get("minecraft:mace"))) {
            var ptAngleMultiplier = (I.isOf(context.item, Items.get("minecraft:mace")) ? 0.2 : 0.15)
            M.rotateX(mat, -20 * M.sin(context.equipProgress * context.equipProgress * context.equipProgress) + (ptAngle * ptAngleMultiplier), 0.3 * l, -0.3, 0)
        } else {
            M.rotateX(mat, -20 * M.sin(context.equipProgress * context.equipProgress * context.equipProgress) + (ptAngle * 0.05), 0.3 * l, -0.4, 0)
        }
    }
    if ((I.isIn(context.item, Tags.getVanillaTag("axes")) || I.isOf(context.item, Items.get("minecraft:mace"))) && I.getUseAction(context.item) != "crossbow") {
        M.rotateX(mat, (P.getPitch(context.player) * -0.05) + ptAngle * 0.2, 0, -0.2, 0)
    } else if (I.getUseAction(context.item) != "crossbow") {
        M.rotateX(mat, (P.getPitch(context.player) * -0.025) + ptAngle * 0.1, 0, -0.2, 0)
    }
}
// if(not I:isIn(renderedItem, ConventionalItemTags.TOOLS) and not I:isIn(renderedItem, Tags:getVanillaTag("swords"))) then
// 	M:rotateX(mat, 10)
// 	M:rotateZ(mat, 10 * l)
// 	M:rotateY(mat, -30 * l)
// end
// if (context.mainHand) then
// 	local switchItems = M:sin(M:clamp(mainHandSwitch, 0, 0.5) * 3.14);
// 	local switch_fast = M:sin(M:clamp(mainHandSwitch, 0, 0.125) * 12.56);
// 	switchItems = Easings:easeInOutBack(switchItems);
// 	M:rotateX(mat, -70 * switch_fast, 0, -0.2, 0);
// 	M:rotateZ(mat, 40 * switch_fast);
// 	M:rotateZ(mat, -40 * switch_fast);
// 	M:rotateX(mat, 70 * switchItems, 0, -0.2, 0);
// else
// 	local switchItems = M:sin(M:clamp(offHandSwitch, 0, 0.5) * 3.14);
// 	local switch_fast = M:sin(M:clamp(offHandSwitch, 0, 0.125) * 12.56);
// 	switchItems = Easings:easeInOutBack(switchItems);
// 	M:rotateX(mat, -70 * switch_fast, 0, -0.2, 0);
// 	M:rotateZ(mat, 40 * l * switch_fast);
// 	M:rotateZ(mat, -40 * l * switch_fast);
// 	M:rotateX(mat, 70 * switchItems, 0, -0.2, 0);
// end

if ((I.getUseAction(context.item) == "drink" || I.getUseAction(context.item) == "eat" || I.getUseAction(context.item) == "toot_horn") && context.mainHand) {
    M.moveX(mat, 0.02 * l * foodCount)
    M.moveZ(mat, -0.05 * foodCount)
    if (I.getUseAction(context.item) == "eat" || I.getUseAction(context.item) == "toot_horn") {
        M.rotateX(mat, -23 * foodCount * foodCount)
        M.rotateZ(mat, -12 * l * foodCount * foodCount)
    }
    M.rotateY(mat, -50 * l * foodCount * foodCount)

    if (I.getUseAction(context.item) == "drink") {
        M.rotateX(mat, 15 * foodCount * foodCount)
    }
}

if ((I.getUseAction(context.item) == "drink" || I.getUseAction(context.item) == "eat" || I.getUseAction(context.item) == "toot_horn") && !context.mainHand) {
    M.moveX(mat, 0.02 * l * foodCountO)
    M.moveZ(mat, -0.05 * foodCountO)
    if (I.getUseAction(context.item) == "eat" || I.getUseAction(context.item) == "toot_horn") {
        M.rotateX(mat, -23 * foodCountO * foodCountO)
        M.rotateZ(mat, -12 * l * foodCountO * foodCountO)
    }
    M.rotateY(mat, -50 * l * foodCountO * foodCountO)

    if (I.getUseAction(context.item) == "drink") {
        M.rotateX(mat, 15 * foodCountO * foodCountO)
    }
}

if (I.getUseAction(context.item) == "brush" && context.mainHand) {
    M.moveZ(mat, -0.03 * Easings.easeInOutBack(brushCounter))
    M.rotateX(mat, -30 * Easings.easeInOutBack(brushCounter))
    M.rotateZ(mat, 15 * l * M.sin((foodCountSec - 0.5) * 4.14) * Easings.easeInOutBack(brushCounter))
    M.rotateZ(mat, l * brushAngleM)
}
if (I.getUseAction(context.item) == "brush" && !context.mainHand) {
    M.moveZ(mat, -0.03 * Easings.easeInOutBack(brushCounterO))
    M.rotateX(mat, -30 * Easings.easeInOutBack(brushCounterO))
    M.rotateZ(mat, 15 * l * M.sin((foodCountSecO - 0.5) * 4.14) * Easings.easeInOutBack(brushCounterO))
    M.rotateZ(mat, l * brushAngleO)
}

if (I.isIn(context.item, Tags.getVanillaTag("doors"))) {
    M.moveX(mat, 0.1 * l)
    M.moveZ(mat, 0.25)
    M.moveY(mat, -0.35)
    M.rotateZ(mat, -10 * l)
    M.rotateY(mat, -90 * l)
} else if (I.isIn(context.item, Tags.getVanillaTag("beds"))) {
    M.moveZ(mat, 0.17)
    M.rotateY(mat, -35 * l, 0.3 * l, -0.4, 0)
    M.scale(mat, 0.9, 0.9, 0.9)
}

if (I.isOf(context.item, Items.get("minecraft:slime_ball")) || I.isOf(context.item, Items.get("minecraft:slime_block")) || I.isOf(context.item, Items.get("minecraft:honey_block"))) {
    if (I.isOf(context.item, Items.get("minecraft:slime_ball"))) {
        M.moveY(mat, -0.1)
        var scaleY = (fall < 0 ? fall * 0.06 : fall * 0.12)
        M.scale(mat, 1, 1 + scaleY, 1)
        M.moveY(mat, 0.1)
    } else {
        var scaleX_Z = (fall < 0 ? fall * 0.05 : fall * 0.1)
        var scaleY = (fall < 0 ? fall * 0.1 : fall * 0.3)
        M.moveY(mat, -0)
        M.scale(mat, 1 - scaleX_Z, 1 + scaleY, 1 - scaleX_Z)
        M.moveY(mat, 0)

        if (context.bl) {
            M.shear(mat, 0, 0 - ywAngle * 0.006, 0)
        } else {
            M.shear(mat, 0, 0 + ywAngle * 0.006, 0)
        }
    }
}

if (I.isIn(context.item, Tags.getVanillaTag("shovels"))) {
    M.moveX(mat, -0.09 * l)
    M.rotateY(mat, 80 * l)
}
if (context.mainHand) {
    prevPitch = P.getPitch(context.player)
    prevYaw = P.getYaw(context.player)
} else {
    prevPitchO = P.getPitch(context.player)
    prevYawO = P.getYaw(context.player)
}

// context.bl == true -- right
// context.bl == false -- left

var autoFlip = (context.bl ? 1 : -1)

if (I.isOf(context.item, Items.get("minecraft:magma_cream"))) {
    M.scale(mat, 1 - (fall / 5), 1 + (fall / 5), 1)
}

var switch_val = (context.mainHand ? mainHandSwitch : offHandSwitch)
var musicDiscHandTilt;
if (switch_val < 0.65245) {
    musicDiscHandTilt = M.sin(M.clamp(switch_val, 0, 0.16675) * 3.14 * 3)
} else {
    musicDiscHandTilt = M.sin(M.clamp(switch_val, 0.65245, 1) * 4.4 - 1.3)
}
var musicDiscHandJump = M.sin(M.clamp(switch_val, 0.52459, 0.85809) * 3.14 * 3 - 1.8)
// if(I:isIn(renderedItem, Tags:getVanillaTag("music_discs"))) then
// 	M:rotateX(mat, -45 * musicDiscHandTilt);
// 	M:moveZ(mat, -0.2 * musicDiscHandTilt)
// 	M:moveY(mat, -0.05 * Easings:easeInBack(musicDiscHandJump))
// 	M:moveY(mat, 0.1)
// 	M:moveZ(mat, -0.07)
// 	M:rotateY(mat, 360 * Easings:easeInOutBack((context.mainHand and mainHandSwitch) or offHandSwitch), 0, 0, 0.2);
// 	M:rotateX(mat, 90);
// end

var switchAnimationVariable = Easings.easeInBack(M.sin(M.clamp((context.mainHand ? mainHandSwitch : offHandSwitch), 0.09723, 0.60632) * 3.24 * 1.65 - 0.1))
if ((I.isIn(context.item, Tags.getVanillaTag("bundles")) || I.isOf(context.item, Items.get("minecraft:ender_pearl")) || I.isOf(context.item, Items.get("minecraft:ender_eye")) || I.isThrowable(context.item) || I.isIn(context.item, Tags.getFabricTag("music_discs")) || I.isIn(context.item, Tags.getFabricTag("nuggets")) || I.isIn(context.item, Tags.getVanillaTag("skulls"))) && I.getUseAction(context.item) != "trident") {
    M.rotateX(mat, -10 * switchAnimationVariable)
    M.moveY(mat, 0.62 * switchAnimationVariable)
    M.moveY(mat, M.clamp(0.1 * fall, 0, 255))

    var switchEvent = (context.mainHand ? mainHandSwitchEvent : offHandSwitchEvent)

    if (I.isIn(context.item, Tags.getFabricTag("nuggets"))) {
        if (switchEvent) {
            S.playSound("entity.experience_orb.pickup", 0.3)
        }
        M.moveY(mat, -0.07)
        M.rotateX(mat, 360 * Easings.easeInOutBack((context.mainHand ? M.clamp(mainHandSwitch * 1.65, 0, 1) : M.clamp(offHandSwitch * 1.65, 0, 1))), 0, 0.1, 0)
    } else if (I.isIn(context.item, Tags.getFabricTag("music_discs"))) {
        if (switchEvent) {
            S.playSound("entity.context.player.attack.weak", 0.3)
        }
        M.rotateZ(mat, 360 * Easings.easeInOutBack((context.mainHand ? M.clamp(mainHandSwitch * 1.65, 0, 1) : M.clamp(offHandSwitch * 1.65, 0, 1))), -0.1 * l, 0.25, 0)
    } else {
        if (switchEvent) {
            S.playSound("entity.context.player.attack.weak", 0.3)
        }
        var clampedSwitch = (context.mainHand ? M.clamp(mainHandSwitch * 1.2, 0, 1) : M.clamp(offHandSwitch * 1.2, 0, 1))
        M.rotateZ(mat, -7 * l * M.sin(M.clamp(clampedSwitch, 0.0943, 0.66791) * 7.07 * 1.5 - 0.8))
    }
    // M:scale(mat, 1 - (switchAnimationVariable * 0.17), 1 + (switchAnimationVariable * 0.17), 1 - (switchAnimationVariable * 0.17))
}

var easedMapTransition = Easings.easeInOutBack(mapTransition)
var easedMapSmoother = Easings.easeInOutBack(mapSmoother)
var easedMapZoomer = Easings.easeInOutBack(mapZoomer)

if (I.isOf(context.item, Items.get("minecraft:filled_map"))) {
    M.rotateZ(mat, 5 * l * easedMapSmoother)
    M.rotateY(mat, (-40 - (20 * easedMapZoomer)) * l * easedMapSmoother)
    M.rotateZ(mat, 15 * l * easedMapSmoother)
    M.rotateX(mat, -10 * easedMapZoomer * easedMapSmoother)
}
if (I.isOf(context.item, Items.get("minecraft:filled_map"))) {
    var smoother = 1 - easedMapSmoother
    M.moveZ(mat, -0.05 * smoother)
    M.moveY(mat, -0.05 * smoother)
    M.rotateX(mat, -40 * smoother)
    M.rotateY(mat, -10 * l * smoother)
    M.rotateZ(mat, 5 * l * smoother)
} else if (I.shouldTranslateItem(context.item) && !I.isBlock(context.item) && !I.isOf(context.item, Items.get("minecraft:bone")) && I.getUseAction(context.item) != "bow" && I.getUseAction(context.item) != "spear") {
    M.moveX(mat, -0.05 * l)
    M.rotateX(mat, -8)
    M.rotateY(mat, -10 * l)
    M.rotateZ(mat, 6 * l)
}

if (I.isCustomTranslate(context.item)) {
    M.moveX(mat, -0.05 * l)
    M.rotateX(mat, -8)
    M.rotateY(mat, -10 * l)
    M.rotateZ(mat, 6 * l)
}

if (I.isOf(context.item, Items.get("minecraft:shears"))) {
    if (!context.bl) {
        M.moveZ(mat, 0.1)
        M.rotateY(mat, 180)
    }
    M.rotateZ(mat, 45)
}
if (I.isIn(context.item, Tags.getVanillaTag("skulls")) && !I.isOf(context.item, Items.get("minecraft:dragon_head"))) {
    M.moveX(mat, -0.1 * l)
    M.moveY(mat, 0.11)
    M.rotateZ(mat, 15 * l)
    M.rotateY(mat, -85 * l)
    M.rotateX(mat, -55)
    // M:rotateY(mat, 120 * l)
} else if (I.isOf(context.item, Items.get("minecraft:dragon_head"))) {
    M.moveY(mat, 0.25)
    M.rotateZ(mat, 6 * l)
    M.rotateY(mat, 160 * l)
}

if ((context.mainHand ? mainHandSwitchEvent : offHandSwitchEvent)) {
    S.playSound("context.item.armor.equip_leather", 0.2)
}

var ticker = function(particle) {
    particle.dy = particle.dy + 0.005 * context.deltaTime * 30
    particle.dx = particle.dx + 0.005 * M.sin(context.player.age * 0.5) * context.deltaTime * 30
};

if (I.isOf(context.item, Items.get("minecraft:brewing_stand")) || I.isOf(context.item, Items.get("minecraft:redstone_torch")) || I.isOf(context.item, Items.get("minecraft:torch")) || I.isOf(context.item, Items.get("minecraft:lantern")) || I.isOf(context.item, Items.get("minecraft:soul_torch")) || I.isOf(context.item, Items.get("minecraft:soul_lantern"))) {
    if (I.isOf(context.item, Items.get("minecraft:brewing_stand")) || I.isOf(context.item, Items.get("minecraft:torch"))) {
        particleManager.addParticle(context.particles, false, 0.5 * l, 0.6, 0.5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1.3, Texture.of("minecraft", "textures/particle/orange_glow.png"), "ITEM", context.hand, "SPAWN", "ADDITIVE", 0, 200 + (20 * M.sin(P.getAge(context.player) * 0.2)))
    } else if (I.isOf(context.item, Items.get("minecraft:lantern"))) {
        particleManager.addParticle(context.particles, false, 0.45 * l, 0.15, 0.5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1.3, Texture.of("minecraft", "textures/particle/orange_glow.png"), "ITEM", context.hand, "SPAWN", "ADDITIVE", 0, 200 + (20 * M.sin(P.getAge(context.player) * 0.2)))
    } else if (I.isLantern(context.item) && string.find(I.getName(context.item), "copper")) {
        particleManager.addParticle(context.particles, false, 0.45 * l, 0.15, 0.5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1.3, Texture.of("minecraft", "textures/particle/orange_glow.png"), "ITEM", context.hand, "SPAWN", "ADDITIVE", 0, 200 + (20 * M.sin(P.getAge(context.player) * 0.2)))
    } else if (I.isOf(context.item, Items.get("minecraft:soul_torch"))) {
        particleManager.addParticle(context.particles, false, 0.5 * l, 0.6, 0.5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1.3, Texture.of("minecraft", "textures/particle/blue_glow.png"), "ITEM", context.hand, "SPAWN", "ADDITIVE", 0, 110 + (10 * M.sin(P.getAge(context.player) * 0.2)))
    } else if (I.isOf(context.item, Items.get("minecraft:soul_lantern"))) {
        particleManager.addParticle(context.particles, false, 0.45 * l, 0.15, 0.5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1.3, Texture.of("minecraft", "textures/particle/blue_glow.png"), "ITEM", context.hand, "SPAWN", "ADDITIVE", 0, 110 + (10 * M.sin(P.getAge(context.player) * 0.2)))
    } else if (I.isOf(context.item, Items.get("minecraft:redstone_torch"))) {
        particleManager.addParticle(context.particles, false, 0.5 * l, 0.6, 0.5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1.3, Texture.of("minecraft", "textures/particle/red_glow.png"), "ITEM", context.hand, "SPAWN", "ADDITIVE", 0, 110 + (10 * M.sin(P.getAge(context.player) * 0.2)))
    }
}


if (KeyBindManager.isKeyPressed(74)) {
inspectionSpin = inspectionSpin + 0.025 * context.deltaTime * 30
} else {
inspectionSpin = 0
}
inspectionSpin = M.clamp(inspectionSpin, 0, 1)

if ((I.isIn(context.item, Tags.getVanillaTag("swords")) || I.isIn(context.item, Tags.getVanillaTag("pickaxes")) || I.isIn(context.item, Tags.getVanillaTag("axes")) || I.getUseAction(context.item) == "trident") && context.mainHand) {
M.moveX(mat, -0.2 * l * inspectionCounter)
M.rotateX(mat, -360 * Easings.easeInOutBack(inspectionSpin), 0, 0, 0.15)
}
prevAge = P.getAge(context.player)


if (swingCountPrev != P.getSwingCount(context.player) && context.mainHand && I.isOf(context.item, Items.get("minecraft:bell"))) {
S.playSound("block.bell.use", 0.3)
}
swingCountPrev = P.getSwingCount(context.player)


if (I.isOf(context.item, Items.get("minecraft:pink_petals")) || I.isOf(context.item, Items.get("minecraft:wildflowers")) || I.isOf(context.item, Items.get("minecraft:leaf_litter"))) {
var flower = ""
if (I.isOf(context.item, Items.get("minecraft:pink_petals"))) {
flower = "pink_petals"
} else if (I.isOf(context.item, Items.get("minecraft:wildflowers"))) {
flower = "wild_flowers"
} else if (I.isOf(context.item, Items.get("minecraft:leaf_litter"))) {
flower = "leaf_litter"
}

var particle_ticker = function(particle) {
particle.dx = particle.dx + 0.005 * M.sin(P.getAge(context.player) * 0.3) * context.deltaTime * 30
};

if (swingMHandPrev != context.swingMHand && context.mainHand) {
S.playSound("block.leaf_litter.place", 0.7);
var value = Math.random() * 0.3
particleManager.addParticle(context.particles, true, 0.75 * l, -0.2, -0.9, (Math.random() * 0.12 - 0.06) * l, Math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.4, Texture.of("minecraft", "textures/particle/firefly.png"), "SCREEN", context.hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
value = Math.random() * 0.3
particleManager.addParticle(context.particles, true, 0.75 * l, -0.2, -0.9, (Math.random() * 0.12 - 0.06) * l, Math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.4, Texture.of("minecraft", "textures/particle/firefly.png"), "SCREEN", context.hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
//----------------------------------------
particleManager.addParticle(context.particles, true, 0.65 * l, -0.25, -0.9, (Math.random() * 0.12 - 0.06) * l, Math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.3, Texture.of("minecraft", "textures/particle/" + flower + "_1.png"), "SCREEN", context.hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
value = Math.random() * 0.3
particleManager.addParticle(context.particles, true, 0.65 * l, -0.25, -0.9, (Math.random() * 0.12 - 0.06) * l, Math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.3, Texture.of("minecraft", "textures/particle/" + flower + "_1.png"), "SCREEN", context.hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
particleManager.addParticle(context.particles, true, 0.65 * l, -0.25, -0.9, (Math.random() * 0.12 - 0.06) * l, Math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.3, Texture.of("minecraft", "textures/particle/" + flower + "_2.png"), "SCREEN", context.hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
value = Math.random() * 0.3
particleManager.addParticle(context.particles, true, 0.65 * l, -0.25, -0.9, (Math.random() * 0.12 - 0.06) * l, Math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.3, Texture.of("minecraft", "textures/particle/" + flower + "_2.png"), "SCREEN", context.hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
particleManager.addParticle(context.particles, true, 0.65 * l, -0.25, -0.9, (Math.random() * 0.12 - 0.06) * l, Math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.2, Texture.of("minecraft", "textures/particle/" + flower + "_4.png"), "SCREEN", context.hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
value = Math.random() * 0.3
particleManager.addParticle(context.particles, true, 0.65 * l, -0.25, -0.9, (Math.random() * 0.12 - 0.06) * l, Math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.2, Texture.of("minecraft", "textures/particle/" + flower + "_4.png"), "SCREEN", context.hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
} else if (swingOHandPrev != context.swingOHand && !context.mainHand) {
S.playSound("block.leaf_litter.place", 0.7);
var value = Math.random() * 0.3
particleManager.addParticle(context.particles, true, 0.75 * l, -0.2, -0.9, (Math.random() * 0.12 - 0.06) * l, Math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.4, Texture.of("minecraft", "textures/particle/firefly.png"), "SCREEN", context.hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
value = Math.random() * 0.3
particleManager.addParticle(context.particles, true, 0.75 * l, -0.2, -0.9, (Math.random() * 0.12 - 0.06) * l, Math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.4, Texture.of("minecraft", "textures/particle/firefly.png"), "SCREEN", context.hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
//----------------------------------------
particleManager.addParticle(context.particles, true, 0.65 * l, -0.25, -0.9, (Math.random() * 0.12 - 0.06) * l, Math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.3, Texture.of("minecraft", "textures/particle/" + flower + "_1.png"), "SCREEN", context.hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
value = Math.random() * 0.3
particleManager.addParticle(context.particles, true, 0.65 * l, -0.25, -0.9, (Math.random() * 0.12 - 0.06) * l, Math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.3, Texture.of("minecraft", "textures/particle/" + flower + "_1.png"), "SCREEN", context.hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
particleManager.addParticle(context.particles, true, 0.65 * l, -0.25, -0.9, (Math.random() * 0.12 - 0.06) * l, Math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.3, Texture.of("minecraft", "textures/particle/" + flower + "_2.png"), "SCREEN", context.hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
value = Math.random() * 0.3
particleManager.addParticle(context.particles, true, 0.65 * l, -0.25, -0.9, (Math.random() * 0.12 - 0.06) * l, Math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.3, Texture.of("minecraft", "textures/particle/" + flower + "_2.png"), "SCREEN", context.hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
particleManager.addParticle(context.particles, true, 0.65 * l, -0.25, -0.9, (Math.random() * 0.12 - 0.06) * l, Math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.2, Texture.of("minecraft", "textures/particle/" + flower + "_4.png"), "SCREEN", context.hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
value = Math.random() * 0.3
particleManager.addParticle(context.particles, true, 0.65 * l, -0.25, -0.9, (Math.random() * 0.12 - 0.06) * l, Math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.2, Texture.of("minecraft", "textures/particle/" + flower + "_4.png"), "SCREEN", context.hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
}
}

if (context.mainHand) {
swingMHandPrev = context.swingMHand
} else {
swingOHandPrev = context.swingOHand
}
var tags = [
    "copper_golem_statues"
];

var itemIds = [
"minecraft:string",
"minecraft:resin_clump",
"minecraft:vine",
"minecraft:kelp",
"minecraft:seagrass",
"minecraft:iron_bars",
"minecraft:glass_pane",
"minecraft:white_stained_glass_pane",
"minecraft:orange_stained_glass_pane",
"minecraft:magenta_stained_glass_pane",
"minecraft:light_blue_stained_glass_pane",
"minecraft:yellow_stained_glass_pane",
"minecraft:lime_stained_glass_pane",
"minecraft:pink_stained_glass_pane",
"minecraft:gray_stained_glass_pane",
"minecraft:light_gray_stained_glass_pane",
"minecraft:cyan_stained_glass_pane",
"minecraft:purple_stained_glass_pane",
"minecraft:blue_stained_glass_pane",
"minecraft:brown_stained_glass_pane",
"minecraft:green_stained_glass_pane",
"minecraft:red_stained_glass_pane",
"minecraft:black_stained_glass_pane",
"minecraft:ladder",
"minecraft:oak_sign",
"minecraft:spruce_sign",
"minecraft:birch_sign",
"minecraft:jungle_sign",
"minecraft:acacia_sign",
"minecraft:dark_oak_sign",
"minecraft:mangrove_sign",
"minecraft:cherry_sign",
"minecraft:bamboo_sign",
"minecraft:crimson_sign",
"minecraft:warped_sign",
"minecraft:pale_oak_sign",
"minecraft:tripwire_hook",
"minecraft:hopper",
"minecraft:cauldron",
"minecraft:rail",
"minecraft:powered_rail",
"minecraft:detector_rail",
"minecraft:activator_rail",
"minecraft:repeater",
"minecraft:comparator",
"minecraft:twisting_vines",
"minecraft:weeping_vines",
"minecraft:sniffer_egg",
"minecraft:candle",
"minecraft:white_candle",
"minecraft:orange_candle",
"minecraft:magenta_candle",
"minecraft:light_blue_candle",
"minecraft:yellow_candle",
"minecraft:lime_candle",
"minecraft:pink_candle",
"minecraft:gray_candle",
"minecraft:light_gray_candle",
"minecraft:cyan_candle",
"minecraft:purple_candle",
"minecraft:blue_candle",
"minecraft:brown_candle",
"minecraft:green_candle",
"minecraft:red_candle",
"minecraft:black_candle",
"minecraft:frogspawn",
"minecraft:light",
"minecraft:structure_void",
"minecraft:barrier",
"minecraft:carrot",
"minecraft:powder_snow_bucket",
"minecraft:glow_berries",
"minecraft:potato",
"minecraft:sweet_berries",
"minecraft:redstone"
];




// The 'for (let id of itemIds)' loop is translated to 'for _, id in ipairs(itemIds) do'

for (const id of itemIds) {
// Assuming 'renderAsBlock.put' is a method, using the preferred colon syntax for consistency
renderAsBlock.put(id, false)
if (id != "bamboo") {
translateItem.put(id, true)
}
}

for (const id of tags) {
if ((I.isIn(context.item, Tags.getVanillaTag(id)))) {
renderAsBlock.put(I.getName(context.item), false)
}
}



itemSwingSpeed.put('minecraft:trident', 12)
itemSwingSpeed.put('minecraft:iron_spear', 15)
itemSwingSpeed.put('minecraft:copper_spear', 15)
itemSwingSpeed.put('minecraft:diamond_spear', 15)
itemSwingSpeed.put('minecraft:wooden_spear', 15)
itemSwingSpeed.put('minecraft:stone_spear', 15)
itemSwingSpeed.put('minecraft:golden_spear', 15)
itemSwingSpeed.put('minecraft:netherite_spear', 15)
itemSwingSpeed.put('minecraft:mace', 12)


if (I.isIn(context.item, Tags.getVanillaTag('shovels'))) {
itemSwingSpeed.put(I.getName(context.item), 14)
}

//I:setChestOpen(M:clamp(fall / 6, 0, 1))
//I:setShulkerOpen(M:clamp(fall / 6, 0, 1))


//Cyber, Sapling and Axolotl were here :3

var l = (context.bl ? 1 : -1)

var foodCount = Object.prototype.hasOwnProperty.call(__hmi_registry, 'foodCount') ? __hmi_registry['foodCount'] : (0.0);
var foodCountO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'foodCountO') ? __hmi_registry['foodCountO'] : (0.0);

var easedFoodCounter = Easings.easeInQuart((context.mainHand ? foodCount : foodCountO))

// Buckets
if (( I.isOf(context.item, Items.get("minecraft:bucket")) || I.isOf(context.item, Items.get("minecraft:axolotl_bucket")) || I.isOf(context.item, Items.get("minecraft:powder_snow_bucket")) || I.isOf(context.item, Items.get("minecraft:pufferfish_bucket")) || I.isOf(context.item, Items.get("minecraft:tadpole_bucket")) || I.isOf(context.item, Items.get("minecraft:salmon_bucket")) || I.isOf(context.item, Items.get("minecraft:cod_bucket")) || I.isOf(context.item, Items.get("minecraft:tropical_fish_bucket")) || I.isOf(context.item, Items.get("minecraft:water_bucket")) || I.isOf(context.item, Items.get("minecraft:lava_bucket")) )) {
M.moveY(mat, 0.025)
M.moveX(mat, -0 * l)
M.moveZ(mat, -0.1)
M.rotateY(mat, 180)
M.rotateX(mat, -82.5)
M.rotateZ(mat, -20 * l)
}

// Milk Bucket
if (I.isOf(context.item, Items.get("minecraft:milk_bucket"))) {
M.moveY(mat, 0.025)
M.moveX(mat, -0 * l)
M.moveZ(mat, -0.1)
M.rotateY(mat, 180)
M.rotateX(mat, -82.5)
M.rotateZ(mat, -20 * l)
M.rotateX(mat, -0 * easedFoodCounter)
M.rotateZ(mat, 30 * l * easedFoodCounter)
M.rotateY(mat, 0 * l * easedFoodCounter)
M.moveX(mat, 0 * l * easedFoodCounter)
M.moveY(mat, 0.1 * easedFoodCounter)
M.moveZ(mat, 0.02 * easedFoodCounter)
}

// Lava Bucket
if (I.isOf(context.item, Items.get("minecraft:lava_bucket"))) {
particleManager.addParticle(
context.particles,
false,
-0.05 * l,
0,
0,
0,
0,
0,
0,
0,
0,
0,
0,
0,
2,
Texture.of("minecraft", "textures/particle/orange_glow.png"),
"ITEM",
context.hand,
"SPAWN",
"ADDITIVE",
0,
150 + (20 * M.sin(P.getAge(context.player) * 0.2))
)
}

// Frog Buckets (Holld my Frog mod)
if (( I.isOf(context.item, Items.get("bucket_of_frog:frog_bucket_cold")) || I.isOf(context.item, Items.get("bucket_of_frog:frog_bucket_warm")) || I.isOf(context.item, Items.get("bucket_of_frog:frog_bucket_temperate")) )) {
M.moveY(mat, 0.025)
M.moveX(mat, -0 * l)
M.moveZ(mat, -0.1)
M.rotateY(mat, 180)
M.rotateX(mat, -82.5)
M.rotateZ(mat, -20 * l)
}

if (I.getUseAction(context.item) == "trident") {
//M:moveZ(mat, -0.1 * Easings:easeOutBack(M:clamp(tridentM * 1.5, 0, 1)))

M.rotateZ(mat, 170 * l * Easings.easeOutBack(M.clamp((context.mainHand ? tridentM : tridentMO * 1.5), 0, 1)))
M.moveZ(mat, -0.1)
M.rotateY(mat, 40 * l)
}

if (I.getUseAction(context.item) == "spear") {
    M.moveZ(mat, -0.1)
    M.rotateY(mat, 10 * l)
}

var riptideCounter = Object.prototype.hasOwnProperty.call(__hmi_registry, 'riptideCounter') ? __hmi_registry['riptideCounter'] : (0);
var riptideCounterO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'riptideCounterO') ? __hmi_registry['riptideCounterO'] : (0);

if (I.getUseAction(context.item) == "trident") {
M.rotateX(mat, -90 * Easings.easeOutBack(M.sin((context.mainHand ? riptideCounter : riptideCounterO * 3.14))))
M.rotateZ(mat, -45 * l * Easings.easeOutBack(M.sin((context.mainHand ? riptideCounter : riptideCounterO * 3.14))))
}

if (I.isIn(context.item, Tags.getVanillaTag("hanging_signs")) || I.isIn(context.item, Tags.getVanillaTag("doors")) || I.isIn(context.item, Tags.getVanillaTag("skulls")) || I.isIn(context.item, Tags.getVanillaTag("signs"))) {
    applyBlockRotation.put(I.getName(context.item), false)
}

if (I.isBlock(context.item) && applyBlockRotation.getOrDefault(I.getName(context.item), true) && renderAsBlock.getOrDefault(I.getName(context.item), true) && !I.isOf(context.item, Items.get("minecraft:pink_petals")) && !I.isOf(context.item, Items.get("minecraft:leaf_litter")) && !I.isOf(context.item, Items.get("minecraft:wildflowers")) && !I.isOf(context.item, Items.get("minecraft:redstone")) && !I.isOf(context.item, Items.get("minecraft:bell"))) {
M.moveZ(mat, -0.05)
if (!I.isLantern(context.item)) {
M.moveY(mat, -0.15)
M.rotateZ(mat, 6 * l)
M.rotateX(mat, -8)
}
M.rotateY(mat, 25 * l)
M.scale(mat, 1.1, 1.1, 1.1)
}


var easedBow = Easings.easeInOutBack(bowCount)
var easedBowO = Easings.easeInOutBack(bowCountO)
var easedBowSec = Easings.easeOutBack(bowCountSec)
var easedBowSecO = Easings.easeOutBack(bowCountSecO)
var bc = (context.mainHand ? easedBowSec : easedBowSecO)
var b = (context.mainHand ? easedBow : easedBowO)


if (bc < 0.1) {
usingItem.put("minecraft:bow", false)
} else {
usingItem.put("minecraft:bow", true)
}

useDuration.put("minecraft:bow", Easings.cubicEase(bc) * 20)

var easedCrossBowM = Easings.easeOutBack(crossBowM)
var easedCrossBowSecM = Easings.easeOutBack(crossBowSecM)
var easedCrossBowO = Easings.easeOutBack(crossBowO)
var easedCrossBowSecO = Easings.easeOutBack(crossBowSecO)


// Persist the original HMI global.* state between frames.
__hmi_registry['crossBowM'] = crossBowM;
__hmi_registry['swordAttack2'] = swordAttack2;
__hmi_registry['swordAttack'] = swordAttack;
__hmi_registry['crossBowSecM'] = crossBowSecM;
__hmi_registry['crossBowO'] = crossBowO;
__hmi_registry['crossBowSecO'] = crossBowSecO;
__hmi_registry['walk'] = walk;
__hmi_registry['blockRender'] = blockRender;
__hmi_registry['walkSmoother'] = walkSmoother;
__hmi_registry['swimSmoother'] = swimSmoother;
__hmi_registry['swimCounter'] = swimCounter;
__hmi_registry['mainHandSwitch'] = mainHandSwitch;
__hmi_registry['offHandSwitch'] = offHandSwitch;
__hmi_registry['swingCountPrev'] = swingCountPrev;
__hmi_registry['swingOHandPrev'] = swingOHandPrev;
__hmi_registry['swingMHandPrev'] = swingMHandPrev;
__hmi_registry['inspectionCounter'] = inspectionCounter;
__hmi_registry['inspectionSpin'] = inspectionSpin;
__hmi_registry['prevAge'] = prevAge;
__hmi_registry['bowCountO'] = bowCountO;
__hmi_registry['bowCountSecO'] = bowCountSecO;
__hmi_registry['bowCount'] = bowCount;
__hmi_registry['bowCountSec'] = bowCountSec;
__hmi_registry['bowPullSpeed'] = bowPullSpeed;
__hmi_registry['bowPullAngle'] = bowPullAngle;
__hmi_registry['bowPullSpeedO'] = bowPullSpeedO;
__hmi_registry['bowPullAngleO'] = bowPullAngleO;
__hmi_registry['mapSmoother'] = mapSmoother;
__hmi_registry['mapTransition'] = mapTransition;
__hmi_registry['mapZoomer'] = mapZoomer;
__hmi_registry['fall'] = fall;
__hmi_registry['a'] = a;
__hmi_registry['prevPitch'] = prevPitch;
__hmi_registry['prevPitchO'] = prevPitchO;
__hmi_registry['pitchSpeed'] = pitchSpeed;
__hmi_registry['pitchAngle'] = pitchAngle;
__hmi_registry['pitchSpeedO'] = pitchSpeedO;
__hmi_registry['pitchAngleO'] = pitchAngleO;
__hmi_registry['yawSpeedO'] = yawSpeedO;
__hmi_registry['yawAngleO'] = yawAngleO;
__hmi_registry['prevYaw'] = prevYaw;
__hmi_registry['prevYawO'] = prevYawO;
__hmi_registry['yawSpeed'] = yawSpeed;
__hmi_registry['yawAngle'] = yawAngle;
__hmi_registry['foodCount'] = foodCount;
__hmi_registry['foodCountSec'] = foodCountSec;
__hmi_registry['foodCountSecO'] = foodCountSecO;
__hmi_registry['foodCountO'] = foodCountO;
__hmi_registry['brushCounter'] = brushCounter;
__hmi_registry['brushCounterO'] = brushCounterO;
__hmi_registry['shieldDisable'] = shieldDisable;
__hmi_registry['shieldM'] = shieldM;
__hmi_registry['shieldO'] = shieldO;
__hmi_registry['sneak'] = sneak;
__hmi_registry['bundleCounter'] = bundleCounter;
__hmi_registry['brushSpeedM'] = brushSpeedM;
__hmi_registry['brushSpeedO'] = brushSpeedO;
__hmi_registry['brushAngleM'] = brushAngleM;
__hmi_registry['brushAngleO'] = brushAngleO;
__hmi_registry['tridentM'] = tridentM;
__hmi_registry['tridentMO'] = tridentMO;
__hmi_registry['tridentJ'] = tridentJ;
__hmi_registry['tridentJO'] = tridentJO;
__hmi_registry['spearCounterM'] = spearCounterM;
__hmi_registry['spearUsageTime'] = spearUsageTime;
__hmi_registry['canDismountCounter'] = canDismountCounter;
__hmi_registry['canKnockbackCounter'] = canKnockbackCounter;
__hmi_registry['spearCounterO'] = spearCounterO;
__hmi_registry['canDismountCounterO'] = canDismountCounterO;
__hmi_registry['canKnockbackCounterO'] = canKnockbackCounterO;
__hmi_registry['hitImpactCounter'] = hitImpactCounter;
__hmi_registry['hitImpactCounterO'] = hitImpactCounterO;
__hmi_registry['riptideCounter'] = riptideCounter;
__hmi_registry['riptideCounterO'] = riptideCounterO;
