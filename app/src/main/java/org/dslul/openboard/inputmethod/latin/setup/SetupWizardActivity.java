/*
 * Copyright (C) 2013 The Android Open Source Project
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

package org.dslul.openboard.inputmethod.latin.setup;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.Resources;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;
import android.app.AlertDialog;
import android.content.DialogInterface;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.settings.SettingsActivity;
import org.dslul.openboard.inputmethod.latin.utils.LeakGuardHandlerWrapper;
import org.dslul.openboard.inputmethod.latin.utils.UncachedInputMethodManagerUtils;

import java.util.ArrayList;

import javax.annotation.Nonnull;

// TODO: Use Fragment to implement welcome screen and setup steps.
public final class SetupWizardActivity extends Activity implements View.OnClickListener {
    static final String TAG = SetupWizardActivity.class.getSimpleName();

    // For debugging purpose.
    private static final boolean FORCE_TO_SHOW_WELCOME_SCREEN = false;
    private static final boolean ENABLE_WELCOME_VIDEO = true;

    private InputMethodManager mImm;

    private View mSetupWizard;
    private View mWelcomeScreen;
    private View mSetupScreen;
    private Uri mWelcomeVideoUri;
    private VideoView mWelcomeVideoView;
    private ImageView mWelcomeImageView;
    private View mActionStart;
    private View mActionNext;
    private TextView mStep1Bullet;
    private TextView mActionFinish;
    private SetupStepGroup mSetupStepGroup;
    private static final String STATE_STEP = "step";
    private int mStepNumber;
    private boolean mNeedsToAdjustStepNumberToSystemState;
    private static final int STEP_WELCOME = 0;
    private static final int STEP_1 = 1;
    private static final int STEP_2 = 2;
    private static final int STEP_3 = 3;
    private static final int STEP_LAUNCHING_IME_SETTINGS = 4;
    private static final int STEP_BACK_FROM_IME_SETTINGS = 5;

    private SettingsPoolingHandler mHandler;

    private static final class SettingsPoolingHandler
            extends LeakGuardHandlerWrapper<SetupWizardActivity> {
        private static final int MSG_POLLING_IME_SETTINGS = 0;
        private static final long IME_SETTINGS_POLLING_INTERVAL = 200;

        private final InputMethodManager mImmInHandler;

        public SettingsPoolingHandler(@Nonnull final SetupWizardActivity ownerInstance,
                final InputMethodManager imm) {
            super(ownerInstance);
            mImmInHandler = imm;
        }

        @Override
        public void handleMessage(final Message msg) {
            try {
                final SetupWizardActivity setupWizardActivity = getOwnerInstance();
                if (setupWizardActivity == null) {
                    Log.w("SettingsPoolingHandler", "handleMessage: owner instance is null");
                    return;
                }
                
                if (setupWizardActivity.isFinishing() || setupWizardActivity.isDestroyed()) {
                    Log.w("SettingsPoolingHandler", "handleMessage: activity is finishing or destroyed");
                    return;
                }
                
                switch (msg.what) {
                case MSG_POLLING_IME_SETTINGS:
                    try {
                        if (UncachedInputMethodManagerUtils.isThisImeEnabled(setupWizardActivity,
                                mImmInHandler)) {
                            setupWizardActivity.invokeSetupWizardOfThisIme();
                            return;
                        }
                        startPollingImeSettings();
                    } catch (Exception e) {
                        Log.e("SettingsPoolingHandler", "Error in MSG_POLLING_IME_SETTINGS: " + e.getMessage(), e);
                        // Stop polling if there's an error
                        cancelPollingImeSettings();
                    }
                    break;
                default:
                    Log.w("SettingsPoolingHandler", "handleMessage: unknown message type: " + msg.what);
                    break;
                }
            } catch (Exception e) {
                Log.e("SettingsPoolingHandler", "Critical error in handleMessage: " + e.getMessage(), e);
                // Stop polling if there's a critical error
                try {
                    cancelPollingImeSettings();
                } catch (Exception cancelError) {
                    Log.e("SettingsPoolingHandler", "Error canceling polling: " + cancelError.getMessage(), cancelError);
                }
            }
        }

        public void startPollingImeSettings() {
            try {
                sendMessageDelayed(obtainMessage(MSG_POLLING_IME_SETTINGS),
                        IME_SETTINGS_POLLING_INTERVAL);
            } catch (Exception e) {
                Log.e("SettingsPoolingHandler", "Error starting polling: " + e.getMessage(), e);
            }
        }

        public void cancelPollingImeSettings() {
            try {
                removeMessages(MSG_POLLING_IME_SETTINGS);
            } catch (Exception e) {
                Log.e("SettingsPoolingHandler", "Error canceling polling: " + e.getMessage(), e);
            }
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        try {
            setTheme(android.R.style.Theme_Translucent_NoTitleBar);
            super.onCreate(savedInstanceState);

            mImm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if (mImm == null) {
                Log.e(TAG, "InputMethodManager is null, cannot continue setup");
                android.widget.Toast.makeText(this, "Cannot initialize input method manager", android.widget.Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            
            mHandler = new SettingsPoolingHandler(this, mImm);
            if (mHandler == null) {
                Log.e(TAG, "SettingsPoolingHandler creation failed");
                android.widget.Toast.makeText(this, "Cannot initialize setup handler", android.widget.Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            setContentView(R.layout.setup_wizard);
            mSetupWizard = findViewById(R.id.setup_wizard);
            if (mSetupWizard == null) {
                Log.e(TAG, "setup_wizard view not found");
                finish();
                return;
            }

            if (savedInstanceState == null) {
                mStepNumber = determineSetupStepNumberFromLauncher();
            } else {
                mStepNumber = savedInstanceState.getInt(STATE_STEP);
            }

            final String applicationName = getResources().getString(getApplicationInfo().labelRes);
            mWelcomeScreen = findViewById(R.id.setup_welcome_screen);
            if (mWelcomeScreen == null) {
                Log.e(TAG, "setup_welcome_screen view not found");
                finish();
                return;
            }
            
            final TextView welcomeTitle = findViewById(R.id.setup_welcome_title);
            if (welcomeTitle != null) {
                welcomeTitle.setText(getString(R.string.setup_welcome_title, applicationName));
            }

            mSetupScreen = findViewById(R.id.setup_steps_screen);
            if (mSetupScreen == null) {
                Log.e(TAG, "setup_steps_screen view not found");
                finish();
                return;
            }
            
            final TextView stepsTitle = findViewById(R.id.setup_title);
            if (stepsTitle != null) {
                stepsTitle.setText(getString(R.string.setup_steps_title, applicationName));
            }

            final SetupStepIndicatorView indicatorView =
                    findViewById(R.id.setup_step_indicator);
            if (indicatorView == null) {
                Log.e(TAG, "setup_step_indicator view not found");
                finish();
                return;
            }
            mSetupStepGroup = new SetupStepGroup(indicatorView);

            mStep1Bullet = findViewById(R.id.setup_step1_bullet);
            if (mStep1Bullet == null) {
                Log.e(TAG, "setup_step1_bullet view not found");
                finish();
                return;
            }
            mStep1Bullet.setOnClickListener(this);
            final SetupStep step1 = new SetupStep(STEP_1, applicationName,
                    mStep1Bullet, findViewById(R.id.setup_step1),
                    R.string.setup_step1_title, R.string.setup_step1_instruction,
                    R.string.setup_step1_finished_instruction, R.drawable.ic_setup_step1,
                    R.string.setup_step1_action);
            final SettingsPoolingHandler handler = mHandler;
            step1.setAction(new Runnable() {
                @Override
                public void run() {
                    invokeLanguageAndInputSettings();
                    handler.startPollingImeSettings();
                }
            });
            mSetupStepGroup.addStep(step1);

            final SetupStep step2 = new SetupStep(STEP_2, applicationName,
                    (TextView)findViewById(R.id.setup_step2_bullet), findViewById(R.id.setup_step2),
                    R.string.setup_step2_title, R.string.setup_step2_instruction,
                    0 /* finishedInstruction */, R.drawable.ic_setup_step2,
                    R.string.setup_step2_action);
            step2.setAction(new Runnable() {
                @Override
                public void run() {
                    invokeInputMethodPicker();
                }
            });
            mSetupStepGroup.addStep(step2);

            final SetupStep step3 = new SetupStep(STEP_3, applicationName,
                    (TextView)findViewById(R.id.setup_step3_bullet), findViewById(R.id.setup_step3),
                    R.string.setup_step3_title, R.string.setup_step3_instruction,
                    0 /* finishedInstruction */, R.drawable.ic_setup_step3,
                    R.string.setup_step3_action);
            step3.setAction(new Runnable() {
                @Override
                public void run() {
                    invokeSubtypeEnablerOfThisIme();
                }
            });
            mSetupStepGroup.addStep(step3);

            mWelcomeVideoUri = new Uri.Builder()
                    .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                    .authority(getPackageName())
                    .path(String.valueOf(R.raw.setup_welcome_video))
                    .build();

            mWelcomeVideoView = findViewById(R.id.setup_welcome_video);
            mWelcomeImageView = findViewById(R.id.setup_welcome_image);
            if (mWelcomeVideoView == null || mWelcomeImageView == null) {
                Log.e(TAG, "Video or image view not found");
                finish();
                return;
            }

            mActionStart = findViewById(R.id.setup_start_label);
            mActionNext = findViewById(R.id.setup_next);
            mActionFinish = findViewById(R.id.setup_finish);
            if (mActionStart == null || mActionNext == null || mActionFinish == null) {
                Log.e(TAG, "Action buttons not found");
                finish();
                return;
            }

            mActionStart.setOnClickListener(this);
            mActionNext.setOnClickListener(this);
            mActionFinish.setOnClickListener(this);
            
            // Set finish button icon
            try {
                mActionFinish.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    getResources().getDrawable(R.drawable.ic_setup_finish), null, null, null);
            } catch (Exception e) {
                Log.e(TAG, "Error setting finish button icon: " + e.getMessage(), e);
            }
            
            updateSetupStepView();
        } catch (Exception e) {
            Log.e(TAG, "Critical error in onCreate: " + e.getMessage(), e);
            // Show error message to user
            try {
                android.widget.Toast.makeText(this, "Setup error: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
            } catch (Exception toastError) {
                Log.e(TAG, "Error showing toast: " + toastError.getMessage(), toastError);
            }
            // Finish activity to prevent further crashes
            try {
                finish();
            } catch (Exception finishError) {
                Log.e(TAG, "Error finishing activity: " + finishError.getMessage(), finishError);
            }
        }
    }

    @Override
    public void onClick(final View v) {
        try {
            if (v == mActionFinish) {
                finish();
                return;
            }
            final int currentStep = determineSetupStepNumber();
            final int nextStep;
            if (v == mActionStart) {
                nextStep = STEP_1;
            } else if (v == mActionNext) {
                nextStep = mStepNumber + 1;
            } else if (v == mStep1Bullet && currentStep == STEP_2) {
                nextStep = STEP_1;
            } else {
                nextStep = mStepNumber;
            }
            if (mStepNumber != nextStep) {
                mStepNumber = nextStep;
                updateSetupStepView();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onClick: " + e.getMessage(), e);
            // Fallback: show welcome screen if there's an error
            try {
                mStepNumber = STEP_WELCOME;
                updateSetupStepView();
            } catch (Exception fallbackError) {
                Log.e(TAG, "Fallback error in onClick: " + fallbackError.getMessage(), fallbackError);
            }
        }
    }

    void invokeSetupWizardOfThisIme() {
        try {
            if (isFinishing() || isDestroyed()) {
                Log.w(TAG, "invokeSetupWizardOfThisIme: Activity is finishing or destroyed");
                return;
            }
            
            // Prevent infinite loop by checking if we're already in setup wizard
            if (getClass().equals(SetupWizardActivity.class)) {
                Log.w(TAG, "invokeSetupWizardOfThisIme: Already in SetupWizardActivity, preventing infinite loop");
                return;
            }
            
            final Intent intent = new Intent();
            intent.setClass(this, SetupWizardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            
            // Check if the intent can be resolved before starting the activity
            if (getPackageManager().resolveActivity(intent, 0) != null) {
                startActivity(intent);
                mNeedsToAdjustStepNumberToSystemState = true;
            } else {
                Log.e(TAG, "invokeSetupWizardOfThisIme: No activity found to handle intent");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException in invokeSetupWizardOfThisIme: " + e.getMessage(), e);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in invokeSetupWizardOfThisIme: " + e.getMessage(), e);
        }
    }

    private void invokeSettingsOfThisIme() {
        try {
            if (isFinishing() || isDestroyed()) {
                Log.w(TAG, "invokeSettingsOfThisIme: Activity is finishing or destroyed");
                return;
            }
            
            final Intent intent = new Intent();
            intent.setClass(this, SettingsActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra(SettingsActivity.EXTRA_ENTRY_KEY,
                    SettingsActivity.EXTRA_ENTRY_VALUE_APP_ICON);
            
            // Check if the intent can be resolved before starting the activity
            if (getPackageManager().resolveActivity(intent, 0) != null) {
                startActivity(intent);
            } else {
                Log.e(TAG, "invokeSettingsOfThisIme: No activity found to handle intent");
                android.widget.Toast.makeText(this, "Settings not available", 
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException in invokeSettingsOfThisIme: " + e.getMessage(), e);
            android.widget.Toast.makeText(this, "Permission denied to access settings", 
                    android.widget.Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in invokeSettingsOfThisIme: " + e.getMessage(), e);
            android.widget.Toast.makeText(this, "Unable to open settings", 
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    void invokeLanguageAndInputSettings() {
        try {
            final Intent intent = new Intent();
            intent.setAction(Settings.ACTION_INPUT_METHOD_SETTINGS);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            
            // Check if the intent can be resolved before starting the activity
            if (getPackageManager().resolveActivity(intent, 0) != null) {
                startActivity(intent);
                mNeedsToAdjustStepNumberToSystemState = true;
            } else {
                Log.e(TAG, "invokeLanguageAndInputSettings: No activity found to handle intent");
                android.widget.Toast.makeText(this, "Language & input settings not available", 
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException in invokeLanguageAndInputSettings: " + e.getMessage(), e);
            android.widget.Toast.makeText(this, "Permission denied to access language settings", 
                    android.widget.Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in invokeLanguageAndInputSettings: " + e.getMessage(), e);
            android.widget.Toast.makeText(this, "Unable to open language settings", 
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    void invokeInputMethodPicker() {
        try {
            if (mImm == null) {
                Log.e(TAG, "invokeInputMethodPicker: InputMethodManager is null");
                android.widget.Toast.makeText(this, "Input method manager not available", 
                        android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Invoke input method picker.
            mImm.showInputMethodPicker();
            mNeedsToAdjustStepNumberToSystemState = true;
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException in invokeInputMethodPicker: " + e.getMessage(), e);
            android.widget.Toast.makeText(this, "Permission denied to show input method picker", 
                    android.widget.Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in invokeInputMethodPicker: " + e.getMessage(), e);
            android.widget.Toast.makeText(this, "Unable to show input method picker", 
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    void invokeSubtypeEnablerOfThisIme() {
        try {
            if (mImm == null) {
                Log.e(TAG, "invokeSubtypeEnablerOfThisIme: InputMethodManager is null");
                showSubtypeEnablerUnavailableDialog();
                return;
            }
            
            final InputMethodInfo imi =
                    UncachedInputMethodManagerUtils.getInputMethodInfoOf(getPackageName(), mImm);
            if (imi == null) {
                Log.e(TAG, "invokeSubtypeEnablerOfThisIme: InputMethodInfo is null");
                showSubtypeEnablerUnavailableDialog();
                return;
            }
            
            final String imiId = imi.getId();
            if (imiId == null) {
                Log.e(TAG, "invokeSubtypeEnablerOfThisIme: InputMethodInfo ID is null");
                showSubtypeEnablerUnavailableDialog();
                return;
            }
            
            final Intent intent = new Intent();
            intent.setAction(Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.putExtra(Settings.EXTRA_INPUT_METHOD_ID, imiId);
            
            // Check if the intent can be resolved before starting the activity
            if (getPackageManager().resolveActivity(intent, 0) != null) {
                startActivity(intent);
            } else {
                Log.e(TAG, "invokeSubtypeEnablerOfThisIme: No activity found to handle intent");
                // Fallback: show a toast or dialog to inform user
                showSubtypeEnablerUnavailableDialog();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException in invokeSubtypeEnablerOfThisIme: " + e.getMessage(), e);
            showSubtypeEnablerUnavailableDialog();
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in invokeSubtypeEnablerOfThisIme: " + e.getMessage(), e);
            showSubtypeEnablerUnavailableDialog();
        }
    }

    private void showSubtypeEnablerUnavailableDialog() {
        try {
            if (isFinishing() || isDestroyed()) {
                Log.w(TAG, "showSubtypeEnablerUnavailableDialog: Activity is finishing or destroyed");
                return;
            }
            
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            if (builder == null) {
                Log.e(TAG, "showSubtypeEnablerUnavailableDialog: AlertDialog.Builder is null");
                showFallbackToast();
                return;
            }
            
            builder.setTitle("Language Configuration")
                    .setMessage("The language configuration screen is not available for this input method. You can still use the keyboard with the current language settings.")
                    .setPositiveButton("OK", null)
                    .setNegativeButton("Go to Settings", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Fallback to general IME settings
                            try {
                                if (isFinishing() || isDestroyed()) {
                                    Log.w(TAG, "showSubtypeEnablerUnavailableDialog onClick: Activity is finishing or destroyed");
                                    return;
                                }
                                
                                final Intent intent = new Intent();
                                intent.setAction(Settings.ACTION_INPUT_METHOD_SETTINGS);
                                intent.addCategory(Intent.CATEGORY_DEFAULT);
                                if (getPackageManager().resolveActivity(intent, 0) != null) {
                                    startActivity(intent);
                                } else {
                                    Log.e(TAG, "showSubtypeEnablerUnavailableDialog onClick: No activity found for IME settings");
                                    showFallbackToast();
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error launching IME settings: " + e.getMessage(), e);
                                showFallbackToast();
                            }
                        }
                    });
            
            AlertDialog dialog = builder.create();
            if (dialog == null) {
                Log.e(TAG, "showSubtypeEnablerUnavailableDialog: AlertDialog is null");
                showFallbackToast();
                return;
            }
            
            dialog.show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing dialog: " + e.getMessage(), e);
            // Fallback to toast if dialog fails
            showFallbackToast();
        }
    }
    
    private void showFallbackToast() {
        try {
            if (isFinishing() || isDestroyed()) {
                Log.w(TAG, "showFallbackToast: Activity is finishing or destroyed");
                return;
            }
            
            android.widget.Toast.makeText(this, 
                    "Language configuration not available. You can configure languages in Android Settings > System > Languages & input > Virtual keyboard.",
                    android.widget.Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing fallback toast: " + e.getMessage(), e);
        }
    }

    private int determineSetupStepNumberFromLauncher() {
        final int stepNumber = determineSetupStepNumber();
        if (stepNumber == STEP_1) {
            return STEP_WELCOME;
        }
        if (stepNumber == STEP_3) {
            return STEP_LAUNCHING_IME_SETTINGS;
        }
        return stepNumber;
    }

    private int determineSetupStepNumber() {
        try {
            if (mHandler != null) {
                try {
                    mHandler.cancelPollingImeSettings();
                } catch (Exception e) {
                    Log.e(TAG, "Error canceling polling IME settings: " + e.getMessage(), e);
                }
            }
            
            if (FORCE_TO_SHOW_WELCOME_SCREEN) {
                return STEP_1;
            }
            if (mImm == null) {
                Log.e(TAG, "determineSetupStepNumber: InputMethodManager is null");
                return STEP_1;
            }
            
            boolean isEnabled = false;
            try {
                isEnabled = UncachedInputMethodManagerUtils.isThisImeEnabled(this, mImm);
            } catch (Exception e) {
                Log.e(TAG, "Error checking if IME is enabled: " + e.getMessage(), e);
                return STEP_1;
            }
            
            if (!isEnabled) {
                return STEP_1;
            }
            
            boolean isCurrent = false;
            try {
                isCurrent = UncachedInputMethodManagerUtils.isThisImeCurrent(this, mImm);
            } catch (Exception e) {
                Log.e(TAG, "Error checking if IME is current: " + e.getMessage(), e);
                return STEP_1;
            }
            
            if (!isCurrent) {
                return STEP_2;
            }
            return STEP_3;
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException in determineSetupStepNumber: " + e.getMessage(), e);
            return STEP_1;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in determineSetupStepNumber: " + e.getMessage(), e);
            return STEP_1;
        }
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        try {
            super.onSaveInstanceState(outState);
            outState.putInt(STATE_STEP, mStepNumber);
        } catch (Exception e) {
            Log.e(TAG, "Error in onSaveInstanceState: " + e.getMessage(), e);
            // Continue with super call even if saving fails
            try {
                super.onSaveInstanceState(outState);
            } catch (Exception superError) {
                Log.e(TAG, "Error in super.onSaveInstanceState: " + superError.getMessage(), superError);
            }
        }
    }

    @Override
    protected void onRestoreInstanceState(final Bundle savedInstanceState) {
        try {
            super.onRestoreInstanceState(savedInstanceState);
            if (savedInstanceState != null) {
                mStepNumber = savedInstanceState.getInt(STATE_STEP, STEP_WELCOME);
            } else {
                mStepNumber = STEP_WELCOME;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onRestoreInstanceState: " + e.getMessage(), e);
            // Fallback: set to welcome screen if there's an error
            try {
                super.onRestoreInstanceState(savedInstanceState);
                mStepNumber = STEP_WELCOME;
            } catch (Exception superError) {
                Log.e(TAG, "Error in super.onRestoreInstanceState: " + superError.getMessage(), superError);
                mStepNumber = STEP_WELCOME;
            }
        }
    }

    private static boolean isInSetupSteps(final int stepNumber) {
        return stepNumber >= STEP_1 && stepNumber <= STEP_3;
    }

    @Override
    protected void onRestart() {
        try {
            super.onRestart();
            // Probably the setup wizard has been invoked from "Recent" menu. The setup step number
            // needs to be adjusted to system state, because the state (IME is enabled and/or current)
            // may have been changed.
            if (isInSetupSteps(mStepNumber)) {
                try {
                    mStepNumber = determineSetupStepNumber();
                } catch (Exception e) {
                    Log.e(TAG, "Error determining setup step in onRestart: " + e.getMessage(), e);
                    // Fallback: set to welcome screen if there's an error
                    mStepNumber = STEP_WELCOME;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Critical error in onRestart: " + e.getMessage(), e);
            // Fallback: set to welcome screen if there's an error
            try {
                mStepNumber = STEP_WELCOME;
            } catch (Exception fallbackError) {
                Log.e(TAG, "Fallback error in onRestart: " + fallbackError.getMessage(), fallbackError);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (mStepNumber == STEP_LAUNCHING_IME_SETTINGS) {
                // Prevent white screen flashing while launching settings activity.
                mSetupWizard.setVisibility(View.INVISIBLE);
                invokeSettingsOfThisIme();
                mStepNumber = STEP_BACK_FROM_IME_SETTINGS;
                return;
            }
            if (mStepNumber == STEP_BACK_FROM_IME_SETTINGS) {
                finish();
                return;
            }
            updateSetupStepView();
        } catch (Exception e) {
            Log.e(TAG, "Error in onResume: " + e.getMessage(), e);
            // Fallback: show welcome screen if there's an error
            try {
                mStepNumber = STEP_WELCOME;
                updateSetupStepView();
            } catch (Exception fallbackError) {
                Log.e(TAG, "Fallback error in onResume: " + fallbackError.getMessage(), fallbackError);
            }
        }
    }

    @Override
    public void onBackPressed() {
        try {
            if (mStepNumber == STEP_1) {
                mStepNumber = STEP_WELCOME;
                updateSetupStepView();
                return;
            }
            super.onBackPressed();
        } catch (Exception e) {
            Log.e(TAG, "Error in onBackPressed: " + e.getMessage(), e);
            // Fallback: finish activity if there's an error
            try {
                finish();
            } catch (Exception finishError) {
                Log.e(TAG, "Error finishing activity: " + finishError.getMessage(), finishError);
            }
        }
    }

    void hideWelcomeVideoAndShowWelcomeImage() {
        try {
            if (mWelcomeVideoView != null) {
                mWelcomeVideoView.setVisibility(View.GONE);
            }
            if (mWelcomeImageView != null) {
                mWelcomeImageView.setImageResource(R.raw.setup_welcome_image);
                mWelcomeImageView.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in hideWelcomeVideoAndShowWelcomeImage: " + e.getMessage(), e);
        }
    }

    private void showAndStartWelcomeVideo() {
        try {
            if (mWelcomeVideoView != null) {
                mWelcomeVideoView.setVisibility(View.VISIBLE);
                mWelcomeVideoView.setVideoURI(mWelcomeVideoUri);
                mWelcomeVideoView.start();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in showAndStartWelcomeVideo: " + e.getMessage(), e);
            // Fallback to image if video fails
            try {
                hideWelcomeVideoAndShowWelcomeImage();
            } catch (Exception fallbackError) {
                Log.e(TAG, "Fallback error in showAndStartWelcomeVideo: " + fallbackError.getMessage(), fallbackError);
            }
        }
    }

    private void hideAndStopWelcomeVideo() {
        try {
            if (mWelcomeVideoView != null) {
                mWelcomeVideoView.stopPlayback();
                mWelcomeVideoView.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in hideAndStopWelcomeVideo: " + e.getMessage(), e);
        }
    }

    @Override
    protected void onPause() {
        try {
            hideAndStopWelcomeVideo();
            super.onPause();
        } catch (Exception e) {
            Log.e(TAG, "Error in onPause: " + e.getMessage(), e);
            // Continue with super.onPause even if video handling fails
            try {
                super.onPause();
            } catch (Exception superError) {
                Log.e(TAG, "Error in super.onPause: " + superError.getMessage(), superError);
            }
        }
    }

    @Override
    public void onWindowFocusChanged(final boolean hasFocus) {
        try {
            super.onWindowFocusChanged(hasFocus);
            if (hasFocus && mNeedsToAdjustStepNumberToSystemState) {
                mNeedsToAdjustStepNumberToSystemState = false;
                try {
                    mStepNumber = determineSetupStepNumber();
                    updateSetupStepView();
                } catch (Exception e) {
                    Log.e(TAG, "Error in onWindowFocusChanged step adjustment: " + e.getMessage(), e);
                    // Fallback: set to welcome screen if there's an error
                    try {
                        mStepNumber = STEP_WELCOME;
                        updateSetupStepView();
                    } catch (Exception fallbackError) {
                        Log.e(TAG, "Fallback error in onWindowFocusChanged: " + fallbackError.getMessage(), fallbackError);
                        // Last resort: finish activity if everything fails
                        try {
                            finish();
                        } catch (Exception finishError) {
                            Log.e(TAG, "Error finishing activity: " + finishError.getMessage(), finishError);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Critical error in onWindowFocusChanged: " + e.getMessage(), e);
            // Critical error: finish activity to prevent further crashes
            try {
                finish();
            } catch (Exception finishError) {
                Log.e(TAG, "Error finishing activity: " + finishError.getMessage(), finishError);
            }
        }
    }

    private void updateSetupStepView() {
        try {
            mSetupWizard.setVisibility(View.VISIBLE);
            final boolean welcomeScreen = (mStepNumber == STEP_WELCOME);
            mWelcomeScreen.setVisibility(welcomeScreen ? View.VISIBLE : View.GONE);
            mSetupScreen.setVisibility(welcomeScreen ? View.GONE : View.VISIBLE);
            if (welcomeScreen) {
                if (ENABLE_WELCOME_VIDEO) {
                    showAndStartWelcomeVideo();
                } else {
                    hideWelcomeVideoAndShowWelcomeImage();
                }
                return;
            }
            hideAndStopWelcomeVideo();
            final boolean isStepActionAlreadyDone = mStepNumber < determineSetupStepNumber();
            mSetupStepGroup.enableStep(mStepNumber, isStepActionAlreadyDone);
            mActionNext.setVisibility(isStepActionAlreadyDone ? View.VISIBLE : View.GONE);
            mActionFinish.setVisibility((mStepNumber == STEP_3) ? View.VISIBLE : View.GONE);
        } catch (Exception e) {
            Log.e(TAG, "Error in updateSetupStepView: " + e.getMessage(), e);
            // Fallback: show welcome screen if there's an error
            try {
                mStepNumber = STEP_WELCOME;
                mSetupWizard.setVisibility(View.VISIBLE);
                mWelcomeScreen.setVisibility(View.VISIBLE);
                mSetupScreen.setVisibility(View.GONE);
                if (ENABLE_WELCOME_VIDEO) {
                    showAndStartWelcomeVideo();
                } else {
                    hideWelcomeVideoAndShowWelcomeImage();
                }
            } catch (Exception fallbackError) {
                Log.e(TAG, "Fallback error in updateSetupStepView: " + fallbackError.getMessage(), fallbackError);
            }
        }
    }

    static final class SetupStep implements View.OnClickListener {
        public final int mStepNo;
        private final View mStepView;
        private final TextView mBulletView;
        private final int mActivatedColor;
        private final int mDeactivatedColor;
        private final String mInstruction;
        private final String mFinishedInstruction;
        private final TextView mActionLabel;
        private Runnable mAction;

        public SetupStep(final int stepNo, final String applicationName, final TextView bulletView,
                final View stepView, final int title, final int instruction,
                final int finishedInstruction, final int actionIcon, final int actionLabel) {
            mStepNo = stepNo;
            mStepView = stepView;
            mBulletView = bulletView;
            final Resources res = stepView.getResources();
            mActivatedColor = res.getColor(R.color.setup_text_action);
            mDeactivatedColor = res.getColor(R.color.setup_text_dark);

            final TextView titleView = mStepView.findViewById(R.id.setup_step_title);
            titleView.setText(res.getString(title, applicationName));
            mInstruction = (instruction == 0) ? null
                    : res.getString(instruction, applicationName);
            mFinishedInstruction = (finishedInstruction == 0) ? null
                    : res.getString(finishedInstruction, applicationName);

            mActionLabel = mStepView.findViewById(R.id.setup_step_action_label);
            mActionLabel.setText(res.getString(actionLabel));
            if (actionIcon == 0) {
                final int paddingEnd = mActionLabel.getPaddingEnd();
                mActionLabel.setPaddingRelative(paddingEnd, 0, paddingEnd, 0);
            } else {
                mActionLabel.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        res.getDrawable(actionIcon), null, null, null);
            }
        }

        public void setEnabled(final boolean enabled, final boolean isStepActionAlreadyDone) {
            try {
                if (mStepView == null) {
                    Log.e("SetupStep", "setEnabled: mStepView is null");
                    return;
                }
                
                if (mBulletView == null) {
                    Log.e("SetupStep", "setEnabled: mBulletView is null");
                    return;
                }
                
                if (mActionLabel == null) {
                    Log.e("SetupStep", "setEnabled: mActionLabel is null");
                    return;
                }
                
                try {
                    mStepView.setVisibility(enabled ? View.VISIBLE : View.GONE);
                } catch (Exception e) {
                    Log.e("SetupStep", "Error setting step view visibility: " + e.getMessage(), e);
                }
                
                try {
                    mBulletView.setTextColor(enabled ? mActivatedColor : mDeactivatedColor);
                } catch (Exception e) {
                    Log.e("SetupStep", "Error setting bullet view color: " + e.getMessage(), e);
                }
                
                final TextView instructionView = mStepView.findViewById(R.id.setup_step_instruction);
                if (instructionView != null) {
                    try {
                        final String textToSet = isStepActionAlreadyDone ? mFinishedInstruction : mInstruction;
                        if (textToSet != null) {
                            instructionView.setText(textToSet);
                        }
                    } catch (Exception e) {
                        Log.e("SetupStep", "Error setting instruction text: " + e.getMessage(), e);
                    }
                }
                
                try {
                    mActionLabel.setVisibility(isStepActionAlreadyDone ? View.GONE : View.VISIBLE);
                } catch (Exception e) {
                    Log.e("SetupStep", "Error setting action label visibility: " + e.getMessage(), e);
                }
            } catch (Exception e) {
                Log.e("SetupStep", "Error in setEnabled: " + e.getMessage(), e);
            }
        }

        public void setAction(final Runnable action) {
            try {
                if (mActionLabel != null) {
                    mActionLabel.setOnClickListener(this);
                }
                mAction = action;
            } catch (Exception e) {
                Log.e("SetupStep", "Error in setAction: " + e.getMessage(), e);
            }
        }

        @Override
        public void onClick(final View v) {
            try {
                if (v == mActionLabel && mAction != null) {
                    mAction.run();
                    return;
                }
            } catch (Exception e) {
                Log.e("SetupStep", "Error in onClick: " + e.getMessage(), e);
            }
        }
    }

    static final class SetupStepGroup {
        private final SetupStepIndicatorView mIndicatorView;
        private final ArrayList<SetupStep> mGroup = new ArrayList<>();

        public SetupStepGroup(final SetupStepIndicatorView indicatorView) {
            mIndicatorView = indicatorView;
        }

        public void addStep(final SetupStep step) {
            try {
                if (step != null && mGroup != null) {
                    mGroup.add(step);
                } else {
                    Log.e("SetupStepGroup", "addStep: step or mGroup is null");
                }
            } catch (Exception e) {
                Log.e("SetupStepGroup", "Error in addStep: " + e.getMessage(), e);
            }
        }

        public void enableStep(final int enableStepNo, final boolean isStepActionAlreadyDone) {
            try {
                if (mIndicatorView == null) {
                    Log.e("SetupStepGroup", "enableStep: mIndicatorView is null");
                    return;
                }
                
                if (mGroup == null || mGroup.isEmpty()) {
                    Log.e("SetupStepGroup", "enableStep: mGroup is null or empty");
                    return;
                }
                
                for (final SetupStep step : mGroup) {
                    try {
                        if (step != null) {
                            step.setEnabled(step.mStepNo == enableStepNo, isStepActionAlreadyDone);
                        }
                    } catch (Exception e) {
                        Log.e("SetupStepGroup", "Error enabling step " + (step != null ? step.mStepNo : "null") + ": " + e.getMessage(), e);
                    }
                }
                
                try {
                    mIndicatorView.setIndicatorPosition(enableStepNo - STEP_1, mGroup.size());
                } catch (Exception e) {
                    Log.e("SetupStepGroup", "Error setting indicator position: " + e.getMessage(), e);
                }
            } catch (Exception e) {
                Log.e("SetupStepGroup", "Error in enableStep: " + e.getMessage(), e);
            }
        }
    }
}

