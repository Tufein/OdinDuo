package nl.retroid.touchguard;

public final class UsbIdentityTest {
    public static void main(String[] args) {
        byte[] retroid = new byte[18];
        retroid[0] = 18; retroid[1] = 1;
        retroid[8] = 0x2a; retroid[9] = 0x22; retroid[10] = 1;
        check(UsbIdentity.validDescriptor(retroid, 18), "expected touchscreen descriptor");
        check(!UsbIdentity.validDescriptor(retroid, -1), "USB timeout must fail");
        check(!UsbIdentity.validDescriptor(retroid, 8), "short descriptor must fail");
        check(!UsbIdentity.validDescriptor(new byte[2], 18), "truncated buffer must fail");
        retroid[10] = 2;
        check(!UsbIdentity.validDescriptor(retroid, 18), "other product must not be accepted");
        retroid[10] = 1; retroid[9] = 0x23;
        check(!UsbIdentity.validDescriptor(retroid, 18), "other vendor must not be accepted");
        check(!UsbIdentity.validDescriptor(null, 18), "missing descriptor must fail");
        System.out.println("7 descriptor/target checks passed");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
