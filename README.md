## Toolchain Example

The vagrant sub folder contains a multi-machine configuration using Nexus, Jenkins and Rundeck as a pipeline infrastructure.

See some [screenshots](vagrant/docs/index.md).

## Configuration properties
The plugin requires several configuration properties defined so it knows how to access the nexus server.
Rundeck can be configured globally via the [framework.properties](http://rundeck.org/docs/administration/configuration-file-reference.html#framework.properties) or per project using
[project.properties](http://rundeck.org/docs/administration/configuration-file-reference.html#project.properties).


Nexus: Nexus server URL. eg, http://repository.example.com:8081

* configure project: project.plugin.WorkflowStep.nexus-deliver-artifact-step.nexus=value
* configure framework: framework.plugin.WorkflowStep.nexus-deliver-artifact-step.nexus=value

Nexus User: Nexus login name

* configure project: project.plugin.WorkflowStep.nexus-deliver-artifact-step.nexusUser=value
* configure framework: framework.plugin.WorkflowStep.nexus-deliver-artifact-step.nexusUser=value

Nexus Password: Nexus login password

* configure project: project.plugin.WorkflowStep.nexus-deliver-artifact-step.nexusPassword=value
* configure framework: framework.plugin.WorkflowStep.nexus-deliver-artifact-step.nexusPassword=value

Model Source Directory: Directory where model source data is stored.

* configure project: project.plugin.WorkflowStep.nexus-register-delivery-step.modelDirPath=value
* configure framework: framework.plugin.WorkflowStep.nexus-register-delivery-step.modelDirPath=value
