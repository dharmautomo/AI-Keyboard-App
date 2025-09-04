package org.dslul.openboard.inputmethod.latin.utils;

import android.content.Context;
import android.os.IBinder;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

/**
 * Utility class untuk menangani error pada input method operations
 * dan mencegah force close
 */
public class InputMethodErrorHandler {
    private static final String TAG = "InputMethodErrorHandler";

    /**
     * Validasi parameter untuk setInputMethodAndSubtype
     */
    public static boolean validateSetInputMethodAndSubtypeParams(
            IBinder token, String imiId, InputMethodSubtype subtype) {
        if (token == null) {
            Log.e(TAG, "validateSetInputMethodAndSubtypeParams: token is null");
            return false;
        }
        if (imiId == null || imiId.isEmpty()) {
            Log.e(TAG, "validateSetInputMethodAndSubtypeParams: imiId is null or empty");
            return false;
        }
        if (subtype == null) {
            Log.e(TAG, "validateSetInputMethodAndSubtypeParams: subtype is null");
            return false;
        }
        return true;
    }

    /**
     * Validasi parameter untuk switchToNextInputMethod
     */
    public static boolean validateSwitchToNextInputMethodParams(IBinder token) {
        if (token == null) {
            Log.e(TAG, "validateSwitchToNextInputMethodParams: token is null");
            return false;
        }
        return true;
    }

    /**
     * Validasi InputMethodManager
     */
    public static boolean validateInputMethodManager(Context context) {
        if (context == null) {
            Log.e(TAG, "validateInputMethodManager: context is null");
            return false;
        }
        
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) {
            Log.e(TAG, "validateInputMethodManager: InputMethodManager is null");
            return false;
        }
        return true;
    }

    /**
     * Log error dengan stack trace yang lengkap
     */
    public static void logError(String methodName, String message, Exception e) {
        Log.e(TAG, "Error in " + methodName + ": " + message, e);
        if (e != null) {
            Log.e(TAG, "Stack trace:", e);
        }
    }

    /**
     * Log warning dengan informasi yang berguna
     */
    public static void logWarning(String methodName, String message) {
        Log.w(TAG, "Warning in " + methodName + ": " + message);
    }

    /**
     * Log info untuk debugging
     */
    public static void logInfo(String methodName, String message) {
        Log.i(TAG, "Info in " + methodName + ": " + message);
    }
} 