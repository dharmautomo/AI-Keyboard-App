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

package org.dslul.openboard.inputmethod.latin.utils;

import android.content.Context;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.util.Log;
import java.util.List;

/*
 * A utility class for {@link InputMethodManager}. Unlike {@link RichInputMethodManager}, this
 * class provides synchronous, non-cached access to {@link InputMethodManager}. The setup activity
 * is a good example to use this class because {@link InputMethodManagerService} may not be aware of
 * this IME immediately after this IME is installed.
 */
public final class UncachedInputMethodManagerUtils {
    /**
     * Check if the IME specified by the context is enabled.
     * CAVEAT: This may cause a round trip IPC.
     *
     * @param context package context of the IME to be checked.
     * @param imm the {@link InputMethodManager}.
     * @return true if this IME is enabled.
     */
    public static boolean isThisImeEnabled(final Context context,
            final InputMethodManager imm) {
        try {
            if (context == null) {
                Log.e("UncachedInputMethodManagerUtils", "isThisImeEnabled: context is null");
                return false;
            }
            if (imm == null) {
                Log.e("UncachedInputMethodManagerUtils", "isThisImeEnabled: InputMethodManager is null");
                return false;
            }
            
            final String packageName = context.getPackageName();
            if (packageName == null) {
                Log.e("UncachedInputMethodManagerUtils", "isThisImeEnabled: packageName is null");
                return false;
            }
            
            List<InputMethodInfo> enabledInputMethodList = null;
            try {
                enabledInputMethodList = imm.getEnabledInputMethodList();
            } catch (SecurityException e) {
                Log.e("UncachedInputMethodManagerUtils", "SecurityException calling imm.getEnabledInputMethodList(): " + e.getMessage(), e);
                return false;
            } catch (Exception e) {
                Log.e("UncachedInputMethodManagerUtils", "Error calling imm.getEnabledInputMethodList(): " + e.getMessage(), e);
                return false;
            }
            
            if (enabledInputMethodList == null) {
                Log.e("UncachedInputMethodManagerUtils", "isThisImeEnabled: EnabledInputMethodList is null");
                return false;
            }
            
            for (final InputMethodInfo imi : enabledInputMethodList) {
                if (imi != null && packageName.equals(imi.getPackageName())) {
                    return true;
                }
            }
            return false;
        } catch (SecurityException e) {
            Log.e("UncachedInputMethodManagerUtils", "SecurityException in isThisImeEnabled: " + e.getMessage(), e);
            return false;
        } catch (Exception e) {
            Log.e("UncachedInputMethodManagerUtils", "Unexpected error in isThisImeEnabled: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check if the IME specified by the context is the current IME.
     * CAVEAT: This may cause a round trip IPC.
     *
     * @param context package context of the IME to be checked.
     * @param imm the {@link InputMethodManager}.
     * @return true if this IME is the current IME.
     */
    public static boolean isThisImeCurrent(final Context context,
            final InputMethodManager imm) {
        try {
            if (context == null) {
                Log.e("UncachedInputMethodManagerUtils", "isThisImeCurrent: context is null");
                return false;
            }
            if (imm == null) {
                Log.e("UncachedInputMethodManagerUtils", "isThisImeCurrent: InputMethodManager is null");
                return false;
            }
            
            final InputMethodInfo imi = getInputMethodInfoOf(context.getPackageName(), imm);
            if (imi == null) {
                return false;
            }
            
            try {
                final String currentImeId = Settings.Secure.getString(
                        context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
                if (currentImeId == null) {
                    Log.w("UncachedInputMethodManagerUtils", "isThisImeCurrent: currentImeId is null");
                    return false;
                }
                
                return imi.getId().equals(currentImeId);
            } catch (SecurityException e) {
                Log.e("UncachedInputMethodManagerUtils", "SecurityException accessing Settings.Secure: " + e.getMessage(), e);
                return false;
            } catch (Exception e) {
                Log.e("UncachedInputMethodManagerUtils", "Error accessing Settings.Secure: " + e.getMessage(), e);
                return false;
            }
        } catch (SecurityException e) {
            Log.e("UncachedInputMethodManagerUtils", "SecurityException in isThisImeCurrent: " + e.getMessage(), e);
            return false;
        } catch (Exception e) {
            Log.e("UncachedInputMethodManagerUtils", "Unexpected error in isThisImeCurrent: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get {@link InputMethodInfo} of the IME specified by the package name.
     * CAVEAT: This may cause a round trip IPC.
     *
     * @param packageName package name of the IME.
     * @param imm the {@link InputMethodManager}.
     * @return the {@link InputMethodInfo} of the IME specified by the <code>packageName</code>,
     * or null if not found.
     */
    public static InputMethodInfo getInputMethodInfoOf(final String packageName,
            final InputMethodManager imm) {
        try {
            if (imm == null) {
                Log.e("UncachedInputMethodManagerUtils", "getInputMethodInfoOf: InputMethodManager is null");
                return null;
            }
            if (packageName == null) {
                Log.e("UncachedInputMethodManagerUtils", "getInputMethodInfoOf: packageName is null");
                return null;
            }
            
            List<InputMethodInfo> inputMethodList = null;
            try {
                inputMethodList = imm.getInputMethodList();
            } catch (SecurityException e) {
                Log.e("UncachedInputMethodManagerUtils", "SecurityException calling imm.getInputMethodList(): " + e.getMessage(), e);
                return null;
            } catch (Exception e) {
                Log.e("UncachedInputMethodManagerUtils", "Error calling imm.getInputMethodList(): " + e.getMessage(), e);
                return null;
            }
            
            if (inputMethodList == null) {
                Log.e("UncachedInputMethodManagerUtils", "getInputMethodInfoOf: InputMethodList is null");
                return null;
            }
            
            for (final InputMethodInfo imi : inputMethodList) {
                if (imi != null && packageName.equals(imi.getPackageName())) {
                    return imi;
                }
            }
            return null;
        } catch (SecurityException e) {
            Log.e("UncachedInputMethodManagerUtils", "SecurityException in getInputMethodInfoOf: " + e.getMessage(), e);
            return null;
        } catch (Exception e) {
            Log.e("UncachedInputMethodManagerUtils", "Unexpected error in getInputMethodInfoOf: " + e.getMessage(), e);
            return null;
        }
    }
}
