package com.company.mysapcpsdkproject.logon;

import android.support.annotation.NonNull;
import com.company.mysapcpsdkproject.R;
import com.company.mysapcpsdkproject.app.ErrorMessage;
import com.company.mysapcpsdkproject.app.SAPWizardApplication;
import com.sap.cloud.mobile.foundation.authentication.AppLifecycleCallbackHandler;
import com.sap.cloud.mobile.foundation.common.ClientProvider;
import com.sap.cloud.mobile.foundation.common.SettingsParameters;
import com.sap.cloud.mobile.foundation.networking.WebkitCookieJar;
import com.sap.cloud.mobile.foundation.settings.Settings;
import com.sap.cloud.mobile.onboarding.passcode.PasscodePolicy;

import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ch.qos.logback.classic.Level;

public class ClientPolicyManager {

    public static final String KEY_RETRY_COUNT = "retryCount";
    public static final String KEY_PC_WAS_SET_AT = "when_was_the_pc_set";
    public static final String KEY_CLIENT_LOG_LEVEL = "client_log_level";

    private static final String PASSCODE_POLICY_FINGERPRINT_ENABLED = "passwordPolicyFingerprintEnabled";
    private static final String PASSCODE_POLICY_DIGIT_REQUIRED = "passwordPolicyDigitRequired";
    private static final String PASSCODE_POLICY_LOWER_REQUIRED = "passwordPolicyLowerRequired";
    private static final String PASSCODE_POLICY_SPECIAL_REQUIRED = "passwordPolicySpecialRequired";
    private static final String PASSCODE_POLICY_UPPER_REQUIRED = "passwordPolicyUpperRequired";
    private static final String PASSCODE_POLICY_MIN_LENGTH = "passwordPolicyMinLength";
    private static final String PASSCODE_POLICY_MIN_UNIQUE_CHARS = "passwordPolicyMinUniqueChars";
    private static final String PASSCODE_POLICY_RETRY_LIMIT = "passwordPolicyRetryLimit";
    private static final String PASSCODE_POLICY_IS_DIGITS_ONLY = "passwordPolicyIsDigitsOnly";
    private static final String PASSCODE_POLICY_ENABLED = "passwordPolicyEnabled";
    private static final String PASSCODE_POLICY_LOCK_TIMEOUT = "passwordPolicyLockTimeout";
    private static final String PASSCODE_POLICY_EXPIRES_IN_N_DAYS = "passwordPolicyExpiresInNDays";
    private static final String PASSCODE_POLICY_DEFAULT_ENABLED = "passwordPolicyDefaultPasswordAllowed";

    private static final String LOG_POLICY_LOG_LEVEL = "logLevel";
    private static final String LOG_POLICY_LOG_ENABLED = "logEnabled";

    private static final String SETTINGS_PASSCODE = "passwordPolicy";
    private static final String SETTINGS_LOG = "logSettings";

    private static final String KEY_CLIENT_POLICY = "passcodePolicy";

    private static ClientPolicy lastPolicy;
    private static ClientPolicy policyFromServer;

    private final SAPWizardApplication sapWizardApplication;
    private final SecureStoreManager secureStoreManager;

    public ClientPolicyManager(@NonNull  SAPWizardApplication sapWizardApplication) {
        this.sapWizardApplication = sapWizardApplication;
        this.secureStoreManager = sapWizardApplication.getSecureStoreManager();
    }

    /**
     * Gets the client policies, including the passcode policy and the logging policy.
     * @param forceRefresh When true, this method will get the policy from the server.  If the
     *                     policy cannot be retrieved from the server, it will be retrieved from a
     *                     cached policy from a local store.  If there is no cached policy, a
     *                     default policy will be returned.
     *                     When false, this method will get the cached policy from a local store.
     *                     If there is no cached policy, a default policy will be returned.  No
     *                     network requests will be sent when this parameter is false.
     * @return
     */
    public ClientPolicy getClientPolicy(boolean forceRefresh) {

        ClientPolicy clientPolicy = null;
        if (!forceRefresh) {
            if (lastPolicy == null) {
                lastPolicy = getClientPolicyFromStore();
            }
            if (lastPolicy ==  null) {
                lastPolicy = getDefaultPolicy();
            }
            clientPolicy = lastPolicy;
        } else {
            // first try to read it from the server, if not successful then take the local
            // persisted one, finally, fallback to the default one
            getClientPolicyFromServer();
            clientPolicy = policyFromServer;
            if (clientPolicy == null) {
                clientPolicy = getClientPolicyFromStore();
                if (clientPolicy == null) {
                    clientPolicy = getDefaultPolicy();
                }
            } else {
				// store policy and retry count in the RLM store
                final ClientPolicy policy = clientPolicy;
                secureStoreManager.doWithPasscodePolicyStore(passcodePolicyStore -> {
                    passcodePolicyStore.put(KEY_CLIENT_POLICY, policy);
                    passcodePolicyStore.put(KEY_RETRY_COUNT, 0);
                });
            }
            lastPolicy = clientPolicy;
        }
        return clientPolicy;
    }

    private ClientPolicy getDefaultPolicy() {
        ClientPolicy defClientPolicy = new ClientPolicy();
        // There is no default passcode policy, keep the passcode policy null in defClientPolicy.
        defClientPolicy.setLogEnabled(true);
        defClientPolicy.setLogLevel(Level.INFO);
        return defClientPolicy;
    }

    private void getClientPolicyFromServer() {

        policyFromServer = null;

        SettingsParameters settingsParameters = sapWizardApplication.getSettingsParameters();
        if (settingsParameters != null) {
            CountDownLatch downloadLatch = new CountDownLatch(1);
            ExecutorService executor = Executors.newSingleThreadExecutor();

            executor.submit(() -> {
                Settings settings = new Settings(ClientProvider.get(), settingsParameters);
                settings.load(Settings.SettingTarget.DEVICE, "mobileservices/settingsExchange", new PolicyCallbackListener(downloadLatch));
            });

            executor.shutdown();
            try {
                downloadLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private ClientPolicy getClientPolicyFromStore() {
        return secureStoreManager.getWithPasscodePolicyStore(
                passcodePolicyStore -> passcodePolicyStore.getSerializable(KEY_CLIENT_POLICY)
        );
    }

    private static Level logLevelFromServerString(String logLevel) {
        String lowerCaseLogLevel = logLevel.toLowerCase(Locale.getDefault());
        if (lowerCaseLogLevel.equals("none")) {
            return Level.OFF;
        } else if (lowerCaseLogLevel.equals("fatal")) {
            return Level.ERROR;
        } else if (lowerCaseLogLevel.equals("error")) {
            return Level.ERROR;
        } else if (lowerCaseLogLevel.startsWith("warn")) {
            // use startsWith so this matches both the server-provided string WARN, and the user-friendly string Warning.
            return  Level.WARN;
        } else if (lowerCaseLogLevel.equals("info")) {
            return Level.INFO;
        } else if (lowerCaseLogLevel.equals("debug")) {
            return Level.DEBUG;
        } else if (lowerCaseLogLevel.equals("path")) {
            return Level.ALL;
        }
        return Level.DEBUG;
    }

    private class PolicyCallbackListener implements Settings.CallbackListener {

        private CountDownLatch downloadLatch;

        public PolicyCallbackListener(CountDownLatch downloadLatch) {
            this.downloadLatch = downloadLatch;
        }

        public ClientPolicy getPolicyFromServer() {
            return policyFromServer;
        }

        @Override
        public void onSuccess(@NonNull JSONObject result) {
            JSONObject passcodePolicyJson = result.optJSONObject(SETTINGS_PASSCODE);
            if (passcodePolicyJson != null) {
                policyFromServer = new ClientPolicy();
                boolean isPasscodePolicyEnabled = passcodePolicyJson.optBoolean(PASSCODE_POLICY_ENABLED, true);
                policyFromServer.setPasscodePolicyEnabled(isPasscodePolicyEnabled);

                PasscodePolicy passcodePolicy = new PasscodePolicy();
                passcodePolicy.setAllowsFingerprint(passcodePolicyJson.optBoolean(PASSCODE_POLICY_FINGERPRINT_ENABLED, true));
                passcodePolicy.setHasDigit(passcodePolicyJson.optBoolean(PASSCODE_POLICY_DIGIT_REQUIRED, false));
                passcodePolicy.setHasLower(passcodePolicyJson.optBoolean(PASSCODE_POLICY_LOWER_REQUIRED, false));
                passcodePolicy.setHasSpecial(passcodePolicyJson.optBoolean(PASSCODE_POLICY_SPECIAL_REQUIRED, false));
                passcodePolicy.setHasUpper(passcodePolicyJson.optBoolean(PASSCODE_POLICY_UPPER_REQUIRED, false));
                passcodePolicy.setIsDigitsOnly(passcodePolicyJson.optBoolean(PASSCODE_POLICY_IS_DIGITS_ONLY, false)); // Is this actually set on the server??
                passcodePolicy.setMinLength(passcodePolicyJson.optInt(PASSCODE_POLICY_MIN_LENGTH, 8));
                passcodePolicy.setMinUniqueChars(passcodePolicyJson.optInt(PASSCODE_POLICY_MIN_UNIQUE_CHARS, 0));
                passcodePolicy.setRetryLimit(passcodePolicyJson.optInt(PASSCODE_POLICY_RETRY_LIMIT, 20));
                // if policy were enabled, then no default would be allowed
                passcodePolicy.setSkipEnabled(false);
                policyFromServer.setPasscodePolicy(passcodePolicy);
                secureStoreManager.setPasscodeLockTimeout(passcodePolicyJson.optInt(PASSCODE_POLICY_LOCK_TIMEOUT, 300));
                secureStoreManager.setPasscodeExpirationTimeFrame(passcodePolicyJson.optInt(PASSCODE_POLICY_EXPIRES_IN_N_DAYS, 0));
            }

            JSONObject logSettingsJson = result.optJSONObject(SETTINGS_LOG);
            if (logSettingsJson != null) {
                boolean isLogEnabled = logSettingsJson.optBoolean(LOG_POLICY_LOG_ENABLED, false);
                policyFromServer.setLogEnabled(isLogEnabled);
                if (isLogEnabled) {
                    String logLevelStr = logSettingsJson.optString(LOG_POLICY_LOG_LEVEL, "DEBUG");
                    Level logLevel = logLevelFromServerString(logLevelStr);
                    policyFromServer.setLogLevel(logLevel);
                }
            }
            downloadLatch.countDown();
        }

        @Override
        public void onError(@NonNull Throwable throwable) {
            policyFromServer = null;
            downloadLatch.countDown();
        }
    }

}
