package com.company.mysapcpsdkproject.test.core.factory;

import android.os.Build;

import com.company.mysapcpsdkproject.logon.ClientPolicy;
import com.company.mysapcpsdkproject.test.core.Credentials;
import com.company.mysapcpsdkproject.test.core.Utils;
import com.company.mysapcpsdkproject.test.pages.PasscodePage;


public class PasscodePageFactory {

    private static final int WAIT_TIMEOUT = 5000;

    public static void PasscodeFlow() {

        // Get the current clientpolicy
        ClientPolicy clientPolicy = Utils.getClientPolicyManager().getClientPolicy(true);
        // If there is a passcode policy
        if (clientPolicy.isPasscodePolicyEnabled()) {
            // Actions on the passcode Page
            PasscodePage.CreatePasscodePage createPasscodePage = new PasscodePage().new CreatePasscodePage();
            createPasscodePage.createPasscode(Credentials.PASSCODE);
            createPasscodePage.clickNext();
            createPasscodePage.leavePage();

            // Actions on the verifypasscode Page
            PasscodePage.VerifyPasscodePage verifyPasscodePage = new PasscodePage().new VerifyPasscodePage();
            verifyPasscodePage.verifyPasscode(Credentials.PASSCODE);
            verifyPasscodePage.clickNext();
            verifyPasscodePage.leavePage();

            // Skip Fingerprint
            Utils.skipFingerprint();


        } else {
            // we skip the passcode flow
        }

    }

    public static void PasscodeFlowBack() {

        // Get the current clientpolicy
        ClientPolicy clientPolicy = Utils.getClientPolicyManager().getClientPolicy(true);
        // If there is a passcode policy
        if (clientPolicy.isPasscodePolicyEnabled()) {
            // Actions on the passcode Page
            PasscodePage.CreatePasscodePage createPasscodePage = new PasscodePage().new CreatePasscodePage();
            createPasscodePage.createPasscode(Credentials.PASSCODE);
            createPasscodePage.clickNext();
            createPasscodePage.leavePage();

            // Actions on the verifypasscode Page
            PasscodePage.VerifyPasscodePage verifyPasscodePage = new PasscodePage().new VerifyPasscodePage();
            verifyPasscodePage.verifyPasscode(Credentials.PASSCODE);
            verifyPasscodePage.clickBack();
            verifyPasscodePage.leavePage();
            createPasscodePage.createPasscode(Credentials.PASSCODE);
            createPasscodePage.clickNext();
            createPasscodePage.leavePage();
            verifyPasscodePage.verifyPasscode(Credentials.PASSCODE);
            verifyPasscodePage.clickNext();
            verifyPasscodePage.leavePage();

            // Skip Fingerprint
            Utils.skipFingerprint();


        } else {
            // we skip the passcode flow
        }

    }

    public static void NewPasscodeFlow() {

        // Get the current clientpolicy
        Utils.getClientPolicyManager().getClientPolicy(true);

        PasscodePage.CreatePasscodePage createPasscodePage = new PasscodePage().new CreatePasscodePage();
        createPasscodePage.createPasscode(Credentials.NEWPASSCODE);
        createPasscodePage.clickSignIn();
        createPasscodePage.leavePage();

        PasscodePage.VerifyPasscodePage verifyPasscodePage = new PasscodePage().new VerifyPasscodePage();
        verifyPasscodePage.verifyPasscode(Credentials.NEWPASSCODE);
        verifyPasscodePage.clickSignIn();
        verifyPasscodePage.leavePage();
    }

    public static void NewPasscodeFlowBack() {

        // Get the current clientpolicy
        Utils.getClientPolicyManager().getClientPolicy(true);

        PasscodePage.CreatePasscodePage createPasscodePage = new PasscodePage().new CreatePasscodePage();
        createPasscodePage.createPasscode(Credentials.NEWPASSCODE);
        createPasscodePage.clickSignIn();
        createPasscodePage.leavePage();

        PasscodePage.VerifyPasscodePage verifyPasscodePage = new PasscodePage().new VerifyPasscodePage();
        verifyPasscodePage.verifyPasscode(Credentials.NEWPASSCODE);
        verifyPasscodePage.clickBack();
        verifyPasscodePage.leavePage();

        createPasscodePage.createPasscode(Credentials.NEWPASSCODE);
        createPasscodePage.clickSignIn();
        createPasscodePage.leavePage();

        verifyPasscodePage.verifyPasscode(Credentials.NEWPASSCODE);
        verifyPasscodePage.clickSignIn();
        verifyPasscodePage.leavePage();
    }

}
