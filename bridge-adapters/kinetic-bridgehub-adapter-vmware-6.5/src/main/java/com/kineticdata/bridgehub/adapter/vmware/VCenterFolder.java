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
import com.vmware.vapi.std.errors.InvalidArgument;
import com.vmware.vcenter.Folder;
import com.vmware.vcenter.FolderTypes;
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
public class VCenterFolder {

    private Folder folderService;
    private VMWareQualificationParser qualificationParser;
    
    public VCenterFolder(VapiAuthenticationHelper vapiAuthHelper, StubConfiguration sessionStubConfig) {
        this.folderService = vapiAuthHelper.getStubFactory().createStub(Folder.class, sessionStubConfig); 
    }
    
    /*---------------------------------------------------------------------------------------------
     * IMPLEMENTATION METHODS
     *-------------------------------------------------------------------------------------------*/

    public Count count(BridgeRequest request) throws BridgeError {
        
        qualificationParser = new VMWareQualificationParser();
       
        List<FolderTypes.Summary> list = getList(qualificationParser.parseQuery(request));
        
        Long count;
        count = Long.valueOf(list.size());

        logger.trace("Count of Records: " + count);
        //Return the response
        return new Count(count);
    }

    public Record retrieve(BridgeRequest request) throws BridgeError {
        
        qualificationParser = new VMWareQualificationParser();
       
        List<FolderTypes.Summary> list = getList(qualificationParser.parseQuery(request));

        Record record = new Record(null);
        
        if (list.size() > 1) {
            throw new BridgeError("Multiple results matched an expected single match query");
        } else if (list.isEmpty()) {
            record = new Record(null);
        } else {
            FolderTypes.Summary summary = list.get(0);
            record = new Record(buildRecord(request, summary));
        }
        
        logger.trace("One Record Found: " + record.getRecord());
        // Returning the response
        return record;
    }

    public RecordList search(BridgeRequest request) throws BridgeError {
        
        qualificationParser = new VMWareQualificationParser();
       
        List<FolderTypes.Summary> list = getList(qualificationParser.parseQuery(request));
       
        ArrayList<Record> records = new ArrayList<Record>();
        
        for (FolderTypes.Summary summary : list) {
            Record record = new Record(null);
            if (!list.isEmpty()) {
                record = new Record(buildRecord(request, summary));
            }
            records.add(record);
        }
        
        List<String> fields = request.getFields();
        
        if(fields == null) {
            fields = Arrays.asList("folder", "name", "type");
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
    
    protected Map<String, Object> buildRecord(BridgeRequest request, FolderTypes.Summary summary) {
        Map<String, Object> recordValues = new HashMap();
        List<String> fields = request.getFields();
        
        if (fields != null && fields.size() > 0) {
            for (String field : fields) {
                if (field.equals("folder")) {
                    recordValues.put(field, summary.getFolder().toString());
                } else if(field.equals("name")) {
                    recordValues.put(field, summary.getName());
                } else if(field.equals("type")) {
                    recordValues.put(field, summary.getType().toString());
                } else {
                    logger.debug(field + " is not a valid field for the " + request.getStructure() + " Structure");
                }
            }
        } else {
            recordValues.put("folder", summary.getFolder().toString());
            recordValues.put("name", summary.getName());
            recordValues.put("type", summary.getType().toString());
        }
        
        return recordValues;
    }
    
    protected List<FolderTypes.Summary> getList(Map<String,ArrayList<String>> parsedQuery) throws BridgeError {
        List<FolderTypes.Summary> list;
        
        if (!parsedQuery.isEmpty() && parsedQuery.get("filter.type") != null &&
                parsedQuery.get("filter.type").size() > 1) {
            throw new BridgeError("filter.type was included more than one time in the query parameters");
        } 
        
        try {
            FolderTypes.FilterSpec.Builder bldr = new FolderTypes.FilterSpec.Builder();
                parsedQuery.forEach((field,value) -> {
                    field = field.trim();
                    Set<String> set = new HashSet<String>(value);
                    if (field.equals("filter.folders")) {
                        bldr.setFolders(set);
                    } else if (field.equals("filter.type")) {
                        if (value.contains("DATACENTER")) {
                            bldr.setType(FolderTypes.Type.DATACENTER);
                        } else if (value.contains("DATASTORE")) {
                            bldr.setType(FolderTypes.Type.DATASTORE);
                        } else if (value.contains("HOST")) {
                            bldr.setType(FolderTypes.Type.HOST);
                        } else if (value.contains("NETWORK")) {
                            bldr.setType(FolderTypes.Type.NETWORK);
                        } else if (value.contains("VIRTUAL_MACHINE")) {
                            bldr.setType(FolderTypes.Type.VIRTUAL_MACHINE);
                        }
                    } else if (field.equals("filter.parent_folders")) {
                        bldr.setParentFolders(set);
                    } else if (field.equals("filter.names")) {
                        bldr.setNames(set);
                    } else if (field.equals("filter.datacenters")) {
                        bldr.setDatacenters(set);
                    }
                });
               
            list = this.folderService.list(bldr.build());
        } catch (InvalidArgument e) {
            throw new BridgeError(e);
        }
        return list;
    };
}
