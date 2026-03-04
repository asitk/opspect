package com.opscog.engine;

import java.io.*;
import java.nio.BufferOverflowException;
import java.util.Random;

public class WriteToFileExample implements Runnable {
  File file;

  WriteToFileExample() {
    try {
      file = new File("/tmp/errors.log");
      file.createNewFile();
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(-1);
    }
  }

  public void run() {
    for (int i = 0; i < 50; i++) {

      GenerateExceptions();
      try {
        Thread.sleep(5000);
      } catch (InterruptedException e) {
      }
    }
  }

  private void WriteExceptions(String Content) {
    try {
      FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
      BufferedWriter bw = new BufferedWriter(fw);
      bw.write(Content);
      bw.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void GenerateExceptions() {

    int max = randInt(1, 30);
    for (int i = 0; i < max; i++) {

      int j = randInt(1, 6);
      try {
        switch (j) {
          case 1:
            throw new IOException();
          case 2:
            throw new ArrayIndexOutOfBoundsException(j);
          case 3:
            throw new ArithmeticException();
          case 4:
            throw new ArrayStoreException();
          case 5:
            throw new BufferOverflowException();
          case 6:
            throw new NullPointerException();
          default:
            throw new IOException();
        }
      } catch (Exception e) {
        Writer writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        e.printStackTrace(printWriter);
        String s = writer.toString();
        WriteExceptions(s);
      }
    }
  }

  private int randInt(int min, int max) {

    // Usually this can be a field rather than a method variable
    Random rand = new Random();

    // nextInt is normally exclusive of the top value,
    // so add 1 to make it inclusive
    int randomNum = rand.nextInt((max - min) + 1) + min;

    return randomNum;
  }
}
