package com.opspect.injest;

import java.io.Serializable;
import java.util.Properties;
import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import kafka.producer.ProducerConfig;

/** Created by prashun on 6/7/16. */
public class HighLevelProducer implements Serializable {
  public static void send(String topic, byte[] key, byte[] message) {
    Properties props = new Properties();
    props.put("metadata.broker.list", "localhost:9092");
    props.put("serializer.class", "kafka.serializer.DefaultEncoder");
    ProducerConfig properties = new ProducerConfig(props);
    Producer<byte[], byte[]> producer = new Producer<byte[], byte[]>(properties);

    KeyedMessage<byte[], byte[]> km = new KeyedMessage<byte[], byte[]>(topic, key, message);
    producer.send(km);
    producer.close();
    com.opspect.util.Log.getLogger().trace("Written to Kafka Queue " + topic);
  }
}
