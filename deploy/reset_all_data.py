#! /usr/bin/env python
# -*- coding: utf-8 -*-
# vim:fenc=utf-8
#
# Copyright © 2016 asitk <asitk@ak-ubuntu>
#

import os
import sys
import time
from subprocess import call

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(SCRIPT_DIR)

def yes_or_no(question):
    reply = str(input(question + ' (y/n): ')).lower().strip()
    if reply[0] == 'y':
        return True
    if reply[0] == 'n':
        return False
    else:
        return yes_or_no("please enter ")

def main(argv):
    print("cleaning kafka")
    call(["/opt/tools/kafka/clean.sh"])
    #call(['sudo', 'service', 'zookeeper', 'restart'])
    #print("Waiting 10 secs")
    #time.sleep(10)
    call(['sudo', 'service', 'kafka', 'restart'])
    call(['tail', '/opt/tools/kafka/logs/server.log'])

    proceed = yes_or_no('Do you wish to clean kairos')
    if proceed is True:
        call(['sudo', 'service', 'kairosdb', 'stop'])
        call(['cqlsh', '-f', os.path.join(REPO_ROOT, 'models/kafka/consumer/clean_kairos.cql')])
        call(['sudo', 'service', 'kairosdb', 'start'])

    proceed = yes_or_no('Do you wish to reset schema')
    if proceed is True:
        call(['cqlsh', '-f', os.path.join(REPO_ROOT, 'models/kafka/consumer/schema.cql')])
        call(['sudo', 'service', 'cassandra', 'restart'])
        print("Waiting 5 secs")
        time.sleep(5)
        call(['tail', '/var/log/cassandra/system.log'])
        print("Updating node details")
        call([os.path.join(REPO_ROOT, 'deploy/update-kafka.sh')])

    call(['sudo', 'service', 'spark', 'restart'])

    # format hdfs
    # Namenode format
        # sudo service hadoop stop
        # sudo rm -rf /app/hadoop/tmp/dfs/
        # hadoop namenode -format
        # sudo service hadoop start
    # restart machine

if __name__ == "__main__":
    main(sys.argv)
