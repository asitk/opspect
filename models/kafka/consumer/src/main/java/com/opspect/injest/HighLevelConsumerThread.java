package com.opspect.injest;

import com.opspect.entry.Line;
import com.opspect.entry.rawrecord.RawRecord;
import com.opspect.entry.rawrecord.RawRecordType;
import com.opspect.util.TCPClient;
import java.util.Objects;
import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;

public class HighLevelConsumerThread implements Runnable {
  private KafkaStream m_stream;
  private int m_threadNumber;

  public HighLevelConsumerThread(KafkaStream a_stream, int a_threadNumber) {
    m_threadNumber = a_threadNumber;
    m_stream = a_stream;
  }

  public void run() {
    ConsumerIterator<byte[], byte[]> it = m_stream.iterator();
    while (it.hasNext()) {
      String msg = new String((it.next().message()));
      // System.out.println("Thread " + m_threadNumber + ": " + msg);

      // Create a record object and write points to timeseries
      RawRecord rr = Line.Parse(msg);

      if (rr.rectype() == RawRecordType.LOG()) {

        if (Objects.equals(rr.hinttagv(), "accesslog")) {
          System.out.println("Thread " + m_threadNumber + ": " + rr.toLogStashString());
          TCPClient tc = new TCPClient();
          tc.Send(rr.toLogStashString(), "localhost", 5000);
        }

        if (Objects.equals(rr.hinttagv(), "errorlog")) {
          System.out.println("Thread " + m_threadNumber + ": " + rr.toLogStashString());
          TCPClient tc = new TCPClient();
          tc.Send(rr.toLogStashString(), "localhost", 5001);
        }
      }
    }
    System.out.println("Shutting down Thread: " + m_threadNumber);
  }
}
