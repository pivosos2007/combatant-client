/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.util.click;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Rolling click scheduler adapted from LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Original copyright (c) CCBlueX.
 */
public final class ClickScheduler {

    private static final int CYCLE_LENGTH = 20;
    private static final int ITERATIONS = 2;

    private final RollingClickArray clickArray = new RollingClickArray(CYCLE_LENGTH, ITERATIONS);
    private int minCps;
    private int maxCps;

    public ClickScheduler(int minCps, int maxCps) {
        setCps(minCps, maxCps);
        fill();
    }

    public void setCps(int minCps, int maxCps) {
        this.minCps = Math.max(1, minCps);
        this.maxCps = Math.max(this.minCps, maxCps);
        fill();
    }

    public void reset() {
        fill();
    }

    public void tick() {
        if (clickArray.advance()) {
            clickArray.push(generateCycle());
        }
    }

    public boolean shouldClick() {
        return willClickAt(0);
    }

    public int getClicksAt(int tick) {
        return getClickAmount(Math.max(0, tick));
    }

    public boolean willClickAt(int tick) {
        return getClicksAt(tick) > 0;
    }

    public boolean willClickInTicks(int ticks) {
        int clamped = Math.max(0, ticks);
        for (int i = 0; i <= clamped; i++) {
            if (willClickAt(i)) {
                return true;
            }
        }
        return false;
    }

    private int getClickAmount(int tick) {
        return clickArray.get(tick);
    }

    private void fill() {
        clickArray.clear();
        int[] cycleArray = new int[CYCLE_LENGTH];
        for (int i = 0; i < ITERATIONS; i++) {
            Arrays.fill(cycleArray, 0);
            fillStabilizedPattern(cycleArray);
            clickArray.push(cycleArray);
            clickArray.advance(CYCLE_LENGTH);
        }
    }

    private int[] generateCycle() {
        int[] cycleArray = new int[CYCLE_LENGTH];
        fillStabilizedPattern(cycleArray);
        return cycleArray;
    }

    private void fillStabilizedPattern(int[] cycleArray) {
        int clicks = randomCps();
        int interval = clicks > 0 ? cycleArray.length / clicks : 0;
        int remainder = clicks > 0 ? cycleArray.length % clicks : 0;
        int currentIndex = 0;

        for (int i = 0; i < clicks; i++) {
            cycleArray[currentIndex % cycleArray.length]++;
            currentIndex += Math.max(interval, 1);
            if (remainder > 0) {
                currentIndex++;
                remainder--;
            }
        }
    }

    private int randomCps() {
        if (minCps >= maxCps) return minCps;
        return ThreadLocalRandom.current().nextInt(minCps, maxCps + 1);
    }

    private static final class RollingClickArray {
        private final int cycleLength;
        private final int[] array;
        private int head;

        private RollingClickArray(int cycleLength, int iterations) {
            this.cycleLength = cycleLength;
            this.array = new int[cycleLength * iterations];
        }

        private int get(int relativeIndex) {
            int actualIndex = (head + relativeIndex) % array.length;
            return array[actualIndex];
        }

        private boolean advance() {
            head = (head + 1) % array.length;
            return head % cycleLength == 0;
        }

        private void advance(int amount) {
            head = (head + amount) % array.length;
        }

        private void clear() {
            Arrays.fill(array, 0);
            head = 0;
        }

        private void push(int[] cycleArray) {
            if (cycleArray.length != cycleLength) {
                throw new IllegalArgumentException("Array size must match cycle length");
            }

            if (head == 0) {
                System.arraycopy(cycleArray, 0, array, cycleLength, cycleLength);
            } else if (head == cycleLength) {
                System.arraycopy(cycleArray, 0, array, 0, cycleLength);
            } else {
                throw new IllegalStateException("Head must be at 0 or cycle length");
            }
        }
    }
}
