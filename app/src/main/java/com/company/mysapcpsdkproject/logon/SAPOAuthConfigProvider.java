package com.company.mysapcpsdkproject.logon;

import android.content.Context;

import com.sap.cloud.mobile.foundation.authentication.OAuth2Configuration;

/**
 * This class provides the OAuth configuration object for the application.
 *
 */
public class SAPOAuthConfigProvider {

    private final static String OAUTH_REDIRECT_URL = "https://oauthasservices-cgsr0s9x4f.hana.ondemand.com";
    private final static String OAUTH_CLIENT_ID = "0c0e04d1-8d29-4ce1-89a4-d2e407b617ad";
    private final static String AUTH_END_POINT = "https://oauthasservices-cgsr0s9x4f.hana.ondemand.com/oauth2/api/v1/authorize";
    private final static String TOKEN_END_POINT = "https://oauthasservices-cgsr0s9x4f.hana.ondemand.com/oauth2/api/v1/token";

    public static OAuth2Configuration getOAuthConfiguration(Context context) {

        OAuth2Configuration oAuth2Configuration = new OAuth2Configuration.Builder(context)
                .clientId(OAUTH_CLIENT_ID)
                .responseType("code")
                .authUrl(AUTH_END_POINT)
                .tokenUrl(TOKEN_END_POINT)
                .redirectUrl(OAUTH_REDIRECT_URL)
                .build();

        return oAuth2Configuration;
    }
}
