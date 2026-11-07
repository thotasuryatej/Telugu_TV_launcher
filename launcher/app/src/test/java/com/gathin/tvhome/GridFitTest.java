package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

/** fit_all_tiles grows the configured layout, rows first, only as far as the tiles need. */
public class GridFitTest {
    private static void assertFit(int tiles, int columns, int rows) {
        assertArrayEquals("tiles " + tiles, new int[] {columns, rows}, GridFit.fit(tiles, 5, 2));
    }

    @Test public void fewerTilesKeepTheConfiguredLayout() {
        assertFit(0, 5, 2);
        assertFit(15, 5, 2);
    }

    @Test public void aRowIsAddedFirst() {
        assertFit(16, 5, 3);
        assertFit(19, 5, 3);
        assertFit(20, 5, 3);
    }

    @Test public void thenColumnsGrow() {
        assertFit(21, 6, 3);
        assertFit(24, 6, 3);
        assertFit(25, 7, 3);
        assertFit(28, 7, 3);
        assertFit(29, 8, 3);
        assertFit(32, 8, 3);
    }

    @Test public void pastTheLargestLayoutItStaysThere() {
        assertFit(33, 8, 3);
        assertFit(200, 8, 3);
    }
}
