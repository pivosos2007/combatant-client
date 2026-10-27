/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player.schematic;

import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.schematic.Schematic;
import combatant.client.util.schematic.SchematicFormats;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Grim ({@code FarPlace}, {@code PositionPlace}, {@code RotationPlace}, {@code FabricatedPlace},
 * {@code MultiActions}, {@code PostOrder}) and Vulcan ({@code Scaffold}, {@code BadPackets 5/I/K/M/N/P/R/X})
 * invariants of the schematic builder's click, sneak and container rules.
 */
class SchematicBuilderAnticheatTest {

    private static final Path SOURCES = Path.of("src", "main", "java", "combatant", "client", "features", "module",
            "modules", "player", "schematic");
    private static final Path SCHEMATICS = Path.of("run", "schematics");
    private static final Pattern ACTIONS = Pattern.compile(
            "useItemOn|startDestroyBlock|continueDestroyBlock|stopDestroyBlock|handleContainerInput|closeContainer"
                    + "|selectHotbar|\\.swing\\(|useWithRotation|tryPlace|tryAdjust|placeHelper");
    private static final double REACH = 4.5;
    private static final BlockPos SUPPORT = new BlockPos(10, 64, 10);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ------------------------------------------------------------------ source structure

    private static String source(String file) throws IOException {
        return Files.readString(SOURCES.resolve(file));
    }

    /** The body of the first method whose declaration contains {@code signature}, braces matched. */
    private static String body(String source, String signature) {
        int at = source.indexOf(signature);
        assertTrue(at >= 0, "missing " + signature);
        int open = source.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return source.substring(open, i + 1);
        }
        throw new AssertionError("unbalanced " + signature);
    }

    @Test
    void noActionPacketIsSentFromTheClientTick() throws IOException {
        String builder = source("SchematicBuilder.java");
        for (String method : List.of("public void onTick()", "private void onMovementInput(",
                "private void checkGateStall(", "private void pruneAttention(")) {
            Matcher m = ACTIONS.matcher(body(builder, method));
            assertFalse(m.find(), () -> method + " must not act (Grim PostOrder / Vulcan BadPackets M/N/P/R/X)");
        }
        String mover = source("BuildMover.java");
        for (String method : List.of("void trackDistance(", "void applyInput(")) {
            Matcher m = ACTIONS.matcher(body(mover, method));
            assertFalse(m.find(), () -> method + " must not act");
        }
    }

    @Test
    void builderActionsLiveInRotationEventHandlers() throws IOException {
        String builder = source("SchematicBuilder.java");
        assertTrue(builder.replace("\r", "").contains("@EventHandler(priority = -15)\n    private void onRotationUpdate(RotationUpdateEvent"));
        String handler = body(builder, "private void onRotationUpdate(");
        assertTrue(handler.contains("pre()") && handler.contains("post()"));
        // Every action call sits in a method only reachable from pre()/post(): none of the entry points other
        // than the rotation handler may call them (checked for each public/@Override entry point).
        for (String entry : List.of("public void onEnable()", "public void onDisable()")) {
            assertFalse(ACTIONS.matcher(body(builder, entry)).find(), entry);
        }
        String mover = source("BuildMover.java");
        assertFalse(mover.contains("useItemOn"), "the mover never places; clicks go through the builder's post()");
    }

    @Test
    void chestIsOpenedAgainstAVisibleFaceNotAFixedTopFace() throws IOException {
        String builder = source("SchematicBuilder.java");
        String post = body(builder, "private void post()");
        assertFalse(post.contains("Direction.UP"), "restock click must not use a fixed face");
        assertFalse(post.contains("atCenterOf"), "restock click must not use a fixed point");
        assertTrue(post.contains("BuildPlanner.trace(") && post.contains("BuildPlanner.legalClick("));
        String plan = body(builder, "private boolean planChestRestock(");
        assertTrue(plan.contains("BuildPlanner.planClick("));
        assertFalse(plan.contains("restockChestTicks >= 6"), "no open on timeout without a ray on the chest");
        assertTrue(body(builder, "private boolean chestRayOnFace(").contains("BuildPlanner.matches("));
    }

    @Test
    void noActionWhileUsingAnItemOrInAContainer() throws IOException {
        String builder = source("SchematicBuilder.java");
        String canWork = body(builder, "private boolean canWork(");
        assertTrue(canWork.contains("isUsingItem()") && canWork.contains("containerMenu != player.inventoryMenu"));
        assertTrue(body(builder, "private boolean tryPlace(").contains("containerMenu != player.inventoryMenu"));
        assertTrue(body(builder, "private void tryAdjust(").contains("containerMenu != player.inventoryMenu"));
        String input = body(builder, "private void onMovementInput(");
        assertTrue(input.contains("setSprint(false)"), "no sprint while a container is open or clicked");
        assertTrue(body(source("BuildMover.java"), "private void mine(").contains("isUsingItem()"));
    }

    @Test
    void containerClicksWaitForOpenAndKeepTheDelay() throws IOException {
        String tick = body(source("SchematicBuilder.java"), "private void tickOpenRestockChest(");
        int ready = tick.indexOf("ContainerPacing.ready(");
        int click = tick.indexOf("handleContainerInput(");
        int close = tick.indexOf("closeContainer()");
        assertTrue(ready >= 0 && ready < click && ready < close, "gate precedes every container packet");
        assertTrue(tick.contains("ContainerPacing.spacing(delay.get(), donutRules())"));
    }

    // ------------------------------------------------------------------ container pacing

    @Test
    void firstQuickMoveComesTwoTicksAfterOpenAndClicksAreSpaced() {
        int opened = 0;
        int cooldown = 0;
        int lastClick = -100;
        int firstClick = -1;
        for (int tick = 0; tick < 40; tick++) {
            opened++;
            if (cooldown > 0) cooldown--;
            if (!SchematicBuilder.ContainerPacing.ready(opened, cooldown)) continue;
            if (firstClick < 0) firstClick = tick;
            assertTrue(tick - lastClick >= 2, "clicks at least 2 ticks apart");
            lastClick = tick;
            cooldown = SchematicBuilder.ContainerPacing.spacing(0, true);
        }
        assertEquals(2, firstClick, "two ticks pass before the first click");
        assertEquals(2, SchematicBuilder.ContainerPacing.spacing(0, true));
        assertEquals(5, SchematicBuilder.ContainerPacing.spacing(5, true));
        assertEquals(1, SchematicBuilder.ContainerPacing.spacing(0, false));
        assertFalse(SchematicBuilder.ContainerPacing.ready(1, 0));
        assertFalse(SchematicBuilder.ContainerPacing.ready(2, 0));
        assertTrue(SchematicBuilder.ContainerPacing.ready(3, 0));
        assertFalse(SchematicBuilder.ContainerPacing.ready(10, 1));
    }

    // ------------------------------------------------------------------ sneak pacing (Vulcan BadPackets K)

    @Test
    void startSneakingPacketsAreNeverCloserThanTwoTicks() {
        SchematicBuilder.SneakPacing pacing = new SchematicBuilder.SneakPacing();
        boolean previous = false;
        int lastStart = -100;
        int lastStop = -100;
        int starts = 0;
        // A worst case caller: wants sneak on every other tick, then every tick, then never.
        for (int tick = 0; tick < 200; tick++) {
            boolean wants = tick < 60 ? tick % 2 == 0 : tick < 120 || tick % 7 == 0 && tick < 150;
            boolean sneak = pacing.apply(wants, tick);
            if (sneak && !previous) {
                assertTrue(tick - lastStart >= 2, "START_SNEAKING gap at tick " + tick);
                lastStart = tick;
                starts++;
            }
            if (!sneak && previous) {
                assertTrue(tick - lastStart >= SchematicBuilder.SneakPacing.MIN_HOLD_TICKS, "held at least 2 ticks");
                lastStop = tick;
            }
            previous = sneak;
        }
        assertTrue(starts > 0 && lastStop > 0);
        assertTrue(SchematicBuilder.SneakPacing.MIN_START_GAP_TICKS >= 2 && SchematicBuilder.SneakPacing.MIN_HOLD_TICKS >= 2);
    }

    @Test
    void sneakStartsOnlyWhenWantedAndEndsOnlyWhenNotWanted() {
        SchematicBuilder.SneakPacing pacing = new SchematicBuilder.SneakPacing();
        assertFalse(pacing.apply(false, 0));
        assertTrue(pacing.apply(true, 1));
        assertTrue(pacing.apply(false, 2), "released too early: still held");
        assertFalse(pacing.apply(false, 3));
        assertFalse(pacing.apply(true, 3), "restart gap not elapsed");
        assertTrue(pacing.apply(true, 4));
        pacing.reset();
        assertFalse(pacing.holding());
    }

    // ------------------------------------------------------------------ cursor / ray / reach

    private static Vec3 faceCentre(BlockPos pos, Direction face) {
        return Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
    }

    private static boolean legal(Vec3 eyes, BlockPos support, Direction face, Vec3 hit) {
        return BuildPlanner.legalClick(eyes, Rotation.lookingAt(hit, eyes), support, Shapes.block(), face, hit, REACH);
    }

    @Test
    void cursorMustStayInsideTheClickedFace() {
        VoxelShape block = Shapes.block();
        for (Direction face : Direction.values()) {
            Vec3 centre = faceCentre(SUPPORT, face);
            assertTrue(BuildPlanner.cursorValid(SUPPORT, face, centre, block), face.toString());
            // Off the face plane (Grim FabricatedPlace) or outside [0, 1] on any axis.
            Vec3 inside = centre.subtract(face.getStepX() * 0.2, face.getStepY() * 0.2, face.getStepZ() * 0.2);
            assertFalse(BuildPlanner.cursorValid(SUPPORT, face, inside, block), face + " inside the block");
            for (Direction other : Direction.values()) {
                Vec3 out = centre.add(other.getStepX() * 1.01, other.getStepY() * 1.01, other.getStepZ() * 1.01);
                assertFalse(BuildPlanner.cursorValid(SUPPORT, face, out, block), face + " out along " + other);
            }
        }
        // The cursors the planner samples: centre +/- 0.44 across each face stay strictly inside.
        for (Direction face : Direction.values()) {
            for (double u : new double[]{-0.44, -0.3, -0.15, 0.0, 0.15, 0.3, 0.44}) {
                for (double v : new double[]{-0.44, -0.25, 0.0, 0.25, 0.44}) {
                    Vec3 centre = faceCentre(SUPPORT, face);
                    Vec3 point = switch (face.getAxis()) {
                        case X -> centre.add(0, v, u);
                        case Y -> centre.add(u, 0, v);
                        case Z -> centre.add(u, v, 0);
                    };
                    assertTrue(BuildPlanner.cursorValid(SUPPORT, face, point, block));
                }
            }
        }
    }

    @Test
    void clickNeedsTheRayOnTheClickedFaceWithinReach() {
        for (Direction face : Direction.values()) {
            Vec3 hit = faceCentre(SUPPORT, face);
            Vec3 eyes = hit.add(face.getStepX() * 2.0, face.getStepY() * 2.0, face.getStepZ() * 2.0);
            assertTrue(legal(eyes, SUPPORT, face, hit), face + " from the front");
            // Same click from behind the face: the ray enters through another face (Grim RotationPlace).
            Vec3 behind = hit.subtract(face.getStepX() * 2.0, face.getStepY() * 2.0, face.getStepZ() * 2.0);
            assertFalse(legal(behind, SUPPORT, face, hit), face + " from behind");
            // A rotation that looks elsewhere does not click the face.
            Rotation away = Rotation.lookingAt(hit.add(3.0, 3.0, 3.0), eyes);
            assertFalse(BuildPlanner.legalClick(eyes, away, SUPPORT, Shapes.block(), face, hit, REACH));
            // Far Place: beyond the 4.5 interaction range.
            Vec3 far = hit.add(face.getStepX() * 5.0, face.getStepY() * 5.0, face.getStepZ() * 5.0);
            assertFalse(legal(far, SUPPORT, face, hit), face + " too far");
            // The rotation-free mode has no ray, but cursor and reach still apply.
            assertTrue(BuildPlanner.legalClick(eyes, null, SUPPORT, Shapes.block(), face, hit, REACH));
            assertFalse(BuildPlanner.legalClick(far, null, SUPPORT, Shapes.block(), face, hit, REACH));
        }
    }

    @Test
    void eyesBelowTheChestTopCannotClickItsTopFace() {
        // The old restock click: Direction.UP at centre + 0.5 regardless of where the eyes were.
        VoxelShape chest = Blocks.CHEST.defaultBlockState().getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        AABB bounds = chest.bounds();
        Vec3 top = new Vec3(SUPPORT.getX() + 0.5, SUPPORT.getY() + bounds.maxY, SUPPORT.getZ() + 0.5);
        Vec3 below = new Vec3(top.x + 2.0, top.y - 1.2, top.z);
        assertFalse(BuildPlanner.legalClick(below, Rotation.lookingAt(top, below), SUPPORT, chest, Direction.UP, top, REACH));
        Vec3 above = new Vec3(top.x + 1.0, top.y + 1.0, top.z);
        assertTrue(BuildPlanner.legalClick(above, Rotation.lookingAt(top, above), SUPPORT, chest, Direction.UP, top, REACH));
        // The cube-top point the old code used lies above the chest's lower outline: wrong plane.
        Vec3 cubeTop = Vec3.atCenterOf(SUPPORT).add(0.0, 0.5, 0.0);
        assertTrue(bounds.maxY < 1.0 - 1.0e-6, "chest is lower than a full block");
        assertFalse(BuildPlanner.cursorValid(SUPPORT, Direction.UP, cubeTop, chest));
    }

    @Test
    void anotherBlockInFrontOfTheFaceIsNotTheClickedFace() {
        // rayHitsFace only reports the support's own outline; a block in front is caught by trace()/matches(),
        // which the builder runs first. Here: the ray through the near neighbour's space never reaches the far face.
        Vec3 hit = faceCentre(SUPPORT, Direction.UP);
        Vec3 eyes = hit.add(0.0, 2.0, 0.0);
        assertTrue(BuildPlanner.rayHitsFace(eyes, Rotation.lookingAt(hit, eyes), SUPPORT, Shapes.block(), Direction.UP, REACH));
        assertFalse(BuildPlanner.rayHitsFace(eyes, Rotation.lookingAt(hit, eyes), SUPPORT, Shapes.block(), Direction.DOWN, REACH));
        assertFalse(BuildPlanner.rayHitsFace(eyes, null, SUPPORT, Shapes.block(), Direction.UP, REACH));
    }

    // ------------------------------------------------------------------ real schematics

    private static List<Path> schematics() throws IOException {
        if (!Files.isDirectory(SCHEMATICS)) return List.of();
        try (Stream<Path> stream = Files.list(SCHEMATICS)) {
            return stream.filter(path -> {
                String name = path.getFileName().toString().toLowerCase();
                return name.endsWith(".schem") || name.endsWith(".litematic");
            }).sorted().toList();
        }
    }

    @Test
    void everySupportOfEveryRealSchematicCanBeClickedLegallyOnEachOutlineFace() throws IOException {
        List<Path> files = schematics();
        assertFalse(files.isEmpty(), "no schematics in " + SCHEMATICS.toAbsolutePath());
        int checked = 0;
        for (Path path : files) {
            Schematic schematic = SchematicFormats.read(path);
            for (int i = 0; i < schematic.paletteSize(); i++) {
                BlockState state = schematic.paletteState(i);
                if (state.isAir()) continue;
                VoxelShape shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                if (shape.isEmpty() || shape.toAabbs().size() != 1) continue;
                AABB box = shape.bounds();
                for (Direction face : Direction.values()) {
                    Vec3 centre = box.getCenter();
                    double plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE
                            ? box.max(face.getAxis()) : box.min(face.getAxis());
                    Vec3 local = switch (face.getAxis()) {
                        case X -> new Vec3(plane, centre.y, centre.z);
                        case Y -> new Vec3(centre.x, plane, centre.z);
                        case Z -> new Vec3(centre.x, centre.y, plane);
                    };
                    Vec3 hit = local.add(SUPPORT.getX(), SUPPORT.getY(), SUPPORT.getZ());
                    Vec3 eyes = hit.add(face.getStepX() * 2.0, face.getStepY() * 2.0, face.getStepZ() * 2.0);
                    String label = path.getFileName() + " " + schematic.paletteId(i) + " " + face;
                    assertTrue(BuildPlanner.cursorValid(SUPPORT, face, hit, shape), label + " cursor");
                    assertTrue(BuildPlanner.legalClick(eyes, Rotation.lookingAt(hit, eyes), SUPPORT, shape, face, hit, REACH),
                            label + " legal");
                    // Reach: the same click from 5 blocks out is Far Place.
                    Vec3 far = hit.add(face.getStepX() * 5.0, face.getStepY() * 5.0, face.getStepZ() * 5.0);
                    assertFalse(BuildPlanner.legalClick(far, Rotation.lookingAt(hit, far), SUPPORT, shape, face, hit, REACH),
                            label + " far");
                    checked++;
                }
            }
        }
        assertTrue(checked > 0);
    }

    @Test
    void interactiveSupportsAreClassifiedForSneakPlacement() throws IOException {
        // Blocks a plain click would open or toggle must be placed against sneaking, otherwise the click is
        // an interaction packet the server answers with a GUI (Grim MultiActions) instead of a placement.
        assertTrue(BuildPlanner.isInteractive(Blocks.CHEST.defaultBlockState()));
        assertTrue(BuildPlanner.isInteractive(Blocks.HOPPER.defaultBlockState()));
        assertTrue(BuildPlanner.isInteractive(Blocks.REPEATER.defaultBlockState()));
        assertTrue(BuildPlanner.isInteractive(Blocks.OAK_DOOR.defaultBlockState()));
        assertFalse(BuildPlanner.isInteractive(Blocks.STONE.defaultBlockState()));
    }

    @Test
    void rapidSneakTransitionsAreHeldAndSpaced() {
        // Vulcan BadPackets K: a sneak must last >= 2 ticks and starts must be >= 3 ticks apart, even when
        // aboutToFall / bridging flips every tick.
        SneakPacing pacing = new SneakPacing();
        int lastStart = Integer.MIN_VALUE / 2;
        int runStart = -1;
        boolean prev = false;
        for (int t = 0; t < 60; t++) {
            boolean want = t % 2 == 0 || t % 7 == 3; // flickers every tick
            boolean out = pacing.next(want);
            if (out && !prev) {
                assertTrue(t - lastStart >= 3, "sneak starts too close at tick " + t);
                lastStart = t;
                runStart = t;
            }
            if (!out && prev) assertTrue(t - runStart >= 2, "sneak released too early at tick " + t);
            prev = out;
        }
        assertTrue(lastStart > 0, "pacing never sneaked");
        // A steady want is honoured at once and a steady release follows after the minimum hold.
        pacing.reset();
        assertTrue(pacing.next(true));
        assertTrue(pacing.next(false));
        assertFalse(pacing.next(false));
    }
}
