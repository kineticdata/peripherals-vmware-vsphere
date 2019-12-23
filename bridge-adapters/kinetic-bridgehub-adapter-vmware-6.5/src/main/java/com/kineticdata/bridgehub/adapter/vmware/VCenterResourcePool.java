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
import com.vmware.vcenter.ResourcePool;
import com.vmware.vcenter.ResourcePoolTypes;
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
public class VCenterResourcePool {
    
    private ResourcePool resourcePoolService;
    private VMWareQualificationParser qualificationParser;
    
    public VCenterResourcePool(VapiAuthenticationHelper vapiAuthHelper, StubConfiguration sessionStubConfig) {
        this.resourcePoolService = vapiAuthHelper.getStubFactory().createStub(ResourcePool.class, sessionStubConfig); 
    }
    
    /*---------------------------------------------------------------------------------------------
     * IMPLEMENTATION METHODS
     *-------------------------------------------------------------------------------------------*/

    public Count count(BridgeRequest request) throws BridgeError {
        
        qualificationParser = new VMWareQualificationParser();
       
        List<ResourcePoolTypes.Summary> list = getList(qualificationParser.parseQuery(request));
        
        Long count;
        count = Long.valueOf(list.size());

        logger.trace("Count of Records: " + count);
        //Return the response
        return new Count(count);
    }

    public Record retrieve(BridgeRequest request) throws BridgeError {
        
        qualificationParser = new VMWareQualificationParser();
       
        List<ResourcePoolTypes.Summary> list = getList(qualificationParser.parseQuery(request));

        Record record = new Record(null);
        
        if (list.size() > 1) {
            throw new BridgeError("Multiple results matched an expected single match query");
        } else if (list.isEmpty()) {
            record = new Record(null);
        } else {
            ResourcePoolTypes.Summary summary = list.get(0);
            record = new Record(buildRecord(request, summary));
        }
        
        logger.trace("One Record Found: " + record.getRecord());
        // Returning the response
        return record;
    }

    public RecordList search(BridgeRequest request) throws BridgeError {
        
        qualificationParser = new VMWareQualificationParser();
       
        List<ResourcePoolTypes.Summary> list = getList(qualificationParser.parseQuery(request));
       
        ArrayList<Record> records = new ArrayList<Record>();
        
        for (ResourcePoolTypes.Summary summary : list) {
            Record record = new Record(null);
            if (!list.isEmpty()) {
                record = new Record(buildRecord(request, summary));
            }
            records.add(record);
        }
        
        
        List<String> fields = request.getFields();
        
        if(fields == null) {
            fields = Arrays.asList("resource_pool", "name");
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
            System.out.println(BridgeUtils.parseOrder(request.getMetadata("order")));
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
    
    protected Map<String, Object> buildRecord(BridgeRequest request, ResourcePoolTypes.Summary summary) {
        Map<String, Object> recordValues = new HashMap();
        List<String> fields = request.getFields();
        
        if (fields != null && fields.size() > 0) {
            for (String field : fields) {
                field = field.trim();
                if (field.equals("resource_pool")) {
                    recordValues.put(field, summary.getResourcePool().toString());
                } else if(field.equals("name")) {
                    recordValues.put(field, summary.getName());
                }  else {
                    logger.debug(field + " is not a valid field for the " + request.getStructure() + " Structure");
                }
            }
        } else {
            recordValues.put("resource_pool", summary.getResourcePool().toString());
            recordValues.put("name", summary.getName());
        }
        
        return recordValues;
    }
    
    protected List<ResourcePoolTypes.Summary> getList(Map<String,ArrayList<String>> parsedQuery) throws BridgeError {
        List<ResourcePoolTypes.Summary> list;

        try {
            ResourcePoolTypes.FilterSpec.Builder bldr = new ResourcePoolTypes.FilterSpec.Builder();
                parsedQuery.forEach((field,value) -> {
                    Set<String> set = new HashSet<String>(value);
                    if (field.equals("filter.clusters")) {
                        bldr.setClusters(set);
                    } else if (field.equals("filter.hosts")) {
                        bldr.setHosts(set);
                    } else if (field.equals("filter.names")) {
                        bldr.setNames(set);
                    } else if (field.equals("filter.datacenters")) {
                        bldr.setDatacenters(set);
                    } else if (field.equals("filter.resource_pools")) {
                        bldr.setResourcePools(set);
                    } else if (field.equals("filter.parent_resource_pools")) {
                        bldr.setParentResourcePools(set);
                    }
                });
               
            list = this.resourcePoolService.list(bldr.build());
        } catch (Exception e) {
            throw new BridgeError(e);
        }
        return list;
    };
}
