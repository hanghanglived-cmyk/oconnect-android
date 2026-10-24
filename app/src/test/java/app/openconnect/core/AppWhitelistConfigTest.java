/* SPDX-License-Identifier: GPL-2.0-or-later */
package app.openconnect.core;

import org.junit.Test;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class AppWhitelistConfigTest {
    private Set<String> names(String... values) { return new HashSet<>(Arrays.asList(values)); }

    @Test public void disabledDoesNotTouchBuilderEvenWithStoredSelections() throws Exception {
        assertEquals(0, new AppWhitelistConfig(false, names("com.example.app"))
                .apply(name -> { throw new AssertionError("Must not call addAllowedApplication"); }));
    }

    @Test(expected = AppWhitelistConfig.EmptyWhitelistException.class)
    public void enabledEmptyCannotEstablish() throws Exception {
        new AppWhitelistConfig(true, names()).apply(name -> true);
    }

    @Test public void skipsMalformedNamesBeforeCallingAndroid() throws Exception {
        Set<String> called = new HashSet<>();
        AppWhitelistConfig config = new AppWhitelistConfig(true,
                names("com.example.app", "bad/name", "", "com..bad", "com.example;", null));
        assertEquals(1, config.apply(name -> { called.add(name); return true; }));
        assertEquals(names("com.example.app"), called);
    }

    @Test public void uninstallRaceSkipsMissingPackageAndKeepsValidRules() throws Exception {
        AppWhitelistConfig config = new AppWhitelistConfig(true, names("com.example.gone", "com.example.app"));
        assertEquals(1, config.apply(name -> name.equals("com.example.app")));
    }

    @Test(expected = AppWhitelistConfig.EmptyWhitelistException.class)
    public void allUninstalledMustNotFallBackToFullDeviceVpn() throws Exception {
        new AppWhitelistConfig(true, names("com.example.gone")).apply(name -> false);
    }

    @Test public void immutableSnapshotSurvivesCallerEdits() {
        Set<String> draft = names("com.example.app");
        AppWhitelistConfig saved = new AppWhitelistConfig(true, draft);
        draft.clear();
        assertEquals(names("com.example.app"), saved.packages);
    }

    @Test public void freshConnectionReappliesSnapshotToNewBuilder() throws Exception {
        AppWhitelistConfig saved = new AppWhitelistConfig(true, names("com.example.one", "com.example.two"));
        Set<String> first = new HashSet<>(), second = new HashSet<>();
        saved.apply(first::add);
        saved.apply(second::add);
        assertEquals(first, second);
        assertEquals(2, second.size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void loadedSelectionsCannotBeMutated() {
        new AppWhitelistConfig(true, names("com.example.app")).packages.clear();
    }

    @Test public void changingDisabledSelectionsDoesNotRequireTunnelRestart() {
        assertTrue(new AppWhitelistConfig(false, names("com.example.one"))
                .hasSameRoutingAs(new AppWhitelistConfig(false, names("com.example.two"))));
    }

    @Test public void changingEnabledRulesOrModeRequiresNewTunnel() {
        AppWhitelistConfig old = new AppWhitelistConfig(true, names("com.example.one"));
        assertFalse(old.hasSameRoutingAs(new AppWhitelistConfig(true, names("com.example.two"))));
        assertFalse(old.hasSameRoutingAs(new AppWhitelistConfig(false, old.packages)));
    }
}
