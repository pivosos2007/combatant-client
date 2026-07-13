/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.common;

import combatant.client.config.common.impl.*;

public enum CommonSettingSchemas {
    ;

    public static final TargetFilters TARGET_FILTERS = new TargetFilters();

    public static final CombatPriority COMBAT_PRIORITY = new CombatPriority();

    public static final RotationMovementCorrection ROTATION_MOVEMENT_CORRECTION = new RotationMovementCorrection();
    public static final RotationResetTicks ROTATION_RESET_TICKS = new RotationResetTicks();
    public static final RotationResetThreshold ROTATION_RESET_THRESHOLD = new RotationResetThreshold();
    public static final RotationTiming ROTATION_TIMING = new RotationTiming();
    public static final RotationMode ROTATION_MODE = new RotationMode();
    public static final RotationThroughWalls ROTATION_THROUGH_WALLS = new RotationThroughWalls();

    // Generic repeated combat/action labels.
    public static final CommonSettingSchema PLACE = key("place");
    public static final CommonSettingSchema EXPLODE = key("explode");
    public static final CommonSettingSchema BREAK = key("break");
    public static final CommonSettingSchema TIMING = key("timing");
    public static final CommonSettingSchema CALC_DELAY = key("calc_delay");
    public static final CommonSettingSchema LOW_PLACE_DELAY = key("low_place_delay");
    public static final CommonSettingSchema LOW_EXPLODE_DELAY = key("low_explode_delay");
    public static final CommonSettingSchema AIR_PLACE = key("air_place");
    public static final CommonSettingSchema MIN_DAMAGE = key("min_damage");
    public static final CommonSettingSchema FACEPLACE_HEALTH = key("faceplace_health");
    public static final CommonSettingSchema MAX_SELF_DAMAGE = key("max_self_damage");
    public static final CommonSettingSchema SELF_PREDICT_TICKS = key("self_predict_ticks");
    public static final CommonSettingSchema UNSAFE_DIMENSION_ONLY = key("unsafe_dimension_only");
    public static final CommonSettingSchema PAUSE_MINING = key("pause_mining");
    public static final CommonSettingSchema PAUSE_EATING = key("pause_eating");
    public static final CommonSettingSchema RENDER_SELF_DAMAGE = key("render_self_damage");
    public static final CommonSettingSchema RENDER_DAMAGE = key("render_damage");
    public static final CommonSettingSchema FILL_COLOR = key("fill_color");
    public static final CommonSettingSchema LINE_COLOR = key("line_color");
    public static final CommonSettingSchema LINE_WIDTH = key("line_width");
    public static final CommonSettingSchema TEXT_COLOR = key("text_color");
    public static final CommonSettingSchema SLIDE_DELAY = key("slide_delay");
    public static final CommonSettingSchema FADE_TIME = key("fade_time");
    public static final CommonSettingSchema MODE = key("mode");

    // Sprint Reset
    public static final CommonSettingSchema COMBAT_CRITICALS = key("combat.criticals");
    public static final CommonSettingSchema COMBAT_SPRINT_RESET = key("combat.sprint_reset");
    public static final CommonSettingSchema COMBAT_WHEN_SPRINTING = key("combat.when_sprinting");
    public static final CommonSettingSchema COMBAT_STOP_SPRINTING = key("combat.stop_sprinting");
    public static final CommonSettingSchema COMBAT_ATTACK_MODE = key("combat.attack_mode");
    public static final CommonSettingSchema COMBAT_CPS_MIN = key("combat.cps_min");
    public static final CommonSettingSchema COMBAT_CPS_MAX = key("combat.cps_max");
    public static final CommonSettingSchema COMBAT_PROTOCOL = key("combat.protocol");
    public static final CombatProtocolHeuristicSources COMBAT_PROTOCOL_HEURISTICS = new CombatProtocolHeuristicSources();
    public static final CommonSettingSchema COMBAT_RANGE = key("combat.range", "target_range", "range");
    public static final CommonSettingSchema COMBAT_REACH_MODE = key("combat.reach_mode");
    public static final CommonSettingSchema COMBAT_RANGE_INCREMENT = key("combat.range_increment");
    public static final CommonSettingSchema COMBAT_WALL_RANGE = key("combat.wall_range");
    public static final CommonSettingSchema COMBAT_FOV = key("combat.fov", "target_fov", "fov");
    public static final CommonSettingSchema COMBAT_CORRECTION_TYPE = key("combat.correction_type");
    public static final CommonSettingSchema COMBAT_RAYCAST = key("combat.raycast");
    public static final CommonSettingSchema COMBAT_BACKTRACK_TARGET_MODE = key("combat.backtrack.target_mode", "target_mode");
    public static final CommonSettingSchema COMBAT_BACKTRACK_RANGE_MIN = key("combat.backtrack.range_min", "range_min");
    public static final CommonSettingSchema COMBAT_BACKTRACK_RANGE_MAX = key("combat.backtrack.range_max", "range_max");
    public static final CommonSettingSchema COMBAT_BACKTRACK_DELAY_MIN = key("combat.backtrack.delay_min", "delay_min");
    public static final CommonSettingSchema COMBAT_BACKTRACK_DELAY_MAX = key("combat.backtrack.delay_max", "delay_max");
    public static final CommonSettingSchema COMBAT_BACKTRACK_NEXT_DELAY_MIN = key("combat.backtrack.next_delay_min", "next_delay_min");
    public static final CommonSettingSchema COMBAT_BACKTRACK_NEXT_DELAY_MAX = key("combat.backtrack.next_delay_max", "next_delay_max");
    public static final CommonSettingSchema COMBAT_BACKTRACK_TRACKING_BUFFER = key("combat.backtrack.tracking_buffer", "tracking_buffer");
    public static final CommonSettingSchema COMBAT_BACKTRACK_CHANCE = key("combat.backtrack.chance", "chance");
    public static final CommonSettingSchema COMBAT_BACKTRACK_PAUSE_ON_HURT_TIME = key("combat.backtrack.pause_on_hurt_time", "pause_on_hurt_time");
    public static final CommonSettingSchema COMBAT_BACKTRACK_HURT_TIME = key("combat.backtrack.hurt_time", "hurt_time");
    public static final CommonSettingSchema COMBAT_BACKTRACK_LAST_ATTACK_TIME = key("combat.backtrack.last_attack_time", "last_attack_time");
    public static final CommonSettingSchema COMBAT_BACKTRACK_RENDER = key("combat.backtrack.render", "render");
    public static final CommonSettingSchema COMBAT_BACKTRACK_LINE_WIDTH = key("combat.backtrack.line_width", "line_width");
    public static final CommonSettingSchema COMBAT_WEAPON = key("combat.weapon");
    public static final CommonSettingSchema COMBAT_OBJECTIVE = key("combat.objective");
    public static final CommonSettingSchema COMBAT_ON_ITEM_USE = key("combat.on_item_use");
    public static final CommonSettingSchema COMBAT_CRYSTAL_DISTANCE = key("combat.crystal_distance");
    public static final CommonSettingSchema COMBAT_BREAK_SHIELDS = key("combat.break_shields");
    public static final CommonSettingSchema INVENTORY_SEARCH_SCOPE = key("inventory.search_scope", "swap_scope");
    public static final CommonSettingSchema INVENTORY_SWAP_VISIBILITY = key("inventory.swap_visibility", "swap_visibility");
    public static final CommonSettingSchema INVENTORY_RESTORE_ITEM = key("inventory.restore_item", "restore_item");
    public static final CommonSettingSchema INVENTORY_PREFER_HOTBAR = key("inventory.prefer_hotbar", "prefer_hotbar");
    public static final CommonSettingSchema INTERACTION_IGNORE_OPEN_INVENTORY = key("interaction.ignore_open_inventory", "ignore_open_inventory");
    public static final CommonSettingSchema COMBAT_ONLY_PLAYERS = key("combat.only_players");
    public static final CommonSettingSchema COMBAT_ONLY_BLOCKING = key("combat.only_blocking");
    public static final CommonSettingSchema COMBAT_MIN_FALL_DISTANCE = key("combat.min_fall_distance", "min_fall_distance");
    public static final CommonSettingSchema COMBAT_RESPECT_COOLDOWN = key("combat.respect_cooldown");
    public static final CommonSettingSchema ITEMS_PROJECTILES = key("items.projectiles");
    public static final CommonSettingSchema ITEMS_SAVE_UNENCHANTED = key("items.save_unenchanted");
    //Placement
    public static final CommonSettingSchema PLACEMENT_RANGE = key("placement.range", "place_range");
    public static final CommonSettingSchema PLACEMENT_WALL_RANGE = key("placement.wall_range", "wall_range");
    public static final CommonSettingSchema PLACEMENT_BREAK_RANGE = key("placement.break_range", "break_range");
    public static final CommonSettingSchema PLACEMENT_BREAK_WALL_RANGE = key("placement.break_wall_range", "break_wall_range");
    public static final CommonSettingSchema PLACEMENT_MODE = key("placement.mode", "placement_mode");
    public static final CommonSettingSchema PLACEMENT_SPEED = key("placement.speed", "placement_speed");
    public static final CommonSettingSchema PLACEMENT_DELAY = key("placement.delay", "place_delay");
    public static final CommonSettingSchema PLACEMENT_BREAK_DELAY = key("placement.break_delay", "break_delay", "explode_delay");
    public static final CommonSettingSchema PLACEMENT_PREDICTION = key("placement.prediction", "predict_ticks");
    public static final CommonSettingSchema PLACEMENT_RENDER = key("placement.render", "render");
    public static final CommonSettingSchema PLACEMENT_RENDER_MODE = key("placement.render_mode", "render_mode");
    public static final CommonSettingSchema PLACEMENT_ROTATION_TIMING = key("placement.rotation_timing", "rotation.rotation_timing", "rotation_timing");
    public static final CommonSettingSchema PLACEMENT_MOVEMENT_CORRECTION = key("placement.movement_correction", "rotation.movement_correction", "movement_correction");
    public static final CommonSettingSchema PLACEMENT_ROTATION_RESET_TICKS = key("placement.rotation_reset_ticks", "rotation.rotation_reset_ticks", "rotation_reset_ticks");
    public static final CommonSettingSchema PLACEMENT_ROTATION_RESET_THRESHOLD = key("placement.rotation_reset_threshold", "rotation.rotation_reset_threshold", "rotation_reset_threshold");
    public static final CommonSettingSchema INVENTORY_SWAP_MODE = key("inventory.swap_mode", "swap_mode");
    public static final CommonSettingSchema PLAYER_HEALTH_THRESHOLD = key("player.health_threshold", "pause_health", "health_threshold");
    public static final CommonSettingSchema PLAYER_ELYTRA_HEALTH = key("player.elytra_health");
    public static final CommonSettingSchema PLAYER_FALL_CHECK = key("player.fall_check");
    public static final CommonSettingSchema PLAYER_USE_RELATIONS = key("player.use_relations");
    public static final CommonSettingSchema TOTEM_POP_COUNTER = key("totem_pop.counter", "totem_pop_counter");
    public static final CommonSettingSchema TOTEM_POP_RESET_SECONDS = key("totem_pop.reset_seconds", "totem_pop_reset_seconds");
    public static final CommonSettingSchema PLAYER_FAKELAG_MODE = key("player.fakelag.mode");
    public static final CommonSettingSchema PLAYER_FAKELAG_RANGE_MIN = key("player.fakelag.range_min", "min_range", "range_min");
    public static final CommonSettingSchema PLAYER_FAKELAG_RANGE_MAX = key("player.fakelag.range_max", "max_range", "range_max");
    public static final CommonSettingSchema PLAYER_FAKELAG_DELAY_MIN = key("player.fakelag.delay_min", "delay_min");
    public static final CommonSettingSchema PLAYER_FAKELAG_DELAY_MAX = key("player.fakelag.delay_max", "delay_max");
    public static final CommonSettingSchema PLAYER_FAKELAG_RECOIL_TIME = key("player.fakelag.recoil_time", "recoil_time");
    public static final FakeLagFlushOn PLAYER_FAKELAG_FLUSH_ON = new FakeLagFlushOn();
    // Elytra target / fireworks
    public static final CommonSettingSchema ELYTRA_HITBOX_EXPAND = key("elytra.hitbox_expand");
    public static final CommonSettingSchema ELYTRA_EXPAND_WHEN_READY = key("elytra.expand_when_ready");
    public static final CommonSettingSchema ELYTRA_FIREWORK_SPEED = key("elytra.firework_speed", "firework_speed");
    public static final CommonSettingSchema ELYTRA_TARGET_MUST_FLY = key("elytra.target_must_fly");
    public static final CommonSettingSchema ELYTRA_PREPARE_ROTATIONS = key("elytra.prepare_rotations", "prepare_rotations");
    public static final CommonSettingSchema ELYTRA_VELOCITY_TUNE = key("elytra.velocity_tune");
    public static final CommonSettingSchema ELYTRA_CUSTOM_VELOCITY_TABLE = key("elytra.custom_velocity_table");
    public static final CommonSettingSchema ELYTRA_FIREWORK_SILENT_SWAP = key("elytra.firework_silent_swap", "silent_swap", "firework_silent_swap");
    // ESP / world scan
    public static final CommonSettingSchema ESP_SCAN_MODE = key("esp.scan_mode");
    public static final CommonSettingSchema ESP_LIMIT_DISTANCE = key("esp.limit_distance");
    public static final CommonSettingSchema ESP_MAX_DISTANCE = key("esp.max_distance", "max_distance");
    public static final CommonSettingSchema ESP_SCAN_DELAY = key("esp.scan_delay", "scan_delay");
    public static final CommonSettingSchema ESP_MAX_TARGETS = key("esp.max_targets", "max_targets");
    public static final CommonSettingSchema ESP_TRANSPARENT_BLOCKS = key("esp.transparent_blocks");
    public static final CommonSettingSchema ESP_USE_TRACERS = key("esp.use_tracers");
    public static final CommonSettingSchema ESP_LINE_WIDTH = key("esp.line_width", "line_width");
    public static final CommonSettingSchema ESP_OUTLINE_SHAPE = key("esp.outline_shape", "outline_shape");
    public static final CommonSettingSchema ESP_BOX_MODE = key("esp.box_mode");
    public static final CommonSettingSchema ESP_HEALTH_BAR = key("esp.health_bar");
    public static final CommonSettingSchema ESP_HEALTH_BAR_GRADIENT = key("esp.health_bar_gradient");
    public static final CommonSettingSchema ESP_HEALTH_BAR_DISTANCE = key("esp.health_bar_distance", "health_bar_distance");
    // AngleSmooth generic
    public static final CommonSettingSchema ANGLESMOOTH_HORIZONTAL_MIN = key("rotation.anglesmooth.horizontal_min");
    public static final CommonSettingSchema ANGLESMOOTH_HORIZONTAL_MAX = key("rotation.anglesmooth.horizontal_max");
    public static final CommonSettingSchema ANGLESMOOTH_VERTICAL_MIN = key("rotation.anglesmooth.vertical_min");
    public static final CommonSettingSchema ANGLESMOOTH_VERTICAL_MAX = key("rotation.anglesmooth.vertical_max");
    // Sigmoid
    public static final CommonSettingSchema ANGLESMOOTH_STEEPNESS = key("rotation.anglesmooth.steepness");
    public static final CommonSettingSchema ANGLESMOOTH_MIDPOINT = key("rotation.anglesmooth.midpoint");
    // Smart
    public static final CommonSettingSchema ANGLESMOOTH_YAW_MIN = key("rotation.anglesmooth.yaw_min");
    public static final CommonSettingSchema ANGLESMOOTH_YAW_MAX = key("rotation.anglesmooth.yaw_max");
    public static final CommonSettingSchema ANGLESMOOTH_PITCH_MIN = key("rotation.anglesmooth.pitch_min");
    public static final CommonSettingSchema ANGLESMOOTH_PITCH_MAX = key("rotation.anglesmooth.pitch_max");
    public static final CommonSettingSchema ANGLESMOOTH_SNAP_THRESHOLD = key("rotation.anglesmooth.snap_threshold");
    public static final CommonSettingSchema ANGLESMOOTH_JITTER_YAW = key("rotation.anglesmooth.jitter_yaw");
    public static final CommonSettingSchema ANGLESMOOTH_JITTER_PITCH = key("rotation.anglesmooth.jitter_pitch");
    public static final CommonSettingSchema ANGLESMOOTH_DECELERATE_ENABLED = key("rotation.anglesmooth.decelerate_enabled");
    public static final CommonSettingSchema ANGLESMOOTH_DECELERATE_ANGLE = key("rotation.anglesmooth.decelerate_angle");
    public static final CommonSettingSchema ANGLESMOOTH_DECELERATE_MIN_FACTOR = key("rotation.anglesmooth.decelerate_min_factor");
    // Lazy aim
    public static final CommonSettingSchema LAZY_AIM_ENABLED = key("rotation.lazy_aim.enabled");
    public static final CommonSettingSchema LAZY_AIM_THRESHOLD = key("rotation.lazy_aim.threshold");
    public static final CommonSettingSchema LAZY_AIM_FOLLOW_FACTOR = key("rotation.lazy_aim.follow_factor");
    // Shake aim
    public static final CommonSettingSchema SHAKE_AIM_ENABLED = key("rotation.shake_aim.enabled");
    public static final CommonSettingSchema SHAKE_AIM_YAW_AMPLITUDE = key("rotation.shake_aim.yaw_amplitude");
    public static final CommonSettingSchema SHAKE_AIM_PITCH_AMPLITUDE = key("rotation.shake_aim.pitch_amplitude");
    public static final CommonSettingSchema SHAKE_AIM_SPEED = key("rotation.shake_aim.speed");
    // ShortStop
    public static final CommonSettingSchema SHORT_STOP_ENABLED = key("rotation.short_stop.enabled");
    public static final CommonSettingSchema SHORT_STOP_RATE = key("rotation.short_stop.rate");
    public static final CommonSettingSchema SHORT_STOP_DURATION_MIN = key("rotation.short_stop.duration_min");
    public static final CommonSettingSchema SHORT_STOP_DURATION_MAX = key("rotation.short_stop.duration_max");
    // Fail
    public static final CommonSettingSchema FAIL_ENABLED = key("rotation.fail.enabled");
    public static final CommonSettingSchema FAIL_RATE = key("rotation.fail.rate");
    public static final CommonSettingSchema FAIL_FACTOR = key("rotation.fail.factor");
    public static final CommonSettingSchema FAIL_STRENGTH_HORIZONTAL_MIN = key("rotation.fail.strength_horizontal_min");
    public static final CommonSettingSchema FAIL_STRENGTH_HORIZONTAL_MAX = key("rotation.fail.strength_horizontal_max");
    public static final CommonSettingSchema FAIL_STRENGTH_VERTICAL_MIN = key("rotation.fail.strength_vertical_min");
    public static final CommonSettingSchema FAIL_STRENGTH_VERTICAL_MAX = key("rotation.fail.strength_vertical_max");
    public static final CommonSettingSchema FAIL_TRANSITION_MIN = key("rotation.fail.transition_min");
    public static final CommonSettingSchema FAIL_TRANSITION_MAX = key("rotation.fail.transition_max");
    public static final CommonSettingSchema ANTICHEAT_MODE = key("anticheat.mode");
    public static final CommonSettingSchema RENDER_COLOR_MODE = key("render.color_mode", "color_mode");
    public static final CommonSettingSchema RENDER_COLOR_SPEED = key("render.color_speed", "color_speed");
    public static final CommonSettingSchema RENDER_PRIMARY_COLOR = key("render.primary_color", "primary_color");
    public static final CommonSettingSchema RENDER_SECONDARY_COLOR = key("render.secondary_color", "secondary_color");
    public static final CommonSettingSchema RENDER_DEPTH_TEST = key("render.depth_test", "depth_test");
    public static final CommonSettingSchema RENDER_SCALE = key("render.scale", "scale");

    private static CommonSettingSchema key(String key, String... aliases) {
        return new CommonKey(key, aliases);
    }
}
