package io.nmoncho.faradn.printer;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import javax.usb.UsbConst;
import javax.usb.UsbDevice;
import javax.usb.UsbDeviceDescriptor;
import javax.usb.UsbEndpoint;
import javax.usb.UsbException;
import javax.usb.UsbHostManager;
import javax.usb.UsbHub;
import javax.usb.UsbInterface;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.usb4java.LibUsb;

import io.nmoncho.faradn.PrintingException;

public class Devices {

  private static final String CONFIG_PATH = "vendors";
  private static final String CONFIG_ID = "id";
  private static final String CONFIG_NAME = "name";

  private static final Logger log = LoggerFactory.getLogger(Devices.class);

  static class Vendor {
    private final short id;
    private final String name;

    public Vendor(int id, String name) {
      this(Integer.valueOf(id).shortValue(), name);
    }

    public Vendor(short id, String name) {
      this.id = id;
      this.name = name;
    }

    public short getId() {
      return id;
    }

    public String getName() {
      return name;
    }
  }

  public static class UsbUtils {
    public static UsbHub getRootUsbHub() {
      try {
        return UsbHostManager.getUsbServices().getRootUsbHub();
      } catch (UsbException ex) {
        throw new PrintingException("Something went wrong while getting the USB Root Hub", ex);
      }
    }
  }

  private final static List<Vendor> vendors;

  static {
    Config config = ConfigFactory.load();
    List<? extends Config> vendorsConfig = config.getConfigList(CONFIG_PATH);
    vendors = new LinkedList<>();

    for (Config conf : vendorsConfig) {
      vendors.add(new Vendor(conf.getInt(CONFIG_ID), conf.getString(CONFIG_NAME)));
    }
  }

  /**
   * Finds a Device by its vendor name, if there are several with the same value,
   * it will pick the first.
   *
   * @param vendorName
   *        Vendor's name (must be configured in the vendors list)
   * @return Some device if found, empty otherwise
   */
  public static Optional<UsbDevice> findDevice(String vendorName) {
    Optional<Vendor> vendor = vendors.stream().filter(v -> v.name.equals(vendorName)).findFirst();

    if (vendor.isEmpty()) {
      log.info("Couldn't find registered vendor by name [{}]", vendorName);
    }

    return vendor.flatMap(v -> findDevice(v.id));
  }

  /**
   * Finds a Device by its `vendorId` and `productId`, as defined in <a href=
   * "https://www.usb.org/sites/default/files/vendor_ids032322.pdf_1.pdf">Valid
   * USB Vendor ID Numbers</a>.
   *
   * @param vendorId
   *        Device's Vendor ID.
   * @param productId
   *        Device's Product ID
   * @return Some device if found, empty otherwise
   */
  public static Optional<UsbDevice> findDevice(short vendorId, short productId) {
    try {
      return findDevice(
          UsbUtils.getRootUsbHub(),
          descriptor -> descriptor.idVendor() == vendorId && descriptor.idProduct() == productId);
    } catch (SecurityException ex) {
      throw new PrintingException("Something went wrong while finding a USB device", ex);
    }
  }

  /**
   * Finds a Device by its `vendorId`, as defined in <a href=
   * "https://www.usb.org/sites/default/files/vendor_ids032322.pdf_1.pdf">Valid
   * USB Vendor ID Numbers</a>.
   *
   * @param vendorId
   *        Device's Vendor ID.
   * @return Some device if found, empty otherwise
   */
  public static Optional<UsbDevice> findDevice(short vendorId) {
    try {
      return findDevice(
          UsbUtils.getRootUsbHub(),
          descriptor -> descriptor.idVendor() == vendorId);
    } catch (SecurityException ex) {
      throw new PrintingException("Something went wrong while finding a USB device", ex);
    }
  }

  /**
   * Finds a {@link UsbDevice} by the specified predicate.
   *
   * @param pred
   *        predicate tested against the Device's
   *        {@link UsbDeviceDescriptor}
   * @return some Device, if found. Empty otherwise.
   */
  private static Optional<UsbDevice> findDevice(UsbHub hub, Predicate<UsbDeviceDescriptor> pred) {
    List<UsbDevice> devices = (List<UsbDevice>) hub.getAttachedUsbDevices();
    Optional<UsbDevice> found = Optional.empty();

    for (UsbDevice device : devices) {
      if (device.isUsbHub()) {
        found = findDevice((UsbHub) device, pred);
      } else if (pred.test(device.getUsbDeviceDescriptor())) {
        found = Optional.of(device);
      }

      if (found.isPresent()) {
        return found;
      }
    }

    return Optional.empty();
  }

  /**
   * Lists the available USB devices, excluding USB Hubs.
   *
   * @return connected USB devices.
   */
  public static List<UsbDevice> listDevices() {
    try {
      UsbHub rootUsbHub = UsbUtils.getRootUsbHub();
      return listDevices(rootUsbHub, dev -> true);
    } catch (SecurityException ex) {
      throw new PrintingException("Something went wrong while listing USB devices", ex);
    }
  }

  /**
   * Lists the available USB printers.
   *
   * @return connected USB printers.
   */
  public static List<UsbDevice> listPrinterDevices() {
    try {
      UsbHub rootUsbHub = UsbUtils.getRootUsbHub();
      return listDevices(rootUsbHub, dev -> findPrinterInterface(dev).isPresent());
    } catch (SecurityException ex) {
      throw new PrintingException("Something went wrong while listing USB printers", ex);
    }
  }

  private static List<UsbDevice> listDevices(UsbHub hub, Predicate<UsbDevice> pred) {
    List<UsbDevice> available = new LinkedList<>();
    List<UsbDevice> devices = (List<UsbDevice>) hub.getAttachedUsbDevices();

    for (UsbDevice device : devices) {
      if (device.isUsbHub()) {
        available.addAll(listDevices((UsbHub) device, pred));
      } else if (pred.test(device)) {
        available.add(device);
      }
    }

    return available;
  }

  /**
   * Finds the first most appropriate {@link javax.usb.UsbInterface} for a given
   * {@link javax.usb.UsbDevice}, that is, one that has a PRINTER class.
   *
   * @param device
   *        to inspect
   * @return a present Interface, if it's marked as a PRINTER. Empty otherwise.
   */
  public static Optional<UsbInterface> findPrinterInterface(UsbDevice device) {
    return findInterface(
        device,
        iface -> iface.getUsbInterfaceDescriptor().bInterfaceClass() == LibUsb.CLASS_PRINTER);
  }

  /**
   * Finds a {@link javax.usb.UsbInterface} for a given
   * {@link javax.usb.UsbDevice}
   * considering the provided {@link java.util.function.Predicate}
   *
   * @param device
   *        to inspect
   * @param pred
   *        to test against a device's interface
   * @return a present Interface, if it passed the predicate. Empty otherwise.
   */
  public static Optional<UsbInterface> findInterface(UsbDevice device, Predicate<UsbInterface> pred) {
    return ((List<UsbInterface>) device.getActiveUsbConfiguration().getUsbInterfaces())
        .stream()
        .filter(pred)
        .findFirst();
  }

  /**
   * Finds the first most appropriate {@link javax.usb.UsbEndpoint} for a given
   * {@link UsbInterface}, that is,
   * one that writes to the device.
   *
   * @param iface
   *        to inspect
   * @return a present Endpoint. Empty otherwise.
   */
  public static Optional<UsbEndpoint> findOutEndpoint(UsbInterface iface) {
    return findEndpoint(
        iface,
        endpoint -> endpoint.getDirection() == UsbConst.ENDPOINT_DIRECTION_OUT);
  }

  /**
   * Finds the first {@link javax.usb.UsbEndpoint} that reads from the device,
   * used for the real-time status back-channel, if the interface exposes one.
   *
   * @param iface
   *        to inspect
   * @return a present IN Endpoint, empty otherwise.
   */
  public static Optional<UsbEndpoint> findInEndpoint(UsbInterface iface) {
    return findEndpoint(
        iface,
        endpoint -> endpoint.getDirection() == UsbConst.ENDPOINT_DIRECTION_IN);
  }

  /**
   * Finds the a {@link javax.usb.UsbEndpoint} for a given {@link UsbInterface},
   * for the given {@link java.util.function.Predicate}
   *
   * @param iface
   *        to inspect
   * @param pred
   *        to test against a interface's endpoint
   * @return a present Endpoint. Empty otherwise.
   */
  public static Optional<UsbEndpoint> findEndpoint(UsbInterface iface, Predicate<UsbEndpoint> pred) {
    return ((List<UsbEndpoint>) iface.getUsbEndpoints())
        .stream()
        .filter(pred)
        .findFirst();
  }
}
