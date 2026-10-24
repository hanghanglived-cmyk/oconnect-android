/*
 * Copyright (c) 2013, Kevin Cernekee
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 * In addition, as a special exception, the copyright holders give
 * permission to link the code of portions of this program with the
 * OpenSSL library.
 */

package app.openconnect.fragments;

import java.util.HashMap;
import java.util.Map;

import app.openconnect.ConnectionEditorActivity;
import app.openconnect.PreferenceScreenStyler;
import app.openconnect.R;
import app.openconnect.ShowTextPreference;
import app.openconnect.TokenImportActivity;
import app.openconnect.VpnProfile;
import app.openconnect.core.ProfileManager;

import android.app.AlertDialog;
import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;

public class ConnectionEditorFragment extends PreferenceFragment
		implements OnSharedPreferenceChangeListener {

	PreferenceManager mPrefs;
	VpnProfile mProfile;
	String mUUID;
    String mScreen;

    HashMap<String,Integer> fileSelectMap = new HashMap<String,Integer>();

    private final int IDX_TOKEN_STRING = 65536;

	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mProfile = ProfileManager.get(getArguments().getString("profileUUID"));
        mUUID = mProfile.getUUIDString();

        mPrefs = getPreferenceManager();
        mPrefs.setSharedPreferencesName(ProfileManager.getPrefsName(mUUID));
        mPrefs.setSharedPreferencesMode(Context.MODE_PRIVATE);

        mScreen = getArguments().getString(ConnectionEditorActivity.EXTRA_SCREEN,
                ConnectionEditorActivity.SCREEN_MAIN);
        if (ConnectionEditorActivity.SCREEN_AUTHENTICATION.equals(mScreen)) {
            addPreferencesFromResource(R.xml.pref_openconnect_authentication);
        } else if (ConnectionEditorActivity.SCREEN_ADVANCED.equals(mScreen)) {
            addPreferencesFromResource(R.xml.pref_openconnect_advanced);
        } else {
            addPreferencesFromResource(R.xml.pref_openconnect);
        }

        setClickListeners();
        configureNavigation();

        SharedPreferences sp = mPrefs.getSharedPreferences();
        for (Map.Entry<String,?> entry : sp.getAll().entrySet()) {
            updatePref(sp, entry.getKey());
        }
        for (String key : new String[] {
                "profile_name", "server_address", "ca_certificate", "user_certificate",
                "private_key", "software_token", "token_string", "batch_mode",
                "reported_os", "custom_csd_wrapper", "split_tunnel_mode",
                "split_tunnel_networks", "dpd_value"
        }) {
            updatePref(sp, key);
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        PreferenceScreenStyler.apply(this);
    }

    private void configureNavigation() {
        Preference appRules = findPreference("pref_key_app_whitelist");
        if (appRules != null) appRules.setOnPreferenceClickListener(clicked -> {
            Intent intent = new Intent(getActivity(), app.openconnect.AppWhitelistActivity.class);
            intent.putExtra(ConnectionEditorActivity.EXTRA_PROFILE_UUID, mUUID);
            startActivity(intent);
            return true;
        });
        configureNavigationPreference("pref_key_authentication",
                ConnectionEditorActivity.SCREEN_AUTHENTICATION);
        configureNavigationPreference("pref_key_advanced",
                ConnectionEditorActivity.SCREEN_ADVANCED);
    }

    private void configureNavigationPreference(String key, String screen) {
        Preference preference = findPreference(key);
        if (preference == null) {
            return;
        }
        preference.setOnPreferenceClickListener(clicked -> {
            Intent intent = new Intent(getActivity(), ConnectionEditorActivity.class);
            intent.putExtra(ConnectionEditorActivity.EXTRA_PROFILE_UUID, mUUID);
            intent.putExtra(ConnectionEditorActivity.EXTRA_SCREEN, screen);
            startActivity(intent);
            return true;
        });
    }

    @Override
	public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    private void updatePref(SharedPreferences sp, String key) {
        String value;
        try {
            value = sp.getString(key, "");
        } catch (ClassCastException e) {
            /* wasn't a string preference */
            return;
        }

        Preference pref = findPreference(key);
        if (pref != null) {
			if (pref instanceof ListPreference) {
				/* update all spinner prefs so the summary shows the current value */
				ListPreference lpref = (ListPreference)pref;
				if (value.equals("")) {
					value = lpref.getValue();
				}
				if (value == null && key.equals("batch_mode")) {
					value = "disabled";
				}
				if (value == null) {
					return;
				}
				lpref.setValue(value);
				if (key.equals("batch_mode")) {
					pref.setSummary(getBatchModeSummary(value));
				} else {
					pref.setSummary(lpref.getEntry());
				}
			} else {
				/* for ShowTextPreference entries, hide the filename */
				if (fileSelectMap.containsKey(key)) {
                    pref.setSummary(value.isEmpty()
                            ? getString(R.string.not_configured)
                            : getString(R.string.stored));
                } else if (pref instanceof ShowTextPreference && value.isEmpty()) {
                    pref.setSummary(R.string.not_configured);
				} else {
					pref.setSummary(value);
				}
			}
			if (pref instanceof EditTextPreference) {
				final EditTextPreference etpref = (EditTextPreference)pref;
				etpref.getEditText().setOnEditorActionListener(new TextView.OnEditorActionListener() {
					@Override
					public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
						if (actionId == EditorInfo.IME_ACTION_DONE ||
								(keyEvent != null &&
										keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER &&
										keyEvent.getAction() == KeyEvent.ACTION_DOWN)) {
							etpref.onClick(etpref.getDialog(), Dialog.BUTTON_POSITIVE);
							etpref.getDialog().dismiss();
							return true;
						} else {
							return false;
						}
					}
				});
			}
        }

        /* disable token_string item if the profile isn't using a software token */ 
        if (key.equals("software_token")) {
            pref = findPreference("token_string");
            if (pref != null) {
                pref.setEnabled(!value.equals("disabled"));
            }
        }

        /* similarly, if split tunnel is "auto", ignore manually entered subnets */
        if (key.equals("split_tunnel_mode")) {
            pref = findPreference("split_tunnel_networks");
            if (pref != null) {
                pref.setEnabled(!value.equals("auto"));
            }
        }

        if (key.equals("profile_name")) {
			mProfile.mName = value;
        	((ConnectionEditorActivity)getActivity()).setProfileName(value);
        }
    }

	private String getBatchModeSummary(String value) {
		if ("empty_only".equals(value)) {
			return getString(R.string.batch_mode_empty_only_summary);
		} else if ("enabled".equals(value)) {
			return getString(R.string.batch_mode_enabled_summary);
		}
		return getString(R.string.batch_mode_disabled_summary);
	}

	public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
		updatePref(sp, key);
	}

	private void setClickListeners() {
		for (int idx = 0; idx < ProfileManager.fileSelectKeys.length; idx++) {
			String key = ProfileManager.fileSelectKeys[idx];
			Preference p = findPreference(key);
            if (p == null) {
                continue;
            }
			fileSelectMap.put(key, idx);

			p.setOnPreferenceClickListener(new OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					Integer idx = fileSelectMap.get(preference.getKey());
					if (idx == null) {
						return false;
					}

					String value = mPrefs.getSharedPreferences().getString(
							preference.getKey(), "");
					if (value.isEmpty()) {
						launchFilePicker(idx);
					} else {
						new AlertDialog.Builder(getActivity())
								.setTitle(preference.getTitle())
								.setItems(new CharSequence[] {
										getString(R.string.select_file),
										getString(R.string.clear)
								}, (dialog, which) -> {
									if (which == 0) {
										launchFilePicker(idx);
									} else {
										clearFilePreference(preference.getKey());
									}
								})
								.setNegativeButton(android.R.string.cancel, null)
								.show();
					}
					return true;
				}
			});
		}

		Preference p = findPreference("token_string");
        if (p == null) {
            return;
        }
		/* The TokenImport activity will set the token_string preference for us */
		p.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				Intent intent = new Intent(getActivity(), TokenImportActivity.class);
				intent.putExtra(TokenImportActivity.EXTRA_UUID, mUUID);
				startActivityForResult(intent, IDX_TOKEN_STRING);
				return false;
			}
		});
	}

	private void launchFilePicker(int requestCode) {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("*/*");
		startActivityForResult(intent, requestCode);
	}

	private void clearFilePreference(String key) {
		ProfileManager.deleteFilePref(mProfile, key);
		ShowTextPreference preference = (ShowTextPreference)findPreference(key);
		preference.setText(null);
		updatePref(mPrefs.getSharedPreferences(), key);
	}

	@Override
	public void onActivityResult(int idx, int resultCode, Intent data) {
		super.onActivityResult(idx, resultCode, data);

		if (resultCode != Activity.RESULT_OK) {
			return;
		}

		SharedPreferences prefs = mPrefs.getSharedPreferences();
		if (idx >= IDX_TOKEN_STRING) {
			updatePref(prefs, "token_string");
			updatePref(prefs, "software_token");
		} else {
			String key = ProfileManager.fileSelectKeys[idx];
			ShowTextPreference p = (ShowTextPreference)findPreference(key);
			Uri uri = data == null ? null : data.getData();
			if (uri == null) {
				return;
			}

			String storedPath = ProfileManager.storeFilePref(
					mProfile, key, getActivity().getContentResolver(), uri);
			if (storedPath == null) {
				Toast.makeText(getActivity(), R.string.import_error_message,
						Toast.LENGTH_LONG).show();
				return;
			}
			p.setText(storedPath);
			updatePref(prefs, key);
		}
	}
}
