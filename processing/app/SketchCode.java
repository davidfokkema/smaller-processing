/* -*- mode: jde; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  SketchCode - data class for a single file inside a sketch
  Part of the Processing project - http://processing.org

  Copyright (c) 2004-05 Ben Fry and Casey Reas
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

import java.io.*;


public class SketchCode {
  String name;  // pretty name (no extension), not the full file name
  File file;
  int flavor;

  String program;
  public boolean modified;
  //SketchHistory history;  // TODO add history information

  String preprocName;  // name of .java file after preproc
  int lineOffset;  // where this code starts relative to the concat'd code


  public SketchCode(String name, File file, int flavor) {
    this.name = name;
    this.file = file;
    this.flavor = flavor;

    try {
      load();
    } catch (IOException e) {
      System.err.println("error while loading code " + name);
    }
  }


  /**
   * Load this piece of code from a file.
   */
  public void load() throws IOException {
    program = Base.loadFile(file);
    modified = false;
  }


  /**
   * Save this piece of code, regardless of whether the modified
   * flag is set or not.
   */
  public void save() throws IOException {
    // TODO re-enable history
    //history.record(s, SketchHistory.SAVE);

    Base.saveFile(program, file);
    modified = false;
  }
}
