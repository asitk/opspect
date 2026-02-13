package com.infrared.injest;

import kafka.message.Message;
import kafka.producer.ProducerConfig;
import kafka.producer.KeyedMessage;
import kafka.javaapi.producer.Producer;
import java.io.Serializable;
import java.util.Properties;

/**
 * Created by prashun on 6/7/16.
 */

public class HighLevelProducer implements Serializable{
    public static void send(String topic, byte []  key, byte [] message) {
        Properties props = new Properties();
        props.put("metadata.broker.list", "localhost:9092");
        props.put("serializer.class", "kafka.serializer.DefaultEncoder");
        ProducerConfig properties = new ProducerConfig(props);
        Producer<byte [], byte []> producer = new Producer<byte [],byte []>(properties);

        KeyedMessage<byte [],byte []> km = new KeyedMessage<byte [], byte[]>(topic, key, message);
        producer.send(km);
        producer.close();
        com.infrared.util.Log.getLogger().trace("Written to Kafka Queue " + topic);
    }
}
