package io.nmoncho.faradn;

public class PrintJob {
  private final Document doc;
  private final Printer printer;

  public PrintJob(Document doc, Printer printer) {
    this.doc = doc;
    this.printer = printer;
  }

}
