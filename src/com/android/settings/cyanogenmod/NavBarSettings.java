/*
* Copyright (C) 2012 Slimroms
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

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.Settings;

import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.R;
import com.android.settings.Utils;


public class NavBarSettings extends SettingsPreferenceFragment {

    private static final String TAG = "NavBar";
    private static final String ENABLE_NAVIGATION_BAR = "enable_nav_bar";
    private static final String PREF_STYLE_DIMEN = "navbar_style_dimen_settings";
    private static final String ENABLE_NAVBAR_OPTIONS = "enable_navbar_option";

    private boolean mHasNavBarByDefault;

    CheckBoxPreference mEnableNavigationBar;
    PreferenceScreen mStyleDimenPreference;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.navbar_settings);

        PreferenceScreen prefs = getPreferenceScreen();

	mStyleDimenPreference = (PreferenceScreen) findPreference(PREF_STYLE_DIMEN);


        mHasNavBarByDefault = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_showNavigationBar);
        boolean enableNavigationBar = Settings.System.getInt(getContentResolver(),
                Settings.System.NAVIGATION_BAR_SHOW, mHasNavBarByDefault ? 1 : 0) == 1;
        mEnableNavigationBar = (CheckBoxPreference) findPreference(ENABLE_NAVIGATION_BAR);
        mEnableNavigationBar.setChecked(enableNavigationBar);

        // don't allow devices that must use a navigation bar to disable it
        //if (mHasNavBarByDefault) {
        //    prefs.removePreference(mEnableNavigationBar);
        //}
	updateNavbarPreferences(enableNavigationBar);
    }


    private void updateNavbarPreferences(boolean show) {
        if (mHasNavBarByDefault) {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.UI_FORCE_OVERFLOW_BUTTON,
                    show ? 0 : 1);
        }
        mStyleDimenPreference.setEnabled(show);

    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        if (preference == mEnableNavigationBar) {
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.NAVIGATION_BAR_SHOW,
                    ((CheckBoxPreference) preference).isChecked() ? 1 : 0);
            updateNavbarPreferences(((CheckBoxPreference) preference).isChecked());
            return true;
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public void onResume() {
        super.onResume();
    }
}
