package com.company.mysapcpsdkproject.logon;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import com.company.mysapcpsdkproject.service.SAPServiceManager;
import com.company.mysapcpsdkproject.R;
import com.company.mysapcpsdkproject.app.ConfigurationData;
import com.company.mysapcpsdkproject.app.ErrorHandler;
import com.company.mysapcpsdkproject.app.ErrorMessage;
import com.company.mysapcpsdkproject.app.SAPWizardApplication;

import com.company.mysapcpsdkproject.SimpleActivity;

import com.sap.cloud.mobile.foundation.authentication.AppLifecycleCallbackHandler;
import com.sap.cloud.mobile.foundation.common.EncryptionError;
import com.sap.cloud.mobile.foundation.securestore.OpenFailureException;
import com.sap.cloud.mobile.onboarding.passcode.EnterPasscodeActivity;
import com.sap.cloud.mobile.onboarding.passcode.EnterPasscodeSettings;
import com.sap.cloud.mobile.onboarding.passcode.PasscodePolicy;
import com.sap.cloud.mobile.onboarding.launchscreen.LaunchScreenSettings;
import com.sap.cloud.mobile.onboarding.passcode.SetPasscodeSettings;
import com.sap.cloud.mobile.onboarding.utility.OnboardingType;
import com.sap.cloud.mobile.onboarding.passcode.SetPasscodeActivity;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class LogonActivity extends AppCompatActivity {

    public static final String IS_RESUMING_KEY = "isResuming";
    public static final String IS_AWAITING_RESULT_KEY = "isAwaitingResult";
    private static final int LAUNCH_SCREEN = 100;
    private static final int SET_PASSCODE = 200;
    private static final int ENTER_PASSCODE = 300;
    private static final int SIMPLE_ACTIVITY = 500;


	private boolean isResuming = false;
	private boolean isAwaitingResult = false;

    private SAPServiceManager sapServiceManager;

    private SecureStoreManager secureStoreManager;

    private ClientPolicyManager clientPolicyManager;

    private ErrorHandler errorHandler;

    private ConfigurationData configurationData;

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		isAwaitingResult = false;

		switch (requestCode) {
			case LAUNCH_SCREEN:
				switch (resultCode) {
					case RESULT_OK:
						setPasscode();
						break;
					case CONTEXT_IGNORE_SECURITY:
						startSimpleActivity();
						break;
					case RESULT_CANCELED:
						finish();
						break;
					default:
						startLaunchScreen();
						break;
				}
				break;
			case SET_PASSCODE:
				switch (resultCode) {
					case RESULT_OK:
						startSimpleActivity();
						break;
					case RESULT_CANCELED:
						startLaunchScreen();
						break;
                    case SetPasscodeActivity.POLICY_CANCELLED:
						((SAPWizardApplication) getApplication()).resetApp(this);
						break;
				}
				break;
			case ENTER_PASSCODE:
				switch (resultCode) {
				case RESULT_OK:
					startSimpleActivity();
					break;
				case RESULT_CANCELED:
					// clicking back on EnterPasscode screen will quit the application
                    finishAffinity();
					break;
			}
			break;
			case SIMPLE_ACTIVITY:
				// TODO check if something should be done here?
				break;

			default:
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putBoolean(IS_AWAITING_RESULT_KEY, isAwaitingResult);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (savedInstanceState != null) {
			isAwaitingResult = savedInstanceState.getBoolean(IS_AWAITING_RESULT_KEY);
		}

		sapServiceManager = ((SAPWizardApplication)getApplication()).getSAPServiceManager();
		secureStoreManager = ((SAPWizardApplication)getApplication()).getSecureStoreManager();
		clientPolicyManager = ((SAPWizardApplication)getApplication()).getClientPolicyManager();
		errorHandler = ((SAPWizardApplication)getApplication()).getErrorHandler();
		configurationData = ((SAPWizardApplication)getApplication()).getConfigurationData();

		Bundle bundle = getIntent().getExtras();
		if (bundle != null) {
			isResuming = bundle.getBoolean(IS_RESUMING_KEY, false);
		}
		FingerprintActionHandlerImpl.setDisableOnCancel(false);
		setContentView(R.layout.activity_logon);
		if (isAwaitingResult) {
			return;
		}
		boolean isOnBoarded = ((SAPWizardApplication)getApplication()).isOnboarded();
		if (!isOnBoarded) {
			// create the store for application data (with default passcode)
			try {
				secureStoreManager.openApplicationStore();
				} catch (EncryptionError | OpenFailureException e) {
			}
			startLaunchScreen();
		} else {
			// config data must be present
			if(!configurationData.isLoaded()) {
				// Log an error message and reset the Application
				String errorTitle = getResources().getString(R.string.config_data_error_title);
				String errorDetails = getResources().getString(R.string.config_data_corrupted_description);
				ErrorMessage errorMessage = new ErrorMessage(errorTitle, errorDetails, null, false);
				errorHandler.sendErrorMessage(errorMessage);
				((SAPWizardApplication)getApplication()).resetApp(this);
			} else {
				boolean isUserPasscode = secureStoreManager.isUserPasscodeSet();
				if (isUserPasscode) {
					// user passcode
					if (secureStoreManager.isApplicationStoreOpen()) {
						startSimpleActivity();
					} else {
						enterPasscode();
					}
				} else {
					// default passcode
					openApplicationStore();
					ExecutorService executorService = Executors.newSingleThreadExecutor();
					executorService.submit(() -> {
						ClientPolicy clientPolicy = clientPolicyManager.getClientPolicy(true);
						boolean isPolicyEnabled = clientPolicy.isPasscodePolicyEnabled();
						secureStoreManager.setIsPasscodePolicyEnabled(isPolicyEnabled);
						boolean isDefaultEnabled = clientPolicyManager.getClientPolicy(false).getPasscodePolicy().isSkipEnabled();
						if (isPolicyEnabled && !isDefaultEnabled) {
							LogonActivity.this.runOnUiThread(() -> {
								Activity activity = AppLifecycleCallbackHandler.getInstance().getActivity();
								AlertDialog.Builder alertBuilder = new AlertDialog.Builder(activity, R.style.AlertDialogStyle);
								Resources res = LogonActivity.this.getResources();
								alertBuilder.setTitle(res.getString(R.string.passcode_required));
								alertBuilder.setMessage(res.getString(R.string.passcode_required_detail));
								alertBuilder.setPositiveButton(res.getString(R.string.ok), null);
								alertBuilder.setOnDismissListener(new DialogInterface.OnDismissListener() {
									@Override
									public void onDismiss(DialogInterface dialog) {
										Intent intent = new Intent(activity, SetPasscodeActivity.class);
										SetPasscodeSettings setPasscodeSettings = new SetPasscodeSettings();
										setPasscodeSettings.saveToIntent(intent);
										isAwaitingResult = true;
										LogonActivity.this.startActivityForResult(intent, SET_PASSCODE);
									}
								});
							});
						}
					});
					executorService.shutdown();
					startSimpleActivity();
				}
			}
		}
	}


	private void startLaunchScreen() {
		Intent welcome = new Intent(this, com.sap.cloud.mobile.onboarding.launchscreen.LaunchScreenActivity.class);
		LaunchScreenSettings launchScreenSettings = new LaunchScreenSettings();
		launchScreenSettings.setDemoAvailable(false);
		launchScreenSettings.setLaunchScreenHeadline(getString(R.string.welcome_screen_headline_label));
		launchScreenSettings.setWelcomeScreenType(OnboardingType.STANDARD_ONBOARDING);
		launchScreenSettings.setLaunchScreenTitles(new String[]{getString(R.string.application_name)});
		launchScreenSettings.setLaunchScreenImages(new int[]{R.drawable.ic_android_white});
		launchScreenSettings.setLaunchScreenDescriptions(new String[]{getString(R.string.welcome_screen_detail_label)});
		launchScreenSettings.setLaunchScreenPrimaryButton(getString(R.string.welcome_screen_primary_button_label));
		launchScreenSettings.saveToIntent(welcome);
		isAwaitingResult = true;
		startActivityForResult(welcome, LAUNCH_SCREEN);
	}

	private void setPasscode() {
		ExecutorService executorService = Executors.newSingleThreadExecutor();
		executorService.submit(() -> {
			ClientPolicy clientPolicy = clientPolicyManager.getClientPolicy(true);
			PasscodePolicy passcodePolicy = clientPolicy.getPasscodePolicy();
			// isPolicyEnabled defaults to true, because the error message informing the user the
			// passcode policy couldn't be retrieved is shown on the set passcode screen.
			boolean isPolicyEnabled = passcodePolicy != null ? clientPolicy.isPasscodePolicyEnabled() : true;
			secureStoreManager.setIsPasscodePolicyEnabled(isPolicyEnabled);

			if (isPolicyEnabled) {
				Intent i = new Intent(LogonActivity.this, SetPasscodeActivity.class);
				SetPasscodeSettings setPasscodeSettings = new SetPasscodeSettings();
				setPasscodeSettings.setSkipButtonText(getString(R.string.skip_passcode));
				setPasscodeSettings.saveToIntent(i);
				isAwaitingResult = true;
				startActivityForResult(i, SET_PASSCODE);
			} else {
				openApplicationStore();
				((SAPWizardApplication)getApplication()).setIsOnboarded(true);
					startSimpleActivity();
			}
		});
		executorService.shutdown();
	}

	private void enterPasscode() {
		// if retry limit is reached, then EnterPasscode screen is opened in disabled mode, i.e. only
		// reset is possible
		int currentRetryCount = secureStoreManager.getWithPasscodePolicyStore(
				passcodePolicyStore -> passcodePolicyStore.getInt(ClientPolicyManager.KEY_RETRY_COUNT)
		);
		int retryLimit = clientPolicyManager.getClientPolicy(false).getPasscodePolicy().getRetryLimit();
		if (retryLimit <= currentRetryCount) {
			// only reset is allowed
			Intent enterPasscodeIntent = new Intent(this, EnterPasscodeActivity.class);
			EnterPasscodeSettings enterPasscodeSettings = new EnterPasscodeSettings();
			enterPasscodeSettings.setFinalDisabled(true);
			enterPasscodeSettings.saveToIntent(enterPasscodeIntent);
			isAwaitingResult = true;
			startActivityForResult(enterPasscodeIntent, ENTER_PASSCODE);
		} else {
			// client policy is refreshed now in UnlockActivity
			Intent unlockIntent = new Intent(this, UnlockActivity.class);
			isAwaitingResult = true;
			startActivityForResult(unlockIntent, ENTER_PASSCODE);
		}
	}


	private void startSimpleActivity() {
	    sapServiceManager.openODataStore(() -> {
			Intent intent = new Intent(LogonActivity.this, SimpleActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
				startActivityForResult(intent, SIMPLE_ACTIVITY);
		});
	}

	private void openApplicationStore() {
		try {
			secureStoreManager.openApplicationStore();
		} catch(EncryptionError | OpenFailureException e) {
			String errorTitle = getResources().getString(R.string.secure_store_error);
			String errorDetails = getResources().getString(R.string.secure_store_open_default_error_detail);
			ErrorMessage errorMessage = new ErrorMessage(errorTitle, errorDetails, e, false);
			errorHandler.sendErrorMessage(errorMessage);
		}
	}
}
