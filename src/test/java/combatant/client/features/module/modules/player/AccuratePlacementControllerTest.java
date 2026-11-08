package combatant.client.features.module.modules.player;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AccuratePlacementControllerTest {
    private final ReferenceHarness reference = new ReferenceHarness();
    private final AccuratePlacementController<ReferenceHarness.BlockHitResult> controller = new AccuratePlacementController<>();
    private final Set<BlockPos> placed = new HashSet<>();
    private final List<String> calls = new ArrayList<>();

    @Test
    void referenceBranchIsThePinnedUpstreamSource() throws Exception {
        String upstream;
        try (var stream = getClass().getResourceAsStream("/abp/original/MinecraftMixin.java")) {
            assertNotNull(stream);
            upstream = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        String fixture = Files.readString(Path.of("src/test/java/combatant/client/features/module/modules/player/ReferenceHarness.java"));
        String start = "BlockPlaceContext targetPlacement =";
        String end = "lastSeenBlockPos = blockHitResult.getBlockPos();";
        assertEquals(upstream.substring(upstream.indexOf(start), upstream.indexOf(end) + end.length()),
                fixture.substring(fixture.indexOf(start), fixture.indexOf(end) + end.length()));
    }

    private static BlockPos pos(ReferenceHarness.BlockPos p) {
        return p == null ? null : new BlockPos(p.x(), p.y(), p.z());
    }

    private static Vec3 vec(ReferenceHarness.Vec3 v) {
        return v == null ? null : new Vec3(v.x(), v.y(), v.z());
    }

    private void step(ReferenceHarness.BlockHitResult hit, int clicks, boolean held, int cooldown, Vec3 player, double mouse) {
        reference.step(hit, clicks, held, cooldown,
                new ReferenceHarness.Vec3(player.x, player.y, player.z), new ReferenceHarness.Vec3(mouse, 0, 0));
        Vec3 mousePosition = new Vec3(mouse, 0, 0);
        if (clicks > 0) controller.freshPress(mousePosition);
        Direction face = Direction.valueOf(hit.face.name());
        var decision = controller.select(hit, pos(hit.support), face, clicks, held, cooldown, player, mousePosition);
        boolean existedBefore = placed.contains(pos(hit.destination));
        for (var previous : decision.backfill()) use(previous);
        for (int i = 0; i < decision.uses(); i++) {
            use(hit);
            if (existedBefore != placed.contains(pos(hit.destination))) {
                controller.placed(pos(hit.destination), face, player);
            }
        }
        var state = controller.state();
        assertAll(
                () -> assertEquals(reference.client.calls, calls, "use calls"),
                () -> assertEquals(pos(reference.lastSeenBlockPos), state.seen(), "seen support"),
                () -> assertEquals(pos(reference.lastPlacedBlockPos), state.placed(), "placed cell"),
                () -> assertEquals(vec(reference.lastPlayerPlacedBlockPos), state.player(), "accumulated player"),
                () -> assertEquals(reference.autoRepeatWaitingOnCooldown, state.waiting(), "waiting"),
                () -> assertEquals(reference.backFillList.size(), state.backfillSize(), "backfill"));
    }

    private void use(ReferenceHarness.BlockHitResult hit) {
        calls.add(hit.name);
        if (reference.client.place) placed.add(pos(hit.destination));
    }

    private void step(int target, int clicks, boolean held, int cooldown, double mouse) {
        step(ReferenceHarness.hit(target), clicks, held, cooldown, Vec3.ZERO, mouse);
    }

    @Test
    void shortPressIsOneUseEvenIfReleasedBeforePick() {
        step(0, 1, false, 4, 0);
        step(0, 0, false, 3, 0);
        assertEquals(List.of("target-0"), calls);
    }

    @Test
    void heldSupportDoesNotRepeatAfterCooldownOrMouseMovement() {
        step(0, 1, true, 0, 0);
        for (int i = 0; i < 200; i++) step(0, 0, true, Math.max(0, 4 - i), i * .01);
        assertEquals(1, calls.size());
    }

    @Test
    void backlogHasNoFourEntryLimitAndExpiresOnlyOnFreshTarget() {
        step(0, 1, true, 0, 0);
        for (int i = 1; i <= 9; i++) step(i, 0, true, 3, 0);
        assertEquals(9, controller.state().backfillSize());
        step(9, 0, true, 0, 0);
        assertEquals(1, calls.size());
        step(10, 0, true, 0, 0);
        assertEquals(11, calls.size());
        assertEquals("target-9", calls.get(9));
        assertFalse(controller.state().waiting());
        step(11, 0, true, 4, 0);
        assertEquals(12, calls.size());
    }

    @Test
    void mouseThresholdIsInclusiveAndFreshPressResetsBackfill() {
        step(0, 1, true, 0, 0);
        step(1, 0, true, 3, .099);
        assertEquals(1, calls.size());
        step(2, 0, true, 3, .1);
        assertEquals(List.of("target-0", "target-1", "target-2"), calls);
        step(3, 1, true, 4, .1);
        step(4, 0, true, 3, .1);
        step(5, 1, true, 3, .1);
        assertFalse(calls.contains("target-4"));
        assertEquals(0, controller.state().backfillSize());
    }

    @Test
    void twoClicksAreConsumedAsTwoUsesNotTwoHistoryResets() {
        step(0, 2, false, 4, 0);
        assertEquals(List.of("target-0", "target-0"), calls);
        assertEquals(new Vec3(0, 1, 0), controller.state().player());
        step(1, 2, true, 4, 0);
        assertEquals(4, calls.size());
    }

    @Test
    void returningToPreviousSupportIsFreshButFaceAndCellJitterIsNot() {
        step(0, 1, true, 0, 0);
        var support = new ReferenceHarness.BlockPos(0, 0, 0);
        for (var face : ReferenceHarness.Direction.values()) {
            var hit = new ReferenceHarness.BlockHitResult("jitter", support,
                    new ReferenceHarness.BlockPos(face.step.x(), face.step.y(), face.step.z()), face);
            step(hit, 0, true, 0, Vec3.ZERO, 0);
        }
        assertEquals(1, calls.size());
        step(1, 0, true, 0, 0);
        step(0, 0, true, 4, 0);
        assertEquals(List.of("target-0", "target-1", "target-0"), calls);
    }

    @Test
    void consumedActionWithoutBlockChangeDoesNotCreatePlacementHistory() {
        reference.client.place = false;
        step(0, 1, true, 0, 0);
        step(1, 0, true, 0, 0);
        assertNull(controller.state().placed());
        assertNull(controller.state().player());
    }

    @Test
    void allFacesUseOriginalBackwardBoundaryAndAccumulatedPlayerPosition() {
        for (var face : ReferenceHarness.Direction.values()) {
            AccuratePlacementControllerTest run = new AccuratePlacementControllerTest();
            var support = new ReferenceHarness.BlockPos(0, 0, 0);
            var destination = face.step;
            var first = new ReferenceHarness.BlockHitResult("first", support, destination, face);
            Vec3 player = new Vec3(3 * face.step.x(), 3 * face.step.y(), 3 * face.step.z());
            run.step(first, 1, true, 0, player, 0);
            var second = new ReferenceHarness.BlockHitResult("backwards", destination,
                    new ReferenceHarness.BlockPos(destination.x() * 2, destination.y() * 2, destination.z() * 2), face);
            run.step(second, 0, true, 0, player, 0);
            assertEquals(1, run.calls.size(), face.toString());
            run.step(second, 0, true, 0, player.add(face.step.x() * .98, face.step.y() * .98, face.step.z() * .98), 0);
            assertEquals(1, run.calls.size(), face.toString());
            run.step(second, 0, true, 0, player.add(face.step.x(), face.step.y(), face.step.z()), 0);
            assertEquals(2, run.calls.size(), face.toString());
        }
    }

    @Test
    void deterministicMixedTracesMatchExtractedUpstreamBranch() {
        Random random = new Random(0xABF26_3);
        for (int i = 0; i < 2500; i++) {
            var face = ReferenceHarness.Direction.values()[random.nextInt(6)];
            int x = random.nextInt(6), y = random.nextInt(3), z = random.nextInt(6);
            var hit = new ReferenceHarness.BlockHitResult("hit-" + x + y + z + face,
                    new ReferenceHarness.BlockPos(x, y, z),
                    new ReferenceHarness.BlockPos(x + face.step.x(), y + face.step.y(), z + face.step.z()), face);
            step(hit, random.nextInt(8) == 0 ? 1 + random.nextInt(3) : 0,
                    random.nextBoolean(), random.nextInt(5),
                    new Vec3(random.nextDouble() * 6, random.nextDouble() * 3, random.nextDouble() * 6),
                    random.nextDouble() * .2);
        }
    }

    @Test
    void resetDropsWorldHistoryAndBacklog() {
        step(0, 1, true, 0, 0);
        step(1, 0, true, 4, 0);
        controller.reset();
        assertEquals(new AccuratePlacementController.State(null, null, null, true, 0), controller.state());
    }
}
