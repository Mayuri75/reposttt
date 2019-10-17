package com.company.mysapcpsdkproject.logon;

import com.company.mysapcpsdkproject.R;
import com.company.mysapcpsdkproject.app.SAPWizardApplication;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;

import com.sap.cloud.mobile.foundation.common.EncryptionState;
import com.sap.cloud.mobile.foundation.common.EncryptionUtil;
import com.sap.cloud.mobile.onboarding.fingerprint.FingerprintActivity;
import com.sap.cloud.mobile.onboarding.fingerprint.FingerprintErrorSettings;
import com.sap.cloud.mobile.onboarding.fingerprint.FingerprintSettings;
import com.sap.cloud.mobile.onboarding.passcode.EnterPasscodeActivity;
import com.sap.cloud.mobile.onboarding.passcode.EnterPasscodeSettings;
import com.sap.cloud.mobile.onboarding.passcode.PasscodePolicy;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UnlockActivity extends Activity {
    private static final int PASSCODE_UNLOCK = 1;
    private static final int FINGERPRINT_UNLOCK = 2;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Can't refresh the policy from the server yet because the store is locked, and necessary
        // credentials could be in the store.
        ClientPolicyManager clientPolicyManager = ((SAPWizardApplication)getApplication()).getClientPolicyManager();
        SecureStoreManager secureStoreManager = ((SAPWizardApplication)getApplication()).getSecureStoreManager();
        PasscodePolicy passcodePolicy = clientPolicyManager.getClientPolicy(false).getPasscodePolicy();
        if (passcodePolicy.allowsFingerprint() && secureStoreManager
                .getApplicationStoreState() == EncryptionState.PASSCODE_BIOMETRIC) {
            unlockWithFingerprint();
        } else {
            unlockWithPasscode();
        }
    }

    private void unlockWithPasscode() {
        SecureStoreManager secureStoreManager = ((SAPWizardApplication)getApplication()).getSecureStoreManager();
        if (!secureStoreManager.isApplicationStoreOpen()) {
            // if retry limit is reached, then EnterPasscode screen is opened in disabled mode, i.e. only reset is possible
            int currentRetryCount = secureStoreManager.getWithPasscodePolicyStore(
                    passcodePolicyStore -> passcodePolicyStore.getInt(ClientPolicyManager.KEY_RETRY_COUNT)
            );
            int retryLimit = ((SAPWizardApplication) getApplication()).getClientPolicyManager().getClientPolicy(false).getPasscodePolicy().getRetryLimit();
            Intent enterPasscodeIntent = new Intent(this, EnterPasscodeActivity.class);
            EnterPasscodeSettings enterPasscodeSettings = new EnterPasscodeSettings();
            if (retryLimit <= currentRetryCount) {
                // only reset is allowed
                enterPasscodeSettings.setFinalDisabled(true);
            } else {
                enterPasscodeSettings.setMaxAttemptsReachedMessage(getString(R.string.max_retries_title));
                enterPasscodeSettings.setEnterCredentialsMessage(getString(R.string.max_retries_message));
                enterPasscodeSettings.setResetEnabled(true);
                enterPasscodeSettings.setOkButtonString(this.getString(R.string.reset_app));
                enterPasscodeSettings.setResetButtonText(this.getString(R.string.reset_app));
            }
            enterPasscodeSettings.saveToIntent(enterPasscodeIntent);
            startActivityForResult(enterPasscodeIntent, PASSCODE_UNLOCK);
        }
    }

    private void unlockWithFingerprint() {
		SecureStoreManager secureStoreManager = ((SAPWizardApplication)getApplication()).getSecureStoreManager();
        if (!secureStoreManager.isApplicationStoreOpen()) {
			Intent intent = new Intent(this, FingerprintActivity.class);
			FingerprintSettings fingerprintSettings = new FingerprintSettings();
			fingerprintSettings.setFallbackButtonTitle(getString(R.string.use_passcode));
			fingerprintSettings.setFallbackButtonEnabled(true);
			fingerprintSettings.saveToIntent(intent);
			FingerprintErrorSettings fingerprintErrorSettings = new FingerprintErrorSettings();
			fingerprintErrorSettings.setFingerprintErrorResetEnabled(true);
			fingerprintErrorSettings.setFingerprintErrorResetButtonTitle(getString(R.string.use_passcode));
			fingerprintErrorSettings.saveToIntent(intent);
			this.startActivityForResult(intent, FINGERPRINT_UNLOCK);
		}
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FINGERPRINT_UNLOCK && resultCode == RESULT_CANCELED) {
            unlockWithPasscode();
        } else if (resultCode == Activity.RESULT_CANCELED) {
			setResult(RESULT_CANCELED);
            finish();
        } else {
            setResult(RESULT_OK);
            finish();
        }
    }
}
