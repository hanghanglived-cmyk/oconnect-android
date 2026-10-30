/* SPDX-License-Identifier: GPL-2.0-or-later */
package app.openconnect.core;

import org.junit.Test;
import java.util.Arrays;
import java.util.HashSet;
import static org.junit.Assert.*;

public class VpnDiagnosticsTest {
    @Test public void exportedFieldsExcludeProfileSecretsAndMalformedInput() {
        String secret = "cookie=private-token\npassword=secret";
        AppWhitelistConfig rules = new AppWhitelistConfig(true,
                new HashSet<>(Arrays.asList("com.openai.chatgpt", secret)));
        VpnDiagnostics.selection(secret, rules);
        VpnDiagnostics d = VpnDiagnostics.begin(secret, "connection_requested", rules);
        d.rules(rules);
        d.address("dns", secret);
        d.address("dns", "1.1.1.1");
        d.rejected(secret, "NameNotFoundException");
        d.failure("establish", SecurityException.class);
        String report = VpnDiagnostics.snapshot();
        assertFalse(report.contains("private-token"));
        assertFalse(report.contains("password=secret"));
        assertTrue(report.contains("com.openai.chatgpt"));
        assertTrue(report.contains("1.1.1.1"));
        assertTrue(report.contains("SecurityException"));
    }

    @Test public void generationsKeepSeparateGlobalAndWhitelistEvidence() {
        AppWhitelistConfig rules = new AppWhitelistConfig(true,
                new HashSet<>(Arrays.asList("com.openai.chatgpt", "com.google.android.gms")));
        VpnDiagnostics d = VpnDiagnostics.begin("test-profile", "whitelist_saved", rules);
        d.accepted("com.openai.chatgpt", 10123);
        d.accepted("com.google.android.gms", 10124);
        d.established(true);
        d.state(5);
        VpnDiagnostics.begin("test-profile", "connection_requested", new AppWhitelistConfig(false, rules.packages));
        String report = VpnDiagnostics.snapshot();
        assertTrue(report.contains("added_app_count=2"));
        assertTrue(report.contains("mode=global"));
        assertTrue(report.contains("mode=whitelist"));
        assertTrue(report.contains("establish=true"));
    }
}
