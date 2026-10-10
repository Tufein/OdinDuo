package nl.retroid.touchguard;

public final class SessionClockTest {
    private static void expect(long expected, long actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    public static void main(String[] args) {
        expect(1000, SessionClock.start(true, "boot:1", "boot:1", 1000, 5000));
        // Sleep contributes to elapsed duration; wall-clock corrections cannot affect these inputs.
        expect(7204000, SessionClock.duration("boot:1", "boot:1", 1000, 7205000));
        expect(5000, SessionClock.start(false, "boot:1", "boot:1", 1000, 5000));
        expect(50, SessionClock.start(true, "boot:1", "boot:2", 1000, 50));
        expect(-1, SessionClock.duration("boot:1", "boot:2", 1, 5000));
        expect(5000, SessionClock.start(true, "boot:1", "boot:1", 6000, 5000));
        expect(5000, SessionClock.start(true, "boot:1", "boot:1", -1, 5000));
        expect(-1, SessionClock.duration("boot:1", "boot:1", -1, 5000));
        expect(-1, SessionClock.duration("boot:1", "boot:1", 6000, 5000));
        if (SessionClock.sameBoot("", "") || SessionClock.sameBoot(null, "boot:1"))
            throw new AssertionError("Unknown boot treated as valid");
        System.out.println("Session clock checks passed: restarts, new boots, invalid records and elapsed sleep time");
    }
}
