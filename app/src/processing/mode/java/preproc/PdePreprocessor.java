/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  PdePreprocessor - wrapper for default ANTLR-generated parser
  Part of the Processing project - http://processing.org

  Copyright (c) 2004-10 Ben Fry and Casey Reas
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

package processing.mode.java.preproc;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import processing.app.Preferences;
import processing.app.RunnerException;
import processing.app.antlr.PdeLexer;
import processing.app.antlr.PdeRecognizer;
import processing.app.antlr.PdeTokenTypes;
import processing.core.PApplet;
import antlr.*;
import antlr.collections.AST;

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
 * 
 * Hacked to death in 2010 by
 * @author Jonathan Feinberg &lt;jdf@pobox.com&gt;
 */
public class PdePreprocessor {

  // used for calling the ASTFactory to get the root node
  private static final int ROOT_ID = 0;

  protected final String indent;
  private final String name;

  public static enum Mode {
    STATIC, ACTIVE, JAVA
  }

  private TokenStreamCopyingHiddenTokenFilter filter;

  private boolean foundMain;

  public void setFoundMain(boolean foundMain) {
    this.foundMain = foundMain;
  }

  public boolean getFoundMain() {
    return foundMain;
  }

  private String advClassName = "";

  public void setAdvClassName(final String advClassName) {
    this.advClassName = advClassName;
  }

  protected Mode mode;

  public void setMode(final Mode mode) {
    // System.err.println("Setting mode to " + mode);
    this.mode = mode;
  }

  public PdePreprocessor(final String sketchName) {
    this(sketchName, Preferences.getInteger("editor.tabs.size"));
  }

  public PdePreprocessor(final String sketchName, final int tabSize) {
    this.name = sketchName;
    final char[] indentChars = new char[tabSize];
    Arrays.fill(indentChars, ' ');
    indent = new String(indentChars);
  }

  CommonHiddenStreamToken getHiddenAfter(final CommonHiddenStreamToken t) {
    return filter.getHiddenAfter(t);
  }

  CommonHiddenStreamToken getInitialHiddenToken() {
    return filter.getInitialHiddenToken();
  }

  private static int countNewlines(final String s) {
    int count = 0;
    for (int pos = s.indexOf('\n', 0); pos >= 0; pos = s.indexOf('\n', pos + 1))
      count++;
    return count;
  }

  private static void checkForUnterminatedMultilineComment(final String program)
      throws RunnerException {
    final int length = program.length();
    for (int i = 0; i < length; i++) {
      // for any double slash comments, ignore until the end of the line
      if ((program.charAt(i) == '/') && (i < length - 1)
          && (program.charAt(i + 1) == '/')) {
        i += 2;
        while ((i < length) && (program.charAt(i) != '\n')) {
          i++;
        }
        // check to see if this is the start of a new multiline comment.
        // if it is, then make sure it's actually terminated somewhere.
      } else if ((program.charAt(i) == '/') && (i < length - 1)
          && (program.charAt(i + 1) == '*')) {
        final int startOfComment = i;
        i += 2;
        boolean terminated = false;
        while (i < length - 1) {
          if ((program.charAt(i) == '*') && (program.charAt(i + 1) == '/')) {
            i += 2;
            terminated = true;
            break;
          } else {
            i++;
          }
        }
        if (!terminated) {
          throw new RunnerException("Unclosed /* comment */", 0,
                                    countNewlines(program.substring(0,
                                      startOfComment)));
        }
      } else if (program.charAt(i) == '"') {
        final int stringStart = i;
        boolean terminated = false;
        for (i++; i < length; i++) {
          final char c = program.charAt(i);
          if (c == '"') {
            terminated = true;
            break;
          } else if (c == '\\') {
            if (i == length - 1) {
              break;
            }
            i++;
          } else if (c == '\n') {
            break;
          }
        }
        if (!terminated) {
          throw new RunnerException("Unterminated string constant", 0,
                                    countNewlines(program.substring(0,
                                      stringStart)));
        }
      } else if (program.charAt(i) == '\'') {
        i++;
        if (i >= length) {
          throw new RunnerException("Unterminated character constant", 0,
                                    countNewlines(program.substring(0, i)));
        }
        if (program.charAt(i) == '\\') {
          i++;
        }
        i++;
        if (i >= length) {
          throw new RunnerException("Unterminated character constant", 0,
                                    countNewlines(program.substring(0, i)));
        }
        if (program.charAt(i) != '\'') {
          throw new RunnerException("Badly formed character constant", 0,
                                    countNewlines(program.substring(0, i)));
        }
      }

    }
  }

  public PreprocessorResult write(final Writer out, String program)
      throws RunnerException, RecognitionException, TokenStreamException {
    return write(out, program, null);
  }

  public PreprocessorResult write(Writer out, String program,
                                String codeFolderPackages[])
      throws RunnerException, RecognitionException, TokenStreamException {

    // these ones have the .* at the end, since a class name might be at the end
    // instead of .* which would make trouble other classes using this can lop
    // off the . and anything after it to produce a package name consistently.
    final ArrayList<String> programImports = new ArrayList<String>();

    // imports just from the code folder, treated differently
    // than the others, since the imports are auto-generated.
    final ArrayList<String> codeFolderImports = new ArrayList<String>();

    // need to reset whether or not this has a main()
    foundMain = false;

    // bug #5
    if (!program.endsWith("\n"))
      program += "\n";

    checkForUnterminatedMultilineComment(program);

    if (Preferences.getBoolean("preproc.substitute_unicode")) {
      program = substituteUnicode(program);
    }

    final String importRegexp = "(?:^|;)\\s*(import\\s+)((?:static\\s+)?\\S+)(\\s*;)";

    do {
      String[] pieces = PApplet.match(program, importRegexp);
      // Stop the loop if we've removed all the importy lines
      if (pieces == null)
        break;

      String piece = pieces[1] + pieces[2] + pieces[3];
      int len = piece.length(); // how much to trim out

      programImports.add(pieces[2]); // the package name

      // find index of this import in the program
      int idx = program.indexOf(piece);

      // Remove the import from the main program
      program = program.substring(0, idx) + program.substring(idx + len);
    } while (true);

    if (codeFolderPackages != null) {
      for (String item : codeFolderPackages) {
        codeFolderImports.add(item + ".*");
      }
    }

    final PrintWriter stream = new PrintWriter(out);
    final int headerOffset = writeImports(stream, programImports,
      codeFolderImports);
    return new PreprocessorResult(mode, headerOffset + 2, write(program, stream),
                                programImports);
  }

  static String substituteUnicode(String program) {
    // check for non-ascii chars (these will be/must be in unicode format)
    char p[] = program.toCharArray();
    int unicodeCount = 0;
    for (int i = 0; i < p.length; i++) {
      if (p[i] > 127)
        unicodeCount++;
    }
    if (unicodeCount == 0)
      return program;
    // if non-ascii chars are in there, convert to unicode escapes
    // add unicodeCount * 5.. replacing each unicode char
    // with six digit uXXXX sequence (xxxx is in hex)
    // (except for nbsp chars which will be a replaced with a space)
    int index = 0;
    char p2[] = new char[p.length + unicodeCount * 5];
    for (int i = 0; i < p.length; i++) {
      if (p[i] < 128) {
        p2[index++] = p[i];
      } else if (p[i] == 160) { // unicode for non-breaking space
        p2[index++] = ' ';
      } else {
        int c = p[i];
        p2[index++] = '\\';
        p2[index++] = 'u';
        char str[] = Integer.toHexString(c).toCharArray();
        // add leading zeros, so that the length is 4
        //for (int i = 0; i < 4 - str.length; i++) p2[index++] = '0';
        for (int m = 0; m < 4 - str.length; m++)
          p2[index++] = '0';
        System.arraycopy(str, 0, p2, index, str.length);
        index += str.length;
      }
    }
    return new String(p2, 0, index);
  }

  private static final Pattern PUBLIC_CLASS = Pattern.compile(
    "(^|;)\\s*public\\s+class", Pattern.MULTILINE);

  private static final Pattern FUNCTION_DECL = Pattern
      .compile(
        "(^|;)\\s*((public|private|protected|final|static)\\s+)*(void|int|float|double|String|char|byte)(\\s*\\[\\s*\\])?\\s+[a-zA-Z0-9]+\\s*\\(",
        Pattern.MULTILINE);

  /**
   * preprocesses a pde file and writes out a java file
   * @return the class name of the exported Java
   */
  private String write(final String program, final PrintWriter stream)
      throws RunnerException, RecognitionException, TokenStreamException {

    PdeRecognizer parser = createParser(program);
    if (PUBLIC_CLASS.matcher(program).find()) {
      try {
        final PrintStream saved = System.err;
        try {
          // throw away stderr for this tentative parse
          System.setErr(new PrintStream(new ByteArrayOutputStream()));
          parser.javaProgram();
        } finally {
          System.setErr(saved);
        }
        setMode(Mode.JAVA);
      } catch (Exception e) {
        // I can't figure out any other way of resetting the parser.
        parser = createParser(program);
        parser.pdeProgram();
      }
    } else if (FUNCTION_DECL.matcher(program).find()) {
      setMode(Mode.ACTIVE);
      parser.activeProgram();
    } else {
      parser.pdeProgram();
    }

    // set up the AST for traversal by PdeEmitter
    //
    ASTFactory factory = new ASTFactory();
    AST parserAST = parser.getAST();
    AST rootNode = factory.create(ROOT_ID, "AST ROOT");
    rootNode.setFirstChild(parserAST);

    makeSimpleMethodsPublic(rootNode);

    // unclear if this actually works, but it's worth a shot
    //
    //((CommonAST)parserAST).setVerboseStringConversion(
    //  true, parser.getTokenNames());
    // (made to use the static version because of jikes 1.22 warning)
    CommonAST.setVerboseStringConversion(true, parser.getTokenNames());

    final String className;
    if (mode == Mode.JAVA) {
      // if this is an advanced program, the classname is already defined.
      className = getFirstClassName(parserAST);
    } else {
      className = this.name;
    }

    // if 'null' was passed in for the name, but this isn't
    // a 'java' mode class, then there's a problem, so punt.
    //
    if (className == null)
      return null;

    // debug
    if (false) {
      final StringWriter buf = new StringWriter();
      final PrintWriter bufout = new PrintWriter(buf);
      writeDeclaration(bufout, className);
      new PdeEmitter(this, bufout).print(rootNode);
      writeFooter(bufout, className);
      debugAST(rootNode, true);
      System.err.println(buf.toString());
    }

    writeDeclaration(stream, className);
    new PdeEmitter(this, stream).print(rootNode);
    writeFooter(stream, className);

    // if desired, serialize the parse tree to an XML file.  can
    // be viewed usefully with Mozilla or IE
    if (Preferences.getBoolean("preproc.output_parse_tree")) {
      writeParseTree("parseTree.xml", parserAST);
    }

    return className;
  }

  private PdeRecognizer createParser(final String program) {
    // create a lexer with the stream reader, and tell it to handle
    // hidden tokens (eg whitespace, comments) since we want to pass these
    // through so that the line numbers when the compiler reports errors
    // match those that will be highlighted in the PDE IDE
    //
    PdeLexer lexer = new PdeLexer(new StringReader(program));
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
    filter.copy(PdeRecognizer.TRIPLE_DOT);

    // Because the meanings of < and > are overloaded to support
    // type arguments and type parameters, we have to treat them
    // as copyable to hidden text (or else the following syntax,
    // such as (); and what not gets lost under certain circumstances)
    // -- jdf
    filter.copy(PdeRecognizer.LT);
    filter.copy(PdeRecognizer.GT);
    filter.copy(PdeRecognizer.SR);
    filter.copy(PdeRecognizer.BSR);

    // create a parser and set what sort of AST should be generated
    //
    final PdeRecognizer parser = new PdeRecognizer(this, filter);

    // use our extended AST class
    //
    parser.setASTNodeClass("antlr.ExtendedCommonASTWithHiddenTokens");
    return parser;
  }

  /**
   * Walk the tree looking for METHOD_DEFs. Any simple METHOD_DEF (one
   * without TYPE_PARAMETERS) lacking an
   * access specifier is given public access.
   * @param node
   */
  private void makeSimpleMethodsPublic(final AST node) {
    if (node.getType() == PdeTokenTypes.METHOD_DEF) {
      final AST mods = node.getFirstChild();
      final AST oldFirstMod = mods.getFirstChild();
      for (AST mod = oldFirstMod; mod != null; mod = mod.getNextSibling()) {
        final int t = mod.getType();
        if (t == PdeTokenTypes.LITERAL_private || 
            t == PdeTokenTypes.LITERAL_protected || 
            t == PdeTokenTypes.LITERAL_public) {
          return;
        }
      }
      if (mods.getNextSibling().getType() == PdeTokenTypes.TYPE_PARAMETERS) {
        return;
      }
      final CommonHiddenStreamToken publicToken = 
        new CommonHiddenStreamToken(PdeTokenTypes.LITERAL_public, "public") {
        {
          setHiddenAfter(new CommonHiddenStreamToken(PdeTokenTypes.WS, " "));
        }
      };
      final AST publicNode = new CommonASTWithHiddenTokens(publicToken);
      publicNode.setNextSibling(oldFirstMod);
      mods.setFirstChild(publicNode);
    } else {
      for (AST kid = node.getFirstChild(); kid != null; kid = kid
          .getNextSibling())
        makeSimpleMethodsPublic(kid);
    }
  }

  protected void writeParseTree(String filename, AST ast) {
    try {
      PrintStream stream = new PrintStream(new FileOutputStream(filename));
      stream.println("<?xml version=\"1.0\"?>");
      stream.println("<document>");
      OutputStreamWriter writer = new OutputStreamWriter(stream);
      if (ast != null) {
        ((CommonAST) ast).xmlSerialize(writer);
      }
      writer.flush();
      stream.println("</document>");
      writer.close();
    } catch (IOException e) {

    }
  }

  /**
   * 
   * @param out
   * @param programImports
   * @param codeFolderImports
   * @return the header offset
   */
  protected int writeImports(final PrintWriter out,
                             final List<String> programImports,
                             final List<String> codeFolderImports) {
    int count = writeImportList(out, getCoreImports());
    count += writeImportList(out, programImports);
    count += writeImportList(out, codeFolderImports);
    count += writeImportList(out, getDefaultImports());
    return count;
  }

  protected int writeImportList(PrintWriter out, List<String> imports) {
    return writeImportList(out, (String[]) imports.toArray(new String[0]));
  }

  protected int writeImportList(PrintWriter out, String[] imports) {
    int count = 0;
    if (imports != null && imports.length != 0) {
      for (String item : imports) {
        out.println("import " + item + "; ");
        count++;
      }
      out.println();
      count++;
    }
    return count;
  }

  /**
   * Write any required header material (eg imports, class decl stuff)
   *
   * @param out                 PrintStream to write it to.
   * @param exporting           Is this being exported from PDE?
   * @param className           Name of the class being created.
   */
  protected void writeDeclaration(PrintWriter out, String className) {
    if (mode == Mode.JAVA) {
      // Print two blank lines so that the offset doesn't change
      out.println();
      out.println();

    } else if (mode == Mode.ACTIVE) {
      // Print an extra blank line so the offset is identical to the others
      out.println("public class " + className + " extends PApplet {");
      out.println();

    } else if (mode == Mode.STATIC) {
      out.println("public class " + className + " extends PApplet {");
      out.println(indent + "public void setup() {");
    }
  }

  /**
   * Write any necessary closing text.
   *
   * @param out PrintStream to write it to.
   */
  protected void writeFooter(PrintWriter out, String className) {
    if (mode == Mode.STATIC) {
      // close off draw() definition
      out.println(indent + "noLoop();");
      out.println("} ");
    }

    if ((mode == Mode.STATIC) || (mode == Mode.ACTIVE)) {
      if (!foundMain) {
        out.println(indent + "static public void main(String args[]) {");
        out.print(indent + indent + "PApplet.main(new String[] { ");

        if (Preferences.getBoolean("export.application.fullscreen")) {
          out.print("\"" + PApplet.ARGS_PRESENT + "\", ");

          String farbe = Preferences.get("run.present.bgcolor");
          out.print("\"" + PApplet.ARGS_BGCOLOR + "=" + farbe + "\", ");

          if (Preferences.getBoolean("export.application.stop")) {
            farbe = Preferences.get("run.present.stop.color");
            out.print("\"" + PApplet.ARGS_STOP_COLOR + "=" + farbe + "\", ");
          } else {
            out.print("\"" + PApplet.ARGS_HIDE_STOP + "\", ");
          }
        } else {
          String farbe = Preferences.get("run.window.bgcolor");
          out.print("\"" + PApplet.ARGS_BGCOLOR + "=" + farbe + "\", ");
        }
        out.println("\"" + className + "\" });");
        out.println(indent + "}");
      }

      // close off the class definition
      out.println("}");
    }
  }

  public String[] getCoreImports() {
    return new String[] { "processing.core.*", "processing.xml.*" };
  }

  public String[] getDefaultImports() {
    // These may change in-between (if the prefs panel adds this option)
    String prefsLine = Preferences.get("preproc.imports.list");
    return PApplet.splitTokens(prefsLine, ", ");
  }

  /**
   * Find the first CLASS_DEF node in the tree, and return the name of the
   * class in question.
   *
   * TODO [dmose] right now, we're using a little hack to the grammar to get
   * this info.  In fact, we should be descending the AST passed in.
   */
  String getFirstClassName(AST ast) {

    String t = advClassName;
    advClassName = "";

    return t;
  }

  public void debugAST(final AST ast, final boolean includeHidden) {
    System.err.println("------------------");
    debugAST(ast, includeHidden, 0);
  }

  private void debugAST(final AST ast, final boolean includeHidden,
                        final int indent) {
    for (int i = 0; i < indent; i++)
      System.err.print("    ");
    if (includeHidden) {
      System.err.print(debugHiddenBefore(ast));
    }
    if (ast.getType() > 0 && !ast.getText().equals(TokenUtil.nameOf(ast))) {
      System.err.print(TokenUtil.nameOf(ast) + "/");
    }
    System.err.print(ast.getText().replace("\n", "\\n"));
    if (includeHidden) {
      System.err.print(debugHiddenAfter(ast));
    }
    System.err.println();
    for (AST kid = ast.getFirstChild(); kid != null; kid = kid.getNextSibling())
      debugAST(kid, includeHidden, indent + 1);
  }

  private String debugHiddenAfter(AST ast) {
    if (!(ast instanceof antlr.CommonASTWithHiddenTokens))
      return "";
    return debugHiddenTokens(((antlr.CommonASTWithHiddenTokens) ast)
        .getHiddenAfter());
  }

  private String debugHiddenBefore(AST ast) {
    if (!(ast instanceof antlr.CommonASTWithHiddenTokens))
      return "";
    antlr.CommonHiddenStreamToken child = null, parent = ((antlr.CommonASTWithHiddenTokens) ast)
        .getHiddenBefore();

    if (parent == null) {
      return "";
    }

    do {
      child = parent;
      parent = child.getHiddenBefore();
    } while (parent != null);

    return debugHiddenTokens(child);
  }

  private String debugHiddenTokens(antlr.CommonHiddenStreamToken t) {
    final StringBuilder sb = new StringBuilder();
    for (; t != null; t = filter.getHiddenAfter(t)) {
      if (sb.length() == 0)
        sb.append("[");
      sb.append(t.getText().replace("\n", "\\n"));
    }
    if (sb.length() > 0)
      sb.append("]");
    return sb.toString();
  }

}
