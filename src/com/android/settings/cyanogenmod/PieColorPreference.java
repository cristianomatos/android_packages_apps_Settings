package com.android.settings.cyanogenmod;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.PaintDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.Preference;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.settings.R;

public class PieColorPreference extends Preference {
    public static final String TAG = "PieColorPreference";

    private ImageView mColorView;
    private Integer mDefaultColor;
    private int mColorValue;

    public PieColorPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setWidgetLayoutResource(R.layout.preference_widget_pie_color);
    }

    public PieColorPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWidgetLayoutResource(R.layout.preference_widget_pie_color);
    }

    public PieColorPreference(Context context) {
        this(context, null);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        try {
            mDefaultColor = (int) Long.parseLong(a.getString(index), 16);
        } catch (NumberFormatException e) {
            Slog.w(TAG, "Unexpected default value during inflate", e);
        }
        return mDefaultColor;
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        if (restoreValue) {
            mColorValue = getPersistedInt(0xff000000);
        } else {
            mColorValue = (Integer) defaultValue;
        }
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        mColorView = (ImageView) view.findViewById(R.id.pie_color);
        updateColor(mColorValue, false);
    }

    private void updateColor(int color, boolean notify) {
        // persist value when notify && callChangeListener returns true, otherwise skip it
        if (notify && !callChangeListener(color)) {
            return;
        }
        mColorValue = color;
        if (mColorView != null) {
            mColorView.setEnabled(true);
            mColorView.setImageDrawable(createRectShape(0xff000000 | mColorValue));
        }
        persistInt(mColorValue);
    }

    @Override
    public void onClick() {
        editPreferenceValues();
    }

    private void editPreferenceValues() {
        final Resources resources = getContext().getResources();
        final PieColorDialog d = new PieColorDialog(getContext(), mColorValue);
        d.setAlphaSliderVisible(true);

        d.setButton(AlertDialog.BUTTON_POSITIVE, resources.getString(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                updateColor(d.getColor(), true);
            }
        });
        // show a reset button if we have a default value to set to
        if (getDefaultColor() != null) {
            d.setButton(AlertDialog.BUTTON_NEUTRAL, resources.getString(R.string.pie_control_color_reset), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    updateColor(getDefaultColor(), true);
                }
            });
        }
        d.setButton(AlertDialog.BUTTON_NEGATIVE, resources.getString(R.string.cancel), (DialogInterface.OnClickListener) null);
        d.show();
    }

    public int getColor() {
        return mColorValue;
    }

    public void setColor(int color) {
        setColor(color, true);
    }

    public void setColor(int color, boolean notifyChange) {
        updateColor(color, notifyChange);
    }

    public Integer getDefaultColor() {
        return mDefaultColor;
    }

    public void setDefaultColor(Integer color) {
        mDefaultColor = color;
    }

    private ShapeDrawable createRectShape(int color) {
        ShapeDrawable shape = new ShapeDrawable(new RectShape());
        final Resources resources = getContext().getResources();
        // hacky hacky hacky
        final int width = (int) resources.getDimension(R.dimen.device_memory_usage_button_width);
        final int height = (int) resources.getDimension(R.dimen.device_memory_usage_button_height);

        shape.setIntrinsicHeight(height);
        shape.setIntrinsicWidth(width);
        shape.getPaint().setColor(color);
        return shape;
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        /*
         * Suppose a client uses this preference type without persisting. We
         * must save the instance state so it is able to, for example, survive
         * orientation changes.
         */

        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            // No need to save instance state since it's persistent
            return superState;
        }

        // Save the instance state
        final SavedState myState = new SavedState(superState);
        myState.color = mColorValue;
        myState.defaultColor = mDefaultColor;
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!state.getClass().equals(SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        // Restore the instance state
        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        mColorValue = myState.color;
        mDefaultColor = myState.defaultColor;
        notifyChanged();
    }

    /**
     * SavedState, a subclass of {@link BaseSavedState}, will store the state
     * of MyPreference, a subclass of Preference.
     * <p>
     * It is important to always call through to super methods.
     */
    private static class SavedState extends BaseSavedState {
        int color;
        Integer defaultColor;

        public SavedState(Parcel source) {
            super(source);

            // Restore
            color = source.readInt();
            if (source.readByte() == 1) {
                defaultColor = source.readInt();
            }
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);

            // Save
            dest.writeInt(color);
            if (defaultColor != null) {
                dest.writeByte((byte) 1);
                dest.writeInt(color);
            } else {
                dest.writeByte((byte) 0);
            }
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @SuppressWarnings("unused")
        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
}
