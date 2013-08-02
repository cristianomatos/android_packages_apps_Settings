package com.android.settings.cyanogenmod;

import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.preference.SeekBarDialogPreference;
import android.provider.Settings;

import com.android.internal.util.pie.PieColorUtils;
import com.android.internal.util.pie.PieColorUtils.PieColor;
import com.android.internal.util.pie.PieColorUtils.PieColorSettings; 
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

public class PieControl extends SettingsPreferenceFragment
                        implements Preference.OnPreferenceChangeListener {

    private static final int DEFAULT_POSITION = 1 << 1; // this equals Position.BOTTOM.FLAG

    private static final String PIE_CONTROL = "pie_control_checkbox";
    private static final String PIE_SENSITIVITY = "pie_control_sensitivity";
    private static final String PIE_SIZE = "pie_control_size";
    private static final String[] TRIGGER = {
        "pie_control_trigger_left",
        "pie_control_trigger_bottom",
        "pie_control_trigger_right",
        "pie_control_trigger_top"
    };
    // You want this to be in sync with COLOR_MAPPINGS!
    private static final String[] COLORS = {
        "pie_control_color_normal",
        "pie_control_color_selected",
        "pie_control_color_longpressed",
        "pie_control_color_icon",
    };
    private static final PieColor[] COLOR_MAPPING = {
        PieColorUtils.COLOR_NORMAL,
        PieColorUtils.COLOR_SELECTED,
        PieColorUtils.COLOR_LONG_PRESSED,
        PieColorUtils.COLOR_ICON,
    };
    private static final String PIE_OUTLINE = "pie_control_outline"; 

    private CheckBoxPreference mPieControl;
    private ListPreference mPieSensitivity;
    private SeekBarDialogPreference mPieSize;
    private CheckBoxPreference[] mTrigger = new CheckBoxPreference[TRIGGER.length];
    private PieColorPreference[] mColor = new PieColorPreference[COLORS.length];
    private CheckBoxPreference mPieOutline;

    private ContentObserver mPieTriggerObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            updatePieTriggers();
        }
    };

    private PieColorSettings mColorDefaults;
    private boolean mOutlineByDefault; 

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.pie_control);

        PreferenceScreen prefSet = getPreferenceScreen();
        mPieControl = (CheckBoxPreference) prefSet.findPreference(PIE_CONTROL);
        mPieControl.setOnPreferenceChangeListener(this);
        mPieSensitivity = (ListPreference) prefSet.findPreference(PIE_SENSITIVITY);
        mPieSensitivity.setOnPreferenceChangeListener(this);
        mPieSize = (SeekBarDialogPreference) prefSet.findPreference(PIE_SIZE);

        for (int i = 0; i < TRIGGER.length; i++) {
            mTrigger[i] = (CheckBoxPreference) prefSet.findPreference(TRIGGER[i]);
            mTrigger[i].setOnPreferenceChangeListener(this);
        }

	mColorDefaults = PieColorUtils.loadDefaultPieColors(getActivity());
        for (int i = 0; i < COLORS.length; i++) {
            mColor[i] = (PieColorPreference) prefSet.findPreference(COLORS[i]);
            mColor[i].setOnPreferenceChangeListener(this);
            mColor[i].setDefaultColor(mColorDefaults.getColor(COLOR_MAPPING[i]));
        }
        mPieOutline = (CheckBoxPreference) prefSet.findPreference(PIE_OUTLINE);
        mPieOutline.setOnPreferenceChangeListener(this);
        mOutlineByDefault = mColorDefaults.getColor(PieColorUtils.COLOR_OUTLINE)
                != mColorDefaults.getColor(PieColorUtils.COLOR_NORMAL); 
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mPieControl) {
            boolean newState = (Boolean) newValue;

            Settings.System.putInt(getContentResolver(),
                    Settings.System.PIE_CONTROLS, newState ? 1 : 0);
            propagatePieControl(newState);

        } else if (preference == mPieSensitivity) {
            String newState = (String) newValue;

            Settings.System.putString(getContentResolver(),
                    Settings.System.PIE_SENSITIVITY, newState);
            mPieSensitivity.setSummary(
                    mPieSensitivity.getEntries()[Integer.parseInt(newState) - 1]);
        } else if (isColorPreference(preference)) {
            PieColorSettings colorSettings = PieColorUtils.loadPieColors(getActivity(), false);
            for (int i = 0; i < mColor.length; i++) {
                if (preference == mColor[i]) {
                    // only store color settings that are non default values
                    if ((Integer) newValue != ((PieColorPreference) preference).getDefaultColor()) {
                        colorSettings.setColor(COLOR_MAPPING[i], (Integer) newValue);
                    } else {
                        colorSettings.removeColor(COLOR_MAPPING[i]);
                    }
                }
            }
            // correct outline color if appropriate
            applyOutlineColor(colorSettings, mPieOutline.isChecked());
            PieColorUtils.storePieColors(getActivity(), colorSettings);
        } else if (preference == mPieOutline) {
            boolean newState = (Boolean) newValue;

            PieColorSettings colorSettings = PieColorUtils.loadPieColors(getActivity(), false);
            applyOutlineColor(colorSettings, newState);
            PieColorUtils.storePieColors(getActivity(), colorSettings);
        } else if (isTriggerPreference(preference)) {
            int triggerSlots = 0;
            for (int i = 0; i < mTrigger.length; i++) {
                boolean checked = preference == mTrigger[i]
                        ? (Boolean) newValue : mTrigger[i].isChecked();
                if (checked) {
                    triggerSlots |= 1 << i;
                }
            }
            Settings.System.putInt(getContentResolver(),
                    Settings.System.PIE_POSITIONS, triggerSlots);
        }
        return true;
    }

    private void applyOutlineColor(PieColorSettings colorSettings, boolean state) {
        PieColor sourceColor = state ? PieColorUtils.COLOR_ICON : PieColorUtils.COLOR_NORMAL;
        // if state and byDefault is equal and source is default, fall back to default outline color
        if ((state == mOutlineByDefault) && !colorSettings.isColorPresent(sourceColor)) {
            colorSettings.removeColor(PieColorUtils.COLOR_OUTLINE);
            return;
        }
        // otherwise get the color from settings or default and set the color
        int newColorValue = mColorDefaults.getColor(sourceColor);
        if (colorSettings.isColorPresent(sourceColor)) {
            newColorValue = colorSettings.getColor(sourceColor);
        }
        // set the color only if it is different from the default
        if (newColorValue != mColorDefaults.getColor(PieColorUtils.COLOR_OUTLINE)) {
            colorSettings.setColor(PieColorUtils.COLOR_OUTLINE, newColorValue);
        } else {
            colorSettings.removeColor(PieColorUtils.COLOR_OUTLINE);
        }
    }

    private boolean isColorPreference(Preference preference) {
        for (int i = 0; i < mColor.length; i++) {
            if (mColor[i] == preference) {
                return true;
            }
        }
        return false;
    }

    private boolean isTriggerPreference(Preference preference) {
        for (int i = 0; i < mTrigger.length; i++) {
            if (mTrigger[i] == preference) {
                return true;
            }
        }
        return false;
    } 

    @Override
    public void onResume() {
        super.onResume();

        mPieControl.setChecked(Settings.System.getInt(getContentResolver(),
                Settings.System.PIE_CONTROLS, 0) == 1);
        propagatePieControl(mPieControl.isChecked());

        int sensitivity = Settings.System.getInt(getContentResolver(),
                Settings.System.PIE_SENSITIVITY, 3);
        mPieSensitivity.setValue(Integer.toString(sensitivity));

        getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.PIE_POSITIONS), true,
                mPieTriggerObserver);

	updatePieColors();
        updatePieTriggers();
        updateSensitivity();
    }

    @Override
    public void onPause() {
        super.onPause();
        getContentResolver().unregisterContentObserver(mPieTriggerObserver);
    }

    private void propagatePieControl(boolean value) {
        for (int i = 0; i < mTrigger.length; i++) {
	    mColor[i].setEnabled(value);
        }
        for (int i = 0; i < mTrigger.length; i++) { 
            mTrigger[i].setEnabled(value);
        }
        mPieSensitivity.setEnabled(value);
        mPieSize.setEnabled(value);
    }

    private void updatePieColors() {
        PieColorSettings colorSettings = PieColorUtils.loadPieColors(getActivity(), true);
        mPieOutline.setChecked(colorSettings.getColor(PieColorUtils.COLOR_OUTLINE)
                != colorSettings.getColor(PieColorUtils.COLOR_NORMAL));
        for (int i = 0; i < mColor.length; i++) {
            mColor[i].setColor(colorSettings.getColor(COLOR_MAPPING[i]), false);
        }
    } 

    private void updatePieTriggers() {
        int triggerSlots = Settings.System.getInt(getContentResolver(),
                Settings.System.PIE_POSITIONS, DEFAULT_POSITION);

        for (int i = 0; i < mTrigger.length; i++) {
            if ((triggerSlots & (0x01 << i)) != 0) {
                mTrigger[i].setChecked(true);
            } else {
                mTrigger[i].setChecked(false);
            }
        }
    }

    private void updateSensitivity() {
        int triggerSlots = Settings.System.getInt(getContentResolver(),
                Settings.System.PIE_SENSITIVITY, 3);
        mPieSensitivity.setSummary(mPieSensitivity.getEntry());
    }

}
