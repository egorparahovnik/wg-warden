package com.example.wgwarden.provisioning;

import java.net.InetAddress;
import java.util.regex.Pattern;

/** Строгая проверка значений, которые попадают в shell-скрипты и конфиги WireGuard. */
public final class WireGuardValidation {
    private static final Pattern PUBLIC_KEY = Pattern.compile("^[A-Za-z0-9+/]{43}=$");
    private static final Pattern IPV4 = Pattern.compile("^[0-9]{1,3}(\\.[0-9]{1,3}){3}$");

    private WireGuardValidation() {
    }

    public static boolean publicKey(String value) {
        return value != null && PUBLIC_KEY.matcher(value).matches();
    }

    public static boolean ipv4(String value) {
        if (value == null || value.length() > 15 || !IPV4.matcher(value).matches()) {
            return false;
        }
        try {
            return InetAddress.getByName(value).getAddress().length == 4;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean ipv4Cidr(String value) {
        if (value == null || value.length() > 18) {
            return false;
        }
        String[] parts = value.split("/", -1);
        if (parts.length != 2 || !IPV4.matcher(parts[0]).matches()) {
            return false;
        }
        try {
            int prefix = Integer.parseInt(parts[1]);
            if (prefix < 0 || prefix > 32) {
                return false;
            }
            return InetAddress.getByName(parts[0]).getAddress().length == 4;
        } catch (Exception e) {
            return false;
        }
    }
}
