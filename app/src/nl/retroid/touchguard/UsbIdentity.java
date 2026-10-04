package nl.retroid.touchguard;

final class UsbIdentity {
    static final int VENDOR = 0x222a;
    static final int PRODUCT = 0x0001;

    static boolean matches(int vendor, int product) {
        return vendor == VENDOR && product == PRODUCT;
    }

    static boolean validDescriptor(byte[] data, int count) {
        if (data == null || count != 18 || data.length < count
                || (data[0] & 255) != 18 || (data[1] & 255) != 1) return false;
        int vendor = (data[8] & 255) | ((data[9] & 255) << 8);
        int product = (data[10] & 255) | ((data[11] & 255) << 8);
        return matches(vendor, product);
    }
}
