/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  PdePreprocessor - wrapper for default ANTLR-generated parser
  Part of the Processing project - http://processing.org

  Copyright (c) 2004-05 Ben Fry and Casey Reas
  Copyright (c) 2001-04 Massachusetts Institute of Technology

  ANTLR-generated parser and several supporting classes written
  by Dan Mosedale via funding from the Interaction Institute IVREA.

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

package processing.app.preproc;

import processing.app.*;
import processing.core.*;

import java.io.*;

import antlr.*;
import antlr.collections.*;
//import antlr.collections.impl.*;

import com.oroinc.text.regex.*;


/**
 * Class that orchestrates preprocessing p5 syntax into straight Java.
 * <P/>
 * <B>Current Preprocessor Subsitutions:</B>
 * <UL>
 * <LI>any function not specified as being protected or private will
 * be made 'public'. this means that <TT>void setup()</TT> becomes
 * <TT>public void setup()</TT>. This is important to note when
 * coding with core.jar outside of the PDE.
 * <LI><TT>compiler.substitute_floats</TT> (currently "substitute_f")
 * treat doubles as floats, i.e. 12.3 becomes 12.3f so that people
 * don't have to add f after their numbers all the time since it's
 * confusing for beginners.
 * <LI><TT>compiler.enhanced_casting</TT> byte(), char(), int(), float()
 * works for casting. this is basic in the current implementation, but
 * should be expanded as described above. color() works similarly to int(),
 * however there is also a *function* called color(r, g, b) in p5.
 * <LI><TT>compiler.color_datatype</TT> 'color' is aliased to 'int'
 * as a datatype to represent ARGB packed into a single int, commonly
 * used in p5 for pixels[] and other color operations. this is just a
 * search/replace type thing, and it can be used interchangeably with int.
 * <LI><TT>compiler.web_colors</TT> (currently "inline_web_colors")
 * color c = #cc0080; should unpack to 0xffcc0080 (the ff at the top is
 * so that the color is opaque), which is just an int.
 * </UL>
 * <B>Other preprocessor functionality</B>
 * <UL>
 * <LI>detects what 'mode' the program is in: static (no function
 * brackets at all, just assumes everything is in draw), active
 * (setup plus draw or loop), and java mode (full java support).
 * http://processing.org/reference/environment/
 * </UL>
 * <P/>
 * The PDE Preprocessor is based on the Java Grammar that comes with
 * ANTLR 2.7.2.  Moving it forward to a new version of the grammar
 * shouldn't be too difficult.
 * <P/>
 * Here's some info about the various files in this directory:
 * <P/>
 * <TT>java.g:</TT> this is the ANTLR grammar for Java 1.3/1.4 from the
 * ANTLR distribution.  It is in the public domain.  The only change to
 * this file from the original this file is the uncommenting of the
 * clauses required to support assert().
 * <P/>
 * <TT>java.tree.g:</TT> this describes the Abstract Syntax Tree (AST)
 * generated by java.g.  It is only here as a reference for coders hacking
 * on the preprocessor, it is not built or used at all.  Note that pde.g
 * overrides some of the java.g rules so that in PDE ASTs, there are a
 * few minor differences.  Also in the public domain.
 * <P/>
 * <TT>pde.g:</TT> this is the grammar and lexer for the PDE language
 * itself. It subclasses the java.g grammar and lexer.  There are a couple
 * of overrides to java.g that I hope to convince the ANTLR folks to fold
 * back into their grammar, but most of this file is highly specific to
 * PDE itself.
 * <TT>PdeEmitter.java:</TT> this class traverses the AST generated by
 * the PDE Recognizer, and emits it as Java code, doing any necessary
 * transformations along the way.  It is based on JavaEmitter.java,
 * available from antlr.org, written by Andy Tripp <atripp@comcast.net>,
 * who has given permission for it to be distributed under the GPL.
 * <P/>
 * <TT>ExtendedCommonASTWithHiddenTokens.java:</TT> this adds a necessary
 * initialize() method, as well as a number of methods to allow for XML
 * serialization of the parse tree in a such a way that the hidden tokens
 * are visible.  Much of the code is taken from the original
 * CommonASTWithHiddenTokens class.  I hope to convince the ANTLR folks
 * to fold these changes back into that class so that this file will be
 * unnecessary.
 * <P/>
 * <TT>TokenStreamCopyingHiddenTokenFilter.java:</TT> this class provides
 * TokenStreamHiddenTokenFilters with the concept of tokens which can be
 * copied so that they are seen by both the hidden token stream as well
 * as the parser itself.  This is useful when one wants to use an
 * existing parser (like the Java parser included with ANTLR) that throws
 * away some tokens to create a parse tree which can be used to spit out
 * a copy of the code with only minor modifications.  Partially derived
 * from ANTLR code.  I hope to convince the ANTLR folks to fold this
 * functionality back into ANTLR proper as well.
 * <P/>
 * <TT>whitespace_test.pde:</TT> a torture test to ensure that the
 * preprocessor is correctly preserving whitespace, comments, and other
 * hidden tokens correctly.  See the comments in the code for details about
 * how to run the test.
 * <P/>
 * All other files in this directory are generated at build time by ANTLR
 * itself.  The ANTLR manual goes into a fair amount of detail about the
 * what each type of file is for.
 * <P/>
 */
public class PdePreprocessor {

  static final int JDK11 = 0;
  static final int JDK13 = 1;
  static final int JDK14 = 2;

  static String defaultImports[][] = new String[3][];

  // these ones have the .* at the end, since a class name might be at the end 
  // instead of .* which would make trouble other classes using this can lop 
  // off the . and anything after it to produce a package name consistently.
  public String extraImports[];

  // imports just from the code folder, treated differently
  // than the others, since the imports are auto-generated.
  public String codeFolderImports[];

  static public final int STATIC = 0;  // formerly BEGINNER
  static public final int ACTIVE = 1;  // formerly INTERMEDIATE
  static public final int JAVA   = 2;  // formerly ADVANCED
  // static to make it easier for the antlr preproc to get at it
  static public int programType;
  static public boolean foundMain;

  Reader programReader;
  String buildPath;

  // used for calling the ASTFactory to get the root node
  private static final int ROOT_ID = 0;


  /**
   * These may change in-between (if the prefs panel adds this option)
   * so grab them here on construction.
   */
  public PdePreprocessor() {
    defaultImports[JDK11] =
      PApplet.split(Preferences.get("preproc.imports.jdk11"), ',');
    defaultImports[JDK13] =
      PApplet.split(Preferences.get("preproc.imports.jdk13"), ',');
    defaultImports[JDK14] =
      PApplet.split(Preferences.get("preproc.imports.jdk14"), ',');
  }


  /**
   * Used by PdeEmitter.dumpHiddenTokens()
   */
  public static TokenStreamCopyingHiddenTokenFilter filter;


  /**
   * preprocesses a pde file and write out a java file
   * @param pretty true if should also space out/indent lines
   * @return the classname of the exported Java
   */
  //public String write(String program, String buildPath, String name,
  //                  String extraImports[]) throws java.lang.Exception {
  public String write(String program, String buildPath,
                      String name, String codeFolderPackages[], boolean pretty)
    throws java.lang.Exception {
    // need to reset whether or not this has a main()
    foundMain = false;

    // if the program ends with no CR or LF an OutOfMemoryError will happen.
    // not gonna track down the bug now, so here's a hack for it:
    // bug filed at http://dev.processing.org/bugs/show_bug.cgi?id=5
    //if ((program.length() > 0) &&
    //program.charAt(program.length()-1) != '\n') {
    program += "\n";
    //}

    // if the program ends with an unterminated multiline comment,
    // an OutOfMemoryError or NullPointerException will happen.
    // again, not gonna bother tracking this down, but here's a hack.
    // http://dev.processing.org/bugs/show_bug.cgi?id=16
    Sketch.scrubComments(program);
    // this returns the scrubbed version, but more important for this
    // function, it'll check to see if there are errors with the comments.

    if (Preferences.getBoolean("preproc.substitute_unicode")) {
      // check for non-ascii chars (these will be/must be in unicode format)
      char p[] = program.toCharArray();
      int unicodeCount = 0;
      for (int i = 0; i < p.length; i++) {
        if (p[i] > 127) unicodeCount++;
      }
      // if non-ascii chars are in there, convert to unicode escapes
      if (unicodeCount != 0) {
        // add unicodeCount * 5.. replacing each unicode char
        // with six digit uXXXX sequence (xxxx is in hex)
        // (except for nbsp chars which will be a replaced with a space)
        int index = 0;
        char p2[] = new char[p.length + unicodeCount*5];
        for (int i = 0; i < p.length; i++) {
          if (p[i] < 128) {
            p2[index++] = p[i];

          } else if (p[i] == 160) {  // unicode for non-breaking space
            p2[index++] = ' ';

          } else {
            int c = p[i];
            p2[index++] = '\\';
            p2[index++] = 'u';
            char str[] = Integer.toHexString(c).toCharArray();
            // add leading zeros, so that the length is 4
            //for (int i = 0; i < 4 - str.length; i++) p2[index++] = '0';
            for (int m = 0; m < 4 - str.length; m++) p2[index++] = '0';
            System.arraycopy(str, 0, p2, index, str.length);
            index += str.length;
          }
        }
        program = new String(p2, 0, index);
      }
    }

    // if this guy has his own imports, need to remove them
    // just in case it's not an advanced mode sketch
    PatternMatcher matcher = new Perl5Matcher();
    PatternCompiler compiler = new Perl5Compiler();
    String mess = "^\\s*(import\\s+)(\\S+)(\\s*;)";
    java.util.Vector imports = new java.util.Vector();

    Pattern pattern = null;
    try {
      pattern = compiler.compile(mess);
    } catch (MalformedPatternException e) {
      e.printStackTrace();
      return null;
    }

    do {
      PatternMatcherInput input = new PatternMatcherInput(program);
      if (!matcher.contains(input, pattern)) break;

      MatchResult result = matcher.getMatch();
      String piece1 = result.group(1).toString();
      String piece2 = result.group(2).toString();  // the package name
      String piece3 = result.group(3).toString();
      String piece = piece1 + piece2 + piece3;
      int len = piece.length();

      imports.add(piece2);
      int idx = program.indexOf(piece);
      // just remove altogether?
      program = program.substring(0, idx) + program.substring(idx + len);

    } while (true);

    extraImports = new String[imports.size()];
    imports.copyInto(extraImports);

    if (codeFolderPackages != null) {
      codeFolderImports = new String[codeFolderPackages.length];
      for (int i = 0; i < codeFolderPackages.length; i++) {
        codeFolderImports[i] = codeFolderPackages[i] + ".*";
      }
    } else {
      codeFolderImports = null;
    }

    // do this after the program gets re-combobulated
    this.programReader = new StringReader(program);
    this.buildPath = buildPath;

    // create a lexer with the stream reader, and tell it to handle
    // hidden tokens (eg whitespace, comments) since we want to pass these
    // through so that the line numbers when the compiler reports errors
    // match those that will be highlighted in the PDE IDE
    //
    PdeLexer lexer  = new PdeLexer(programReader);
    lexer.setTokenObjectClass("antlr.CommonHiddenStreamToken");

    // create the filter for hidden tokens and specify which tokens to
    // hide and which to copy to the hidden text
    //
    filter = new TokenStreamCopyingHiddenTokenFilter(lexer);
    filter.hide(PdeRecognizer.SL_COMMENT);
    filter.hide(PdeRecognizer.ML_COMMENT);
    filter.hide(PdeRecognizer.WS);
    filter.copy(PdeRecognizer.SEMI);
    filter.copy(PdeRecognizer.LPAREN);
    filter.copy(PdeRecognizer.RPAREN);
    filter.copy(PdeRecognizer.LCURLY);
    filter.copy(PdeRecognizer.RCURLY);
    filter.copy(PdeRecognizer.COMMA);
    filter.copy(PdeRecognizer.RBRACK);
    filter.copy(PdeRecognizer.LBRACK);
    filter.copy(PdeRecognizer.COLON);

    // create a parser and set what sort of AST should be generated
    //
    PdeRecognizer parser = new PdeRecognizer(filter);

    // use our extended AST class
    //
    parser.setASTNodeClass("antlr.ExtendedCommonASTWithHiddenTokens");

    // start parsing at the compilationUnit non-terminal
    //
    parser.pdeProgram();

    // set up the AST for traversal by PdeEmitter
    //
    ASTFactory factory = new ASTFactory();
    AST parserAST = parser.getAST();
    AST rootNode = factory.create(ROOT_ID, "AST ROOT");
    rootNode.setFirstChild(parserAST);

    // unclear if this actually works, but it's worth a shot
    //
    //((CommonAST)parserAST).setVerboseStringConversion(
    //  true, parser.getTokenNames());
    // (made to use the static version because of jikes 1.22 warning)
    CommonAST.setVerboseStringConversion(true, parser.getTokenNames());

    // if this is an advanced program, the classname is already defined.
    //
    if (programType == JAVA) {
      name = getFirstClassName(parserAST);
    }

    // if 'null' was passed in for the name, but this isn't
    // a 'java' mode class, then there's a problem, so punt.
    //
    if (name == null) return null;

    // output the code
    //
    PdeEmitter emitter = new PdeEmitter();
    File streamFile = new File(buildPath, name + ".java");
    PrintStream stream = new PrintStream(new FileOutputStream(streamFile));

    //writeHeader(stream, extraImports, name);
    writeHeader(stream, name, pretty);

    emitter.setOut(stream);
    emitter.print(rootNode);

    writeFooter(stream, name, pretty);
    stream.close();

    // if desired, serialize the parse tree to an XML file.  can
    // be viewed usefully with Mozilla or IE

    if (Preferences.getBoolean("preproc.output_parse_tree")) {

      stream = new PrintStream(new FileOutputStream("parseTree.xml"));
      stream.println("<?xml version=\"1.0\"?>");
      stream.println("<document>");
      OutputStreamWriter writer = new OutputStreamWriter(stream);
      if (parserAST != null) {
        ((CommonAST)parserAST).xmlSerialize(writer);
      }
      writer.flush();
      stream.println("</document>");
      writer.close();
    }

    return name;
  }


  /**
   * Write any required header material (eg imports, class decl stuff)
   *
   * @param out                 PrintStream to write it to.
   * @param exporting           Is this being exported from PDE?
   * @param name                Name of the class being created.
   */
  void writeHeader(PrintStream out, String className, boolean pretty) {

    // must include processing.core
    out.print("import processing.core.*; ");
    if (pretty) out.println();

    // emit emports that are needed for classes from the code folder
    if (extraImports != null) {
      for (int i = 0; i < extraImports.length; i++) {
        out.print("import " + extraImports[i] + "; ");
        if (pretty) out.println();
      }
    }

    if (codeFolderImports != null) {
      for (int i = 0; i < codeFolderImports.length; i++) {
        out.print("import " + codeFolderImports[i] + "; ");
        if (pretty) out.println();
      }
    }

    // emit standard imports (read from pde.properties)
    // for each language level that's being used.
    String jdkVersionStr = Preferences.get("preproc.jdk_version");

    int jdkVersion = JDK11;  // default
    if (jdkVersionStr.equals("1.3")) { jdkVersion = JDK13; };
    if (jdkVersionStr.equals("1.4")) { jdkVersion = JDK14; };

    for (int i = 0; i <= jdkVersion; i++) {
      for (int j = 0; j < defaultImports[i].length; j++) {
        out.print("import " + defaultImports[i][j] + ".*; ");
        if (pretty) out.println();
      }
    }

    //boolean opengl = Preferences.get("renderer").equals("opengl");
    //if (opengl) {
    //out.println("import processing.opengl.*; ");
    //}

    if (programType < JAVA) {
      if (pretty) out.println();

      // open the class definition
      out.print("public class " + className + " extends PApplet {");
      if (pretty) out.println();

      if (programType == STATIC) {
        // now that size() and background() can go inside of draw()
        // actually, use setup(), because when running externally
        // the applet size needs to be set before the window is drawn,
        // meaning that after setup() things need to be ducky.
        //out.print("public void draw() {");
        out.print("public void setup() {");
        if (pretty) out.println();

        // split up lines to indent four spaces
        //String[] lines = PApplet.split(program, '\n');
        //program = "    " + PApplet.join(lines, "\n    ");
      //} else {
        // indent only two spaces
        //String[] lines = PApplet.split(program, '\n');
        //program = "    " + PApplet.join(lines, "\n    ");
      }
    }
  }

  /**
   * Write any necessary closing text.
   *
   * @param out PrintStream to write it to.
   */
  void writeFooter(PrintStream out, String className, boolean pretty) {

    if (programType == STATIC) {
      // close off draw() definition
      if (pretty) out.print("  ");
      out.print("noLoop(); ");
      if (pretty) out.println();
      out.print("} ");
      if (pretty) out.println();
    }

    if ((programType == STATIC) || (programType < JAVA)) {
      if (!PdePreprocessor.foundMain) {
        out.print("  static public void main(String args[]) { ");
        if (pretty) out.println();
        out.print("    PApplet.main(new String[] { \"" + className + "\" });");
        if (pretty) out.println();
        out.print("  }");
        if (pretty) out.println();
      }
    }

    if (programType < JAVA) {
      // close off the class definition
      out.print("}");
    }
  }


  static String advClassName = "";

  /**
   * Find the first CLASS_DEF node in the tree, and return the name of the
   * class in question.
   *
   * XXXdmose right now, we're using a little hack to the grammar to get
   * this info.  In fact, we should be descending the AST passed in.
   */
  String getFirstClassName(AST ast) {

    String t = advClassName;
    advClassName = "";

    return t;
  }

}
