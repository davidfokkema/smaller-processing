/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Original Copyright (c) 1997, 1998 Van Di-Han HO. All Rights Reserved.
  Updates Copyright (c) 2001 Jason Pell.
  Further updates Copyright (c) 2003 Martin Gomez, Ateneo de Manila University
  Bug fixes Copyright (c) 2005-09 Ben Fry and Casey Reas

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, version 2.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package processing.app.tools;

import java.io.IOException;
import java.util.regex.Pattern;
import processing.app.Editor;
import processing.app.Preferences;
import processing.core.PApplet;

/**
 * Handler for dealing with auto format.
 * Contributed by Martin Gomez, additional bug fixes by Ben Fry.
 * 
 * After some further digging, this code in fact appears to be a modified 
 * version of Jason Pell's GPLed "Java Beautifier" class found here:
 * http://www.geocities.com/jasonpell/programs.html
 * Which is itself based on code from Van Di-Han Ho:
 * http://www.geocities.com/~starkville/vancbj_idx.html
 * [Ben Fry, August 2009]
 */
public class AutoFormat implements Tool {
  private static final String INDENT_STRING = " ";

  Editor editor;

  private char[] chars;
  private final StringBuilder currentLine = new StringBuilder();

  final StringBuilder result = new StringBuilder();

  int indentValue;
  boolean EOF;
  boolean a_flg, e_flg, if_flg, s_flg, q_flg;
  boolean s_if_flg[];
  int pos, lineNumber;
  int s_level[];
  int c_level;
  int sp_flg[][];
  int s_ind[][];
  int s_if_lev[];
  int if_lev, level;
  int ind[];
  int paren;
  int p_flg[];
  char l_char;
  int ct;
  int s_tabs[][];
  boolean jdoc_flag;
  char cc;
  int tabs;
  char currentChar;
  char c;

  public void init(final Editor editor) {
    this.editor = editor;
  }

  public String getMenuTitle() {
    return "Auto Format";
  }

  private void comment() throws IOException {
    final boolean save_s_flg = s_flg;

    currentLine.append(c = next()); // extra char
    while (true) {
      currentLine.append(c = next());
      while ((c != '/')) {
        if (c == '\n') {
          lineNumber++;
          putcoms();
          s_flg = true;
        }
        currentLine.append(c = next());
      }
      if (currentLine.length() >= 2
          && currentLine.charAt(currentLine.length() - 2) == '*') {
        jdoc_flag = false;
        break;
      }
    }

    putcoms();
    s_flg = save_s_flg;
    jdoc_flag = false;
    return;
  }

  private char get_string() throws IOException {
    char ch;
    while (true) {
      currentLine.append(ch = next());
      if (ch == '\\') {
        currentLine.append(next());
        continue;
      }
      if (ch == '\'' || ch == '"') {
        currentLine.append(cc = next());
        while (!EOF && cc != ch) {
          if (cc == '\\') {
            currentLine.append(next());
          }
          currentLine.append(cc = next());
        }
        continue;
      }
      if (ch == '\n') {
        indent_puts();
        a_flg = true;
        continue;
      }
      return ch;
    }
  }

  private void indent_puts() {
    if (currentLine.length() > 0) {
      if (s_flg) {
        final boolean shouldIndent = (tabs > 0)
            && (currentLine.charAt(0) != '{') && a_flg;
        if (shouldIndent) {
          tabs++;
        }
        p_tabs();
        s_flg = false;
        if (shouldIndent) {
          tabs--;
        }
        a_flg = false;
      }
      result.append(currentLine);
      currentLine.setLength(0);
    } else if (s_flg) {
      s_flg = a_flg = false;
    }
  }

  /* special edition of put string for comment processing */
  private void putcoms() {
    final boolean sav_s_flg = s_flg;
    if (currentLine.length() > 0) {
      if (s_flg) {
        p_tabs();
        s_flg = false;
      }
      int i = 0;
      while (currentLine.charAt(i) == ' ') {
        i++;
      }
      if (lookup_com("/**")) {
        jdoc_flag = true;
      }
      if (currentLine.charAt(i) == '/' && currentLine.charAt(i + 1) == '*') {
        if ((currentChar != ';') && sav_s_flg) {
          result.append(currentLine.substring(i));
        } else {
          result.append(currentLine);
        }
      } else {
        if (currentLine.charAt(i) == '*' || !jdoc_flag) {
          result.append((INDENT_STRING + currentLine.substring(i)));
        } else {
          result.append((" * " + currentLine.substring(i)));
        }
      }
      currentLine.setLength(0);
    }
  }

  private void cpp_comment() throws IOException {
    c = next();
    while (c != '\n') {
      currentLine.append(c);
      c = next();
    }
    lineNumber++;
    indent_puts();
    s_flg = true;
  }

  /* expand indentValue into tabs and spaces */
  private void p_tabs() {
    int i, k;

    if (tabs < 0) {
      tabs = 0;
    }
    if (tabs == 0) {
      return;
    }
    i = tabs * indentValue; // calc number of spaces
    for (k = 0; k < i; k++) {
      result.append(INDENT_STRING);
    }
  }

  private char peek() {
    if (pos + 1 >= chars.length) {
      return 0;
    }
    return chars[pos + 1];
  }

  private char lastNonWhitespace = 0;

  private int prev() {
    return lastNonWhitespace;
  }

  private char next() {
    if (EOF) {
      return 0;
    }
    pos++;
    if (pos < chars.length) {
      currentChar = chars[pos];
      if (!Character.isWhitespace(currentChar))
        lastNonWhitespace = currentChar;
    } else {
      currentChar = 0;
    }
    if (pos == chars.length - 1) {
      EOF = true;
    }
    return currentChar;
  }

  /* else processing */
  private void gotelse() {
    tabs = s_tabs[c_level][if_lev];
    p_flg[level] = sp_flg[c_level][if_lev];
    ind[level] = s_ind[c_level][if_lev];
    if_flg = true;
  }

  /* read to new_line */
  private boolean getnl() throws IOException {
    final int savedTabs = tabs;
    char c = peek();
    while (!EOF && (c == '\t' || c == ' ')) {
      currentLine.append(next());
      c = peek();
    }

    if (c == '/') {
      currentLine.append(next());
      c = peek();
      if (c == '*') {
        currentLine.append(next());
        comment();
      } else if (c == '/') {
        currentLine.append(next());
        cpp_comment();
        return true;
      }
    }

    c = peek();
    if (c == '\n') {
      // eat it
      next();
      lineNumber++;
      tabs = savedTabs;
      return true;
    }
    return false;
  }

  private boolean lookup(final String keyword) {
    return Pattern.matches("^\\s*" + keyword + "(?![a-zA-Z0-9_&]).*$",
      currentLine);
  }

  private boolean lookup_com(final String keyword) {
    final String regex = "^\\s*" + keyword.replaceAll("\\*", "\\\\*") + ".*$";
    return Pattern.matches(regex, currentLine);
  }

  public void run() {
    // Adding an additional newline as a hack around other errors
    final String normalizedText = editor.getText().replaceAll("\r", "");
    final String cleanText = normalizedText
        + (normalizedText.endsWith("\n") ? "" : "\n");
    result.setLength(0);
    indentValue = Preferences.getInteger("editor.tabs.size");

    lineNumber = 0;
    q_flg = e_flg = a_flg = if_flg = false;
    s_flg = true;
    c_level = if_lev = level = paren = 0;
    tabs = 0;
    jdoc_flag = false;

    s_level = new int[10];
    sp_flg = new int[20][10];
    s_ind = new int[20][10];
    s_if_lev = new int[10];
    s_if_flg = new boolean[10];
    ind = new int[10];
    p_flg = new int[10];
    s_tabs = new int[20][10];
    pos = -1;
    chars = cleanText.toCharArray();
    lineNumber = 1;

    EOF = false; // set in getchr when EOF

    try {
      while (!EOF) {
        c = next();
        switch (c) {
        default:
          currentLine.append(c);
          if (c != ',') {
            l_char = c;
          }
          break;

        case ' ':
        case '\t':
          if (lookup("else")) {
            gotelse();
            if ((!s_flg) || currentLine.length() > 0) {
              currentLine.append(c);
            }
            indent_puts();
            s_flg = false;
            break;
          }
          if ((!s_flg) || currentLine.length() > 0) {
            currentLine.append(c);
          }
          break;

        case '\n':
          lineNumber++;
          if (EOF) {
            break;
          }
          e_flg = lookup("else");
          if (e_flg) {
            gotelse();
          }
          if (lookup_com("//")) {
            final char lastChar = currentLine.charAt(currentLine.length() - 1);
            if (lastChar == '\n') {
              currentLine.setLength(currentLine.length() - 1);
            }
          }

          indent_puts();
          result.append("\n");
          s_flg = true;
          if (e_flg) {
            p_flg[level]++;
            tabs++;
          } else if (prev() == l_char) {
            a_flg = true;
          }
          break;

        case '{':
          if (lookup("else")) {
            gotelse();
          }
          if (s_if_lev.length == c_level) {
            s_if_lev = PApplet.expand(s_if_lev);
            s_if_flg = PApplet.expand(s_if_flg);
          }
          s_if_lev[c_level] = if_lev;
          s_if_flg[c_level] = if_flg;
          if_lev = 0;
          if_flg = false;
          c_level++;
          if (s_flg && p_flg[level] != 0) {
            p_flg[level]--;
            tabs--;
          }
          currentLine.append(c);
          indent_puts();
          getnl();
          indent_puts();
          //fprintf(outfil,"\n");
          result.append("\n");
          tabs++;
          s_flg = true;
          if (p_flg[level] > 0) {
            ind[level] = 1;
            level++;
            s_level[level] = c_level;
          }
          break;

        case '}':
          c_level--;
          if (c_level < 0) {
            c_level = 0;
            currentLine.append(c);
            indent_puts();
            break;
          }
          if_lev = s_if_lev[c_level] - 1;
          if (if_lev < 0) {
            if_lev = 0;
          }
          if_flg = s_if_flg[c_level];
          indent_puts();
          tabs--;
          p_tabs();
          result.append(c);
          if (peek() == ';') {
            result.append(next());
          }
          getnl();
          indent_puts();
          result.append("\n");
          s_flg = true;
          if (c_level < s_level[level]) {
            if (level > 0) {
              level--;
            }
          }
          if (ind[level] != 0) {
            tabs -= p_flg[level];
            p_flg[level] = 0;
            ind[level] = 0;
          }
          break;

        case '"':
        case '\'':
          currentLine.append(c);
          cc = next();
          while (!EOF && cc != c) {
            currentLine.append(cc);
            if (cc == '\\') {
              currentLine.append(cc = next());
            }
            if (cc == '\n') {
              lineNumber++;
              indent_puts();
              s_flg = true;
            }
            cc = next();
          }
          currentLine.append(cc);
          if (getnl()) {
            l_char = cc;
            // push a newline into the stream
            chars[pos--] = '\n';
          }
          break;

        case ';':
          currentLine.append(c);
          indent_puts();
          if (p_flg[level] > 0 && ind[level] == 0) {
            tabs -= p_flg[level];
            p_flg[level] = 0;
          }
          getnl();
          indent_puts();
          result.append("\n");
          s_flg = true;
          if (if_lev > 0) {
            if (if_flg) {
              if_lev--;
              if_flg = false;
            } else {
              if_lev = 0;
            }
          }
          break;

        case '\\':
          currentLine.append(c);
          currentLine.append(next());
          break;

        case '?':
          q_flg = true;
          currentLine.append(c);
          break;

        case ':':
          currentLine.append(c);
          if (peek() == ':') {
            indent_puts();
            result.append(next());
            break;
          }

          if (q_flg) {
            q_flg = false;
            break;
          }
          if (!lookup("default") && !lookup("case")) {
            s_flg = false;
            indent_puts();
          } else {
            tabs--;
            indent_puts();
            tabs++;
          }
          if (peek() == ';') {
            result.append(next());
          }
          getnl();
          indent_puts();
          result.append("\n");
          s_flg = true;
          break;

        case '/':
          final char la = peek();
          if (la == '/') {
            currentLine.append(c).append(next());
            cpp_comment();
            result.append("\n");
          } else if (la == '*') {
            if (currentLine.length() > 0) {
              indent_puts();
            }
            currentLine.append(c).append(next());
            comment();
          } else {
            currentLine.append(c);
          }
          break;

        case ')':
          paren--;
          if (paren < 0) {
            paren = 0;
          }
          currentLine.append(c);
          indent_puts();
          if (getnl()) {
            chars[pos--] = '\n';
            if (paren != 0) {
              a_flg = true;
            } else if (tabs > 0) {
              p_flg[level]++;
              tabs++;
              ind[level] = 0;
            }
          }
          break;

        case '(':
          currentLine.append(c);
          paren++;
          if ((lookup("for"))) {
            c = get_string();
            while (c != ';') {
              c = get_string();
            }
            ct = 0;
            int for_done = 0;
            while (for_done == 0) {
              c = get_string();
              while (c != ')') {
                if (c == '(') {
                  ct++;
                }
                c = get_string();
              }
              if (ct != 0) {
                ct--;
              } else {
                for_done = 1;
              }
            } // endwhile for_done
            paren--;
            if (paren < 0) {
              paren = 0;//EOF = true;
              //System.out.println("eof d");
            }
            indent_puts();
            if (getnl()) {
              chars[pos--] = '\n';
              p_flg[level]++;
              tabs++;
              ind[level] = 0;
            }
            break;
          }

          if (lookup("if")) {
            indent_puts();
            s_tabs[c_level][if_lev] = tabs;
            sp_flg[c_level][if_lev] = p_flg[level];
            s_ind[c_level][if_lev] = ind[level];
            if_lev++;
            if_flg = true;
          }
        } // end switch
      } // end while not EOF

      // save current (rough) selection point
      int selectionEnd = editor.getSelectionStop();

      // make sure the caret would be past the end of the text
      if (result.length() < selectionEnd - 1) {
        selectionEnd = result.length() - 1;
      }

      final String formattedText = result.toString();
      if (formattedText.equals(cleanText)) {
        editor.statusNotice("No changes necessary for Auto Format.");

      } else if (paren != 0) {
        // warn user if there are too many parens in either direction
        editor.statusError("Auto Format Canceled: Too many "
            + ((paren < 0) ? "right" : "left") + " parentheses.");

      } else if (c_level != 0) { // check braces only if parens are ok
        editor.statusError("Auto Format Canceled: Too many "
            + ((c_level < 0) ? "right" : "left") + " curly braces.");

      } else {
        // replace with new bootiful text
        // selectionEnd hopefully at least in the neighborhood
        editor.setText(formattedText);
        editor.setSelection(selectionEnd, selectionEnd);
        editor.getSketch().setModified(true);
        // mark as finished
        editor.statusNotice("Auto Format finished.");
      }

    } catch (final Exception e) {
      editor.statusError(e);
    }
  }
}
