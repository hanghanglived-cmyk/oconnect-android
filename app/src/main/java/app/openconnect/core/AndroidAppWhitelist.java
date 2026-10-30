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
            VpnDiagnostics.current().catalogUnavailable(name, e.getClass());
            return false;
        }
    }

    public static int apply(AppWhitelistConfig config, VpnService.Builder builder, PackageManager pm)
            throws AppWhitelistConfig.EmptyWhitelistException {
        return apply(config, builder, pm, VpnDiagnostics.current());
    }

    public static int apply(AppWhitelistConfig config, VpnService.Builder builder, PackageManager pm,
            VpnDiagnostics diagnostics) throws AppWhitelistConfig.EmptyWhitelistException {
        diagnostics.rules(config);
        return config.apply(name -> {
            try {
                android.content.pm.ApplicationInfo info = pm.getApplicationInfo(name, 0);
                if (!info.enabled) {
                    diagnostics.rejected(name, "disabled");
                    return false;
                }
                builder.addAllowedApplication(name);
                diagnostics.accepted(name, info.uid);
                return true;
            } catch (PackageManager.NameNotFoundException | IllegalArgumentException
                    | IllegalStateException | SecurityException e) {
                // Do not persist raw exception messages: OEM messages can contain private data.
                diagnostics.rejected(name, e.getClass().getSimpleName());
                return false;
            }
        });
    }
}
