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
import com.vmware.vcenter.Cluster;
import com.vmware.vcenter.ClusterTypes;
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
public class VCenterCluster {
    
    private Cluster clusterService;
    private VMWareQualificationParser qualificationParser;
    
    public VCenterCluster(VapiAuthenticationHelper vapiAuthHelper, StubConfiguration sessionStubConfig) {
        this.clusterService = vapiAuthHelper.getStubFactory().createStub(Cluster.class, sessionStubConfig); 
    }
    
    /*---------------------------------------------------------------------------------------------
     * IMPLEMENTATION METHODS
     *-------------------------------------------------------------------------------------------*/

    public Count count(BridgeRequest request) throws BridgeError {
        
        qualificationParser = new VMWareQualificationParser();
       
        List<ClusterTypes.Summary> list = getList(qualificationParser.parseQuery(request));
        
        Long count;
        count = Long.valueOf(list.size());

        logger.trace("Count of Records: " + count);
        //Return the response
        return new Count(count);
    }

    public Record retrieve(BridgeRequest request) throws BridgeError {
        
        qualificationParser = new VMWareQualificationParser();
       
        List<ClusterTypes.Summary> list = getList(qualificationParser.parseQuery(request));

        Record record = new Record(null);
        
        if (list.size() > 1) {
            throw new BridgeError("Multiple results matched an expected single match query");
        } else if (list.isEmpty()) {
            record = new Record(null);
        } else {
            ClusterTypes.Summary summary = list.get(0);
            ClusterTypes.Info info = clusterService.get(summary.getCluster());
            
            record = new Record(buildRecord(request, summary, info));
        }
        
        logger.trace("One Record Found: " + record.getRecord());
        // Returning the response
        return record;
    }

    public RecordList search(BridgeRequest request) throws BridgeError {
        
        qualificationParser = new VMWareQualificationParser();
       
        List<ClusterTypes.Summary> list = getList(qualificationParser.parseQuery(request));
       
        ArrayList<Record> records = new ArrayList<Record>();
        
        for (ClusterTypes.Summary summary : list) {
            Record record = new Record(null);
            if (!list.isEmpty()) {
                ClusterTypes.Info info = clusterService.get(summary.getCluster());
                
                record = new Record(buildRecord(request, summary, info));
            }
            records.add(record);
        }

        List<String> fields = request.getFields();
        
        if(fields == null) {
            fields = Arrays.asList("cluster", "name", "ha_enabled", "drs_enabled", "resource_pool");
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
    
    protected Map<String, Object> buildRecord(BridgeRequest request, ClusterTypes.Summary summary, ClusterTypes.Info info) {
        Map<String, Object> recordValues = new HashMap();
        List<String> fields = request.getFields();
        
        if (fields != null && fields.size() > 0) {
            for (String field : fields) {
                field = field.trim();
                if (field.equals("cluster")) {
                    recordValues.put(field, summary.getCluster());
                } else if(field.equals("name")) {
                    recordValues.put(field, summary.getName());
                } else if(field.equals("ha_enabled")) {
                    recordValues.put(field, Boolean.toString(summary.getHaEnabled()));
                } else if(field.equals("drs_enabled")) {
                    recordValues.put(field, Boolean.toString(summary.getDrsEnabled()));
                } else if(field.equals("resource_pool")) {
                    recordValues.put(field, info.getResourcePool());
                } else {
                    logger.debug(field + " is not a valid field for the " + request.getStructure() + " Structure");
                }
            }
        } else {
            recordValues.put("cluster", summary.getCluster());
            recordValues.put("name", summary.getName());
            recordValues.put("ha_enabled", Boolean.toString(summary.getHaEnabled()));
            recordValues.put("drs_enabled", Boolean.toString(summary.getDrsEnabled()));
            recordValues.put("resource_pool", info.getResourcePool());
        }
            
        return recordValues;
    }
    
    protected List<ClusterTypes.Summary> getList(Map<String,ArrayList<String>> parsedQuery) throws BridgeError {
        List<ClusterTypes.Summary> list;

        try {
            ClusterTypes.FilterSpec.Builder bldr = new ClusterTypes.FilterSpec.Builder();
                parsedQuery.forEach((field,value) -> {
                    Set<String> set = new HashSet<String>(value);
                    if (field.equals("filter.folders")) {
                        bldr.setFolders(set);
                    } else if (field.equals("filter.clusters")) {
                        bldr.setClusters(set);
                    } else if (field.equals("filter.names")) {
                        bldr.setNames(set);
                    } else if (field.equals("filter.datacenters")) {
                        bldr.setDatacenters(set);
                    } else if (field.equals("filter.folders")) {
                        bldr.setFolders(set);
                    }
                });
               
            list = this.clusterService.list(bldr.build());
        } catch (Exception e) {
            throw new BridgeError(e);
        }
        return list;
    };
}