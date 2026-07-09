package io.nmoncho.faradn.jobs;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.usb.UsbDevice;
import javax.usb.UsbDeviceDescriptor;

import org.junit.jupiter.api.Test;
import org.usb4java.Device;
import org.usb4java.DeviceDescriptor;

import io.nmoncho.faradn.Document;
import io.nmoncho.faradn.Printer;
import io.nmoncho.faradn.printer.Devices;
import io.nmoncho.faradn.printer.PrintJobState;
import io.nmoncho.faradn.printer.escpos.NodeVisitor;
import io.nmoncho.faradn.printer.escpos.commands.CharacterCommands;

import static io.nmoncho.faradn.Utils.STYLE_ATTR_PATTERN;

public class PrintingJobTest {

  //  @Test
  //  void simplePrintJob() throws Exception {
  //    File f = new File("src/test/resources/printjobs/01.html");
  //    Document doc = Document.from(f);
  //    System.out.println(doc);
  //    NodeVisitor visitor = new NodeVisitor(doc);
  //
  //    List<PrintJobState> states = visitor.states();
  //    System.out.println("==========================================");
  //    for (PrintJobState state : states) {
  //      System.out.println(state);
  //    }
  //  }
  //
  //  @Test
  //  void simplePrintJob2() throws Exception {
  //    File f = new File("src/test/resources/printjobs/02.html");
  //    Document doc = Document.from(f);
  //    System.out.println(doc);
  //    NodeVisitor visitor = new NodeVisitor(doc);
  //
  //    List<PrintJobState> states = visitor.states();
  //    System.out.println("==========================================");
  //    for (PrintJobState state : states) {
  //      System.out.println(state);
  //    }
  //  }

  @Test
  void simplePrintJob() throws Exception {
    File f = new File("src/test/resources/printjobs/ticket01.html");
    Document doc = Document.from(f);
    System.out.println(doc);
    NodeVisitor visitor = new NodeVisitor(doc);

    List<PrintJobState> states = visitor.states();
    System.out.println("==========================================");
    for (PrintJobState state : states) {
      System.out.println(state);
    }
  }
}
