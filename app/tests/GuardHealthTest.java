package nl.retroid.touchguard;

public final class GuardHealthTest {
    private static void expect(GuardHealth.State expected, GuardHealth.State actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    public static void main(String[] args) {
        GuardHealth health = new GuardHealth();
        health.launched(1000);
        expect(GuardHealth.State.STARTING, health.observe(false, 15999));
        expect(GuardHealth.State.LOST, health.observe(false, 16000));
        health.launched(20000);
        expect(GuardHealth.State.HEALTHY, health.observe(true, 21000));
        // A stale historical ACTIVE log must not override a missing heartbeat.
        expect(GuardHealth.State.RECOVERING, health.observe(false, 22000));
        expect(GuardHealth.State.RECOVERING, health.observe(false, 31999));
        expect(GuardHealth.State.LOST, health.observe(false, 32000));
        // Several hours of deep sleep advance elapsedRealtime, but not the supplied uptime.
        health.launched(40000);
        expect(GuardHealth.State.HEALTHY, health.observe(true, 41000));
        expect(GuardHealth.State.RECOVERING, health.observe(false, 42000));
        expect(GuardHealth.State.RECOVERING, health.observe(false, 42000));
        expect(GuardHealth.State.HEALTHY, health.observe(true, 43000));
        expect(GuardHealth.State.RECOVERING, health.observe(false, 44000));
        expect(GuardHealth.State.RECOVERING, health.observe(false, 53999));
        if (!health.allowRecovery(54000)) throw new AssertionError("First recovery denied");
        health.launched(55000);
        if (!health.allowRecovery(71000)) throw new AssertionError("Second recovery denied");
        health.launched(72000);
        if (health.allowRecovery(88000)) throw new AssertionError("Repeated failures were not bounded");
        if (!health.allowRecovery(114000)) throw new AssertionError("New recovery window denied");
        System.out.println("Helper health checks passed: startup, silent loss, sleep grace and bounded recovery");
    }
}
