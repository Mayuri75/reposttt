package com.company.mysapcpsdkproject.logon;

import android.content.Context;
import android.support.annotation.NonNull;

import com.sap.cloud.mobile.foundation.common.EncryptionError;
import com.sap.cloud.mobile.foundation.common.EncryptionState;
import com.sap.cloud.mobile.foundation.common.EncryptionUtil;
import com.sap.cloud.mobile.foundation.securestore.OpenFailureException;
import com.sap.cloud.mobile.foundation.securestore.SecureKeyValueStore;
import com.sap.cloud.mobile.odata.core.Action1;
import com.sap.cloud.mobile.odata.core.Function1;


import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.EnumSet;
import java.util.Objects;

import javax.crypto.Cipher;

/**
 * Manages access to encrypted key-value-stores used throughout the application. Two stores are
 * implemented: The <code>passcodePolicyStore</code> and the <code>applicationStore</code>. The
 * former is used to store information that needs to be available prior to unlocking the app,
 * such as the passcode policy. The latter is used to store any other information, such as user
 * credentials and access tokens.
 */
public class SecureStoreManager {

    /** Identifier of the application secure store */
    private static final String APP_SECURE_STORE_NAME = "APP_SECURE_STORE";
    /** Application secure store alias for encryption utility */
    public static final String APP_SECURE_STORE_PCODE_ALIAS = "app_pc_alias";

    /** Identifier of the application secure store */
    private static final String PASSCODE_SECURE_STORE_NAME = "RLM_SECURE_STORE";
    /** Application secure store alias for encryption utility */
    private static final String PASSCODE_SECURE_STORE_PCODE_ALIAS = "rlm_pc_alias";
    private static final String IS_PASSCODE_POLICY_ENABLED = "isPasscodePolicyEnabled";
    private static final String IS_ONBOARDED = "isOnboarded";
    private static final String PASSCODE_POLICY_LOCK_TIMEOUT = "passwordPolicyLockTimeout";
    private static final String PASSCODE_EXPIRATION_TIME_FRAME_DAYS = "passwordPolicyExpiresInNDays";


    /**
     * Secure key-value-store that is protected by a default encryption key.
     */
    private SecureKeyValueStore passcodePolicyStore;

    /**
     * Secure key-value-store that is optionally protected by user-supplied input, such as a
     * passcode, fingerprint or other biometric data. If the user does not choose to supply such
     * input, it is encrypted using a default encryption key.
     */
    private SecureKeyValueStore applicationStore;


    private final Context applicationContext;

    /**
     * Lock preventing concurrent access to the passcode policy store, even between
     * <code>SecureStoreManager</code> instances.
     */
    private static final Object PASSCODE_POLICY_STORE_LOCK = new Object();

    /**
     * Creates a new secure store manager bound to the current application context and initializes
     * the encryption utilities required for encryption operations on secure key-value-stores.
     *
     * @param applicationContext Application context used to access resources
     */
    public SecureStoreManager(Context applicationContext) {
        this.applicationContext = applicationContext;
        EncryptionUtil.initialize(applicationContext);
        passcodePolicyStore = new SecureKeyValueStore(applicationContext, PASSCODE_SECURE_STORE_NAME);
        applicationStore = new SecureKeyValueStore(applicationContext, SecureStoreManager.APP_SECURE_STORE_NAME);
    }

    /**
     * Opens the passcode policy store with the default encryption key. The encryption key is
     * cleared from memory after the operation completes, regardless if it was successful or not.
     *
     * @throws EncryptionError if the passcode policy store cannot be decrypted
     * @throws OpenFailureException if opening the store fails for other reasons
     */
    private void openPasscodePolicyStore() throws EncryptionError, OpenFailureException {
        if (!passcodePolicyStore.isOpen()) {
            byte[] passcodePolicyStoreKey = null;
            try {
                passcodePolicyStoreKey = EncryptionUtil.getEncryptionKey(PASSCODE_SECURE_STORE_PCODE_ALIAS);
                passcodePolicyStore.open(passcodePolicyStoreKey);
            } finally {
                if (passcodePolicyStoreKey != null) {
                    Arrays.fill(passcodePolicyStoreKey, (byte) 0);
                }
            }
        }
    }

    /**
     * Tells if the <code>applicationStore</code> is currently protected by a user-provided passcode.
     *
     * @return true iff passcode (and optionally biometric) protection is enabled
     */
    public boolean isUserPasscodeSet() {
        return EnumSet.of(EncryptionState.PASSCODE_ONLY, EncryptionState.PASSCODE_BIOMETRIC)
                .contains(getApplicationStoreState());
    }

    /**
     * Returns the current application store encryption status.
     *
     * @return current application store encryption status
     */
    public EncryptionState getApplicationStoreState() {
        return EncryptionUtil.getState(SecureStoreManager.APP_SECURE_STORE_PCODE_ALIAS);
    }

    /**
     * Tells if the onboarded flag has been set for the application.
     *
     * @return true iff the user completed onboarding
     */
    public boolean isOnboarded() {
        Boolean isOnboarded =
                getWithPasscodePolicyStore(passcodePolicyStore -> passcodePolicyStore.getBoolean(IS_ONBOARDED));
        if (isOnboarded != null) {
            return isOnboarded;
        } else {
            return false;
        }
    }

    /**
     * Sets the application onboarded flag.
     *
     * @param isOnboarded if the user completed onboarding
     */
    public void setIsOnboarded(boolean isOnboarded) {
        doWithPasscodePolicyStore(passcodePolicyStore -> {
            passcodePolicyStore.put(IS_ONBOARDED, isOnboarded);
        });
    }

    /**
     * Returns the time frame for which passcodes are valid before a new one needs to be created.
     *
     * @return passcode validity time frame in days
     */
    private int getPasscodeExpirationTimeFrame() {
        Integer timeFrame =
                getWithPasscodePolicyStore(passcodePolicyStore -> passcodePolicyStore.getInt(PASSCODE_EXPIRATION_TIME_FRAME_DAYS));
        if (timeFrame != null) {
            return timeFrame;
        } else {
            return -1;
        }
    }

    /**
     * Set the time frame for which passcodes are valid before a new one needs to be created.
     *
     * @param expireIntervalInDays number of days till expiry
     */
    public void setPasscodeExpirationTimeFrame(int expireIntervalInDays) {
        doWithPasscodePolicyStore(passcodePolicyStore -> {
            passcodePolicyStore.put(PASSCODE_EXPIRATION_TIME_FRAME_DAYS, expireIntervalInDays);
        });
    }

    /**
     * Returns the timestamp (milliseconds) at which the passcode expires.
     *
     * @return timestamp (milliseconds) at which passcode expires
     */
    private long getPasscodeExpiresAt() {
        long retVal = 0;
        if (getPasscodeExpirationTimeFrame() > 0) {
            Calendar expiresAt = getWithPasscodePolicyStore(
                    passcodePolicyStore -> passcodePolicyStore.getSerializable(ClientPolicyManager.KEY_PC_WAS_SET_AT));
            if (expiresAt != null) {
                expiresAt.add(Calendar.DAY_OF_YEAR, getPasscodeExpirationTimeFrame());
                Date expiration = expiresAt.getTime();
                retVal = expiresAt.getTimeInMillis();
            }
        }
        return retVal;
    }

    /**
     * Returns the timeout in seconds of user inactivity before encrypted stores should automatically
     * be closed.
     *
     * @return encrypted store lock timeout in seconds
     */
    public int getPasscodeLockTimeout() {
        Integer lockTimeout =
                getWithPasscodePolicyStore(passcodePolicyStore -> passcodePolicyStore.getInt(PASSCODE_POLICY_LOCK_TIMEOUT));
        if (lockTimeout != null) {
            return lockTimeout;
        } else {
            return -1;
        }
    }

    /**
     * Sets the timeout in seconds of user inactivity before encrypted stores should automatically
     * be closed.
     *
     * @param lockTimeout encrypted store lock timeout in seconds
     */
    public void setPasscodeLockTimeout(int lockTimeout) {
        doWithPasscodePolicyStore(passcodePolicyStore -> {
            passcodePolicyStore.put(PASSCODE_POLICY_LOCK_TIMEOUT, lockTimeout);
        });
    }

    /**
     * Tells if a server-side passcode policy has been set.
     *
     * @return if server-side passcode policy has been set
     */
    public boolean isPasscodePolicyEnabled() {
        Boolean isPolicyEnabled =
                getWithPasscodePolicyStore(passcodePolicyStore -> passcodePolicyStore.getBoolean(IS_PASSCODE_POLICY_ENABLED));
        if (isPolicyEnabled != null) {
            return isPolicyEnabled;
        } else {
            return true;
        }
    }

    /**
     * Sets the flag indicating if a server-side passcode policy has been set.
     *
     * @param isPasscodePolicyEnabled flag indicating if server-side passcode policy has been set
     */
    public void setIsPasscodePolicyEnabled(boolean isPasscodePolicyEnabled) {
        doWithPasscodePolicyStore(passcodePolicyStore -> {
            passcodePolicyStore.put(IS_PASSCODE_POLICY_ENABLED, isPasscodePolicyEnabled);
        });
    }

    /**
     * Tells if the currently set passcode has expired.
     *
     * @return if the currently set passcode has expired
     */
    public boolean isPasscodeExpired() {
        if (isPasscodePolicyEnabled() && getPasscodeExpirationTimeFrame() != 0) {
            if (System.currentTimeMillis() - getPasscodeExpiresAt() > 0) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * Deletes and recreates the application encrypted key-value-stores, clearing the data within
     * them. After resetting the stores the user onboarding flow should be restarted so that the
     * <code>applicationStore</code> can be securely re-encrypted with user-provided credentials.
     */
    public void resetStores() {
        resetApplicationStore();
        resetPasscodePolicyStore();
    }

    /**
     * Deletes the physical application store and any related encryption data.
     */
    private void resetApplicationStore() {
        applicationStore.deleteStore(applicationContext);

        try {
            EncryptionUtil.delete(SecureStoreManager.APP_SECURE_STORE_PCODE_ALIAS);
        } catch (EncryptionError encryptionError) {
        } finally {
            applicationStore = new SecureKeyValueStore(applicationContext, APP_SECURE_STORE_NAME);
        }
    }

    /**
     * Deletes the physical passcode policy store and any related encryption data.
     */
    private void resetPasscodePolicyStore() {
        passcodePolicyStore.deleteStore(applicationContext);

        try {
            EncryptionUtil.delete(SecureStoreManager.PASSCODE_SECURE_STORE_PCODE_ALIAS);
        } catch (EncryptionError encryptionError) {
        } finally {
            passcodePolicyStore = new SecureKeyValueStore(applicationContext, PASSCODE_SECURE_STORE_NAME);
        }
    }

    /**
     * Tells if the application store has been opened.
     *
     * @return if the application store has been opened
     */
    public boolean isApplicationStoreOpen() {
        return applicationStore != null && applicationStore.isOpen();
    }

    public void doWithApplicationStore(@NonNull Action1<SecureKeyValueStore> action) {
        Objects.requireNonNull(action);
        if (!isApplicationStoreOpen()) {
            throw new IllegalStateException("Application store has not been unlocked yet.");
        }
        action.call(applicationStore);
    }

    /**
     * Allows operations on the passcode policy store while managing resources. The opened store
     * instance is passed to <code>action</code> and it is closed after the action completes.
     * <p>
     * This method is guarded by {@link #PASSCODE_POLICY_STORE_LOCK}.
     *
     * @param action Action to perform with the passcode policy store
     */
    public void doWithPasscodePolicyStore(@NonNull Action1<SecureKeyValueStore> action) {
        Objects.requireNonNull(action);
        synchronized (PASSCODE_POLICY_STORE_LOCK) {
            try {
                openPasscodePolicyStore();
                action.call(passcodePolicyStore);
            } catch (OpenFailureException | EncryptionError e) {
            } finally {
                passcodePolicyStore.close();
            }
        }
    }

    /**
     * Allows retrieving values from the passcode policy store while managing resources. The opened
     * store instance is passed to <code>function</code> and it is closed after the function completes.
     * <p>
     * This method is guarded by {@link #PASSCODE_POLICY_STORE_LOCK}.
     *
     * @param function Function to perform with the passcode policy store
     */
    public <T> T getWithPasscodePolicyStore(@NonNull Function1<SecureKeyValueStore, T> function) {
        Objects.requireNonNull(function);
        synchronized (PASSCODE_POLICY_STORE_LOCK) {
            try {
                openPasscodePolicyStore();
                return function.call(passcodePolicyStore);
            } catch (OpenFailureException | EncryptionError e) {
                return null;
            } finally {
                passcodePolicyStore.close();
            }
        }
    }

    /**
     * Allows retrieving values from the application store.
     *
     * @param function Function to perform with the passcode policy store
     */
    public <T> T getWithApplicationStore(@NonNull Function1<SecureKeyValueStore, T> function) {
        Objects.requireNonNull(function);
        return function.call(applicationStore);
    }

    /**
     * Reopens the application store with the provided passcode. This should be done whenever the
     * encryption key (e.g. user passcode) is changed. The provided passcode data is cleared from
     * memory after the operation completes, regardless if it is successful or fails.
     *
     * @param passcode new passcode
     * @throws EncryptionError if the store cannot be decrypted
     * @throws OpenFailureException if the store cannot be opened for other reasons
     */
    public void reopenApplicationStoreWithPasscode(@NonNull char[] passcode) throws EncryptionError, OpenFailureException {
        Objects.requireNonNull(passcode);
        applicationStore.close();
        openApplicationStore(passcode);
    }

    /**
     * Opens the application store with the provided cipher. The encryption key derived from the
     * cipher is cleared from memory after the operation completes, regardless if it is successful
     * or fails.
     *
     * @param cipher the cipher to use
     * @throws EncryptionError if the store cannot be decrypted
     * @throws OpenFailureException if the store cannot be opened for other reasons
     */
    public void openApplicationStore(@NonNull Cipher cipher) throws OpenFailureException, EncryptionError {
        Objects.requireNonNull(cipher);
        openApplicationStore(EncryptionUtil.getEncryptionKey(
                SecureStoreManager.APP_SECURE_STORE_PCODE_ALIAS, cipher));
    }

    /**
     * Opens the application store with the provided cipher. The provided passcode data is cleared
     * from memory after the operation completes, regardless if it is successful or fails.
     *
     * @param passcode the passcode to use
     * @throws EncryptionError if the store cannot be decrypted
     * @throws OpenFailureException if the store cannot be opened for other reasons
     */
    public void openApplicationStore(@NonNull char[] passcode) throws EncryptionError, OpenFailureException {
        Objects.requireNonNull(passcode);
        try {
            openApplicationStore(EncryptionUtil.getEncryptionKey(
                    SecureStoreManager.APP_SECURE_STORE_PCODE_ALIAS, passcode));
        } finally {
            Arrays.fill(passcode, ' ');
        }
    }

    /**
     * Opens the application store with the provided encryption key. The key is cleared
     * from memory after the operation completes, regardless if it is successful or fails.
     *
     * @param encryptionKey the passcode to use
     * @throws EncryptionError if the store cannot be decrypted
     * @throws OpenFailureException if the store cannot be opened for other reasons
     */
    private void openApplicationStore(@NonNull byte[] encryptionKey) throws OpenFailureException {
        Objects.requireNonNull(encryptionKey);
        try {
            if (!applicationStore.isOpen()) {
                applicationStore.open(encryptionKey);
            }
        } finally {
            Arrays.fill(encryptionKey, (byte) 0);
        }
    }

    /**
     * Opens the application store with the default encryption key. The key is cleared
     * from memory after the operation completes, regardless if it is successful or fails.
     *
     * @throws EncryptionError if the store cannot be decrypted
     * @throws OpenFailureException if the store cannot be opened for other reasons
     */
    public void openApplicationStore() throws EncryptionError, OpenFailureException {
        if (EnumSet.of(EncryptionState.NO_PASSCODE, EncryptionState.INIT)
                .contains(getApplicationStoreState())) {
            openApplicationStore(EncryptionUtil.getEncryptionKey(
                    SecureStoreManager.APP_SECURE_STORE_PCODE_ALIAS));
        } else {
            throw new OpenFailureException("Expected application store state NO_PASSCODE or INIT but got " +
                    getApplicationStoreState(), null);
        }
    }

    /**
     * Closes the application store, freeing related resources.
     */
    public void closeApplicationStore() {
        if (isApplicationStoreOpen()) {
            applicationStore.close();
        }
    }

}
