package com.company.mysapcpsdkproject.logon;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.support.v4.app.Fragment;

import com.company.mysapcpsdkproject.R;
import com.company.mysapcpsdkproject.app.SAPWizardApplication;
import com.sap.cloud.mobile.foundation.authentication.AppLifecycleCallbackHandler;
import com.sap.cloud.mobile.foundation.common.EncryptionError;
import com.sap.cloud.mobile.foundation.common.EncryptionState;
import com.sap.cloud.mobile.foundation.common.EncryptionUtil;
import com.sap.cloud.mobile.foundation.securestore.OpenFailureException;
import com.sap.cloud.mobile.onboarding.fingerprint.FingerprintActionHandler;
import com.sap.cloud.mobile.onboarding.passcode.EnterPasscodeActivity;
import com.sap.cloud.mobile.onboarding.passcode.EnterPasscodeSettings;
import com.sap.cloud.mobile.onboarding.passcode.PasscodePolicy;


import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;

/**
 * Handles the callbacks from {@link com.sap.cloud.mobile.onboarding.fingerprint.FingerprintActivity}
 * to allow the user to user their fingerprint to unlock the app.
 */
public class FingerprintActionHandlerImpl implements FingerprintActionHandler {
    // The user's passcode.  This array is cleared as soon as the passcode is no longer needed.
    private static char[] passcode;
    // Tracks the application state so that appropriate action is taken if the user cancels the
    // fingerprint screen.
    private static boolean disableOnCancel = false;

    /**
     * Provides the cipher that will be authenticated by the user.
     * @param fragment The active UI Fragment.
     * @return The cipher that will be authenticated by the user.
     */
    @Override
    public Cipher getCipher(Fragment fragment) {
        try {
            return EncryptionUtil.getCipher(SecureStoreManager.APP_SECURE_STORE_PCODE_ALIAS);
        } catch (EncryptionError e) {
            return null;
        }
    }

    /**
     * Receives the cipher after it has been authenticated by the user.  If fingerprint is not
     * currently enabled, this method will enable it.  Otherwise this method unlocks the secure
     * store with the cipher.  After the store is unlocked the server is queried for the passcode
     * policy to make sure using fingerprint is still allowed.
     * @param fragment The active UI Fragment.
     * @param cipher The cipher that has been authenticated by the user.
     */
    @Override
    public void startDone(Fragment fragment, Cipher cipher) {
        try {
            SecureStoreManager secureStoreManager = getSecureStoreManager(fragment);
            if (secureStoreManager.getApplicationStoreState() == EncryptionState.PASSCODE_BIOMETRIC) {
                secureStoreManager.openApplicationStore(cipher);
                checkPolicyStillAllowsFingerprint(secureStoreManager, getClientPolicyUtilities(fragment));
            } else {
                EncryptionUtil.enableBiometric(SecureStoreManager.APP_SECURE_STORE_PCODE_ALIAS, passcode, cipher);
            }
        } catch (EncryptionError | OpenFailureException e) {
        } finally {
            clearPasscode();
        }
        Activity activity = fragment.getActivity();
        if (activity != null) {
            activity.setResult(Activity.RESULT_OK);
            activity.finish();
        }
    }

    /**
     * This method should be invoked when the user presses the fallback button.  Depending on the
     * app state this method may disable fingerprint.
     * @param fragment The active UI Fragment.
     */
    @Override
    public void fallback(Fragment fragment) {
        SecureStoreManager secureStoreManager = getSecureStoreManager(fragment);
        try {
            if (disableOnCancel) {
                try {
                    if (secureStoreManager.getApplicationStoreState() == EncryptionState.PASSCODE_BIOMETRIC) {
                        EncryptionUtil.disableBiometric(SecureStoreManager.APP_SECURE_STORE_PCODE_ALIAS, passcode);
                    }
                    setDisableOnCancel(false);
                } catch (EncryptionError e) {
                }
            }
        } finally {
            clearPasscode();
        }
        Activity activity = fragment.getActivity();
        if (activity != null) {
            activity.setResult(Activity.RESULT_CANCELED);
            activity.finish();
        }
    }

    /**
     * This method is called when the user failed to authenticate with fingerprint too many times.
     * This method simply delegates to {@link FingerprintActionHandlerImpl#fallback(Fragment)}.
     * @param fragment The active UI Fragment.
     */
    @Override
    public void shouldResetPasscode(Fragment fragment) {
        // Don't want to reset if the user can't enter fingerprint, just do the fallback.
        fallback(fragment);
    }

    /**
     * This method dictates how a cancellation on the fingerprint screen is handled.
     * @param shouldDisableOnCancel true indicates if the fingerprint screen is cancelled
     *                              fingerprint should be disabled.
     */
    public static void setDisableOnCancel(boolean shouldDisableOnCancel) {
        disableOnCancel = shouldDisableOnCancel;
    }

    /**
     * This method receives the passcode, which is required when enabling fingerprint.  The array
     * containing the passcode is cleared as soon as it is no longer needed.
     * @param passcode The user's passcode.
     */
    public static void setPasscode(char[] passcode) {
        FingerprintActionHandlerImpl.passcode = passcode;
    }

    /**
     * Helper to clear the passcode.
     */
    private static void clearPasscode() {
        if (passcode != null) {
            Arrays.fill(passcode, ' ');
            passcode = null;
        }
    }

    /**
     * This method gets the passcode policy from the server to make sure using fingerprint is still
     * allowed.
     */
    private void checkPolicyStillAllowsFingerprint(SecureStoreManager secureStoreManager, ClientPolicyManager clientPolicyManager) {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> {
            PasscodePolicy passcodePolicy = clientPolicyManager.getClientPolicy(true).getPasscodePolicy();
            if (!passcodePolicy.allowsFingerprint()) {
                policyDisabledFingerprint(secureStoreManager);
            }
        });
        executorService.shutdown();
    }

    /**
     * This method handles the case when the policy has changed so that fingerprint is no longer
     * allowed.  We have to get the user to enter their passcode to disable fingerprint (the
     * passcode member variable only gets set when fingerprint is being enabled, not when
     * unlocking).
     */
    private void policyDisabledFingerprint(SecureStoreManager secureStoreManager) {
        if (secureStoreManager.getApplicationStoreState() == EncryptionState.PASSCODE_BIOMETRIC ) {
            Activity activity = AppLifecycleCallbackHandler.getInstance().getActivity();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(() -> {
                AlertDialog.Builder alertBuilder = new AlertDialog.Builder(activity, R.style.AlertDialogStyle);
                alertBuilder.setTitle(activity.getString(R.string.policy_changed));
                alertBuilder.setMessage(activity.getString(R.string.fingerprint_no_longer_allowed));
                alertBuilder.setPositiveButton(activity.getString(R.string.ok), null);
                alertBuilder.setOnDismissListener(dialog -> {
                    Intent intent = new Intent(activity, EnterPasscodeActivity.class);
                    EnterPasscodeSettings enterPasscodeSettings = new EnterPasscodeSettings();
                    enterPasscodeSettings.setMaxAttemptsReachedMessage(activity.getString(R.string.max_retries_title));
                    enterPasscodeSettings.setEnterCredentialsMessage(activity.getString(R.string.max_retries_message));
                    enterPasscodeSettings.setResetEnabled(true);
                    enterPasscodeSettings.saveToIntent(intent);
                    activity.startActivity(intent);
                });
                alertBuilder.create().show();
            });
        }
    }

    private SecureStoreManager getSecureStoreManager(Fragment fragment) {
        return ((SAPWizardApplication)fragment.getActivity().getApplication()).getSecureStoreManager();
    }

    private ClientPolicyManager getClientPolicyUtilities(Fragment fragment) {
        return ((SAPWizardApplication)fragment.getActivity().getApplication()).getClientPolicyManager();
    }
}
