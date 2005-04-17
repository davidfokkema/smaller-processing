/* -*- mode: jde; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2005 Ben Fry and Casey Reas

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

import processing.core.*;


/**
 * Threaded class to check for updates.
 * <P>
 * This generates a random id number for this user,
 * and hits the server to check for updates.
 * The id number is used so that we can keep track of
 * how many people are using Processing, which helps us
 * when writing grant proposals and that kind of thing.
 */
public class UpdateCheck implements Runnable {
  Editor editor;
  String downloadURL = "http://processing.org/download/latest.txt";


  public UpdateCheck(Editor editor) {
    this.editor = editor;
    Thread thread = new Thread(this);
    thread.start();
  }


  public void run() {
    //System.out.println("checking for updates...");

    // generate a random id in case none exists yet
    Random r = new Random();
    long id = r.nextLong();

    String idString = Preferences.get("update.id");
    if (idString != null) {
      id = Long.parseLong(idString);
    } else {
      Preferences.set("update.id", String.valueOf(id));
    }

    try {
      //int id = PApplet.parseInt(idString);
      //int latest = PApplet.toInt(PApplet.loadStrings());
      int latest = readInt(downloadURL + "?" + id);

      String prompt =
        "A new version of Processing is available,\n" +
        "would you like to visit the Processing download page?";

      if (latest > Base.VERSION) {
        Object[] options = { "Yes", "No" };
        int result = JOptionPane.showOptionDialog(editor,
                                                  prompt,
                                                  "Update",
                                                  JOptionPane.YES_NO_OPTION,
                                                  JOptionPane.QUESTION_MESSAGE,
                                                  null,
                                                  options,
                                                  options[0]);

        if (result == JOptionPane.YES_OPTION) {
          Base.openURL("http://processing.org/download/");

        //} else if (result == JOptionPane.NO_OPTION) {
        }
      }
    } catch (Exception e) {
      //e.printStackTrace();
      //System.err.println("Error while trying to check for an update.");
    }
  }


  protected int readInt(String filename) throws Exception {
    URL url = new URL(filename);
    InputStream stream = url.openStream();
    InputStreamReader isr = new InputStreamReader(stream);
    BufferedReader reader = new BufferedReader(isr);
    return Integer.parseInt(reader.readLine());
  }
}
