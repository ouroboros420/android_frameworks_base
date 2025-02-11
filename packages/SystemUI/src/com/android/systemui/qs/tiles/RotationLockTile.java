/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.qs.tiles;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;

import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.widget.Switch;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.qs.QSHost;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.statusbar.policy.RotationLockController;
import com.android.systemui.statusbar.policy.RotationLockController.RotationLockControllerCallback;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.statusbar.policy.KeyguardMonitor;

/** Quick settings tile: Rotation **/
public class RotationLockTile extends QSTileImpl<BooleanState> {

    private final Icon mIcon = ResourceIcon.get(R.drawable.ic_qs_auto_rotate);
    private final RotationLockController mController;

    private final KeyguardMonitor mKeyguard;
    private final KeyguardCallback mKeyguardCallback = new KeyguardCallback();

    public RotationLockTile(QSHost host) {
        super(host);
        mController = Dependency.get(RotationLockController.class);
        mKeyguard = Dependency.get(KeyguardMonitor.class);
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void handleSetListening(boolean listening) {
        if (listening) {
            mController.addCallback(mCallback);
            mKeyguard.addCallback(mKeyguardCallback);
        } else {
            mController.removeCallback(mCallback);
            mKeyguard.removeCallback(mKeyguardCallback);
        }
    }

    @Override
    public Intent getLongClickIntent() {
        return new Intent(Settings.ACTION_DISPLAY_SETTINGS);
    }

    protected void handleClickInner() {
        final boolean newState = !mState.value;
        mController.setRotationLocked(!newState);
        refreshState(newState);
    }

    @Override
    protected void handleClick() {
        if (mController == null) return;
        if (mKeyguard.isSecure() && mKeyguard.isShowing()) {
            Dependency.get(ActivityStarter.class).postQSRunnableDismissingKeyguard(() -> {
                mHost.openPanels();
                handleClickInner();
            });
            return;
        }
        handleClickInner();
    }

    @Override
    public CharSequence getTileLabel() {
        return getState().label;
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final boolean rotationLocked = mController.isRotationLocked();

        state.value = !rotationLocked;
        state.label = mContext.getString(R.string.quick_settings_rotation_unlocked_label);
        state.icon = mIcon;
        state.contentDescription = getAccessibilityString(rotationLocked);
        state.expandedAccessibilityClassName = Switch.class.getName();
        state.state = state.value ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
    }

    public static boolean isCurrentOrientationLockPortrait(RotationLockController controller,
            Context context) {
        int lockOrientation = controller.getRotationLockOrientation();
        if (lockOrientation == Configuration.ORIENTATION_UNDEFINED) {
            // Freely rotating device; use current rotation
            return context.getResources().getConfiguration().orientation
                    != Configuration.ORIENTATION_LANDSCAPE;
        } else {
            return lockOrientation != Configuration.ORIENTATION_LANDSCAPE;
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QS_ROTATIONLOCK;
    }

    /**
     * Get the correct accessibility string based on the state
     *
     * @param locked Whether or not rotation is locked.
     */
    private String getAccessibilityString(boolean locked) {
        return mContext.getString(R.string.accessibility_quick_settings_rotation);
    }

    @Override
    protected String composeChangeAnnouncement() {
        return getAccessibilityString(mState.value);
    }

    private final RotationLockControllerCallback mCallback = new RotationLockControllerCallback() {
        @Override
        public void onRotationLockStateChanged(boolean rotationLocked, boolean affordanceVisible) {
            refreshState(rotationLocked);
        }
    };

    private final class KeyguardCallback implements KeyguardMonitor.Callback {
        @Override
        public void onKeyguardShowingChanged() {
            refreshState();
        }
    };
}
