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

package combatant.client.util.time;

import net.minecraft.util.Mth;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TimerController {
    public static final TimerController INSTANCE = new TimerController();

    public static final int NOT_IMPORTANT = 0;
    public static final int NORMAL = 100;
    public static final int IMPORTANT_FOR_USAGE_1 = 1000;
    public static final int IMPORTANT_FOR_USAGE_2 = 2000;
    public static final int IMPORTANT_FOR_PLAYER_LIFE = 10000;

    private final Map<Object, Request> requests = new LinkedHashMap<>();
    private long sequence;

    private TimerController() {
    }

    public static float getTimerSpeed() {
        return INSTANCE.activeTimerSpeed();
    }

    public static void requestTimerSpeed(float timerSpeed, int priority, Object provider) {
        requestTimerSpeed(timerSpeed, priority, provider, 1);
    }

    public static void requestTimerSpeed(float timerSpeed, int priority, Object provider, int resetAfterTicks) {
        INSTANCE.request(provider, timerSpeed, priority, resetAfterTicks);
    }

    public static void clear(Object provider) {
        INSTANCE.clearProvider(provider);
    }

    private static float sanitize(float timerSpeed) {
        if (Float.isNaN(timerSpeed) || Float.isInfinite(timerSpeed)) {
            return 1.0f;
        }
        return Mth.clamp(timerSpeed, 0.1f, 20.0f);
    }

    @EventHandler(priority = 10000)
    public void onTick(GameTickEvent event) {
        tick();
    }

    public synchronized void request(Object provider, float timerSpeed, int priority, int resetAfterTicks) {
        Object key = provider != null ? provider : TimerController.class;
        float speed = sanitize(timerSpeed);
        int ticks = Math.max(0, resetAfterTicks) + 1;
        requests.put(key, new Request(speed, priority, ticks, ++sequence));
    }

    public synchronized void clearProvider(Object provider) {
        if (provider != null) {
            requests.remove(provider);
        }
    }

    public synchronized void reset() {
        requests.clear();
    }

    public synchronized float activeTimerSpeed() {
        Request best = null;
        for (Request request : requests.values()) {
            if (best == null
                    || request.priority > best.priority
                    || (request.priority == best.priority && request.sequence > best.sequence)) {
                best = request;
            }
        }
        return best != null ? best.timerSpeed : 1.0f;
    }

    private synchronized void tick() {
        Iterator<Map.Entry<Object, Request>> iterator = requests.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Object, Request> entry = iterator.next();
            Request request = entry.getValue();
            request.remainingTicks--;
            if (request.remainingTicks <= 0) {
                iterator.remove();
            }
        }
    }

    private static final class Request {
        final float timerSpeed;
        final int priority;
        final long sequence;
        int remainingTicks;

        Request(float timerSpeed, int priority, int remainingTicks, long sequence) {
            this.timerSpeed = timerSpeed;
            this.priority = priority;
            this.remainingTicks = remainingTicks;
            this.sequence = sequence;
        }
    }
}
