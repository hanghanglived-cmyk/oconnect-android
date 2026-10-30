/* SPDX-License-Identifier: GPL-2.0-or-later */
package app.openconnect.core;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Immutable per-profile snapshot; never mutate a SharedPreferences string set. */
public final class AppWhitelistConfig {
    public static final String ENABLED = "app_whitelist_enabled";
    public static final String PACKAGES = "app_whitelist_packages";
    public final boolean enabled;
    public final Set<String> packages;

    public AppWhitelistConfig(boolean enabled, Set<String> packages) {
        this.enabled = enabled;
        this.packages = Collections.unmodifiableSet(new HashSet<>(packages));
    }

    public interface RuleTarget {
        /** False means uninstalled, malformed, or unavailable to this Android user. */
        boolean allow(String packageName);
    }

    public static class EmptyWhitelistException extends Exception { }

    /** No partially applied list may reach establish(): that changes the user's intent. */
    public static final class IncompleteWhitelistException extends EmptyWhitelistException { }

    /** Must finish successfully before establish(); never fall back to all-app VPN. */
    public int apply(RuleTarget target) throws EmptyWhitelistException {
        if (!enabled) return 0;
        int accepted = 0;
        boolean rejected = false;
        for (String name : packages) {
            if (isPackageName(name) && target.allow(name)) accepted++;
            else rejected = true;
        }
        if (accepted == 0) throw new EmptyWhitelistException();
        if (rejected) throw new IncompleteWhitelistException();
        return accepted;
    }

    public static boolean isPackageName(String name) {
        return name != null && name.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");
    }

    public boolean hasSameRoutingAs(AppWhitelistConfig other) {
        return enabled == other.enabled && (!enabled || packages.equals(other.packages));
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof AppWhitelistConfig)) return false;
        AppWhitelistConfig c = (AppWhitelistConfig) other;
        return enabled == c.enabled && packages.equals(c.packages);
    }

    @Override public int hashCode() { return packages.hashCode() * 31 + (enabled ? 1 : 0); }
}
