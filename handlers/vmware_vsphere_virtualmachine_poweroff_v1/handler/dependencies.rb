# Ensure the JRuby java library is loaded
require 'java'

# Determine the path to the handler
handler_path = File.expand_path(File.dirname(__FILE__))

# Attempt to load a class from the Dom4j package
begin
  org.dom4j.Document
# If JRuby was unable to load the Document class.
rescue NameError
  # Require the java package.
  require File.join(handler_path, 'vendor', 'dom4j-1.6.1.jar')
end

# Attempt to load a class from the vijava package
begin
  com.vmware.vim25.mo.ServiceInstance
# If JRuby was unable to load the ServiceInstance class.
rescue NameError
  # Require the java package.
  require File.join(handler_path, 'vendor', 'vijava2120100824.jar')
end

# We can't check the version since the vijava package does not set an 
# Implementation-Version value in their manifest file.  The need for jar file
# consistency has been documented in the README file.