package com.company.mysapcpsdkproject.service;

import com.sap.cloud.android.odata.espmcontainer2.ESPMContainer2;
import com.sap.cloud.android.odata.espmcontainer.ESPMContainer;
import com.company.mysapcpsdkproject.app.ConfigurationData;
import com.sap.cloud.mobile.foundation.common.ClientProvider;
import com.sap.cloud.mobile.odata.OnlineODataProvider;
import com.sap.cloud.mobile.odata.core.Action0;
import com.sap.cloud.mobile.odata.http.OKHttpHandler;

public class SAPServiceManager {

    private final ConfigurationData configurationData;
    private OnlineODataProvider provider;
    private String serviceRoot;
    ESPMContainer2 eSPMContainer2;
    ESPMContainer eSPMContainer;
    public static final String CONNECTION_ID_ESPMCONTAINER2 = "com.sap.edm.sampleservice";
    public static final String CONNECTION_ID_ESPMCONTAINER = "PODServices";

    public SAPServiceManager(ConfigurationData configurationData) {
        this.configurationData = configurationData;
    }

    public void openODataStore(Action0 callback) {
        if (configurationData.loadData()) {
            String serviceUrl = configurationData.getServiceUrl();
            provider = new OnlineODataProvider("SAPService", serviceUrl + CONNECTION_ID_ESPMCONTAINER2);
            provider.getNetworkOptions().setHttpHandler(new OKHttpHandler(ClientProvider.get()));
            provider.getServiceOptions().setCheckVersion(false);
            provider.getServiceOptions().setRequiresType(true);
            provider.getServiceOptions().setCacheMetadata(false);
            eSPMContainer2 = new ESPMContainer2(provider);

            provider = new OnlineODataProvider("SAPService", serviceUrl + CONNECTION_ID_ESPMCONTAINER);
            provider.getNetworkOptions().setHttpHandler(new OKHttpHandler(ClientProvider.get()));
            provider.getServiceOptions().setCheckVersion(false);
            provider.getServiceOptions().setRequiresType(true);
            provider.getServiceOptions().setCacheMetadata(false);
            eSPMContainer = new ESPMContainer(provider);

        }
        callback.call();
    }

    public ESPMContainer2 getESPMContainer2() {
        if (eSPMContainer2 == null) {
            throw new IllegalStateException("SAPServiceManager was not initialized");
        }
        return eSPMContainer2;
    }

    public ESPMContainer getESPMContainer() {
        if (eSPMContainer == null) {
            throw new IllegalStateException("SAPServiceManager was not initialized");
        }
        return eSPMContainer;
    }

}