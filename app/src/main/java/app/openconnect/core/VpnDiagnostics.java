/* SPDX-License-Identifier: GPL-2.0-or-later */
package app.openconnect.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/** Technical fields only. Never feed authentication logs, hostnames or exception messages here. */
public final class VpnDiagnostics {
    private static final String SALT = UUID.randomUUID().toString();
    private static final ArrayDeque<VpnDiagnostics> RECENT = new ArrayDeque<>();
    private static final Map<String, AppWhitelistConfig> UI = new LinkedHashMap<>(16, 0.75f, true);
    private static volatile VpnDiagnostics current = new VpnDiagnostics("none", "not_started");
    private final String generation = UUID.randomUUID().toString();
    private final String profile;
    private final String reason;
    private final List<String> events = new ArrayList<>();
    private final Map<String, String> network = new TreeMap<>();
    private final TreeSet<String> accepted = new TreeSet<>();
    private String state = "not_started";
    private String lastError = "none";
    private String lastWhitelistError = "none";
    private boolean interfaceEstablished;
    private boolean enabled;

    private VpnDiagnostics(String profile, String reason) {
        this.profile = profile;
        this.reason = reason;
    }

    public static synchronized VpnDiagnostics begin(String uuid, String reason, AppWhitelistConfig config) {
        VpnDiagnostics d = new VpnDiagnostics(token(uuid), reason);
        AppWhitelistConfig draft = UI.get(d.profile);
        d.event("ui_selection=" + (draft == null ? "not_observed_in_this_process" : names(draft)));
        d.event("loaded_selection=" + names(config));
        d.enabled = config.enabled;
        current = d;
        RECENT.addLast(d);
        while (RECENT.size() > 6) RECENT.removeFirst();
        return d;
    }

    public static synchronized void selection(String uuid, AppWhitelistConfig config) {
        UI.put(token(uuid), config);
        // The page only needs the most recent few profiles; do not retain an installed-app inventory.
        while (UI.size() > 8) UI.remove(UI.keySet().iterator().next());
    }

    public static VpnDiagnostics current() { return current; }

    public synchronized void rules(AppWhitelistConfig config) {
        enabled = config.enabled;
        event("validated_candidates=" + names(config));
        for (String name : config.packages) {
            if (!AppWhitelistConfig.isPackageName(name)) event("rejected=<invalid_package>: malformed");
        }
    }

    public synchronized void accepted(String name, int uid) {
        accepted.add(name);
        event("addAllowedApplication=" + name + " uid=" + uid);
    }

    public synchronized void rejected(String name, String category) {
        String value = AppWhitelistConfig.isPackageName(name) ? name : "<invalid_package>";
        lastError = "allow_failed=" + value + " reason=" + category;
        lastWhitelistError = lastError;
        event(lastError);
    }

    public synchronized void catalogUnavailable(String name, Class<?> type) {
        event("catalog_unavailable=" + (AppWhitelistConfig.isPackageName(name) ? name : "<invalid_package>")
                + " reason=" + type.getSimpleName());
    }

    public synchronized void state(int value) { state = Integer.toString(value); }

    public synchronized void failure(String stage, Class<?> type) {
        lastError = stage + ": " + type.getSimpleName();
        event(lastError);
    }

    public synchronized void connectionFailed() {
        if ("none".equals(lastError)) lastError = "connection_failed (authentication detail omitted)";
        event("connection_terminated_with_error");
    }

    public synchronized void established(boolean success) {
        interfaceEstablished = success;
        if (!success) lastError = "establish_returned_null";
        event("establish=" + success);
    }

    public synchronized void transportProtected(boolean success) {
        event("outer_socket_protect=" + success);
    }

    public synchronized void internalAllowed(String packageName) {
        event("internal_keepalive_package=" + packageName);
    }

    public synchronized void address(String kind, String value) {
        // IPs/prefixes only; exclude hostnames, zones, credentials, controls and raw server text.
        if (value != null && value.length() <= 80 && value.matches("[0-9A-Fa-f.:/]+")) {
            String key = kind + ":" + value;
            if (network.size() < 256) network.put(key, value);
        }
    }

    public synchronized void number(String key, int value) { network.put(key, Integer.toString(value)); }

    private synchronized void event(String value) {
        if (events.size() == 256) events.remove(0);
        events.add(value);
    }

    private synchronized String render() {
        return "generation=" + generation + "\nprofile=" + profile + "\nrebuild_reason=" + reason
                + "\nstate=" + state + " (1=auth,2=prompt,3=authenticated,4=connecting,5=connected,6=disconnected)"
                + "\nmode=" + (enabled ? "whitelist" : "global")
                + "\nadded_app_count=" + accepted.size() + "\nadded_packages=" + accepted
                + "\neffective_user_app_count=" + (enabled && interfaceEstablished && "5".equals(state) ? accepted.size() : 0)
                + "\nNote: added packages are effective only after establish=true and state=5."
                + "\nInternal VPN app is added separately for the existing DNS keepalive."
                + "\nlast_error=" + lastError + "\nlast_whitelist_error=" + lastWhitelistError
                + "\nnetwork_configuration=" + network
                + "\n" + String.join("\n", events) + "\n";
    }

    /** Already allowlisted at collection time; export never reads the legacy authentication log. */
    public static synchronized String snapshot() {
        StringBuilder out = new StringBuilder("OConnect VPN diagnostics v1\nIn-memory only; no automatic upload.\n");
        if (RECENT.isEmpty()) out.append(current.render());
        for (VpnDiagnostics d : RECENT) out.append("\n---\n").append(d.render());
        return out.toString();
    }

    private static String names(AppWhitelistConfig config) {
        TreeSet<String> safe = new TreeSet<>();
        for (String name : config.packages) if (AppWhitelistConfig.isPackageName(name)) safe.add(name);
        return "enabled=" + config.enabled + " packages=" + safe;
    }

    private static String token(String uuid) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((SALT + uuid).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < 6; i++) out.append(String.format(java.util.Locale.ROOT, "%02x", digest[i]));
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
