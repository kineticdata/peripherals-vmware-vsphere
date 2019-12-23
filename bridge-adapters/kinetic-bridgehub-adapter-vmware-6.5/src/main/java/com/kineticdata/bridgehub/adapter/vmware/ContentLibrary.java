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
import com.vmware.content.Library;
import com.vmware.content.LibraryModel;
import com.vmware.content.LibraryTypes;
import com.vmware.vapi.bindings.StubConfiguration;
import com.vmware.vapi.std.errors.InvalidArgument;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author chad.rehm
 */
public class ContentLibrary {
    
    private Library libraryService;
    private VMWareQualificationParser qualificationParser;
    
    public ContentLibrary(VapiAuthenticationHelper vapiAuthHelper, StubConfiguration sessionStubConfig) {
        this.libraryService = vapiAuthHelper.getStubFactory().createStub(Library.class, sessionStubConfig); 
    }
    
    /*---------------------------------------------------------------------------------------------
     * IMPLEMENTATION METHODS
     *-------------------------------------------------------------------------------------------*/

    public Count count(BridgeRequest request) throws BridgeError {
        
        qualificationParser = new VMWareQualificationParser();
       
        Map<String,ArrayList<String>> parsedQuery = qualificationParser.parseQuery(request);
        List<String> idList;
        
        if (parsedQuery.isEmpty()) {
            idList = this.libraryService.list();
        } else { 
            idList = getIdList(parsedQuery);
        }
        
        Long count;
        count = Long.valueOf(idList.size());

        logger.trace("Count of Records: " + count);
        //Return the response
        return new Count(count);
    }

    public Record retrieve(BridgeRequest request) throws BridgeError {
        
        qualificationParser = new VMWareQualificationParser();
       
        Map<String,ArrayList<String>> parsedQuery = qualificationParser.parseQuery(request);
        List<String> idList = new ArrayList();
        
        if (parsedQuery.isEmpty()) {
            idList = this.libraryService.list();
        } else if (parsedQuery.get("library_id").size() == 1){
            idList.add(parsedQuery.get("library_id").get(0));
        } else if (parsedQuery.get("library_id").size() > 1) {
            throw new BridgeError("Only one library_id parameter may be provided");
        } else { 
            idList = getIdList(parsedQuery);
        }
        
        Record record = new Record(null);
        
        if (idList.size() > 1){
            throw new BridgeError("Multiple results matched an expected single match query");
        } else {
            LibraryModel libraryModel = libraryService.get(idList.get(0));
            record = new Record(buildRecord(request, libraryModel));
        }

        logger.trace("One Record Found: " + record.getRecord());
        // Returning the response
        return record;
    }

    public RecordList search(BridgeRequest request) throws BridgeError {
        
        qualificationParser = new VMWareQualificationParser();
        
        Map<String,ArrayList<String>> parsedQuery = qualificationParser.parseQuery(request);
        List<String> idList = new ArrayList();
        
        if (parsedQuery.isEmpty()) {
            idList = this.libraryService.list();
        } else if (parsedQuery.get("library_id").size() == 1){
            idList.add(parsedQuery.get("library_id").get(0));
        } else if (parsedQuery.get("library_id").size() > 1) {
            throw new BridgeError("Only one library_id parameter may be provided");
        } else { 
            idList = getIdList(parsedQuery);
        }
        
        ArrayList<Record> records = new ArrayList<Record>();
        
        for (String id : idList) {
            Record record = new Record(null);
            LibraryModel libraryModel = libraryService.get(id);
            
            record = new Record(buildRecord(request, libraryModel));
            records.add(record);
        }
        
        List<String> fields = request.getFields();
        
        if(fields == null) {
            fields = Arrays.asList("id", "name", "creation_time", "description",
                "last_modified_time", "server_guid", "type", "version",
                "publish_info[authentication_method]");
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
    
    protected Map<String, Object> buildRecord(BridgeRequest request, LibraryModel libraryModel) {
        Map<String, Object> recordValues = new HashMap();
        List<String> fields = request.getFields();
        
        if (fields != null && fields.size() > 0) {
            for (String field : fields) {
                field = field.trim();
                if (field.equals("id")) {
                    recordValues.put(field, libraryModel.getId());
                } else if(field.equals("name")) {
                    recordValues.put(field, libraryModel.getName());
                } else if(field.equals("creation_time")) {
                    recordValues.put(field, libraryModel.getCreationTime().toInstant().toString());
                } else if(field.equals("description")) {
                    recordValues.put(field, libraryModel.getDescription());
                } else if(field.equals("last_modified_time")) {
                    recordValues.put(field, libraryModel.getLastModifiedTime().toInstant().toString());
                } else if(field.equals("server_guid")) {
                    recordValues.put(field, libraryModel.getServerGuid());
                } else if(field.equals("type")) {
                    recordValues.put(field, libraryModel.getType().toString());
                } else if(field.equals("version")) {
                    recordValues.put(field, libraryModel.getVersion());
                } else if(field.equals("publish_info[authentication_method]")) {
                    recordValues.put(field, libraryModel.getPublishInfo().getAuthenticationMethod().toString());
                }else {
                    logger.debug(field + " is not a valid field for the " + request.getStructure() + " Structure");
                }
            }
        } else {
            recordValues.put("id", libraryModel.getId());
            recordValues.put("name", libraryModel.getName());
            recordValues.put("creation_time", libraryModel.getCreationTime().toInstant().toString());
            recordValues.put("description", libraryModel.getDescription());
            recordValues.put("last_modified_time", libraryModel.getLastModifiedTime().toInstant().toString());
            recordValues.put("server_guid", libraryModel.getServerGuid());
            recordValues.put("type", libraryModel.getType().toString());
            recordValues.put("version", libraryModel.getVersion());
            recordValues.put("publish_info[authentication_method]",
                libraryModel.getPublishInfo().getAuthenticationMethod().toString());
        }
        
        return recordValues;
    }
    
    protected List<String> getIdList(Map<String,ArrayList<String>> parsedQuery) throws BridgeError {
        List<String> list = null;

        try {
            LibraryTypes.FindSpec.Builder bldr = new LibraryTypes.FindSpec.Builder();
                parsedQuery.forEach((field,value) -> {
                    if (field.equals("filter.name")) {
                        bldr.setName(field);
                    } else if (field.equals("filter.type")) {
                        if (value.contains("LOCAL")) {
                            bldr.setType(LibraryModel.LibraryType.LOCAL);
                        }
                        if (value.contains("SUBSCRIBED")) {
                            bldr.setType(LibraryModel.LibraryType.SUBSCRIBED);
                        }
                    } 
                });
            this.libraryService.find(bldr.build());
        } catch ( InvalidArgument e) {
            throw new BridgeError(e.getMessages().get(0).getDefaultMessage() +
                    " filter.name and filter.type are valid query properties.");
        }
        
        return list;
    };
}
