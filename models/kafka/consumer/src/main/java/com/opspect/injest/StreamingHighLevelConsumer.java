package com.opspect.injest;

import com.opspect.util.Log;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import kafka.consumer.ConsumerConfig;
import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
import kafka.javaapi.consumer.ConsumerConnector;
import org.apache.spark.storage.StorageLevel;
import org.apache.spark.streaming.receiver.Receiver;

class StreamingHighLevelConsumerThread implements Runnable, Serializable {
  private KafkaStream m_stream;
  private int m_threadNumber;
  private Receiver<String> m_receiver;

  public StreamingHighLevelConsumerThread(
      KafkaStream a_stream, int a_threadNumber, Receiver<String> receiver) {
    m_threadNumber = a_threadNumber;
    m_stream = a_stream;
    m_receiver = receiver;
  }

  public void run() {
    ConsumerIterator<byte[], byte[]> it = m_stream.iterator();

    while (!m_receiver.isStopped() && it.hasNext()) {
      String msg = new String((it.next().message()));
      Log.getLogger().debug("Thread " + m_threadNumber + ": " + msg);
      m_receiver.store(msg);
    }
    Log.getLogger().trace("Shutting down Thread: " + m_threadNumber);
  }
}

public class StreamingHighLevelConsumer extends Receiver<String> {
  transient ConsumerConnector m_consumer;
  private String m_topic;
  private /*static*/ Receiver<String> m_reciever;
  private transient Thread _consumerThread;
  private StreamingHighLevelConsumerThread _kConsumer;

  // private  ExecutorService executor;

  @Override
  public StorageLevel storageLevel() {
    return StorageLevel.MEMORY_AND_DISK_SER_2();
  }

  public StreamingHighLevelConsumer(String a_zookeeper, String a_groupId, String a_topic) {
    super(StorageLevel.MEMORY_AND_DISK_SER_2());

    // m_consumer = kafka.consumer.Consumer.createJavaConsumerConnector(
    //      CreateConfig(a_zookeeper, a_groupId));

    // if (m_consumer == null) {
    //  System.out.println("Asit: Unable to create Consumer");
    // System.exit(0);
    // }
    // else {
    //  System.out.println("Asit: Created Consumer : " + a_zookeeper + a_groupId + a_topic);
    // }

    this.m_topic = a_topic;
    this.m_reciever = this;
  }

  public void onStart() {
    // Start the thread that receives data over a connection
    // new Thread()  {
    // @Override public void run() {
    try {
      Execute(1);
    } catch (Exception e) {
      Log.getLogger().error(e.getMessage());
      throw e;
    }
    // }.start();
  }

  public void onStop() {
    // There is nothing much to do as the thread calling receive()
    // is designed to stop by itself isStopped() returns false
  }

  public void shutdown() {
    if (m_consumer != null) m_consumer.shutdown();
    // if (executor != null) executor.shutdown();
    // try {
    // if (!executor.awaitTermination(5000, TimeUnit.MILLISECONDS)) {
    // System.out.println("Timed out waiting for consumer threads to shut down, exiting uncleanly");
    // }
    // } catch (InterruptedException e) {
    // System.out.println("Interrupted during shutdown, exiting uncleanly");
    // }
  }

  private void Execute(int a_numThreads) {
    m_consumer =
        kafka.consumer.Consumer.createJavaConsumerConnector(
            CreateConfig("localhost:2181", "grplog"));
    m_topic = "log";

    Map<String, List<KafkaStream<byte[], byte[]>>> consumerMap;

    Map<String, Integer> topicCountMap = new HashMap<String, Integer>();
    topicCountMap.put(m_topic, a_numThreads);

    Log.getLogger().debug("Inside StreamingHighLevelConsumer.Execute : " + m_topic);

    if (m_consumer != null) {
      consumerMap = m_consumer.createMessageStreams(topicCountMap);
      Log.getLogger().debug("StreamingHighLevelConsumer Created Stream ");
    } else {
      consumerMap = null;
      Log.getLogger().fatal("Got Null Consumer ");
      System.exit(0);
    }

    List<KafkaStream<byte[], byte[]>> streams = consumerMap.get(m_topic);

    int threadNumber = 0;

    for (final KafkaStream stream : streams) {
      // _kConsumer = new StreamingHighLevelConsumerThread(stream, threadNumber, m_reciever);

      /* Thread.UncaughtExceptionHandler eh = new Thread.UncaughtExceptionHandler() {

          public void uncaughtException(Thread th, Throwable ex) {
              if (ex instanceof InterruptedException) {
                  th.interrupt();
              }
              stop(" Stopping Receiver " + ex);

          }
      };*/

      // _consumerThread = new Thread(_kConsumer);
      // _consumerThread = new Thread(new StreamingHighLevelConsumerThread(stream, threadNumber,
      // m_reciever));
      _consumerThread =
          new Thread(new StreamingHighLevelConsumerThread(stream, threadNumber, this));

      _consumerThread.setDaemon(true);
      // _consumerThread.setUncaughtExceptionHandler(eh);
      _consumerThread.start();

      threadNumber++;
    }

    // now launch all the threads
    // executor = Executors.newFixedThreadPool(a_numThreads);

    // now create an object to consume the messages
    // for (final KafkaStream stream : streams) {
    // executor.submit(new StreamingHighLevelConsumerThread(stream, threadNumber, m_reciever));
    // threadNumber++;
    // }
  }

  private static ConsumerConfig CreateConfig(String a_zookeeper, String a_groupId) {
    Properties props = new Properties();
    props.put("zookeeper.connect", a_zookeeper);
    props.put("group.id", a_groupId);
    props.put("zookeeper.session.timeout.ms", "5000");
    props.put("zookeeper.sync.time.ms", "200");
    props.put("auto.commit.interval.ms", "1000");

    return new ConsumerConfig(props);
  }
}
