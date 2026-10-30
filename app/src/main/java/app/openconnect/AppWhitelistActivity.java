/* SPDX-License-Identifier: GPL-2.0-or-later */
package app.openconnect;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import app.openconnect.core.AndroidAppWhitelist;
import app.openconnect.core.AppWhitelistConfig;
import app.openconnect.core.OpenVpnService;
import app.openconnect.core.ProfileManager;
import app.openconnect.core.VpnDiagnostics;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppWhitelistActivity extends ToolbarActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Set<String> selected = new HashSet<>();
    private final List<Entry> apps = new ArrayList<>();
    private final List<Entry> visible = new ArrayList<>();
    private VpnProfile profile;
    private MaterialSwitch enabled;
    private MaterialSwitch showSystem;
    private TextView count;
    private EditText search;
    private AppAdapter adapter;
    private OpenVpnService service;
    private boolean bound;
    private boolean loaded;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((OpenVpnService.LocalBinder) binder).getService();
            findViewById(R.id.whitelist_save).setEnabled(loaded);
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            service = null;
            findViewById(R.id.whitelist_save).setEnabled(false);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        profile = ProfileManager.get(getIntent().getStringExtra(ConnectionEditorActivity.EXTRA_PROFILE_UUID));
        if (profile == null) { finish(); return; }
        setContentView(R.layout.activity_app_whitelist);
        setupToolbar(R.id.toolbar, getString(R.string.whitelist_title), true);
        enabled = findViewById(R.id.whitelist_enabled);
        showSystem = findViewById(R.id.whitelist_show_system);
        showSystem.setChecked(state != null && state.getBoolean("show_system"));
        showSystem.setOnCheckedChangeListener((button, checked) -> filter());
        count = findViewById(R.id.whitelist_count);
        search = findViewById(R.id.whitelist_search);
        AppWhitelistConfig saved = AndroidAppWhitelist.load(profile.mPrefs);
        if (state != null) {
            ArrayList<String> draft = state.getStringArrayList("selected");
            if (draft != null) selected.addAll(draft);
            enabled.setChecked(state.getBoolean("enabled"));
        } else {
            selected.addAll(saved.packages);
            enabled.setChecked(saved.enabled);
        }
        enabled.setOnCheckedChangeListener((button, checked) -> {
            if (checked && selected.isEmpty()) {
                enabled.setChecked(false);
                Toast.makeText(this, R.string.whitelist_empty, Toast.LENGTH_LONG).show();
            }
        });
        adapter = new AppAdapter();
        ListView list = findViewById(R.id.whitelist_apps);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            String name = visible.get(position).info.packageName;
            if (!selected.remove(name)) selected.add(name);
            if (selected.isEmpty()) enabled.setChecked(false);
            refreshCount();
            adapter.notifyDataSetChanged();
        });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int n, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int n) { filter(); }
            public void afterTextChanged(Editable s) { }
        });
        findViewById(R.id.whitelist_clear).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(this).setTitle(R.string.whitelist_clear)
                        .setMessage(R.string.whitelist_clear_confirm)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            selected.clear(); enabled.setChecked(false);
                            refreshCount(); adapter.notifyDataSetChanged();
                        }).show());
        findViewById(R.id.whitelist_save).setOnClickListener(v -> save());
        findViewById(R.id.whitelist_diagnostics).setOnClickListener(v ->
                startActivity(new Intent(this, VpnDiagnosticsActivity.class)));
        refreshCount();
        Intent bind = new Intent(this, OpenVpnService.class).setAction(OpenVpnService.START_SERVICE);
        bound = bindService(bind, connection, Context.BIND_AUTO_CREATE);
        worker.execute(this::loadApps);
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        List<Entry> result = new ArrayList<>();
        try {
            // Visible launcher apps plus minimal explicit service queries; never assume installation.
            Set<String> seen = new HashSet<>();
            for (ApplicationInfo info : pm.getInstalledApplications(0)) {
                if (info.enabled && !getPackageName().equals(info.packageName) && seen.add(info.packageName)) {
                    result.add(new Entry(info, info.loadLabel(pm).toString()));
                }
            }
            // Persisted selections can lack launcher entries. Probe them individually before deciding
            // availability; visibility filtering must never silently delete the saved routing intent.
            AppWhitelistConfig saved = AndroidAppWhitelist.load(profile.mPrefs);
            for (String name : saved.packages) {
                if (seen.contains(name)) continue;
                try {
                    ApplicationInfo info = pm.getApplicationInfo(name, 0);
                    if (seen.add(name)) result.add(new Entry(info, info.loadLabel(pm).toString()));
                } catch (PackageManager.NameNotFoundException | SecurityException e) {
                    // Kept in selected. Save/connection will fail explicitly until user resolves it.
                    VpnDiagnostics.current().catalogUnavailable(name, e.getClass());
                }
            }
            Collator collator = Collator.getInstance();
            result.sort((a, b) -> collator.compare(a.label, b.label));
        } catch (RuntimeException e) {
            VpnDiagnostics.current().failure("app_catalog", e.getClass());
            runOnUiThread(() -> {
                if (!isDestroyed()) ((TextView) findViewById(R.id.whitelist_loading))
                        .setText(R.string.whitelist_load_failed);
            });
            return;
        }
        runOnUiThread(() -> {
            if (isDestroyed() || isFinishing()) return;
            apps.clear(); apps.addAll(result);
            loaded = true;
            findViewById(R.id.whitelist_save).setEnabled(service != null);
            Set<String> available = new HashSet<>();
            for (Entry e : apps) available.add(e.info.packageName);
            Set<String> unavailable = new HashSet<>(selected);
            unavailable.removeAll(available);
            if (!unavailable.isEmpty()) showUnavailable(unavailable);
            refreshCount(); filter();
        });
    }

    private void refreshCount() { count.setText(getString(R.string.whitelist_count, selected.size())); }

    private void filter() {
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        visible.clear();
        for (Entry e : apps) {
            boolean system = (e.info.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
            if (system && !showSystem.isChecked() && !selected.contains(e.info.packageName)) continue;
            if (e.label.toLowerCase(Locale.ROOT).contains(query)
                    || e.info.packageName.toLowerCase(Locale.ROOT).contains(query)) visible.add(e);
        }
        adapter.notifyDataSetChanged();
        if (loaded) {
            TextView empty = findViewById(R.id.whitelist_loading);
            empty.setText(R.string.whitelist_no_matches);
            empty.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void save() {
        Set<String> valid = new HashSet<>();
        for (String name : selected) {
            if (AndroidAppWhitelist.isInstalled(getPackageManager(), name)) valid.add(name);
        }
        if (enabled.isChecked() && valid.isEmpty()) {
            new MaterialAlertDialogBuilder(this).setMessage(R.string.whitelist_empty)
                    .setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        if (!valid.equals(selected)) {
            Set<String> unavailable = new HashSet<>(selected);
            unavailable.removeAll(valid);
            showUnavailable(unavailable);
            return;
        }
        AppWhitelistConfig config = new AppWhitelistConfig(enabled.isChecked(), valid);
        VpnDiagnostics.selection(profile.getUUIDString(), config);
        boolean reconnecting = service != null && service.profile != null
                && profile.getUUIDString().equals(service.profile.getUUIDString())
                && service.getConnectionState() != app.openconnect.core.OpenConnectManagementThread.STATE_DISCONNECTED;
        if (service == null || !service.saveAppWhitelist(profile.getUUIDString(), config)) {
            new MaterialAlertDialogBuilder(this).setMessage(R.string.whitelist_save_failed)
                    .setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        Toast.makeText(this, R.string.whitelist_saved, Toast.LENGTH_LONG).show();
        if (reconnecting) {
            // The dashboard owns the existing authentication dialogs; bring it forward.
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        }
        finish();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (enabled != null) {
            state.putStringArrayList("selected", new ArrayList<>(selected));
            state.putBoolean("enabled", enabled.isChecked());
            state.putBoolean("show_system", showSystem.isChecked());
        }
        super.onSaveInstanceState(state);
    }

    private void showUnavailable(Set<String> unavailable) {
        new MaterialAlertDialogBuilder(this).setMessage(getString(R.string.whitelist_unavailable,
                android.text.TextUtils.join(", ", unavailable)))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.whitelist_remove_unavailable, (d, w) -> {
                    selected.removeAll(unavailable);
                    // Do not silently switch an enabled whitelist to global routing.
                    refreshCount(); filter();
                }).show();
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        if (bound) unbindService(connection);
        super.onDestroy();
    }

    private static final class Entry {
        final ApplicationInfo info;
        final String label;
        Entry(ApplicationInfo info, String label) { this.info = info; this.label = label; }
    }

    private final class AppAdapter extends BaseAdapter {
        public int getCount() { return visible.size(); }
        public Entry getItem(int position) { return visible.get(position); }
        public long getItemId(int position) { return position; }
        public View getView(int position, View recycled, ViewGroup parent) {
            View view = recycled == null ? LayoutInflater.from(AppWhitelistActivity.this)
                    .inflate(R.layout.row_app_whitelist, parent, false) : recycled;
            Entry entry = getItem(position);
            ((TextView) view.findViewById(R.id.whitelist_name)).setText(entry.label);
            ((TextView) view.findViewById(R.id.whitelist_package)).setText(entry.info.packageName
                    + " · UID " + entry.info.uid);
            ((ImageView) view.findViewById(R.id.whitelist_icon))
                    .setImageDrawable(entry.info.loadIcon(getPackageManager()));
            ((CheckBox) view.findViewById(R.id.whitelist_checked))
                    .setChecked(selected.contains(entry.info.packageName));
            view.setContentDescription(entry.label + ", " + entry.info.packageName + ", "
                    + getString(selected.contains(entry.info.packageName)
                    ? R.string.whitelist_selected : R.string.whitelist_not_selected));
            return view;
        }
    }
}
