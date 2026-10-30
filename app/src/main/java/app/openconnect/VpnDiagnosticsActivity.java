/* SPDX-License-Identifier: GPL-2.0-or-later */
package app.openconnect;

import android.app.Activity;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import app.openconnect.core.VpnDiagnostics;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Read-only diagnostics. SAF export requires the user to choose a local document. */
public class VpnDiagnosticsActivity extends ToolbarActivity {
    private static final int EXPORT = 41;
    private String pendingExport;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_vpn_diagnostics);
        setupToolbar(R.id.toolbar, getString(R.string.vpn_diagnostics_title), true);
        findViewById(R.id.diagnostics_refresh).setOnClickListener(v -> refresh());
        findViewById(R.id.diagnostics_export).setOnClickListener(v -> {
            pendingExport = report();
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain")
                    .putExtra(Intent.EXTRA_TITLE, "oconnect-vpn-diagnostics.txt");
            try {
                startActivityForResult(intent, EXPORT);
            } catch (android.content.ActivityNotFoundException e) {
                pendingExport = null;
                Toast.makeText(this, R.string.diagnostics_export_failed, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void refresh() { ((TextView) findViewById(R.id.diagnostics_text)).setText(report()); }

    private String report() {
        StringBuilder out = new StringBuilder(VpnDiagnostics.snapshot());
        out.append("\nObserved Android network summary (read only; may include another active VPN)\nAPI=")
                .append(Build.VERSION.SDK_INT);
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return out.append("\nConnectivityManager unavailable\n").toString();
        try {
            boolean found = false;
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                if (caps == null) continue;
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    // Do not output SSID, carrier name, subscription ID, owner or VPN server address.
                    out.append("\nphysical_candidate: wifi=")
                            .append(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                            .append(" cellular=").append(caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                            .append(" validated=").append(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
                    continue;
                }
                found = true;
                out.append("\nvpn: internet=").append(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                        .append(" validated=").append(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                        .append(" not_vpn=").append(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN));
                LinkProperties lp = cm.getLinkProperties(n);
                if (lp != null) {
                    out.append("\ninterface=").append(lp.getInterfaceName())
                            .append("\naddresses=").append(lp.getLinkAddresses())
                            .append("\nroutes=").append(lp.getRoutes())
                            .append("\ndns=").append(lp.getDnsServers())
                            .append("\nsearch_domains=").append(lp.getDomains() == null ? "absent" : "present (redacted)");
                    if (Build.VERSION.SDK_INT >= 29) out.append("\nmtu=").append(lp.getMtu());
                    if (Build.VERSION.SDK_INT >= 28) out.append("\nprivate_dns_active=").append(lp.isPrivateDnsActive());
                }
                // The app never calls setUnderlyingNetworks(). Do not use hidden platform APIs
                // or infer the actual VPN transport from the list of physical candidates above.
                out.append("\nunderlying_binding=system-managed (no explicit app binding; actual link not exposed here)");
            }
            out.append("\nvpn_network_visible_to_this_process=").append(found);
            out.append("\nprocess_bound_to_network=").append(cm.getBoundNetworkForProcess() != null);
            out.append("\nPer-app traffic paths and other apps' DNS usage require device/gateway evidence.\n");
        } catch (SecurityException e) {
            out.append("\nnetwork_observation=SecurityException\n");
        }
        return out.toString();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("pending_export", pendingExport);
        super.onSaveInstanceState(state);
    }

    @Override protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        pendingExport = state.getString("pending_export");
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != EXPORT) return;
        if (result == Activity.RESULT_OK && data != null && data.getData() != null && pendingExport != null) {
            try (OutputStream stream = getContentResolver().openOutputStream(data.getData())) {
                if (stream == null) throw new java.io.IOException("No output stream");
                stream.write(pendingExport.getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, R.string.diagnostics_exported, Toast.LENGTH_LONG).show();
            } catch (java.io.IOException | SecurityException e) {
                Toast.makeText(this, R.string.diagnostics_export_failed, Toast.LENGTH_LONG).show();
            }
        }
        pendingExport = null;
    }
}
