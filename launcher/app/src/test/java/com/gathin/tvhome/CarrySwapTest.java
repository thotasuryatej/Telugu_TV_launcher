package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Carrying a tile only ever trades it with the tile it ends on. */
public class CarrySwapTest {
    private static List<String> tiles() {
        return new ArrayList<>(Arrays.asList("A", "B", "C", "D", "E"));
    }

    @Test public void oneStepTradesTwoTiles() {
        List<String> t = tiles();
        CarrySwap.step(t, 0, 0, 1);
        assertEquals(Arrays.asList("B", "A", "C", "D", "E"), t);
    }

    @Test public void tilesPassedOnTheWayGoBack() {
        List<String> t = tiles();
        CarrySwap.step(t, 0, 0, 1);
        CarrySwap.step(t, 0, 1, 2);
        CarrySwap.step(t, 0, 2, 3);
        assertEquals(Arrays.asList("D", "B", "C", "A", "E"), t);
    }

    @Test public void comingBackToTheStartChangesNothing() {
        List<String> t = tiles();
        CarrySwap.step(t, 2, 2, 3);
        CarrySwap.step(t, 2, 3, 2);
        assertEquals(tiles(), t);
    }

    @Test public void movingBackwardsWorksToo() {
        List<String> t = tiles();
        CarrySwap.step(t, 3, 3, 2);
        CarrySwap.step(t, 3, 2, 0);
        assertEquals(Arrays.asList("D", "B", "C", "A", "E"), t);
    }
}
