package com.kineticdata.bridgehub.adapter.vmware;

import com.kineticdata.bridgehub.adapter.BridgeAdapter;
import com.kineticdata.bridgehub.adapter.BridgeError;
import com.kineticdata.bridgehub.adapter.BridgeRequest;
import com.kineticdata.bridgehub.adapter.Count;
import com.kineticdata.bridgehub.adapter.Record;
import com.kineticdata.bridgehub.adapter.RecordList;
import com.kineticdata.commons.v1.config.ConfigurableProperty;
import com.kineticdata.commons.v1.config.ConfigurablePropertyMap;
import com.vmware.vapi.bindings.StubConfiguration;
import com.vmware.vapi.protocol.HttpConfiguration;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.LoggerFactory;

public class VMWareAdapter implements BridgeAdapter {
    /*----------------------------------------------------------------------------------------------
     * PROPERTIES
     *--------------------------------------------------------------------------------------------*/
    
    /** Defines the adapter display name */
    public static final String NAME = "VMWare Bridge";
    
    /** Defines the logger */
    protected static final org.slf4j.Logger logger = LoggerFactory.getLogger(VMWareAdapter.class);

    /** Adapter version constant. */
    public static String VERSION;
    /** Load the properties version from the version.properties file. */
    static {
        try {
            java.util.Properties properties = new java.util.Properties();
            properties.load(VMWareAdapter.class.getResourceAsStream("/"+VMWareAdapter.class.getName()+".version"));
            VERSION = properties.getProperty("version");
        } catch (IOException e) {
            logger.warn("Unable to load " + VMWareAdapter.class.getName() +
                " version properties.", e);
            VERSION = "Unknown";
        }
    }
    
    /** Defines the collection of property names for the adapter */
    public static class Properties {
        public static final String USERNAME = "Username";
        public static final String PASSWORD = "Password";
        public static final String SERVER_URL = "VMWare Server URL";
    }
    
    private final ConfigurablePropertyMap properties = new ConfigurablePropertyMap(
        new ConfigurableProperty(Properties.USERNAME).setIsRequired(true),
        new ConfigurableProperty(Properties.PASSWORD).setIsRequired(true).setIsSensitive(true),
        new ConfigurableProperty(Properties.SERVER_URL).setIsRequired(true)
    );
    
    private String username;
    private String password;
    private String url;
    private VapiAuthenticationHelper vapiAuthHelper;
    private StubConfiguration sessionStubConfig;
    private VCenterVM vCenterVM;
    private VCenterNetwork vCenterNetwork;
    private VCenterFolder vCenterFolder;
    private VCenterDatastore vCenterDatastore;
    private VCenterResourcePool vCenterResourcePool;
    private VCenterCluster vCenterCluster;
    private ContentLibrary contentLibrary;
    private VMWareQualificationParser parser;
    
    /*---------------------------------------------------------------------------------------------
     * SETUP METHODS
     *-------------------------------------------------------------------------------------------*/
    
    @Override
    public void initialize() throws BridgeError {
        this.username = properties.getValue(Properties.USERNAME);
        this.password = properties.getValue(Properties.PASSWORD);
        this.url = properties.getValue(Properties.SERVER_URL);
        
        // Parser.parse will parse the query and exchange out any parameters 
        // with their parameter values.
        // ie. change the query username=<%=parameter["Username"]%> to
        // username=test.user where parameter["Username"]=test.user
        parser = new VMWareQualificationParser();
        
        this.login();
    }

    @Override
    public String getName() {
        return NAME;
    }
    
    @Override
    public String getVersion() {
        return VERSION;
    }
    
    @Override
    public void setProperties(Map<String,String> parameters) {
        properties.setValues(parameters);
    }
    
    @Override
    public ConfigurablePropertyMap getProperties() {
        return properties;
    }
    
    /**
     * Structures that are valid to use in the bridge
     */
    public static final List<String> VALID_STRUCTURES = Arrays.asList(new String[] {
        "/vcenter/vm", "/vcenter/resource-pool", "/vcenter/network", "/vcenter/folder",
        "/vcenter/datastore", "/vcenter/cluster", "/com/vmware/content/library"
    });
    
    /*---------------------------------------------------------------------------------------------
     * IMPLEMENTATION METHODS
     *-------------------------------------------------------------------------------------------*/

    public Count countWrapper(BridgeRequest request) throws BridgeError {
        if (!VALID_STRUCTURES.contains(request.getStructure())) {
            throw new BridgeError("Invalid Structure: " +
                request.getStructure() + " is not a valid structure");
        }    
        
        request.setQuery(
            parser.parse(request.getQuery(),request.getParameters())
        );
        
        Count count;
        if (request.getStructure().equals("/vcenter/vm")) {
            count = this.vCenterVM.count(request);
        } else if (request.getStructure().equals("/vcenter/network")) {
            count = this.vCenterNetwork.count(request);
        } else if (request.getStructure().equals("/vcenter/folder")) {
            count = this.vCenterFolder.count(request);
        } else if (request.getStructure().equals("/vcenter/datastore")) {
            count = this.vCenterDatastore.count(request);
        } else if (request.getStructure().equals("/vcenter/resource-pool")) {
            count = this.vCenterResourcePool.count(request);
        } else if (request.getStructure().equals("/vcenter/cluster")) {
            count = this.vCenterCluster.count(request);
        } else if (request.getStructure().equals("/com/vmware/content/library")) {
            count = this.contentLibrary.count(request);
        } else {
            throw new BridgeError("The structure '" + request.getStructure() +
                "' does not have a count method defined");
        }
        
        //Return the response
        return count;
    }
    
    @Override
    public Count count(BridgeRequest request) throws BridgeError {
        Count count = null;
        
        try {
            count = this.countWrapper(request);
        } catch (BridgeError e) {
            this.login();
            count = this.countWrapper(request);
        }
        
        return count;
    }

    public Record retrieveWrapper(BridgeRequest request) throws BridgeError {
        if (!VALID_STRUCTURES.contains(request.getStructure())) {
            throw new BridgeError("Invalid Structure: " + request.getStructure() +
                " is not a valid structure");
        }    
        
        request.setQuery(
            parser.parse(request.getQuery(),request.getParameters())
        );
        
        Record record;
        if (request.getStructure().equals("/vcenter/vm")) {
            record = this.vCenterVM.retrieve(request);
        } else if (request.getStructure().equals("/vcenter/network")) { 
            record = this.vCenterNetwork.retrieve(request);
        } else if (request.getStructure().equals("/vcenter/folder")) {
            record = this.vCenterFolder.retrieve(request);
        } else if (request.getStructure().equals("/vcenter/datastore")) {
            record = this.vCenterDatastore.retrieve(request);
        } else if (request.getStructure().equals("/vcenter/resource-pool")) { 
            record = this.vCenterResourcePool.retrieve(request);
        } else if (request.getStructure().equals("/vcenter/cluster")) {
            record = this.vCenterCluster.retrieve(request);
        } else if (request.getStructure().equals("/com/vmware/content/library")) {
            record = this.contentLibrary.retrieve(request);
        } else {
            throw new BridgeError("The structure '" + request.getStructure() +
                "' does not have a retrieve method defined");
        }
                
        // Returning the response
        return record;
    }

    @Override
    public Record retrieve(BridgeRequest request) throws BridgeError {
        Record record = null;
        
        try {
            record = this.retrieveWrapper(request);
        } catch (Exception e) {
            this.login();
            record = this.retrieveWrapper(request);
        }
        
        return record;
    }
    
    public RecordList searchWrapper(BridgeRequest request) throws BridgeError {
        // Validate the request
        if (!VALID_STRUCTURES.contains(request.getStructure())) {
            throw new BridgeError("Invalid Structure: " + request.getStructure() + " is not a valid structure");
        }
        
        request.setQuery(
            parser.parse(request.getQuery(),request.getParameters())
        );
        
        RecordList recordList;
        if (request.getStructure().equals("/vcenter/vm")) {
            recordList = this.vCenterVM.search(request);
        } else if (request.getStructure().equals("/vcenter/network")) { 
            recordList = this.vCenterNetwork.search(request);
        }  else if (request.getStructure().equals("/vcenter/folder")) {
            recordList = this.vCenterFolder.search(request);
        }  else if (request.getStructure().equals("/vcenter/datastore")) {
            recordList = this.vCenterDatastore.search(request);
        }  else if (request.getStructure().equals("/vcenter/resource-pool")) { 
            recordList = this.vCenterResourcePool.search(request);
        }  else if (request.getStructure().equals("/vcenter/cluster")) {
            recordList = this.vCenterCluster.search(request);
        }  else if (request.getStructure().equals("/com/vmware/content/library")) {
            recordList = this.contentLibrary.search(request);
        }  else {
            throw new BridgeError("The structure '" + request.getStructure() +
                "' does not have a search method defined");
        }
        
        // Returning the response
        return recordList;
    }

    @Override
    public RecordList search(BridgeRequest request) throws BridgeError {
        RecordList recordList = null;
        
        try {
            recordList = this.searchWrapper(request);
        } catch (BridgeError e) {
            this.login();
            recordList = this.searchWrapper(request);
        }
        
        return recordList;
    }
    
    /*----------------------------------------------------------------------------------------------
     * PRIVATE HELPER METHODS
     *--------------------------------------------------------------------------------------------*/

    /**
     * Builds the Http settings to be applied for the connection to the server.
     * @return http configuration
     * @throws Exception 
     */
    protected HttpConfiguration buildHttpConfiguration() throws Exception {
        HttpConfiguration httpConfig =
            new HttpConfiguration.Builder()
            .setSslConfiguration(buildSslConfiguration())
            .getConfig();
        
        return httpConfig;
    }
    
    /**
     * Creates a session with the server using username/password.
     *
     *<p><b>
     * Note: If the "skip-server-verification" option is specified, then this
     * method trusts the SSL certificate from the server and doesn't verify
     * it. Circumventing SSL trust in this manner is unsafe and should not be
     * used with production code. This is ONLY FOR THE PURPOSE OF DEVELOPMENT
     * ENVIRONMENT
     * <b></p>
     * @throws Exception
     */
    protected void login()
        throws BridgeError {
        try {
            this.vapiAuthHelper = new VapiAuthenticationHelper();
            HttpConfiguration httpConfig = buildHttpConfiguration();
            this.sessionStubConfig =
                    vapiAuthHelper.loginByUsernameAndPassword(
                        this.url, this.username, this.password, httpConfig);
            this.setSessions();
        }
        catch(Exception exc) {
            throw new BridgeError(exc);
        }
    }
    
    /**
     * Builds the SSL configuration to be applied for the connection to the
     * server
     * 
     *<p><b>
     * Note: Below code circumvents SSL trust if "skip-server-verification" is
     * specified. Circumventing SSL trust is unsafe and should not be used
     * in production software. It is ONLY FOR THE PURPOSE OF DEVELOPMENT
     * ENVIRONMENTS.
     *<b></p>
     * @return SSL configuration
     * @throws Exception
     */
    protected HttpConfiguration.SslConfiguration buildSslConfiguration() throws Exception {
        HttpConfiguration.SslConfiguration sslConfig;
            sslConfig =
                new HttpConfiguration.SslConfiguration.Builder()
                .getConfig();
        return sslConfig;
    }
    
    private void setSessions() {    
        this.vCenterVM = new VCenterVM(this.vapiAuthHelper, this.sessionStubConfig);
        this.vCenterNetwork = new VCenterNetwork(this.vapiAuthHelper, this.sessionStubConfig);
        this.vCenterFolder = new VCenterFolder(this.vapiAuthHelper, this.sessionStubConfig);
        this.vCenterDatastore = new VCenterDatastore(this.vapiAuthHelper, this.sessionStubConfig);
        this.vCenterResourcePool = new VCenterResourcePool(this.vapiAuthHelper, this.sessionStubConfig);
        this.vCenterCluster = new VCenterCluster(this.vapiAuthHelper, this.sessionStubConfig);
        this.contentLibrary = new ContentLibrary(this.vapiAuthHelper, this.sessionStubConfig);
    }
}
