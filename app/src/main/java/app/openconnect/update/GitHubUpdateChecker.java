/*
 * Copyright (c) 2026 OConnect contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 */

package app.openconnect.update;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import app.openconnect.BuildConfig;
import app.openconnect.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GitHubUpdateChecker {
	public static final String PREF_AUTO_CHECK = "auto_check_updates";
	public static final String PREF_LAST_CHECK = "update_last_check";
	public static final String PREF_LAST_VERSION = "update_last_version";

	private static final String TAG = "GitHubUpdateChecker";
	private static final String LATEST_RELEASE_API =
			"https://api.github.com/repos/pengyue-polaron/openconnect-next-android/releases/latest";
	private static final long AUTOMATIC_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L;
	private static final String PREF_LAST_PROMPTED = "update_last_prompted";
	private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
	private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
	private static final AtomicBoolean CHECK_IN_PROGRESS = new AtomicBoolean();

	public interface Listener {
		void onFinished(CheckResult result, Exception error);
	}

	public static final class CheckResult {
		public final String latestVersion;
		public final boolean updateAvailable;

		private CheckResult(String latestVersion, boolean updateAvailable) {
			this.latestVersion = latestVersion;
			this.updateAvailable = updateAvailable;
		}
	}

	private static final class Release {
		private final String version;
		private final String name;
		private final String notes;
		private final String releaseUrl;
		private final String downloadUrl;

		private Release(String version, String name, String notes,
				String releaseUrl, String downloadUrl) {
			this.version = version;
			this.name = name;
			this.notes = notes;
			this.releaseUrl = releaseUrl;
			this.downloadUrl = downloadUrl;
		}
	}

    private static boolean isWhitelistBuild() {
        return BuildConfig.APPLICATION_ID.equals("io.github.hanghanglived.oconnect.whitelist");
    }

	private GitHubUpdateChecker() {
	}

	public static void checkAutomatically(Activity activity) {
        if (isWhitelistBuild()) return;
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
		if (!preferences.getBoolean(PREF_AUTO_CHECK, true)) {
			return;
		}
		long now = System.currentTimeMillis();
		long lastCheck = preferences.getLong(PREF_LAST_CHECK, 0);
		if (now - lastCheck < AUTOMATIC_CHECK_INTERVAL_MS) {
			return;
		}
		check(activity, false, null);
	}

	public static void checkManually(Activity activity) {
		Toast.makeText(activity, R.string.update_checking, Toast.LENGTH_SHORT).show();
		check(activity, true, null);
	}

	public static void check(Activity activity, boolean manual, Listener listener) {
        if (isWhitelistBuild()) {
            Toast.makeText(activity, R.string.whitelist_updates_disabled, Toast.LENGTH_LONG).show();
            if (listener != null) listener.onFinished(null,
                    new IOException(activity.getString(R.string.whitelist_updates_disabled)));
            return;
        }
		if (!CHECK_IN_PROGRESS.compareAndSet(false, true)) {
			if (manual && isUsable(activity)) {
				Toast.makeText(activity, R.string.update_check_already_running,
						Toast.LENGTH_SHORT).show();
			}
			if (listener != null) {
				listener.onFinished(null, new IOException(
						activity.getString(R.string.update_check_already_running)));
			}
			return;
		}

		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
		preferences.edit().putLong(PREF_LAST_CHECK, System.currentTimeMillis()).apply();

		EXECUTOR.execute(() -> {
			Release release = null;
			Exception failure = null;
			try {
				release = fetchLatestRelease();
			} catch (Exception error) {
				failure = error;
			}

			Release finalRelease = release;
			Exception finalFailure = failure;
			MAIN_HANDLER.post(() -> {
				CHECK_IN_PROGRESS.set(false);
				if (!isUsable(activity)) {
					return;
				}

				if (finalFailure != null) {
					Log.w(TAG, "Unable to check GitHub releases", finalFailure);
					if (manual) {
						showError(activity, finalFailure);
					}
					if (listener != null) {
						listener.onFinished(null, finalFailure);
					}
					return;
				}

				boolean updateAvailable =
						VersionComparator.compare(finalRelease.version, BuildConfig.VERSION_NAME) > 0;
				preferences.edit().putString(PREF_LAST_VERSION, finalRelease.version).apply();
				CheckResult result = new CheckResult(finalRelease.version, updateAvailable);

				if (updateAvailable) {
					String lastPrompted = preferences.getString(PREF_LAST_PROMPTED, "");
					if (manual || !finalRelease.version.equals(lastPrompted)) {
						preferences.edit().putString(
								PREF_LAST_PROMPTED, finalRelease.version).apply();
						showUpdate(activity, finalRelease);
					}
				} else if (manual) {
					new AlertDialog.Builder(activity)
							.setTitle(R.string.update_up_to_date_title)
							.setMessage(activity.getString(
									R.string.update_up_to_date_message, BuildConfig.VERSION_NAME))
							.setPositiveButton(android.R.string.ok, null)
							.show();
				}

				if (listener != null) {
					listener.onFinished(result, null);
				}
			});
		});
	}

	private static Release fetchLatestRelease() throws Exception {
		HttpURLConnection connection = (HttpURLConnection)new URL(LATEST_RELEASE_API).openConnection();
		connection.setConnectTimeout(10000);
		connection.setReadTimeout(15000);
		connection.setRequestProperty("Accept", "application/vnd.github+json");
		connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
		connection.setRequestProperty("User-Agent",
				"OConnect-Android/" + BuildConfig.VERSION_NAME);

		int status = connection.getResponseCode();
		InputStream stream = status >= 200 && status < 300
				? connection.getInputStream() : connection.getErrorStream();
		String response = readResponse(stream);
		connection.disconnect();
		if (status < 200 || status >= 300) {
			throw new IOException("GitHub returned HTTP " + status);
		}

		JSONObject json = new JSONObject(response);
		if (json.optBoolean("draft") || json.optBoolean("prerelease")) {
			throw new IOException("GitHub latest release is not a stable release");
		}

		String tagName = json.getString("tag_name");
		String releaseUrl = json.getString("html_url");
		String downloadUrl = null;
		JSONArray assets = json.optJSONArray("assets");
		if (assets != null) {
			for (int index = 0; index < assets.length(); index++) {
				JSONObject asset = assets.getJSONObject(index);
				String assetName = asset.optString("name");
				if (assetName.toLowerCase(Locale.ROOT).endsWith(".apk")) {
					downloadUrl = asset.optString("browser_download_url", releaseUrl);
					break;
				}
			}
		}

		String releaseName = json.optString("name", "").trim();
		if (releaseName.isEmpty()) {
			releaseName = tagName;
		}
		return new Release(
				tagName,
				releaseName,
				json.optString("body", ""),
				releaseUrl,
				downloadUrl);
	}

	private static String readResponse(InputStream stream) throws IOException {
		if (stream == null) {
			return "";
		}
		StringBuilder result = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				result.append(line).append('\n');
			}
		}
		return result.toString();
	}

	private static void showUpdate(Activity activity, Release release) {
		StringBuilder message = new StringBuilder(activity.getString(
				R.string.update_available_message,
				BuildConfig.VERSION_NAME,
				release.version));
		String notes = release.notes == null ? "" : release.notes.trim();
		if (!notes.isEmpty()) {
			if (notes.length() > 4000) {
				notes = notes.substring(0, 4000) + "…";
			}
			message.append("\n\n").append(notes);
		}

		AlertDialog.Builder builder = new AlertDialog.Builder(activity)
				.setTitle(activity.getString(R.string.update_available_title, release.name))
				.setMessage(message)
				.setNegativeButton(R.string.update_later, null)
				.setNeutralButton(R.string.update_view_release,
						(dialog, which) -> openUrl(activity, release.releaseUrl));
		if (release.downloadUrl != null && !release.downloadUrl.isEmpty()) {
			builder.setPositiveButton(R.string.update_download,
					(dialog, which) -> openUrl(activity, release.downloadUrl));
		} else {
			builder.setPositiveButton(R.string.update_view_release,
					(dialog, which) -> openUrl(activity, release.releaseUrl));
		}
		builder.show();
	}

	private static void showError(Activity activity, Exception error) {
		String detail = error.getLocalizedMessage();
		if (detail == null || detail.trim().isEmpty()) {
			detail = error.getClass().getSimpleName();
		}
		new AlertDialog.Builder(activity)
				.setTitle(R.string.update_check_failed_title)
				.setMessage(activity.getString(R.string.update_check_failed_message, detail))
				.setPositiveButton(android.R.string.ok, null)
				.show();
	}

	private static void openUrl(Activity activity, String url) {
		try {
			activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
		} catch (ActivityNotFoundException error) {
			showError(activity, error);
		}
	}

	private static boolean isUsable(Activity activity) {
		return !activity.isFinishing() && !activity.isDestroyed();
	}
}
