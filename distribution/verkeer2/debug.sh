#!/bin/bash
. ./configuration.sh

cd $glassfishInstall/bin/  
cat /dev/null > $glassfishInstall/domains/$domainName/logs/server.log
./asadmin start-domain --debug $domainName
cat $glassfishInstall/domains/$domainName/logs/server.log | grep 'done'
cd -
