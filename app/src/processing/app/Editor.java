/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2004-07 Ben Fry and Casey Reas
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

package processing.app;

import processing.app.debug.Runner;
import processing.app.debug.RunnerException;
import processing.app.syntax.*;
import processing.app.tools.*;
import processing.core.*;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.awt.print.*;
import java.io.*;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.undo.*;


/**
 * Main editor panel for the Processing Development Environment.
 */
public class Editor extends JFrame {

  Base base;

  // yeah
  static final String WINDOW_TITLE = "Processing " + Base.VERSION_NAME;

  // otherwise, if the window is resized with the message label
  // set to blank, it's preferredSize() will be fukered
  static public final String EMPTY =
    "                                                                     " +
    "                                                                     " +
    "                                                                     ";

  /** Command on Mac OS X, Ctrl on Windows and Linux */
  static final int SHORTCUT_KEY_MASK =
    Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
  /** Command-W on Mac OS X, Ctrl-W on Windows and Linux */
  static public final KeyStroke WINDOW_CLOSE_KEYSTROKE =
    KeyStroke.getKeyStroke('W', SHORTCUT_KEY_MASK);
  /** Command-Option on Mac OS X, Ctrl-Alt on Windows and Linux */
  static public final int SHORTCUT_ALT_KEY_MASK = ActionEvent.ALT_MASK |
    Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

  /**
   * true if this file has not yet been given a name by the user
   */
  boolean untitled;

  PageFormat pageFormat;
  PrinterJob printerJob;

  // file and sketch menus for re-inserting items
  JMenu fileMenu;
  JMenu sketchMenu;

  EditorToolbar toolbar;
  // these menus are shared so that they needn't be rebuilt for all windows
  // each time a sketch is created, renamed, or moved.
  static JMenu toolbarMenu;
  static JMenu sketchbookMenu;
  static JMenu examplesMenu;
  static JMenu importMenu;

  EditorHeader header;
  EditorStatus status;
  EditorConsole console;

  JSplitPane splitPane;
  JPanel consolePanel;

  JLabel lineNumberComponent;

  // currently opened program
  protected Sketch sketch;

  EditorLineStatus lineStatus;

  public JEditTextArea textarea;
  EditorListener listener;

  // runtime information and window placement
  Point sketchWindowLocation;
  //RunButtonWatcher watcher;
  Runner runtime;

  JMenuItem exportAppItem;
  JMenuItem saveMenuItem;
  JMenuItem saveAsMenuItem;

  // True if the sketchbook has changed since this Editor was last active.
//  boolean sketchbookUpdated;

  boolean running;
  boolean presenting;

  // undo fellers
  JMenuItem undoItem, redoItem;
  protected UndoAction undoAction;
  protected RedoAction redoAction;
  UndoManager undo;
  // used internally, and only briefly
  CompoundEdit compoundEdit;

  //SketchHistory history;  // TODO re-enable history
  //Sketchbook sketchbook;
  FindReplace find;


  public Editor(Base ibase, String path, int[] location) {
    super(WINDOW_TITLE);
    this.base = ibase;

    Base.setIcon(this);

    // add listener to handle window close box hit event
    addWindowListener(new WindowAdapter() {
        public void windowClosing(WindowEvent e) {
          base.handleClose(Editor.this, false);
        }
      });
    // don't close the window when clicked, the app will take care
    // of that via the handleQuitInternal() methods
    // http://dev.processing.org/bugs/show_bug.cgi?id=440
    setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

    // When bringing a window to front, let the Base know
    addWindowListener(new WindowAdapter() {
        public void windowActivated(WindowEvent e) {
          base.handleActivated(Editor.this);
          
          // re-add the sub-menus that are shared by all windows
          fileMenu.insert(sketchbookMenu, 2);
          fileMenu.insert(examplesMenu, 3);
          sketchMenu.insert(importMenu, 4);
        }
      });

    //PdeKeywords keywords = new PdeKeywords();
    //sketchbook = new Sketchbook(this);

    buildMenuBar();

    // For rev 0120, placing things inside a JPanel
    Container contentPain = getContentPane();
    contentPain.setLayout(new BorderLayout());
    JPanel pain = new JPanel();
    pain.setLayout(new BorderLayout());
    contentPain.add(pain, BorderLayout.CENTER);

    Box box = Box.createVerticalBox();
    Box upper = Box.createVerticalBox();

    if (toolbarMenu == null) {
      toolbarMenu = new JMenu();
      base.rebuildToolbarMenu(toolbarMenu);
    }
    toolbar = new EditorToolbar(this, toolbarMenu);
    upper.add(toolbar);

    header = new EditorHeader(this);
    upper.add(header);

    textarea = new JEditTextArea(new PdeTextAreaDefaults());
    textarea.setRightClickPopup(new TextAreaPopup());
    textarea.setHorizontalOffset(6);

    // assemble console panel, consisting of status area and the console itself
    consolePanel = new JPanel();
    consolePanel.setLayout(new BorderLayout());

    status = new EditorStatus(this);
    consolePanel.add(status, BorderLayout.NORTH);

    console = new EditorConsole(this);
    // windows puts an ugly border on this guy
    console.setBorder(null);
    consolePanel.add(console, BorderLayout.CENTER);

    lineStatus = new EditorLineStatus(textarea);
    consolePanel.add(lineStatus, BorderLayout.SOUTH);

    upper.add(textarea);
    splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                               upper, consolePanel);

    splitPane.setOneTouchExpandable(true);
    // repaint child panes while resizing
    splitPane.setContinuousLayout(true);
    // if window increases in size, give all of increase to
    // the textarea in the uppper pane
    splitPane.setResizeWeight(1D);

    // to fix ugliness.. normally macosx java 1.3 puts an
    // ugly white border around this object, so turn it off.
    splitPane.setBorder(null);

    // the default size on windows is too small and kinda ugly
    int dividerSize = Preferences.getInteger("editor.divider.size");
    if (dividerSize != 0) {
      splitPane.setDividerSize(dividerSize);
    }

    splitPane.setMinimumSize(new Dimension(600, 400));
    box.add(splitPane);

    // hopefully these are no longer needed w/ swing
    // (har har har.. that was wishful thinking)
    listener = new EditorListener(this, textarea);
    pain.add(box);

    pain.setTransferHandler(new TransferHandler() {

        public boolean canImport(JComponent dest, DataFlavor[] flavors) {
          return true;
        }

        public boolean importData(JComponent src, Transferable transferable) {
          int successful = 0;

          try {
            DataFlavor uriListFlavor =
              new DataFlavor("text/uri-list;class=java.lang.String");

            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
              java.util.List list = (java.util.List)
                transferable.getTransferData(DataFlavor.javaFileListFlavor);
              for (int i = 0; i < list.size(); i++) {
                File file = (File) list.get(i);
                if (sketch.addFile(file)) {
                  successful++;
                }
              }
            } else if (transferable.isDataFlavorSupported(uriListFlavor)) {
              //System.out.println("uri list");
              String data = (String)transferable.getTransferData(uriListFlavor);
              String[] pieces = PApplet.splitTokens(data, "\r\n");
              //PApplet.println(pieces);
              for (int i = 0; i < pieces.length; i++) {
                if (pieces[i].startsWith("#")) continue;

                String path = null;
                if (pieces[i].startsWith("file:///")) {
                  path = pieces[i].substring(7);
                } else if (pieces[i].startsWith("file:/")) {
                  path = pieces[i].substring(5);
                }
                if (sketch.addFile(new File(path))) {
                  successful++;
                }
              }
            }
          } catch (Exception e) {
            e.printStackTrace();
            return false;
          }

          if (successful == 0) {
            error("No files were added to the sketch.");

          } else if (successful == 1) {
            message("One file added to the sketch.");

          } else {
            message(successful + " files added to the sketch.");
          }
          return true;
        }
      });

//    System.out.println("t1");

    // Finish preparing Editor (formerly found in Base)
    pack();

//    System.out.println("t2");

    // Set the window bounds and the divider location before setting it visible
    setPlacement(location);

//    System.out.println("t3");

    // Bring back the general options for the editor
    applyPreferences();

//    System.out.println("t4");

    // Open the document that was passed in
    boolean loaded = handleOpenInternal(path);
    if (!loaded) sketch = null;

//    System.out.println("t5");

    // All set, now show the window
    //setVisible(true);
  }


  public void setPlacement(int[] location) {
    setBounds(location[0], location[1], location[2], location[3]);
    if (location[4] != 0) {
      splitPane.setDividerLocation(location[4]);
    }
  }


  public int[] getPlacement() {
    int[] location = new int[5];

    // Get the dimensions of the Frame
    Rectangle bounds = getBounds();
    location[0] = bounds.x;
    location[1] = bounds.y;
    location[2] = bounds.width;
    location[3] = bounds.height;

    // Get the current placement of the divider
    location[4] = splitPane.getDividerLocation();

    return location;
  }


  /**
   * Hack for #@#)$(* Mac OS X 10.2.
   * <p/>
   * This appears to only be required on OS X 10.2, and is not
   * even being called on later versions of OS X or Windows.
   */
  public Dimension getMinimumSize() {
    //System.out.println("getting minimum size");
    return new Dimension(500, 550);
  }


  // ...................................................................


  /**
   * Read and apply new values from the preferences, either because
   * the app is just starting up, or the user just finished messing
   * with things in the Preferences window.
   */
  public void applyPreferences() {

    // apply the setting for 'use external editor'
    boolean external = Preferences.getBoolean("editor.external");

    textarea.setEditable(!external);
    saveMenuItem.setEnabled(!external);
    saveAsMenuItem.setEnabled(!external);

    TextAreaPainter painter = textarea.getPainter();
    if (external) {
      // disable line highlight and turn off the caret when disabling
      Color color = Preferences.getColor("editor.external.bgcolor");
      painter.setBackground(color);
      painter.setLineHighlightEnabled(false);
      textarea.setCaretVisible(false);

    } else {
      Color color = Preferences.getColor("editor.bgcolor");
      painter.setBackground(color);
      boolean highlight = Preferences.getBoolean("editor.linehighlight");
      painter.setLineHighlightEnabled(highlight);
      textarea.setCaretVisible(true);
    }

    // apply changes to the font size for the editor
    //TextAreaPainter painter = textarea.getPainter();
    painter.setFont(Preferences.getFont("editor.font"));
    //Font font = painter.getFont();
    //textarea.getPainter().setFont(new Font("Courier", Font.PLAIN, 36));

    // in case tab expansion stuff has changed
    listener.applyPreferences();

    // in case moved to a new location
    // For 0125, changing to async version (to be implemented later)
    //sketchbook.rebuildMenus();
    // For 0126, moved into Base, which will notify all editors.
    //base.rebuildMenusAsync();
  }


  // ...................................................................


  protected void buildMenuBar() {
    JMenuBar menubar = new JMenuBar();
    menubar = new JMenuBar();
    menubar.add(buildFileMenu());
    menubar.add(buildEditMenu());
    menubar.add(buildSketchMenu());
    menubar.add(buildToolsMenu());
    menubar.add(buildHelpMenu());
    setJMenuBar(menubar);
  }


  protected JMenu buildFileMenu() {
    JMenuItem item;
    fileMenu = new JMenu("File");

    item = newJMenuItem("New", 'N');
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          //base.handleNew(false);
          base.handleNew();
        }
      });
    fileMenu.add(item);

    item = Editor.newJMenuItem("Open...", 'O', false);
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          base.handleOpenPrompt();
        }
      });
    fileMenu.add(item);

    if (sketchbookMenu == null) {
      sketchbookMenu = new JMenu("Sketchbook");
      base.rebuildSketchbookMenu(sketchbookMenu);
    }
    fileMenu.add(sketchbookMenu);

    if (examplesMenu == null) {
      examplesMenu = new JMenu("Examples");
      base.rebuildExamplesMenu(examplesMenu);
    }
    fileMenu.add(examplesMenu);

    item = Editor.newJMenuItem("Close", 'W', false);
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          base.handleClose(Editor.this, false);
        }
      });
    fileMenu.add(item);

    saveMenuItem = newJMenuItem("Save", 'S');
    saveMenuItem.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          handleSave(false);
        }
      });
    fileMenu.add(saveMenuItem);

    saveAsMenuItem = newJMenuItem("Save As...", 'S', true);
    saveAsMenuItem.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          handleSaveAs();
        }
      });
    fileMenu.add(saveAsMenuItem);

    item = newJMenuItem("Export", 'E');
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          handleExport();
        }
      });
    fileMenu.add(item);

    exportAppItem = newJMenuItem("Export Application", 'E', true);
    exportAppItem.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          //buttons.activate(EditorButtons.EXPORT);
          //SwingUtilities.invokeLater(new Runnable() {
          //public void run() {
          handleExportApplication();
          //}});
        }
      });
    fileMenu.add(exportAppItem);

    fileMenu.addSeparator();

    item = newJMenuItem("Page Setup", 'P', true);
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          handlePageSetup();
        }
      });
    fileMenu.add(item);

    item = newJMenuItem("Print", 'P');
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          handlePrint();
        }
      });
    fileMenu.add(item);

    // macosx already has its own preferences and quit menu
    if (!Base.isMacOS()) {
      fileMenu.addSeparator();

      item = newJMenuItem("Preferences", ',');
      item.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            base.handlePrefs();
          }
        });
      fileMenu.add(item);

      fileMenu.addSeparator();

      item = newJMenuItem("Quit", 'Q');
      item.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            base.handleQuit();
          }
        });
      fileMenu.add(item);
    }
    return fileMenu;
  }


  protected JMenu buildSketchMenu() {
    JMenuItem item;
    sketchMenu = new JMenu("Sketch");

    item = newJMenuItem("Run", 'R');
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          handleRun(false);
        }
      });
    sketchMenu.add(item);

    item = newJMenuItem("Present", 'R', true);
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          handleRun(true);
        }
      });
    sketchMenu.add(item);

    item = new JMenuItem("Stop");
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          handleStop();
        }
      });
    sketchMenu.add(item);

    sketchMenu.addSeparator();

    if (importMenu == null) {
      importMenu = new JMenu("Import Library...");
      base.rebuildImportMenu(importMenu);
    }
    sketchMenu.add(importMenu);

    //if (Base.isWindows() || Base.isMacOS()) {
    // no way to do an 'open in file browser' on other platforms
    // since there isn't any sort of standard
    item = newJMenuItem("Show Sketch Folder", 'K', false);
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          //Base.openFolder(sketchDir);
          Base.openFolder(sketch.folder);
        }
      });
    sketchMenu.add(item);
    if (!Base.openFolderAvailable()) {
      item.setEnabled(false);
    }

    //menu.addSeparator();

    item = new JMenuItem("Add File...");
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          sketch.addFile();
        }
      });
    sketchMenu.add(item);

    // TODO re-enable history
    //history.attachMenu(menu);
    return sketchMenu;
  }


  protected JMenu buildToolsMenu() {
    JMenuItem item;
    JMenu menu = new JMenu("Tools");

    item = newJMenuItem("Auto Format", 'T', false);
    item.addActionListener(new ActionListener() {
        synchronized public void actionPerformed(ActionEvent e) {
          new AutoFormat(Editor.this).show();

          /*
          Jalopy jalopy = new Jalopy();
          jalopy.setInput(getText(), sketch.current.file.getAbsolutePath());
          StringBuffer buffer = new StringBuffer();
          jalopy.setOutput(buffer);
          jalopy.setInspect(false);
          jalopy.format();
          setText(buffer.toString(), 0, 0);

          if (jalopy.getState() == Jalopy.State.OK)
            System.out.println("successfully formatted");
          else if (jalopy.getState() == Jalopy.State.WARN)
            System.out.println(" formatted with warnings");
          else if (jalopy.getState() == Jalopy.State.ERROR)
            System.out.println(" could not be formatted");
          */
        }
      });
    menu.add(item);

    item = new JMenuItem("Create Font...");
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          new CreateFont(Editor.this).setVisible(true);
        }
      });
    menu.add(item);

    item = new JMenuItem("Color Selector");
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                new ColorSelector(Editor.this).show();
              }
            });
        }
      });
    menu.add(item);

    item = new JMenuItem("Archive Sketch");
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          new Archiver(Editor.this).show();
          //Archiver archiver = new Archiver();
          //archiver.setup(Editor.this);
          //archiver.show();
        }
      });
    menu.add(item);
    
    item = new JMenuItem("Fix Encoding & Reload");
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            new FixEncoding(Editor.this).show();
          }
        });
      }
    });
    menu.add(item);
  
    /*
    item = new JMenuItem("Export Folder...");
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                new ExportFolder(Editor.this).show();
              }
            });
        }
      });
    menu.add(item);
    */

    /*
    item = new JMenuItem("Open in External Editor");
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          Preferences.setBoolean("editor.external", true);
          applyPreferences();

          String path = sketch.current.file.getAbsolutePath();
          try {
            Runtime.getRuntime().exec(new String[] {
              "cmd", "/c", "c:\\emacs-20.7\\bin\\runemacs.exe", path
            });
          } catch (Exception ex) { }
        }
      });
    menu.add(item);
    */

    return menu;
  }


  protected JMenu buildHelpMenu() {
    JMenu menu = new JMenu("Help");
    JMenuItem item;

    item = new JMenuItem("Getting Started");
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          Base.showEnvironment();
        }
      });
    menu.add(item);

    item = new JMenuItem("Troubleshooting");
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          Base.showTroubleshooting();
        }
      });
    menu.add(item);

    item = new JMenuItem("Reference");
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          Base.showReference();
        }
      });
    menu.add(item);

    item = newJMenuItem("Find in Reference", 'F', true);
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if (textarea.isSelectionActive()) {
            handleReference();
          }
        }
      });
    menu.add(item);

    item = new JMenuItem("Frequently Asked Questions");
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          Base.openURL("http://processing.org/faq.html");
        }
      });
    menu.add(item);

    item = newJMenuItem("Visit Processing.org", '5');
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          Base.openURL("http://processing.org/");
        }
      });
    menu.add(item);

    // macosx already has its own about menu
    if (!Base.isMacOS()) {
      menu.addSeparator();
      item = new JMenuItem("About Processing");
      item.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            base.handleAbout();
          }
        });
      menu.add(item);
    }

    return menu;
  }


  public JMenu buildEditMenu() {
    JMenu menu = new JMenu("Edit");
    JMenuItem item;

    undoItem = newJMenuItem("Undo", 'Z');
    undoItem.addActionListener(undoAction = new UndoAction());
    menu.add(undoItem);

    redoItem = newJMenuItem("Redo", 'Y');
    redoItem.addActionListener(redoAction = new RedoAction());
    menu.add(redoItem);

    menu.addSeparator();

    // TODO "cut" and "copy" should really only be enabled
    // if some text is currently selected
    item = newJMenuItem("Cut", 'X');
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          textarea.cut();
          sketch.setModified(true);
        }
      });
    menu.add(item);

    item = newJMenuItem("Copy", 'C');
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          textarea.copy();
        }
      });
    menu.add(item);
    
    item = newJMenuItem("Copy for Discourse", 'C', true);
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                new DiscourseFormat(Editor.this).show();
              }
            });
        }
      });
    menu.add(item);

    item = newJMenuItem("Paste", 'V');
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          textarea.paste();
          sketch.setModified(true);
        }
      });
    menu.add(item);

    item = newJMenuItem("Select All", 'A');
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          textarea.selectAll();
        }
      });
    menu.add(item);

    menu.addSeparator();

    item = newJMenuItem("Comment/Uncomment", '/');
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          beginCompoundEdit();

          int startLine = textarea.getSelectionStartLine();
          int stopLine = textarea.getSelectionStopLine();
          
          // If the selection ends at the beginning of the last line, 
          // then don't (un)comment that line. 
          int lastLineStart = textarea.getLineStartOffset(stopLine);
          int selectionStop = textarea.getSelectionStop();
          if (selectionStop == lastLineStart) {
            stopLine--;
          }
          
          // If the text is empty, ignore the user. 
          int length = textarea.getDocumentLength();
          int pos = textarea.getLineStartOffset(startLine);
          if (pos + 2 > length) return;
          // Check the first two characters to see if it's already a comment.
          String begin = textarea.getText(pos, 2);
          //System.out.println("begin is '" + begin + "'");
          boolean commented = begin.equals("//");

          for (int line = startLine; line <= stopLine; line++) {
            int location = textarea.getLineStartOffset(line);
            if (commented) {
              // remove a comment
              textarea.select(location, location+2);
              if (textarea.getSelectedText().equals("//")) {
                textarea.setSelectedText("");
              }
            } else {
              // add a comment
              textarea.select(location, location);
              textarea.setSelectedText("//");
            }
          }
          // Subtract one from the end, otherwise selects past the current line.
          // (Which causes subsequent calls to keep expanding the selection)
          textarea.select(textarea.getLineStartOffset(startLine),
                          textarea.getLineStopOffset(stopLine) - 1);
          endCompoundEdit();
        }
    });
    menu.add(item);

    menu.addSeparator();

    item = newJMenuItem("Find...", 'F');
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if (find == null) {
            find = new FindReplace(Editor.this);
          }
          //new FindReplace(Editor.this).show();
          find.setVisible(true);
          //find.setVisible(true);
        }
      });
    menu.add(item);

    // TODO find next should only be enabled after a
    // search has actually taken place
    item = newJMenuItem("Find Next", 'G');
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if (find != null) {
            //find.find(true);
            //FindReplace find = new FindReplace(Editor.this); //.show();
            find.find(true);
          }
        }
      });
    menu.add(item);

    return menu;
  }


  /**
   * Convenience method, see below.
   */
  static public JMenuItem newJMenuItem(String title, int what) {
    return newJMenuItem(title, what, false);
  }


  /**
   * A software engineer, somewhere, needs to have his abstraction
   * taken away. In some countries they jail or beat people for writing
   * the sort of API that would require a five line helper function
   * just to set the command key for a menu item.
   */
  static public JMenuItem newJMenuItem(String title,
                                       int what, boolean shift) {
    JMenuItem menuItem = new JMenuItem(title);
    int modifiers = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
    if (shift) modifiers |= ActionEvent.SHIFT_MASK;
    menuItem.setAccelerator(KeyStroke.getKeyStroke(what, modifiers));
    return menuItem;
  }


  static public JMenuItem newJMenuItemAlt(String title, int what) {
    JMenuItem menuItem = new JMenuItem(title);
    //int modifiers = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
    //menuItem.setAccelerator(KeyStroke.getKeyStroke(what, modifiers));
    menuItem.setAccelerator(KeyStroke.getKeyStroke(what, SHORTCUT_ALT_KEY_MASK));
    return menuItem;
  }


  // ...................................................................


  class UndoAction extends AbstractAction {
    public UndoAction() {
      super("Undo");
      this.setEnabled(false);
    }

    public void actionPerformed(ActionEvent e) {
      try {
        undo.undo();
      } catch (CannotUndoException ex) {
        //System.out.println("Unable to undo: " + ex);
        //ex.printStackTrace();
      }
      updateUndoState();
      redoAction.updateRedoState();
    }

    protected void updateUndoState() {
      if (undo.canUndo()) {
        this.setEnabled(true);
        undoItem.setEnabled(true);
        undoItem.setText(undo.getUndoPresentationName());
        putValue(Action.NAME, undo.getUndoPresentationName());
        if (sketch != null) {
          sketch.setModified(true);  // 0107
        }
      } else {
        this.setEnabled(false);
        undoItem.setEnabled(false);
        undoItem.setText("Undo");
        putValue(Action.NAME, "Undo");
        if (sketch != null) {
          sketch.setModified(false);  // 0107
        }
      }
    }
  }


  class RedoAction extends AbstractAction {
    public RedoAction() {
      super("Redo");
      this.setEnabled(false);
    }

    public void actionPerformed(ActionEvent e) {
      try {
        undo.redo();
      } catch (CannotRedoException ex) {
        //System.out.println("Unable to redo: " + ex);
        //ex.printStackTrace();
      }
      updateRedoState();
      undoAction.updateUndoState();
    }

    protected void updateRedoState() {
      if (undo.canRedo()) {
        redoItem.setEnabled(true);
        redoItem.setText(undo.getRedoPresentationName());
        putValue(Action.NAME, undo.getRedoPresentationName());
      } else {
        this.setEnabled(false);
        redoItem.setEnabled(false);
        redoItem.setText("Redo");
        putValue(Action.NAME, "Redo");
      }
    }
  }


  // ...................................................................

  
  /**
   * Gets the current sketch object.
   */
  public Sketch getSketch() {
    return sketch;
  }

  /**
   * Get the contents of the current buffer. Used by the Sketch class.
   */
  public String getText() {
    return textarea.getText();
  }


  /**
   * Replace the entire contents of the frontmost tab.
   * @param what with what?
   */
  public void setText(String what) {
    beginCompoundEdit();
    textarea.setText(what);
    endCompoundEdit();
  }

  /**
   * Called to update the text but not switch to a different
   * set of code (which would affect the undo manager).
   */
  public void setText(String what, int selectionStart, int selectionStop) {
    beginCompoundEdit();
    textarea.setText(what);
    endCompoundEdit();

    // make sure that a tool isn't asking for a bad location
    selectionStart =
      Math.max(0, Math.min(selectionStart, textarea.getDocumentLength()));
    selectionStop =
      Math.max(0, Math.min(selectionStart, textarea.getDocumentLength()));
    textarea.select(selectionStart, selectionStop);

    textarea.requestFocus();  // get the caret blinking
  }


  /**
   * Switch between tabs, this swaps out the Document object
   * that's currently being manipulated.
   */
  public void setCode(SketchCode code) {
    if (code.document == null) {  // this document not yet inited
      code.document = new SyntaxDocument();

      // turn on syntax highlighting
      code.document.setTokenMarker(new PdeKeywords());

      // insert the program text into the document object
      try {
        code.document.insertString(0, code.program, null);
      } catch (BadLocationException bl) {
        bl.printStackTrace();
      }

      // set up this guy's own undo manager
      code.undo = new UndoManager();

      // connect the undo listener to the editor
      code.document.addUndoableEditListener(new UndoableEditListener() {
          public void undoableEditHappened(UndoableEditEvent e) {
            if (compoundEdit != null) {
              compoundEdit.addEdit(e.getEdit());

            } else if (undo != null) {
              undo.addEdit(e.getEdit());
              undoAction.updateUndoState();
              redoAction.updateRedoState();
            }
          }
        });
    }

    // update the document object that's in use
    textarea.setDocument(code.document,
                         code.selectionStart, code.selectionStop,
                         code.scrollPosition);

    textarea.requestFocus();  // get the caret blinking

    this.undo = code.undo;
    undoAction.updateUndoState();
    redoAction.updateRedoState();
  }


  public void beginCompoundEdit() {
    compoundEdit = new CompoundEdit();
  }


  public void endCompoundEdit() {
    compoundEdit.end();
    undo.addEdit(compoundEdit);
    undoAction.updateUndoState();
    redoAction.updateRedoState();
    compoundEdit = null;
  }


  // ...................................................................


  public void handleRun(boolean present) {
    closeRunner();
    running = true;
    toolbar.activate(EditorToolbar.RUN);

    // do this to advance/clear the terminal window / dos prompt / etc
    for (int i = 0; i < 10; i++) System.out.println();

    // clear the console on each run, unless the user doesn't want to
    if (Preferences.getBoolean("console.auto_clear")) {
      console.clear();
    }

    presenting = present;

    try {
      if (!sketch.handleCompile()) {
        System.out.println("Compile failed.");  // TODO remove this
        return;
      }

      runtime = new Runner(Editor.this, presenting);
      // Cannot use invokeLater() here, otherwise it gets
      // placed on the event thread and causes a hang--bad idea all around.
      Thread t = new Thread(new Runnable() {
        public void run() {
            runtime.launch();
        }
      });
      t.start();
      //runtime.start(appletLocation);

      // run button watcher not currently enabled
      // it was contributing to the external vm hanging
      //watcher = new RunButtonWatcher();

    } catch (Exception e) {
      //System.err.println("exception reached editor");
      //e.printStackTrace();
      error(e);
    }

    // this doesn't seem to help much or at all
    /*
    final SwingWorker worker = new SwingWorker() {
        public Object construct() {
          try {
            if (!sketch.handleRun()) return null;

            runtime = new Runner(sketch, Editor.this);
            runtime.start(presenting ? presentLocation : appletLocation);
            watcher = new RunButtonWatcher();

          } catch (RunnerException e) {
            error(e);

          } catch (Exception e) {
            e.printStackTrace();
          }
          return null;  // needn't return anything
        }
      };
    worker.start();
    */
    //sketch.cleanup();  // where does this go?
  }


  public void setSketchLocation(Point p) {
    sketchWindowLocation = p;
  }


  public Point getSketchLocation() {
    return sketchWindowLocation;
  }


  /*
  class RunButtonWatcher implements Runnable {
    Thread thread;

    public RunButtonWatcher() {
      thread = new Thread(this, "run button watcher");
      thread.setPriority(Thread.MIN_PRIORITY);
      thread.start();
    }

    public void run() {
      while (Thread.currentThread() == thread) {
        if (runtime == null) {
          stop();

        } else {
          if (runtime.applet != null) {
            if (runtime.applet.finished) {
              stop();
            }
            //buttons.running(!runtime.applet.finished);

          } else if (runtime.process != null) {
            //buttons.running(true);  // ??

          } else {
            stop();
          }
        }
        try {
          Thread.sleep(250);
        } catch (InterruptedException e) { }
        //System.out.println("still inside runner thread");
      }
    }

    public void stop() {
      toolbar.running(false);
      thread = null;
    }
  }
  */


  public void handleStop() {  // called by menu or buttons
//    System.out.println("stopping");
    toolbar.activate(EditorToolbar.STOP);

    if (presenting) {
      closeRunner();
//    } else if (runtime.window != null) {
//      // When run externally, kill the applet window,
//      // otherwise things may not stop properly with libraries.
//      closeRunner();
    } else {
      stopRunner();
    }
    handleStopped();
  }


  /** Used by handleStop() above, and by Runner to clear the Run button */
  public void handleStopped() {
    toolbar.deactivate(EditorToolbar.RUN);
    toolbar.deactivate(EditorToolbar.STOP);
  }


  /**
   * Stop the applet but don't kill its window.
   */
  public void stopRunner() {
//    System.out.println("stopRunner " + runtime);
    if (runtime != null) runtime.stop();
    //System.out.println("stopRunner 2");
    //if (watcher != null) watcher.stop();
    message(EMPTY);

    // the buttons are sometimes still null during the constructor
    // is this still true? are people still hitting this error?
    /*if (buttons != null)*/ //toolbar.clear();

    running = false;
  }


  /**
   * Stop the applet and kill its window. When running in presentation
   * mode, this will always be called instead of doStop().
   */
  public void closeRunner() {
//    System.out.println("closing runner");

    //if (presenting) {
    //presentationWindow.hide();
    //} else {
//    try {
//      // the window will also be null the process was running
//      // externally. so don't even try setting if window is null
//      // since Runner will set the appletLocation when an
//      // external process is in use.
//      if (runtime.window != null) {
//        appletLocation = runtime.window.getLocation();
//      }
//    } catch (NullPointerException e) { }
//    //}

    //if (running) doStop();
    stopRunner();  // need to stop if runtime error

    try {
      if (runtime != null) {
//        System.out.println("runtime closing");
        runtime.close();  // kills the window
        runtime = null; // will this help?
      }
    } catch (Exception e) { }
    //buttons.clear();  // done by doStop

    sketch.cleanup();

    // focus the PDE again after quitting presentation mode [toxi 030903]
    toFront();
  }


  /**
   * Check if the sketch is modified and ask user to save changes.
   * Immediately should be set true when quitting, or when the save should
   * not happen asynchronously. Come to think of it, that's always now?
   * @return false if canceling the close/quit operation
   */
  protected boolean checkModified(boolean immediately) {
    if (!sketch.modified) return true;

    String prompt = "Save changes to " + sketch.name + "?  ";

    if (PApplet.platform != PConstants.MACOSX || PApplet.javaVersion < 1.5f) {
      int result =
        JOptionPane.showConfirmDialog(this, prompt, "Close",
                                      JOptionPane.YES_NO_CANCEL_OPTION,
                                      JOptionPane.QUESTION_MESSAGE);

      if (result == JOptionPane.YES_OPTION) {
        return handleSave(immediately);

      } else if (result == JOptionPane.NO_OPTION) {
        return true;  // ok to continue

      } else if (result == JOptionPane.CANCEL_OPTION) {
        return false;
      } else {
        throw new IllegalStateException();
      }

    } else {
      // This code is disabled unless Java 1.5 is being used on Mac OS X
      // because of a Java bug that prevents the initial value of the
      // dialog from being set properly (at least on my MacBook Pro).
      // The bug causes the "Don't Save" option to be the highlighted,
      // blinking, default. This sucks. But I'll tell you what doesn't
      // suck--workarounds for the Mac and Apple's snobby attitude about it!
      // I think it's nifty that they treat their developers like dirt.

      // Pane formatting adapted from the quaqua guide
      // http://www.randelshofer.ch/quaqua/guide/joptionpane.html
      JOptionPane pane =
        new JOptionPane("<html> " +
                        "<head> <style type=\"text/css\">"+
                        "b { font: 13pt \"Lucida Grande\" }"+
                        "p { font: 11pt \"Lucida Grande\"; margin-top: 8px }"+
                        "</style> </head>" +
                        "<b>Do you want to save changes to this text<BR>" +
                        " before closing?</b>" +
                        "<p>If you don't save, your changes will be lost.",
                        JOptionPane.QUESTION_MESSAGE);

      String[] options = new String[] {
        "Save", "Cancel", "Don't Save"
      };
      pane.setOptions(options);

      // highlight the safest option ala apple hig
      pane.setInitialValue(options[0]);

      // on macosx, setting the destructive property places this option
      // away from the others at the lefthand side
      pane.putClientProperty("Quaqua.OptionPane.destructiveOption",
                             new Integer(2));

      JDialog dialog = pane.createDialog(this, null);
      dialog.setVisible(true);

      Object result = pane.getValue();
      if (result == options[0]) {  // save (and close/quit)
        return handleSave(immediately);

      } else if (result == options[2]) {  // don't save (still close/quit)
        return true;

      } else {  // cancel?
        return false;
      }
    }
  }


  /**
   * Open a sketch from a particular path, but don't check to save changes.
   * Used by Sketch.saveAs() to re-open a sketch after the "Save As"
   */
  protected void handleOpenUnchecked(String path, int codeIndex,
                                     int selStart, int selStop, int scrollPos) {
    closeRunner();
    handleOpenInternal(path);
    // Replacing a document that may be untitled. If this is an actual
    // untitled document, then editor.untitled will be set by Base.
    untitled = false;

    sketch.setCurrent(codeIndex);
    textarea.select(selStart, selStop);
    textarea.setScrollPosition(scrollPos);
  }


  /**
   * Second stage of open, occurs after having checked to see if the
   * modifications (if any) to the previous sketch need to be saved.
   */
  protected boolean handleOpenInternal(String path) {
    try {
      // check to make sure that this .pde file is
      // in a folder of the same name
      File file = new File(path);
      File parentFile = new File(file.getParent());
      String parentName = parentFile.getName();
      String pdeName = parentName + ".pde";
      File altFile = new File(file.getParent(), pdeName);

      if (pdeName.equals(file.getName())) {
        // no beef with this guy

      } else if (altFile.exists()) {
        // user selected a .java from the same sketch,
        // but open the .pde instead
        path = altFile.getAbsolutePath();
        //System.out.println("found alt file in same folder");

      } else if (!path.endsWith(".pde")) {
        Base.showWarning("Bad file selected",
                         "Processing can only open its own sketches\n" +
                         "and other files ending in .pde", null);
        return false;

      } else {
        String properParent =
          file.getName().substring(0, file.getName().length() - 4);

        Object[] options = { "OK", "Cancel" };
        String prompt =
          "The file \"" + file.getName() + "\" needs to be inside\n" +
          "a sketch folder named \"" + properParent + "\".\n" +
          "Create this folder, move the file, and continue?";

        int result = JOptionPane.showOptionDialog(this,
                                                  prompt,
                                                  "Moving",
                                                  JOptionPane.YES_NO_OPTION,
                                                  JOptionPane.QUESTION_MESSAGE,
                                                  null,
                                                  options,
                                                  options[0]);

        if (result == JOptionPane.YES_OPTION) {
          // create properly named folder
          File properFolder = new File(file.getParent(), properParent);
          if (properFolder.exists()) {
            Base.showWarning("Error",
                             "A folder named \"" + properParent + "\" " +
                             "already exists. Can't open sketch.", null);
            return false;
          }
          if (!properFolder.mkdirs()) {
            throw new IOException("Couldn't create sketch folder");
          }
          // copy the sketch inside
          File properPdeFile = new File(properFolder, file.getName());
          File origPdeFile = new File(path);
          Base.copyFile(origPdeFile, properPdeFile);

          // remove the original file, so user doesn't get confused
          origPdeFile.delete();

          // update with the new path
          path = properPdeFile.getAbsolutePath();

        } else if (result == JOptionPane.NO_OPTION) {
          return false;
        }
      }

      sketch = new Sketch(this, path);
      header.rebuild();      
      // Set the title of the window to "sketch_070752a - Processing 0126"
      setTitle(sketch.name + " | " + WINDOW_TITLE);
      // Disable untitled setting from previous document, if any
      untitled = false;

      // Store information on who's open and running
      // (in case there's a crash or something that can't be recovered)
      base.storeSketches();
      Preferences.save();

      // opening was successful
      return true;

    } catch (Exception e) {
      e.printStackTrace();
      error(e);
      return false;
    }
  }


  /**
   * Actually handle the save command. If 'immediately' is set to false,
   * this will happen in another thread so that the message area
   * will update and the save button will stay highlighted while the
   * save is happening. If 'immediately' is true, then it will happen
   * immediately. This is used during a quit, because invokeLater()
   * won't run properly while a quit is happening. This fixes
   * <A HREF="http://dev.processing.org/bugs/show_bug.cgi?id=276">Bug 276</A>.
   */
  public boolean handleSave(boolean immediately) {
    //stopRunner();
    handleStop();  // 0136

    if (untitled) {
      return handleSaveAs();
      // need to get the name, user might also cancel here

    } else if (immediately) {
      handleSave2();

    } else {
      SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            handleSave2();
          }
        });
    }
    return true;
  }


  protected void handleSave2() {
    toolbar.activate(EditorToolbar.SAVE);
    message("Saving...");
    try {
      if (sketch.save()) {
        message("Done Saving.");
      } else {
        message(EMPTY);
      }
      // rebuild sketch menu in case a save-as was forced
      // Disabling this for 0125, instead rebuild the menu inside
      // the Save As method of the Sketch object, since that's the
      // only one who knows whether something was renamed.
      //sketchbook.rebuildMenus();
      //sketchbook.rebuildMenusAsync();

    } catch (Exception e) {
      // show the error as a message in the window
      error(e);

      // zero out the current action,
      // so that checkModified2 will just do nothing
      //checkModifiedMode = 0;
      // this is used when another operation calls a save
    }
    //toolbar.clear();
    toolbar.deactivate(EditorToolbar.SAVE);
  }


  public boolean handleSaveAs() {
    //stopRunner();  // formerly from 0135
    handleStop();

    toolbar.activate(EditorToolbar.SAVE);

    //SwingUtilities.invokeLater(new Runnable() {
    //public void run() {
    message("Saving...");
    try {
      if (sketch.saveAs()) {
        message("Done Saving.");
        // Disabling this for 0125, instead rebuild the menu inside
        // the Save As method of the Sketch object, since that's the
        // only one who knows whether something was renamed.
        //sketchbook.rebuildMenusAsync();
      } else {
        message("Save Canceled.");
        return false;
      }
    } catch (Exception e) {
      // show the error as a message in the window
      error(e);

    } finally {
      // make sure the toolbar button deactivates
      toolbar.deactivate(EditorToolbar.SAVE);
    }

    return true;
  }


  /**
   * Handles calling the export() function on sketch, and
   * queues all the gui status stuff that comes along with it.
   * <p/>
   * Made synchronized to (hopefully) avoid problems of people
   * hitting export twice, quickly, and horking things up.
   */
  synchronized public void handleExport() {
    if (!handleExportCheckModified()) return;
    toolbar.activate(EditorToolbar.EXPORT);

    //SwingUtilities.invokeLater(new Runnable() {
    Thread t = new Thread(new Runnable() {
        public void run() {
          try {
            boolean success = sketch.exportApplet();
            if (success) {
              File appletFolder = new File(sketch.folder, "applet");
              Base.openFolder(appletFolder);
              message("Done exporting.");
            } else {
              // error message will already be visible
            }
          } catch (Exception e) {
            error(e);
          }
          //toolbar.clear();
          toolbar.deactivate(EditorToolbar.EXPORT);
        }});
    t.start();
  }


  synchronized public void handleExportApplication() {
    if (!handleExportCheckModified()) return;
    toolbar.activate(EditorToolbar.EXPORT);

    //SwingUtilities.invokeLater(new Runnable() {
    SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          message("Exporting application...");
          try {
            if (sketch.exportApplication(PConstants.WINDOWS) &&
                sketch.exportApplication(PConstants.MACOSX) &&
                sketch.exportApplication(PConstants.LINUX)) {
              Base.openFolder(sketch.folder);
              message("Done exporting.");
            } else {
              // error message will already be visible
            }
          } catch (Exception e) {
            message("Error during export.");
            e.printStackTrace();
          }
          //toolbar.clear();
          toolbar.deactivate(EditorToolbar.EXPORT);
        }});
  }


  /**
   * Checks to see if the sketch has been modified, and if so,
   * asks the user to save the sketch or cancel the export.
   * This prevents issues where an incomplete version of the sketch
   * would be exported, and is a fix for
   * <A HREF="http://dev.processing.org/bugs/show_bug.cgi?id=157">Bug 157</A>
   */
  public boolean handleExportCheckModified() {
    if (!sketch.modified) return true;

    Object[] options = { "OK", "Cancel" };
    int result = JOptionPane.showOptionDialog(this,
                                              "Save changes before export?",
                                              "Save",
                                              JOptionPane.OK_CANCEL_OPTION,
                                              JOptionPane.QUESTION_MESSAGE,
                                              null,
                                              options,
                                              options[0]);

    if (result == JOptionPane.OK_OPTION) {
      handleSave(true);

    } else {
      // why it's not CANCEL_OPTION is beyond me (at least on the mac)
      // but f-- it.. let's get this shite done..
      //} else if (result == JOptionPane.CANCEL_OPTION) {
      message("Export canceled, changes must first be saved.");
      //toolbar.clear();
      return false;
    }
    return true;
  }


  public void handlePageSetup() {
    //printerJob = null;
    if (printerJob == null) {
      printerJob = PrinterJob.getPrinterJob();
    }
    if (pageFormat == null) {
      pageFormat = printerJob.defaultPage();
    }
    pageFormat = printerJob.pageDialog(pageFormat);
    //System.out.println("page format is " + pageFormat);
  }


  public void handlePrint() {
    message("Printing...");
    //printerJob = null;
    if (printerJob == null) {
      printerJob = PrinterJob.getPrinterJob();
    }
    if (pageFormat != null) {
      //System.out.println("setting page format " + pageFormat);
      printerJob.setPrintable(textarea.getPainter(), pageFormat);
    } else {
      printerJob.setPrintable(textarea.getPainter());
    }
    // set the name of the job to the code name
    printerJob.setJobName(sketch.current.name);

    if (printerJob.printDialog()) {
      try {
        printerJob.print();
        message("Done printing.");

      } catch (PrinterException pe) {
        error("Error while printing.");
        pe.printStackTrace();
      }
    } else {
      message("Printing canceled.");
    }
    //printerJob = null;  // clear this out?
  }


  /**
   * Quit, but first ask user if it's ok. Also store preferences
   * to disk just in case they want to quit. Final exit() happens
   * in Editor since it has the callback from EditorStatus.
   */
  /*
  public void handleQuitInternal() {
    // doStop() isn't sufficient with external vm & quit
    // instead use doClose() which will kill the external vm
    doClose();

    checkModified(true);
  }
  */


  /**
   * Method for the MRJQuitHandler, needs to be dealt with differently
   * than the regular handler because OS X has an annoying implementation
   * <A HREF="http://developer.apple.com/qa/qa2001/qa1187.html">quirk</A>
   * that requires an exception to be thrown in order to properly cancel
   * a quit message.
   */
  /*
  public void handleQuit() {
    SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          handleQuitInternal();
        }
      });

    // Throw IllegalStateException so new thread can execute.
    // If showing dialog on this thread in 10.2, we would throw
    // upon JOptionPane.NO_OPTION
    throw new IllegalStateException("Quit Pending User Confirmation");
  }
  */


  /**
   * Actually do the quit action.
   */
  /*
  protected void handleQuit2() {
    storePreferences();
    Preferences.save();

    sketchbook.clean();
    console.handleQuit();

    //System.out.println("exiting here");
    System.exit(0);
  }
  */


  protected void handleReference() {
    String text = textarea.getSelectedText().trim();

    if (text.length() == 0) {
      message("First select a word to find in the reference.");

    } else {
      String referenceFile = PdeKeywords.getReference(text);
      //System.out.println("reference file is " + referenceFile);
      if (referenceFile == null) {
        message("No reference available for \"" + text + "\"");
      } else {
        Base.showReference(referenceFile + ".html");
      }
    }
  }


  public void highlightLine(int line) {
    // subtract one from the end so that the \n ain't included
    if (line >= textarea.getLineCount()) {
      // The error is at the end of this current chunk of code, 
      // so the last line needs to be selected. 
      line = textarea.getLineCount() - 1;
      if (textarea.getLineText(line).length() == 0) {
        // The last line may be zero length, meaning nothing to select. 
        // If so, back up one more line.
        line--;
      }
    }
    textarea.select(textarea.getLineStartOffset(line),
                    textarea.getLineStopOffset(line) - 1);
  }


  /*
  // wow! this is old code. bye bye!
  public void highlightLine(int lnum) {
    if (lnum < 0) {
      textarea.select(0, 0);
      return;
    }
    //System.out.println(lnum);
    String s = textarea.getText();
    int len = s.length();
    int st = -1;
    int ii = 0;
    int end = -1;
    int lc = 0;
    if (lnum == 0) st = 0;
    for (int i = 0; i < len; i++) {
      ii++;
      //if ((s.charAt(i) == '\n') || (s.charAt(i) == '\r')) {
      boolean newline = false;
      if (s.charAt(i) == '\r') {
        if ((i != len-1) && (s.charAt(i+1) == '\n')) {
          i++; //ii--;
        }
        lc++;
        newline = true;
      } else if (s.charAt(i) == '\n') {
        lc++;
        newline = true;
      }
      if (newline) {
        if (lc == lnum)
          st = ii;
        else if (lc == lnum+1) {
          //end = ii;
          // to avoid selecting entire, because doing so puts the
          // cursor on the next line [0090]
          end = ii - 1;
          break;
        }
      }
    }
    if (end == -1) end = len;

    // sometimes KJC claims that the line it found an error in is
    // the last line in the file + 1.  Just highlight the last line
    // in this case. [dmose]
    if (st == -1) st = len;

    textarea.select(st, end);
  }
  */


  // ...................................................................


  /**
   * Show an error int the status bar.
   */
  public void error(String what) {
    status.error(what);
    //new Exception("deactivating RUN").printStackTrace();
    toolbar.deactivate(EditorToolbar.RUN);
  }


  public void error(Exception e) {
    e.printStackTrace();
    if (e == null) {
      System.err.println("Editor.error() was passed a null exception.");
      return;
    }

    if (e instanceof RunnerException) {
      RunnerException re = (RunnerException) e;
      if (re.hasCodeIndex()) {
        sketch.setCurrent(re.getCodeIndex());
      }
      if (re.hasCodeLine()) {
        highlightLine(re.getCodeLine());
      }
    }

    // Since this will catch all Exception types, spend some time figuring 
    // out which kind and try to give a better error message to the user.
    String mess = e.getMessage();
    if (mess != null) {
      String javaLang = "java.lang.";
      if (mess.indexOf(javaLang) == 0) {
        mess = mess.substring(javaLang.length());
      }
      String rxString = "RuntimeException: ";
      if (mess.indexOf(rxString) == 0) {
        mess = mess.substring(rxString.length());
      }
      error(mess);
    }
    e.printStackTrace();
  }


  public void message(String msg) {
    status.notice(msg);
  }


  // ...................................................................


  /**
   * Returns the edit popup menu.
   */
  class TextAreaPopup extends JPopupMenu {
    //String currentDir = System.getProperty("user.dir");
    String referenceFile = null;

    JMenuItem cutItem, copyItem;
    JMenuItem referenceItem;


    public TextAreaPopup() {
      JMenuItem item;

      cutItem = new JMenuItem("Cut");
      cutItem.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            textarea.cut();
            sketch.setModified(true);
          }
      });
      this.add(cutItem);

      copyItem = new JMenuItem("Copy");
      copyItem.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            textarea.copy();
          }
        });
      this.add(copyItem);

      item = new JMenuItem("Paste");
      item.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            textarea.paste();
            sketch.setModified(true);
          }
        });
      this.add(item);

      item = new JMenuItem("Select All");
      item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          textarea.selectAll();
        }
      });
      this.add(item);

      this.addSeparator();

      referenceItem = new JMenuItem("Find in Reference");
      referenceItem.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            //Base.showReference(referenceFile + ".html");
            handleReference(); //textarea.getSelectedText());
          }
        });
      this.add(referenceItem);
    }

    // if no text is selected, disable copy and cut menu items
    public void show(Component component, int x, int y) {
      if (textarea.isSelectionActive()) {
        cutItem.setEnabled(true);
        copyItem.setEnabled(true);

        String sel = textarea.getSelectedText().trim();
        referenceFile = PdeKeywords.getReference(sel);
        referenceItem.setEnabled(referenceFile != null);

      } else {
        cutItem.setEnabled(false);
        copyItem.setEnabled(false);
        referenceItem.setEnabled(false);
      }
      super.show(component, x, y);
    }
  }
}

