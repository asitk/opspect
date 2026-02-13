#! /bin/sh
#
# test.sh
# Copyright (C) 2016 asitk <asitk@ak-ubuntu>
#
# Distributed under terms of the MIT license.
#


curl -H "Content-Type: application/json" -X POST -d '{"deployment_id":"ec2-dc-01", "customer_id":"1234abcd", "sts":"1466331323000", "ets":"1475231400000"}' http://localhost:9999/service/getdeploymentmarkers > /tmp/out.txt 2>&1 &
curl -H "Content-Type: application/json" -X POST -d '{"deployment_id":"ec2-dc-01", "customer_id":"1234abcd", "sts":"1466331323000", "ets":"1475231400000"}' http://localhost:9999/service/getdeploymentdetail > /tmp/out1.txt 2>&1 &
curl -H "Content-Type: application/json" -X POST -d '{"deployment_id":"ec2-dc-01", "customer_id":"1234abcd", "sts":"1466331323000", "ets":"1475231400000"}' http://localhost:9999/service/getdeploymentsnapshot > /tmp/out2.txt 2>&1 &
curl -H "Content-Type: application/json" -X POST -d '{"deployment_id":"ec2-dc-01", "customer_id":"1234abcd", "cluster_id":"myappcluster", "sts":"1466331323000", "ets":"1475231400000"}' http://localhost:9999/service/getclusterdetail > /tmp/out3.txt 2>&1 &
curl -H "Content-Type: application/json" -X POST -d '{"deployment_id":"ec2-dc-01", "customer_id":"1234abcd", "cluster_id":"myappcluster", "sts":"1466331323000", "ets":"1475231400000"}' http://localhost:9999/service/getclustersnapshot > /tmp/out4.txt 2>&1 &
