#!/usr/bin/env bash

if [ $# -ne 2 ]
then
    echo >&2 "usage: add-project IP project"
    exit 1
fi
IP=$1
PROJECT=$2

echo "Creating project $PROJECT..."
props=()
# Model source for the nexus plugin
modelSourceDir=/var/rundeck/projects/${PROJECT}/etc/nexus
props=(${props[*]} --resources.source.1.config.directory=${modelSourceDir})
props=(${props[*]} --resources.source.1.type=directory)
props=(${props[*]} --resources.source.2.config.file=/var/rundeck/projects/${PROJECT}/etc/resources.xml)
props=(${props[*]} --resources.source.2.config.generateFileAutomatically=true)
props=(${props[*]} --resources.source.2.config.includeServerNode=true)
props=(${props[*]} --resources.source.2.type=file)

# Nexus configuration
user=admin password=admin123 url=http://${IP}:8081
props=(${props[*]} --project.plugin.WorkflowStep.nexus-deliver-artifact-step.nexus=${url})
props=(${props[*]} --project.plugin.WorkflowStep.nexus-deliver-artifact-step.nexusUser=${user})
props=(${props[*]} --project.plugin.WorkflowStep.nexus-deliver-artifact-step.nexusPassword=${password})
props=(${props[*]} --project.plugin.WorkflowStep.nexus-exists-artifact-step.nexus=${url})
props=(${props[*]} --project.plugin.WorkflowStep.nexus-exists-artifact-step.nexusUser=${user})
props=(${props[*]} --project.plugin.WorkflowStep.nexus-exists-artifact-step.nexusPassword=${password})
props=(${props[*]} --project.plugin.WorkflowStep.nexus-register-delivery-step.nexus=${url})
props=(${props[*]} --project.plugin.WorkflowStep.nexus-register-delivery-step.modelDirPath=${modelSourceDir})
# Jira configuration (placeholders. replace values with your site specific ones)
props=(${props[*]} --project.plugin.WorkflowStep.JIRA-Issue-Exists.url=value)
props=(${props[*]} --project.plugin.WorkflowStep.JIRA-Issue-Exists.login=value)
props=(${props[*]} --project.plugin.WorkflowStep.JIRA-Issue-Exists.password=value)
props=(${props[*]} --project.plugin.Notification.JIRA.url=value)
props=(${props[*]} --project.plugin.Notification.JIRA.login=value)
props=(${props[*]} --project.plugin.Notification.JIRA.password=value)

# Create the project as the rundeck user to ensure proper permissions.
su - rundeck -c "rd-project -a create -p $PROJECT ${props[*]}"

# Run simple commands to sanity check the project.
su - rundeck -c "dispatch -p $PROJECT" > /dev/null
# Fire off a command.
su - rundeck -c "dispatch -p $PROJECT -f -- whoami"

cp /vagrant/rundeck/readme.md.template /var/rundeck/projects/$PROJECT/readme.md
sed -i \
    -e "s,@PROJECT@,$PROJECT,g" \
    -e "s,@NEXUS_URL@,http://$IP:8081/nexus,g" \
    -e "s,@JENKINS_URL@,http://$IP:8080,g" \
    /var/rundeck/projects/$PROJECT/readme.md


chown rundeck:rundeck /var/rundeck/projects/$PROJECT/readme.md

cp /vagrant/rundeck/jobs.yaml /tmp
chown rundeck:rundeck /tmp/jobs.yaml

su - rundeck -c "rd-jobs load -p $PROJECT -F yaml -f /tmp/jobs.yaml"


echo "Project $PROJECT created."
