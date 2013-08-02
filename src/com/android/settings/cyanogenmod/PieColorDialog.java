package com.android.settings.cyanogenmod;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;

import com.android.settings.R;
import com.android.settings.notificationlight.ColorPanelView;
import com.android.settings.notificationlight.ColorPickerView;
import com.android.settings.notificationlight.ColorPickerView.OnColorChangedListener;

import java.util.Locale;

public class PieColorDialog extends AlertDialog implements
        ColorPickerView.OnColorChangedListener, TextWatcher, View.OnFocusChangeListener {

    private ColorPickerView mColorPicker;
    private EditText mHexColorInput;

    private ColorPanelView mColorPanel;

    protected PieColorDialog(Context context, int initialColor) {
        super(context);

        init(initialColor);
    }

    private void init(int color) {
        // To fight color banding.
        getWindow().setFormat(PixelFormat.RGBA_8888);
        setup(color);
    }

    private void setup(int color) {
        final LayoutInflater inflater = (LayoutInflater) getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.dialog_pie_colors, null);

        mColorPicker = (ColorPickerView) layout.findViewById(R.id.color_picker_view);
        mColorPanel = (ColorPanelView) layout.findViewById(R.id.color_panel);
        mHexColorInput = (EditText) layout.findViewById(R.id.hex_color_input);

        mColorPanel.setColor(color);
        mColorPicker.setOnColorChangedListener(this);
        mColorPicker.setColor(color, true);
        mHexColorInput.setOnFocusChangeListener(this);

        setView(layout);
        setTitle(R.string.pie_control_color_title);
    }

    @Override
    public void onColorChanged(int color) {
        final boolean hasAlpha = mColorPicker.isAlphaSliderVisible();
        final String format = hasAlpha ? "%08x" : "%06x";
        final int mask = hasAlpha ? 0xFFFFFFFF : 0x00FFFFFF;

        mColorPanel.setColor(color);
        mHexColorInput.setText(String.format(Locale.US, format, color & mask));
    }

    public void setAlphaSliderVisible(boolean visible) {
        mColorPicker.setAlphaSliderVisible(visible);
    }

    public int getColor() {
        return mColorPicker.getColor();
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (!hasFocus) {
            mHexColorInput.removeTextChangedListener(this);
            InputMethodManager inputMethodManager = (InputMethodManager) getContext()
                    .getSystemService(Activity.INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
        } else {
            mHexColorInput.addTextChangedListener(this);
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        String hexColor = mHexColorInput.getText().toString();
        if (!hexColor.isEmpty()) {
            try {
                int color = Color.parseColor(hexColor.indexOf('#') < 0 ? '#' + hexColor : hexColor);
                if (!mColorPicker.isAlphaSliderVisible()) {
                    color |= 0xFF000000; // set opaque
                }
                mColorPicker.setColor(color);
                mColorPanel.setColor(color);
            } catch (IllegalArgumentException ex) {
                // Number format is incorrect, ignore
            }
        }
    }

}
