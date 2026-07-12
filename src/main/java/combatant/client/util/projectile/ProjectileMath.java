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

package combatant.client.util.projectile;

/*
 * Ported from LiquidBounce (https://github.com/CCBlueX/LiquidBounce).
 * Original work copyright (c) CCBlueX.
 */

enum ProjectileMath {
    ;

    static MinimumResult findFunctionMinimumByBisect(double from, double to, double minDelta, DoubleUnaryFunction function) {
        double lowerBound = from;
        double upperBound = to;

        while (upperBound - lowerBound > minDelta) {
            double mid = (lowerBound + upperBound) / 2.0;
            double leftValue = function.apply((lowerBound + mid) / 2.0);
            double rightValue = function.apply((mid + upperBound) / 2.0);

            if (leftValue < rightValue) {
                upperBound = mid;
            } else {
                lowerBound = mid;
            }
        }

        double x = (lowerBound + upperBound) / 2.0;
        double y = function.apply(x);
        return new MinimumResult(x, y);
    }

    @FunctionalInterface
    interface DoubleUnaryFunction {
        double apply(double value);
    }

    record MinimumResult(double x, double y) {
    }
}
