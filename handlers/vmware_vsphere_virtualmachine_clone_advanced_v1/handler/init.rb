# Require the dependencies file to load the vendor libraries
require File.expand_path(File.join(File.dirname(__FILE__), 'dependencies'))

class VmwareVsphereVirtualmachineCloneAdvancedV1
  # Include the necessary Java classes to be used for this handler
  java_import java.net.URL
  java_import java.lang.Long
  java_import com.vmware.vim25.mo.Folder
  java_import com.vmware.vim25.mo.InventoryNavigator
  java_import com.vmware.vim25.mo.ServiceInstance
  java_import com.vmware.vim25.mo.Task
  java_import com.vmware.vim25.mo.VirtualMachine
  java_import com.vmware.vim25.mo.HostSystem
  java_import com.vmware.vim25.mo.DistributedVirtualPortgroup
  java_import com.vmware.vim25.mo.DistributedVirtualSwitch
  java_import com.vmware.vim25.mo.StoragePod
  java_import com.vmware.vim25.mo.StorageResourceManager
  java_import com.vmware.vim25.StorageDrsPodSelectionSpec
  java_import com.vmware.vim25.StoragePlacementSpec
  java_import com.vmware.vim25.VirtualMachineConfigSpec
  java_import com.vmware.vim25.ResourceAllocationInfo
  java_import com.vmware.vim25.VirtualMachineCloneSpec
  java_import com.vmware.vim25.VirtualMachineRelocateSpec
  java_import com.vmware.vim25.VirtualMachineRelocateTransformation
  java_import com.vmware.vim25.CustomizationFixedIp
  java_import com.vmware.vim25.VirtualEthernetCardDistributedVirtualPortBackingInfo
  java_import com.vmware.vim25.DistributedVirtualSwitchPortConnection
  java_import com.vmware.vim25.VirtualDeviceConnectInfo
  java_import com.vmware.vim25.VirtualDeviceConfigSpec
  java_import com.vmware.vim25.VirtualDeviceConfigSpecOperation
  java_import com.vmware.vim25.VirtualDisk
  java_import com.vmware.vim25.VirtualDiskFlatVer2BackingInfo
  java_import com.vmware.vim25.VirtualDeviceConfigSpecFileOperation

  def initialize(input)
    # Set the input document attribute
    @input_document = REXML::Document.new(input)

    # Store the info values in a Hash of info names to values.
    @info_values = {}
    REXML::XPath.each(@input_document,"/handler/infos/info") { |item|
      @info_values[item.attributes['name']] = item.text
    }

    @enable_debug_logging = @info_values['enable_debug_logging'] == 'Yes'

    # Retrieve all of the handler parameters and store them in a hash attribute
    # named @parameters.
    @parameters = {}
    REXML::XPath.match(@input_document, '/handler/parameters/parameter').each do |node|
      # Associate the attribute name to the String value (stripping leading and
      # trailing whitespace)
      @parameters[node.attribute('name').value] = node.text.to_s.strip
    end

    if @parameters["customization_spec"] == "" && @parameters["ip_address"] != ""
      raise StandardError, "Invalid Input Params: If you want to change the Ip Address, also include a Customization Spec."
    end
  end
  
  # This is a required method that is automatically called by the Kinetic Task
  # Engine.
  def execute()
    # Initialize a service instance, this represents the vSphere server we are
    # connecting too.
    si = ServiceInstance.new(
      URL.new("#{@info_values['server_url']}"),
      "#{@info_values['username']}",
      "#{@info_values['password']}",
      true
    )

    # Attempt to retrieve the virtual machine
    root_folder = si.getRootFolder()
    inventory = InventoryNavigator.new(root_folder)
    vm = inventory.searchManagedEntity("VirtualMachine", @parameters['template_name'])

    # If we were unable to retrieve the virtual machine
    if vm.nil?
      # Raise an error that the VM with the supplied name can not be found
      raise("There was no virtual machine found with the name #{@parameters['template_name']}")
    end

    # Retrieving the Host System if it is needed for determining either the
    # Resource Pool or the Datacenter
    if @parameters['resource_pool'] == "" || @parameters['datacenter_name'] == ""
      hs = HostSystem.new(si.getServerConnection, vm.getRuntime.getHost)
    end

    # Retrieving the Cluster object
    if @parameters['cluster'] != ""
      clusterComputeResource = inventory.searchManagedEntity("ClusterComputeResource", @parameters['cluster']);
      defaultPool = clusterComputeResource.getResourcePool
    end

    # Setting the Resource Pool based on the inputted Resource Pool and Cluster
    resource_pool = nil
    if @parameters['resource_pool'] == "" && @parameters['cluster'] != ""
      # Setting the Resource Pool to the default pool of a cluster is passed and
      # a pool is not
      resource_pool = defaultPool
    elsif @parameters['resource_pool'] != "" && @parameters['cluster'] != ""
      # Iterate through all the clusters resource pools to make sure the passed
      # pool is a child of the cluster
      for pool in defaultPool.getResourcePools
        if pool.name == @parameters['resource_pool']
          resource_pool = pool
        end
      end
      if resource_pool.nil?
        raise StandardError, "The inputted Resource Pool '#{@parameters['resource_pool']}' is not a child of the inputted Cluster '#{@parameters['cluster']}'"
      end
    elsif @parameters['resource_pool'] != "" && @parameters['cluster'] == ""
      # If a Resource Pool name is passed and a Cluster is not, just grab the 
      # pool based on the name
      resource_pool = inventory.searchManagedEntity("ResourcePool",@parameters['resource_pool'])
    else
      # If nothing else, grab the Host System Resource Pool
      resource_pool = hs.getParent.getResourcePool
    end

    if @parameters['datacenter_name'] == ""
      datacenter = hs.parent.parent.parent
    else
      datacenter = si.getSearchIndex.findByInventoryPath(@parameters['datacenter_name'])
    end

    customization_spec = nil
    if @parameters['customization_spec'] != ""
      customizationSpecManager = si.getCustomizationSpecManager
      customization_spec = customizationSpecManager.getCustomizationSpec(@parameters['customization_spec'])

      if @parameters['ip_address'] != ""
        ipAddress = CustomizationFixedIp.new
        ipAddress.ipAddress = @parameters["ip_address"]
        customization_spec.getSpec.getNicSettingMap[0].getAdapter.ip = ipAddress
        customization_spec.getSpec.getNicSettingMap[0].getAdapter.gateway = [@parameters['gateway']] if @parameters['gateway'].to_s != ""
        customization_spec.getSpec.getNicSettingMap[0].getAdapter.subnet_mask = @parameters['subnet_mask'] if @parameters['subnet_mask'].to_s != ""
      end
    end

    if @parameters['folder'] != ""
      folder = inventory.searchManagedEntity("Folder",@parameters['folder'])
    else
      folder = datacenter.getVmFolder
    end

    to_bool = {"true" => true, "false" => false}

    clone_spec = VirtualMachineCloneSpec.new
    relocate_spec = VirtualMachineRelocateSpec.new
    relocate_spec.transform = VirtualMachineRelocateTransformation::sparse
    relocate_spec.pool = resource_pool.getMOR
    clone_spec.setLocation(relocate_spec)
    clone_spec.customization = customization_spec.spec if customization_spec != nil
    clone_spec.setPowerOn(to_bool[@parameters['power_on']])
    clone_spec.setTemplate(to_bool[@parameters['mark_as_template']])

    config_spec = nil
    if @parameters['memory_in_mb'] != "" || @parameters['number_of_cpus'] != "" || @parameters['port_group'] != ""
      config_spec = VirtualMachineConfigSpec.new
    end

    if @parameters['memory_in_mb'] != ""
      config_spec.memory_mb = Long.new(@parameters['memory_in_mb'])
    end

    if @parameters['number_of_cpus'] != ""
      config_spec.numCPUs = @parameters['number_of_cpus'].to_i
    end

    if @parameters['port_group'] != ""
      port_group = inventory.searchManagedEntity("DistributedVirtualPortgroup", @parameters['port_group'])

      virtual_ethernet_card = VirtualEthernetCardDistributedVirtualPortBackingInfo.new
      virtual_ethernet_card.port = DistributedVirtualSwitchPortConnection.new
      virtual_switch = DistributedVirtualSwitch.new(si.getServerConnection, port_group.config.distributed_virtual_switch)
      virtual_ethernet_card.port.switchUuid = virtual_switch.uuid
      virtual_ethernet_card.port.portgroup_key = port_group.config.key

      connectable = VirtualDeviceConnectInfo.new
      connectable.start_connected = true
      connectable.allow_guest_control = true
      connectable.connected = true

      device_change = VirtualDeviceConfigSpec.new
      for device in vm.config.hardware.device
        if device.device_info.label.downcase.include? "network"
          device_change.device = device
          break
        end
      end
      device_change.device.connectable = connectable
      device_change.operation = VirtualDeviceConfigSpecOperation::edit
      device_change.device.backing = virtual_ethernet_card

      config_spec.device_change = [device_change]
    end

    # Setting up the device change for adding new disks to a VM
    controller_key = 1000
    key = 2000
    unit_number = 0
    file_path = ""
    for device in vm.config.hardware.device
      puts device.device_info.label
      if device.device_info.label.downcase.include? "hard disk 1"
        controller_key = device.controllerKey
        key = device.key
        unit_number = device.unit_number
        file_path = device.backing.fileName
        break
      end
    end

    device_change = VirtualDeviceConfigSpec.new
    disk = VirtualDisk.new

    disk_backing = VirtualDiskFlatVer2BackingInfo.new
    disk_backing.setFileName(file_path.gsub("\.vmdk","_2.vmdk"))
    disk_backing.setDiskMode("persistent")

    disk.setControllerKey(controller_key.to_i + 1);
    disk.setUnitNumber(unit_number.to_i + 1);
    disk.setBacking(disk_backing);
    disk.setCapacityInKB(5 * 1024 * 1024);
    disk.setKey(-1);

    device_change.setOperation(VirtualDeviceConfigSpecOperation::add) 
    device_change.setFileOperation(VirtualDeviceConfigSpecFileOperation::create)
    device_change.setDevice(disk)

    append_to_java_array(config_spec.device_change, device_change)

    if @parameters['primary_disk_in_gb'] != ""
      device_change = VirtualDeviceConfigSpec.new
      disk = nil
      for device in vm.config.hardware.device
        if device.device_info.label.downcase.include? "hard disk"
          device_change.device = device
          disk = device
          break
        end
      end

      device_change.operation = VirtualDeviceConfigSpecOperation::edit
      device_change.device.backing = virtual_ethernet_card

      # 1GB = 1048576 KB
      begin
        resized_int = @parameters['primary_disk_in_gb'].to_i
      rescue

      end
      resize_in_kb = 1048576 * resized_int
      puts "#{resized_int}GB is equal to #{resize_in_kb}KB"
      device.capacity_in_kb = resize_in_kb

      if config_spec.device_change.nil?
        config_spec.device_change = append_to_java_array(config_spec.device_change, device_change)
      else
        config_spec.device_change = append_to_java_array(config_spec.device_change, device_change)
      end
    end

    if !config_spec.nil?
      clone_spec.config = config_spec
    end

    datastore = nil
    if @parameters['datastore'] != ""
      datastore = InventoryNavigator.new(datacenter.getDatastoreFolder).searchManagedEntity("Datastore",@parameters['datastore'])
    end

    if @parameters['datastore'] != "" && datastore == nil
      # If datastore is still nil, it likely is being deployed to a datastore
      # cluster, which needs a special clone process
      relocate_spec.datastore = datastore.getMOR if datastore != nil
      datastore_cluster = InventoryNavigator.new(root_folder).searchManagedEntity("StoragePod","Primary_Lab")

      pod_selection_spec = StorageDrsPodSelectionSpec.new
      pod_selection_spec.storage_pod = datastore_cluster.getMOR

      srm_mor = si.getServiceContent.getStorageResourceManager
      storage_resource_manager = StorageResourceManager.new(si.getServerConnection, srm_mor)

      storage_placement_spec = StoragePlacementSpec.new
      storage_placement_spec.pod_selection_spec = pod_selection_spec
      storage_placement_spec.folder = folder.getMOR
      storage_placement_spec.vm = vm.getMOR
      storage_placement_spec.clone_spec = clone_spec
      storage_placement_spec.clone_name = @parameters['clone_name']
      storage_placement_spec.type = "clone"

      store_placement_result = storage_resource_manager.recommendDatastores(storage_placement_spec)
      recommendation_key = store_placement_result.recommendations[0].key

      begin
        # Sending the command to start the clone
        clone = storage_resource_manager.applyStorageDrsRecommendation_Task([recommendation_key])
        sleep(5)
        if clone.getTaskInfo.getState.to_s == "error"
          raise StandardError, clone.getTaskInfo.getError.getLocalizedMessage
        end
      rescue NativeException => ex
        raise
      end
    else
      # If not deploying to a datastore cluster, do the regular clone process
      begin
        # Sending the command to start the clone
        clone = vm.cloneVM_Task(folder, @parameters['clone_name'], clone_spec)
        sleep(5)
        if clone.getTaskInfo.getState.to_s == "error"
          raise StandardError, clone.getTaskInfo.getError.getLocalizedMessage
        end
      rescue NativeException => ex
        raise
      end
    end

    <<-RESULTS
    <results>
      <result name="task_id">#{clone.get_mor.get_val}</result>
    </results>
    RESULTS
  end

  def append_to_java_array(java_array,new_item)
    ruby_list = []
    if !java_array.nil?
      for i in 0...java_array.size
        ruby_list.push(java_array[i])
      end
    end

    ruby_list.push(new_item)
    return ruby_list
  end

  # This is a template method that is used to escape results values (returned in
  # execute) that would cause the XML to be invalid.  This method is not
  # necessary if values do not contain character that have special meaning in
  # XML (&, ", <, and >), however it is a good practice to use it for all return
  # variable results in case the value could include one of those characters in
  # the future.  This method can be copied and reused between handlers.
  def escape(string)
    # Globally replace characters based on the ESCAPE_CHARACTERS constant
    string.to_s.gsub(/[&"><]/) { |special| ESCAPE_CHARACTERS[special] } if string
  end
  # This is a ruby constant that is used by the escape method
  ESCAPE_CHARACTERS = {'&'=>'&amp;', '>'=>'&gt;', '<'=>'&lt;', '"' => '&quot;'}

end