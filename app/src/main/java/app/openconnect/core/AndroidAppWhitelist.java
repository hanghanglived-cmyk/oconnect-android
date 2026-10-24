/* SPDX-License-Identifier: GPL-2.0-or-later */
package app.openconnect.core;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.VpnService;
import java.util.HashSet;

public final class AndroidAppWhitelist {
    private AndroidAppWhitelist() { }

    public static AppWhitelistConfig load(SharedPreferences prefs) {
        // getAll() gives one consistent snapshot of both fields during concurrent saves.
        java.util.Map<String, ?> values = prefs.getAll();
        Object enabled = values.get(AppWhitelistConfig.ENABLED);
        Object names = values.get(AppWhitelistConfig.PACKAGES);
        HashSet<String> packages = new HashSet<>();
        if (names instanceof java.util.Set<?>) {
            for (Object name : (java.util.Set<?>) names) {
                if (name instanceof String) packages.add((String) name);
            }
        }
        return new AppWhitelistConfig(Boolean.TRUE.equals(enabled), packages);
    }

    public static boolean save(SharedPreferences prefs, AppWhitelistConfig config) {
        return prefs.edit().putBoolean(AppWhitelistConfig.ENABLED, config.enabled)
                .putStringSet(AppWhitelistConfig.PACKAGES, new HashSet<>(config.packages)).commit();
    }

    public static boolean isInstalled(PackageManager pm, String name) {
        if (!AppWhitelistConfig.isPackageName(name)) return false;
        try {
            return pm.getApplicationInfo(name, 0).enabled;
        } catch (PackageManager.NameNotFoundException | IllegalArgumentException | SecurityException e) {
            return false;
        }
    }

    public static int apply(AppWhitelistConfig config, VpnService.Builder builder, PackageManager pm)
            throws AppWhitelistConfig.EmptyWhitelistException {
        return config.apply(name -> {
            if (!isInstalled(pm, name)) return false;
            try {
                builder.addAllowedApplication(name);
                return true;
            } catch (PackageManager.NameNotFoundException | IllegalArgumentException | SecurityException e) {
                return false;
            }
        });
    }
}
