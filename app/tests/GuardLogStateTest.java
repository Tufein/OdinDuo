package nl.retroid.touchguard;

public final class GuardLogStateTest {
    private static void expect(GuardLogState.State expected, String log) {
        GuardLogState.State actual = GuardLogState.read(log);
        if (actual != expected) throw new AssertionError(expected + " != " + actual);
    }

    public static void main(String[] args) {
        expect(GuardLogState.State.EMPTY, "");
        expect(GuardLogState.State.WAITING, "100 READY waiting for RDS; owner=123");
        expect(GuardLogState.State.STARTING, "RDS detected path=/sys/device");
        String active = "100 STATE control=on runtime=active host=active\n";
        expect(GuardLogState.State.ACTIVE, active);
        expect(GuardLogState.State.STARTING, active + "101 STATE control=auto runtime=suspended host=active\n");
        expect(GuardLogState.State.ACTIVE, active + "101 STATE control=on runtime=suspended host=active\n102 " + active);
        expect(GuardLogState.State.STOPPED, active + "101 GUARD stopped\nSTOP acknowledged test result=0\n");
        expect(GuardLogState.State.ERROR, active + "RESTORE FAILED /sys/device\nGUARD stopped\n");
        expect(GuardLogState.State.ERROR, "PIN readback failed /sys/device");
        expect(GuardLogState.State.ERROR, "App identity verification failed");
        System.out.println("Guard status checks passed: latest observation, disconnect and error precedence");
    }
}
