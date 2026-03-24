package com.tukuyomil032.mapbrowser.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Parses tile-range expressions for give-frame command.
 */
final class MapTileRangeParser {
    Optional<List<Integer>> parseTileRange(final String rangeExpr, final int width, final int height) {
        final int tileCount = width * height;
        if (rangeExpr == null || rangeExpr.isBlank() || tileCount <= 0) {
            return Optional.empty();
        }

        final String normalized = rangeExpr.trim().toLowerCase(Locale.ROOT);
        if ("all".equals(normalized)) {
            final ArrayList<Integer> all = new ArrayList<>(tileCount);
            for (int i = 0; i < tileCount; i++) {
                all.add(i);
            }
            return Optional.of(all);
        }
        if ("odd".equals(normalized) || "even".equals(normalized)) {
            final boolean odd = "odd".equals(normalized);
            final ArrayList<Integer> picked = new ArrayList<>();
            for (int i = 1; i <= tileCount; i++) {
                if (odd && (i % 2 == 1)) {
                    picked.add(i - 1);
                }
                if (!odd && (i % 2 == 0)) {
                    picked.add(i - 1);
                }
            }
            return picked.isEmpty() ? Optional.empty() : Optional.of(picked);
        }

        final Set<Integer> selected = new TreeSet<>();
        final String[] tokens = rangeExpr.replace(" ", "").split(",");
        for (final String token : tokens) {
            if (token.isBlank()) {
                continue;
            }

            final int colon = token.indexOf(':');
            if (colon >= 0) {
                final String left = token.substring(0, colon);
                final String right = token.substring(colon + 1);
                final Optional<int[]> c1 = parseCoordinate(left, width, height);
                final Optional<int[]> c2 = parseCoordinate(right, width, height);
                if (c1.isEmpty() || c2.isEmpty()) {
                    return Optional.empty();
                }

                final int fromX = Math.min(c1.get()[0], c2.get()[0]);
                final int toX = Math.max(c1.get()[0], c2.get()[0]);
                final int fromY = Math.min(c1.get()[1], c2.get()[1]);
                final int toY = Math.max(c1.get()[1], c2.get()[1]);
                for (int y = fromY; y <= toY; y++) {
                    for (int x = fromX; x <= toX; x++) {
                        selected.add(((y - 1) * width) + (x - 1));
                    }
                }
                continue;
            }

            final int dots = token.indexOf("..");
            if (dots >= 0) {
                final String startRaw = token.substring(0, dots);
                final String endRaw = token.substring(dots + 2);
                if (startRaw.isBlank() || endRaw.isBlank()) {
                    return Optional.empty();
                }

                final int start;
                final int end;
                try {
                    start = Integer.parseInt(startRaw);
                    end = Integer.parseInt(endRaw);
                } catch (final NumberFormatException ex) {
                    return Optional.empty();
                }
                if (start < 1 || end < 1 || start > tileCount || end > tileCount) {
                    return Optional.empty();
                }

                final int from = Math.min(start, end);
                final int to = Math.max(start, end);
                for (int index = from; index <= to; index++) {
                    selected.add(index - 1);
                }
                continue;
            }

            final int dash = token.indexOf('-');
            if (dash >= 0) {
                final Optional<int[]> coordinate = parseCoordinate(token, width, height);
                if (coordinate.isEmpty()) {
                    return Optional.empty();
                }
                final int x = coordinate.get()[0];
                final int y = coordinate.get()[1];
                final int tileIndex = (y - 1) * width + (x - 1);
                selected.add(tileIndex);
                continue;
            }

            final int single;
            try {
                single = Integer.parseInt(token);
            } catch (final NumberFormatException ex) {
                return Optional.empty();
            }
            if (single < 1 || single > tileCount) {
                return Optional.empty();
            }
            selected.add(single - 1);
        }

        if (selected.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ArrayList<>(selected));
    }

    private Optional<int[]> parseCoordinate(final String token, final int width, final int height) {
        final int dash = token.indexOf('-');
        if (dash <= 0 || dash >= token.length() - 1) {
            return Optional.empty();
        }
        final String xRaw = token.substring(0, dash);
        final String yRaw = token.substring(dash + 1);
        final int x;
        final int y;
        try {
            x = Integer.parseInt(xRaw);
            y = Integer.parseInt(yRaw);
        } catch (final NumberFormatException ex) {
            return Optional.empty();
        }
        if (x < 1 || y < 1 || x > width || y > height) {
            return Optional.empty();
        }
        return Optional.of(new int[]{x, y});
    }
}
