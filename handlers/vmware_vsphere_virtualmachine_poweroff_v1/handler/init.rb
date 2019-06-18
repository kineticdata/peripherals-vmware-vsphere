# Require the dependencies file to load the vendor libraries
require File.expand_path(File.join(File.dirname(__FILE__), 'dependencies'))

class VmwareVsphereVirtualmachinePoweroffV1
  # Include the necessary Java classes to be used for this handler
  include_class java.net.URL
  include_class com.vmware.vim25.mo.Folder
  include_class com.vmware.vim25.mo.InventoryNavigator
  include_class com.vmware.vim25.mo.ServiceInstance
  include_class com.vmware.vim25.mo.Task
  include_class com.vmware.vim25.mo.VirtualMachine

  # Prepare for execution by including java classes and building Hash objects
  # for necessary values, and validating the present state.  This method sets 
  # the following instance variables:
  # * @input_document - A REXML::Document object that represents the input Xml.
  # * @debug_logging_enabled - A Boolean value indicating whether logging should
  #   be enabled or disabled.
  # * @parameters - A Hash of parameter names to parameter values.
  # * @info_values - A Hash of name/value pairs used to define the VMWare 
  #   VSphere server and login credentials.
  #
  # This is a required method that is automatically called by the Kinetic Task
  # Engine.
  #
  # ==== Parameters
  # * +input+ - The String of Xml that was built by evaluating the node.xml 
  #   handler template.
  def initialize(input)
    # Set the input document attribute
    @input_document = REXML::Document.new(input)

    # Determine if debug logging is enabled.
    @debug_logging_enabled = get_info_value(@input_document, 'enable_debug_logging') == 'Yes'
    puts("Logging enabled.") if @debug_logging_enabled

    # Store parameters in the node.xml in a hash attribute named @parameters.
    @parameters = {}
    REXML::XPath.match(@input_document, '/handler/parameters/parameter').each do |node|
      @parameters[node.attribute('name').value] = node.text
    end
    puts("Handler Parameters: #{@parameters.inspect}") if @debug_logging_enabled

    # Store infos in the node.xml in a hash attribute named @info_values.
    @info_values = {}
    REXML::XPath.match(@input_document, '/handler/infos/info').each do |node|
      @info_values[node.attribute('name').value] = node.text
    end
    puts("Connecting to #{@info_values['server_url']} as #{@info_values['username']}.") if @debug_logging_enabled
  end
  
  # Establishes a connection to the VMWare VSphere server and locates the
  # virtual machine that is to be powered off.  Once located, it sends a signal 
  # to power the VM off and waits for a response to signal that it is powering
  # off.
  #
  # This is a required method that is automatically called by the Kinetic Task
  # Engine.
  #
  # ==== Returns
  # An Xml formatted String representing the return variable results.
  def execute()
    # Initialize a service instance, this represents the vSphere server we are
    # connecting too.
    service_instance = ServiceInstance.new(
      URL.new("#{@info_values['server_url']}"),
      "#{@info_values['username']}",
      "#{@info_values['password']}",
      true
    )

    # Attempt to retrieve the virtual machine
    root_folder = service_instance.getRootFolder()
    inventory = InventoryNavigator.new(root_folder)
    vm = inventory.searchManagedEntity("VirtualMachine", @parameters['vm_name'])

    # If we were unable to retrieve the virtual machine
    if vm.nil?
      # Raise an error that the VM with the supplied name can not be found
      raise("There was no virtual machine found with the name #{@parameters['vm_name']}")
    end
    
    begin
      # Attempt to power off the virtual machine
      task = vm.powerOffVM_Task()
      # Wait to see if the task raises an exception
      task.waitForMe()
    rescue NativeException => exception
      # If there was an invalid power state exception
      if exception.cause.is_a? com.vmware.vim25.InvalidPowerState
        # Raise a more intelligent error message
        raise "Unable to power off a Virtual Machine that is not powered on."
      else
        # Re-raise the original error
        raise
      end
    end
    
    # Build and return the results xml that will be returned by this handler.
    results = "<results/>"
    puts("Results: \n#{results}") if @debug_logging_enabled
    return results
  end

  ##############################################################################
  # General handler utility functions
  ##############################################################################

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

  # This is a sample helper method that illustrates one method for retrieving
  # values from the input document.  As long as your node.xml document follows
  # a consistent format, these type of methods can be copied and reused between
  # handlers.
  def get_info_value(document, name)
    # Retrieve the XML node representing the desird info value
    info_element = REXML::XPath.first(document, "/handler/infos/info[@name='#{name}']")
    # If the desired element is nil, return nil; otherwise return the text value of the element
    info_element.nil? ? nil : info_element.text
  end
end