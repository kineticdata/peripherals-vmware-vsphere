package com.kineticdata.bridgehub.adapter.vmware;

import com.kineticdata.bridgehub.adapter.BridgeAdapter;
import com.kineticdata.bridgehub.adapter.BridgeError;
import com.kineticdata.bridgehub.adapter.BridgeRequest;
import com.kineticdata.bridgehub.adapter.BridgeUtils;
import com.kineticdata.bridgehub.adapter.Count;
import com.kineticdata.bridgehub.adapter.Record;
import com.kineticdata.bridgehub.adapter.RecordList;
import com.kineticdata.commons.v1.config.ConfigurableProperty;
import com.vmware.vim25.InvalidProperty;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.VirtualMachine;
import com.kineticdata.commons.v1.config.ConfigurablePropertyMap;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.time.DateUtils;
import org.json.simple.JSONObject;
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
            logger.warn("Unable to load "+VMWareAdapter.class.getName()+" version properties.", e);
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
    ServiceInstance instance;
    
    /*---------------------------------------------------------------------------------------------
     * SETUP METHODS
     *-------------------------------------------------------------------------------------------*/
    
    @Override
    public void initialize() throws BridgeError {
        this.username = properties.getValue(Properties.USERNAME);
        this.password = properties.getValue(Properties.PASSWORD);
        this.url = properties.getValue(Properties.SERVER_URL);
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
        "VirtualMachine"
    });
    
    /**
     * Currently supported Managed Reference Object Types
     */
    public static final List<String> VALID_MOR_TYPES = Arrays.asList(new String[] {
        "runtime.host"
    });
    
    /*---------------------------------------------------------------------------------------------
     * IMPLEMENTATION METHODS
     *-------------------------------------------------------------------------------------------*/

    @Override
    public Count count(BridgeRequest request) throws BridgeError {
        if (!VALID_STRUCTURES.contains(request.getStructure())) {
            throw new BridgeError("Invalid Structure: " + request.getStructure() + " is not a valid structure");
        }        
        
        try {
            this.instance = new ServiceInstance(new URL(this.url),this.username,this.password,true);
        } catch (RemoteException ex) {
            throw new BridgeError("Invalid URL: " + ex.getMessage());
        } catch (MalformedURLException ex) {
            throw new BridgeError("Invalid URL: " + ex.getMessage());
        }
        
        Folder rootFolder = instance.getRootFolder();
        InventoryNavigator inventory = new InventoryNavigator(rootFolder);
        List<String> fields = request.getFields();
        
        ManagedEntity[] virtualMachines;
        try {
            virtualMachines = inventory.searchManagedEntities("VirtualMachine");
        } catch (Exception e) {
            throw new BridgeError("Unable to retrieve the Virtual Machines from the server.", e);
        }
        
        // This figures out whether the query uses AND or OR operators
        String queryDelimiter = "";
        if (request.getQuery().contains("\"AND\"") && request.getQuery().contains("\"OR\"")) {
            throw new BridgeError("Invalid Query: Cannot have both AND and OR operators in the same bridge query.");
        }
        else if (request.getQuery().contains("\"AND\"")) {
            queryDelimiter = "AND";
        } else if (request.getQuery().contains("\"OR\"")) {
            queryDelimiter = "OR";
        }

        ArrayList<ArrayList> queryArray = buildQueryMap(request, queryDelimiter);
        TreeSet<String> retrieveFields = new TreeSet<String>();
        for (ArrayList queryEntry : queryArray) {
            retrieveFields.add(queryEntry.get(0).toString());
        }
        if (request.getFields() != null) {
            retrieveFields.addAll(request.getFields());
        }
               
       // Finding if the VMWare call will contain Managed Object Reference Types
        List<String> intersectingFields = new ArrayList(retrieveFields);
        intersectingFields.retainAll(VALID_MOR_TYPES);
        
        ArrayList<JSONObject> vmList = new ArrayList();

        for (int i=0; i < virtualMachines.length; i++) {
            VirtualMachine vm = (VirtualMachine)virtualMachines[i];
            Map vmDetails;
            try {
                vmDetails = vm.getPropertiesByPaths(retrieveFields.toArray(new String[retrieveFields.size()]));
            } catch (InvalidProperty ip) {
                throw new BridgeError("The following field could not be found : " + ip.getName());
            } catch (Exception e) {
                throw new BridgeError("Unable to parse virtual machine data", e);
            }
            
            if (!intersectingFields.isEmpty()) {
               vmDetails = convertMorFields(intersectingFields, vmDetails); 
            }
            
            Boolean meetsQualification = checkVmDetails(queryArray, vmDetails, queryDelimiter);
            
            if (meetsQualification == true) {
                JSONObject jsonDetails = new JSONObject(vmDetails);
                vmList.add(jsonDetails);
            }
        }
        
        Long count;
        count = Long.valueOf(vmList.size());

        //Return the response
        return new Count(count);
    }

    @Override
    public Record retrieve(BridgeRequest request) throws BridgeError {
        // Initialize the result data and response variables
        Map<String,Object> data = new LinkedHashMap();

        // Validate the request
        if (!VALID_STRUCTURES.contains(request.getStructure())) {
            throw new BridgeError("Invalid Structure: " + request.getStructure() + " is not a valid structure");
        }
        
        try {
            this.instance = new ServiceInstance(new URL(this.url),this.username,this.password,true);
        } catch (RemoteException ex) {
            throw new BridgeError("Invalid URL: " + ex.getMessage());
        } catch (MalformedURLException ex) {
            throw new BridgeError("Invalid URL: " + ex.getMessage());
        }
        
        Folder rootFolder = instance.getRootFolder();
        InventoryNavigator inventory = new InventoryNavigator(rootFolder);
        List<String> fields = request.getFields();
        
        ManagedEntity[] virtualMachines;
        try {
            virtualMachines = inventory.searchManagedEntities("VirtualMachine");
        } catch (Exception e) {
            throw new BridgeError("Unable to retrieve the Virtual Machines from the server.", e);
        }
        
        // This figures out whether the query uses AND or OR operators
        String queryDelimiter = "";
        if (request.getQuery().contains("\"AND\"") && request.getQuery().contains("\"OR\"")) {
            throw new BridgeError("Invalid Query: Cannot have both AND and OR operators in the same bridge query.");
        }
        else if (request.getQuery().contains("\"AND\"")) {
            queryDelimiter = "AND";
        } else if (request.getQuery().contains("\"OR\"")) {
            queryDelimiter = "OR";
        }

        ArrayList<ArrayList> queryArray = buildQueryMap(request, queryDelimiter);
        TreeSet<String> retrieveFields = new TreeSet<String>();
        for (ArrayList queryEntry : queryArray) {
            retrieveFields.add(queryEntry.get(0).toString());
        }
        if (request.getFields() != null) {
            retrieveFields.addAll(request.getFields());
        }
               
       // Finding if the VMWare call will contain Managed Object Reference Types
        List<String> intersectingFields = new ArrayList(retrieveFields);
        intersectingFields.retainAll(VALID_MOR_TYPES);
        
        ArrayList<JSONObject> vmList = new ArrayList();

        for (int i=0; i < virtualMachines.length; i++) {
            VirtualMachine vm = (VirtualMachine)virtualMachines[i];
            Map vmDetails;
            try {
                vmDetails = vm.getPropertiesByPaths(retrieveFields.toArray(new String[retrieveFields.size()]));
            } catch (InvalidProperty ip) {
                throw new BridgeError("The following field could not be found : " + ip.getName());
            } catch (Exception e) {
                throw new BridgeError("Unable to parse virtual machine data", e);
            }
            
            if (!intersectingFields.isEmpty()) {
               vmDetails = convertMorFields(intersectingFields, vmDetails); 
            }
            
            Boolean meetsQualification = checkVmDetails(queryArray, vmDetails, queryDelimiter);
            
            if (meetsQualification == true) {
                JSONObject jsonDetails = new JSONObject(vmDetails);
                vmList.add(jsonDetails);
            }
        }
        
        Record record = new Record(null);
        
        if (vmList.size() > 1) {
            throw new BridgeError("Multiple results matched an expected single match query");
        } else if (vmList.isEmpty()) {
            record = new Record(null);
        } else {
            JSONObject result = (JSONObject)vmList.get(0);
            Map<String,Object> recordMap = new LinkedHashMap<String,Object>();
            if (fields == null) { 
                fields = new ArrayList(result.entrySet());
            } else {
                for (String field : fields) {
                    recordMap.put(field, result.get(field));
                }
            }
            record = new Record(recordMap);
        }
        
//        JSONObject results = vmList.get(0);
//        
//        for (int i=0; i < vmList.size(); i++) {
//            JSONObject recordObject = (JSONObject)vmList.get(i);
//            results.add(new Record((Map<String,Object>)recordObject));
//        }
        
        // Returning the response
        return record;
    }

    @Override
    public RecordList search(BridgeRequest request) throws BridgeError {
        // Validate the request
        if (!VALID_STRUCTURES.contains(request.getStructure())) {
            throw new BridgeError("Invalid Structure: " + request.getStructure() + " is not a valid structure");
        }
        
        try {
            this.instance = new ServiceInstance(new URL(this.url),this.username,this.password,true);
        } catch (RemoteException ex) {
            throw new BridgeError("Invalid URL: " + ex.getMessage());
        } catch (MalformedURLException ex) {
            throw new BridgeError("Invalid URL: " + ex.getMessage());
        }

        Folder rootFolder = instance.getRootFolder();
        InventoryNavigator inventory = new InventoryNavigator(rootFolder);
        List<String> fields = request.getFields();
        
        ManagedEntity[] virtualMachines;
        try {
            virtualMachines = inventory.searchManagedEntities("VirtualMachine");
        } catch (Exception e) {
            throw new BridgeError("Unable to retrieve the Virtual Machines from the server.", e);
        }
        
        // This figures out whether the query uses AND or OR operators
        String queryDelimiter = "";
        if (request.getQuery().contains("\"AND\"") && request.getQuery().contains("\"OR\"")) {
            throw new BridgeError("Invalid Query: Cannot have both AND and OR operators in the same bridge query.");
        }
        else if (request.getQuery().contains("\"AND\"")) {
            queryDelimiter = "AND";
        } else if (request.getQuery().contains("\"OR\"")) {
            queryDelimiter = "OR";
        }

        ArrayList<ArrayList> queryArray = buildQueryMap(request, queryDelimiter);
        TreeSet<String> retrieveFields = new TreeSet<String>();
        for (ArrayList queryEntry : queryArray) {
            retrieveFields.add(queryEntry.get(0).toString());
        }
        if (request.getFields() != null) {
            retrieveFields.addAll(request.getFields());
        }
               
       // Finding if the VMWare call will contain Managed Object Reference Types
        List<String> intersectingFields = new ArrayList(retrieveFields);
        intersectingFields.retainAll(VALID_MOR_TYPES);
        
        ArrayList<JSONObject> vmList = new ArrayList();

        for (int i=0; i < virtualMachines.length; i++) {
            VirtualMachine vm = (VirtualMachine)virtualMachines[i];
            Map vmDetails;
            try {
                vmDetails = vm.getPropertiesByPaths(retrieveFields.toArray(new String[retrieveFields.size()]));
            } catch (InvalidProperty ip) {
                throw new BridgeError("The following field could not be found : " + ip.getName());
            } catch (Exception e) {
                throw new BridgeError("Unable to parse virtual machine data", e);
            }
            
            if (!intersectingFields.isEmpty()) {
               vmDetails = convertMorFields(intersectingFields, vmDetails); 
            }
            
            Boolean meetsQualification = checkVmDetails(queryArray, vmDetails, queryDelimiter);
            
            if (meetsQualification == true) {
                JSONObject jsonDetails = new JSONObject(vmDetails);
                vmList.add(jsonDetails);
            }
        }
        
        ArrayList<Record> records = new ArrayList<Record>();
        
        for (int i=0; i < vmList.size(); i++) {
            JSONObject recordObject = (JSONObject)vmList.get(i);
            records.add(new Record((Map<String,Object>)recordObject));
        }
        
        Collections.sort(records, new RecordComparator(fields)); 

        // Building the output metadata
        Map<String,String> metadata = BridgeUtils.normalizePaginationMetadata(request.getMetadata());
        metadata.put("pageSize", "0");
        metadata.put("pageNumber", "1");
        metadata.put("offset", "0");
        metadata.put("size", String.valueOf(records.size()));
        metadata.put("count", metadata.get("size"));
        
        // Returning the response
        return new RecordList(fields, records, metadata);
    }
    
    /*----------------------------------------------------------------------------------------------
     * PRIVATE HELPER METHODS
     *--------------------------------------------------------------------------------------------*/


    /** 
     * A helper method used to build the query map that will be used to compare
     * the returned VM values against the inputted query. 
     *
     * @param request
     * @param soapTag
     * @return
     * @throws BridgeError
     */
    protected ArrayList<ArrayList> buildQueryMap(BridgeRequest request, String queryDelimiter) throws BridgeError {
        ArrayList<ArrayList> queryArray = new ArrayList<ArrayList>();

        // Parsing the query to include parameters if they have been used. 
        VMWareQualificationParser parser = new VMWareQualificationParser();
        String query = parser.parse(request.getQuery(),request.getParameters());
        
        String queryCopy = new String(query);
        String operatorString = queryCopy.replaceAll("([\"\"])(?:(?=(\\\\?))\\2.)*?\\1","");
        logger.debug(operatorString);
        String[] operatorOrder;
        if (queryDelimiter.equals("")) {
            operatorOrder = new String[] {operatorString};
        } else {
            operatorOrder = operatorString.split(queryDelimiter);
        }
        Integer operatorNumber = operatorOrder.length;
                
        // This pattern is used to match the quotes around each of the names and
        // values in the query string. If there are escaped double quotes inside
        // the outside quotes, the regex ignores them.
        Pattern pattern = Pattern.compile("([\"\"])(?:(?=(\\\\?))\\2.)*?\\1");
        Matcher matcher = pattern.matcher(query);

        // Counting the amount of = signs in the query to better test if the
        // query is valid. Keeps the following scenario from happening.
        // ie. query = Name1=\"Value1\"&Name2=\"Value2\" and this is seen as
        // correct (Value1 => Value2) without this check.
        Pattern countPattern = Pattern.compile("(<=|>=|<>|!=|>|<|=)(?=(?:[^\"]|\"[^\"]*\")*$)");
        // This catches all the operators. The complex operators needs to go first
        // so that a >= does not get interpreted as > AND =
        Matcher countMatcher = countPattern.matcher(query);
        Integer countEquals = 0;
        while (countMatcher.find()) {
            countEquals = countEquals + 1;
        }

        try  {
            // While there is still a name contained within double quotes
            while (matcher.find()) {
                String key = matcher.group();
                // used to replace the outside quotes from the key value
                key = key.replaceAll("(AND|OR)\"|\"$|^\"", "");
                if (!key.equals("*")) {
                    // While there is still a value contained within double quotes
                    matcher.find();
                    String value = matcher.group();
                    // used to replace the outside quotes from the value
                    value = value.replaceAll("(AND|OR)\"|\"$|^\"", "");
                    // Adding the name value pair to the query string in SOAP 
                    // format (ie \"Name\"=\"Value\" => <Name>Value</Name>).
                    if (!value.equals("*")) {
                        // If the value is a date, change it into a gregorian
                        // date object so that it is easier to compare
                        // Converts the date value from a string to a Gregorian Calendar
                        String[] date = null;
                        if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                            date = value.split("-");
                        } else if (value.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")) {
                            date = value.split("T")[0].split("-");
                        }

                        ArrayList valueArray = new ArrayList();
                        valueArray.add(key);
                        valueArray.add(operatorOrder[operatorNumber-countEquals]);
                        // queryArray contains query info in the order of 
                        // [key,operator,value]
                        if (date != null) {
                            GregorianCalendar valueCal = new GregorianCalendar(Integer.valueOf(date[0]),Integer.valueOf(date[1])-1,Integer.valueOf(date[2]));
                            valueArray.add(valueCal);
                        } else {
                            valueArray.add(value);
                        }
                        queryArray.add(valueArray);
                    }
                    countEquals = countEquals - 1;
                }
            }
        }
        catch (IllegalStateException e) {
            throw new BridgeError("Error parsing the query string.", e);
        }
        return queryArray;
    }

    /** 
     * A helper method used to parse the list of returned vm's based on the
     * inputted query.
     *
     * @param queryArray
     * @param vmMap
     * @param queryDelimiter
     * @return
     * @throws BridgeError
     */
    protected Boolean checkVmDetails(ArrayList<ArrayList> queryArray, Map vmMap, String queryDelimiter) throws BridgeError {
        Boolean meetsQualification = true;
        for (ArrayList queryEntry : queryArray) {
            // meetsQualification is automatically assumed to be false if there
            // is a query and will be turned to true again if the proper query
            // conditions are met
            meetsQualification = false;
            if (vmMap.containsKey(queryEntry.get(0))) {
                Object vmObject = vmMap.get(queryEntry.get(0));
                String operator = (String)queryEntry.get(1);
                Object compareObject = queryEntry.get(2);
                if (vmObject.getClass().equals(GregorianCalendar.class)) {
                    // handling all the possible options for calendar comparison
                    GregorianCalendar cal = (GregorianCalendar)vmObject;
                    GregorianCalendar calCompare = (GregorianCalendar)compareObject;
                    if (operator.equals("<")) {
                        if (cal.before(calCompare)) {
                            meetsQualification = true;
                        }
                    } else if (operator.equals(">")) {
                        if (cal.after(calCompare)) {
                            meetsQualification = true;
                        }
                    } else if (operator.equals(">=")) {
                        if (cal.after(calCompare)) {
                            meetsQualification = true;
                        } else if (DateUtils.isSameDay(cal, calCompare)) {
                            meetsQualification = true;
                        }
                    } else if (operator.equals("<=")) {
                        if (cal.before(calCompare)) {
                            meetsQualification = true;
                        } else if (DateUtils.isSameDay(cal, calCompare)) {
                            meetsQualification = true;
                        }
                    } else if (operator.equals("=")) {
                        if (DateUtils.isSameDay(cal, calCompare)) {
                            meetsQualification = true;
                        }
                    } else if (operator.equals("!=") || operator.equals("<>")) {
                        if (!DateUtils.isSameDay(cal, calCompare)) {
                            meetsQualification = true;
                        }
                    }
                }
                else if (vmObject.getClass().equals(ManagedObjectReference.class)) {

                }
                else if (operator.equals("<")) {
                    if (Long.valueOf(vmObject.toString()) < Long.valueOf(compareObject.toString())) {
                        meetsQualification = true;
                    } 
                }
                else if (operator.equals(">")) {
                    if (Long.valueOf(vmObject.toString()) > Long.valueOf(compareObject.toString())) {
                        meetsQualification = true;
                    }
                }
                else if (operator.equals("<=")) {
                    if (Long.valueOf(vmObject.toString()) <= Long.valueOf(compareObject.toString())) {
                        meetsQualification = true;
                    } 
                }
                else if (operator.equals(">=")) {
                    if (Long.valueOf(vmObject.toString()) >= Long.valueOf(compareObject.toString())) {
                        meetsQualification = true;
                    } 
                }
                else if (operator.equals("!=") || operator.equals("<>")) {
                    if (!vmObject.toString().equals(compareObject)) {
                        meetsQualification = true;
                    }
                }
                else if (vmObject.toString().equals(compareObject)) {
                    meetsQualification = true;
                }
            }
            if (meetsQualification == false && queryDelimiter.equals("AND")) {
                // breaks to the return if the queryDelimiter is AND and any of
                // the query definitions are false
                break;
            }
            if (meetsQualification == true && queryDelimiter.equals("OR")) {
                // breaks to the return if the queryDelimiter is OR and any of
                // the query definitions are true
                break;
            }
        }
        return meetsQualification;
    }

    private Map convertMorFields(List<String> intersectingFields, Map vmDetails) throws BridgeError{
        for (String field : intersectingFields) {
            if (field.equals("runtime.host")) {
                HostSystem hs = new HostSystem(this.instance.getServerConnection(), (ManagedObjectReference)vmDetails.get(field));
                vmDetails.put("runtime.host", hs.getName());
            }
            else {
                throw new BridgeError("The bridge does not currently support the managed object type for field");
            }
        }
        return vmDetails;
    }
    
    private static class RecordComparator implements Comparator<Record> {
        private List<String> fields;

        public RecordComparator(List<String> fields) {
            this.fields = fields;
        }
        
        @Override
        public int compare( Record record1, Record record2) {
            int comparison = 0;
            for(String field : fields) {
                String value1 = record1.getValue(field).toString();
                String value2 = record2.getValue(field).toString();
                // If both records are null, continue with the comparison
                if (value1 == null && value2 == null) {continue;}
                // If only value1 is null, return a negative number (indicating
                // that record1 is "less than" record2).
                else if (value1 == null) {
                    comparison = -1;
                    break;
                }
                // If only value2 is null, return a positive number (indicating
                // that record1 is "greater than" record 2);
                else if (value2 == null) {
                    comparison = 1;
                    break;
                }
                // If the values are equal, continue to the next comparison
                else if (value1.compareTo(value2) == 0) {continue;}
                // If the values are not equal, return the result of the
                // comparison
                else {
                    comparison = value1.compareTo(value2);
                    break;
                }
            }
            return comparison;
        }
    }
    
}
