package im.yaHustler.module.impl.movement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The donor's queued 190 ms digit roll, with a controllable clock for verification. */
public final class BoosterAngleDisplay {
    public static final long ROLL_NANOS = 190_000_000L;
    private final List<Digit> digits = new ArrayList<>();
    private final List<Digit> view = Collections.unmodifiableList(digits);
    private String text = "";
    private Integer pending;

    public String text() { return text; }
    public List<Digit> digits() { return view; }
    public void clear() { digits.clear(); text = ""; pending = null; }

    public void update(int angle, long now) {
        String next = Integer.toString(angle);
        if (text.isEmpty()) { replace(next); return; }
        pending = text.equals(next) ? null : angle;
        if (pending == null || digits.stream().anyMatch(digit -> digit.progress(now) < 1)) return;
        next = Integer.toString(pending);
        pending = null;
        if (next.length() != digits.size()) { replace(next); return; }
        for (int index = 0; index < next.length(); index++) digits.get(index).change(next.charAt(index), now);
        text = next;
    }

    private void replace(String next) {
        digits.clear();
        for (int index = 0; index < next.length(); index++) digits.add(new Digit(next.charAt(index)));
        text = next;
    }

    public static final class Digit {
        private String previous;
        private String current;
        private long started;
        private boolean rolling;
        private Digit(char value) { previous = current = Character.toString(value); }
        public String previous() { return previous; }
        public String current() { return current; }
        private void change(char value, long now) {
            String next = Character.toString(value);
            if (current.equals(next)) return;
            previous = current;
            current = next;
            started = now;
            rolling = true;
        }
        public float progress(long now) {
            if (!rolling) return 1;
            long elapsed = Math.max(0, now - started);
            if (elapsed >= ROLL_NANOS) return 1;
            // Float conversion can round the last nanoseconds up to 1. Keep the
            // queue closed until the actual 190 ms deadline, at every frame rate.
            return Math.min(Math.nextDown(1f), (float) ((double) elapsed / ROLL_NANOS));
        }
    }
}
