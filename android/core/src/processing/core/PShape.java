/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2006-10 Ben Fry and Casey Reas

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License version 2.1 as published by the Free Software Foundation.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

package processing.core;

import java.util.HashMap;


/**
 * In-progress class to handle shape data, currently to be considered of
 * alpha or beta quality. Major structural work may be performed on this class
 * after the release of Processing 1.0. Such changes may include:
 *
 * <ul>
 * <li> addition of proper accessors to read shape vertex and coloring data
 * (this is the second most important part of having a PShape class after all).
 * <li> a means of creating PShape objects ala beginShape() and endShape().
 * <li> load(), update(), and cache methods ala PImage, so that shapes can
 * have renderer-specific optimizations, such as vertex arrays in OpenGL.
 * <li> splitting this class into multiple classes to handle different
 * varieties of shape data (primitives vs collections of vertices vs paths)
 * <li> change of package declaration, for instance moving the code into
 * package processing.shape (if the code grows too much).
 * </ul>
 *
 * <p>For the time being, this class and its shape() and loadShape() friends in
 * PApplet exist as placeholders for more exciting things to come. If you'd
 * like to work with this class, make a subclass (see how PShapeSVG works)
 * and you can play with its internal methods all you like.</p>
 *
 * <p>Library developers are encouraged to create PShape objects when loading
 * shape data, so that they can eventually hook into the bounty that will be
 * the PShape interface, and the ease of loadShape() and shape().</p>
 */
public class PShape implements PConstants {

  protected String name;
  protected HashMap<String,PShape> nameTable;

  /** Generic, only draws its child objects. */
  static public final int GROUP = 0;
  /** A line, ellipse, arc, image, etc. */
  static public final int PRIMITIVE = 1;
  /** A series of vertex, curveVertex, and bezierVertex calls. */
  static public final int PATH = 2;
  /** Collections of vertices created with beginShape(). */
  static public final int GEOMETRY = 3;
  /** The shape type, one of GROUP, PRIMITIVE, PATH, or GEOMETRY. */
  protected int family;

  /** ELLIPSE, LINE, QUAD; TRIANGLE_FAN, QUAD_STRIP; etc. */
  protected int kind;

  protected PMatrix matrix;

  /** Texture or image data associated with this shape. */
  protected PImage image;

  // boundary box of this shape
  //protected float x;
  //protected float y;
  //protected float width;
  //protected float height;
  /**
   * The width of the PShape document.
   * @webref
   * @brief     Shape document width
   */
  public float width;
  /**
   * The width of the PShape document.
   * @webref
   * @brief     Shape document height
   */
  public float height;
  public float depth;

  // set to false if the object is hidden in the layers palette
  protected boolean visible = true;

  protected boolean stroke;
  protected int strokeColor;
  protected float strokeWeight; // default is 1
  protected int strokeCap;
  protected int strokeJoin;

  protected boolean fill;
  protected int fillColor;

  protected boolean tint;
  protected int tintColor;

  protected int ambientColor;  
  protected int specularColor;  
  protected int emissiveColor;
  protected float shininess;  
  
  /** Temporary toggle for whether styles should be honored. */
  protected boolean style = true;

  /** For primitive shapes in particular, parms like x/y/w/h or x1/y1/x2/y2. */
  protected float[] params;

  protected int vertexCount;
  /**
   * When drawing POLYGON shapes, the second param is an array of length
   * VERTEX_FIELD_COUNT. When drawing PATH shapes, the second param has only
   * two variables.
   */
  protected float[][] vertices;

  static public final int VERTEX = 0;
  static public final int BEZIER_VERTEX = 1;
  static public final int QUAD_BEZIER_VERTEX = 2;
  static public final int CURVE_VERTEX = 3;
  static public final int BREAK = 4;

  // should this be called vertices (consistent with PGraphics internals)
  // or does that hurt flexibility?

  protected PShape parent;
  protected int childCount;
  protected PShape[] children;
  

  /** Array of VERTEX, BEZIER_VERTEX, and CURVE_VERTEX calls. */
  protected int vertexCodeCount;
  protected int[] vertexCodes;
  /** True if this is a closed path. */
  protected boolean close;

  
  // ........................................................

  // internal color for setting/calculating
  protected float calcR, calcG, calcB, calcA;
  protected int calcRi, calcGi, calcBi, calcAi;
  protected int calcColor;
  protected boolean calcAlpha;  

  /** The current colorMode */
  public int colorMode; // = RGB;

  /** Max value for red (or hue) set by colorMode */
  public float colorModeX; // = 255;

  /** Max value for green (or saturation) set by colorMode */
  public float colorModeY; // = 255;

  /** Max value for blue (or value) set by colorMode */
  public float colorModeZ; // = 255;

  /** Max value for alpha set by colorMode */
  public float colorModeA; // = 255;

  /** True if colors are not in the range 0..1 */
  boolean colorModeScale; // = true;

  /** True if colorMode(RGB, 255) */
  boolean colorModeDefault; // = true;
  
  // POINTS, LINES, xLINE_STRIP, xLINE_LOOP
  // TRIANGLES, TRIANGLE_STRIP, TRIANGLE_FAN
  // QUADS, QUAD_STRIP
  // xPOLYGON
//  static final int PATH = 1;  // POLYGON, LINE_LOOP, LINE_STRIP
//  static final int GROUP = 2;

  // how to handle rectmode/ellipsemode?
  // are they bitshifted into the constant?
  // CORNER, CORNERS, CENTER, (CENTER_RADIUS?)
//  static final int RECT = 3; // could just be QUAD, but would be x1/y1/x2/y2
//  static final int ELLIPSE = 4;
//
//  static final int VERTEX = 7;
//  static final int CURVE = 5;
//  static final int BEZIER = 6;


  // fill and stroke functions will need a pointer to the parent
  // PGraphics object.. may need some kind of createShape() fxn
  // or maybe the values are stored until draw() is called?

  // attaching images is very tricky.. it's a different type of data

  // material parameters will be thrown out,
  // except those currently supported (kinds of lights)

  // pivot point for transformations
//  public float px;
//  public float py;


  public PShape() {
    this.family = GROUP;
  }


  public PShape(int family) {
    this.family = family;
  }

  
  public void setKind(int kind) {
    this.kind = kind;
  }
  
  
  public void setName(String name) {
    this.name = name;
  }


  public String getName() {
    return name;
  }

  /**
   * Returns a boolean value "true" if the image is set to be visible, "false" if not. This is modified with the <b>setVisible()</b> parameter.
   * <br><br>The visibility of a shape is usually controlled by whatever program created the SVG file.
   * For instance, this parameter is controlled by showing or hiding the shape in the layers palette in Adobe Illustrator.
   *
   * @webref
   * @brief Returns a boolean value "true" if the image is set to be visible, "false" if not
   */
  public boolean isVisible() {
    return visible;
  }

  /**
   * Sets the shape to be visible or invisible. This is determined by the value of the <b>visible</b> parameter.
   * <br><br>The visibility of a shape is usually controlled by whatever program created the SVG file.
   * For instance, this parameter is controlled by showing or hiding the shape in the layers palette in Adobe Illustrator.
   * @param visible "false" makes the shape invisible and "true" makes it visible
   * @webref
   * @brief Sets the shape to be visible or invisible
   */
  public void setVisible(boolean visible) {
    this.visible = visible;
  }


  /**
   * Disables the shape's style data and uses Processing's current styles. Styles include attributes such as colors, stroke weight, and stroke joints.
   *  =advanced
   * Overrides this shape's style information and uses PGraphics styles and
   * colors. Identical to ignoreStyles(true). Also disables styles for all
   * child shapes.
   * @webref
   * @brief     Disables the shape's style data and uses Processing styles
   */
  public void disableStyle() {
    style = false;

    for (int i = 0; i < childCount; i++) {
      children[i].disableStyle();
    }
  }


  /**
   * Enables the shape's style data and ignores Processing's current styles. Styles include attributes such as colors, stroke weight, and stroke joints.
   * @webref
   * @brief Enables the shape's style data and ignores the Processing styles
   */
  public void enableStyle() {
    style = true;

    for (int i = 0; i < childCount; i++) {
      children[i].enableStyle();
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


//  protected void checkBounds() {
//    if (width == 0 || height == 0) {
//      // calculate bounds here (also take kids into account)
//      width = 1;
//      height = 1;
//    }
//  }


  /**
   * Get the width of the drawing area (not necessarily the shape boundary).
   */
  public float getWidth() {
    //checkBounds();
    return width;
  }


  /**
   * Get the height of the drawing area (not necessarily the shape boundary).
   */
  public float getHeight() {
    //checkBounds();
    return height;
  }


  /**
   * Get the depth of the shape area (not necessarily the shape boundary). Only makes sense for 3D PShape subclasses,
   * such as PShape3D.
   */
  public float getDepth() {
    //checkBounds();
    return depth;
  }

  
  public PVector getCenter() {
    return new PVector();
  }  
  

  /**
   * Return true if this shape is 3D. Defaults to false.
   */
  public boolean is3D() {
    return false;
  }

  ///////////////////////////////////////////////////////////  
  
  //
  
  // Drawing methods  
  
  public void texture(PImage tex) {
  }
  
  public void noTexture() {
  }  
    
  public void solid(boolean solid) {
  }
  
  public void beginContour() {
  }
    
  public void endContour() {
  }
  
  public void vertex(float x, float y) { 
  }

  public void vertex(float x, float y, float u, float v) { 
  }
  
  public void vertex(float x, float y, float z) {
  }

  public void vertex(float x, float y, float z, float u, float v) {
  }  
  
  public void normal(float nx, float ny, float nz) {
  }

  public void end() {
  }  

  public void end(int mode) {    
  }    
  
  //////////////////////////////////////////////////////////////

  // Stroke cap/join/weight set/update

  public void strokeWeight(float weight) {
  }

  public void strokeJoin(int join) {
  }

  public void strokeCap(int cap) {
  }

  //////////////////////////////////////////////////////////////

  // Fill set/update

  public void noFill() {
  }

  public void fill(int rgb) {
  }

  public void fill(int rgb, float alpha) {
  }

  public void fill(float gray) {
  }

  public void fill(float gray, float alpha) {
  }

  public void fill(float x, float y, float z) {
  }

  public void fill(float x, float y, float z, float a) {
  }

  //////////////////////////////////////////////////////////////

  // Stroke (color) set/update 

  public void noStroke() {
  }

  public void stroke(int rgb) {
  }

  public void stroke(int rgb, float alpha) {
  }

  public void stroke(float gray) {
  }

  public void stroke(float gray, float alpha) {
  }

  public void stroke(float x, float y, float z) {
  }

  public void stroke(float x, float y, float z, float alpha) {
  }

  //////////////////////////////////////////////////////////////

  // Tint set/update 


  public void noTint() {
  }

  public void tint(int rgb) {
  }

  public void tint(int rgb, float alpha) {
  }

  public void tint(float gray) {
  }

  public void tint(float gray, float alpha) {
  }

  public void tint(float x, float y, float z) {
  }

  public void tint(float x, float y, float z, float alpha) {
  }

  //////////////////////////////////////////////////////////////

  // Ambient set/update
  
  public void ambient(int rgb) {    
  }
  
  public void ambient(float gray) {    
  }
  
  public void ambient(float x, float y, float z) {
  }
  
  //////////////////////////////////////////////////////////////

  // Specular set/update
  
  public void specular(int rgb) {    
  }
  
  public void specular(float gray) {    
  }
  
  public void specular(float x, float y, float z) {
  }  
  
  
  //////////////////////////////////////////////////////////////

  // Emissive set/update  

  public void emissive(int rgb) {    
  }
  
  public void emissive(float gray) {    
  }
  
  public void emissive(float x, float y, float z) {
  }    
  
  //////////////////////////////////////////////////////////////

  // Shininess set/update  
  
  public void shininess(float shine) {    
  }  
  
  ///////////////////////////////////////////////////////////  
  
  //
  
  // Bezier curves   
  
  
  public void bezierDetail(int detail) {
  }  
  
  public void bezierVertex(float x2, float y2,
                           float x3, float y3,
                           float x4, float y4) {
  }
  
  public void bezierVertex(float x2, float y2, float z2,
                           float x3, float y3, float z3,
                           float x4, float y4, float z4) {
  }
  
  public void quadraticVertex(float cx, float cy,
                              float x3, float y3) {
  }  
  
  public void quadraticVertex(float cx, float cy, float cz,
                              float x3, float y3, float z3) {
  }
  
  ///////////////////////////////////////////////////////////  
  
  //
  
  // Catmull-Rom curves

  public void curveDetail(int detail) {
  }
  
  public void curveTightness(float tightness) {
  }  
  
  public void curveVertex(float x, float y) {
  }  

  public void curveVertex(float x, float y, float z) {
  }
  
  
  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  /*
  boolean strokeSaved;
  int strokeColorSaved;
  float strokeWeightSaved;
  int strokeCapSaved;
  int strokeJoinSaved;

  boolean fillSaved;
  int fillColorSaved;

  int rectModeSaved;
  int ellipseModeSaved;
  int shapeModeSaved;
  */


  protected void pre(PGraphics g) {
    if (matrix != null) {
      g.pushMatrix();
      g.applyMatrix(matrix);
    }

    /*
    strokeSaved = g.stroke;
    strokeColorSaved = g.strokeColor;
    strokeWeightSaved = g.strokeWeight;
    strokeCapSaved = g.strokeCap;
    strokeJoinSaved = g.strokeJoin;

    fillSaved = g.fill;
    fillColorSaved = g.fillColor;

    rectModeSaved = g.rectMode;
    ellipseModeSaved = g.ellipseMode;
    shapeModeSaved = g.shapeMode;
    */
    if (style) {
      g.pushStyle();
      styles(g);
    }
  }


  protected void styles(PGraphics g) {
    // should not be necessary because using only the int version of color
    //parent.colorMode(PConstants.RGB, 255);

    if (stroke) {
      g.stroke(strokeColor);
      g.strokeWeight(strokeWeight);
      g.strokeCap(strokeCap);
      g.strokeJoin(strokeJoin);
    } else {
      g.noStroke();
    }

    if (fill) {
      //System.out.println("filling " + PApplet.hex(fillColor));
      g.fill(fillColor);
    } else {
      g.noFill();
    }
  }


  public void post(PGraphics g) {
//    for (int i = 0; i < childCount; i++) {
//      children[i].draw(g);
//    }

    /*
    // TODO this is not sufficient, since not saving fillR et al.
    g.stroke = strokeSaved;
    g.strokeColor = strokeColorSaved;
    g.strokeWeight = strokeWeightSaved;
    g.strokeCap = strokeCapSaved;
    g.strokeJoin = strokeJoinSaved;

    g.fill = fillSaved;
    g.fillColor = fillColorSaved;

    g.ellipseMode = ellipseModeSaved;
    */

    if (matrix != null) {
      g.popMatrix();
    }

    if (style) {
      g.popStyle();
    }
  }

  
  
  ////////////////////////////////////////////////////////////////////////
  //
  // The new copy methods to put an SVG into a PShape3D, for example
  
  public PShape copy(PGraphics g) {
    PShape res = null;
    if (family == GROUP) {
      res = g.createShape(GROUP);
      copyGroup(g, res);      
    } else if (family == PRIMITIVE) {
      res = g.createShape(kind, params);
      copyPrimitive(res);
    } else if (family == GEOMETRY) {
      res = g.createShape(kind);
      copyGeometry(res);
    } else if (family == PATH) {
      res = g.createShape(PATH);
      copyPath(res);
    }
    return res;
  }

  
  protected void copyGroup(PGraphics g, PShape s) {
    if (matrix != null) {
      s.applyMatrix(matrix);  
    }
    copyStyles(s);
    copyImage(s);
    for (int i = 0; i < childCount; i++) {
      PShape c = children[i].copy(g);
      s.addChild(c);
    }
  }
  
  
  protected void copyPrimitive(PShape s) {
    if (matrix != null) {
      s.applyMatrix(matrix);  
    }
    copyStyles(s);
    copyImage(s);
  }
  
  protected void copyGeometry(PShape s) {
    if (matrix != null) {
      s.applyMatrix(matrix);  
    }
    copyStyles(s);
    copyImage(s);
    
    if (style) {
      for (int i = 0; i < vertexCount; i++) {
        float[] vert = vertices[i];
//        s.ambient(vert[AR] * 255, vert[AG] * 255, vert[AB] * 255);
//        s.specular(vert[SPR] * 255, vert[SPG] * 255, vert[SPB] * 255);
//        s.emissive(vert[ER] * 255, vert[EG] * 255, vert[EB] * 255);
//        s.shininess(vert[SHINE]);
        
        s.normal(vert[NX], vert[NY], vert[NZ]);
        s.vertex(vert[X], vert[Y], vert[Z], vert[U], vert[V]);        
      }
    } else {
      for (int i = 0; i < vertexCount; i++) {
        float[] vert = vertices[i];
        if (vert[PGraphics.Z] == 0) {
          s.vertex(vert[X], vert[Y]);
        } else {
          s.vertex(vert[X], vert[Y], vert[Z]);
        }
      }
    }
    
    s.end();  
  }
  
  protected void copyPath(PShape s) {
    if (matrix != null) {
      s.applyMatrix(matrix);  
    }
    copyStyles(s);
    copyImage(s);
    s.close = close;
    s.setPath(vertexCount, vertices, vertexCodeCount, vertexCodes);
    
  }
  
  protected void copyStyles(PShape s) {
    if (stroke) {
      s.stroke = true;
      s.strokeColor = strokeColor;
      s.strokeWeight = strokeWeight;
      s.strokeCap = strokeCap;
      s.strokeJoin = strokeJoin;
    } else {
      s.stroke = false;
    }

    if (fill) {
      s.fill = true;
      s.fillColor = fillColor;
    } else {
      s.fill = false;
    }
  } 
  
  protected void copyImage(PShape s) {
    if (image != null) {
      s.texture(image);
    }    
  }
  
  ////////////////////////////////////////////////////////////////////////
  

  /**
   * Called by the following (the shape() command adds the g)
   * PShape s = loadShapes("blah.svg");
   * shape(s);
   */
  public void draw(PGraphics g) {
    if (visible) {
      pre(g);
      drawImpl(g);
      post(g);
    }
  }


  /**
   * Draws the SVG document.
   */
  public void drawImpl(PGraphics g) {
    //System.out.println("drawing " + family);
    if (family == GROUP) {
      drawGroup(g);
    } else if (family == PRIMITIVE) {
      drawPrimitive(g);
    } else if (family == GEOMETRY) {
      drawGeometry(g);
    } else if (family == PATH) {
      drawPath(g);
    }
  }


  protected void drawGroup(PGraphics g) {
    for (int i = 0; i < childCount; i++) {
      children[i].draw(g);
    }
  }


  protected void drawPrimitive(PGraphics g) {
    if (kind == POINT) {
      g.point(params[0], params[1]);

    } else if (kind == LINE) {
      if (params.length == 4) {  // 2D
        g.line(params[0], params[1],
               params[2], params[3]);
      } else {  // 3D
        g.line(params[0], params[1], params[2],
               params[3], params[4], params[5]);
      }

    } else if (kind == TRIANGLE) {
      g.triangle(params[0], params[1],
                 params[2], params[3],
                 params[4], params[5]);

    } else if (kind == QUAD) {
      g.quad(params[0], params[1],
             params[2], params[3],
             params[4], params[5],
             params[6], params[7]);

    } else if (kind == RECT) {
      if (image != null) {
        g.imageMode(CORNER);
        g.image(image, params[0], params[1], params[2], params[3]);
      } else {
        g.rectMode(CORNER);
        g.rect(params[0], params[1], params[2], params[3]);
      }

    } else if (kind == ELLIPSE) {
      g.ellipseMode(CORNER);
      g.ellipse(params[0], params[1], params[2], params[3]);

    } else if (kind == ARC) {
      g.ellipseMode(CORNER);
      g.arc(params[0], params[1], params[2], params[3], params[4], params[5]);

    } else if (kind == BOX) {
      if (params.length == 1) {
        g.box(params[0]);
      } else {
        g.box(params[0], params[1], params[2]);
      }

    } else if (kind == SPHERE) {
      g.sphere(params[0]);
    }
  }


  protected void drawGeometry(PGraphics g) {
    g.beginShape(kind);
    if (style) {
      for (int i = 0; i < vertexCount; i++) {
        g.vertex(vertices[i]);
      }
    } else {
      for (int i = 0; i < vertexCount; i++) {
        float[] vert = vertices[i];
        if (vert[PGraphics.Z] == 0) {
          g.vertex(vert[X], vert[Y]);
        } else {
          g.vertex(vert[X], vert[Y], vert[Z]);
        }
      }
    }
    g.endShape();
  }


  /*
  protected void drawPath(PGraphics g) {
    g.beginShape();
    for (int j = 0; j < childCount; j++) {
      if (j > 0) g.breakShape();
      int count = children[j].vertexCount;
      float[][] vert = children[j].vertices;
      int[] code = children[j].vertexCodes;

      for (int i = 0; i < count; i++) {
        if (style) {
          if (children[j].fill) {
            g.fill(vert[i][R], vert[i][G], vert[i][B]);
          } else {
            g.noFill();
          }
          if (children[j].stroke) {
            g.stroke(vert[i][R], vert[i][G], vert[i][B]);
          } else {
            g.noStroke();
          }
        }
        g.edge(vert[i][EDGE] == 1);

        if (code[i] == VERTEX) {
          g.vertex(vert[i]);

        } else if (code[i] == BEZIER_VERTEX) {
          float z0 = vert[i+0][Z];
          float z1 = vert[i+1][Z];
          float z2 = vert[i+2][Z];
          if (z0 == 0 && z1 == 0 && z2 == 0) {
            g.bezierVertex(vert[i+0][X], vert[i+0][Y], z0,
                           vert[i+1][X], vert[i+1][Y], z1,
                           vert[i+2][X], vert[i+2][Y], z2);
          } else {
            g.bezierVertex(vert[i+0][X], vert[i+0][Y],
                           vert[i+1][X], vert[i+1][Y],
                           vert[i+2][X], vert[i+2][Y]);
          }
        } else if (code[i] == CURVE_VERTEX) {
          float z = vert[i][Z];
          if (z == 0) {
            g.curveVertex(vert[i][X], vert[i][Y]);
          } else {
            g.curveVertex(vert[i][X], vert[i][Y], z);
          }
        }
      }
    }
    g.endShape();
  }
  */


  protected void drawPath(PGraphics g) {
    // Paths might be empty (go figure)
    // http://dev.processing.org/bugs/show_bug.cgi?id=982
    if (vertices == null) return;

    g.beginShape();

    if (vertexCodeCount == 0) {  // each point is a simple vertex
      if (vertices[0].length == 2) {  // drawing 2D vertices
        for (int i = 0; i < vertexCount; i++) {
          g.vertex(vertices[i][X], vertices[i][Y]);
        }
      } else {  // drawing 3D vertices
        for (int i = 0; i < vertexCount; i++) {
          g.vertex(vertices[i][X], vertices[i][Y], vertices[i][Z]);
        }
      }

    } else {  // coded set of vertices
      int index = 0;

      if (vertices[0].length == 2) {  // drawing a 2D path
        for (int j = 0; j < vertexCodeCount; j++) {
          switch (vertexCodes[j]) {

          case VERTEX:
            g.vertex(vertices[index][X], vertices[index][Y]);
//            cx = vertices[index][X];
//            cy = vertices[index][Y];
            index++;
            break;

          case QUAD_BEZIER_VERTEX:
            g.quadraticVertex(vertices[index+0][X], vertices[index+0][Y],
                         vertices[index+1][X], vertices[index+1][Y]);
//            float x1 = vertices[index+0][X];
//            float y1 = vertices[index+0][Y];
//            float x2 = vertices[index+1][X];
//            float y2 = vertices[index+1][Y];
//            g.bezierVertex(x1 + ((cx-x1)*2/3.0f), y1 + ((cy-y1)*2/3.0f),
//                           x2 + ((cx-x2)*2/3.0f), y2 + ((cy-y2)*2/3.0f),
//                           x2, y2);
//            cx = vertices[index+1][X];
//            cy = vertices[index+1][Y];
            index += 2;
            break;

          case BEZIER_VERTEX:
            g.bezierVertex(vertices[index+0][X], vertices[index+0][Y],
                           vertices[index+1][X], vertices[index+1][Y],
                           vertices[index+2][X], vertices[index+2][Y]);
//            cx = vertices[index+2][X];
//            cy = vertices[index+2][Y];
            index += 3;
            break;

          case CURVE_VERTEX:
            g.curveVertex(vertices[index][X], vertices[index][Y]);
            index++;

          case BREAK:
            g.breakShape();
          }
        }
      } else {  // drawing a 3D path
        for (int j = 0; j < vertexCodeCount; j++) {
          switch (vertexCodes[j]) {

          case VERTEX:
            g.vertex(vertices[index][X], vertices[index][Y], vertices[index][Z]);
//            cx = vertices[index][X];
//            cy = vertices[index][Y];
//            cz = vertices[index][Z];
            index++;
            break;

          case QUAD_BEZIER_VERTEX:
            g.quadraticVertex(vertices[index+0][X], vertices[index+0][Y], vertices[index+0][Z],
                         vertices[index+1][X], vertices[index+1][Y], vertices[index+0][Z]);
            index += 2;
            break;


          case BEZIER_VERTEX:
            g.bezierVertex(vertices[index+0][X], vertices[index+0][Y], vertices[index+0][Z],
                           vertices[index+1][X], vertices[index+1][Y], vertices[index+1][Z],
                           vertices[index+2][X], vertices[index+2][Y], vertices[index+2][Z]);
            index += 3;
            break;

          case CURVE_VERTEX:
            g.curveVertex(vertices[index][X], vertices[index][Y], vertices[index][Z]);
            index++;

          case BREAK:
            g.breakShape();
          }
        }
      }
    }
    g.endShape(close ? CLOSE : OPEN);
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  public PShape getParent() {
    return parent;
  }


  public int getChildCount() {
    return childCount;
  }


  public PShape[] getChildren() {
    return children;
  }


  /**
   *
   * @param index the layer position of the shape to get
   */
  public PShape getChild(int index) {
    return children[index];
  }


  /**
   * Extracts a child shape from a parent shape. Specify the name of the shape with the <b>target</b> parameter.
   * The shape is returned as a <b>PShape</b> object, or <b>null</b> is returned if there is an error.
   * @param target the name of the shape to get
   * @webref
   * @brief Returns a child element of a shape as a PShape object
   */
  public PShape getChild(String target) {
    if (name != null && name.equals(target)) {
      return this;
    }
    if (nameTable != null) {
      PShape found = nameTable.get(target);
      if (found != null) return found;
    }
    for (int i = 0; i < childCount; i++) {
      PShape found = children[i].getChild(target);
      if (found != null) return found;
    }
    return null;
  }


  /**
   * Same as getChild(name), except that it first walks all the way up the
   * hierarchy to the eldest grandparent, so that children can be found anywhere.
   */
  public PShape findChild(String target) {
    if (parent == null) {
      return getChild(target);

    } else {
      return parent.findChild(target);
    }
  }


  // can't be just 'add' because that suggests additive geometry
  public void addChild(PShape who) {
    if (children == null) {
      children = new PShape[1];
    }
    if (childCount == children.length) {
      children = (PShape[]) PApplet.expand(children);
    }
    children[childCount++] = who;
    who.parent = this;

    if (who.getName() != null) {
      addName(who.getName(), who);
    }
  }


  // adds child who exactly at position idx in the array of children.
  public void addChild(PShape who, int idx) {
    if (idx < childCount) {
      if (childCount == children.length) {
        children = (PShape[]) PApplet.expand(children);
      }

      // Copy [idx, childCount - 1] to [idx + 1, childCount]
      for (int i = childCount - 1; i >= idx; i--) {
        children[i + 1] = children[i];
      }
      childCount++;

      children[idx] = who;

      who.parent = this;

      if (who.getName() != null) {
        addName(who.getName(), who);
      }
    }
  }


  /**
   * Remove the child shape with index idx.
   */
  public void removeChild(int idx) {
    if (idx < childCount) {
      PShape child = children[idx];

      // Copy [idx + 1, childCount - 1] to [idx, childCount - 2]
      for (int i = idx; i < childCount - 1; i++) {
        children[i] = children[i + 1];
      }
      childCount--;

      if (child.getName() != null && nameTable != null) {
        nameTable.remove(child.getName());
      }
    }
  }


  /**
   * Add a shape to the name lookup table.
   */
  public void addName(String nom, PShape shape) {
    if (parent != null) {
      parent.addName(nom, shape);
    } else {
      if (nameTable == null) {
        nameTable = new HashMap<String,PShape>();
      }
      nameTable.put(nom, shape);
    }
  }


  /**
   * Returns the index of child who.
   */
  public int getChildIndex(PShape who) {
    for (int i = 0; i < childCount; i++) {
      if (children[i] == who) {
        return i;
      }
    }
    return -1;
  }


  public void updateRoot(PShape root) {    
  }

  
  public PShape getTessellation() {
    return null;
  }
  
  
  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  /** The shape type, one of GROUP, PRIMITIVE, PATH, or GEOMETRY. */
  public int getFamily() {
    return family;
  }


  public int getKind() {
    return kind;
  }


  public float[] getParams() {
    return getParams(null);
  }


  public float[] getParams(float[] target) {
    if (target == null || target.length != params.length) {
      target = new float[params.length];
    }
    PApplet.arrayCopy(params, target);
    return target;
  }


  public float getParam(int index) {
    return params[index];
  }

  
  public void setParams(float[] source) {
    if (params == null) {
      params = new float[source.length];  
    }    
    if (source.length != params.length) {
      PGraphics.showWarning("Wrong number of parameters");
      return;
    }
    PApplet.arrayCopy(source, params);
  }    
  

  public void setPath(int vcount, float[][] verts) {
    setPath(vcount, verts, 0, null);
  }
  
  
  public void setPath(int vcount, float[][] verts, int ccount, int[] codes) {
    if (verts == null || verts.length < vcount) return;
    if (0 < ccount && (codes == null || codes.length < ccount)) return;
      
    int ndim = verts[0].length;
    vertexCount = vcount;
    vertices = new float[vertexCount][ndim];
    for (int i = 0; i < vertexCount; i++) {
      PApplet.arrayCopy(verts[i], vertices[i]);
    }    
     
    vertexCodeCount = ccount;
    if (0 < vertexCodeCount) {
      vertexCodes = new int[vertexCodeCount];
      PApplet.arrayCopy(codes, vertexCodes, vertexCodeCount);
    }    
  }
  
  
  public int getVertexCount() {
    return vertexCount;
  }


  
  public PVector getVertex(int index) {
    return getVertex(index, null);
  }  
  
  
  public PVector getVertex(int index, PVector vec) {
    if (vec == null) {
      vec = new PVector();
    }
    vec.x = vertices[index][X];
    vec.y = vertices[index][Y];
    vec.z = vertices[index][Z];
    return vec;
  }

  
  public float getVertexX(int index) {
    return vertices[index][X];
  }


  public float getVertexY(int index) {
    return vertices[index][Y];
  }


  public float getVertexZ(int index) {
    return vertices[index][Z];
  }

  
  public void setVertex(int index, float x, float y) {
    setVertex(index, x, y, 0);
  }
  
  
  public void setVertex(int index, float x, float y, float z) {
    vertices[index][X] = x;
    vertices[index][Y] = y;
    vertices[index][Z] = z;
  }
  
  
  public PVector getNormal(int index) {
    return getNormal(index, null);
  }
  
  
  public PVector getNormal(int index, PVector vec) {
    if (vec == null) {
      vec = new PVector();
    }
    vec.x = vertices[index][NX];
    vec.y = vertices[index][NY];
    vec.z = vertices[index][NZ];
    return vec;    
  }
  
  
  public float getNormalX(int index) {
    return vertices[index][NX];
  }
  

  public float getNormalY(int index) {
    return vertices[index][NY];
  }
  
  
  public float getNormalZ(int index) {
    return vertices[index][NZ];  
  }    
  

  public void setNormal(int index, float nx, float ny, float nz) {
    vertices[index][NX] = nx;
    vertices[index][NY] = ny;
    vertices[index][NZ] = nz;    
  }
  
  
  public float getTextureU(int index) {
    return vertices[index][U];
  }
  
  
  public float getTextureV(int index) {
    return vertices[index][V];
  }  
  
  
  public void setTextureUV(int index, float u, float v) {
    vertices[index][U] = u;
    vertices[index][V] = v;
  }
  
  
  public int getFill(int index) {
    int a = (int) (vertices[index][A] * 255);
    int r = (int) (vertices[index][R] * 255);
    int g = (int) (vertices[index][G] * 255);
    int b = (int) (vertices[index][B] * 255);                                        
    return (a << 24) | (r << 16) | (g << 8) | b;
  }

  
  public void setFill(int index, int fill) {
    vertices[index][A] = ((fill >> 24) & 0xFF) / 255.0f; 
    vertices[index][R] = ((fill >> 16) & 0xFF) / 255.0f;
    vertices[index][G] = ((fill >>  8) & 0xFF) / 255.0f;
    vertices[index][B] = ((fill >>  0) & 0xFF) / 255.0f;  
  }  

  
  public int getStroke(int index) {
    int a = (int) (vertices[index][SA] * 255);
    int r = (int) (vertices[index][SR] * 255);
    int g = (int) (vertices[index][SG] * 255);
    int b = (int) (vertices[index][SB] * 255);                                        
    return (a << 24) | (r << 16) | (g << 8) | b;
  }

  
  public void setStroke(int index, int stroke) {
    vertices[index][SA] = ((stroke >> 24) & 0xFF) / 255.0f; 
    vertices[index][SR] = ((stroke >> 16) & 0xFF) / 255.0f;
    vertices[index][SG] = ((stroke >>  8) & 0xFF) / 255.0f;
    vertices[index][SB] = ((stroke >>  0) & 0xFF) / 255.0f;   
  }  
  
  
  public float getStrokeWeight(int index) {
    return vertices[index][SW];
  }
  

  public void setStrokeWeight(int index, float weight) {
    vertices[index][SW] = weight;
  }  
  
  
  public int getAmbient(int index) {
    return 0;
  }

  
  public void setAmbient(int index, int ambient) {
  }    
  
  public int getSpecular(int index) {
    return 0;
  }

  
  public void setSpecular(int index, int specular) {
  }    
    
  
  public int getEmissive(int index) {
    return 0;
  }

  
  public void setEmissive(int index, int emissive) {
  }     
  
  
  public float getShininess(int index) {
    return 0;
  }

  
  public void setShininess(int index, float shine) {
  }
  
  
  public int[] getVertexCodes() {
    if (vertexCodes == null) {
      return null;
    }
    if (vertexCodes.length != vertexCodeCount) {
      vertexCodes = PApplet.subset(vertexCodes, 0, vertexCodeCount);
    }
    return vertexCodes;
  }
  
  
  public int getVertexCodeCount() {
    return vertexCodeCount;
  }


  /**
   * One of VERTEX, BEZIER_VERTEX, CURVE_VERTEX, or BREAK.
   */
  public int getVertexCode(int index) {
    return vertexCodes[index];
  }


  public boolean isClosed() {
    return close;
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  // http://www.ecse.rpi.edu/Homepages/wrf/Research/Short_Notes/pnpoly.html
  public boolean contains(float x, float y) {
    if (family == PATH) {
      boolean c = false;
      for (int i = 0, j = vertexCount-1; i < vertexCount; j = i++) {
        if (((vertices[i][Y] > y) != (vertices[j][Y] > y)) &&
            (x <
                (vertices[j][X]-vertices[i][X]) *
                (y-vertices[i][Y]) /
                (vertices[j][1]-vertices[i][Y]) +
                vertices[i][X])) {
          c = !c;
        }
      }
      return c;
    } else {
      throw new IllegalArgumentException("The contains() method is only implemented for paths.");
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  public void center(float cx, float cy) {
    
  }
  
  public void center(float cx, float cy, float cz) {
    
  }  
  
  // translate, rotate, scale, apply (no push/pop)
  //   these each call matrix.translate, etc
  // if matrix is null when one is called,
  //   it is created and set to identity

  public void translate(float tx, float ty) {
    checkMatrix(2);
    matrix.translate(tx, ty);
  }

  /**
   * Specifies an amount to displace the shape. The <b>x</b> parameter specifies left/right translation, the <b>y</b> parameter specifies up/down translation, and the <b>z</b> parameter specifies translations toward/away from the screen. Subsequent calls to the method accumulates the effect. For example, calling <b>translate(50, 0)</b> and then <b>translate(20, 0)</b> is the same as <b>translate(70, 0)</b>. This transformation is applied directly to the shape, it's not refreshed each time <b>draw()</b> is run.
   * <br><br>Using this method with the <b>z</b> parameter requires using the P3D or OPENGL parameter in combination with size.
   * @webref
   * @param tx left/right translation
   * @param ty up/down translation
   * @param tz forward/back translation
   * @brief Displaces the shape
   */
  public void translate(float tx, float ty, float tz) {
    checkMatrix(3);
    matrix.translate(tx, ty, tz);
  }

  /**
   * Rotates a shape around the x-axis the amount specified by the <b>angle</b> parameter. Angles should be specified in radians (values from 0 to TWO_PI) or converted to radians with the <b>radians()</b> method.
   * <br><br>Shapes are always rotated around the upper-left corner of their bounding box. Positive numbers rotate objects in a clockwise direction.
   * Subsequent calls to the method accumulates the effect. For example, calling <b>rotateX(HALF_PI)</b> and then <b>rotateX(HALF_PI)</b> is the same as <b>rotateX(PI)</b>.
   * This transformation is applied directly to the shape, it's not refreshed each time <b>draw()</b> is run.
   * <br><br>This method requires a 3D renderer. You need to pass P3D or OPENGL as a third parameter into the <b>size()</b> method as shown in the example above.
   * @param angle angle of rotation specified in radians
   * @webref
   * @brief Rotates the shape around the x-axis
   */
  public void rotateX(float angle) {
    rotate(angle, 1, 0, 0);
  }

  /**
   * Rotates a shape around the y-axis the amount specified by the <b>angle</b> parameter. Angles should be specified in radians (values from 0 to TWO_PI) or converted to radians with the <b>radians()</b> method.
   * <br><br>Shapes are always rotated around the upper-left corner of their bounding box. Positive numbers rotate objects in a clockwise direction.
   * Subsequent calls to the method accumulates the effect. For example, calling <b>rotateY(HALF_PI)</b> and then <b>rotateY(HALF_PI)</b> is the same as <b>rotateY(PI)</b>.
   * This transformation is applied directly to the shape, it's not refreshed each time <b>draw()</b> is run.
   * <br><br>This method requires a 3D renderer. You need to pass P3D or OPENGL as a third parameter into the <b>size()</b> method as shown in the example above.
   * @param angle angle of rotation specified in radians
   * @webref
   * @brief Rotates the shape around the y-axis
   */
  public void rotateY(float angle) {
    rotate(angle, 0, 1, 0);
  }


  /**
   * Rotates a shape around the z-axis the amount specified by the <b>angle</b> parameter. Angles should be specified in radians (values from 0 to TWO_PI) or converted to radians with the <b>radians()</b> method.
   * <br><br>Shapes are always rotated around the upper-left corner of their bounding box. Positive numbers rotate objects in a clockwise direction.
   * Subsequent calls to the method accumulates the effect. For example, calling <b>rotateZ(HALF_PI)</b> and then <b>rotateZ(HALF_PI)</b> is the same as <b>rotateZ(PI)</b>.
   * This transformation is applied directly to the shape, it's not refreshed each time <b>draw()</b> is run.
   * <br><br>This method requires a 3D renderer. You need to pass P3D or OPENGL as a third parameter into the <b>size()</b> method as shown in the example above.
   * @param angle angle of rotation specified in radians
   * @webref
   * @brief Rotates the shape around the z-axis
   */
  public void rotateZ(float angle) {
    rotate(angle, 0, 0, 1);
  }

  /**
   * Rotates a shape the amount specified by the <b>angle</b> parameter. Angles should be specified in radians (values from 0 to TWO_PI) or converted to radians with the <b>radians()</b> method.
   * <br><br>Shapes are always rotated around the upper-left corner of their bounding box. Positive numbers rotate objects in a clockwise direction.
   * Transformations apply to everything that happens after and subsequent calls to the method accumulates the effect.
   * For example, calling <b>rotate(HALF_PI)</b> and then <b>rotate(HALF_PI)</b> is the same as <b>rotate(PI)</b>.
   * This transformation is applied directly to the shape, it's not refreshed each time <b>draw()</b> is run.
   * @param angle angle of rotation specified in radians
   * @webref
   * @brief Rotates the shape
   */
  public void rotate(float angle) {
    checkMatrix(2);  // at least 2...
    matrix.rotate(angle);
  }


  public void rotate(float angle, float v0, float v1, float v2) {
    checkMatrix(3);
    matrix.rotate(angle, v0, v1, v2);
  }


  //

  /**
   * @param s percentage to scale the object
   */
  public void scale(float s) {
    checkMatrix(2);  // at least 2...
    matrix.scale(s);
  }


  public void scale(float x, float y) {
    checkMatrix(2);
    matrix.scale(x, y);
  }


  /**
   * Increases or decreases the size of a shape by expanding and contracting vertices. Shapes always scale from the relative origin of their bounding box.
   * Scale values are specified as decimal percentages. For example, the method call <b>scale(2.0)</b> increases the dimension of a shape by 200%.
   * Subsequent calls to the method multiply the effect. For example, calling <b>scale(2.0)</b> and then <b>scale(1.5)</b> is the same as <b>scale(3.0)</b>.
   * This transformation is applied directly to the shape, it's not refreshed each time <b>draw()</b> is run.
   * <br><br>Using this fuction with the <b>z</b> parameter requires passing P3D or OPENGL into the size() parameter.
   * @param x percentage to scale the object in the x-axis
   * @param y percentage to scale the object in the y-axis
   * @param z percentage to scale the object in the z-axis
   * @webref
   * @brief Increases and decreases the size of a shape
   */
  public void scale(float x, float y, float z) {
    checkMatrix(3);
    matrix.scale(x, y, z);
  }


  public void centerAt(float cx, float cy, float cz) {

  }


  //


  public void resetMatrix() {
    checkMatrix(2);
    matrix.reset();
  }


  public void applyMatrix(PMatrix source) {
    if (source instanceof PMatrix2D) {
      applyMatrix((PMatrix2D) source);
    } else if (source instanceof PMatrix3D) {
      applyMatrix((PMatrix3D) source);
    }
  }


  public void applyMatrix(PMatrix2D source) {
    applyMatrix(source.m00, source.m01, 0, source.m02,
                source.m10, source.m11, 0, source.m12,
                0, 0, 1, 0,
                0, 0, 0, 1);
  }


  public void applyMatrix(float n00, float n01, float n02,
                          float n10, float n11, float n12) {
    checkMatrix(2);
    matrix.apply(n00, n01, n02, 0,
                 n10, n11, n12, 0,
                 0,   0,   1,   0,
                 0,   0,   0,   1);
  }


  public void apply(PMatrix3D source) {
    applyMatrix(source.m00, source.m01, source.m02, source.m03,
                source.m10, source.m11, source.m12, source.m13,
                source.m20, source.m21, source.m22, source.m23,
                source.m30, source.m31, source.m32, source.m33);
  }


  public void applyMatrix(float n00, float n01, float n02, float n03,
                          float n10, float n11, float n12, float n13,
                          float n20, float n21, float n22, float n23,
                          float n30, float n31, float n32, float n33) {
    checkMatrix(3);
    matrix.apply(n00, n01, n02, n03,
                 n10, n11, n12, n13,
                 n20, n21, n22, n23,
                 n30, n31, n32, n33);
  }


  //


  /**
   * Make sure that the shape's matrix is 1) not null, and 2) has a matrix
   * that can handle <em>at least</em> the specified number of dimensions.
   */
  protected void checkMatrix(int dimensions) {
    if (matrix == null) {
      if (dimensions == 2) {
        matrix = new PMatrix2D();
      } else {
        matrix = new PMatrix3D();
      }
    } else if (dimensions == 3 && (matrix instanceof PMatrix2D)) {
      // time for an upgrayedd for a double dose of my pimpin'
      matrix = new PMatrix3D(matrix);
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  /**
   * Center the shape based on its bounding box. Can't assume
   * that the bounding box is 0, 0, width, height. Common case will be
   * opening a letter size document in Illustrator, and drawing something
   * in the middle, then reading it in as an svg file.
   * This will also need to flip the y axis (scale(1, -1)) in cases
   * like Adobe Illustrator where the coordinates start at the bottom.
   */
//  public void center() {
//  }


  /**
   * Set the pivot point for all transformations.
   */
//  public void pivot(float x, float y) {
//    px = x;
//    py = y;
//  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

  
  public void colorMode(int mode) {
    colorMode(mode, colorModeX, colorModeY, colorModeZ, colorModeA);
  }

  /**
   * @param max range for all color elements
   */
  public void colorMode(int mode, float max) {
    colorMode(mode, max, max, max, max);
  }


  /**
   * @param maxX range for the red or hue depending on the current color mode
   * @param maxY range for the green or saturation depending on the current color mode
   * @param maxZ range for the blue or brightness depending on the current color mode
   */
  public void colorMode(int mode, float maxX, float maxY, float maxZ) {
    colorMode(mode, maxX, maxY, maxZ, colorModeA);
  }

/**
 * @param maxA range for the alpha
 */
  public void colorMode(int mode,
                        float maxX, float maxY, float maxZ, float maxA) {
    colorMode = mode;

    colorModeX = maxX;  // still needs to be set for hsb
    colorModeY = maxY;
    colorModeZ = maxZ;
    colorModeA = maxA;

    // if color max values are all 1, then no need to scale
    colorModeScale =
      ((maxA != 1) || (maxX != maxY) || (maxY != maxZ) || (maxZ != maxA));

    // if color is rgb/0..255 this will make it easier for the
    // red() green() etc functions
    colorModeDefault = (colorMode == RGB) &&
      (colorModeA == 255) && (colorModeX == 255) &&
      (colorModeY == 255) && (colorModeZ == 255);
  }
  
  
  protected void colorCalc(int rgb) {
    if (((rgb & 0xff000000) == 0) && (rgb <= colorModeX)) {
      colorCalc((float) rgb);

    } else {
      colorCalcARGB(rgb, colorModeA);
    }
  }


  protected void colorCalc(int rgb, float alpha) {
    if (((rgb & 0xff000000) == 0) && (rgb <= colorModeX)) {  // see above
      colorCalc((float) rgb, alpha);

    } else {
      colorCalcARGB(rgb, alpha);
    }
  }


  protected void colorCalc(float gray) {
    colorCalc(gray, colorModeA);
  }


  protected void colorCalc(float gray, float alpha) {
    if (gray > colorModeX) gray = colorModeX;
    if (alpha > colorModeA) alpha = colorModeA;

    if (gray < 0) gray = 0;
    if (alpha < 0) alpha = 0;

    calcR = colorModeScale ? (gray / colorModeX) : gray;
    calcG = calcR;
    calcB = calcR;
    calcA = colorModeScale ? (alpha / colorModeA) : alpha;

    calcRi = (int)(calcR*255); calcGi = (int)(calcG*255);
    calcBi = (int)(calcB*255); calcAi = (int)(calcA*255);
    calcColor = (calcAi << 24) | (calcRi << 16) | (calcGi << 8) | calcBi;
    calcAlpha = (calcAi != 255);
  }


  protected void colorCalc(float x, float y, float z) {
    colorCalc(x, y, z, colorModeA);
  }


  protected void colorCalc(float x, float y, float z, float a) {
    if (x > colorModeX) x = colorModeX;
    if (y > colorModeY) y = colorModeY;
    if (z > colorModeZ) z = colorModeZ;
    if (a > colorModeA) a = colorModeA;

    if (x < 0) x = 0;
    if (y < 0) y = 0;
    if (z < 0) z = 0;
    if (a < 0) a = 0;

    switch (colorMode) {
    case RGB:
      if (colorModeScale) {
        calcR = x / colorModeX;
        calcG = y / colorModeY;
        calcB = z / colorModeZ;
        calcA = a / colorModeA;
      } else {
        calcR = x; calcG = y; calcB = z; calcA = a;
      }
      break;

    case HSB:
      x /= colorModeX; // h
      y /= colorModeY; // s
      z /= colorModeZ; // b

      calcA = colorModeScale ? (a/colorModeA) : a;

      if (y == 0) {  // saturation == 0
        calcR = calcG = calcB = z;

      } else {
        float which = (x - (int)x) * 6.0f;
        float f = which - (int)which;
        float p = z * (1.0f - y);
        float q = z * (1.0f - y * f);
        float t = z * (1.0f - (y * (1.0f - f)));

        switch ((int)which) {
        case 0: calcR = z; calcG = t; calcB = p; break;
        case 1: calcR = q; calcG = z; calcB = p; break;
        case 2: calcR = p; calcG = z; calcB = t; break;
        case 3: calcR = p; calcG = q; calcB = z; break;
        case 4: calcR = t; calcG = p; calcB = z; break;
        case 5: calcR = z; calcG = p; calcB = q; break;
        }
      }
      break;
    }
    calcRi = (int)(255*calcR); calcGi = (int)(255*calcG);
    calcBi = (int)(255*calcB); calcAi = (int)(255*calcA);
    calcColor = (calcAi << 24) | (calcRi << 16) | (calcGi << 8) | calcBi;
    calcAlpha = (calcAi != 255);
  }


  protected void colorCalcARGB(int argb, float alpha) {
    if (alpha == colorModeA) {
      calcAi = (argb >> 24) & 0xff;
      calcColor = argb;
    } else {
      calcAi = (int) (((argb >> 24) & 0xff) * (alpha / colorModeA));
      calcColor = (calcAi << 24) | (argb & 0xFFFFFF);
    }
    calcRi = (argb >> 16) & 0xff;
    calcGi = (argb >> 8) & 0xff;
    calcBi = argb & 0xff;
    calcA = calcAi / 255.0f;
    calcR = calcRi / 255.0f;
    calcG = calcGi / 255.0f;
    calcB = calcBi / 255.0f;
    calcAlpha = (calcAi != 255);
  }
   
  
}