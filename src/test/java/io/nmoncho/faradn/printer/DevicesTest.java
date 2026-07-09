package io.nmoncho.faradn.printer;

import javax.usb.UsbConfiguration;
import javax.usb.UsbConst;
import javax.usb.UsbDescriptor;
import javax.usb.UsbDevice;
import javax.usb.UsbDeviceDescriptor;
import javax.usb.UsbEndpoint;
import javax.usb.UsbHub;
import javax.usb.UsbInterface;
import javax.usb.UsbInterfaceDescriptor;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.usb4java.LibUsb;

import io.nmoncho.faradn.printer.Devices.UsbUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class DevicesTest {

  private final String vendorName = "Seiko Epson Corp";
  private final short vendorId = 1208;
  private final short productId = 3586; // TM-T88V

  @Test
  public void findDevice_empty() {
    withMockedRootUsbHub(Collections.emptyList(), () -> {
      // find by vendor id
      Optional<UsbDevice> devByVendorId = Devices.findDevice(vendorId);
      assertTrue(devByVendorId.isEmpty());

      // find by vendor id, and product id
      Optional<UsbDevice> dev = Devices.findDevice(vendorId, productId);
      assertTrue(dev.isEmpty());

      // find by vendor name
      Optional<UsbDevice> devByVendorName = Devices.findDevice(vendorName);
      assertTrue(devByVendorName.isEmpty());
    });
  }

  @Test
  public void findDevice() {
    short anotherProductId = 333;
    UsbDevice device = mockUsbPrinter(vendorId, productId);
    List<UsbDevice> devices = List.of(
        device,
        mockUsbPrinter(vendorId, anotherProductId));

    withMockedRootUsbHub(devices, () -> {
      // find by vendor id
      Optional<UsbDevice> devByVendorId = Devices.findDevice(vendorId);
      assertTrue(devByVendorId.isPresent());
      assertEquals(Optional.of(device), devByVendorId);

      // find by vendor id, and product id
      Optional<UsbDevice> dev = Devices.findDevice(vendorId, productId);
      assertTrue(dev.isPresent());
      assertEquals(Optional.of(device), dev);

      // find by vendor id, and product id (not first match)
      Optional<UsbDevice> specificDev = Devices.findDevice(vendorId, anotherProductId);
      assertTrue(specificDev.isPresent());
      assertNotEquals(Optional.of(device), specificDev);

      // find by vendor name
      Optional<UsbDevice> devByVendorName = Devices.findDevice(vendorName);
      assertTrue(devByVendorName.isPresent());
      assertEquals(Optional.of(device), devByVendorName);
    });
  }

  @Test
  public void findDevice_insideHub() {
    List<UsbDevice> devices = List.of(mockUsbHub(
        List.of(mockUsbPrinter(vendorId, productId))));

    withMockedRootUsbHub(devices, () -> {
      // find by vendor id
      Optional<UsbDevice> devByVendorId = Devices.findDevice(vendorId);
      assertTrue(devByVendorId.isPresent());

      // find by vendor id, and product id
      Optional<UsbDevice> dev = Devices.findDevice(vendorId, productId);
      assertTrue(dev.isPresent());

      // find by vendor name
      Optional<UsbDevice> devByVendorName = Devices.findDevice(vendorName);
      assertTrue(devByVendorName.isPresent());
    });
  }

  @Test
  public void findDevice_wrongParameters() {
    String wrongVendorName = "foobar";
    short wrongVendorId = 1233;
    short wrongProductId = 1233;

    withMockedRootUsbHub(List.of(mockUsbPrinter(vendorId, productId)), () -> {
      // find by vendor id
      Optional<UsbDevice> devByVendorId = Devices.findDevice(wrongVendorId);
      assertTrue(devByVendorId.isEmpty());

      // find by vendor id (wrong), and product id (wrong)
      Optional<UsbDevice> devA = Devices.findDevice(wrongVendorId, wrongProductId);
      assertTrue(devA.isEmpty());

      // find by vendor id, and product id (wrong)
      Optional<UsbDevice> devB = Devices.findDevice(vendorId, wrongProductId);
      assertTrue(devB.isEmpty());

      // find by vendor id (wrong), and product id
      Optional<UsbDevice> devC = Devices.findDevice(wrongProductId, productId);
      assertTrue(devC.isEmpty());

      // find by vendor name
      Optional<UsbDevice> devByVendorName = Devices.findDevice(wrongVendorName);
      assertTrue(devByVendorName.isEmpty());
    });
  }

  @Test
  public void listDevices() {
    // No USB Devices available
    withMockedRootUsbHub(Collections.emptyList(), () -> {
      assertTrue(Devices.listDevices().isEmpty());
    });

    // One USB device inside a hub
    List<UsbDevice> devices = List.of(mockUsbHub(
        List.of(mockUsbPrinter(vendorId, productId))));

    withMockedRootUsbHub(devices, () -> {
      List<UsbDevice> devs = Devices.listDevices();
      assertFalse(devs.isEmpty());
      assertEquals(devs.size(), 1); // only count actual devices
    });
  }

  @Test
  public void listPrinters() {
    // No USB Printers available
    withMockedRootUsbHub(Collections.emptyList(), () -> {
      assertTrue(Devices.listPrinterDevices().isEmpty());
    });

    // One USB printer inside a hub
    List<UsbDevice> devices = List.of(mockUsbHub(
        List.of(
            mockUsbPrinter(vendorId, productId),
            mockUsbDevice(vendorId, productId, false))));
    withMockedRootUsbHub(devices, () -> {
      List<UsbDevice> devs = Devices.listPrinterDevices();
      assertFalse(devs.isEmpty());
      assertEquals(devs.size(), 1);
    });
  }

  private void withMockedRootUsbHub(List<UsbDevice> devices, Runnable test) {
    UsbHub usbHub = mockUsbHub(devices);

    try (MockedStatic<UsbUtils> manager = mockStatic(UsbUtils.class)) {
      manager.when(UsbUtils::getRootUsbHub).thenReturn(usbHub);
      test.run();
    }
  }

  private UsbHub mockUsbHub(List<UsbDevice> devices) {
    UsbHub hub = mock(UsbHub.class);
    when(hub.getAttachedUsbDevices()).thenReturn(devices);
    when(hub.isUsbHub()).thenReturn(true);

    return hub;
  }

  private UsbDevice mockUsbPrinter(int vendorId, int productId) {
    return mockUsbDevice(vendorId, productId, true);
  }

  private UsbDevice mockUsbDevice(int vendorId, int productId, boolean isPrinter) {
    UsbDeviceDescriptor desc = mockUsbDescriptor(vendorId, productId);
    UsbDevice dev = mock(UsbDevice.class);

    when(dev.isUsbHub()).thenReturn(false);
    when(dev.getUsbDeviceDescriptor()).thenReturn(desc);

    UsbConfiguration config;
    if (isPrinter) {
      config = mockUsbConfiguration(List.of(mockPrinterInterface()));
    } else {
      config = mockUsbConfiguration(List.of(mockInterface(LibUsb.CLASS_PER_INTERFACE)));
    }
    when(dev.getActiveUsbConfiguration()).thenReturn(config);

    return dev;
  }

  private UsbDeviceDescriptor mockUsbDescriptor(int vendorId, int productId) {
    UsbDeviceDescriptor desc = mock(UsbDeviceDescriptor.class);

    when(desc.idVendor()).thenReturn((short) vendorId);
    when(desc.idProduct()).thenReturn((short) productId);

    return desc;
  }

  private UsbInterface mockPrinterInterface() {
    return mockInterface(LibUsb.CLASS_PRINTER, List.of(mockEndpoint(false), mockEndpoint(true)));
  }

  private UsbInterface mockInterface(byte clazz) {
    return mockInterface(clazz, List.of(mockEndpoint(false), mockEndpoint(true)));
  }

  private UsbInterface mockInterface(byte clazz, List<UsbEndpoint> endpoints) {
    UsbInterfaceDescriptor ifaceDesc = mock(UsbInterfaceDescriptor.class);
    when(ifaceDesc.bInterfaceClass()).thenReturn(clazz);

    UsbInterface iface = mock(UsbInterface.class);
    when(iface.getUsbInterfaceDescriptor()).thenReturn(ifaceDesc);
    when(iface.getUsbEndpoints()).thenReturn(endpoints);

    return iface;
  }

  private UsbConfiguration mockUsbConfiguration(List<UsbInterface> ifaces) {
    UsbConfiguration config = mock(UsbConfiguration.class);

    when(config.getUsbInterfaces()).thenReturn(ifaces);

    return config;
  }

  private UsbEndpoint mockEndpoint(boolean isOut) {
    UsbEndpoint endpoint = mock(UsbEndpoint.class);

    when(endpoint.getDirection()).thenReturn(isOut ? UsbConst.ENDPOINT_DIRECTION_OUT : UsbConst.ENDPOINT_DIRECTION_IN);

    return endpoint;
  }
}
