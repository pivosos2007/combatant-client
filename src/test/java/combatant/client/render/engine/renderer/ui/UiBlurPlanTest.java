package combatant.client.render.engine.renderer.ui;

import combatant.client.render.engine.renderer.ui.draw.UiRect;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiBlurPlanTest {
    @Test
    void combinesNearbyConsumersWithoutMixingProfilesOrGenerations() {
        UiBlurPlan plan = new UiBlurPlan();
        UiRect first = new UiRect(10, 10, 80, 30);
        UiRect second = new UiRect(10, 45, 80, 30);
        plan.add(2, 1.5f, first);
        plan.add(2, 1.5f, second);
        assertEquals(new UiRect(10, 10, 80, 65), plan.bounds(2, 1.5f, first));
        assertEquals(first, plan.bounds(3, 1.5f, first));
        assertEquals(first, plan.bounds(2, 2, first));
        assertNull(plan.bounds(2, 1.5f, null));
        plan.clear();
        assertEquals(first, plan.bounds(2, 1.5f, first));
    }

    @Test
    void distantPanelsDoNotForceFullscreenWork() {
        UiBlurPlan plan = new UiBlurPlan();
        UiRect first = new UiRect(0, 0, 30, 30);
        plan.add(2, 1, first);
        plan.add(2, 1, new UiRect(1900, 1000, 30, 30));
        assertEquals(first, plan.bounds(2, 1, first));
    }

    @Test
    void regionalRecomputePreservesEarlierCoverageWithinItsCostBudget() {
        UiBlurRegion first = new UiBlurRegion(10, 10, 100, 50);
        UiBlurRegion second = new UiBlurRegion(10, 55, 100, 50);
        UiBlurRegion union = second.mergeNearby(first);
        assertTrue(union.contains(first));
        assertTrue(union.contains(second));
        assertEquals(first, first.mergeNearby(new UiBlurRegion(1900, 1000, 30, 30)));
    }
}
