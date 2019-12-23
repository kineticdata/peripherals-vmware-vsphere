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
import com.vmware.vcenter.Datastore;
import com.vmware.vcenter.DatastoreTypes;
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
public class VCenterDatastore {
    
    private Datastore datastoreService;
    private VMWareQualificationParser qualificationParser;
    
    public VCenterDatastore(VapiAuthenticationHelper vapiAuthHelper, StubConfiguration sessionStubConfig) {
        this.datastoreService = vapiAuthHelper.getStubFactory().createStub(Datastore.class, sessionStubConfig); 
    }
    
    /*---------------------------------------------------------------------------------------------
     * IMPLEMENTATION METHODS
     *-------------------------------------------------------------------------------------------*/

    public Count count(BridgeRequest request) throws BridgeError {
        
        qualificationParser = new VMWareQualificationParser();
       
        List<DatastoreTypes.Summary> list = getList(qualificationParser.parseQuery(request));
        
        Long count;
        count = Long.valueOf(list.size());

        logger.trace("Count of Records: " + count);
        //Return the response
        return new Count(count);
    }

    public Record retrieve(BridgeRequest request) throws BridgeError {
        
        qualificationParser = new VMWareQualificationParser();
       
        List<DatastoreTypes.Summary> list = getList(qualificationParser.parseQuery(request));

        Record record = new Record(null);
        
        if (list.size() > 1) {
            throw new BridgeError("Multiple results matched an expected single match query");
        } else if (list.isEmpty()) {
            record = new Record(null);
        } else {
            DatastoreTypes.Summary summary = list.get(0);
            DatastoreTypes.Info info = datastoreService.get(summary.getDatastore());
            record = new Record(buildRecord(request, summary, info));
        }
        
        logger.trace("One Record Found: " + record.getRecord());
        // Returning the response
        return record;
    }

    public RecordList search(BridgeRequest request) throws BridgeError {
        
        qualificationParser = new VMWareQualificationParser();
       
        List<DatastoreTypes.Summary> list = getList(qualificationParser.parseQuery(request));
       
        ArrayList<Record> records = new ArrayList<Record>();
        
        for (DatastoreTypes.Summary summary : list) {
            Record record = new Record(null);
            if (!list.isEmpty()) {
                DatastoreTypes.Info info = datastoreService.get(summary.getDatastore());
                record = new Record(buildRecord(request, summary, info));
            }
            records.add(record);
        }
         
        List<String> fields = request.getFields();
        
        if(fields == null) {
            fields = Arrays.asList("datastore", "name", "type", "free_space",
                "capacity", "accessible", "multiple_host_access", "thin_provisioning_supported");
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
    
    protected Map<String, Object> buildRecord(BridgeRequest request, DatastoreTypes.Summary summary, DatastoreTypes.Info info) {
        Map<String, Object> recordValues = new HashMap();
        List<String> fields = request.getFields();
        
        if (fields != null && fields.size() > 0) {
            for (String field : fields) {
                if (field.equals("datastore")) {
                    recordValues.put(field, summary.getDatastore());
                } else if(field.equals("name")) {
                    recordValues.put(field, summary.getName());
                } else if(field.equals("type")) {
                    recordValues.put(field, summary.getType().toString());
                } else if(field.equals("free_space")) {
                    recordValues.put(field, summary.getFreeSpace().toString());
                } else if(field.equals("capacity")) {
                    recordValues.put(field, summary.getCapacity().toString());
                } else if(field.equals("accessible")) {
                    recordValues.put(field, Boolean.toString(info.getAccessible()));
                } else if(field.equals("multiple_host_access")) {
                    recordValues.put(field, Boolean.toString(info.getMultipleHostAccess()));
                } else if(field.equals("thin_provisioning_supported")) {
                    recordValues.put(field, Boolean.toString(info.getThinProvisioningSupported()));
                } else {
                    logger.debug(field + " is not a valid field for the " + request.getStructure() + " Structure");
                }
            }
        } else {
            recordValues.put("datastore", summary.getDatastore());
            recordValues.put("name", summary.getName());
            recordValues.put("type", summary.getType().toString());
            recordValues.put("free_space", summary.getFreeSpace().toString());
            recordValues.put("capacity", summary.getCapacity().toString());
            recordValues.put("accessible", Boolean.toString(info.getAccessible()));
            recordValues.put("multiple_host_access", Boolean.toString(info.getMultipleHostAccess()));
            recordValues.put("thin_provisioning_supported", Boolean.toString(info.getThinProvisioningSupported()));
        }
        
        return recordValues;
    }
    
    protected List<DatastoreTypes.Summary> getList(Map<String,ArrayList<String>> parsedQuery) throws BridgeError {
        List<DatastoreTypes.Summary> list;

        try {
            DatastoreTypes.FilterSpec.Builder bldr = new DatastoreTypes.FilterSpec.Builder();
                parsedQuery.forEach((field,value) -> {
                    field = field.trim();
                    Set<String> set = new HashSet<String>(value);
                    if (field.equals("filter.folders")) {
                        bldr.setFolders(set);
                    } else if (field.equals("filter.types")) {
                        Set<DatastoreTypes.Type> datastoreSet = new HashSet<DatastoreTypes.Type>();
                        if (value.contains("CIFS")) {
                            datastoreSet.add(DatastoreTypes.Type.CIFS);
                        }
                        if (value.contains("NFS")) {
                            datastoreSet.add(DatastoreTypes.Type.NFS);
                        }
                        if (value.contains("NFS41")) {
                            datastoreSet.add(DatastoreTypes.Type.NFS41);
                        }
                        if (value.contains("VFFS")) {
                            datastoreSet.add(DatastoreTypes.Type.VFFS);
                        }
                        if (value.contains("VMFS")) {
                            datastoreSet.add(DatastoreTypes.Type.VMFS);
                        }
                        if (value.contains("VSAN")) {
                            datastoreSet.add(DatastoreTypes.Type.VSAN);
                        }
                        if (value.contains("VVOL")) {
                            datastoreSet.add(DatastoreTypes.Type.VVOL);
                        }
                        bldr.setTypes(datastoreSet);
                    } else if (field.equals("filter.datastores")) {
                        bldr.setDatastores(set);
                    } else if (field.equals("filter.names")) {
                        bldr.setNames(set);
                    } else if (field.equals("filter.datacenters")) {
                        bldr.setDatacenters(set);
                    } 
                });
               
            list = this.datastoreService.list(bldr.build());

        } catch (Exception e) {
            throw new BridgeError(e);
        }
        return list;
    };
}