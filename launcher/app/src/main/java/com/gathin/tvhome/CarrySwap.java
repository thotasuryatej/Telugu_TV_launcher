package com.gathin.tvhome;

import java.util.Collections;
import java.util.List;

/**
 * swap_tiles: a carried tile trades places with the one it is on, and every other tile stays put. The tile it was on
 * goes back to its own place first, so only the last tile it stopped on ends up moved.
 */
final class CarrySwap {
    private CarrySwap() {}

    /** @param origin where the carried tile was picked up. It is at {@code from} now and goes to {@code to}. */
    static <T> void step(List<T> items, int origin, int from, int to) {
        if (from != origin) Collections.swap(items, from, origin);
        if (to != origin) Collections.swap(items, origin, to);
    }
}
