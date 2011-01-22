/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2009-11 Ben Fry and Casey Reas

 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License version 2
 as published by the Free Software Foundation.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software Foundation,
 Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package processing.mode.android;

import java.io.*;
import java.util.*;

import org.apache.tools.ant.*;

import processing.app.*;
import processing.app.exec.*;
import processing.core.PApplet;
import processing.mode.java.JavaBuild;


class AndroidBuild extends JavaBuild {
  static final String basePackage = "processing.android.test";
  static final String sdkVersion = "7";

//  private final Editor editor;
  private final AndroidSDK sdk;

  Manifest manifest;
  String className;
  File tempBuildFolder;
  File buildFile;


  public AndroidBuild(final Sketch sketch, final AndroidSDK sdk) {
    super(sketch);
    this.sdk = sdk;
  }

  
  public File createProject(String target) throws IOException, SketchException {
//    try {
    tempBuildFolder = createTempBuildFolder(sketch);
//    } catch (final IOException e) {
//      editor.statusError(e);
//      return null;
//    }

    // Create the 'src' folder with the preprocessed code.
    final File srcFolder = new File(tempBuildFolder, "src");
    if (processing.app.Base.DEBUG) {
      Base.openFolder(tempBuildFolder);
    }

//    try {
    manifest = new Manifest(sketch);
    // grab code from current editing window (GUI only)
    sketch.prepare();
    // build the preproc and get to work
    AndroidPreprocessor preproc = new AndroidPreprocessor(sketch, getPackageName());
    if (!preproc.parseSketchSize()) {
      editor.statusError("Could not parse the size() command.");
      return null; 
    }
    className = preprocess(srcFolder.getAbsolutePath(), 
                           manifest.getPackageName(), 
                           preproc);
    if (className != null) {
//        final File androidXML = new File(tempBuildFolder, "AndroidManifest.xml");
//        writeAndroidManifest(androidXML, sketch.getName(), className);
//        manifest.setClassName(className);
      File tempManifest = new File(tempBuildFolder, "AndroidManifest.xml");
      manifest.writeBuild(tempManifest, className, target.equals("debug"));

      writeBuildProps(new File(tempBuildFolder, "build.properties"));
      buildFile = new File(tempBuildFolder, "build.xml");
      writeBuildXML(buildFile, sketch.getName());
      writeDefaultProps(new File(tempBuildFolder, "default.properties"));
      writeLocalProps(new File(tempBuildFolder, "local.properties"));
      writeRes(new File(tempBuildFolder, "res"), className);

      final File libsFolder = mkdirs(tempBuildFolder, "libs");
      final File assetsFolder = mkdirs(tempBuildFolder, "assets");

      final InputStream input = 
        PApplet.createInput(AndroidEditor.getCoreZipLocation());
      PApplet.saveStream(new File(libsFolder, "processing-core.jar"), input);

      try {
        // Copy any imported libraries or code folder contents to the project
        writeLibraries(libsFolder, assetsFolder);

        // Copy the data folder, if one exists, to the 'assets' folder of the
        // project
        final File sketchDataFolder = sketch.getDataFolder();
        if (sketchDataFolder.exists()) {
          Base.copyDir(sketchDataFolder, assetsFolder);
        }
      } catch (final IOException e) {
        e.printStackTrace();
        throw new SketchException(e.getMessage());
      }
    }
//    } catch (final SketchException e) {
//      editor.statusError(e);
//      return null;
//    } catch (final IOException e) {
//      editor.statusError(e);
//      return null;
//    }
    return tempBuildFolder;
  }


  /**
   * The Android dex util pukes on paths containing spaces, which will happen
   * most of the time on Windows, since Processing sketches wind up in
   * "My Documents". Therefore, build android in a temp file.
   * http://code.google.com/p/android/issues/detail?id=4567
   *
   * TODO: better would be to retrieve the 8.3 name for the sketch folder!
   *
   * @param sketch
   * @return A folder in which to build the android sketch
   * @throws IOException
   */
  private File createTempBuildFolder(final Sketch sketch) throws IOException {
    final File tmp = File.createTempFile("android", ".pde");
    if (!(tmp.delete() && tmp.mkdir())) {
      throw new IOException("Cannot create temp dir " + tmp
          + " to build android sketch");
    }
    return tmp;
  }


  protected File createExportFolder() throws IOException {
//    Sketch sketch = editor.getSketch();
    // Create the 'android' build folder, and move any existing version out. 
    File androidFolder = new File(sketch.getFolder(), "android");
    if (androidFolder.exists()) {
//      Date mod = new Date(androidFolder.lastModified());
      String stamp = AndroidEditor.getDateStamp(androidFolder.lastModified());
      File dest = new File(sketch.getFolder(), "android." + stamp);
      boolean result = androidFolder.renameTo(dest);
      if (!result) {
        ProcessHelper mv;
        ProcessResult pr;
        try {
          System.err.println("createProject renameTo() failed, resorting to mv/move instead.");
          mv = new ProcessHelper("mv", androidFolder.getAbsolutePath(), dest.getAbsolutePath());
          pr = mv.execute();

//        } catch (IOException e) {
//          editor.statusError(e);
//          return null;
//
        } catch (InterruptedException e) {
          e.printStackTrace();
          return null;
        }
        if (!pr.succeeded()) {
          System.err.println(pr.getStderr());
          Base.showWarning("Failed to rename", 
                           "Could not rename the old “android” build folder.\n" + 
                           "Please delete, close, or rename the folder\n" + 
                           androidFolder.getAbsolutePath() + "\n" +  
                           "and try again." , null);
          Base.openFolder(sketch.getFolder());
          return null;
        }
      }
    } else {
      boolean result = androidFolder.mkdirs();
      if (!result) {
        Base.showWarning("Folders, folders, folders", 
                         "Could not create the necessary folders to build.\n" +
                         "Perhaps you have some file permissions to sort out?", null);
        return null;
      }
    }
    return androidFolder;
  }
  

  /**
   * @param target "debug" or "release"
   */
  protected boolean antBuild(final String target) {
    final Project p = new Project();
    String path = buildFile.getAbsolutePath().replace('\\', '/');
    p.setUserProperty("ant.file", path);

    // deals with a problem where javac error messages weren't coming through
    p.setUserProperty("build.compiler", "extJavac");
    // p.setUserProperty("build.compiler.emacs", "true"); // does nothing

    final DefaultLogger consoleLogger = new DefaultLogger();
    consoleLogger.setErrorPrintStream(System.err);
    //    consoleLogger.setOutputPrintStream(System.out);
    consoleLogger.setMessageOutputLevel(Project.MSG_ERR);
    p.addBuildListener(consoleLogger);

    final DefaultLogger errorLogger = new DefaultLogger();
    final ByteArrayOutputStream errb = new ByteArrayOutputStream();
    final PrintStream errp = new PrintStream(errb);
    errorLogger.setErrorPrintStream(errp);
    final ByteArrayOutputStream outb = new ByteArrayOutputStream();
    final PrintStream outp = new PrintStream(outb);
    errorLogger.setOutputPrintStream(outp);
    errorLogger.setMessageOutputLevel(Project.MSG_INFO);
    p.addBuildListener(errorLogger);

    try {
      editor.statusNotice("Building sketch for Android...");
      p.fireBuildStarted();
      p.init();
      final ProjectHelper helper = ProjectHelper.getProjectHelper();
      p.addReference("ant.projectHelper", helper);
      helper.parse(p, buildFile);
      // p.executeTarget(p.getDefaultTarget());
      p.executeTarget(target);
      editor.statusNotice("Finished building sketch.");
      return true;

    } catch (final BuildException e) {
      // Send a "build finished" event to the build listeners for this project.
      p.fireBuildFinished(e);

      // PApplet.println(new String(errb.toByteArray()));
      // PApplet.println(new String(outb.toByteArray()));

      // String errorOutput = new String(errb.toByteArray());
      // String[] errorLines =
      // errorOutput.split(System.getProperty("line.separator"));
      // PApplet.println(errorLines);

      final String outPile = new String(outb.toByteArray());
      final String[] outLines = outPile.split(System.getProperty("line.separator"));
      // PApplet.println(outLines);

      for (final String line : outLines) {
        final String javacPrefix = "[javac]";
        final int javacIndex = line.indexOf(javacPrefix);
        if (javacIndex != -1) {
          // System.out.println("checking: " + line);
//          final Sketch sketch = editor.getSketch();
          // String sketchPath = sketch.getFolder().getAbsolutePath();
          int offset = javacIndex + javacPrefix.length() + 1;
          String[] pieces = 
            PApplet.match(line.substring(offset), "^(.+):([0-9]+):\\s+(.+)$");
          if (pieces != null) {
            // PApplet.println(pieces);
            String fileName = pieces[1];
            // remove the path from the front of the filename
            fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
            final int lineNumber = PApplet.parseInt(pieces[2]) - 1;
            // PApplet.println("looking for " + fileName + " line " +
            // lineNumber);
            final SketchException rex = placeException(pieces[3],
              fileName, lineNumber);
            if (rex != null) {
              rex.hideStackTrace();
              editor.statusError(rex);
              return false; // get outta here
            }
          }
        }
      }
      editor.statusError(e);
    }
    return false;
  }

  
  protected String getClassName() {
    return className;
  }

  
  String getPathForAPK(final String target) {
    String suffix = target.equals("release") ? "unsigned" : "debug";
    String apkName = "bin/" + sketch.getName() + "-" + suffix + ".apk";
    final File apkFile = new File(tempBuildFolder, apkName);
    return apkFile.getAbsolutePath();
  }

    
  private void writeBuildProps(final File file) {
    final PrintWriter writer = PApplet.createWriter(file);
    writer.println("application-package=" + getPackageName());
    writer.flush();
    writer.close();
  }

  
  private void writeBuildXML(final File file, final String projectName) {
    final PrintWriter writer = PApplet.createWriter(file);
    writer.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");

    writer.println("<project name=\"" + projectName + "\" default=\"help\">");
    writer.println("  <property file=\"local.properties\"/>");
    writer.println("  <property file=\"build.properties\"/>");
    writer.println("  <property file=\"default.properties\"/>");

    writer.println("  <path id=\"android.antlibs\">");
    writer.println("    <pathelement path=\"${sdk.dir}/tools/lib/anttasks.jar\" />");
    writer.println("    <pathelement path=\"${sdk.dir}/tools/lib/sdklib.jar\" />");
    writer.println("    <pathelement path=\"${sdk.dir}/tools/lib/androidprefs.jar\" />");
    writer.println("    <pathelement path=\"${sdk.dir}/tools/lib/apkbuilder.jar\" />");
    writer.println("    <pathelement path=\"${sdk.dir}/tools/lib/jarutils.jar\" />");
    writer.println("  </path>");

    writer.println("  <taskdef name=\"setup\"");
    writer.println("           classname=\"com.android.ant.SetupTask\"");
    writer.println("           classpathref=\"android.antlibs\" />");

    writer.println("  <setup />");

    writer.println("</project>");
    writer.flush();
    writer.close();
  }

  
  private void writeDefaultProps(final File file) {
    final PrintWriter writer = PApplet.createWriter(file);
    writer.println("target=Google Inc.:Google APIs:" + sdkVersion);
    writer.flush();
    writer.close();
  }

  
  private void writeLocalProps(final File file) {
    final PrintWriter writer = PApplet.createWriter(file);
    final String sdkPath = sdk.getSdkFolder().getAbsolutePath();
    if (Base.isWindows()) {
      // Windows needs backslashes escaped, or it will also accept forward
      // slashes in the build file. We're using the forward slashes since this
      // path gets concatenated with a lot of others that use forwards anyway.
      writer.println("sdk.dir=" + sdkPath.replace('\\', '/'));
    } else {
      writer.println("sdk.dir=" + sdkPath);
    }
    writer.flush();
    writer.close();
  }

  
  static final String ICON_72 = "icon-72.png";
  static final String ICON_48 = "icon-48.png";
  static final String ICON_36 = "icon-36.png";

  private void writeRes(File resFolder, 
                        String className) throws SketchException {
    File layoutFolder = mkdirs(resFolder, "layout");
    File layoutFile = new File(layoutFolder, "main.xml");
    writeResLayoutMain(layoutFile);

    // write the icon files
    File sketchFolder = sketch.getFolder();
    File localIcon36 = new File(sketchFolder, ICON_36);
    File localIcon48 = new File(sketchFolder, ICON_48);
    File localIcon72 = new File(sketchFolder, ICON_72);
    
//    File drawableFolder = new File(resFolder, "drawable");
//    drawableFolder.mkdirs()
    File buildIcon48 = new File(resFolder, "drawable/icon.png");
    File buildIcon36 = new File(resFolder, "drawable-ldpi/icon.png");
    File buildIcon72 = new File(resFolder, "drawable-hdpi/icon.png");

    if (!localIcon36.exists() && 
        !localIcon48.exists() && 
        !localIcon72.exists()) {
      // if no icons are in the sketch folder, then copy all the defaults
      if (new File(resFolder, "drawable-ldpi").mkdirs()) {
        PApplet.saveStream(buildIcon36, getClass().getResourceAsStream("data/icon-36.png"));
      } else {
        System.err.println("Could not create \"drawable-ldpi\" folder.");
      }
      if (new File(resFolder, "drawable").mkdirs()) {
        PApplet.saveStream(buildIcon48, getClass().getResourceAsStream("data/icon-48.png"));
      } else {
        System.err.println("Could not create \"drawable\" folder.");
      }
      if (new File(resFolder, "drawable-hdpi").mkdirs()) {
        PApplet.saveStream(buildIcon72, getClass().getResourceAsStream("data/icon-72.png"));
      } else {
        System.err.println("Could not create \"drawable-hdpi\" folder.");
      }
    } else {
      // if at least one of the icons already exists, then use that across the board
      try {
        if (localIcon36.exists()) {
          if (new File(resFolder, "drawable-ldpi").mkdirs()) {
            Base.copyFile(localIcon36, buildIcon36);
          }
        }
        if (localIcon48.exists()) {
          if (new File(resFolder, "drawable").mkdirs()) {
            Base.copyFile(localIcon48, buildIcon48);
          }
        }
        if (localIcon72.exists()) {
          if (new File(resFolder, "drawable-hdpi").mkdirs()) {
            Base.copyFile(localIcon72, buildIcon72);
          }
        }
      } catch (IOException e) {
        System.err.println("Problem while copying icons.");
        e.printStackTrace();
      }
    }
    
//    final File valuesFolder = mkdirs(resFolder, "values");
//    final File stringsFile = new File(valuesFolder, "strings.xml");
//    writeResValuesStrings(stringsFile, className);
  }

  
  private File mkdirs(final File parent, final String name) throws SketchException {
    final File result = new File(parent, name);
    if (!(result.exists() || result.mkdirs())) {
      throw new SketchException("Could not create " + result);
    }
    return result;
  }

  
  private void writeResLayoutMain(final File file) {
    final PrintWriter writer = PApplet.createWriter(file);
    writer.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
    writer.println("<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"");
    writer.println("              android:orientation=\"vertical\"");
    writer.println("              android:layout_width=\"fill_parent\"");
    writer.println("              android:layout_height=\"fill_parent\">");
    writer.println("</LinearLayout>");
    writer.flush();
    writer.close();
  }


  // This recommended to be a string resource so that it can be localized. 
  // nah.. we're gonna be messing with it in the GUI anyway... 
  // people can edit themselves if they need to
//  private static void writeResValuesStrings(final File file,
//                                            final String className) {
//    final PrintWriter writer = PApplet.createWriter(file);
//    writer.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
//    writer.println("<resources>");
//    writer.println("  <string name=\"app_name\">" + className + "</string>");
//    writer.println("</resources>");
//    writer.flush();
//    writer.close();
//  }


  private void writeLibraries(final File libsFolder, 
                              final File assetsFolder) throws IOException {
    // Copy any libraries to the 'libs' folder
    for (Library library : getImportedLibraries()) {
      File libraryFolder = new File(library.getPath());
      // in the list is a File object that points the
      // library sketch's "library" folder
      final File exportSettings = new File(libraryFolder, "export.txt");
      final HashMap<String, String> exportTable = 
        Base.readSettings(exportSettings);
      final String androidList = exportTable.get("android");
      String exportList[] = null;
      if (androidList != null) {
        exportList = PApplet.splitTokens(androidList, ", ");
      } else {
        exportList = libraryFolder.list();
      }
      for (int i = 0; i < exportList.length; i++) {
        exportList[i] = PApplet.trim(exportList[i]);
        if (exportList[i].equals("") || exportList[i].equals(".")
            || exportList[i].equals("..")) {
          continue;
        }

        final File exportFile = new File(libraryFolder, exportList[i]);
        if (!exportFile.exists()) {
          System.err.println("File " + exportList[i] + " does not exist");
        } else if (exportFile.isDirectory()) {
          System.err.println("Ignoring sub-folder \"" + exportList[i] + "\"");
        } else {
          final String name = exportFile.getName();
          final String lcname = name.toLowerCase();
          if (lcname.endsWith(".zip") || lcname.endsWith(".jar")) {
            // As of r4 of the Android SDK, it looks like .zip files
            // are ignored in the libs folder, so rename to .jar
            final String jarName = 
              name.substring(0, name.length() - 4) + ".jar";
            Base.copyFile(exportFile, new File(libsFolder, jarName));
          } else {
            Base.copyFile(exportFile, new File(assetsFolder, name));
          }
        }
      }
    }

    // Copy files from the 'code' directory into the 'libs' folder
    final File codeFolder = sketch.getCodeFolder();
    if (codeFolder != null && codeFolder.exists()) {
      for (final File item : codeFolder.listFiles()) {
        if (!item.isDirectory()) {
          final String name = item.getName();
          final String lcname = name.toLowerCase();
          if (lcname.endsWith(".jar") || lcname.endsWith(".zip")) {
            String jarName = name.substring(0, name.length() - 4) + ".jar";
            Base.copyFile(item, new File(libsFolder, jarName));
          }
        }
      }
    }
  }

  
  protected String getPackageName() {
    return manifest.getPackageName();
  }


  public void cleanup() {
    // don't want to be responsible for this
    //rm(tempBuildFolder);
    tempBuildFolder.deleteOnExit();
  }
}