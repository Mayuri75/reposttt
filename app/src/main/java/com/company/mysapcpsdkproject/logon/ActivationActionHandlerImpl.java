package com.company.mysapcpsdkproject.logon;

import android.app.Activity;
import android.content.Intent;
import android.support.v4.app.Fragment;

import com.sap.cloud.mobile.onboarding.activation.ActivationActionHandler;

public class ActivationActionHandlerImpl implements ActivationActionHandler {

    public static final String DISCOVERY_SVC_EMAIL = "eMailAddress";

    @Override
    public void startOnboardingWithDiscoveryServiceEmail(Fragment fragment, String userEmail) throws InterruptedException {

        Intent intent = new Intent();
        intent.putExtra(DISCOVERY_SVC_EMAIL, userEmail);
        fragment.getActivity().setResult(Activity.RESULT_OK, intent);
        fragment.getActivity().finish();
    }
}
