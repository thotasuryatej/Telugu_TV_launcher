package com.gathin.tvhome;

/**
 * fit_all_tiles: the smallest layout at or above the configured one that shows every tile without scrolling. Rows
 * are added first, up to MAX_ROWS_BELOW under the dock, then columns, up to MAX_COLUMNS. Past that the extra tiles
 * scroll as they always did.
 */
final class GridFit {
    static final String TILE_COUNT_KEY = "fit_tile_count";
    static final int MAX_COLUMNS = 8, MAX_ROWS_BELOW = 3;

    private GridFit() {}

    /** @return {columns, rows below the dock}, never smaller than the configured layout. */
    static int[] fit(int tiles, int columns, int rowsBelow) {
        for (int rows = rowsBelow; rows < MAX_ROWS_BELOW; rows++) {
            if (tiles <= columns * (rows + 1)) return new int[] {columns, rows};
        }
        int rows = Math.max(rowsBelow, MAX_ROWS_BELOW);
        for (int across = columns; across < MAX_COLUMNS; across++) {
            if (tiles <= across * (rows + 1)) return new int[] {across, rows};
        }
        return new int[] {Math.max(columns, MAX_COLUMNS), rows};
    }
}
