/*
 * Copyright (C) 2026 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.spacemit.policy.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-asserts persisted policy after boot by nudging the service to re-apply. */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            context.startService(new Intent(context, SpacemitPolicyService.class));
        }
    }
}
