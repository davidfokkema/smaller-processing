/* -*- mode: jde; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  PdeSketchbook - handles sketchbook mechanics for the sketch menu
  Part of the Processing project - http://processing.org

  Except where noted, code is written by Ben Fry and is
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
import java.net.*;
import java.text.*;
import java.util.*;
import java.util.zip.*;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.undo.*;

import com.apple.mrj.*;


public class PdeSketchbook {
  PdeEditor editor;

  JMenu menu;
  JMenu popup;

  // set to true after the first time it's built.
  // so that the errors while building don't show up again.
  boolean builtOnce;

  //File sketchbookFolder;
  //String sketchbookPath;  // canonical path

  // last file/directory used for file opening
  //String handleOpenDirectory;
  // opted against this.. in imovie, apple always goes
  // to the "Movies" folder, even if that wasn't the last used

  // these are static because they're used by PdeSketch
  static File examplesFolder;
  static String examplesPath;  // canonical path (for comparison)


  public PdeSketchbook(PdeEditor editor) {
    this.editor = editor;

    // this shouldn't change throughout.. it may as well be static 
    // but only one instance of sketchbook will be built so who cares
    examplesFolder = new File(System.getProperty("user.dir"), "examples");
    examplesPath = examplesFolder.getAbsolutePath();

    //String sketchbookPath = PdePreferences.get("sketchbook.path");
    //if (sketchbookPath == null) {
    if (PdePreferences.get("sketchbook.path") == null) {
      // by default, set default sketchbook path to the user's 
      // home folder with 'sketchbook' as a subdirectory of that

      /*
      File home = new File(System.getProperty("user.home"));

      if (PdeBase.platform == PdeBase.MACOSX) {
        // on macosx put the sketchbook in the "Documents" folder
        home = new File(home, "Documents");

      } else if (PdeBase.platform == PdeBase.WINDOWS) {
        // on windows put the sketchbook in the "My Documents" folder
        home = new File(home, "My Documents");
      }
      */
      File home = PdePreferences.getProcessingHome();

      String folderName = PdePreferences.get("sketchbook.name.default");
      //System.out.println("home = " + home);
      //System.out.println("fname = " + folderName);
      File sketchbookFolder = new File(home, folderName);
      PdePreferences.set("sketchbook.path", 
                         sketchbookFolder.getAbsolutePath());

      if (!sketchbookFolder.exists()) sketchbookFolder.mkdirs();
    }
    menu = new JMenu("Sketchbook");
    popup = new JMenu("Sketchbook");
  }


  /**
   * Handle creating a sketch folder, return its base .pde file 
   * or null if the operation was cancelled.
   */
  public String handleNew(boolean startup, 
                          boolean shift) throws IOException {
    File newbieDir = null;
    String newbieName = null;

    boolean prompt = PdePreferences.getBoolean("sketchbook.prompt");
    if (shift) prompt = !prompt; // reverse behavior if shift is down

    // no sketch has been started, don't prompt for the name if it's 
    // starting up, just make the farker. otherwise if the person hits 
    // 'cancel' i'd have to add a thing to make p5 quit, which is silly. 
    // instead give them an empty sketch, and they can look at examples. 
    // i hate it when imovie makes you start with that goofy dialog box. 
    // unless, ermm, they user tested it and people preferred that as 
    // a way to get started. shite. now i hate myself. 
    // 
    if (startup) prompt = false;

    if (prompt) {
    //if (!startup) {
      // prompt for the filename and location for the new sketch

      FileDialog fd = new FileDialog(editor, //new Frame(), 
                                     //"Create new sketch named", 
                                     "Create sketch folder named:", 
                                     FileDialog.SAVE);
      fd.setDirectory(PdePreferences.get("sketchbook.path"));
      fd.show();

      String newbieParentDir = fd.getDirectory();
      newbieName = fd.getFile();
      if (newbieName == null) return null;

      newbieName = sanitizeName(newbieName);
      newbieDir = new File(newbieParentDir, newbieName);

    } else {
      // use a generic name like sketch_031008a, the date plus a char
      String newbieParentDir = PdePreferences.get("sketchbook.path");

      int index = 0;
      SimpleDateFormat formatter = new SimpleDateFormat("yyMMdd");
      String purty = formatter.format(new Date());
      do {
        newbieName = "sketch_" + purty + ((char) ('a' + index));
        newbieDir = new File(newbieParentDir, newbieName);
        index++;
      } while (newbieDir.exists());
    }

    // make the directory for the new sketch
    newbieDir.mkdirs();

    // make an empty pde file
    File newbieFile = new File(newbieDir, newbieName + ".pde");
    new FileOutputStream(newbieFile);  // create the file

    // TODO this wouldn't be needed if i could figure out how to 
    // associate document icons via a dot-extension/mime-type scenario
    // help me steve jobs. you're my only hope

    // jdk13 on osx, or jdk11
    // though apparently still available for 1.4
    if ((PdeBase.platform == PdeBase.MACOS9) ||
        (PdeBase.platform == PdeBase.MACOSX)) {
      MRJFileUtils.setFileTypeAndCreator(newbieFile,
                                         MRJOSType.kTypeTEXT,
                                         new MRJOSType("Pde1"));
      // thank you apple, for changing this @#$)(*
      //com.apple.eio.setFileTypeAndCreator(String filename, int, int)
    }

    // make a note of a newly added sketch in the sketchbook menu
    rebuildMenu();

    // now open it up
    //handleOpen(newbieName, newbieFile, newbieDir);
    //return newSketch;
    return newbieFile.getAbsolutePath();
  }


  /**
   * Convert to sanitized name and alert the user 
   * if changes were made.
   */
  static public String sanitizeName(String origName) {
    String newName = sanitizedName(origName);

    if (!newName.equals(origName)) {
      PdeBase.showMessage("Naming issue",
                          "The sketch name had to be modified.\n" +
                          "You can only use basic letters and numbers\n" + 
                          "to name a sketch (ascii only and no spaces,\n" +
                          "and it can't start with a number)");
    }
    return newName;
  }


  /**
   * Java classes are pretty limited about what you can use
   * for their naming. This helper function replaces everything 
   * but A-Z, a-z, and 0-9 with underscores. Also disallows
   * starting the sketch name with a digit.
   */
  static public String sanitizedName(String origName) {
    char c[] = origName.toCharArray();
    StringBuffer buffer = new StringBuffer();

    // can't lead with a digit, so start with an underscore
    if ((c[0] >= '0') && (c[0] <= '9')) {
      buffer.append('_');
    }
    for (int i = 0; i < c.length; i++) {
      if (((c[i] >= '0') && (c[i] <= '9')) ||
          ((c[i] >= 'a') && (c[i] <= 'z')) ||
          ((c[i] >= 'A') && (c[i] <= 'Z'))) {
        buffer.append(c[i]);

      } else {
        buffer.append('_');
      }
    }
    //return buffer.toString();
    return buffer.toString();
  }


  public String handleOpen() {
    // swing's file choosers are ass ugly, so we use the
    // native (awt peered) dialogs instead
    FileDialog fd = new FileDialog(editor, //new Frame(), 
                                   "Open a Processing sketch...", 
                                   FileDialog.LOAD);
    //if (handleOpenDirectory == null) {
    //  handleOpenDirectory = PdePreferences.get("sketchbook.path");
    //}
    //fd.setDirectory(handleOpenDirectory);
    fd.setDirectory(PdePreferences.get("sketchbook.path"));

    // only show .pde files as eligible bachelors
    // TODO this doesn't seem to ever be used. AWESOME.
    fd.setFilenameFilter(new FilenameFilter() {
        public boolean accept(File dir, String name) {
          //System.out.println("check filter on " + dir + " " + name);
          return name.endsWith(".pde");
        }
      });

    // gimme some money
    fd.show();

    // what in the hell yu want, boy?
    String directory = fd.getDirectory();
    String filename = fd.getFile();

    // user cancelled selection
    if (filename == null) return null;

    // this may come in handy sometime
    //handleOpenDirectory = directory;

    File selection = new File(directory, filename);
    return selection.getAbsolutePath();
  }


  public JPopupMenu getPopupMenu() {
    //return menu.getPopupMenu();
    return popup.getPopupMenu();
  }


  /**
   * Rebuild the menu full of sketches based on the 
   * contents of the sketchbook. 
   * 
   * Creates a separate JMenu object for the popup, 
   * because it seems that after calling "getPopupMenu" 
   * the menu will disappear from its original location.
   */
  public JMenu rebuildMenu() {
    menu.removeAll();
    popup.removeAll();

    try {
      //JMenuItem item = PdeEditor.newJMenuItem("Open...");
      JMenuItem item = new JMenuItem("Open...");
      item.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            editor.handleOpen(null);
          }
        });
      popup.add(item);
      popup.addSeparator();

      // identical to below
      addSketches(popup, new File(PdePreferences.get("sketchbook.path")));
      popup.addSeparator();
      addSketches(popup, examplesFolder);

      // disable error messages while loading
      builtOnce = true;

      // identical to above
      addSketches(menu, new File(PdePreferences.get("sketchbook.path")));
      menu.addSeparator();
      addSketches(menu, examplesFolder);

    } catch (IOException e) {
      PdeBase.showWarning("Problem while building sketchbook menu",
                          "There was a problem with building the\n" +
                          "sketchbook menu. Things might get a little\n" +
                          "kooky around here.", e);
    }
    return menu;
  }


  protected boolean addSketches(JMenu menu, File folder) throws IOException {
    // skip .DS_Store files, etc
    if (!folder.isDirectory()) return false;

    String list[] = folder.list();
    // if a bad folder or something like that, this might come back null
    if (list == null) return false;

    // alphabetize list, since it's not always alpha order
    // use cheapie bubble-style sort which should be fine
    // since not a tone of files, and things will mostly be sorted
    // or may be completely sorted already by the os
    for (int i = 0; i < list.length; i++) {
      int who = i;
      for (int j = i+1; j < list.length; j++) {
        if (list[j].compareTo(list[who]) < 0) {
          who = j;  // this guy is earlier in the alphabet
        }
      }
      if (who != i) {  // swap with someone if changes made
        String temp = list[who];
        list[who] = list[i];
        list[i] = temp;
      }
    }

    //SketchbookMenuListener listener = 
    //new SketchbookMenuListener(folder.getAbsolutePath());

    ActionListener listener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          editor.handleOpen(e.getActionCommand());
        }
      };

    boolean ifound = false;

    for (int i = 0; i < list.length; i++) {
      if ((list[i].charAt(0) == '.') ||
          list[i].equals("CVS")) continue;

      File subfolder = new File(folder, list[i]);
      File entry = new File(subfolder, list[i] + ".pde");
      // if a .pde file of the same prefix as the folder exists..
      if (entry.exists()) {
        String sanityCheck = sanitizedName(list[i]);
        if (!sanityCheck.equals(list[i])) {
          if (!builtOnce) {
            String mess = 
              "The sketch \"" + list[i] + "\" cannot be used.\n" +
              "Sketch names must contain only basic letters and numbers.\n" + 
              "(ascii only and no spaces, and it cannot start with a number)";
            PdeBase.showMessage("Ignoring bad sketch name", mess);
          }
          continue;
        }

        JMenuItem item = new JMenuItem(list[i]);
        item.addActionListener(listener);
        item.setActionCommand(entry.getAbsolutePath());
        menu.add(item);
        ifound = true;

      } else {  // might contain other dirs, get recursive
        JMenu submenu = new JMenu(list[i]);
        // needs to be separate var 
        // otherwise would set ifound to false
        boolean found = addSketches(submenu, subfolder); //, false);
        if (found) {
          menu.add(submenu);
          ifound = true;
        }
      }
    }
    return ifound;  // actually ignored, but..
  }


  /**
   * Clear out projects that are empty.
   */
  public void clean() {
    //if (!PdePreferences.getBoolean("sketchbook.auto_clean")) return;

    File sketchbookFolder = new File(PdePreferences.get("sketchbook.path"));
    if (!sketchbookFolder.exists()) return;

    //String entries[] = new File(userPath).list();
    String entries[] = sketchbookFolder.list();
    if (entries != null) {
      for (int j = 0; j < entries.length; j++) {
        //System.out.println(entries[j] + " " + entries.length);
        if (entries[j].charAt(0) == '.') continue;

        //File prey = new File(userPath, entries[j]);
        File prey = new File(sketchbookFolder, entries[j]);
        File pde = new File(prey, entries[j] + ".pde");

        // make sure this is actually a sketch folder with a .pde,
        // not a .DS_Store file or another random user folder

        if (pde.exists() &&
            (PdeBase.calcFolderSize(prey) == 0)) {
          //System.out.println("i want to remove " + prey);

          if (PdePreferences.getBoolean("sketchbook.auto_clean")) {
            PdeBase.removeDir(prey);

          } else {  // otherwise prompt the user
            String prompt = 
              "Remove empty sketch titled \"" + entries[j] + "\"?";

            Object[] options = { "Yes", "No" };
            int result = 
              JOptionPane.showOptionDialog(editor,
                                           prompt,
                                           "Housekeeping",
                                           JOptionPane.YES_NO_OPTION,
                                           JOptionPane.QUESTION_MESSAGE,
                                           null,
                                           options, 
                                           options[0]);
            if (result == JOptionPane.YES_OPTION) {
              PdeBase.removeDir(prey);
            }
          }
        }
      }
    }
  }
}
