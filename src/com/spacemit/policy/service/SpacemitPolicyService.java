/*
 * Copyright (C) 2026 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.spacemit.policy.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Slog;

import com.spacemit.policy.ISpacemitPolicyManager;
import com.spacemit.policy.SpacemitPolicy;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Hosts {@link ISpacemitPolicyManager} and applies policies to the device.
 * Applied values are kept in device-protected prefs so they can be re-asserted
 * on boot and reported for compliance.
 */
public final class SpacemitPolicyService extends Service {

    static final String TAG = "SpacemitPolicy";
    private static final String PREFS = "applied_policies";
    private static final Pattern KEY = Pattern.compile("[a-zA-Z0-9_.-]{1,64}");

    private SharedPreferences mApplied;

    @Override
    public void onCreate() {
        super.onCreate();
        mApplied = createDeviceProtectedStorageContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // BootReceiver starts us just to re-assert policy, then we stop.
        reapplyAllInternal();
        stopSelf(startId);
        return START_NOT_STICKY;
    }

    private final ISpacemitPolicyManager.Stub mBinder = new ISpacemitPolicyManager.Stub() {
        @Override public int getApiVersion() {
            return SpacemitPolicy.API_VERSION;
        }

        @Override public boolean setVendorProperty(String key, String value) {
            enforce();
            if (!validKey(key)) return false;
            return applyVendorProperty(key, value == null ? "" : value, true);
        }

        @Override public String getVendorProperty(String key, String defValue) {
            enforce();
            if (!validKey(key)) return defValue;
            String v = Settings.Global.getString(getContentResolver(),
                    SpacemitPolicy.PROP_PREFIX + key);
            return v != null ? v : defValue;
        }

        @Override public boolean setDeviceSetting(int namespace, String key, String value) {
            enforce();
            if (!validKey(key)) return false;
            return applyDeviceSetting(namespace, key, value, true);
        }

        @Override public String getDeviceSetting(int namespace, String key) {
            enforce();
            return readSetting(namespace, key);
        }

        @Override public Bundle getAppliedPolicies() {
            enforce();
            Bundle out = new Bundle();
            for (Map.Entry<String, ?> e : mApplied.getAll().entrySet()) {
                out.putString(e.getKey(), String.valueOf(e.getValue()));
            }
            return out;
        }

        @Override public void reapplyAll() {
            enforce();
            reapplyAllInternal();
        }
    };

    private void enforce() {
        enforceCallingOrSelfPermission(SpacemitPolicy.PERMISSION_MANAGE,
                "Requires " + SpacemitPolicy.PERMISSION_MANAGE);
    }

    private static boolean validKey(String key) {
        return !TextUtils.isEmpty(key) && KEY.matcher(key).matches();
    }

    private boolean applyVendorProperty(String key, String value, boolean record) {
        try {
            boolean ok = Settings.Global.putString(getContentResolver(),
                    SpacemitPolicy.PROP_PREFIX + key, value);
            if (ok && record) mApplied.edit().putString("prop/" + key, value).apply();
            return ok;
        } catch (RuntimeException e) {
            Slog.e(TAG, "setVendorProperty " + key + " failed", e);
            return false;
        }
    }

    private boolean applyDeviceSetting(int ns, String key, String value, boolean record) {
        try {
            boolean ok;
            switch (ns) {
                case SpacemitPolicy.NS_GLOBAL:
                    ok = Settings.Global.putString(getContentResolver(), key, value);
                    break;
                case SpacemitPolicy.NS_SECURE:
                    ok = Settings.Secure.putString(getContentResolver(), key, value);
                    break;
                case SpacemitPolicy.NS_SYSTEM:
                    ok = Settings.System.putString(getContentResolver(), key, value);
                    break;
                default:
                    return false;
            }
            if (ok && record) {
                mApplied.edit().putString("setting/" + ns + "/" + key, value).apply();
            }
            return ok;
        } catch (RuntimeException e) {
            Slog.e(TAG, "setDeviceSetting " + ns + "/" + key + " failed", e);
            return false;
        }
    }

    private String readSetting(int ns, String key) {
        switch (ns) {
            case SpacemitPolicy.NS_GLOBAL: return Settings.Global.getString(getContentResolver(), key);
            case SpacemitPolicy.NS_SECURE: return Settings.Secure.getString(getContentResolver(), key);
            case SpacemitPolicy.NS_SYSTEM: return Settings.System.getString(getContentResolver(), key);
            default: return null;
        }
    }

    private void reapplyAllInternal() {
        for (Map.Entry<String, ?> e : mApplied.getAll().entrySet()) {
            String stored = e.getKey();
            String value = String.valueOf(e.getValue());
            if (stored.startsWith("prop/")) {
                applyVendorProperty(stored.substring(5), value, false);
            } else if (stored.startsWith("setting/")) {
                String[] p = stored.split("/", 3);
                if (p.length == 3) {
                    applyDeviceSetting(Integer.parseInt(p[1]), p[2], value, false);
                }
            }
        }
    }
}
