#!/usr/bin/env bash

# Exit immediately on error or undefined variable.
set -eu


# Process command line arguments.
# ----------------
if [[ $# -ne 1 ]]
then
    echo >&2 "usage: $0 IP"
    exit 2
fi
RUNDECK_IP=$1

# Software install
# ----------------

#
# JRE
#
yum -y install java-1.7.0-openjdk

#
# Jenkins
#
curl -# --fail -L -o /etc/yum.repos.d/jenkins.repo http://pkg.jenkins-ci.org/redhat/jenkins.repo || {
    echo "failed downloading jenkins.repo config"
    exit 3
}
rpm --import http://pkg.jenkins-ci.org/redhat/jenkins-ci.org.key

yum -y install jenkins

mkdir -p /var/lib/jenkins/examples/simple
cp /vagrant/jenkins/simple-1.0.0.war /var/lib/jenkins/examples/simple
chown -R jenkins:jenkins /var/lib/jenkins/examples/simple
echo "Simple war file: $(ls /var/lib/jenkins/examples/simple)"

# Configure jenkins.
# -----------------
if [[ ! -f /var/lib/jenkins/jenkins.model.JenkinsLocationConfiguration.xml ]]
then
cat >/var/lib/jenkins/jenkins.model.JenkinsLocationConfiguration.xml <<EOF
<?xml version='1.0' encoding='UTF-8'?>
<jenkins.model.JenkinsLocationConfiguration>
  <adminAddress>address not configured yet &lt;nobody@nowhere&gt;</adminAddress>
  <jenkinsUrl>http://$RUNDECK_IP:8080/</jenkinsUrl>
</jenkins.model.JenkinsLocationConfiguration>
EOF
chown jenkins:jenkins /var/lib/jenkins/jenkins.model.JenkinsLocationConfiguration.xml
fi

# Start up jenkins
# ----------------

source /vagrant/common/functions.sh
success_msg="Jenkins is fully up and running"
if ! service jenkins status
then
    service jenkins start 
    wait_for_success_msg "$success_msg" /var/log/jenkins/jenkins.log
fi

echo "Jenkins started."
service iptables stop


# Install the rundeck plugin using the jenkins CLI.
curl -s --fail -o jenkins-cli.jar http://localhost:8080/jnlpJars/jenkins-cli.jar

if test -f /vagrant/rundeck.hpi ; then
    java -jar jenkins-cli.jar -s http://localhost:8080 install-plugin \
        /vagrant/rundeck.hpi
else
    java -jar jenkins-cli.jar -s http://localhost:8080 install-plugin \
    	http://updates.jenkins-ci.org/download/plugins/rundeck/3.0/rundeck.hpi
fi

# Configure rundeck plugin.
# sed "s/localhost/$RUNDECK_IP/g" /vagrant/jenkins/org.jenkinsci.plugins.rundeck.RundeckNotifier.xml > /var/lib/jenkins/org.jenkinsci.plugins.rundeck.RundeckNotifier.xml
# chown jenkins:jenkins /var/lib/jenkins/org.jenkinsci.plugins.rundeck.RundeckNotifier.xml
echo >&2 "You must configure the Rundeck plugin after Jenkins starts"

# Install the git plugin
java -jar jenkins-cli.jar -s http://localhost:8080 install-plugin \
   https://updates.jenkins-ci.org/download/plugins/git/2.2.1/git.hpi

# Load job definiton.
java -jar jenkins-cli.jar -s http://localhost:8080 create-job simple \
	< /vagrant/jenkins/simple.xml

# Restart it to finilize the install.
java -jar jenkins-cli.jar -s http://localhost:8080 safe-restart


# Done.
exit $?
