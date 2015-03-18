/**
 * (c) 2014 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.stage.origin.spooldir;

import com.streamsets.pipeline.api.BatchMaker;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.config.DataFormat;
import com.streamsets.pipeline.config.LogMode;
import com.streamsets.pipeline.sdk.SourceRunner;
import com.streamsets.pipeline.sdk.StageRunner;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TestLogSpoolDirSourceApacheErrorLogFormat {

  private static final String CUSTOM_LOG_FORMAT = "%h %l %u %t \"%r\" %>s %b";
  private static final String REGEX =
    "^(\\S+) (\\S+) (\\S+) \\[([\\w:/]+\\s[+\\-]\\d{4})\\] \"(\\S+) (\\S+) (\\S+)\" (\\d{3}) (\\d+)";
  private static final List<RegExConfig> REGEX_CONFIG = new ArrayList<>();

  static {
    RegExConfig r1 = new RegExConfig();
    r1.fieldPath = "remoteHost";
    r1.group = 1;
    REGEX_CONFIG.add(r1);
    RegExConfig r2 = new RegExConfig();
    r2.fieldPath = "logName";
    r2.group = 2;
    REGEX_CONFIG.add(r2);
    RegExConfig r3 = new RegExConfig();
    r3.fieldPath = "remoteUser";
    r3.group = 3;
    REGEX_CONFIG.add(r3);
    RegExConfig r4 = new RegExConfig();
    r4.fieldPath = "requestTime";
    r4.group = 4;
    REGEX_CONFIG.add(r4);
    RegExConfig r5 = new RegExConfig();
    r5.fieldPath = "request";
    r5.group = 5;
    REGEX_CONFIG.add(r5);
    RegExConfig r6 = new RegExConfig();
    r6.fieldPath = "status";
    r6.group = 6;
    REGEX_CONFIG.add(r6);
    RegExConfig r7 = new RegExConfig();
    r7.fieldPath = "bytesSent";
    r7.group = 7;
    REGEX_CONFIG.add(r7);
  }

  private String createTestDir() {
    File f = new File("target", UUID.randomUUID().toString());
    Assert.assertTrue(f.mkdirs());
    return f.getAbsolutePath();
  }

  private final static String LINE1 = "[Wed Oct 11 14:32:52 2000] [error] [client 127.0.0.1] client denied " +
    "by server configuration1: /export/home/live/ap/htdocs/test1";
  private final static String LINE2 = "[Thu Oct 11 14:32:52 2000] [warn] [client 127.0.0.1] client denied "+
    "by server configuration2: /export/home/live/ap/htdocs/test2";

  private File createLogFile() throws Exception {
    File f = new File(createTestDir(), "test.log");
    Writer writer = new FileWriter(f);
    IOUtils.write(LINE1 + "\n", writer);
    IOUtils.write(LINE2, writer);
    writer.close();
    return f;
  }

  private SpoolDirSource createSource() {
    return new SpoolDirSource(DataFormat.LOG, "UTF-8", 100, createTestDir(), 10, 1, "file-[0-9].log", 10, null, null,
      PostProcessingOptions.ARCHIVE, createTestDir(), 10, null, null, -1, null, 0, 0,
      null, 0, LogMode.APACHE_ERROR_LOG_FORMAT, 1000, true, CUSTOM_LOG_FORMAT, REGEX, REGEX_CONFIG);
  }

  @Test
  public void testProduceFullFile() throws Exception {
    SpoolDirSource source = createSource();
    SourceRunner runner = new SourceRunner.Builder(source).addOutputLane("lane").build();
    runner.runInit();
    try {
      BatchMaker batchMaker = SourceRunner.createTestBatchMaker("lane");
      Assert.assertEquals(-1, source.produce(createLogFile(), 0, 10, batchMaker));
      StageRunner.Output output = SourceRunner.getOutput(batchMaker);
      List<Record> records = output.getRecords().get("lane");
      Assert.assertNotNull(records);
      Assert.assertEquals(2, records.size());

      Assert.assertFalse(records.get(0).has("/truncated"));

      Record record = records.get(0);

      Assert.assertEquals(LINE1, record.get().getValueAsMap().get("originalLine").getValueAsString());

      Assert.assertFalse(record.has("/truncated"));

      Assert.assertTrue(record.has("/dateTime"));
      Assert.assertEquals("Wed Oct 11 14:32:52 2000", record.get("/dateTime").getValueAsString());

      Assert.assertTrue(record.has("/severity"));
      Assert.assertEquals("error", record.get("/severity").getValueAsString());

      Assert.assertTrue(record.has("/clientIpAddress"));
      Assert.assertEquals("127.0.0.1", record.get("/clientIpAddress").getValueAsString());

      Assert.assertTrue(record.has("/message"));
      Assert.assertEquals("client denied by server configuration1: /export/home/live/ap/htdocs/test1",
        record.get("/message").getValueAsString());

      record = records.get(1);

      Assert.assertEquals(LINE2, records.get(1).get().getValueAsMap().get("originalLine").getValueAsString());
      Assert.assertFalse(record.has("/truncated"));

      Assert.assertTrue(record.has("/dateTime"));
      Assert.assertEquals("Thu Oct 11 14:32:52 2000", record.get("/dateTime").getValueAsString());

      Assert.assertTrue(record.has("/severity"));
      Assert.assertEquals("warn", record.get("/severity").getValueAsString());

      Assert.assertTrue(record.has("/clientIpAddress"));
      Assert.assertEquals("127.0.0.1", record.get("/clientIpAddress").getValueAsString());

      Assert.assertTrue(record.has("/message"));
      Assert.assertEquals("client denied by server configuration2: /export/home/live/ap/htdocs/test2",
        record.get("/message").getValueAsString());

    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testProduceLessThanFile() throws Exception {
    SpoolDirSource source = createSource();
    SourceRunner runner = new SourceRunner.Builder(source).addOutputLane("lane").build();
    runner.runInit();
    try {
      BatchMaker batchMaker = SourceRunner.createTestBatchMaker("lane");
      long offset = source.produce(createLogFile(), 0, 1, batchMaker);
      //FIXME
      Assert.assertEquals(128, offset);
      StageRunner.Output output = SourceRunner.getOutput(batchMaker);
      List<Record> records = output.getRecords().get("lane");
      Assert.assertNotNull(records);
      Assert.assertEquals(1, records.size());


      Record record = records.get(0);
      Assert.assertFalse(record.has("/truncated"));

      Assert.assertTrue(record.has("/dateTime"));
      Assert.assertEquals("Wed Oct 11 14:32:52 2000", record.get("/dateTime").getValueAsString());

      Assert.assertTrue(record.has("/severity"));
      Assert.assertEquals("error", record.get("/severity").getValueAsString());

      Assert.assertTrue(record.has("/clientIpAddress"));
      Assert.assertEquals("127.0.0.1", record.get("/clientIpAddress").getValueAsString());

      Assert.assertTrue(record.has("/message"));
      Assert.assertEquals("client denied by server configuration1: /export/home/live/ap/htdocs/test1",
        record.get("/message").getValueAsString());

      batchMaker = SourceRunner.createTestBatchMaker("lane");
      offset = source.produce(createLogFile(), offset, 1, batchMaker);
      Assert.assertEquals(254, offset);
      output = SourceRunner.getOutput(batchMaker);
      records = output.getRecords().get("lane");
      Assert.assertNotNull(records);
      Assert.assertEquals(1, records.size());

      Assert.assertEquals(LINE2, records.get(0).get().getValueAsMap().get("originalLine").getValueAsString());
      Assert.assertFalse(records.get(0).has("/truncated"));

      record = records.get(0);
      Assert.assertTrue(record.has("/dateTime"));
      Assert.assertEquals("Thu Oct 11 14:32:52 2000", record.get("/dateTime").getValueAsString());

      Assert.assertTrue(record.has("/severity"));
      Assert.assertEquals("warn", record.get("/severity").getValueAsString());

      Assert.assertTrue(record.has("/clientIpAddress"));
      Assert.assertEquals("127.0.0.1", record.get("/clientIpAddress").getValueAsString());

      Assert.assertTrue(record.has("/message"));
      Assert.assertEquals("client denied by server configuration2: /export/home/live/ap/htdocs/test2",
        record.get("/message").getValueAsString());


      batchMaker = SourceRunner.createTestBatchMaker("lane");
      offset = source.produce(createLogFile(), offset, 1, batchMaker);
      Assert.assertEquals(-1, offset);
      output = SourceRunner.getOutput(batchMaker);
      records = output.getRecords().get("lane");
      Assert.assertNotNull(records);
      Assert.assertEquals(0, records.size());

    } finally {
      runner.runDestroy();
    }
  }
}
