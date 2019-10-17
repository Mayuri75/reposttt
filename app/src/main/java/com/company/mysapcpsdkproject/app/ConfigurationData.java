package com.company.mysapcpsdkproject.app;

import android.content.Context;
import android.content.res.Resources;
import android.util.Patterns;

import com.company.mysapcpsdkproject.R;
import com.sap.cloud.mobile.foundation.configurationprovider.ConfigurationPersistenceException;
import com.sap.cloud.mobile.foundation.configurationprovider.DefaultPersistenceMethod;
import com.sap.cloud.mobile.foundation.configurationprovider.ConfigurationLoader;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Central unsecured configuration data class, which contains all of the entries provided by the
 * {@link ConfigurationLoader}. It is loaded once and then accessed by the modules that need it's data.
 */
public class ConfigurationData {

    // Define the Keys for the JSON Configuration Data
    private static final String KEY_SERVICE_URL = "ServiceUrl";
    private static final String SERVICE_URL_FORMAT = "%s://%s:%s/";
    private static final String KEY_DISC_SVC_DFLT_PROTOCOL = "protocol";
    private static final String KEY_DISC_SVC_DFLT_HOST = "host";
    private static final String KEY_DISC_SVC_DFLT_PORT = "port";

    private final Context applicationContext;
    private final ErrorHandler errorHandler;

    public ConfigurationData(Context applicationContext, ErrorHandler errorHandler) {
        this.applicationContext = applicationContext;
        this.errorHandler = errorHandler;
        resetData();
    }

    /**
     * Resets the underlying persisted data
     *
     * @param context reference to the application context
     */
    public static void resetPersistedConfiguration(Context context) {
        DefaultPersistenceMethod.resetPersistedConfiguration(context);
    }

    // private data members
    private boolean configurationIsValid;
    private String serviceUrl;

    // public accessor methods

    /**
     * Returns a boolean value representing whether or not the configuration has been successfully loaded.
     *
     * @return true if configuration has been successfully loaded, false if not.
     */
    public boolean isLoaded() {
        return configurationIsValid;
    }

    /***
     * Returns the Service URL
     *
     * @return a String representing the Service URL
     */
    public String getServiceUrl() {
        return serviceUrl;
    }

    /***
     * Resets all of the configuration fields to the unloaded state.
     */
    public void resetData() {
        configurationIsValid = false;
        serviceUrl = null;
    }

    /***
     * Loads the cofiguration data from the persistent store. The data was deposited by the
     * {@link ConfigurationLoader}.
     *
     * @return a boolean value representing whether or not the data was successfully loaded.
     */
    public boolean loadData() {
        Resources resources = applicationContext.getResources();
        final String errorMsgTitle = resources.getString(R.string.config_data_error_title);
        boolean success = false;

        try {
            JSONObject configData = DefaultPersistenceMethod.getPersistedConfiguration(applicationContext);

             if (configData.length() == 0) {
                 errorHandler.sendErrorMessage(new ErrorMessage(
                        errorMsgTitle,
                        resources.getString(R.string.config_data_no_data_description)
                ));
            } else {
                 // Get all configuration values
                if (configData.has(KEY_SERVICE_URL)) {
                    serviceUrl = configData.getString(KEY_SERVICE_URL);
                } else {
                    // Build the service url from the default discovery service data
                    serviceUrl = String.format(SERVICE_URL_FORMAT,
                            configData.getString(KEY_DISC_SVC_DFLT_PROTOCOL),
                            configData.getString(KEY_DISC_SVC_DFLT_HOST),
                            configData.getString(KEY_DISC_SVC_DFLT_PORT));
                }

                success = true;     // If we got here we have all of them

                // Validate the ones that we can validate
                if (!Patterns.WEB_URL.matcher(serviceUrl).matches()) {
                    errorHandler.sendErrorMessage(new ErrorMessage(
                            errorMsgTitle,
                            String.format(resources.getString(
                                    R.string.config_data_bad_service_url_description),
                                    serviceUrl)
                    ));
                    serviceUrl = null;
                    success = false;
                }
            }
        } catch (ConfigurationPersistenceException e) {
            errorHandler.sendErrorMessage(new ErrorMessage(
                    errorMsgTitle,
                    resources.getString(R.string.config_data_build_json_description),
                    e,
                    false
            ));
        } catch (JSONException e) {
            errorHandler.sendErrorMessage(new ErrorMessage(
                    errorMsgTitle,
                    resources.getString(R.string.config_data_bad_field_description),
                    e,
                    false
            ));
        }
        configurationIsValid = success;
        return configurationIsValid;
    }
}
