#! /bin/sh
#
# update-kafka.sh
# Copyright (C) 2016 asitk <asitk@ak-ubuntu>
#
# Distributed under terms of the MIT license.
#

/opt/tools/kafka/bin/kafka-topics.sh  --create --zookeeper localhost:2181 --replication-factor 1 --partition 1 --topic log
/opt/tools/kafka/bin/kafka-topics.sh  --create --zookeeper localhost:2181 --replication-factor 1 --partition 1 --topic meta

/opt/tools/spark/bin/spark-submit --class com.infrared.entry.ScalaApp --master local[*] --executor-memory 256M /mnt/data/src/bitbucket.org/infrared/models/kafka/consumer/target/consumer-1.0-SNAPSHOT.jar update-kafka-for-agents

