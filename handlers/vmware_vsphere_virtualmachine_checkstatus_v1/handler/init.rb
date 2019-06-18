# Require the dependencies file to load the vendor libraries
require File.expand_path(File.join(File.dirname(__FILE__), 'dependencies'))

class VmwareVsphereVirtualmachineCheckstatusV1
  # Include the necessary Java classes to be used for this handler
  include_class java.net.URL
  include_class com.vmware.vim25.mo.Folder
  include_class com.vmware.vim25.mo.InventoryNavigator
  include_class com.vmware.vim25.mo.ServiceInstance
  include_class com.vmware.vim25.mo.Task
  include_class com.vmware.vim25.mo.VirtualMachine
  include_class com.vmware.vim25.ManagedObjectReference

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

    mor = ManagedObjectReference.new
    mor.set_type("Task")
    mor.set_val(@parameters['task_id'])

    task = Task.new(si.get_server_connection, mor)

    begin
      state = task.taskInfo.state.to_s
    rescue Exception => e
      if e.message == "java.lang.RuntimeException: com.vmware.vim25.ManagedObjectNotFound"
        raise StandardError, "Invalid Task Id: Task Id '#{@parameters['task_id']}' could not be found"
      else
        raise e
      end
    end

    if state == "error"
      message = task.taskInfo.error.localizedMessage
    elsif state == "running"
      message = "Progress: #{task.taskInfo.progress.to_s}%"
    else
      message = ""
    end

    <<-RESULTS
    <results>
      <result name="state">#{state}</result>
      <result name="message">#{message}</result>
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