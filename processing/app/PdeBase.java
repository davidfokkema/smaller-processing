/* -*- mode: jde; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  PdeBase - base class for the main processing application
  Part of the Processing project - http://processing.org

  Except where noted, code is written by Ben Fry and
  Copyright (c) 2001-04 Massachusetts Institute of Technology

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.zip.*;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.undo.*;

import com.apple.mrj.*;
import com.ice.jni.registry.*;


/**
 * Primary role of this class is for platform identification and
 * general interaction with the system (launching URLs, loading
 * files and images, etc) that comes from that.
 */
public class PdeBase {
  static final String VERSION = "0076 Alpha";

  static String openedAtStartup;

  //static ClassLoader loader;

  PdeEditor editor;

  static final int WINDOWS = 1;
  static final int MACOS9  = 2;
  static final int MACOSX  = 3;
  static final int LINUX   = 4;
  static final int IRIX    = 5;
  static final int UNKNOWN = 5;
  static int platform;

  static final String platforms[] = {
    "", "windows", "macos9", "macosx", "linux", "irix"
  };


  static public void main(String args[]) {
    if (args.length == 1) {
      PdeBase.openedAtStartup = args[0];
    }

    MRJOpenDocumentHandler startupOpen = new MRJOpenDocumentHandler() {
        public void handleOpenFile(File file) {
          // this will only get set once.. later will be handled
          // by the PdeEditor version of this fella
          if (PdeBase.openedAtStartup == null) {
            //System.out.println("handling outside open file: " + file);
            PdeBase.openedAtStartup = file.getAbsolutePath();
          }
        }
      };
    MRJApplicationUtils.registerOpenDocumentHandler(startupOpen);

    PdeBase app = new PdeBase();
  }


  public PdeBase() {
    // figure out which operating system
    // this has to be first, since editor needs to know

    if (System.getProperty("mrj.version") != null) {  // running on a mac
      platform = (System.getProperty("os.name").equals("Mac OS X")) ?
        MACOSX : MACOS9;

    } else {
      String osname = System.getProperty("os.name");

      if (osname.indexOf("Windows") != -1) {
        platform = WINDOWS;

      } else if (osname.equals("Linux")) {  // true for the ibm vm
        platform = LINUX;

      } else if (osname.equals("Irix")) {
        platform = IRIX;

      } else {
        platform = UNKNOWN;
        //System.out.println("unhandled osname: \"" + osname + "\"");
      }
    }


    // set the look and feel before opening the window

    try {
      if (platform == LINUX) {
        // linux is by default (motif?) even uglier than metal
        // actually, i'm using native menus, so they're ugly and
        // motif-looking. ick. need to fix this.
        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
      } else {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      }
    } catch (Exception e) {
      e.printStackTrace();
    }


    // build the editor object
    editor = new PdeEditor();

    // get things rawkin
    editor.pack();

    // has to be here to set window size properly
    editor.restorePreferences();

    // show the window
    editor.show();


    // maybe?
    //loader = new PdeClassLoader();
  }


  // .................................................................


  static final int kDocumentsFolderType =
    ('d' << 24) | ('o' << 16) | ('c' << 8) | 's';
  static final int kPreferencesFolderType =
    ('p' << 24) | ('r' << 16) | ('e' << 8) | 'f';
  static final int kDomainLibraryFolderType =
    ('d' << 24) | ('l' << 16) | ('i' << 8) | 'b';
  static final short kUserDomain = -32763;


  static public File getProcessingDataFolder() {
    File dataFolder = null;

    String pref = PdePreferences.get("settings.path");
    if (pref != null) {
      dataFolder = new File(pref);

    } else if (platform == MACOSX) {
      // carbon folder constants
      // http://developer.apple.com/documentation/Carbon/Reference
      //   /Folder_Manager/folder_manager_ref/constant_6.html#/
      //   /apple_ref/doc/uid/TP30000238/C006889

      // additional information found int the local file:
      // /System/Library/Frameworks/CoreServices.framework
      //   /Versions/Current/Frameworks/CarbonCore.framework/Headers/

      // this is the 1.4 version.. but using 1.3 since i have the stubs
      // import com.apple.eio.*
      //println(FileManager.findFolder(kUserDomain,
      //        kDomainLibraryFolderType));

      // not clear if i can write to this folder tho..
      try {
        /*
        if (false) {
          // this is because the mrjtoolkit stubs don't have the
          // thows exception around them
          new FileInputStream("ignored");
        }
        */

        // this method has to be dynamically loaded, because
        MRJOSType domainLibrary = new MRJOSType("dlib");
        Method findFolderMethod =
          MRJFileUtils.class.getMethod("findFolder",
                                       new Class[] { Short.TYPE,
                                                     MRJOSType.class });
        File libraryFolder = (File)
          findFolderMethod.invoke(null, new Object[] { new Short(kUserDomain),
                                                       domainLibrary });

        dataFolder = new File(libraryFolder, "Processing");

      } catch (Exception e) {
        // this could be FileNotFound or NoSuchMethod
        //} catch (FileNotFoundException e) {
        //e.printStackTrace();
        //System.exit(1);
        showError("Problem getting data folder",
                  "Error getting the Processing data folder.", e);
      }

    } else if (platform == WINDOWS) {
      // looking for Documents and Settings/blah/Application Data/Processing

      // this is just based on the other documentation, and eyeballing
      // that part of the registry.. not confirmed by any msft/msdn docs.
      // HKEY_CURRENT_USER\Software\Microsoft
      //   \Windows\CurrentVersion\Explorer\Shell Folders
      // Value Name: AppData
      // Value Type: REG_SZ
      // Value Data: path

      try {
        //RegistryKey topKey = Registry.getTopLevelKey("HKCU");
        RegistryKey topKey = Registry.HKEY_CURRENT_USER;

        String localKeyPath =
          "Software\\Microsoft\\Windows\\CurrentVersion" +
          "\\Explorer\\Shell Folders";
        RegistryKey localKey = topKey.openSubKey(localKeyPath);
        String appDataPath = localKey.getStringValue("AppData");
        //System.out.println("app data path is " + appDataPath);
        //System.exit(0);
        //topKey.closeKey();  // necessary?
        //localKey.closeKey();

        dataFolder = new File(appDataPath, "Processing");

      } catch (Exception e) {
        showError("Problem getting data folder",
                  "Error getting the Processing data folder.", e);
      }
      //return null;

    } else {
      // otherwise make a .processing directory int the user's home dir
      File home = new File(System.getProperty("user.home"));
      dataFolder = new File(home, ".processing");
    }

    // create the folder if it doesn't exist already
    boolean result = true;
    if (!dataFolder.exists()) {
      result = dataFolder.mkdirs();
    }

    if (!result) {
      // try the fallback location
      String fallback = PdePreferences.get("settings.path.fallback");
      dataFolder = new File(fallback);
      if (!dataFolder.exists()) {
        result = dataFolder.mkdirs();
      }
    }

    if (!result) {
      showError("Settings issues",
                "Processing cannot run because it could not\n" +
                "create a folder to store your settings.", null);
    }

    return dataFolder;
  }


  static public File getProcessingDataFile(String filename) {
    return new File(getProcessingDataFolder(), filename);
  }


  static public File getBuildFolder() {
    File folder = new File(getProcessingDataFolder(), "build");
    if (!folder.exists()) folder.mkdirs();
    return folder;
  }


  /*
  static public void addBuildFolderToClassPath() {
    String path = getBuildFolder().getAbsolutePath();
    String jcp = System.getProperty("java.class.path");
    if (jcp.indexOf(path) == -1) {
      System.setProperty("java.class.path", path + File.pathSeparator + jcp);
      //return new File(getProcessingDataFolder(), "build");
      System.out.println("jcp is now " +
                         System.getProperty("java.class.path"));
    }
  }
  */


  static public File getDefaultSketchbookFolder() {
    File sketchbookFolder = null;

    if (platform == MACOSX) {
      // looking for /Users/blah/Documents/Processing

      // carbon folder constants
      // http://developer.apple.com/documentation/Carbon/Reference/Folder_Manager/folder_manager_ref/constant_6.html#//apple_ref/doc/uid/TP30000238/C006889

      // additional information found int the local file:
      // /System/Library/Frameworks/CoreServices.framework/Versions/Current/Frameworks/CarbonCore.framework/Headers/

      // this is the 1.4 version.. but using 1.3 since i have the stubs
      // import com.apple.eio.*
      //println(FileManager.findFolder(kUserDomain,
      //        kDomainLibraryFolderType));

      // not clear if i can write to this folder tho..
      try {
        MRJOSType domainDocuments = new MRJOSType("docs");
        File libraryFolder = MRJFileUtils.findFolder(domainDocuments);
          //MRJFileUtils.findFolder(kUserDomain, domainDocuments);
        sketchbookFolder = new File(libraryFolder, "Processing");

      } catch (FileNotFoundException e) {
        showError("sketch folder problem",
                  "Could not locate default sketch folder location.", e);
      }

    } else if (platform == WINDOWS) {
      // looking for Documents and Settings/blah/My Documents/Processing
      // (though using a reg key since it's different on other platforms)

      // http://support.microsoft.com/?kbid=221837&sd=RMVP
      // The path to the My Documents folder is stored in the
      // following registry key, where path is the complete path
      // to your storage location:
      // HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Explorer\Shell Folders
      // Value Name: Personal
      // Value Type: REG_SZ
      // Value Data: path

      try {
        RegistryKey topKey = Registry.HKEY_CURRENT_USER;

        String localKeyPath =
          "Software\\Microsoft\\Windows\\CurrentVersion" +
          "\\Explorer\\Shell Folders";
        RegistryKey localKey = topKey.openSubKey(localKeyPath);
        String personalPath = localKey.getStringValue("Personal");
        //topKey.closeKey();  // necessary?
        //localKey.closeKey();

        sketchbookFolder = new File(personalPath, "Processing");

      } catch (Exception e) {
        showError("Problem getting documents folder",
                  "Error getting the Processing sketchbook folder.", e);
      }

    } else {
      // on linux (or elsewhere?) prompt the user for the location
      JFileChooser fc = new JFileChooser();
      //fc.setSelectedFile(new File(sketchbookLocationField.getText()));
      fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

      int returned = fc.showOpenDialog(new JDialog());
      if (returned == JFileChooser.APPROVE_OPTION) {
        //File file = fc.getSelectedFile();
        //sketchbookLocationField.setText(file.getAbsolutePath());
        sketchbookFolder = fc.getSelectedFile();

      } else {
        System.exit(0);
      }
    }

    // create the folder if it doesn't exist already
    boolean result = true;
    if (!sketchbookFolder.exists()) {
      result = sketchbookFolder.mkdirs();
    }

    if (!result) {
      // try the fallback location
      String fallback = PdePreferences.get("sketchbook.path.fallback");
      sketchbookFolder = new File(fallback);
      if (!sketchbookFolder.exists()) {
        result = sketchbookFolder.mkdirs();
      }
    }

    if (!result) {
      showError("error",
                "Processing cannot run because it could not\n" +
                "create a folder to store your sketchbook.", null);
    }

    return sketchbookFolder;
  }


  /*
  static public File getProcessingHome() {
    File home = new File(System.getProperty("user.home"));

    File phome = null;

    // NOTE if this location changes, be sure to note the change
    // in the default preferences.txt because it mentions this path
    if (PdeBase.platform == PdeBase.MACOSX) {
      // on macosx put the sketchbook in the "Documents" folder
      //phome = new File(home, "Documents" + File.separator + "Processing");

    } else if (PdeBase.platform == PdeBase.WINDOWS) {
      // on windows put the sketchbook in the "My Documents" folder
      phome = new File(home, "My Documents" + File.separator + "Processing");

    } else {
      // for linux et al, make a dot folder
      // if people don't like things being buried, they can move the sketches
      // but the prefs will stay hidden in the dot folder
      phome = new File(home, ".processing");
    }
    if (!phome.exists()) phome.mkdirs();
    return phome;
  }
  */


  // .................................................................


  /**
   * Given the reference filename from the keywords list,
   * builds a URL and passes it to openURL.
   */
  static public void showReference(String referenceFile) {
    String currentDir = System.getProperty("user.dir");
    openURL(currentDir + File.separator +
            "reference" + File.separator +
            referenceFile + ".html");
  }


  /**
   * Implements the cross-platform headache of opening URLs
   */
  static public void openURL(String url) {
    //System.out.println("opening url " + url);
    try {
      if (platform == WINDOWS) {
        // this is not guaranteed to work, because who knows if the
        // path will always be c:\progra~1 et al. also if the user has
        // a different browser set as their default (which would
        // include me) it'd be annoying to be dropped into ie.
        //Runtime.getRuntime().exec("c:\\progra~1\\intern~1\\iexplore "
        // + currentDir

        // the following uses a shell execute to launch the .html file
        // note that under cygwin, the .html files have to be chmodded +x
        // after they're unpacked from the zip file. i don't know why,
        // and don't understand what this does in terms of windows
        // permissions. without the chmod, the command prompt says
        // "Access is denied" in both cygwin and the "dos" prompt.
        //Runtime.getRuntime().exec("cmd /c " + currentDir + "\\reference\\" +
        //                    referenceFile + ".html");
        if (url.startsWith("http://")) {
          // open dos prompt, give it 'start' command, which will
          // open the url properly. start by itself won't work since
          // it appears to need cmd
          Runtime.getRuntime().exec("cmd /c start " + url);
        } else {
          // just launching the .html file via the shell works
          // but make sure to chmod +x the .html files first
          // also place quotes around it in case there's a space
          // in the user.dir part of the url
          Runtime.getRuntime().exec("cmd /c \"" + url + "\"");
        }

      } else if (platform == MACOSX) {
        //com.apple.eio.FileManager.openURL(url);

        if (!url.startsWith("http://")) {
          // prepend file:// on this guy since it's a file
          url = "file://" + url;

          // replace spaces with %20 for the file url
          // otherwise the mac doesn't like to open it
          // can't just use URLEncoder, since that makes slashes into
          // %2F characters, which is no good. some might say "useless"
          if (url.indexOf(' ') != -1) {
            StringBuffer sb = new StringBuffer();
            char c[] = url.toCharArray();
            for (int i = 0; i < c.length; i++) {
              if (c[i] == ' ') {
                sb.append("%20");
              } else {
                sb.append(c[i]);
              }
            }
            url = sb.toString();
          }
        }
        //System.out.println("trying to open " + url);
        com.apple.mrj.MRJFileUtils.openURL(url);

      } else if (platform == MACOS9) {
        com.apple.mrj.MRJFileUtils.openURL(url);

      } else if (platform == LINUX) {
        // how's mozilla sound to ya, laddie?
        //Runtime.getRuntime().exec(new String[] { "mozilla", url });
        String browser = PdePreferences.get("browser");
        Runtime.getRuntime().exec(new String[] { browser, url });

      } else {
        System.err.println("unspecified platform");
      }

    } catch (IOException e) {
      PdeBase.showWarning("Could not open URL",
                          "An error occurred while trying to open\n" + url, e);

      //e.printStackTrace();
    }
  }


  /**
   * Implements the other cross-platform headache of opening
   * a folder in the machine's native file browser.
   */
  static public void openFolder(File file) {
    try {
      String folder = file.getAbsolutePath();

      if (platform == WINDOWS) {
        // doesn't work
        //Runtime.getRuntime().exec("cmd /c \"" + folder + "\"");

        // works fine on winxp, prolly win2k as well
        Runtime.getRuntime().exec("explorer \"" + folder + "\"");

        // not tested
        //Runtime.getRuntime().exec("start explorer \"" + folder + "\"");

      } else if (platform == MACOSX) {
        openURL(folder);  // handles char replacement, etc

      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }


  /**
   * "No cookie for you" type messages. Nothing fatal or all that
   * much of a bummer, but something to notify the user about.
   */
  static public void showMessage(String title, String message) {
    if (title == null) title = "Message";
    JOptionPane.showMessageDialog(new Frame(), message, title,
                                  JOptionPane.INFORMATION_MESSAGE);
  }


  /**
   * Non-fatal error message with optional stack trace side dish.
   */
  static public void showWarning(String title, String message,
                                 Exception e) {
    if (title == null) title = "Warning";
    JOptionPane.showMessageDialog(new Frame(), message, title,
                                  JOptionPane.WARNING_MESSAGE);

    //System.err.println(e.toString());
    if (e != null) e.printStackTrace();
  }


  /**
   * Show an error message that's actually fatal to the program.
   * This is an error that can't be recovered. Use showWarning()
   * for errors that allow P5 to continue running.
   */
  static public void showError(String title, String message,
                               Exception e) {
    if (title == null) title = "Error";
    JOptionPane.showMessageDialog(new Frame(), message, title,
                                  JOptionPane.ERROR_MESSAGE);

    if (e != null) e.printStackTrace();
    System.exit(1);
  }


  // ...................................................................


  static public Image getImage(String name, Component who) {
    Image image = null;
    Toolkit tk = Toolkit.getDefaultToolkit();

    //if ((PdeBase.platform == PdeBase.MACOSX) ||
    //(PdeBase.platform == PdeBase.MACOS9)) {
    image = tk.getImage("lib/" + name);
    //} else {
    //image = tk.getImage(who.getClass().getResource(name));
    //}

    //image =  tk.getImage("lib/" + name);
    //URL url = PdeApplet.class.getResource(name);
    //image = tk.getImage(url);
    //}
    //MediaTracker tracker = new MediaTracker(applet);
    MediaTracker tracker = new MediaTracker(who); //frame);
    tracker.addImage(image, 0);
    try {
      tracker.waitForAll();
    } catch (InterruptedException e) { }
    return image;
  }


  static public InputStream getStream(String filename) throws IOException {
    //if (PdeBase.platform == PdeBase.MACOSX) {
    // macos doesn't seem to think that files in the lib folder
    // are part of the resources, unlike windows or linux.
    // actually, this is only the case when running as a .app,
    // since it works fine from run.sh, but not Processing.app
    return new FileInputStream("lib/" + filename);
    //}

    // all other, more reasonable operating systems
    //return cls.getResource(filename).openStream();
    //return PdeBase.class.getResource(filename).openStream();
  }


  // ...................................................................


  static public byte[] grabFile(File file) throws IOException {
    int size = (int) file.length();
    FileInputStream input = new FileInputStream(file);
    byte buffer[] = new byte[size];
    int offset = 0;
    int bytesRead;
    while ((bytesRead = input.read(buffer, offset, size-offset)) != -1) {
      offset += bytesRead;
      if (bytesRead == 0) break;
    }
    input.close();  // weren't properly being closed
    input = null;
    return buffer;
  }


  static public void copyFile(File afile, File bfile) throws IOException {
    InputStream from = new BufferedInputStream(new FileInputStream(afile));
    OutputStream to = new BufferedOutputStream(new FileOutputStream(bfile));
    byte[] buffer = new byte[16 * 1024];
    int bytesRead;
    while ((bytesRead = from.read(buffer)) != -1) {
      to.write(buffer, 0, bytesRead);
    }
    to.flush();
    from.close(); // ??
    from = null;
    to.close(); // ??
    to = null;

    bfile.setLastModified(afile.lastModified());  // jdk13+ required
  //} catch (IOException e) {
  //  e.printStackTrace();
  //}
  }


  /**
   * Grab the contents of a file as a string.
   */
  static public String loadFile(File file) throws IOException {
    // empty code file.. no worries, might be getting filled up later
    if (file.length() == 0) return "";

    InputStreamReader isr = new InputStreamReader(new FileInputStream(file));
    BufferedReader reader = new BufferedReader(isr);

    StringBuffer buffer = new StringBuffer();
    String line = null;
    while ((line = reader.readLine()) != null) {
      buffer.append(line);
      buffer.append('\n');
    }
    reader.close();
    return buffer.toString();
  }


  /**
   * Spew the contents of a String object out to a file.
   */
  static public void saveFile(String str,
                              File file) throws IOException {

    ByteArrayInputStream bis = new ByteArrayInputStream(str.getBytes());
    InputStreamReader isr = new InputStreamReader(bis);
    BufferedReader reader = new BufferedReader(isr);

    FileWriter fw = new FileWriter(file);
    PrintWriter writer = new PrintWriter(new BufferedWriter(fw));

    String line = null;
    while ((line = reader.readLine()) != null) {
      writer.println(line);
    }
    writer.flush();
    writer.close();
  }


  static public void copyDir(File sourceDir,
                             File targetDir) throws IOException {
    targetDir.mkdirs();
    String files[] = sourceDir.list();
    for (int i = 0; i < files.length; i++) {
      if (files[i].equals(".") || files[i].equals("..")) continue;
      File source = new File(sourceDir, files[i]);
      File target = new File(targetDir, files[i]);
      if (source.isDirectory()) {
        //target.mkdirs();
        copyDir(source, target);
        target.setLastModified(source.lastModified());
      } else {
        copyFile(source, target);
      }
    }
  }


  /**
   * Remove all files in a directory and the directory itself.
   */
  static public void removeDir(File dir) {
    if (dir.exists()) {
      removeDescendants(dir);
      dir.delete();
    }
  }


  /**
   * Recursively remove all files within a directory,
   * used with removeDir(), or when the contents of a dir
   * should be removed, but not the directory itself.
   * (i.e. when cleaning temp files from lib/build)
   */
  static public void removeDescendants(File dir) {
    if (!dir.exists()) return;

    String files[] = dir.list();
    for (int i = 0; i < files.length; i++) {
      if (files[i].equals(".") || files[i].equals("..")) continue;
      File dead = new File(dir, files[i]);
      if (!dead.isDirectory()) {
        if (!PdePreferences.getBoolean("compiler.save_build_files")) {
          if (!dead.delete()) {
            // temporarily disabled
            //System.err.println("couldn't delete " + dead);
          }
        }
      } else {
        removeDir(dead);
        //dead.delete();
      }
    }
  }


  /**
   * Calculate the size of the contents of a folder.
   * Used to determine whether sketches are empty or not.
   * Note that the function calls itself recursively.
   */
  static public int calcFolderSize(File folder) {
    int size = 0;

    String files[] = folder.list();
    // null if folder doesn't exist, happens when deleting sketch
    if (files == null) return -1;

    for (int i = 0; i < files.length; i++) {
      if (files[i].equals(".") || (files[i].equals("..")) ||
          files[i].equals(".DS_Store")) continue;
      File fella = new File(folder, files[i]);
      if (fella.isDirectory()) {
        size += calcFolderSize(fella);
      } else {
        size += (int) fella.length();
      }
    }
    return size;
  }


  /**
   * Equivalent to the one in PApplet, but static (die() is removed)
   */
  static public String[] loadStrings(File file) {
    try {
      FileInputStream input = new FileInputStream(file);
      BufferedReader reader =
        new BufferedReader(new InputStreamReader(input));

      String lines[] = new String[100];
      int lineCount = 0;
      String line = null;
      while ((line = reader.readLine()) != null) {
        if (lineCount == lines.length) {
          String temp[] = new String[lineCount << 1];
          System.arraycopy(lines, 0, temp, 0, lineCount);
          lines = temp;
        }
        lines[lineCount++] = line;
      }
      reader.close();

      if (lineCount == lines.length) {
        return lines;
      }

      // resize array to appropraite amount for these lines
      String output[] = new String[lineCount];
      System.arraycopy(lines, 0, output, 0, lineCount);
      return output;

    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }
}
