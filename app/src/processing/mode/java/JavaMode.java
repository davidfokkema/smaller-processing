/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2010 Ben Fry and Casey Reas

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

package processing.mode.java;

import java.awt.Color;
import java.awt.Font;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.*;

import processing.app.*;
import processing.app.syntax.PdeKeywords;
import processing.app.syntax.SyntaxStyle;
import processing.app.syntax.TokenMarker;
import processing.core.PApplet;
import processing.mode.java.runner.Runner;


public class JavaMode extends Mode {
  private Runner runtime;

  // classpath for all known libraries for p5
  // (both those in the p5/libs folder and those with lib subfolders
  // found in the sketchbook)
//  static public String librariesClassPath;

  
  public Editor createEditor(Base base, String path, int[] location) {
    return new JavaEditor(base, path, location, this);
  }


  public JavaMode(Base base, File folder) {
    super(base, folder);

    try {
      loadKeywords();
    } catch (IOException e) {
      Base.showError("Problem loading keywords",
                     "Could not load keywords.txt, please re-install Processing.", e);
    }
    
    try {
      theme = new Settings(new File(folder, "theme.txt"));
    } catch (IOException e) {
      Base.showError("Problem loading theme.txt", 
                     "Could not load theme.txt, please re-install Processing", e);
    }
    
    /*
    item = newJMenuItem("Export", 'E');
    if (editor != null) {
      item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          editor.handleExport();
        }
      });
    } else {
      item.setEnabled(false);
    }
    fileMenu.add(item);

    item = newJMenuItemShift("Export Application", 'E');
    if (editor != null) {
      item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          editor.handleExportApplication();
        }
      });
    } else {
      item.setEnabled(false);
    }
    fileMenu.add(item);
     */
  }


  protected void loadKeywords() throws IOException {
    File file = new File(folder, "keywords.txt");
    BufferedReader reader = PApplet.createReader(file);

    tokenMarker = new PdeKeywords();
    keywordToReference = new HashMap<String, String>();

    String line = null;
    while ((line = reader.readLine()) != null) {
      String[] pieces = PApplet.trim(PApplet.split(line, '\t'));
      if (pieces.length >= 2) {
        String keyword = pieces[0];
        String coloring = pieces[1];

        if (coloring.length() > 0) {
          tokenMarker.addColoring(keyword, coloring);
        }
        if (pieces.length == 3) {
          String htmlFilename = pieces[2];
          if (htmlFilename.length() > 0) {
            keywordToReference.put(keyword, htmlFilename);
          }
        }
      }
    }
  }

  
  public String getTitle() {
    return "Standard";
  }


  public EditorToolbar createToolbar(Editor editor) {
    return new Toolbar(editor);
  }

  
  public Formatter createFormatter() {
    return new AutoFormat();
  }
  
  
//  public Editor createEditor(Base ibase, String path, int[] location) {
//  }
  

  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  public String getDefaultExtension() {
    return "pde";
  }
 
  
  public String[] getExtensions() {
    return new String[] { "pde", "java" };
  }

  
  public String[] getIgnorable() {
    return new String[] { 
      "applet",
      "application.macosx",
      "application.windows",
      "application.linux"
    };
  }

  
  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

  
  /**
   * Implements Sketch &rarr; Run.
   * @param present Set true to run in full screen (present mode).
   */
  public void handleRun(Editor editor, Sketch sketch) {
    Build build = new Build(editor);
    build.prepareRun();
    String appletClassName = sketch.build();
    if (appletClassName != null) {
      runtime = new Runner(Editor.this, sketch);
      runtime.launch(appletClassName, false);
    }
  }


  public void handlePresent(Editor editor, Sketch sketch) {
    Build build = new Build(editor);
    build.prepareRun();
    String appletClassName = sketch.build();
    if (appletClassName != null) {
      runtime = new Runner(Editor.this, sketch);
      runtime.launch(appletClassName, true);
    }
  }


  public void handleStop(Editor editor) {
//    try {
//      if (runtime != null) {
//        runtime.close();  // kills the window
//        runtime = null; // will this help?
//      }
//    } catch (Exception e) {
//      editor.statusError(e);
//    }
    if (runtime != null) {
      runtime.close();  // kills the window
      runtime = null; // will this help?
    }
  }
  
  
  public boolean handleExportApplet(Editor editor, Sketch sketch) {
    
  }
}