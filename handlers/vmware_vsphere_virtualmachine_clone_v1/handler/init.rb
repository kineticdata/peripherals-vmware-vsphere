# Require the dependencies file to load the vendor libraries
require File.expand_path(File.join(File.dirname(__FILE__), 'dependencies'))

class VmwareVsphereVirtualmachineCloneV1
  # Include the necessary Java classes to be used for this handler
  include_class java.net.URL
  include_class com.vmware.vim25.mo.Folder
  include_class com.vmware.vim25.mo.InventoryNavigator
  include_class com.vmware.vim25.mo.ServiceInstance
  include_class com.vmware.vim25.mo.Task
  include_class com.vmware.vim25.mo.VirtualMachine
  include_class com.vmware.vim25.mo.HostSystem
  include_class com.vmware.vim25.VirtualMachineCloneSpec
  include_class com.vmware.vim25.VirtualMachineRelocateSpec
  include_class com.vmware.vim25.VirtualMachineRelocateTransformation

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

    datacenter = si.getSearchIndex.findByInventoryPath(@parameters['datacenter_name'])
    folder = datacenter.getVmFolder

    # Finding the resource pool
    if @parameters['resource_pool'] == ""
      hs = HostSystem.new(si.getServerConnection, vm.getRuntime.getHost)
      resource_pool = hs.getParent.getResourcePool
    else
      resource_pool = inventory.searchManagedEntity("ResourcePool",@parameters['resource_pool'])
    end
    to_bool = {"true" => true, "false" => false}

    clone_spec = VirtualMachineCloneSpec.new
    relocate_spec = VirtualMachineRelocateSpec.new
    relocate_spec.transform = VirtualMachineRelocateTransformation::sparse
    relocate_spec.pool = resource_pool.getMOR
    clone_spec.setLocation(relocate_spec)
    clone_spec.setPowerOn(to_bool[@parameters['power_on']])
    clone_spec.setTemplate(to_bool[@parameters['mark_as_template']])

    begin
      # Sending the command to start the clone
      clone = vm.cloneVM_Task(folder, @parameters['clone_name'], clone_spec)
      sleep(5)
      if clone.getTaskInfo.getState.to_s == "error"
        raise StandardError, clone.getTaskInfo.getError.getLocalizedMessage
      end
    rescue NativeException
      raise
    end

    <<-RESULTS
    <results>
      <result name="task_id">#{clone.get_mor.get_val}</result>
    </results>
    RESULTS
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