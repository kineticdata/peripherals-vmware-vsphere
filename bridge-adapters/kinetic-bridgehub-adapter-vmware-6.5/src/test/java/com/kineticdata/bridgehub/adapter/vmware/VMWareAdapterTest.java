/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.kineticdata.bridgehub.adapter.vmware;

import com.kineticdata.bridgehub.adapter.BridgeAdapterTestBase;
import com.kineticdata.bridgehub.adapter.BridgeRequest;
import com.kineticdata.bridgehub.adapter.Count;
import com.kineticdata.bridgehub.adapter.Record;
import com.kineticdata.bridgehub.adapter.RecordList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author chad.rehm
 */
public class VMWareAdapterTest extends BridgeAdapterTestBase {
    
    @Override
    public String getConfigFilePath() {
        return "src/test/resources/bridge-config.yml";
    }
    
    @Override
    public Class getAdapterClass() {
        return VMWareAdapter.class;
    }
     
    /*
    Test count method
    */
    @Test
    public void count() throws Exception {
        BridgeRequest request = new BridgeRequest();
        
        List<String> structures = new ArrayList<>(Arrays.asList("/vcenter/vm", 
            "/vcenter/resource-pool", "/vcenter/network", "/vcenter/folder",
            "/vcenter/datastore", "/com/vmware/content/library"));
        
        for(String structure : structures){
            request.setStructure(structure);
            request.setQuery("");
        
            Count count = getAdapter().count(request);
            
            assertTrue("The count for " + structure + " should be at least 1", (int)count.getValue() >= 1);
        }
    }
    
        /*
    Test count method
    */
    @Test
    public void countWithQuery() throws Exception {
        BridgeRequest request = new BridgeRequest();
        
        List<String> structures = new ArrayList<>(Arrays.asList("/vcenter/vm"));
        
        for(String structure : structures){
            request.setStructure(structure);
            request.setQuery("?filter.power_states=POWERED_ON");
        
            Count count = getAdapter().count(request);
            assertTrue("The count for " + structure + " should be at least 1", (int)count.getValue() >= 1);
        }
    }
    
    /*
    Test count method
    */
    @Test
    public void retrieve() throws Exception {
        BridgeRequest request = new BridgeRequest();
                
        List<String> structures = new ArrayList<>(Arrays.asList("/vcenter/vm", 
            "/vcenter/resource-pool", "/vcenter/network", "/vcenter/folder",
            "/vcenter/datastore", "/com/vmware/content/library"));
        List<String> queries = new ArrayList<>(Arrays.asList("?filter.vms=vm-86", 
                "?filter.resource_pools=resgroup-118", "?filter.networks=network-31",
                "?filter.folders=group-d1","?filter.datastores=datastore-120",
                "?library_id=6f0ab7e1-38fe-43a4-a007-caf556b51d32"));
        
        for(int x = 1; x < structures.size(); x++){
            
            request.setStructure(structures.get(x));
            request.setQuery(queries.get(x));

            List<String> list = Arrays.asList("name", "cpu_count");

            request.setFields(list);
            Record record = getAdapter().retrieve(request);
            Map<String,Object> recordMap = record.getRecord();

            assertNotNull("The retrieve for " + structures.get(x) + " should not be null",recordMap);
        }
    }
    
    /*
    Test count method
    */
    @Test
    public void search() throws Exception {
        BridgeRequest request = new BridgeRequest();
        
        List<String> structures = new ArrayList<>(Arrays.asList("/vcenter/vm", 
            "/vcenter/resource-pool", "/vcenter/network", "/vcenter/folder",
            "/vcenter/datastore", "/com/vmware/content/library"));
        
        for(String structure : structures){
            request.setStructure(structure);
            request.setQuery("");
            
            List<String> list = Arrays.asList("name","last_modified_time", "publish_info[authentication_method]");
            request.setFields(list);
            
            RecordList recordList = getAdapter().search(request);
            List<Record> records = recordList.getRecords();
            assertTrue("The search for " + structure + 
                " should be returning at least 1 record.", records.size() >= 1);
        }
    }
    
        /*
    Test count method
    */
    @Test
    public void searchNoFields() throws Exception {
        BridgeRequest request = new BridgeRequest();
        
        List<String> structures = new ArrayList<>(Arrays.asList("/vcenter/vm", 
            "/vcenter/resource-pool", "/vcenter/network", "/vcenter/folder",
            "/vcenter/datastore", "/com/vmware/content/library"));
        
        for(String structure : structures){
            request.setStructure(structure);
            request.setQuery("");
            
            request.setFields(null);
            
            RecordList recordList = getAdapter().search(request);
            List<Record> records = recordList.getRecords();
            
            assertTrue("The search for " + structure + 
                " should be returning at least 1 record.", records.size() >= 1);
        }
    }
    
    /*
        Test count method
    */
    @Test
    public void sortOrder() throws Exception {
        BridgeRequest request = new BridgeRequest();
        
        List<String> structures = new ArrayList<>(Arrays.asList("/vcenter/vm", 
            "/vcenter/resource-pool", "/vcenter/network", "/vcenter/folder",
            "/vcenter/datastore", "/com/vmware/content/library"));
        Map<String,String> metadata = new LinkedHashMap<String,String>();
        metadata.put("order","<%=field[\"name\"]%>:ASC");
        
        for(String structure : structures){
            request.setMetadata(metadata);
            request.setStructure(structure);
            request.setQuery("");
            
            List<String> list = Arrays.asList("name","last_modified_time", "publish_info[authentication_method]");
            request.setFields(list);
            
            RecordList recordList = getAdapter().search(request);
            List<Record> records = recordList.getRecords();
            System.out.println("\n\n\n"+structure);
            for(Record record: records) {
                System.out.println(record.getRecord().get("name"));
            }
            assertTrue("The search for " + structure + 
                " should be returning at least 1 record.", records.size() >= 1);
        }
    }
}
