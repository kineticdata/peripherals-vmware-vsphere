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
import com.vmware.vcenter.VM;
import com.vmware.vcenter.VMTypes;
import com.vmware.vcenter.vm.PowerTypes;
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
public class VCenterVM {
    
    private VM vmService;
    private VMWareQualificationParser qualificationParser;
    
    public VCenterVM(VapiAuthenticationHelper vapiAuthHelper, StubConfiguration sessionStubConfig) {
        this.vmService = vapiAuthHelper.getStubFactory().createStub(VM.class, sessionStubConfig); 
    }
    
    /*---------------------------------------------------------------------------------------------
     * IMPLEMENTATION METHODS
     *-------------------------------------------------------------------------------------------*/

    public Count count(BridgeRequest request) throws BridgeError {
        
        qualificationParser = new VMWareQualificationParser();
       
        List<VMTypes.Summary> list = getList(qualificationParser.parseQuery(request));
        
        Long count;
        count = Long.valueOf(list.size());

        logger.trace("Count of Records: " + count);
        //Return the response
        return new Count(count);
    }

    public Record retrieve(BridgeRequest request) throws BridgeError {
        
        qualificationParser = new VMWareQualificationParser();
       
        List<VMTypes.Summary> list = getList(qualificationParser.parseQuery(request));

        Record record = new Record(null);
        
        if (list.size() > 1) {
            throw new BridgeError("Multiple results matched an expected single match query");
        } else if (list.isEmpty()) {
            record = new Record(null);
        } else {
            VMTypes.Summary summary = list.get(0);
            VMTypes.Info info = vmService.get(summary.getVm());
            record = new Record(buildRecord(request, summary, info));
        }
        
        logger.trace("One Record Found: " + record.getRecord());
        // Returning the response
        return record;
    }

    public RecordList search(BridgeRequest request) throws BridgeError {
        
        qualificationParser = new VMWareQualificationParser();
       
        List<VMTypes.Summary> list = getList(qualificationParser.parseQuery(request));
       
        ArrayList<Record> records = new ArrayList<Record>();
        
        for (VMTypes.Summary summary : list) {
            Record record = new Record(null);
            if (!list.isEmpty()) {
                VMTypes.Info info = vmService.get(summary.getVm());
                record = new Record(buildRecord(request, summary, info));
            }
            records.add(record);
        }
        
        List<String> fields = request.getFields();
        
        if(fields == null) {
            fields = Arrays.asList("memory_size_MiB", "name", "cpu_count", 
                "power_state", "vm", "guest_os", "memory[size_MiB]", 
                "memory[hot_add_enabled]", "cpu[hot_remove_enabled]", 
                "cpu[count]", "cpu[hot_add_enabled]", "cpu[cores_per_socket]");
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
    
    protected Map<String, Object> buildRecord(BridgeRequest request, VMTypes.Summary summary, VMTypes.Info info) {
        Map<String, Object> recordValues = new HashMap();
        List<String> fields = request.getFields();

        if (fields != null && fields.size() > 0) {
            for (String field : fields) {
                field = field.trim();
                if (field.equals("memory_size_MiB")) {
                    recordValues.put(field, summary.getMemorySizeMiB().toString());
                } else if(field.equals("name")) {
                    recordValues.put(field, summary.getName());
                } else if(field.equals("cpu_count")) {
                    recordValues.put(field, summary.getCpuCount().toString());
                } else if(field.equals("power_state")) {
                    recordValues.put(field, summary.getPowerState().toString());
                } else if(field.equals("vm")) {
                    recordValues.put(field, summary.getVm());
                } else if(field.equals("guest_os")) {
                    recordValues.put(field, info.getGuestOS().toString());
                } else if(field.equals("memory[size_MiB]")) {
                    recordValues.put(field, Long.toString(info.getMemory().getSizeMiB()));
                } else if(field.equals("memory[hot_add_enabled]")) {
                    recordValues.put(field, Boolean.toString(info.getMemory().getHotAddEnabled()));
                } else if(field.equals("cpu[hot_remove_enabled]")) {
                    recordValues.put(field, Boolean.toString(info.getCpu().getHotRemoveEnabled()));
                } else if(field.equals("cpu[count]")) {
                    recordValues.put(field, Long.toString(info.getCpu().getCount()));
                } else if(field.equals("cpu[hot_add_enabled]")) {
                    recordValues.put(field, Boolean.toString(info.getCpu().getHotAddEnabled()));
                } else if(field.equals("cpu[cores_per_socket]")) {
                    recordValues.put(field, Long.toString(info.getCpu().getCoresPerSocket()));
                } else {
                    logger.debug(field + " is not a valid field for the " + request.getStructure() + " Structure");
                }
            }
        } else {
            recordValues.put("memory_size_MiB", summary.getMemorySizeMiB().toString());
            recordValues.put("name", summary.getName());
            recordValues.put("cpu_count", summary.getCpuCount().toString());
            recordValues.put("power_state", summary.getPowerState().toString());
            recordValues.put("vm", summary.getVm());
            recordValues.put("guest_os", info.getGuestOS().toString());
            recordValues.put("memory[size_MiB]", Long.toString(info.getMemory().getSizeMiB()));
            recordValues.put("memory[hot_add_enabled]", Boolean.toString(info.getMemory().getHotAddEnabled()));
            recordValues.put("cpu[hot_remove_enabled]", Boolean.toString(info.getCpu().getHotRemoveEnabled()));
            recordValues.put("cpu[count]", Long.toString(info.getCpu().getCount()));
            recordValues.put("cpu[hot_add_enabled]", Boolean.toString(info.getCpu().getHotAddEnabled()));
            recordValues.put("cpu[cores_per_socket]", Long.toString(info.getCpu().getCoresPerSocket()));                
        }
        
        return recordValues;
    }
    
    protected List<VMTypes.Summary> getList(Map<String,ArrayList<String>> parsedQuery) throws BridgeError {
        List<VMTypes.Summary> list;

        try {
            VMTypes.FilterSpec.Builder bldr = new VMTypes.FilterSpec.Builder();
                parsedQuery.forEach((field,value) -> {
                    Set<String> set = new HashSet<String>(value);
                    if (field.equals("filter.folders")) {
                        bldr.setFolders(set);
                    } else if (field.equals("filter.clusters")) {
                        bldr.setClusters(set);
                    } else if (field.equals("filter.power_states")) {
                        Set<PowerTypes.State> powersSet = new HashSet<PowerTypes.State>();
                        if (value.contains("POWERED_ON")) {
                            powersSet.add(PowerTypes.State.POWERED_ON);
                        }
                        if (value.contains("POWERED_OFF")) {
                            powersSet.add(PowerTypes.State.POWERED_OFF);
                        }
                        if (value.contains("SUSPENDED")) {
                            powersSet.add(PowerTypes.State.SUSPENDED);
                        }
                        bldr.setPowerStates(powersSet);
                    } else if (field.equals("filter.hosts")) {
                        bldr.setHosts(set);
                    } else if (field.equals("filter.names")) {
                        bldr.setNames(set);
                    } else if (field.equals("filter.datacenters")) {
                        bldr.setDatacenters(set);
                    } else if (field.equals("filter.resource_pools")) {
                        bldr.setResourcePools(set);
                    } else if (field.equals("filter.vms")) {
                        bldr.setVms(set);
                    }
                });
               
            list = this.vmService.list(bldr.build());
        } catch (Exception e) {
            throw new BridgeError(e);
        }
        return list;
    };
}