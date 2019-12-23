package com.kineticdata.bridgehub.adapter.vmware;

import com.kineticdata.bridgehub.adapter.BridgeRequest;
import com.kineticdata.bridgehub.adapter.QualificationParser;
import java.util.*;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;

public class VMWareQualificationParser extends QualificationParser {
        /** Defines the logger */
    protected static final org.slf4j.Logger logger = LoggerFactory.getLogger(VMWareAdapter.class);
    
    public Map<String,ArrayList<String>> parseQuery(BridgeRequest request) {
        Map<String,ArrayList<String>> parsedQuery = new HashMap<String,ArrayList<String>>();
        
         // Split into individual queries by splitting on the & between each distinct query
        String[] queries = request.getQuery().split("[&,?]");
        
        for (String query : queries) {
            // Split the query on the = to determine the field/value key-pair. Anything before the
            // first = is considered to be the field and anything after (including more = signs if
            // there are any) is considered to be part of the value
            String[] str_array = query.split("=");
            String field = str_array[0].trim();
            String value = "";
            if (str_array.length > 1) value = StringUtils.join(Arrays.copyOfRange(str_array, 1, str_array.length),"=");
            
            if (field.equals("filter.folders")) {
                if (parsedQuery.get(field) == null) {
                    parsedQuery.put(field, new ArrayList<String>());
                }
                parsedQuery.get(field).add(value);
            } else if (field.equals("filter.clusters")) {
                if (parsedQuery.get(field) == null) {
                    parsedQuery.put(field, new ArrayList<String>());
                }
                parsedQuery.get(field).add(value);
            } else if (field.equals("filter.power_states")) {
                if (parsedQuery.get(field) == null) {
                    parsedQuery.put(field, new ArrayList<String>());
                }
                parsedQuery.get(field).add(value);
            } else if (field.equals("filter.hosts")) {
                if (parsedQuery.get(field) == null) {
                    parsedQuery.put(field, new ArrayList<String>());
                }
                parsedQuery.get(field).add(value);
            } else if (field.equals("filter.names")) {
                if (parsedQuery.get(field) == null) {
                    parsedQuery.put(field, new ArrayList<String>());
                }
                parsedQuery.get(field).add(value);
            } else if (field.equals("filter.datacenters")) {
                if (parsedQuery.get(field) == null) {
                    parsedQuery.put(field, new ArrayList<String>());
                }
                parsedQuery.get(field).add(value);
            } else if (field.equals("filter.resource_pools")) {
                if (parsedQuery.get(field) == null) {
                    parsedQuery.put(field, new ArrayList<String>());
                }
                parsedQuery.get(field).add(value);
            } else if (field.equals("filter.vms")) {
                if (parsedQuery.get(field) == null) {
                    parsedQuery.put(field, new ArrayList<String>());
                }
                parsedQuery.get(field).add(value);
            } else if (field.equals("filter.datastores")) {
                if (parsedQuery.get(field) == null) {
                    parsedQuery.put(field, new ArrayList<String>());
                }
                parsedQuery.get(field).add(value);
            } else if (field.equals("filter.types")) {
                if (parsedQuery.get(field) == null) {
                    parsedQuery.put(field, new ArrayList<String>());
                }
                parsedQuery.get(field).add(value);
            } else if (field.equals("filter.parent_folders")) {
                if (parsedQuery.get(field) == null) {
                    parsedQuery.put(field, new ArrayList<String>());
                }
                parsedQuery.get(field).add(value);
            } else if (field.equals("filter.type")) {
                if (parsedQuery.get(field) == null) {
                    parsedQuery.put(field, new ArrayList<String>());
                }
                parsedQuery.get(field).add(value);
            } else if (field.equals("filter.networks")) {
                if (parsedQuery.get(field) == null) {
                    parsedQuery.put(field, new ArrayList<String>());
                }
                parsedQuery.get(field).add(value);
            } else if (field.equals("filter.parent_resource_pools")) {
                if (parsedQuery.get(field) == null) {
                    parsedQuery.put(field, new ArrayList<String>());
                }
                parsedQuery.get(field).add(value);
            } else if (field.equals("library_id")) {
                if (parsedQuery.get(field) == null) {
                    parsedQuery.put(field, new ArrayList<String>());
                }
                parsedQuery.get(field).add(value);
            }
        }
        return parsedQuery;
    }
    
    public String encodeParameter(String name, String value) {
        return value;
    }
}
