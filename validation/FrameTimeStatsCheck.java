import com.metallum.benchmark.FrameTimeStats;

public class FrameTimeStatsCheck {
    public static void main(String[] args) {
        long[] samples = new long[100];
        for (int i = 0; i < 100; i++) samples[i] = (100L - i) * 1_000_000;
        var s = FrameTimeStats.calculate(samples, samples.length);
        if (s.frames() != 100 || s.medianMs() != 50 || s.p95Ms() != 95 || s.p99Ms() != 99
                || s.worstMs() != 100 || s.meanMs() != 50.5 || s.over16_67Ms() != 84
                || s.over33_33Ms() != 67 || Math.abs(s.averageFps() - 1000 / 50.5) > 1e-9)
            throw new AssertionError("Incorrect frame statistics: " + s);
        if (samples[0] != 100_000_000L) throw new AssertionError("Input mutated");
        var single = FrameTimeStats.calculate(new long[]{5_000_000}, 1);
        if (single.p99Ms() != 5 || single.averageFps() != 200) throw new AssertionError("Single sample");
        reject(new long[0], 0);
        reject(new long[]{0}, 1);
        reject(new long[]{-1}, 1);
        reject(new long[]{1}, 2);
        System.out.println("PASS: frame percentiles, FPS, thresholds, immutability, and invalid samples");
    }
    private static void reject(long[] samples, int count) {
        try { FrameTimeStats.calculate(samples, count); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid samples accepted");
    }
}
