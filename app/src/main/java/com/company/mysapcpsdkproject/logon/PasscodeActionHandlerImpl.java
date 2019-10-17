package com.company.mysapcpsdkproject.logon;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Resources;
import android.content.Intent;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.support.v4.app.Fragment;

import com.company.mysapcpsdkproject.R;
import com.company.mysapcpsdkproject.app.ConfigurationData;
import com.company.mysapcpsdkproject.app.ErrorHandler;
import com.company.mysapcpsdkproject.app.ErrorMessage;
import com.company.mysapcpsdkproject.app.SAPWizardApplication;
import com.sap.cloud.mobile.foundation.authentication.AppLifecycleCallbackHandler;
import com.sap.cloud.mobile.foundation.common.EncryptionError;
import com.sap.cloud.mobile.foundation.common.EncryptionState;
import com.sap.cloud.mobile.foundation.common.EncryptionUtil;
import com.sap.cloud.mobile.foundation.securestore.OpenFailureException;
import com.sap.cloud.mobile.onboarding.fingerprint.FingerprintActivity;
import com.sap.cloud.mobile.onboarding.fingerprint.FingerprintSettings;
import com.sap.cloud.mobile.onboarding.passcode.PasscodeActionHandler;
import com.sap.cloud.mobile.onboarding.passcode.PasscodeInputMode;
import com.sap.cloud.mobile.onboarding.passcode.PasscodePolicy;
import com.sap.cloud.mobile.onboarding.passcode.PasscodeValidationException;
import com.sap.cloud.mobile.onboarding.passcode.SetPasscodeActivity;
import com.sap.cloud.mobile.onboarding.passcode.SetPasscodeSettings;
import com.sap.cloud.mobile.onboarding.passcode.PasscodeValidationFailedToMatchException;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Arrays;
import java.util.Calendar;

public class PasscodeActionHandlerImpl implements PasscodeActionHandler {

    private static AlertDialog alertDialog;
    private static char[] oldPasscode;

    @Override
    public void shouldTryPasscode(final char[] passcode, PasscodeInputMode mode, final Fragment fragment) throws PasscodeValidationException {
        boolean isSettingFingerprint = false;

        SAPWizardApplication sapWizardApplication = getSAPWizardApplication(fragment);
        SecureStoreManager secureStoreManager = sapWizardApplication.getSecureStoreManager();
        ErrorHandler errorHandler = sapWizardApplication.getErrorHandler();
        ClientPolicyManager clientPolicyManager = sapWizardApplication.getClientPolicyManager();

        switch (mode) {
            case CREATE:
                // change from default to user pc
                try {
                    EncryptionUtil.enablePasscode(SecureStoreManager.APP_SECURE_STORE_PCODE_ALIAS, passcode);
                    secureStoreManager.openApplicationStore(
                            Arrays.copyOf(passcode, passcode.length));
                    updatePasscodeTimestamp(secureStoreManager);

                    sapWizardApplication.setIsOnboarded(true);
                    if (clientPolicyManager.getClientPolicy(false).getPasscodePolicy().allowsFingerprint()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            FingerprintManager fingerprintManager = fragment.getActivity().getSystemService(FingerprintManager.class);
                            if (fingerprintManager.isHardwareDetected() && fingerprintManager.hasEnrolledFingerprints()) {
                                if (secureStoreManager.getApplicationStoreState() == EncryptionState.PASSCODE_ONLY) {
                                    Intent intent = new Intent(fragment.getActivity(), FingerprintActivity.class);
                                    FingerprintSettings fingerprintSettings = new FingerprintSettings();
                                    fingerprintSettings.setFallbackButtonTitle(fragment.getString(R.string.skip_fingerprint));
                                    fingerprintSettings.setFallbackButtonEnabled(true);
                                    fingerprintSettings.saveToIntent(intent);
                                    FingerprintActionHandlerImpl.setPasscode(passcode);
                                    fragment.getActivity().startActivity(intent);
                                    isSettingFingerprint = true;
                                }
                            }
                        }
                    }
                    if (!isSettingFingerprint) {
                        clearPasscode(passcode);
                    }
                } catch(EncryptionError | OpenFailureException e) {
                    Resources res = sapWizardApplication.getResources();
                    String errorTitle = res.getString(R.string.invalid_passcode);
                    String errorDetails = res.getString(R.string.invalid_passcode_detail);
                    ErrorMessage errorMessage = new ErrorMessage(errorTitle, errorDetails);
                    errorHandler.sendErrorMessage(errorMessage);
                }
                break;
            case CHANGE:
                try {
                    EncryptionUtil.changePasscode(SecureStoreManager.APP_SECURE_STORE_PCODE_ALIAS, oldPasscode, passcode);
                    updatePasscodeTimestamp(secureStoreManager);
                } catch(EncryptionError e) {
                    Resources res = sapWizardApplication.getResources();
                    ErrorMessage errorMessage = new ErrorMessage(res.getString(R.string.passcode_change_error), res.getString(R.string.passcode_change_error_detail));
                    errorHandler.sendErrorMessage(errorMessage);
                    throw new PasscodeValidationException("Invalid Passcode", e);
                } finally {
                    clearPasscode(oldPasscode);
                    oldPasscode = null;
                }
                if (clientPolicyManager.getClientPolicy(false).getPasscodePolicy().allowsFingerprint()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        FingerprintManager fingerprintManager = fragment.getActivity().getSystemService(FingerprintManager.class);
                        if (fingerprintManager != null && fingerprintManager.isHardwareDetected() && fingerprintManager.hasEnrolledFingerprints()) {
                            if (secureStoreManager.getApplicationStoreState() == EncryptionState.PASSCODE_ONLY ||
                                    secureStoreManager.getApplicationStoreState() == EncryptionState.PASSCODE_BIOMETRIC) {
                                Intent intent = new Intent(fragment.getActivity(), FingerprintActivity.class);
                                FingerprintSettings fingerprintSettings = new FingerprintSettings();
                                fingerprintSettings.setFallbackButtonTitle(fragment.getString(R.string.skip_fingerprint));
                                fingerprintSettings.setFallbackButtonEnabled(true);
                                fingerprintSettings.saveToIntent(intent);
                                FingerprintActionHandlerImpl.setDisableOnCancel(true);
                                FingerprintActionHandlerImpl.setPasscode(passcode);
                                fragment.getActivity().startActivity(intent);
                                isSettingFingerprint = true;
                            }
                        }
                    }
                }
                if (!isSettingFingerprint) {
                    clearPasscode(passcode);
                }
                break;
            case MATCH:
                matchPasscode(clientPolicyManager, secureStoreManager, sapWizardApplication, passcode);
                ExecutorService executorService = Executors.newSingleThreadExecutor();
                executorService.submit(() -> {
                    ClientPolicy clientPolicy = clientPolicyManager.getClientPolicy(true);
                    PasscodePolicy passcodePolicy = clientPolicy.getPasscodePolicy();
                    if (!passcodePolicy.allowsFingerprint() &&
                            secureStoreManager.getApplicationStoreState() == EncryptionState.PASSCODE_BIOMETRIC) {
                        // Policy no longer allows fingerprint, but fingerprint is currently enabled.
                        try {
                            EncryptionUtil.disableBiometric(SecureStoreManager.APP_SECURE_STORE_PCODE_ALIAS, passcode);
                        } catch (EncryptionError encryptionError) {
                        }
                    }
                    if (!passcodePolicy.validate(passcode) || secureStoreManager.isPasscodeExpired()) {
                        if ( !(alertDialog != null && alertDialog.isShowing()) ) {
                            oldPasscode = passcode;
                            AppLifecycleCallbackHandler.getInstance().getActivity().runOnUiThread(() -> {
                                Activity activity = AppLifecycleCallbackHandler.getInstance().getActivity();
                                AlertDialog.Builder alertBuilder = new AlertDialog.Builder(activity, R.style.AlertDialogStyle);
                                alertBuilder.setTitle(activity.getString(R.string.new_passcode_required));
                                alertBuilder.setMessage(activity.getString(R.string.new_passcode_required_detail));
                                alertBuilder.setPositiveButton(activity.getString(R.string.ok), null);
                                alertBuilder.setOnDismissListener(dialog -> {
                                    if( !(AppLifecycleCallbackHandler.getInstance().getActivity() instanceof SetPasscodeActivity) ) {
                                        Intent intent = new Intent(activity, SetPasscodeActivity.class);
                                        SetPasscodeSettings setPasscodeSettings = new SetPasscodeSettings();
                                        setPasscodeSettings.setChangePasscode(true);
                                        setPasscodeSettings.saveToIntent(intent);
                                        activity.startActivity(intent);
                                    }
                                });
                                alertDialog = alertBuilder.create();
                                alertDialog.show();
                            });
                        }
                    } else {
                        clearPasscode(passcode);
                    }
                });
                executorService.shutdown();
                break;
            case MATCHFORCHANGE:
                matchPasscode(clientPolicyManager, secureStoreManager, sapWizardApplication, passcode);
                oldPasscode = passcode;
                break;
            default:
                clearPasscode(passcode);
                throw new Error("Unknown input mode");
        }
    }

    private void clearPasscode(char[] passcode) {
        if(passcode != null) {
            Arrays.fill(passcode, ' ');
        }
    }

    private void updatePasscodeTimestamp(SecureStoreManager secureStoreManager) {
        secureStoreManager.doWithPasscodePolicyStore(passcodePolicyStore -> {
            passcodePolicyStore.put(ClientPolicyManager.KEY_PC_WAS_SET_AT, Calendar.getInstance());
        });
    }

    private void matchPasscode(ClientPolicyManager clientPolicyManager, SecureStoreManager secureStoreManager, Context context, char[] passcode) throws PasscodeValidationFailedToMatchException {
        int currentRetryCount = secureStoreManager.getWithPasscodePolicyStore(
                passcodePolicyStore -> passcodePolicyStore.getInt(ClientPolicyManager.KEY_RETRY_COUNT)
        );
        int retryLimit = clientPolicyManager.getClientPolicy(false).getPasscodePolicy().getRetryLimit();
        try {
            secureStoreManager.reopenApplicationStoreWithPasscode(Arrays.copyOf(passcode, passcode.length));
            // reset retry count on success
            secureStoreManager.doWithPasscodePolicyStore(passcodePolicyStore -> {
                passcodePolicyStore.put(ClientPolicyManager.KEY_RETRY_COUNT, 0);
            });
        } catch(EncryptionError | OpenFailureException e) {
            // invalid passcode
            currentRetryCount++;
            int remaining = retryLimit - currentRetryCount;
            final int newRetryCount = currentRetryCount;
            secureStoreManager.doWithPasscodePolicyStore(passcodePolicyStore -> {
                passcodePolicyStore.put(ClientPolicyManager.KEY_RETRY_COUNT, newRetryCount);
            });
            Resources res = context.getResources();
            throw new PasscodeValidationFailedToMatchException(res.getString(R.string.invalid_passcode), remaining, e);
        }
    }

    @Override
    public void shouldResetPasscode(Fragment fragment) {

        SAPWizardApplication sapWizardApplication = getSAPWizardApplication(fragment);
        final SecureStoreManager secureStoreManager = sapWizardApplication.getSecureStoreManager();
        ClientPolicyManager clientPolicyManager = sapWizardApplication.getClientPolicyManager();
        int retryLimit = clientPolicyManager.getClientPolicy(false).getPasscodePolicy().getRetryLimit();
        int currentRetryCount = secureStoreManager.getWithPasscodePolicyStore(
                passcodePolicyStore -> passcodePolicyStore.getInt(ClientPolicyManager.KEY_RETRY_COUNT)
        );
        Activity activity = AppLifecycleCallbackHandler.getInstance().getActivity();

        if (retryLimit == currentRetryCount) {
            sapWizardApplication.resetApp(activity);
        } else {
            activity.runOnUiThread(() -> sapWizardApplication.resetAppWithUserConfirmation());
        }
    }

    @Override
    public void didSkipPasscodeSetup(Fragment fragment) {
		
        SAPWizardApplication sapWizardApplication = getSAPWizardApplication(fragment);
        SecureStoreManager secureStoreManager = sapWizardApplication.getSecureStoreManager();

        if (secureStoreManager.isUserPasscodeSet()) {
            if (oldPasscode != null) {
                try {
                    EncryptionUtil.disablePasscode(SecureStoreManager.APP_SECURE_STORE_PCODE_ALIAS, oldPasscode);
                } catch (EncryptionError e) {
                    Resources res = fragment.getActivity().getResources();
                    ErrorMessage errorMessage = new ErrorMessage(res.getString(R.string.passcode_change_error), res.getString(R.string.passcode_change_error_detail_default));
                    sapWizardApplication.getErrorHandler().sendErrorMessage(errorMessage);
                } finally {
                    clearPasscode(oldPasscode);
                    oldPasscode = null;
                }
            } else {
                finish(fragment);
            }
        } else {
            try {
                secureStoreManager.openApplicationStore();
            } catch(EncryptionError | OpenFailureException e) {
            }
        }
		finish(fragment);
    }

    private void finish(Fragment fragment) {
        getSAPWizardApplication(fragment).setIsOnboarded(true);

        Intent intent = new Intent();
        fragment.getActivity().setResult(Activity.RESULT_OK, intent);
        fragment.getActivity().finish();
    }

    /**
     * Starts retrieving the passcode policy.
     *
     * @param fragment the enclosing fragment invoking this handler, must be non-null
     * @return the passcode policy
     */
    @Override
    public PasscodePolicy getPasscodePolicy(Fragment fragment) {
        ClientPolicyManager clientPolicyManager = getSAPWizardApplication(fragment).getClientPolicyManager();


        // The policy should have been refreshed by UnlockActivity.  Only force a refresh here if it
        // is null.
        PasscodePolicy passcodePolicy = clientPolicyManager.getClientPolicy(false).getPasscodePolicy();
        if (passcodePolicy == null) {
            passcodePolicy = clientPolicyManager.getClientPolicy(true).getPasscodePolicy();
        }
        return passcodePolicy;
    }

    private SAPWizardApplication getSAPWizardApplication(Fragment fragment) {
        return (SAPWizardApplication)fragment.getActivity().getApplication();
    }
}
