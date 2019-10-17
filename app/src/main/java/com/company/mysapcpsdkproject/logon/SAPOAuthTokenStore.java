package com.company.mysapcpsdkproject.logon;

import android.net.Uri;

import com.company.mysapcpsdkproject.app.SAPWizardApplication;

import com.sap.cloud.mobile.foundation.authentication.OAuth2Token;
import com.sap.cloud.mobile.foundation.authentication.OAuth2TokenStore;
import com.sap.cloud.mobile.foundation.securestore.SecureKeyValueStore;

/**
 * Singleton class which is used for store of OAuth tokens. The tokens are persisted simple
 * in the application's secure store.
 */
public class SAPOAuthTokenStore implements OAuth2TokenStore {

    private final SecureStoreManager secureStoreManager;

    public SAPOAuthTokenStore(SecureStoreManager secureStoreManager) {
        this.secureStoreManager = secureStoreManager;
    }

    @Override
    public void storeToken(OAuth2Token oAuth2Token, String url) {
        secureStoreManager.doWithApplicationStore(applicationStore -> {
            applicationStore.put(key(url), oAuth2Token);
        });
    }

   @Override
    public OAuth2Token getToken(String url) {
        OAuth2Token retVal = null;
        if(secureStoreManager.isApplicationStoreOpen()) {
            retVal = secureStoreManager.getWithApplicationStore(
                    applicationStore -> applicationStore.getSerializable(key(url)));
        }
        return retVal;
    }

    @Override
    public void deleteToken(String url) {
        secureStoreManager.doWithApplicationStore(applicationStore -> {
            applicationStore.remove(key(url));
        });
    }

    private String key(String url) {
        return Uri.parse(url).getHost();
    }
}
