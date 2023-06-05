package io.kodebite.ppf;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class PPFSecurity {

    Context context;
    SharedPreferences preferences;
    SharedPreferences.Editor editor;
    String PREF_NAME = "security_ppf_pref_main";
    String PREF_NAME_GLOBAL = "security_ppf_pref_global";
    String PREF_NAME_FINGER = "security_ppf_pref_finger";
    String PREF_NAME_PIN = "security_ppf_pref_pin";
    String PREF_NAME_PIN_ENABLED = "security_ppf_pref_pin_enabled";
    String PREF_NAME_TYPE = "security_ppf_pref_security_type";
    String PREF_NAME_PATTERN = "security_ppf_pref_pattern";
    String PREF_NAME_PATTERN_ENABLED = "security_ppf_pref_pattern_enabled";
    String PREF_NAME_SQ = "security_ppf_pref_sq";


    public PPFSecurity(Context context) {
        this.context = context;
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = preferences.edit();
    }

    //////////////////////////////
    public void setPin(String pin) {
        editor.putString(PREF_NAME_PIN, stringToSHA256(pin));
        editor.apply();
        setPinEnabled(true);
    }

    public boolean isPinSet() {
        return preferences.contains(PREF_NAME_PIN);
    }

    public boolean isPinCorrect(String pin) {
        return preferences.getString(PREF_NAME_PIN, "").equals(stringToSHA256(pin));
    }

    public void clearPin() {
        editor.remove(PREF_NAME_PIN);
        editor.apply();
        setPinEnabled(false);
    }

    public boolean isPinEnabled() {
        return preferences.getBoolean(PREF_NAME_PIN_ENABLED, false);
    }

    public void setPinEnabled(boolean isEnabled) {
        editor.putBoolean(PREF_NAME_PIN_ENABLED, isEnabled);
        editor.apply();
    }

    //////////////////////////////
    public void setPattern(String pattern) {
        editor.putString(PREF_NAME_PATTERN, stringToSHA256(pattern));
        editor.apply();
        setPatternEnabled(true);
    }
    ////////////////////////////////////

    public boolean isPatternSet() {
        return preferences.contains(PREF_NAME_PATTERN);
    }

    public boolean isPatternCorrect(String pattern) {
        return preferences.getString(PREF_NAME_PATTERN, "").equals(stringToSHA256(pattern));
    }

    public void clearPattern() {
        editor.remove(PREF_NAME_PATTERN);
        editor.apply();
        setPatternEnabled(false);
    }

    public boolean isPatternEnabled() {
        return preferences.getBoolean(PREF_NAME_PATTERN_ENABLED, false);
    }

    public void setPatternEnabled(boolean isEnabled) {
        editor.putBoolean(PREF_NAME_PATTERN_ENABLED, isEnabled);
        editor.apply();
    }

    ///////////////////////////////////////
    public void enableFingerPrint(boolean isFingerPrint) {
        editor.putBoolean(PREF_NAME_FINGER, isFingerPrint);
        editor.apply();
    }
    ///////////////////////////////////////

    public boolean isFingerPrintEnabled() {
        return preferences.getBoolean(PREF_NAME_FINGER, false);
    }

    public void clearFingerPrintSettings() {
        editor.remove(PREF_NAME_FINGER);
        editor.apply();
    }

    public PPFSecurityType getSecurityType() {
        String type = preferences.getString(PREF_NAME_TYPE, "");
        if (type.equals(PPFSecurityType.PIN.name())) {
            return PPFSecurityType.PIN;
        } else if (type.equals(PPFSecurityType.PATTERN.name())) {
            return PPFSecurityType.PATTERN;
        } else {
            return null;
        }
    }
    ////////////////////////////////////////

    public void setSecurityType(PPFSecurityType type) {
        editor.putString(PREF_NAME_TYPE, type.name());
        editor.apply();
    }

    public void clearSecurityType() {
        editor.remove(PREF_NAME_TYPE);
        editor.apply();
    }

    public boolean isSecurityTypeSet() {
        return preferences.contains(PREF_NAME_TYPE);
    }

    //////////////////////
    public void setSQ(boolean isSet) {
        editor.putBoolean(PREF_NAME_SQ, isSet);
        editor.apply();
    }

    public boolean isSQSet() {
        return preferences.getBoolean(PREF_NAME_SQ, false);
    }

    public boolean isGlobalToggle() {
        return preferences.getBoolean(PREF_NAME_GLOBAL, false);
    }
    ////////////////////

    ////////////////////////
    public void setGlobalToggle(boolean isGlobal) {
        editor.putBoolean(PREF_NAME_GLOBAL, isGlobal);
        editor.apply();
    }

    private String bin2hex(byte[] data) {
        StringBuilder hex = new StringBuilder(data.length * 2);
        for (byte b : data)
            hex.append(String.format("%02x", b & 0xFF));
        return hex.toString();
    }
    ///////////////////////

    private String stringToSHA256(String string) {
        try {
            MessageDigest digest = null;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e1) {
                Log.e("TAG", "stringToSHA256: ", e1);
            }
            assert digest != null;
            digest.reset();
            return bin2hex(digest.digest(string.getBytes()));
        } catch (Exception ignored) {
            return null;
        }
    }

    public enum PPFSecurityType {
        PIN, PATTERN
    }
}
