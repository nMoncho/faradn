package io.nmoncho.faradn.printer;

/**
 * Profile for the Epson TM-T88V, an 80&nbsp;mm thermal receipt printer.
 * <p>
 * Figures follow Epson's specification: 180&nbsp;dpi, a 72&nbsp;mm / 512-dot
 * printable width, and 42 columns in Font A.
 */
public final class TmT88vProfile implements PrinterProfile {

  public static final TmT88vProfile INSTANCE = new TmT88vProfile();

  @Override
  public String name() {
    return "Epson TM-T88V";
  }

  @Override
  public int dotsPerLine() {
    return 512;
  }

  @Override
  public int columns() {
    return 42;
  }

  @Override
  public int dpi() {
    return 180;
  }

  @Override
  public boolean supportsCut() {
    return true;
  }

  @Override
  public CodePage codePage() {
    return CodePage.PC437;
  }

}
