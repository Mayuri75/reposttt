package com.company.mysapcpsdkproject.app;

import android.app.Activity;
import android.arch.lifecycle.DefaultLifecycleObserver;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.ProcessLifecycleOwner;
import android.content.Intent;
import android.support.annotation.NonNull;

import com.company.mysapcpsdkproject.logon.LogonActivity;
import com.company.mysapcpsdkproject.logon.SecureStoreManager;
import com.sap.cloud.mobile.foundation.authentication.AppLifecycleCallbackHandler;
import com.sap.cloud.mobile.onboarding.fingerprint.FingerprintActivity;
import com.sap.cloud.mobile.onboarding.passcode.EnterPasscodeActivity;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Singleton class for handling application lifecycle events.
 */
public class SAPWizardLifecycleObserver implements DefaultLifecycleObserver {

    private Timer timer;
    private final Object lock = new Object();

    private final SecureStoreManager secureStoreManager;

    public SAPWizardLifecycleObserver(SecureStoreManager secureStoreManager) {
        this.secureStoreManager = secureStoreManager;
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        synchronized (lock) {
            if (timer != null) {
                timer.cancel();
				timer = null;
			}

			if ((!secureStoreManager.isApplicationStoreOpen())) {
				Activity activity = AppLifecycleCallbackHandler.getInstance().getActivity();
				if (!activity.getClass().equals(LogonActivity.class) && !activity.getClass().equals(EnterPasscodeActivity.class)
					&& !activity.getClass().equals(FingerprintActivity.class)) {
					Intent startIntent = new Intent(activity, LogonActivity.class);
					startIntent.putExtra(LogonActivity.IS_RESUMING_KEY, true);
					activity.startActivity(startIntent);  
				}
            }
        }
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        synchronized (lock) {
            if (timer == null) {
                int timeOut = secureStoreManager.getPasscodeLockTimeout();
                boolean isUserPasscodeSet = secureStoreManager.isUserPasscodeSet();
                if (timeOut >= 0 && isUserPasscodeSet) {
                    timer = new Timer();
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            secureStoreManager.closeApplicationStore();
                        }
                    }, timeOut * 1000);
                }
            }
        }
    }

    /**
     * Method checks if the app is in background or not
     */
    public boolean isAppInBackground() {
        Lifecycle.State currentState = ProcessLifecycleOwner.get().getLifecycle().getCurrentState();
        return !currentState.isAtLeast(Lifecycle.State.RESUMED);
    }
}
