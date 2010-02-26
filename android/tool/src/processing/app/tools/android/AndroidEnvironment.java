package processing.app.tools.android;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/**
 * <p>An AndroidEnvironment is an object that periodically polls for the existence
 * of running Android devices, both emulated and hardware. You can register to
 * be notified when devices are added to or removed from the environment. You
 * can also ask for an emulator or a hardware device specifically.
 * 
 * <pre> AndroidEnvironment env = new AndroidEnvironment();
 * env.addPropertyChangeListener(...);
 * env.initialize();
 * 
 * AndroidDevice n1 = env.getHardware();</pre>
 * 
 * @author Jonathan Feinberg &lt;jdf@pobox.com&gt;
 *
 */
public class AndroidEnvironment implements AndroidEnvironmentProperties {
  private static final AndroidEnvironment INSTANCE = new AndroidEnvironment();

  public static AndroidEnvironment getInstance() {
    return INSTANCE;
  }

  private final Map<String, AndroidDevice> devices = new ConcurrentHashMap<String, AndroidDevice>();
  private final ExecutorService deviceLaunchThread = Executors
      .newSingleThreadExecutor();

  public static void killAdbServer() {
    System.err.println("Killing server");
    try {
      new ProcessHelper("adb", "kill-server").execute();
      System.err.println("Dead");
    } catch (final Exception e) {
      e.printStackTrace(System.err);
    }
  }

  private AndroidEnvironment() {
    System.err.println("Startup AndroidEnvironment");
    try {
      new ProcessHelper("adb", "start-server").execute();
    } catch (final Exception e) {
      e.printStackTrace(System.err);
    }
    Runtime.getRuntime().addShutdownHook(
      new Thread("AndroidEnvironment Shutdown") {
        @Override
        public void run() {
          shutdown();
        }
      });
  }

  protected void shutdown() {
    System.err.println("Shutting down AndroidEnvironment");
    for (final AndroidDevice device : new ArrayList<AndroidDevice>(devices
        .values())) {
      device.shutdown();
    }
  }

  public Future<AndroidDevice> getEmulator() {
    final Callable<AndroidDevice> androidFinder = new Callable<AndroidDevice>() {
      public AndroidDevice call() throws Exception {
        return blockingGetEmulator();
      }
    };
    final FutureTask<AndroidDevice> task = new FutureTask<AndroidDevice>(
                                                                         androidFinder);
    deviceLaunchThread.execute(task);
    return task;
  }

  private final AndroidDevice blockingGetEmulator() {
    AndroidDevice emu = find(true);
    if (emu != null) {
      return emu;
    }
    try {
      EmulatorController.launch();
    } catch (final IOException e) {
      e.printStackTrace(System.err);
      return null;
    }
    while (!Thread.currentThread().isInterrupted()) {
      try {
        Thread.sleep(2000);
      } catch (final InterruptedException e) {
        return null;
      }
      emu = find(true);
      if (emu != null) {
        return emu;
      }
    }
    return null;
  }

  private AndroidDevice find(final boolean wantEmulator) {
    refresh();
    for (final AndroidDevice device : devices.values()) {
      final boolean isEmulator = device.getId().contains("emulator");
      if ((isEmulator && wantEmulator) || (!isEmulator && !wantEmulator)) {
        return device;
      }
    }
    return null;
  }

  /**
   * @return the first Android hardware device known to be running, or null if there are none.
   */
  public Future<AndroidDevice> getHardware() {
    final Callable<AndroidDevice> androidFinder = new Callable<AndroidDevice>() {
      public AndroidDevice call() throws Exception {
        return blockingGetHardware();
      }
    };
    final FutureTask<AndroidDevice> task = new FutureTask<AndroidDevice>(
                                                                         androidFinder);
    deviceLaunchThread.execute(task);
    return task;
  }

  private final AndroidDevice blockingGetHardware() {
    AndroidDevice emu = find(false);
    if (emu != null) {
      return emu;
    }
    while (!Thread.currentThread().isInterrupted()) {
      try {
        Thread.sleep(2000);
      } catch (final InterruptedException e) {
        return null;
      }
      emu = find(false);
      if (emu != null) {
        return emu;
      }
    }
    return null;
  }

  private void refresh() {
    final List<String> activeDevices = listDevices();
    for (final String deviceId : activeDevices) {
      if (!devices.containsKey(deviceId)) {
        addDevice(new AndroidDevice(this, deviceId));
      }
    }
    for (final String deviceId : new ArrayList<String>(devices.keySet())) {
      if (!activeDevices.contains(deviceId)) {
        devices.get(deviceId).shutdown();
      }
    }
  }

  private void addDevice(final AndroidDevice device) {
    if (devices.put(device.getId(), device) != null) {
      throw new IllegalStateException("Adding " + device
          + ", which already exists!");
    }
    try {
      device.initialize();
    } catch (final Exception e) {
      System.err.println("Cannot initialize " + device + ": " + e);
      devices.remove(device.getId());
    }
  }

  void deviceRemoved(final AndroidDevice device) {
    if (devices.remove(device.getId()) == null) {
      throw new IllegalStateException("I didn't know about device "
          + device.getId() + "!");
    }
  }

  static final String ADB_DEVICES_ERROR = "Received unfamiliar output from “adb devices”.\n"
      + "The device list may have errors.";

  /**
   *    <p>First line starts "List of devices"

        <p>When an emulator is started with a debug port, then it shows up
        in the list of devices.

        <p>List of devices attached
        <br>HT91MLC00031 device
        <br>emulator-5554 offline

        <p>List of devices attached
        <br>HT91MLC00031 device
        <br>emulator-5554 device

   * @return list of device identifiers
   * @throws IOException
   */
  private static List<String> listDevices() {
    ProcessResult result;
    try {
      result = new ProcessHelper("adb", "devices").execute();
    } catch (final InterruptedException e) {
      return Collections.emptyList();
    } catch (final IOException e) {
      System.err.println(e);
      return Collections.emptyList();
    }
    if (!result.succeeded()) {
      System.err.println(result);
      return Collections.emptyList();
    }

    // might read "List of devices attached"
    final String stdout = result.getStdout();
    if (!(stdout.startsWith("List of devices") || stdout.trim().length() == 0)) {
      System.err.println(result);
      System.err.println(ADB_DEVICES_ERROR);
      return Collections.emptyList();
    }

    final List<String> devices = new ArrayList<String>();
    for (final String line : result) {
      if (!line.contains("\t")) {
        continue;
      }
      devices.add(line.split("\t")[0]);
    }
    return devices;
  }
}
