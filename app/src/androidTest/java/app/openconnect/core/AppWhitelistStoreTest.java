/* SPDX-License-Identifier: GPL-2.0-or-later */
package app.openconnect.core;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.util.Arrays;
import java.util.HashSet;
import static org.junit.Assert.*;

public class AppWhitelistStoreTest {
    @Test public void persistsAcrossReloadAndIsolatesProfiles() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences first = context.getSharedPreferences("whitelist-test-a", Context.MODE_PRIVATE);
        SharedPreferences second = context.getSharedPreferences("whitelist-test-b", Context.MODE_PRIVATE);
        try {
            first.edit().clear().commit(); second.edit().clear().commit();
            assertFalse(AndroidAppWhitelist.load(first).enabled);
            AppWhitelistConfig config = new AppWhitelistConfig(true,
                    new HashSet<>(Arrays.asList("com.example.app")));
            assertTrue(AndroidAppWhitelist.save(first, config));
            assertEquals(config, AndroidAppWhitelist.load(context.getSharedPreferences(
                    "whitelist-test-a", Context.MODE_PRIVATE)));
            assertFalse(AndroidAppWhitelist.load(second).enabled);
            assertTrue(AndroidAppWhitelist.load(second).packages.isEmpty());
            assertTrue(AndroidAppWhitelist.save(first, new AppWhitelistConfig(false, config.packages)));
            assertFalse(AndroidAppWhitelist.load(first).enabled);
            assertEquals(config.packages, AndroidAppWhitelist.load(first).packages);
        } finally {
            first.edit().clear().commit(); second.edit().clear().commit();
        }
    }
}
