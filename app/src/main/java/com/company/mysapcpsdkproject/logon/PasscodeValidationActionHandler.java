package com.company.mysapcpsdkproject.logon;

import android.support.v4.app.Fragment;

import com.sap.cloud.mobile.onboarding.passcode.PasscodeValidationException;

public class PasscodeValidationActionHandler implements com.sap.cloud.mobile.onboarding.passcode.PasscodeValidationActionHandler {

    @Override
    public void validate(Fragment fragment, char[] chars) throws PasscodeValidationException, InterruptedException {

     // You can extend the validator with your own policy.
    }
}
