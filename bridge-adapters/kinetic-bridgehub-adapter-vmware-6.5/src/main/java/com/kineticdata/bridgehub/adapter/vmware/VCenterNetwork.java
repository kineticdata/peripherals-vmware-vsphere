/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.kineticdata.bridgehub.adapter.vmware;

import com.kineticdata.bridgehub.adapter.BridgeError;
import com.kineticdata.bridgehub.adapter.BridgeRequest;
import com.kineticdata.bridgehub.adapter.BridgeUtils;
import com.kineticdata.bridgehub.adapter.Count;
import com.kineticdata.bridgehub.adapter.Record;
import com.kineticdata.bridgehub.adapter.RecordList;
import static com.kineticdata.bridgehub.adapter.vmware.VMWareAdapter.logger;
import com.vmware.vapi.bindings.StubConfiguration;
import com.vmware.vcenter.Network;
import com.vmware.vcenter.NetworkTypes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author chad.rehm
 */
public class VCenterNetwork {
    
    private Network networkService;
    private VMWareQualificationParser qualificationParser;
    
    public VCenterNetwork(VapiAuthenticationHelper vapiAuthHelper, StubConfiguration sessionStubConfig) {
        this.networkService = vapiAuthHelper.getStubFactory().createStub(Network.class, sessionStubConfig); 
    }
    
    /*---------------------------------------------------------------------------------------------
     * IMPLEMENTATION METHODS
     *-------------------------------------------------------------------------------------------*/

    public Count count(BridgeRequest request) throws BridgeError {
        
        qualificationParser = new VMWareQualificationParser();
       
        List<NetworkTypes.Summary> list = getList(qualificationParser.parseQuery(request));
        
        Long count;
        count = Long.valueOf(list.size());

        logger.trace("Count of Records: " + count);
        //Return the response
        return new Count(count);
    }

    public Record retrieve(BridgeRequest request) throws BridgeError {
        
        qualificationParser = new VMWareQualificationParser();
       
        List<NetworkTypes.Summary> list = getList(qualificationParser.parseQuery(request));

        Record record = new Record(null);
        
        if (list.size() > 1) {
            throw new BridgeError("Multiple results matched an expected single match query");
        } else if (list.isEmpty()) {
            record = new Record(null);
        } else {
            NetworkTypes.Summary summary = list.get(0);
            record = new Record(buildRecord(request, summary));
        }
        
        logger.trace("One Record Found: " + record.getRecord());
        // Returning the response
        return record;
    }

    public RecordList search(BridgeRequest request) throws BridgeError {
        
        qualificationParser = new VMWareQualificationParser();
       
        List<NetworkTypes.Summary> list = getList(qualificationParser.parseQuery(request));
        
        ArrayList<Record> records = new ArrayList<Record>();
        
        for (NetworkTypes.Summary summary : list) {
            Record record = new Record(null);
            if (!list.isEmpty()) {
                record = new Record(buildRecord(request, summary));
            }
            records.add(record);
        }
        
                
        List<String> fields = request.getFields();
        
        if(fields == null) {
            fields = Arrays.asList("network", "name", "type");
        }
        
        // Sort records
        if (request.getMetadata("order") == null) {
            // name,type,desc assumes name ASC,type ASC,desc ASC
            Map<String,String> defaultOrder = new LinkedHashMap<String,String>();
            for (String field : fields) {
                defaultOrder.put(field, "ASC");
            }
            records = VMWareSortHelper.sortRecords(defaultOrder, records);
        } else {
            // Creates a map out of order metadata
            Map<String,String> orderParse = BridgeUtils.parseOrder(request.getMetadata("order"));
            records = VMWareSortHelper.sortRecords(orderParse, records);
        }

        // Building the output metadata
        Map<String,String> metadata = BridgeUtils.normalizePaginationMetadata(request.getMetadata());
        metadata.put("size", String.valueOf(records.size()));
        metadata.put("count", metadata.get("size"));
        
        logger.trace("Search resulted in " + records.size() + " found.");
        // Returning the response
        return new RecordList(fields, records, metadata);
    }
    
    /*----------------------------------------------------------------------------------------------
     * PRIVATE HELPER METHODS
     *--------------------------------------------------------------------------------------------*/
    
    protected Map<String, Object> buildRecord(BridgeRequest request, NetworkTypes.Summary summary) {
        Map<String, Object> recordValues = new HashMap();
        List<String> fields = request.getFields();
        
        if (fields != null && fields.size() > 0) {
            for (String field : fields) {
                field = field.trim();
                if (field.equals("network")) {
                    recordValues.put(field, summary.getNetwork());
                } else if(field.equals("name")) {
                    recordValues.put(field, summary.getName());
                } else if(field.equals("type")) {
                    recordValues.put(field, summary.getType().toString());
                } else {
                    logger.debug(field + " is not a valid field for the " + request.getStructure() + " Structure");
                }
            }
        } else {
            recordValues.put("network", summary.getNetwork());
            recordValues.put("name", summary.getName());
            recordValues.put("type", summary.getType().toString());
                
        }
        
        return recordValues;
    }
    
    protected List<NetworkTypes.Summary> getList(Map<String,ArrayList<String>> parsedQuery) throws BridgeError {
        List<NetworkTypes.Summary> list;

        try {
            NetworkTypes.FilterSpec.Builder bldr = new NetworkTypes.FilterSpec.Builder();
                parsedQuery.forEach((field,value) -> {
                    Set<String> set = new HashSet<String>(value);
                    if (field.equals("filter.folders")) {
                        bldr.setFolders(set);
                    } else if (field.equals("filter.types")) {
                        Set<NetworkTypes.Type> typesSet = new HashSet<NetworkTypes.Type>();
                        if (value.contains("STANDARD_PORTGROUP")) {
                            typesSet.add(NetworkTypes.Type.STANDARD_PORTGROUP);
                        }
                        if (value.contains("DISTRIBUTED_PORTGROUP")) {
                            typesSet.add(NetworkTypes.Type.DISTRIBUTED_PORTGROUP);
                        }
                        if (value.contains("OPAQUE_NETWORK")) {
                            typesSet.add(NetworkTypes.Type.OPAQUE_NETWORK);
                        }
                        bldr.setTypes(typesSet);
                    } else if (field.equals("filter.networks")) {
                        bldr.setNetworks(set);
                    } else if (field.equals("filter.names")) {
                        bldr.setNames(set);
                    } else if (field.equals("filter.datacenters")) {
                        bldr.setDatacenters(set);
                    } 
                });
               
            list = this.networkService.list(bldr.build());
        } catch (Exception e) {
            throw new BridgeError(e);
        }
        return list;
    };
}