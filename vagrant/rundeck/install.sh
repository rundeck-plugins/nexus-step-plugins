#!/usr/bin/env bash

# Exit immediately on error or undefined variable.
set -eu


# Process command line arguments.

if [[ $# -lt 3 ]]
then
    echo >&2 "usage: $0 name IP rundeck_yum_repo"
    exit 2
fi
NAME=$1
IP=$2
RUNDECK_REPO_URL=$3

# Software install
# ----------------


#
# JRE
#
yum -y install java-1.7.0-openjdk

# Rundeck 
#
if [ -n "$RUNDECK_REPO_URL" ]
then
    curl -# --fail -L -o /etc/yum.repos.d/rundeck.repo "$RUNDECK_REPO_URL" || {
        echo "failed downloading rundeck.repo config"
        exit 2
    }
else
    if ! rpm -q rundeck-repo
    then
        rpm -Uvh http://repo.rundeck.org/latest.rpm 
    fi
fi
yum -y --skip-broken install rundeck

# 2.0 uses newer jetty. This should eventually be fixed in the rpm.
sed -i "s/org.mortbay/org.eclipse/g" /etc/rundeck/jaas-loginmodule.conf

# Add ops and dev user logins

cat >> /etc/rundeck/realm.properties <<EOF
dev:dev,dev,user,simple
ops:ops,ops,user,simple
releng:releng,releng,user,simple
EOF
cp /vagrant/rundeck/simple.aclpolicy /etc/rundeck/simple.aclpolicy
chown rundeck:rundeck /etc/rundeck/simple.aclpolicy


#
# Disable the firewall so we can easily access it from any host.
service iptables stop
#

# Install plugins
# ---------------

# nexus-step-plugins

curl -# --fail -L -o /var/lib/rundeck/libext/nexus-step-plugins-1.0.0.jar  "http://dl.bintray.com/ahonor/rundeck-plugins/nexus-step-plugins-1.0.0.jar"

# Jira plugins
curl -# --fail -L -o /var/lib/rundeck/libext/jira-notification-1.0.0.jar 	 "http://dl.bintray.com/ahonor/rundeck-plugins/jira-notification-1.0.0.jar"
curl -# --fail -L -o /var/lib/rundeck/libext/jira-workflow-step-1.0.0.jar  "http://dl.bintray.com/ahonor/rundeck-plugins/jira-workflow-step-1.0.0.jar"


chown rundeck:rundeck  /var/lib/rundeck/libext/*.jar

# Configure rundeck.
# -----------------


#
# Configure the mysql connection and log file storage plugin.
cd /etc/rundeck

cp /vagrant/rundeck/id_rsa* /var/lib/rundeck/.ssh/
chown rundeck:rundeck /var/lib/rundeck/.ssh/

# Update the framework.properties with name
sed -i \
    -e "s/localhost/$NAME/g" \
    -e "s,framework.server.url = .*,framework.server.url = http://$IP:4440,g" \
    -e "s,framework.rundeck.url = .*,framework.rundeck.url = http://$IP:4440,g" \
    framework.properties

sed -i \
    -e "s,grails.serverURL=.*,grails.serverURL=http://$IP:4440,g" \
    rundeck-config.properties

chown rundeck:rundeck framework.properties


# Start up rundeck
# ----------------

# Check if rundeck is running and start it if necessary.
# Checks if startup message is contained by log file.
# Fails and exits non-zero if reaches max tries.

set +e; # shouldn't have to turn off errexit.

source /vagrant/common/functions.sh
success_msg="Connector@"
if ! service rundeckd status
then
    echo "Starting rundeck..."
    (
        exec 0>&- # close stdin
        service rundeckd start 
    ) &> /var/log/rundeck/service.log # redirect stdout/err to a log.

    wait_for_success_msg "$success_msg" /var/log/rundeck/service.log 

fi

echo "Rundeck started."


# Done.
exit $?
