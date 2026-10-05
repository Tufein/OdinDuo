package nl.retroid.touchguard;

public final class GuardHeartbeatTest {
    private static final String TOKEN = "0123456789abcdef0123456789abcdef";
    private static void expect(GuardLogState.State expected, GuardLogState.State actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static void reject(String token, String record, long now) {
        if (GuardHeartbeat.read(token, record, now).alive) throw new AssertionError("Accepted invalid heartbeat");
    }
    public static void main(String[] args) {
        String active = "STATE control=on runtime=active host=active\n";
        String noisyTail = "PIN device=on\n".repeat(4000);
        expect(GuardLogState.State.EMPTY, GuardLogState.read(noisyTail));
        GuardHeartbeat current = GuardHeartbeat.read(TOKEN, TOKEN + " 100 ACTIVE\n", 101);
        if (!current.alive) throw new AssertionError("Fresh heartbeat rejected");
        expect(GuardLogState.State.ACTIVE, current.observation(noisyTail));
        expect(GuardLogState.State.WAITING, GuardHeartbeat.read(TOKEN, TOKEN + " 100 WAITING", 100).observation(active));
        expect(GuardLogState.State.STARTING, GuardHeartbeat.read(TOKEN, TOKEN + " 100 STARTING", 100).observation(active));
        expect(GuardLogState.State.ERROR, current.observation(active + "RESTORE FAILED readback\n"));
        expect(GuardLogState.State.STOPPED, current.observation(active + "GUARD stopped\n"));
        GuardHeartbeat legacy = GuardHeartbeat.read(TOKEN, TOKEN + " 100", 110);
        if (!legacy.alive || legacy.state != null) throw new AssertionError("Older APK session cannot be adopted");
        expect(GuardLogState.State.ACTIVE, legacy.observation(active));
        reject(TOKEN, TOKEN + " 100 ACTIVE", 111);
        reject(TOKEN, TOKEN + " 101 ACTIVE", 100);
        reject(TOKEN, TOKEN + " -1 ACTIVE", 100);
        reject(TOKEN, TOKEN + " 9223372036854775807 ACTIVE", 100);
        reject(TOKEN, "ffffffffffffffffffffffffffffffff 100 ACTIVE", 100);
        reject("bad-token", "bad-token 100 ACTIVE", 100);
        reject(TOKEN, TOKEN + " 100 ACTIVE extra", 100);
        reject(TOKEN, TOKEN + " 100 ERROR", 100);
        reject(TOKEN, TOKEN + "", 100);
        System.out.println("Heartbeat checks passed: noisy logs, current power state, legacy adoption, freshness and malformed records");
    }
}
