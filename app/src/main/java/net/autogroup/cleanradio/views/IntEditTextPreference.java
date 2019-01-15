package net.autogroup.cleanradio.views;

import android.content.Context;
import android.support.v7.preference.EditTextPreference;
import android.util.AttributeSet;

/**
 * Android doesn't provide a way to have integer preferences. This is a quick hack to have them.
 * User can enter anything in the text edit but only valid integer will be saved.
 */
public class IntEditTextPreference extends EditTextPreference {
    private int value = 0;
    private String summaryFormat;

    public IntEditTextPreference(Context context) {
        super(context);
    }

    public IntEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        summaryFormat = getSummary().toString();
    }

    public IntEditTextPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        summaryFormat = getSummary().toString();
    }

    @Override
    public void setText(String text) {
        final boolean wasBlocking = shouldDisableDependents();
        Integer currentValue = parseInteger(text);
        if (currentValue != null) {
            value = currentValue;
            persistInt(value);

            if (summaryFormat != null) {
                setSummary(String.format(summaryFormat, value));
            }
        }

        final boolean isBlocking = shouldDisableDependents();
        if (isBlocking != wasBlocking) {
            notifyDependencyChange(isBlocking);
        }
    }

    @Override
    public String getText() {
        return Integer.toString(value);
    }

    @Override
    protected String getPersistedString(String defaultReturnValue) {
        return String.valueOf(getPersistedInt(-1));
    }

    private static Integer parseInteger(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
