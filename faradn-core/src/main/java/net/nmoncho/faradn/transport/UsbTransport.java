package net.nmoncho.faradn.transport;

import javax.usb.UsbDevice;
import javax.usb.UsbEndpoint;
import javax.usb.UsbException;
import javax.usb.UsbInterface;
import javax.usb.UsbIrp;
import javax.usb.UsbPipe;

import net.nmoncho.faradn.printer.Devices;
import net.nmoncho.faradn.printer.escpos.Code;
import net.nmoncho.faradn.printer.escpos.commands.StatusCommands;

/**
 * Sends bytes to a USB printer. On construction it claims the printer interface
 * force-claiming, which detaches the kernel driver (e.g. {@code usblp}) on
 * Linux and opens the OUT pipe, plus the IN pipe for status when the printer
 * exposes one. {@link #close()} closes the pipes and releases the interface, so
 * use it with try-with-resources.
 * <p>
 * On macOS and Windows the interface must not be claimed by another process; on
 * Linux the kernel {@code usblp} driver is detached automatically by the forced
 * claim. If the printer has no IN endpoint, {@link #status()} is unavailable.
 */
public final class UsbTransport implements Transport {

  /** Default bound on a single {@code DLE EOT} status read, in milliseconds. */
  public static final int DEFAULT_STATUS_TIMEOUT_MILLIS = 2000;

  private final UsbInterface iface;
  private final UsbPipe outPipe;
  private final UsbPipe inPipe; // null when the printer has no status endpoint
  private final int statusTimeoutMillis;

  public UsbTransport(UsbDevice device) {
    this(device, DEFAULT_STATUS_TIMEOUT_MILLIS);
  }

  /**
   * @param device
   *        the USB printer to open
   * @param statusTimeoutMillis
   *        how long a single {@link #status()} read waits for each response
   *        byte before failing, so a silent printer cannot block the caller
   */
  public UsbTransport(UsbDevice device, int statusTimeoutMillis) {
    if (statusTimeoutMillis < 1) {
      throw new IllegalArgumentException("statusTimeoutMillis must be positive, got " + statusTimeoutMillis);
    }

    this.statusTimeoutMillis = statusTimeoutMillis;
    this.iface = Devices.findPrinterInterface(device)
        .orElseThrow(() -> new TransportException("Device has no USB printer interface"));
    final UsbEndpoint outEndpoint = Devices.findOutEndpoint(iface)
        .orElseThrow(() -> new TransportException("Printer interface has no OUT endpoint"));
    final UsbPipe in = Devices.findInEndpoint(iface).map(UsbEndpoint::getUsbPipe).orElse(null);

    try {
      iface.claim(claimed -> true); // force claim: detaches the kernel driver on Linux
      this.outPipe = outEndpoint.getUsbPipe();
      outPipe.open();
      if (in != null) {
        in.open();
      }
      this.inPipe = in;
    } catch (UsbException e) {
      releaseQuietly();
      throw new TransportException("Failed to open USB printer", e);
    }
  }

  @Override
  public void write(byte[] payload) {
    try {
      outPipe.syncSubmit(payload);
    } catch (UsbException e) {
      throw new TransportException("USB write failed", e);
    }
  }

  @Override
  public PrinterStatus status() {
    if (inPipe == null) {
      throw new TransportException("Printer has no status (IN) endpoint");
    }
    try {
      byte printer = query(StatusCommands.PRINTER_STATUS);
      byte offline = query(StatusCommands.OFFLINE_STATUS);
      byte error = query(StatusCommands.ERROR_STATUS);
      byte paper = query(StatusCommands.PAPER_ROLL_STATUS);
      return PrinterStatus.of(printer, offline, error, paper);
    } catch (UsbException e) {
      throw new TransportException("USB status read failed", e);
    }
  }

  private byte query(Code command) throws UsbException {
    return readStatusByte(outPipe, inPipe, command, statusTimeoutMillis);
  }

  /**
   * Sends a status command and reads one response byte, waiting at most
   * {@code timeoutMillis} for it. The read uses the asynchronous IRP API so a
   * printer that never answers cannot block the caller indefinitely, as the
   * blocking {@link UsbPipe#syncSubmit(byte[])} would.
   */
  static byte readStatusByte(UsbPipe out, UsbPipe in, Code command, int timeoutMillis) throws UsbException {
    out.syncSubmit(command.getCode());

    final byte[] buffer = new byte[1];
    final UsbIrp irp = in.createUsbIrp();

    irp.setData(buffer);
    irp.setAcceptShortPacket(true);
    in.asyncSubmit(irp);
    irp.waitUntilComplete(timeoutMillis);

    if (!irp.isComplete()) {
      in.abortAllSubmissions();
      throw new TransportException(
          "Timed out after " + timeoutMillis + " ms waiting for a status byte from the printer");
    }

    if (irp.isUsbException()) {
      throw irp.getUsbException();
    }

    if (irp.getActualLength() < 1) {
      throw new TransportException("No status byte returned by the printer");
    }

    return buffer[0];
  }

  @Override
  public void close() {
    closeQuietly(inPipe);
    closeQuietly(outPipe);
    releaseQuietly();
  }

  private static void closeQuietly(UsbPipe pipe) {
    if (pipe == null) {
      return;
    }
    try {
      pipe.close();
    } catch (UsbException ignored) {
      // best effort
    }
  }

  private void releaseQuietly() {
    try {
      iface.release();
    } catch (UsbException ignored) {
      // best effort
    }
  }
}
