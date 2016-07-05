# DomoBus-Communications-Library
Unofficial java library implementing a Supervisor's communications within a DomoBus network.

Generic library for the communications module of a DomoBus Supervisor,
enabling interactions across the Supervision-level DomoBus network and
local Control-level DomoBus networks.
Provides packets validation, conversion and transmission across both levels.

A crude implementation of a DomoBus Name Server to allow dynamic management
of the Supervision-level network's nodes is included under the tests folder.
A basic Supervisor implementing the library's API calls is also provided, 
it includes a CLI and allows for manual introduction of commands or executing
a list of commands present within a specified file. Supervisors do need a
configuration file to properly operate within the network, therefore a generic
configuration file is left inside the DomoBus Supervisor folder.


This project is based upon the currently available DomoBus specifications and documentation, but is however unaffiliated with the DomoBus technology.

For more information about DomoBus and its components see: http://www.domobus.net/.
