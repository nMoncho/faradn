package net.nmoncho.faradn.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.usb.UsbIrp;
import javax.usb.UsbPipe;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.printer.escpos.commands.StatusCommands;

class UsbTransportTest {

  @Test
  void readsTheStatusByteWhenTheIrpCompletes() throws Exception {
    UsbPipe out = mock(UsbPipe.class);
    UsbPipe in = mock(UsbPipe.class);
    UsbIrp irp = mock(UsbIrp.class);
    when(in.createUsbIrp()).thenReturn(irp);
    when(irp.isComplete()).thenReturn(true);
    when(irp.isUsbException()).thenReturn(false);
    when(irp.getActualLength()).thenReturn(1);

    byte result = UsbTransport.readStatusByte(out, in, StatusCommands.PRINTER_STATUS, 2000);

    assertEquals(0, result); // the mock never fills the buffer
    verify(out).syncSubmit(StatusCommands.PRINTER_STATUS.getCode());
    verify(in).asyncSubmit(irp);
  }

  @Test
  void abortsAndThrowsWhenTheReadTimesOut() {
    UsbPipe out = mock(UsbPipe.class);
    UsbPipe in = mock(UsbPipe.class);
    UsbIrp irp = mock(UsbIrp.class);
    when(in.createUsbIrp()).thenReturn(irp);
    when(irp.isComplete()).thenReturn(false);

    TransportException ex = assertThrows(TransportException.class,
        () -> UsbTransport.readStatusByte(out, in, StatusCommands.PRINTER_STATUS, 50));

    assertTrue(ex.getMessage().contains("Timed out"), ex.getMessage());
    verify(in).abortAllSubmissions();
  }

  @Test
  void throwsWhenNoStatusByteIsReturned() {
    UsbPipe out = mock(UsbPipe.class);
    UsbPipe in = mock(UsbPipe.class);
    UsbIrp irp = mock(UsbIrp.class);
    when(in.createUsbIrp()).thenReturn(irp);
    when(irp.isComplete()).thenReturn(true);
    when(irp.isUsbException()).thenReturn(false);
    when(irp.getActualLength()).thenReturn(0);

    assertThrows(TransportException.class,
        () -> UsbTransport.readStatusByte(out, in, StatusCommands.PRINTER_STATUS, 2000));
  }
}
