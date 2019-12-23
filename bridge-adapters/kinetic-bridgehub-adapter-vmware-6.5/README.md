# kinetic-bridgehub-adapter-vmware

## Structures

### vCenter
- /vcenter/vm
- /vcenter/resource-pool
- /vcenter/network
- /vcenter/folder
- /vcenter/datastore
- /vcenter/cluster

### content
- /com/vmware/content/libraryx

## Fields

Structure | Fields
--- | ---
/vcenter/vm | memory_size_MiB<br>name<br>cpu_count<br>power_state<br>vm<br>guest_os<br>memory[size_MiB]<br>memory[hot_add_enabled]<br>cpu[hot_remove_enabled]<br>cpu[count]<br>cpu[hot_add_enabled]<br>cpu[cores_per_socket]
/vcenter/resource-pool | name<br>resource_pool
/vcenter/network | name<br>network<br>type
/vcenter/folder | name<br>folder<br>type
/vcenter/datastore | name<br>datastore<br>type<br>free_space<br>capacity<br>accessible<br>multiple_host_access<br>thin_provisioning_supported
/vcenter/cluster | name<br>cluster<br>ha_enabled<br>drs_enable<br>resource_pool
/com/vmware/content/library | name<br>id<br>creation_time<br>description<br>last_modified_time<br>server_guid<br>type<br>version<br>publish_info[authentication_method]

## Qualifications

- filter.folders
- filter.clusters
- filter.power_status
- filter.filter.hosts
- filter.names
- filter.datacenters
- filter.resource_pools
- filter.vms
- filter.datastores
- filter.types
- filter.parent_folders
- filter.type
- filter.networks
- filter.parent_resource_pools
- library_id

