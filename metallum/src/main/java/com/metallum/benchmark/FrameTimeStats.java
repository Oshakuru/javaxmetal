package com.metallum.benchmark;

import java.util.Arrays;

/** Wall-clock frame intervals, not GPU timestamps or displayed-frame timings. */
public record FrameTimeStats(int frames, double meanMs, double medianMs, double p95Ms,
                             double p99Ms, double worstMs, double averageFps,
                             int over16_67Ms, int over33_33Ms) {
    public static FrameTimeStats calculate(long[] intervals, int count) {
        if (count <= 0 || count > intervals.length) throw new IllegalArgumentException("Invalid sample count");
        long[] sorted = Arrays.copyOf(intervals, count);
        double total = 0;
        int slow60 = 0, slow30 = 0;
        for (long value : sorted) {
            if (value <= 0) throw new IllegalArgumentException("Frame intervals must be positive");
            total += value;
            if (value > 1_000_000_000.0 / 60) slow60++;
            if (value > 1_000_000_000.0 / 30) slow30++;
        }
        Arrays.sort(sorted);
        return new FrameTimeStats(count, total / count / 1e6,
                percentile(sorted, 0.5), percentile(sorted, 0.95), percentile(sorted, 0.99),
                sorted[count - 1] / 1e6, count * 1e9 / total, slow60, slow30);
    }

    private static double percentile(long[] sorted, double fraction) {
        return sorted[(int) Math.ceil(sorted.length * fraction) - 1] / 1e6;
    }
}
