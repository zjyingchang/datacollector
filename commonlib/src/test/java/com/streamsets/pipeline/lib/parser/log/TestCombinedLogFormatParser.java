/**
 * (c) 2014 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.lib.parser.log;

import com.streamsets.pipeline.api.OnRecordError;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.api.Stage;
import com.streamsets.pipeline.lib.io.OverrunReader;
import com.streamsets.pipeline.lib.parser.DataParser;
import com.streamsets.pipeline.lib.parser.DataParserException;
import com.streamsets.pipeline.sdk.ContextInfoCreator;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;

public class TestCombinedLogFormatParser {
  private Stage.Context getContext() {
    return ContextInfoCreator.createSourceContext("i", false, OnRecordError.TO_ERROR,
      Collections.<String>emptyList());
  }

  @Test
  public void testParse() throws Exception {
    OverrunReader reader = new OverrunReader(new StringReader(
      "127.0.0.1 ss h [10/Oct/2000:13:55:36 -0700] \"GET /apache_pb.gif HTTP/1.0\" 200 2326 " +
        "\"http://www.example.com/start.html\" \"Mozilla/4.08 [en] (Win98; I ;Nav)\""), 1000, true);
    DataParser parser = new CombinedLogParser(getContext(), "id", reader, 0, 1000, true);
    Assert.assertEquals(0, parser.getOffset());
    Record record = parser.parse();
    Assert.assertNotNull(record);

    Assert.assertEquals("id::0", record.getHeader().getSourceId());

    Assert.assertEquals("127.0.0.1 ss h [10/Oct/2000:13:55:36 -0700] \"GET /apache_pb.gif HTTP/1.0\" 200 2326 " +
        "\"http://www.example.com/start.html\" \"Mozilla/4.08 [en] (Win98; I ;Nav)\"",
      record.get().getValueAsMap().get("originalLine").getValueAsString());

    Assert.assertFalse(record.has("/truncated"));

    Assert.assertEquals(154, parser.getOffset());

    Assert.assertTrue(record.has("/remoteHost"));
    Assert.assertEquals("127.0.0.1", record.get("/remoteHost").getValueAsString());

    Assert.assertTrue(record.has("/logName"));
    Assert.assertEquals("ss", record.get("/logName").getValueAsString());

    Assert.assertTrue(record.has("/remoteUser"));
    Assert.assertEquals("h", record.get("/remoteUser").getValueAsString());

    Assert.assertTrue(record.has("/requestTime"));
    Assert.assertEquals("10/Oct/2000:13:55:36 -0700", record.get("/requestTime").getValueAsString());

    Assert.assertTrue(record.has("/requestMethod"));
    Assert.assertEquals("GET", record.get("/requestMethod").getValueAsString());

    Assert.assertTrue(record.has("/urlPath"));
    Assert.assertEquals("/apache_pb.gif", record.get("/urlPath").getValueAsString());

    Assert.assertTrue(record.has("/requestProtocol"));
    Assert.assertEquals("HTTP/1.0", record.get("/requestProtocol").getValueAsString());

    Assert.assertTrue(record.has("/status"));
    Assert.assertEquals("200", record.get("/status").getValueAsString());

    Assert.assertTrue(record.has("/bytesSent"));
    Assert.assertEquals("2326", record.get("/bytesSent").getValueAsString());

    Assert.assertTrue(record.has("/referer"));
    Assert.assertEquals("http://www.example.com/start.html", record.get("/referer").getValueAsString());

    Assert.assertTrue(record.has("/userAgent"));
    Assert.assertEquals("Mozilla/4.08 [en] (Win98; I ;Nav)", record.get("/userAgent").getValueAsString());

    parser.close();
  }

  @Test
  public void testParseWithOffset() throws Exception {
    OverrunReader reader = new OverrunReader(new StringReader(
      "Hello\n127.0.0.1 ss h [10/Oct/2000:13:55:36 -0700] \"GET /apache_pb.gif HTTP/1.0\" 200 2326 " +
      "\"http://www.example.com/start.html\" \"Mozilla/4.08 [en] (Win98; I ;Nav)\""), 1000, true);
    DataParser parser = new CombinedLogParser(getContext(), "id", reader, 6, 1000, true);
    Assert.assertEquals(6, parser.getOffset());
    Record record = parser.parse();
    Assert.assertNotNull(record);

    Assert.assertEquals("id::6", record.getHeader().getSourceId());

    Assert.assertEquals("127.0.0.1 ss h [10/Oct/2000:13:55:36 -0700] \"GET /apache_pb.gif HTTP/1.0\" 200 2326 " +
        "\"http://www.example.com/start.html\" \"Mozilla/4.08 [en] (Win98; I ;Nav)\"",
      record.get().getValueAsMap().get("originalLine").getValueAsString());

    Assert.assertFalse(record.has("/truncated"));

    Assert.assertEquals(160, parser.getOffset());

    Assert.assertTrue(record.has("/remoteHost"));
    Assert.assertEquals("127.0.0.1", record.get("/remoteHost").getValueAsString());

    Assert.assertTrue(record.has("/logName"));
    Assert.assertEquals("ss", record.get("/logName").getValueAsString());

    Assert.assertTrue(record.has("/remoteUser"));
    Assert.assertEquals("h", record.get("/remoteUser").getValueAsString());

    Assert.assertTrue(record.has("/requestTime"));
    Assert.assertEquals("10/Oct/2000:13:55:36 -0700", record.get("/requestTime").getValueAsString());

    Assert.assertTrue(record.has("/requestMethod"));
    Assert.assertEquals("GET", record.get("/requestMethod").getValueAsString());

    Assert.assertTrue(record.has("/urlPath"));
    Assert.assertEquals("/apache_pb.gif", record.get("/urlPath").getValueAsString());

    Assert.assertTrue(record.has("/requestProtocol"));
    Assert.assertEquals("HTTP/1.0", record.get("/requestProtocol").getValueAsString());

    Assert.assertTrue(record.has("/status"));
    Assert.assertEquals("200", record.get("/status").getValueAsString());

    Assert.assertTrue(record.has("/bytesSent"));
    Assert.assertEquals("2326", record.get("/bytesSent").getValueAsString());

    Assert.assertTrue(record.has("/referer"));
    Assert.assertEquals("http://www.example.com/start.html", record.get("/referer").getValueAsString());

    Assert.assertTrue(record.has("/userAgent"));
    Assert.assertEquals("Mozilla/4.08 [en] (Win98; I ;Nav)", record.get("/userAgent").getValueAsString());

    record = parser.parse();
    Assert.assertNull(record);

    Assert.assertEquals(-1, parser.getOffset());
    parser.close();
  }

  @Test(expected = IOException.class)
  public void testClose() throws Exception {
    OverrunReader reader = new OverrunReader(new StringReader("Hello\nByte"), 1000, true);
    DataParser parser = new CombinedLogParser(getContext(), "id", reader, 0, 1000, false);
    parser.close();
    parser.parse();
  }

  @Test(expected = DataParserException.class)
  public void testTruncate() throws Exception {
    OverrunReader reader = new OverrunReader(new StringReader(
      "127.0.0.1 ss h [10/Oct/2000:13:55:36 -0700] \"GET /apache_pb.gif HTTP/1.0\" 200 2326 " +
        "\"http://www.example.com/start.html\" \"Mozilla/4.08 [en] (Win98; I ;Nav)\""), 1000, true);
    DataParser parser = new CombinedLogParser(getContext(), "id", reader, 0, 25, true); //cut short to 25
    Assert.assertEquals(0, parser.getOffset());
    try {
      parser.parse();
    } finally {
      parser.close();
    }

  }

  @Test(expected = DataParserException.class)
  public void testParseNonLogLine() throws Exception {
    OverrunReader reader = new OverrunReader(new StringReader(
      "127.0.0.1 ss h [10/Oct/2000:13:55:36 -0700] This is a log line that does not confirm to common log format"),
      1000, true);
    DataParser parser = new CombinedLogParser(getContext(), "id", reader, 0, 1000, true);
    Assert.assertEquals(0, parser.getOffset());
    try {
      parser.parse();
    } finally {
      parser.close();
    }
  }
}
