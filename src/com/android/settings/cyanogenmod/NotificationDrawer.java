/*
 * Copyright (C) 2012 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.cyanogenmod;

import java.io.File; 

import android.content.ContentResolver;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.RemoteException; 
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.util.Log;
import android.view.WindowManagerGlobal; 

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import static com.android.internal.util.cm.QSUtils.deviceSupportsMobileData;

public class NotificationDrawer extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "NotificationDrawer"; 
    private static final String UI_COLLAPSE_BEHAVIOUR = "notification_drawer_collapse_on_dismiss";
    private static final String UI_NOTIFICATION_BEHAVIOUR = "notifications_behaviour";
    private static final String UI_BRIGHTNESS_LOC = "brightness_location"; 
    
    private ListPreference mCollapseOnDismiss;
    private ListPreference mNotificationsBehavior;
    private ListPreference mBrightnessLocation;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.notification_drawer);
	PreferenceScreen prefScreen = getPreferenceScreen(); 

        // Notification drawer
        int collapseBehaviour = Settings.System.getInt(getContentResolver(), 
                Settings.System.STATUS_BAR_COLLAPSE_ON_DISMISS,
                Settings.System.STATUS_BAR_COLLAPSE_IF_NO_CLEARABLE);
        mCollapseOnDismiss = (ListPreference) findPreference(UI_COLLAPSE_BEHAVIOUR); 
        mCollapseOnDismiss.setValue(String.valueOf(collapseBehaviour));
        mCollapseOnDismiss.setOnPreferenceChangeListener(this);
        updateCollapseBehaviourSummary(collapseBehaviour);

        int CurrentBehavior = Settings.System.getInt(getContentResolver(), Settings.System.NOTIFICATIONS_BEHAVIOUR, 0);
        mNotificationsBehavior = (ListPreference) findPreference(UI_NOTIFICATION_BEHAVIOUR);
        mNotificationsBehavior.setValue(String.valueOf(CurrentBehavior));
        mNotificationsBehavior.setSummary(mNotificationsBehavior.getEntry());
        mNotificationsBehavior.setOnPreferenceChangeListener(this); 

	mBrightnessLocation = (ListPreference) findPreference(UI_BRIGHTNESS_LOC);
        mBrightnessLocation.setOnPreferenceChangeListener(this);
        mBrightnessLocation.setValue(Integer.toString(Settings.System.getInt(getActivity()
               .getContentResolver(), Settings.System.STATUSBAR_TOGGLES_BRIGHTNESS_LOC, 3)));
        mBrightnessLocation.setSummary(mBrightnessLocation.getEntry());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if (preference == mCollapseOnDismiss) {
            int value = Integer.valueOf((String) objValue);
            Settings.System.putInt(getContentResolver(), 
                    Settings.System.STATUS_BAR_COLLAPSE_ON_DISMISS, value);
            updateCollapseBehaviourSummary(value);
            return true;
        } else if (preference == mNotificationsBehavior) {
            String val = (String) objValue;
                     Settings.System.putInt(getContentResolver(), Settings.System.NOTIFICATIONS_BEHAVIOUR,
            Integer.valueOf(val));
            int index = mNotificationsBehavior.findIndexOfValue(val);
            mNotificationsBehavior.setSummary(mNotificationsBehavior.getEntries()[index]);
            return true; 
	} else if (preference == mBrightnessLocation) {
            int val = Integer.parseInt((String) objValue);
            int index = mBrightnessLocation.findIndexOfValue((String) objValue);
            Settings.System.putInt(getActivity().getContentResolver(),
            Settings.System.STATUSBAR_TOGGLES_BRIGHTNESS_LOC, val);
            mBrightnessLocation.setSummary(mBrightnessLocation.getEntries()[index]);
            return true; 
        }

        return false;
    }

    private void updateCollapseBehaviourSummary(int setting) {
        String[] summaries = getResources().getStringArray(
                R.array.notification_drawer_collapse_on_dismiss_summaries);
        mCollapseOnDismiss.setSummary(summaries[setting]); 
    }

}
