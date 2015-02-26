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

package com.android.settings;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Vibrator;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.preference.SeekBarVolumizer;
import android.preference.SwitchPreference;
import android.preference.TwoStatePreference;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.telephony.SubInfoRecord;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.settings.hardware.VibratorIntensity;
import com.android.settings.notification.IncreasingRingVolumePreference;
import com.android.settings.notification.NotificationAccessSettings;
import com.android.settings.notification.VolumeSeekBarPreference;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SoundSettings extends SettingsPreferenceFragment implements Indexable,
        Preference.OnPreferenceChangeListener {

    private static final String TAG = SoundSettings.class.getSimpleName();

    private static final String KEY_SOUND = "sounds";
    private static final String KEY_VOLUMES = "volumes";
    private static final String KEY_VIBRATE = "vibrate";
    private static final String KEY_MEDIA_VOLUME = "media_volume";
    private static final String KEY_ALARM_VOLUME = "alarm_volume";
    private static final String KEY_RING_VOLUME = "ring_volume";
    private static final String KEY_NOTIFICATION_VOLUME = "notification_volume";
    private static final String KEY_VOLUME_LINK_NOTIFICATION = "volume_link_notification";
    private static final String KEY_PHONE_RINGTONE = "ringtone";
    private static final String KEY_NOTIFICATION_RINGTONE = "notification_ringtone";
    private static final String KEY_VIBRATE_WHEN_RINGING = "vibrate_when_ringing";
    private static final String KEY_NOTIFICATION = "notification";
    private static final String KEY_NOTIFICATION_PULSE = "notification_pulse";
    private static final String KEY_NOTIFICATION_ACCESS = "manage_notification_access";
    private static final String KEY_INCREASING_RING_VOLUME = "increasing_ring_volume";
    private static final String KEY_VOLUME_PANEL_TIMEOUT = "volume_panel_time_out";
    private static final String KEY_VIBRATION_INTENSITY = "vibration_intensity";
    private static final String KEY_VIBRATE_ON_TOUCH = "vibrate_on_touch";

    private static final int SAMPLE_CUTOFF = 2000;  // manually cap sample playback at 2 seconds

    private final VolumePreferenceCallback mVolumeCallback = new VolumePreferenceCallback();
    private final IncreasingRingVolumePreference.Callback mIncreasingRingVolumeCallback =
            new IncreasingRingVolumePreference.Callback() {
        @Override
        public void onStartingSample() {
            mVolumeCallback.stopSample();
        }
    };

    private final H mHandler = new H();
    private final SettingsObserver mSettingsObserver = new SettingsObserver();

    private Context mContext;
    private PackageManager mPM;
    private boolean mVoiceCapable;
    private Vibrator mVibrator;
    private VolumeSeekBarPreference mRingPreference;
    private VolumeSeekBarPreference mNotificationPreference;

    private TwoStatePreference mIncreasingRing;
    private IncreasingRingVolumePreference mIncreasingRingVolume;
    private ArrayList<DefaultRingtonePreference> mPhoneRingtonePreferences;
    private Preference mNotificationRingtonePreference;
    private TwoStatePreference mVibrateWhenRinging;
    private Preference mNotificationAccess;
    private SwitchPreference mVolumeLinkNotificationSwitch;

    private ListPreference mVolumePanelTimeOut;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getActivity();
        mPM = mContext.getPackageManager();
        mVoiceCapable = Utils.isVoiceCapable(mContext);

        mVibrator = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);
        if (mVibrator != null && !mVibrator.hasVibrator()) {
            mVibrator = null;
        }

        addPreferencesFromResource(R.xml.sounds);

        final PreferenceCategory volumes = (PreferenceCategory) findPreference(KEY_VOLUMES);
        final PreferenceCategory sounds = (PreferenceCategory) findPreference(KEY_SOUND);
        final PreferenceCategory vibrate = (PreferenceCategory) findPreference(KEY_VIBRATE);
        initVolumePreference(KEY_MEDIA_VOLUME, AudioManager.STREAM_MUSIC);
        initVolumePreference(KEY_ALARM_VOLUME, AudioManager.STREAM_ALARM);

        if (mVoiceCapable) {
            mRingPreference =
                    initVolumePreference(KEY_RING_VOLUME, AudioManager.STREAM_RING);
            mVolumeLinkNotificationSwitch = (SwitchPreference)
                    volumes.findPreference(KEY_VOLUME_LINK_NOTIFICATION);
        } else {
            volumes.removePreference(volumes.findPreference(KEY_RING_VOLUME));
            volumes.removePreference(volumes.findPreference(KEY_VOLUME_LINK_NOTIFICATION));
        }

        if (!VibratorIntensity.isSupported()) {
            Preference preference = vibrate.findPreference(KEY_VIBRATION_INTENSITY);
            if (preference != null) {
                vibrate.removePreference(preference);
            }
        }

        initRingtones(sounds);
        initIncreasingRing(sounds);
        initVibrateWhenRinging(vibrate);

        mNotificationAccess = findPreference(KEY_NOTIFICATION_ACCESS);
        refreshNotificationListeners();

        mVolumePanelTimeOut = (ListPreference) findPreference(KEY_VOLUME_PANEL_TIMEOUT);
        int volumePanelTimeOut = Settings.System.getInt(getContentResolver(),
                Settings.System.VOLUME_PANEL_TIMEOUT, 3000);
        mVolumePanelTimeOut.setValue(String.valueOf(volumePanelTimeOut));
        mVolumePanelTimeOut.setOnPreferenceChangeListener(this);
        updateVolumePanelTimeOutSummary(volumePanelTimeOut);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshNotificationListeners();
        lookupRingtoneNames();
        updateNotificationPreferenceState();
        mSettingsObserver.register(true);
    }

    @Override
    public void onPause() {
        super.onPause();
        mVolumeCallback.stopSample();
        mSettingsObserver.register(false);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if (preference == mVolumePanelTimeOut) {
            int volumePanelTimeOut = Integer.valueOf((String) objValue);
            Settings.System.putInt(getContentResolver(),
                    Settings.System.VOLUME_PANEL_TIMEOUT,
                    volumePanelTimeOut);
            updateVolumePanelTimeOutSummary(volumePanelTimeOut);
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    private void updateVolumePanelTimeOutSummary(int value) {
        String summary = getResources().getString(R.string.volume_panel_time_out_summary,
                value / 1000);
        mVolumePanelTimeOut.setSummary(summary);
    }

    // === Volumes ===

    private VolumeSeekBarPreference initVolumePreference(String key, int stream) {
        final VolumeSeekBarPreference volumePref = (VolumeSeekBarPreference) findPreference(key);
        if (volumePref == null) return null;
        volumePref.setCallback(mVolumeCallback);
        volumePref.setStream(stream);
        return volumePref;
    }

    private void updateRingIcon(int progress) {
        mRingPreference.showIcon(progress > 0
                    ? R.drawable.ic_audio_ring_24dp
                    : (mVibrator == null
                            ? R.drawable.ring_notif_mute
                            : R.drawable.ring_notif_vibrate));
    }

    private final class VolumePreferenceCallback implements VolumeSeekBarPreference.Callback {
        private SeekBarVolumizer mCurrent;

        @Override
        public void onSampleStarting(SeekBarVolumizer sbv) {
            if (mCurrent != null && mCurrent != sbv) {
                mCurrent.stopSample();
            }
            if (mIncreasingRingVolume != null) {
                mIncreasingRingVolume.stopSample();
            }
            mCurrent = sbv;
            if (mCurrent != null) {
                mHandler.removeMessages(H.STOP_SAMPLE);
                mHandler.sendEmptyMessageDelayed(H.STOP_SAMPLE, SAMPLE_CUTOFF);
            }
        }

        @Override
        public void onStreamValueChanged(int stream, int progress) {
            if (stream == AudioManager.STREAM_RING) {
                mHandler.removeMessages(H.UPDATE_RINGER_ICON);
                mHandler.obtainMessage(H.UPDATE_RINGER_ICON, progress, 0).sendToTarget();
            }
        }

        public void stopSample() {
            if (mCurrent != null) {
                mCurrent.stopSample();
            }
        }
    };


    // === Phone & notification ringtone ===

    private void initRingtones(PreferenceCategory root) {
        DefaultRingtonePreference phoneRingtonePreference =
                (DefaultRingtonePreference) root.findPreference(KEY_PHONE_RINGTONE);
        if (mPhoneRingtonePreferences != null && !mVoiceCapable) {
            root.removePreference(phoneRingtonePreference);
            mPhoneRingtonePreferences = null;
        } else {
            mPhoneRingtonePreferences = new ArrayList<DefaultRingtonePreference>();
            TelephonyManager telephonyManager = (TelephonyManager) mContext.getSystemService(
                    Context.TELEPHONY_SERVICE);
            if (telephonyManager.isMultiSimEnabled()) {
                root.removePreference(phoneRingtonePreference);
                PreferenceCategory soundCategory = (PreferenceCategory) findPreference(KEY_SOUND);
                for (int i = 0; i < TelephonyManager.getDefault().getSimCount(); i++) {
                    DefaultRingtonePreference ringtonePreference =
                            new DefaultRingtonePreference(mContext, null);
                    String title = getString(R.string.sim_ringtone_title, i + 1);
                    ringtonePreference.setTitle(title);
                    ringtonePreference.setSubId(i);
                    ringtonePreference.setOrder(0);
                    ringtonePreference.setRingtoneType(RingtoneManager.TYPE_RINGTONE);
                    soundCategory.addPreference(ringtonePreference);
                    mPhoneRingtonePreferences.add(ringtonePreference);
                }
            } else {
                mPhoneRingtonePreferences.add(phoneRingtonePreference);
            }
        }
        mNotificationRingtonePreference = root.findPreference(KEY_NOTIFICATION_RINGTONE);
    }

    private void lookupRingtoneNames() {
        AsyncTask.execute(mLookupRingtoneNames);
    }

    private final Runnable mLookupRingtoneNames = new Runnable() {
        @Override
        public void run() {
             if (mPhoneRingtonePreferences != null) {
                ArrayList<CharSequence> summaries = new ArrayList<CharSequence>();
                for (DefaultRingtonePreference preference : mPhoneRingtonePreferences) {
                    CharSequence summary = updateRingtoneName(
                            mContext, RingtoneManager.TYPE_RINGTONE, preference.getSubId());
                    summaries.add(summary);
                }
                if (!summaries.isEmpty()) {
                    mHandler.obtainMessage(H.UPDATE_PHONE_RINGTONE, summaries).sendToTarget();
                }
            }
            if (mNotificationRingtonePreference != null) {
                final CharSequence summary = updateRingtoneName(
                        mContext, RingtoneManager.TYPE_NOTIFICATION, -1);
                if (summary != null) {
                    mHandler.obtainMessage(H.UPDATE_NOTIFICATION_RINGTONE, summary).sendToTarget();
                }
            }
        }
    };

    private static CharSequence updateRingtoneName(Context context, int type, int subId) {
        if (context == null) {
            Log.e(TAG, "Unable to update ringtone name, no context provided");
            return null;
        }
        Uri ringtoneUri;
        if (type != RingtoneManager.TYPE_RINGTONE || subId <= 0) {
            ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(context, type);
        } else {
            ringtoneUri = RingtoneManager.getActualRingtoneUriBySubId(context, subId);
         }
        CharSequence summary = context.getString(com.android.internal.R.string.ringtone_unknown);
        // Is it a silent ringtone?
        if (ringtoneUri == null) {
            summary = context.getString(com.android.internal.R.string.ringtone_silent);
        } else {
            Cursor cursor = null;
            try {
                if (MediaStore.AUTHORITY.equals(ringtoneUri.getAuthority())) {
                    // Fetch the ringtone title from the media provider
                    cursor = context.getContentResolver().query(ringtoneUri,
                            new String[] { MediaStore.Audio.Media.TITLE }, null, null, null);
                } else if (ContentResolver.SCHEME_CONTENT.equals(ringtoneUri.getScheme())) {
                    cursor = context.getContentResolver().query(ringtoneUri,
                            new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null);
                }
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        summary = cursor.getString(0);
                    }
                }
            } catch (SQLiteException sqle) {
                // Unknown title for the ringtone
            } catch (IllegalArgumentException iae) {
                // Some other error retrieving the column from the provider
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        return summary;
    }

    // === Increasing ringtone ===

    private void initIncreasingRing(PreferenceCategory root) {
        mIncreasingRing = (TwoStatePreference)
                root.findPreference(Settings.System.INCREASING_RING);
        mIncreasingRingVolume = (IncreasingRingVolumePreference)
                root.findPreference(KEY_INCREASING_RING_VOLUME);

        if (!mVoiceCapable) {
            if (mIncreasingRing != null) {
                root.removePreference(mIncreasingRing);
                mIncreasingRing = null;
            }
            if (mIncreasingRingVolume != null) {
                root.removePreference(mIncreasingRingVolume);
                mIncreasingRingVolume = null;
            }
        } else {
            if (mIncreasingRingVolume != null) {
                mIncreasingRingVolume.setCallback(mIncreasingRingVolumeCallback);
            }
        }
    }

    // === Vibrate when ringing ===

    private void initVibrateWhenRinging(PreferenceCategory root) {
        mVibrateWhenRinging = (TwoStatePreference) root.findPreference(KEY_VIBRATE_WHEN_RINGING);
        if (mVibrateWhenRinging == null) {
            Log.i(TAG, "Preference not found: " + KEY_VIBRATE_WHEN_RINGING);
            return;
        }
        if (!mVoiceCapable) {
            root.removePreference(mVibrateWhenRinging);
            mVibrateWhenRinging = null;
            return;
        }
        mVibrateWhenRinging.setPersistent(false);
        updateVibrateWhenRinging();
        mVibrateWhenRinging.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final boolean val = (Boolean) newValue;
                return Settings.System.putInt(getContentResolver(),
                        Settings.System.VIBRATE_WHEN_RINGING,
                        val ? 1 : 0);
            }
        });
    }

    private void updateVibrateWhenRinging() {
        if (mVibrateWhenRinging == null) return;
        mVibrateWhenRinging.setChecked(Settings.System.getInt(getContentResolver(),
                Settings.System.VIBRATE_WHEN_RINGING, 0) != 0);
    }

    private void updateNotificationPreferenceState() {
        mNotificationPreference = initVolumePreference(KEY_NOTIFICATION_VOLUME,
                AudioManager.STREAM_NOTIFICATION);

        if (mVoiceCapable) {
            final boolean enabled = Settings.System.getInt(getContentResolver(),
                    Settings.Secure.VOLUME_LINK_NOTIFICATION, 1) == 1;

            if (mNotificationPreference != null) {
                mNotificationPreference.setEnabled(!enabled);
            }
            if (mVolumeLinkNotificationSwitch != null){
                mVolumeLinkNotificationSwitch.setChecked(enabled);
            }
        }
    }

    // === Notification listeners ===

    private void refreshNotificationListeners() {
        if (mNotificationAccess != null) {
            final int total = NotificationAccessSettings.getListenersCount(mPM);
            if (total == 0) {
                getPreferenceScreen().removePreference(mNotificationAccess);
            } else {
                final int n = NotificationAccessSettings.getEnabledListenersCount(mContext);
                if (n == 0) {
                    mNotificationAccess.setSummary(getResources().getString(
                            R.string.manage_notification_access_summary_zero));
                } else {
                    mNotificationAccess.setSummary(String.format(getResources().getQuantityString(
                            R.plurals.manage_notification_access_summary_nonzero,
                            n, n)));
                }
            }
        }
    }

    // === Callbacks ===

    private final class SettingsObserver extends ContentObserver {
        private final Uri VIBRATE_WHEN_RINGING_URI =
                Settings.System.getUriFor(Settings.System.VIBRATE_WHEN_RINGING);
        private final Uri NOTIFICATION_LIGHT_PULSE_URI =
                Settings.System.getUriFor(Settings.System.NOTIFICATION_LIGHT_PULSE);
        private final Uri LOCK_SCREEN_PRIVATE_URI =
                Settings.Secure.getUriFor(Settings.Secure.LOCK_SCREEN_ALLOW_PRIVATE_NOTIFICATIONS);
        private final Uri LOCK_SCREEN_SHOW_URI =
                Settings.Secure.getUriFor(Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS);
        private final Uri VOLUME_LINK_NOTIFICATION_URI =
                Settings.Secure.getUriFor(Settings.Secure.VOLUME_LINK_NOTIFICATION);

        public SettingsObserver() {
            super(mHandler);
        }

        public void register(boolean register) {
            final ContentResolver cr = getContentResolver();
            if (register) {
                cr.registerContentObserver(VIBRATE_WHEN_RINGING_URI, false, this);
                cr.registerContentObserver(NOTIFICATION_LIGHT_PULSE_URI, false, this);
                cr.registerContentObserver(LOCK_SCREEN_PRIVATE_URI, false, this);
                cr.registerContentObserver(LOCK_SCREEN_SHOW_URI, false, this);
                cr.registerContentObserver(VOLUME_LINK_NOTIFICATION_URI, false, this);
            } else {
                cr.unregisterContentObserver(this);
            }
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            if (VIBRATE_WHEN_RINGING_URI.equals(uri)) {
                updateVibrateWhenRinging();
            }
            if (VOLUME_LINK_NOTIFICATION_URI.equals(uri)) {
                updateNotificationPreferenceState();
            }
        }
    }

    private final class H extends Handler {
        private static final int UPDATE_PHONE_RINGTONE = 1;
        private static final int UPDATE_NOTIFICATION_RINGTONE = 2;
        private static final int STOP_SAMPLE = 3;
        private static final int UPDATE_RINGER_ICON = 4;

        private H() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPDATE_PHONE_RINGTONE:
                    ArrayList<CharSequence> summaries = (ArrayList<CharSequence>) msg.obj;
                    for (int i = 0; i < summaries.size(); i++) {
                        Preference preference = mPhoneRingtonePreferences.get(i);
                        preference.setSummary(summaries.get(i));
                    }
                    break;
                case UPDATE_NOTIFICATION_RINGTONE:
                    mNotificationRingtonePreference.setSummary((CharSequence) msg.obj);
                    break;
                case STOP_SAMPLE:
                    mVolumeCallback.stopSample();
                    break;
                case UPDATE_RINGER_ICON:
                    updateRingIcon(msg.arg1);
                    break;
            }
        }
    }

    // === Indexing ===

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
        private boolean mHasVibratorIntensity;

        @Override
        public void prepare() {
            super.prepare();
            mHasVibratorIntensity = VibratorIntensity.isSupported();
        }

        public List<SearchIndexableResource> getXmlResourcesToIndex(
                Context context, boolean enabled) {
            final SearchIndexableResource sir = new SearchIndexableResource(context);
            sir.xmlResId = R.xml.sounds;
            return Arrays.asList(sir);
        }

        public List<String> getNonIndexableKeys(Context context) {
            final ArrayList<String> rt = new ArrayList<String>();
            if (Utils.isVoiceCapable(context)) {
                rt.add(KEY_NOTIFICATION_VOLUME);
            } else {
                rt.add(KEY_RING_VOLUME);
                rt.add(KEY_PHONE_RINGTONE);
                rt.add(KEY_VIBRATE_WHEN_RINGING);
            }
            Vibrator vib = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vib == null || !vib.hasVibrator()) {
                rt.add(KEY_VIBRATE);
            }
            if (!mHasVibratorIntensity) {
                rt.add(KEY_VIBRATION_INTENSITY);
            }

            return rt;
        }
    };
}
