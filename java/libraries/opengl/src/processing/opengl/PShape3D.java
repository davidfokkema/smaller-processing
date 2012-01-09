/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2011 Andres Colubri
  Copyright (c) 2010 Ben Fry and Casey Reas

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

package processing.opengl;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GL2ES1;

import processing.core.PApplet;
import processing.core.PGraphics;
import processing.core.PImage;
import processing.core.PMatrix;
import processing.core.PMatrix2D;
import processing.core.PMatrix3D;
import processing.core.PShape;
import processing.core.PStyle;
import processing.opengl.PGraphicsOpenGL.InGeometry;
import processing.opengl.PGraphicsOpenGL.TessGeometry;
import processing.opengl.PGraphicsOpenGL.Tessellator;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashSet;

// Notes about geometry update in PShape3D.
// 1) Flush mechanism for data update in PShape3D?
//    only do copy to tess arrays, and mark updated shapes.
//    When drawing, put all modified data in large array and
//    copy to VBO using single glBufferSubData() call.
// 2) What about fill called on a GROUP shape? should apply
//    color change to all child shapes. Probably yes.
// 3) translate, scale, rotate, and applyMatrix could
//    work in a similar way as update color, since they are
//    transformations that apply on all tesselated geometry
//    at once. So calculate matrix transformation and apply
//    on x,y,z coordinates (as it is done in the tessellator),
//    copy to tess arrays, and then copy to VBO when appropriate.
// 4) Under this scenario, map/unmap methods are only required
//    for advanced use/libraries (custom modification of complex
//    meshes and patches for example).

/**
 * This class holds a 3D model composed of vertices, normals, colors (per vertex) and 
 * texture coordinates (also per vertex). All this data is stored in Vertex Buffer Objects
 * (VBO) in GPU memory for very fast access.
 * OBJ loading implemented using code from Saito's OBJLoader library (http://code.google.com/p/saitoobjloader/)
 * and OBJReader from Ahmet Kizilay (http://www.openprocessing.org/visuals/?visualID=191). 
 * By Andres Colubri
 * 
 * 
 * Other formats to consider:
 * AMF: http://en.wikipedia.org/wiki/Additive_Manufacturing_File_Format
 * STL: http://en.wikipedia.org/wiki/STL_(file_format)
 * OFF: http://en.wikipedia.org/wiki/STL_(file_format)
 * DXF: http://en.wikipedia.org/wiki/AutoCAD_DXF
 */

public class PShape3D extends PShape {
  protected PGraphicsOpenGL ogl;

  protected PShape3D root;  
  protected int glMode;
    
  protected InGeometry in;
  protected TessGeometry tess;
  protected Tessellator tessellator;
  
  protected HashSet<PImage> textures;
  protected PImage texture;
  
  // ........................................................
  
  // OpenGL buffers  
  
  public int glFillVertexBufferID;
  public int glFillColorBufferID;
  public int glFillNormalBufferID;
  public int glFillTexCoordBufferID;  
  public int glFillIndexBufferID;
  
  public int glLineVertexBufferID;
  public int glLineColorBufferID;
  public int glLineNormalBufferID;
  public int glLineAttribBufferID;
  public int glLineIndexBufferID;  
  
  public int glPointVertexBufferID;
  public int glPointColorBufferID;
  public int glPointNormalBufferID;
  public int glPointAttribBufferID;
  public int glPointIndexBufferID;  

  // ........................................................
  
  // Offsets for geometry aggregation and update.
  
  protected int fillVertCopyOffset;
  protected int fillIndCopyOffset;
  protected int lineVertCopyOffset;
  protected int lineIndCopyOffset;
  protected int pointVertCopyOffset;
  protected int pointIndCopyOffset;

  protected int lastFillVertexOffset;
  protected int lastFillIndexOffset;  
  protected int lastLineVertexOffset;
  protected int lastLineIndexOffset;    
  protected int lastPointVertexOffset;
  protected int lastPointIndexOffset;    
  
  // ........................................................
  
  // Drawing/rendering state
  
  protected boolean tessellated;
  
  boolean modifiedFillVertices;
  boolean modifiedFillColors;
  boolean modifiedFillNormals;
  boolean modifiedFillTexCoords;  
  
  boolean modifiedLineVertices;
  boolean modifiedLineColors;
  boolean modifiedLineNormals;
  boolean modifiedLineAttributes;  

  boolean modifiedPointVertices;
  boolean modifiedPointColors;
  boolean modifiedPointNormals;
  boolean modifiedPointAttributes;  

  protected VertexCache fillVerticesCache;
  protected VertexCache fillColorsCache;
  protected VertexCache fillNormalsCache;
  protected VertexCache fillTexCoordsCache;  
  
  protected VertexCache lineVerticesCache;
  protected VertexCache lineColorsCache;
  protected VertexCache lineNormalsCache;
  protected VertexCache lineAttributesCache;  

  protected VertexCache pointVerticesCache;
  protected VertexCache pointColorsCache;
  protected VertexCache pointNormalsCache;
  protected VertexCache pointAttributesCache;  
  
  public static final int DEFAULT_CACHE_SIZE = 128;
    
  protected boolean isSolid;
  protected boolean isClosed;
  
  protected boolean openContour = false;
  protected boolean breakShape = false;
  protected boolean shapeEnded = false;
  
  protected boolean hasFill;
  protected boolean hasLines;
  protected boolean hasPoints;
  
  protected boolean applyMatrix;
  protected boolean childHasMatrix;
  
  // ........................................................
  
  // Input data
  
  protected float normalX, normalY, normalZ;

  // ........................................................

  // Fill, stroke and tint colors

  protected boolean fill;
  protected float fillR, fillG, fillB, fillA;
  
  protected boolean stroke;
  protected float strokeR, strokeG, strokeB, strokeA;  
  
  protected boolean tint;
  protected float tintR, tintG, tintB, tintA;
  
  // ........................................................
  
  // Bezier and Catmull-Rom curves  

  protected boolean bezierInited = false;
  public int bezierDetail = 20;
  protected PMatrix3D bezierDrawMatrix;  

  protected boolean curveInited = false;
  protected int curveDetail = 20;
  public float curveTightness = 0;
  
  // catmull-rom basis matrix, perhaps with optional s parameter
  protected PMatrix3D curveBasisMatrix;
  protected PMatrix3D curveDrawMatrix;

  protected PMatrix3D bezierBasisInverse;
  protected PMatrix3D curveToBezierMatrix;

  protected float curveVertices[][];
  protected int curveVertexCount;  
  
  // ........................................................
  
  // Modes inherited from renderer  
  
  protected int textureMode;
  protected int rectMode;
  protected int ellipseMode;
  protected int shapeMode;
  protected int imageMode;
  
  public PShape3D(PApplet parent, int family) {
    ogl = (PGraphicsOpenGL)parent.g;
    
    glMode = GL.GL_STATIC_DRAW;
    
    glFillVertexBufferID = 0;
    glFillColorBufferID = 0;
    glFillNormalBufferID = 0;
    glFillTexCoordBufferID = 0;
    glFillIndexBufferID = 0;
    
    glLineVertexBufferID = 0;
    glLineColorBufferID = 0;
    glLineNormalBufferID = 0;
    glLineAttribBufferID = 0;
    glLineIndexBufferID = 0;
    
    glPointVertexBufferID = 0;
    glPointColorBufferID = 0;
    glPointNormalBufferID = 0;
    glPointAttribBufferID = 0;
    glPointIndexBufferID = 0;
    
    this.tessellator = ogl.tessellator;
    this.family = family;    
    this.root = this;
    this.parent = null;
    this.tessellated = false;
    
    tess = ogl.newTessGeometry(RETAINED);    
    if (family == GEOMETRY || family == PRIMITIVE || family == PATH) {
      in = ogl.newInGeometry();      
    }
    
    // Modes are retrieved from the current values in the renderer.
    textureMode = ogl.textureMode;    
    rectMode = ogl.rectMode;
    ellipseMode = ogl.ellipseMode;
    shapeMode = ogl.shapeMode;
    imageMode = ogl.imageMode;    
    
    colorMode(ogl.colorMode, ogl.colorModeX, ogl.colorModeY, ogl.colorModeZ, ogl.colorModeA);
    
    // Initial values for fill, stroke and tint colors are also imported from the renderer.
    // This is particular relevant for primitive shapes, since is not possible to set 
    // their color separately when creating them, and their input vertices are actually
    // generated at rendering time, by which the color configuration of the renderer might
    // have changed.
    fill = ogl.fill;
    fillR = ((ogl.fillColor >> 16) & 0xFF) / 255.0f;    
    fillG = ((ogl.fillColor >>  8) & 0xFF) / 255.0f; 
    fillB = ((ogl.fillColor >>  0) & 0xFF) / 255.0f;
    fillA = ((ogl.fillColor >> 24) & 0xFF) / 255.0f;
      
    stroke = ogl.stroke;      
    strokeR = ((ogl.strokeColor >> 16) & 0xFF) / 255.0f;    
    strokeG = ((ogl.strokeColor >>  8) & 0xFF) / 255.0f; 
    strokeB = ((ogl.strokeColor >>  0) & 0xFF) / 255.0f;
    strokeA = ((ogl.strokeColor >> 24) & 0xFF) / 255.0f;

    strokeWeight = ogl.strokeWeight;    
    
    tint = ogl.tint;  
    tintR = ((ogl.tintColor >> 16) & 0xFF) / 255.0f;    
    tintG = ((ogl.tintColor >>  8) & 0xFF) / 255.0f; 
    tintB = ((ogl.tintColor >>  0) & 0xFF) / 255.0f;
    tintA = ((ogl.tintColor >> 24) & 0xFF) / 255.0f;
    
    normalX = normalY = 0; 
    normalZ = 1;
  }
  
  
  public void setKind(int kind) {
    this.kind = kind;
  }

  
  public void setMode(int mode) {
    if (mode == STATIC) {
      glMode = GL.GL_STATIC_DRAW;
    } else if (mode == DYNAMIC) {
      glMode = GL.GL_DYNAMIC_DRAW;
    } else if (mode == STREAM) {
      glMode = GL2.GL_STREAM_COPY;
    }
  }
  
  public void addShape(PShape child) {
    if (child instanceof PShape3D) {
      if (family == GROUP) {
        super.addChild(child);
        child.updateRoot(root);
        root.tessellated = false;
        tessellated = false;
        ((PShape3D)child).tessellated = false;
      } else {
        PGraphics.showWarning("Cannot add child shape to non-group shape.");
      }
    } else {
      PGraphics.showWarning("Shape must be 3D to be added to the group.");
    }
  }  
  
  public void updateRoot(PShape root) {
    this.root = (PShape3D) root;
    if (family == GROUP) {
      for (int i = 0; i < childCount; i++) {
        PShape3D child = (PShape3D)children[i];
        child.updateRoot(root);
      }
    }
  }      
  
  protected void finalize() throws Throwable {
    try {
      finalizeFillBuffers();  
      finalizeLineBuffers();
      finalizePointBuffers();
    } finally {
      super.finalize();
    }
  }
  
  protected void finalizeFillBuffers() {
    if (glFillVertexBufferID != 0) {    
      ogl.finalizeVertexBufferObject(glFillVertexBufferID);   
    }    
    
    if (glFillColorBufferID != 0) {    
      ogl.finalizeVertexBufferObject(glFillColorBufferID);   
    }    

    if (glFillNormalBufferID != 0) {    
      ogl.finalizeVertexBufferObject(glFillNormalBufferID);   
    }     

    if (glFillTexCoordBufferID != 0) {    
      ogl.finalizeVertexBufferObject(glFillTexCoordBufferID);   
    }    
    
    if (glFillIndexBufferID != 0) {    
      ogl.finalizeVertexBufferObject(glFillIndexBufferID);   
    }   
  }
  
  protected void finalizeLineBuffers() {
    if (glLineVertexBufferID != 0) {    
      ogl.finalizeVertexBufferObject(glLineVertexBufferID);   
    }    
    
    if (glLineColorBufferID != 0) {    
      ogl.finalizeVertexBufferObject(glLineColorBufferID);   
    }    

    if (glLineNormalBufferID != 0) {    
      ogl.finalizeVertexBufferObject(glLineNormalBufferID);   
    }     

    if (glLineAttribBufferID != 0) {    
      ogl.finalizeVertexBufferObject(glLineAttribBufferID);   
    }    
    
    if (glLineIndexBufferID != 0) {    
      ogl.finalizeVertexBufferObject(glLineIndexBufferID);   
    }  
  }  
  
  protected void finalizePointBuffers() {
    if (glPointVertexBufferID != 0) {    
      ogl.finalizeVertexBufferObject(glPointVertexBufferID);   
    }    
    
    if (glPointColorBufferID != 0) {    
      ogl.finalizeVertexBufferObject(glPointColorBufferID);   
    }    

    if (glPointNormalBufferID != 0) {    
      ogl.finalizeVertexBufferObject(glPointNormalBufferID);   
    }     

    if (glPointAttribBufferID != 0) {    
      ogl.finalizeVertexBufferObject(glPointAttribBufferID);   
    }    
    
    if (glPointIndexBufferID != 0) {    
      ogl.finalizeVertexBufferObject(glPointIndexBufferID);   
    }  
  }
    
  ///////////////////////////////////////////////////////////  
  
  //
  
  // Drawing methods  
  
  
  public void textureMode(int mode) {
    if (family == GROUP) {
      for (int i = 0; i < childCount; i++) {
        PShape3D child = (PShape3D) children[i];        
        child.textureMode(mode);        
      }         
    } else {    
      textureMode = mode;
    }
  }

  
  public void texture(PImage tex) {
    if (family == GROUP) {
      for (int i = 0; i < childCount; i++) {
        PShape3D child = (PShape3D) children[i];        
        child.texture(tex);        
      }      
    } else {
      if (texture != null && parent != null) {
        ((PShape3D)parent).removeTexture(texture);
      }
      texture = tex;
      if (parent != null) {
        ((PShape3D)parent).addTexture(texture);
      }
    }        
  }

  
  public void noTexture() {
    if (family == GROUP) {
      for (int i = 0; i < childCount; i++) {
        PShape3D child = (PShape3D) children[i];        
        child.noTexture();        
      }
    } else {
      if (texture != null && parent != null) {
        ((PShape3D)parent).removeTexture(texture);
      }
      texture = null;
    }
  }  

  
  protected void addTexture(PImage tex) {
    if (textures == null) {
      textures = new HashSet<PImage>();      
    }
    textures.add(tex);
    if (parent != null) {
      ((PShape3D)parent).addTexture(tex);
    }   
  }
  
  
  protected void removeTexture(PImage tex) {
    if (textures != null) {
      // If it already has a texture, we need to remove from 
      // the parent (if no other child shape has it :-P)
      // .... 
      textures.remove(tex);
      if (textures.size() == 0) {
        textures = null;
      }
    }
  }
  
  
  public void solid(boolean solid) {
    isSolid = solid;
  }
  
  
  public void beginContour() {
    if (openContour) {
      PGraphics.showWarning("P3D: Already called beginContour().");
      return;
    }    
    openContour = true;    
  }
  
  
  public void endContour() {
    if (!openContour) {
      PGraphics.showWarning("P3D: Need to call beginContour() first.");
      return;      
    }
    openContour = false;    
    breakShape = true;  
  }

  
  public void vertex(float x, float y) {
    vertex(x, y, 0, 0, 0);   
  }

  
  public void vertex(float x, float y, float u, float v) {
    vertex(x, y, 0, u, v); 
  }      
  
  
  public void vertex(float x, float y, float z) {
    vertex(x, y, z, 0, 0);      
  }

  
  public void vertex(float x, float y, float z, float u, float v) {
    vertexImpl(x, y, z, u, v, VERTEX);  
  }  
  
  
  protected void vertexImpl(float x, float y, float z, float u, float v, int code) {
    if (family == GROUP) {      
      PGraphics.showWarning("Cannot add vertices to GROUP shape");
      return;
    }

    boolean textured = texture != null;
    float fR, fG, fB, fA;
    fR = fG = fB = fA = 0;
    if (fill || textured) {
      if (!textured) {
        fR = fillR;
        fG = fillG;
        fB = fillB;
        fA = fillA;
      } else {       
        if (tint) {
          fR = tintR;
          fG = tintG;
          fB = tintB;
          fA = tintA;
        } else {
          fR = 1;
          fG = 1;
          fB = 1;
          fA = 1;
        }
      }
    }    
    
    if (texture != null && textureMode == IMAGE) {
      u /= texture.width;
      v /= texture.height;
      
      PTexture tex = ogl.getTexture(texture);
      if (tex.isFlippedY()) {
        v = 1 - v;
      }            
    }
        
    float sR, sG, sB, sA, sW;
    sR = sG = sB = sA = sW = 0;
    if (stroke) {
      sR = strokeR;
      sG = strokeG;
      sB = strokeB;
      sA = strokeA;
      sW = strokeWeight;
    }     
    
    if (breakShape) {
      code = BREAK;
      breakShape = false;
    }    
    
    //in.addVertex(currentVertex, currentColor, currentNormal, currentTexcoord, currentStroke, code);
    
    in.addVertex(x, y, z, 
                 fR, fG, fB, fA, 
                 normalX, normalY, normalZ,
                 u, v, 
                 sR, sG, sB, sA, sW, 
                 code);    
    
    root.tessellated = false;
    tessellated = false;  
  }
  
  
  public void normal(float nx, float ny, float nz) {
    normalX = nx;
    normalY = ny;
    normalZ = nz;
  }

  
  public void end() {
    end(OPEN);
  }  

  public void end(int mode) {    
    isClosed = mode == CLOSE;    
    root.tessellated = false;
    tessellated = false;
    shapeEnded = true;
  }  
  
  public void setParams(float[] source) {
    super.setParams(source);
    root.tessellated = false;
    tessellated = false;
    shapeEnded = true;
  }

  //////////////////////////////////////////////////////////////

  // STROKE CAP/JOIN/WEIGHT

  
  public void strokeWeight(float weight) {
    strokeWeight = weight;
  }


  public void strokeJoin(int join) {
    strokeJoin = join;
  }


  public void strokeCap(int cap) {
    strokeCap = cap;
  }
    
  
  //////////////////////////////////////////////////////////////

  // FILL COLOR

  public void noFill() {
    fill = false;
    fillR = 0;
    fillG = 0;
    fillB = 0;
    fillA = 0;
    fillColor = 0x0;
    updateFillColor();
  }

  public void fill(int rgb) {
    colorCalc(rgb);
    fillFromCalc();
  }

  public void fill(int rgb, float alpha) {
    colorCalc(rgb, alpha);
    fillFromCalc();
  }

  public void fill(float gray) {
    colorCalc(gray);
    fillFromCalc();
  }

  public void fill(float gray, float alpha) {
    colorCalc(gray, alpha);
    fillFromCalc();
  }

  public void fill(float x, float y, float z) {
    colorCalc(x, y, z);
    fillFromCalc();
  }

  public void fill(float x, float y, float z, float a) {
    colorCalc(x, y, z, a);
    fillFromCalc();
  }

  protected void fillFromCalc() {
    fill = true;
    fillR = calcR;
    fillG = calcG;
    fillB = calcB;
    fillA = calcA;
    fillColor = calcColor;
    updateFillColor();  
  }

  protected void updateFillColor() {
    if (!shapeEnded || tess.fillVertexCount == 0 || texture != null) {
      return;
    }
      
    updateTesselation();
    
    int size = tess.fillVertexCount;
    float[] colors = tess.fillColors;
    int index;
    for (int i = 0; i < size; i++) {
      index = 4 * i;
      colors[index++] = fillR;
      colors[index++] = fillG;
      colors[index++] = fillB;
      colors[index  ] = fillA;
    }
    modifiedFillColors = true;
    modified();   
  }
  
    
  //////////////////////////////////////////////////////////////

  // STROKE COLOR 
  
  
  public void noStroke() {
    stroke = false;
    strokeR = 0;
    strokeG = 0;
    strokeB = 0;
    strokeA = 0;
    strokeColor = 0x0;
    updateStrokeColor();  
  }
  
  
  public void stroke(int rgb) {
    colorCalc(rgb);
    strokeFromCalc();
  }
  
  
  public void stroke(int rgb, float alpha) {
    colorCalc(rgb, alpha);
    strokeFromCalc();
  }

  
  public void stroke(float gray) {
    colorCalc(gray);
    strokeFromCalc();
  }

  
  public void stroke(float gray, float alpha) {
    colorCalc(gray, alpha);
    strokeFromCalc();
  }

  
  public void stroke(float x, float y, float z) {
    colorCalc(x, y, z);
    strokeFromCalc();
  }

  
  public void stroke(float x, float y, float z, float alpha) {
    colorCalc(x, y, z, alpha);
    strokeFromCalc();
  }
  
  
  protected void strokeFromCalc() {
    stroke = true;
    strokeR = calcR;
    strokeG = calcG;
    strokeB = calcB;
    strokeA = calcA;
    strokeColor = calcColor;
    updateStrokeColor();  
  }

  protected void updateStrokeColor() {
    if (shapeEnded) {
      updateTesselation();
      
      if (0 < tess.lineVertexCount) {
        int size = tess.lineVertexCount;
        float[] colors = tess.lineColors;
        int index;
        for (int i = 0; i < size; i++) {
          index = 4 * i;
          colors[index++] = strokeR;
          colors[index++] = strokeG;
          colors[index++] = strokeB;
          colors[index  ] = strokeA;
        }
        modifiedLineColors = true;
        modified();         
      }
      
      if (0 < tess.pointVertexCount) {
        int size = tess.pointVertexCount;
        float[] colors = tess.pointColors;
        int index;
        for (int i = 0; i < size; i++) {
          index = 4 * i;
          colors[index++] = strokeR;
          colors[index++] = strokeG;
          colors[index++] = strokeB;
          colors[index  ] = strokeA;
        }
        modifiedPointColors = true;
        modified();            
      }            
    }    
  }  

 
  //////////////////////////////////////////////////////////////

  // TINT COLOR 
  
  public void noTint() {
    tint = false;
    tintR = 0;
    tintG = 0;
    tintB = 0;
    tintA = 0;
    tintColor = 0x0;
    updateTintColor();   
  }  
  
  public void tint(int rgb) {
    colorCalc(rgb);
    tintFromCalc();    
  }  
  
  public void tint(int rgb, float alpha) {
    colorCalc(rgb, alpha);
    tintFromCalc();
  }
  
  public void tint(float gray) {
    colorCalc(gray);
    tintFromCalc();    
  }
  
  public void tint(float gray, float alpha) {
    colorCalc(gray, alpha);
    tintFromCalc();    
  }

  public void tint(float x, float y, float z) {
    colorCalc(x, y, z);
    tintFromCalc();    
  }
  
  public void tint(float x, float y, float z, float alpha) {
    colorCalc(x, y, z, alpha);
    tintFromCalc();    
  }  
  
  protected void tintFromCalc() {
    tint = true;
    tintR = calcR;
    tintG = calcG;
    tintB = calcB;
    tintA = calcA;
    tintColor = calcColor;
    updateTintColor();  
  }  
  
  protected void updateTintColor() {    
    if (!shapeEnded || tess.fillVertexCount == 0 || texture == null) {
      return;
    }
      
    updateTesselation();
    
    int size = tess.fillVertexCount;
    float[] colors = tess.fillColors;
    int index;
    for (int i = 0; i < size; i++) {
      index = 4 * i;
      colors[index++] = tintR;
      colors[index++] = tintG;
      colors[index++] = tintB;
      colors[index  ] = tintA;
    }
    modifiedFillColors = true;
    modified();  
  }
  
  ///////////////////////////////////////////////////////////  
  
  //
  
  // Geometric transformations
  
  public void center(float cx, float cy) {
    if (family == GROUP) {
//      for (int i = 0; i < childCount; i++) {
//        PShape3D child = (PShape3D) children[i];        
//        child.center(cx, cy);
//      }      
    } else {
      tess.center(cx, cy);
      
      modified(); 
      if (0 < tess.fillVertexCount) {
        modifiedFillVertices = true;
      }        
      if (0 < tess.lineVertexCount) {
        modifiedLineVertices = true;
        modifiedLineAttributes = true;
      }
      if (0 < tess.pointVertexCount) {
        modifiedPointVertices = true;    
      }      
    }
  }

  public void center(float cx, float cy, float cz) {
    if (family == GROUP) {
//      for (int i = 0; i < childCount; i++) {
//        PShape3D child = (PShape3D) children[i];        
//        child.center(cx, cy, cz);
//      }   
      
      // calculate current center of all child shapes
      // translate to the new center.
      
    } else {
      tess.center(cx, cy, cz);
      
      modified(); 
      if (0 < tess.fillVertexCount) {
        modifiedFillVertices = true;  
      }        
      if (0 < tess.lineVertexCount) {
        modifiedLineVertices = true;
        modifiedLineAttributes = true;
      }
      if (0 < tess.pointVertexCount) {
        modifiedPointVertices = true;        
      }      
    }
  }  
  
  public void translate(float tx, float ty) {
    if (family == GROUP) {
      // TODO: make sure that for group shapes, just applying the
      // gl transformation is efficient enough (might depend on
      // how much geometry is inside the group).
      childHasMatrix();
      applyMatrix = true;
      super.translate(tx, ty);
    } else {
      checkMatrix(2);
      matrix.reset();
      matrix.translate(tx, ty);
      tess.applyMatrix((PMatrix2D) matrix);
       
      modified();
      if (0 < tess.fillVertexCount) {
        modifiedFillVertices = true;  
        modifiedFillNormals = true; 
      }        
      if (0 < tess.lineVertexCount) {
        modifiedLineVertices = true;
        modifiedLineNormals = true;
        modifiedLineAttributes = true;
      }
      if (0 < tess.pointVertexCount) {
        modifiedPointVertices = true;
        modifiedPointNormals = true;        
      }
      
      // So the transformation is not applied again when drawing
      applyMatrix = false; 
    }    
  }
  
  public void translate(float tx, float ty, float tz) {
    if (family == GROUP) {
      // TODO: make sure that for group shapes, just applying the
      // gl transformation is efficient enough (might depend on
      // how much geometry is inside the group).
      childHasMatrix();
      applyMatrix = true;
      super.translate(tx, ty, tz);
    } else {
      checkMatrix(3);
      matrix.translate(tx, ty, tz);
      tess.applyMatrix((PMatrix3D) matrix);
      
      modified(); 
      if (0 < tess.fillVertexCount) {
        modifiedFillVertices = true;  
        modifiedFillNormals = true; 
      }        
      if (0 < tess.lineVertexCount) {
        modifiedLineVertices = true;
        modifiedLineNormals = true;
        modifiedLineAttributes = true;
      }
      if (0 < tess.pointVertexCount) {
        modifiedPointVertices = true;
        modifiedPointNormals = true;        
      }
      
      // So the transformation is not applied again when drawing
      applyMatrix = false;
    }    
  }
  
  
  public void rotate(float angle) {
    // TODO: implement for geometry shapes.
    super.rotate(angle);
  }
  
  public void rotate(float angle, float v0, float v1, float v2) {
    if (family == GROUP) {
      childHasMatrix();
      applyMatrix = true;
      super.rotate(angle, v0, v1, v2);
    } else {
      checkMatrix(3);
      matrix.reset();
      matrix.rotate(angle, v0, v1, v2);
      tess.applyMatrix((PMatrix3D) matrix);
            
      modified(); 
      if (0 < tess.fillVertexCount) {
        modifiedFillVertices = true;  
        modifiedFillNormals = true; 
      }        
      if (0 < tess.lineVertexCount) {
        modifiedLineVertices = true;
        modifiedLineNormals = true;
        modifiedLineAttributes = true;
      }
      if (0 < tess.pointVertexCount) {
        modifiedPointVertices = true;
        modifiedPointNormals = true;        
      }
      
      // So the transformation is not applied again when drawing
      applyMatrix = true;   
    }
  }
  
  public void scale(float s) {
    // TODO: implement for geometry shapes.
    super.scale(s);
  }


  public void scale(float x, float y) {
    // TODO: implement for geometry shapes.
    super.scale(x, y);
  }


  public void scale(float x, float y, float z) {
    if (family == GROUP) {
      childHasMatrix();
      applyMatrix = true;
      super.scale(x, y, z);
    } else {
      checkMatrix(3);
      matrix.scale(x, y, z);
      tess.applyMatrix((PMatrix3D) matrix);
      
      modified(); 
      if (0 < tess.fillVertexCount) {
        modifiedFillVertices = true;  
        modifiedFillNormals = true; 
      }        
      if (0 < tess.lineVertexCount) {
        modifiedLineVertices = true;
        modifiedLineNormals = true;
        modifiedLineAttributes = true;
      }
      if (0 < tess.pointVertexCount) {
        modifiedPointVertices = true;
        modifiedPointNormals = true;        
      }
      
      // So the transformation is not applied again when drawing
      applyMatrix = false;
    }    
  }  
  
  
  public void resetMatrix() {
    // What to do in the case of geometry shapes?
    // In order to properly reset the transformation,
    // we need to have the last matrix applied, calculate
    // the inverse and apply on the tess object... TODO
    checkMatrix(2);
    matrix.reset();
  }


  public void applyMatrix(PMatrix source) {
    super.applyMatrix(source);
  }


  public void applyMatrix(PMatrix2D source) {
    super.applyMatrix(source);
  }


  public void applyMatrix(float n00, float n01, float n02,
                          float n10, float n11, float n12) {
    super.applyMatrix(n00, n01, n02,
                      n10, n11, n12);
  }


  public void apply(PMatrix3D source) {
    // TODO: implement for geometry shapes.
    applyMatrix(source.m00, source.m01, source.m02, source.m03,
                source.m10, source.m11, source.m12, source.m13,
                source.m20, source.m21, source.m22, source.m23,
                source.m30, source.m31, source.m32, source.m33);
  }


  public void applyMatrix(float n00, float n01, float n02, float n03,
                          float n10, float n11, float n12, float n13,
                          float n20, float n21, float n22, float n23,
                          float n30, float n31, float n32, float n33) {
    if (family == GROUP) {
      childHasMatrix();
      applyMatrix = true;
      super.applyMatrix(n00, n01, n02, n03,
                        n10, n11, n12, n13,
                        n20, n21, n22, n23,
                        n30, n31, n32, n33);
    } else {
      checkMatrix(3);
      matrix.apply(n00, n01, n02, n03,
                   n10, n11, n12, n13,
                   n20, n21, n22, n23,
                   n30, n31, n32, n33);   
      tess.applyMatrix((PMatrix3D) matrix);
      
      modified(); 
      if (0 < tess.fillVertexCount) {
        modifiedFillVertices = true;  
        modifiedFillNormals = true; 
      }        
      if (0 < tess.lineVertexCount) {
        modifiedLineVertices = true;
        modifiedLineNormals = true;
        modifiedLineAttributes = true;
      }
      if (0 < tess.pointVertexCount) {
        modifiedPointVertices = true;
        modifiedPointNormals = true;        
      }
      
      // So the transformation is not applied again when drawing
      applyMatrix = false;     
    }
  }
  
  
  protected void childHasMatrix() {
    childHasMatrix = true;
    if (parent != null) {
      ((PShape3D)parent).childHasMatrix();
    }
  }
  
  ///////////////////////////////////////////////////////////  
  
  //
  
  // Bezier curves 
  
  public void bezierDetail(int detail) {
    bezierDetail = detail;

    if (bezierDrawMatrix == null) {
      bezierDrawMatrix = new PMatrix3D();
    }

    // setup matrix for forward differencing to speed up drawing
    ogl.splineForward(detail, bezierDrawMatrix);

    // multiply the basis and forward diff matrices together
    // saves much time since this needn't be done for each curve
    bezierDrawMatrix.apply(ogl.bezierBasisMatrix);
  }  
  
  public void bezierVertex(float x2, float y2,
                           float x3, float y3,
                           float x4, float y4) {
    bezierVertex(x2, y2, 0, x3, y3, 0, x4, y4, 0); 
  }
  
  public void bezierVertex(float x2, float y2, float z2,
                           float x3, float y3, float z3,
                           float x4, float y4, float z4) {
    bezierInitCheck();
    bezierVertexCheck();
    PMatrix3D draw = bezierDrawMatrix;

    float x1 = in.getlastVertexX();
    float y1 = in.getlastVertexY();
    float z1 = in.getlastVertexZ();

    float xplot1 = draw.m10*x1 + draw.m11*x2 + draw.m12*x3 + draw.m13*x4;
    float xplot2 = draw.m20*x1 + draw.m21*x2 + draw.m22*x3 + draw.m23*x4;
    float xplot3 = draw.m30*x1 + draw.m31*x2 + draw.m32*x3 + draw.m33*x4;

    float yplot1 = draw.m10*y1 + draw.m11*y2 + draw.m12*y3 + draw.m13*y4;
    float yplot2 = draw.m20*y1 + draw.m21*y2 + draw.m22*y3 + draw.m23*y4;
    float yplot3 = draw.m30*y1 + draw.m31*y2 + draw.m32*y3 + draw.m33*y4;

    float zplot1 = draw.m10*z1 + draw.m11*z2 + draw.m12*z3 + draw.m13*z4;
    float zplot2 = draw.m20*z1 + draw.m21*z2 + draw.m22*z3 + draw.m23*z4;
    float zplot3 = draw.m30*z1 + draw.m31*z2 + draw.m32*z3 + draw.m33*z4;

    for (int j = 0; j < bezierDetail; j++) {
      x1 += xplot1; xplot1 += xplot2; xplot2 += xplot3;
      y1 += yplot1; yplot1 += yplot2; yplot2 += yplot3;
      z1 += zplot1; zplot1 += zplot2; zplot2 += zplot3;
      vertexImpl(x1, y1, z1, 0, 0, BEZIER_VERTEX);
    }    
  }
  
  public void quadraticVertex(float cx, float cy,
                              float x3, float y3) {
    quadraticVertex(cx, cy, 0,
                    x3, y3, 0);
  }  
  
  public void quadraticVertex(float cx, float cy, float cz,
                              float x3, float y3, float z3) {
    float x1 = in.getlastVertexX();
    float y1 = in.getlastVertexY();
    float z1 = in.getlastVertexZ();

    bezierVertex(x1 + ((cx-x1)*2/3.0f), y1 + ((cy-y1)*2/3.0f), z1 + ((cz-z1)*2/3.0f),
                 x3 + ((cx-x3)*2/3.0f), y3 + ((cy-y3)*2/3.0f), z3 + ((cz-z3)*2/3.0f),
                 x3, y3, z3);
  }

  protected void bezierInitCheck() {
    if (!bezierInited) {
      bezierInit();
    }
  }

  protected void bezierInit() {
    // overkill to be broken out, but better parity with the curve stuff below
    bezierDetail(bezierDetail);
    bezierInited = true;
  }  
  
  protected void bezierVertexCheck() {
    if (kind != POLYGON) {
      throw new RuntimeException("createGeometry() or createGeometry(POLYGON) " +
                                 "must be used before bezierVertex() or quadraticVertex()");
    }
    if (in.vertexCount == 0) {
      throw new RuntimeException("vertex() must be used at least once" +
                                 "before bezierVertex() or quadraticVertex()");
    }
  }    
  
  ///////////////////////////////////////////////////////////  
  
  //
  
  // Catmull-Rom curves

  public void curveDetail(int detail) {
    curveDetail = detail;
    curveInit();
  }
  
  public void curveTightness(float tightness) {
    curveTightness = tightness;
    curveInit();
  }  
  
  public void curveVertex(float x, float y) {
    curveVertex(x, y, 0);
  }  

  public void curveVertex(float x, float y, float z) {
    curveVertexCheck();
    float[] vertex = curveVertices[curveVertexCount];
    vertex[X] = x;
    vertex[Y] = y;
    vertex[Z] = z;
    curveVertexCount++;

    // draw a segment if there are enough points
    if (curveVertexCount > 3) {
      curveVertexSegment(curveVertices[curveVertexCount-4][X],
                         curveVertices[curveVertexCount-4][Y],
                         curveVertices[curveVertexCount-4][Z],
                         curveVertices[curveVertexCount-3][X],
                         curveVertices[curveVertexCount-3][Y],
                         curveVertices[curveVertexCount-3][Z],
                         curveVertices[curveVertexCount-2][X],
                         curveVertices[curveVertexCount-2][Y],
                         curveVertices[curveVertexCount-2][Z],
                         curveVertices[curveVertexCount-1][X],
                         curveVertices[curveVertexCount-1][Y],
                         curveVertices[curveVertexCount-1][Z]);
    }
    
  }

  protected void curveVertexCheck() {
    
    if (kind != POLYGON) {
      throw new RuntimeException("You must use createGeometry() or " +
                                 "createGeometry(POLYGON) before curveVertex()");
    }
    
    // to improve code init time, allocate on first use.
    if (curveVertices == null) {
      curveVertices = new float[128][3];
    }

    if (curveVertexCount == curveVertices.length) {
      // Can't use PApplet.expand() cuz it doesn't do the copy properly
      float[][] temp = new float[curveVertexCount << 1][3];
      System.arraycopy(curveVertices, 0, temp, 0, curveVertexCount);
      curveVertices = temp;
    }
    curveInitCheck();
  }
  
  protected void curveInitCheck() {
    if (!curveInited) {
      curveInit();
    }
  }
  
  protected void curveInit() {
    // allocate only if/when used to save startup time
    if (curveDrawMatrix == null) {
      curveBasisMatrix = new PMatrix3D();
      curveDrawMatrix = new PMatrix3D();
      curveInited = true;
    }

    float s = curveTightness;
    curveBasisMatrix.set((s-1)/2f, (s+3)/2f,  (-3-s)/2f, (1-s)/2f,
                         (1-s),    (-5-s)/2f, (s+2),     (s-1)/2f,
                         (s-1)/2f, 0,         (1-s)/2f,  0,
                         0,        1,         0,         0);

    ogl.splineForward(curveDetail, curveDrawMatrix);

    if (bezierBasisInverse == null) {
      bezierBasisInverse = ogl.bezierBasisMatrix.get();
      bezierBasisInverse.invert();
      curveToBezierMatrix = new PMatrix3D();
    }

    // TODO only needed for PGraphicsJava2D? if so, move it there
    // actually, it's generally useful for other renderers, so keep it
    // or hide the implementation elsewhere.
    curveToBezierMatrix.set(curveBasisMatrix);
    curveToBezierMatrix.preApply(bezierBasisInverse);

    // multiply the basis and forward diff matrices together
    // saves much time since this needn't be done for each curve
    curveDrawMatrix.apply(curveBasisMatrix);
  }  
  
  /**
   * Handle emitting a specific segment of Catmull-Rom curve. This can be
   * overridden by subclasses that need more efficient rendering options.
   */
  protected void curveVertexSegment(float x1, float y1, float z1,
                                    float x2, float y2, float z2,
                                    float x3, float y3, float z3,
                                    float x4, float y4, float z4) {
    float x0 = x2;
    float y0 = y2;
    float z0 = z2;

    PMatrix3D draw = curveDrawMatrix;

    float xplot1 = draw.m10*x1 + draw.m11*x2 + draw.m12*x3 + draw.m13*x4;
    float xplot2 = draw.m20*x1 + draw.m21*x2 + draw.m22*x3 + draw.m23*x4;
    float xplot3 = draw.m30*x1 + draw.m31*x2 + draw.m32*x3 + draw.m33*x4;

    float yplot1 = draw.m10*y1 + draw.m11*y2 + draw.m12*y3 + draw.m13*y4;
    float yplot2 = draw.m20*y1 + draw.m21*y2 + draw.m22*y3 + draw.m23*y4;
    float yplot3 = draw.m30*y1 + draw.m31*y2 + draw.m32*y3 + draw.m33*y4;

    float zplot1 = draw.m10*z1 + draw.m11*z2 + draw.m12*z3 + draw.m13*z4;
    float zplot2 = draw.m20*z1 + draw.m21*z2 + draw.m22*z3 + draw.m23*z4;
    float zplot3 = draw.m30*z1 + draw.m31*z2 + draw.m32*z3 + draw.m33*z4;

    vertexImpl(x0, y0, z0, 0, 0, CURVE_VERTEX);
    for (int j = 0; j < curveDetail; j++) {
      x0 += xplot1; xplot1 += xplot2; xplot2 += xplot3;
      y0 += yplot1; yplot1 += yplot2; yplot2 += yplot3;
      z0 += zplot1; zplot1 += zplot2; zplot2 += zplot3;
      vertexImpl(x0, y0, z0, 0, 0, CURVE_VERTEX);
    }
  }  
  
  
  ///////////////////////////////////////////////////////////  
  
  //
  
  // Methods to access tessellated data. Intended to use by libraries.
  
  
  public int firstFillVertex() {
    return tess.firstFillVertex;  
  }
  
  public int lastFillVertex() {
    return tess.lastFillVertex;
  }

  public int firstLineVertex() {
    return tess.firstLineVertex;  
  }
  
  public int lastLineVertex() {
    return tess.lastLineVertex;
  }
  
  
  public FloatBuffer mapFillVertices() {
    ByteBuffer bb;
    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, root.glFillVertexBufferID);
    if (root == this) {            
      bb = getGl().glMapBuffer(GL.GL_ARRAY_BUFFER, GL2.GL_READ_WRITE);
    } else {
      bb = ogl.gl2x.glMapBufferRange(GL.GL_ARRAY_BUFFER, 3 * tess.firstFillVertex, 3 * tess.fillVertexCount, GL2.GL_READ_WRITE);  
    }
    return bb.asFloatBuffer();
  }
  
  public void unmapFillVertices() {
    getGl().glUnmapBuffer(GL.GL_ARRAY_BUFFER);
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, 0);   
  }
  


  public FloatBuffer mapLineVertices() {
    ByteBuffer bb;
    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, root.glLineVertexBufferID);
    if (root == this) {            
      bb = getGl().glMapBuffer(GL.GL_ARRAY_BUFFER, GL2.GL_READ_WRITE);
    } else {
      bb = ogl.gl2x.glMapBufferRange(GL.GL_ARRAY_BUFFER, 3 * tess.firstLineVertex, 3 * tess.lineVertexCount, GL2.GL_READ_WRITE);  
    }
    return bb.asFloatBuffer();
  }
  
  public void unmapLineVertices() {
    getGl().glUnmapBuffer(GL.GL_ARRAY_BUFFER);
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, 0);   
  }
  
  
  
  public FloatBuffer mapLineAttributes() {
    ByteBuffer bb;
    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, root.glLineAttribBufferID);
    if (root == this) {            
      bb = getGl().glMapBuffer(GL.GL_ARRAY_BUFFER, GL2.GL_READ_WRITE);
    } else {
      bb = ogl.gl2x.glMapBufferRange(GL.GL_ARRAY_BUFFER, 3 * tess.firstLineVertex, 3 * tess.lineVertexCount, GL2.GL_READ_WRITE);  
    }
    return bb.asFloatBuffer();
  }
  
  public void unmapLineAttributes() {
    getGl().glUnmapBuffer(GL.GL_ARRAY_BUFFER);
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, 0);   
  }  
  
  
  ///////////////////////////////////////////////////////////  
  
  //
  
  // Construction methods  
  
  protected void updateTesselation() {
    if (!root.tessellated) {
      root.tessellate();
      root.aggregate();        
    }
  }
 
  
  protected void tessellate() {

    if (family == GROUP) {
      for (int i = 0; i < childCount; i++) {
        PShape3D child = (PShape3D) children[i];
        child.tessellate();
      }      
    } else {   
      if (!tessellated && shapeEnded) {
        tessellator.setInGeometry(in);
        tessellator.setTessGeometry(tess);
        tessellator.setFill(fill || texture != null);
        tessellator.setStroke(stroke);
        tessellator.setStrokeWeight(strokeWeight);
        tessellator.setStrokeCap(strokeCap);
        tessellator.setStrokeJoin(strokeJoin);
        tessellator.setStrokeColor(strokeR, strokeG, strokeB, strokeA);        
        
        if (family == GEOMETRY) {
          if (kind == POINTS) {
            tessellator.tessellatePoints();    
          } else if (kind == LINES) {
            tessellator.tessellateLines();    
          } else if (kind == TRIANGLE || kind == TRIANGLES) {
            if (stroke) in.addTrianglesEdges();
            tessellator.tessellateTriangles();
          } else if (kind == TRIANGLE_FAN) {
            if (stroke) in.addTriangleFanEdges();
            tessellator.tessellateTriangleFan();
          } else if (kind == TRIANGLE_STRIP) {
            if (stroke) in.addTriangleStripEdges();
            tessellator.tessellateTriangleStrip();
          } else if (kind == QUAD || kind == QUADS) {
            if (stroke) in.addQuadsEdges();
            tessellator.tessellateQuads();
          } else if (kind == QUAD_STRIP) {
            if (stroke) in.addQuadStripEdges();
            tessellator.tessellateQuadStrip();
          } else if (kind == POLYGON) {
            if (stroke) in.addPolygonEdges(isClosed);
            tessellator.tessellatePolygon(isSolid, isClosed);
          }
        } else if (family == PRIMITIVE) {
          if (kind == POINT) {
            tessellatePoint();
          } else if (kind == LINE) {
            tessellateLine(); 
          } else if (kind == TRIANGLE) {
            tessellateTriangle();            
          } else if (kind == QUAD) {
            tessellateQuad();            
          } else if (kind == RECT) {
            tessellateRect();
          } else if (kind == ELLIPSE) {
            tessellateEllipse();
          } else if (kind == ARC) {
            tessellateArc();
          } else if (kind == BOX) {
            tessellateBox();            
          } else if (kind == SPHERE) {
            tessellateSphere();
          }
        } else if (family == PATH) {
          // TODO: Determine if this is necessary, since it is 
          // equivalent to use POLYGON with fill disabled.
        }
        
        if (texture != null && parent != null) {
          ((PShape3D)parent).addTexture(texture);
        }        
      }
    }
    
    tessellated = true;
    modified = false;
  }

  
  protected void tessellatePoint() {
    
  }
  
  
  protected void tessellateLine() {
    
  }
  
  
  protected void tessellateTriangle() {
    
  }
  
  
  protected void tessellateQuad() {
    
  }  
  
  
  protected void tessellateRect() {
    
  }
  
  
  protected void tessellateEllipse() {
    float a = params[0];
    float b = params[1];
    float c = params[2];
    float d = params[3];    

    in.generateEllipse(ellipseMode, a, b, c, d,
                       fill, fillR, fillG, fillB, fillA, 
                       stroke, strokeR, strokeG, strokeB, strokeA,
                       strokeWeight);
    
    tessellator.tessellateTriangleFan(); 
  }
  
  
  protected void tessellateArc() {
    
  }
  
  
  protected void tessellateBox() {
    // TODO: move to InGeometry
    float w = params[0];
    float h = params[1];
    float d = params[2];
        
    float x1 = -w/2f; float x2 = w/2f;
    float y1 = -h/2f; float y2 = h/2f;
    float z1 = -d/2f; float z2 = d/2f;

    // front
    normal(0, 0, 1);
    vertex(x1, y1, z1, 0, 0);
    vertex(x2, y1, z1, 1, 0);
    vertex(x2, y2, z1, 1, 1);
    vertex(x1, y2, z1, 0, 1);

    // right
    normal(1, 0, 0);
    vertex(x2, y1, z1, 0, 0);
    vertex(x2, y1, z2, 1, 0);
    vertex(x2, y2, z2, 1, 1);
    vertex(x2, y2, z1, 0, 1);

    // back
    normal(0, 0, -1);
    vertex(x2, y1, z2, 0, 0);
    vertex(x1, y1, z2, 1, 0);
    vertex(x1, y2, z2, 1, 1);
    vertex(x2, y2, z2, 0, 1);

    // left
    normal(-1, 0, 0);
    vertex(x1, y1, z2, 0, 0);
    vertex(x1, y1, z1, 1, 0);
    vertex(x1, y2, z1, 1, 1);
    vertex(x1, y2, z2, 0, 1);

    // top
    normal(0, 1, 0);
    vertex(x1, y1, z2, 0, 0);
    vertex(x2, y1, z2, 1, 0);
    vertex(x2, y1, z1, 1, 1);
    vertex(x1, y1, z1, 0, 1);

    // bottom
    normal(0, -1, 0);
    vertex(x1, y2, z1, 0, 0);
    vertex(x2, y2, z1, 1, 0);
    vertex(x2, y2, z2, 1, 1);
    vertex(x1, y2, z2, 0, 1);
    
    if (stroke) in.addQuadsEdges(); 
    tessellator.tessellateQuads();      
  }
  
  
  protected void tessellateSphere() {
    // TODO: move to InGeometry
    float r = params[0];
    int nu = ogl.sphereDetailU;
    int nv = ogl.sphereDetailV;
    
    if ((nu < 3) || (nv < 2)) {
      nu = nv = 30;
    }
 
    
    float startLat = -90;
    float startLon = 0.0f;

    float latInc = 180.0f / nu;
    float lonInc = 360.0f / nv;

    float phi1,  phi2;
    float theta1,  theta2;
    float x0, y0, z0;
    float x1, y1, z1;
    float x2, y2, z2;
    float x3, y3, z3;
    float u1, v1, u2, v2, v3;

    for (int col = 0; col < nu; col++) {
      phi1 = (startLon + col * lonInc) * DEG_TO_RAD;
      phi2 = (startLon + (col + 1) * lonInc) * DEG_TO_RAD;
      for (int row = 0; row < nv; row++) {
        theta1 = (startLat + row * latInc) * DEG_TO_RAD;
        theta2 = (startLat + (row + 1) * latInc) * DEG_TO_RAD;

        x0 = PApplet.cos(phi1) * PApplet.cos(theta1);
        x1 = PApplet.cos(phi1) * PApplet.cos(theta2);
        x2 = PApplet.cos(phi2) * PApplet.cos(theta2);
        
        y0 = PApplet.sin(theta1);
        y1 = PApplet.sin(theta2);
        y2 = PApplet.sin(theta2);
        
        z0 = PApplet.sin(phi1) * PApplet.cos(theta1);
        z1 = PApplet.sin(phi1) * PApplet.cos(theta2);
        z2 = PApplet.sin(phi2) * PApplet.cos(theta2);

        x3 = PApplet.cos(phi2) * PApplet.cos(theta1);
        y3 = PApplet.sin(theta1);            
        z3 = PApplet.sin(phi2) * PApplet.cos(theta1);
        
        u1 = PApplet.map(phi1, TWO_PI, 0, 0, 1); 
        u2 = PApplet.map(phi2, TWO_PI, 0, 0, 1);
        v1 = PApplet.map(theta1, -HALF_PI, HALF_PI, 0, 1);
        v2 = PApplet.map(theta2, -HALF_PI, HALF_PI, 0, 1);
        v3 = PApplet.map(theta1, -HALF_PI, HALF_PI, 0, 1);
        
        normal(x0, y0, z0);     
        vertex(r * x0, r * y0, r * z0, u1, v1);
   
        normal(x1, y1, z1);
        vertex(r * x1,  r * y1,  r * z1, u1, v2);

        normal(x2, y2, z2);
        vertex(r * x2, r * y2, r * z2, u2, v2);

        normal(x0, y0, z0);    
        vertex(r * x0, r * y0, r * z0, u1, v1);

        normal(x2, y2, z2);
        vertex(r * x2, r * y2, r * z2, u2, v2);
        
        normal(x3,  y3,  z3);
        vertex(r * x3,  r * y3,  r * z3,  u2,  v3);
      }
    }
    
    if (stroke) in.addTrianglesEdges();
    tessellator.tessellateTriangles();
  }
  
  
  protected void updateGeometry() {
    if (root == this && parent == null && modified) {
      // Initializing offsets
      fillVertCopyOffset = 0;
      lineVertCopyOffset = 0;
      pointVertCopyOffset = 0;
      
      updateRootGeometry();
      
      // Copying any data remaining in the caches
      if (root.fillVerticesCache != null && root.fillVerticesCache.hasData()) {
        root.copyFillVertices(root.fillVerticesCache.offset, root.fillVerticesCache.size, root.fillVerticesCache.data);
        root.fillVerticesCache.reset();
      }
      
      if (root.fillColorsCache != null && root.fillColorsCache.hasData()) {
        root.copyFillColors(root.fillColorsCache.offset, root.fillColorsCache.size, root.fillColorsCache.data);
        root.fillColorsCache.reset();
      }
      
      if (root.fillNormalsCache != null && root.fillNormalsCache.hasData()) {
        root.copyFillNormals(root.fillNormalsCache.offset, root.fillNormalsCache.size, root.fillNormalsCache.data);
        root.fillNormalsCache.reset();
      }
      
      if (root.fillTexCoordsCache != null && root.fillTexCoordsCache.hasData()) {
        root.copyFillTexCoords(root.fillTexCoordsCache.offset, root.fillTexCoordsCache.size, root.fillTexCoordsCache.data);
        root.fillTexCoordsCache.reset();
      }
      
      if (root.lineVerticesCache != null && root.lineVerticesCache.hasData()) {
        root.copyLineVertices(root.lineVerticesCache.offset, root.lineVerticesCache.size, root.lineVerticesCache.data);
        root.lineVerticesCache.reset();
      }
      
      if (root.lineColorsCache != null && root.lineColorsCache.hasData()) {
        root.copyLineColors(root.lineColorsCache.offset, root.lineColorsCache.size, root.lineColorsCache.data);
        root.lineColorsCache.reset();
      }
      
      if (root.lineNormalsCache != null && root.lineNormalsCache.hasData()) {
        root.copyLineNormals(root.lineNormalsCache.offset, root.lineNormalsCache.size, root.lineNormalsCache.data);
        root.lineNormalsCache.reset();
      }
      
      if (root.lineAttributesCache != null && root.lineAttributesCache.hasData()) {
        root.copyLineAttributes(root.lineAttributesCache.offset, root.lineAttributesCache.size, root.lineAttributesCache.data);
        root.lineAttributesCache.reset();
      }      
    
     if (root.pointVerticesCache != null && root.pointVerticesCache.hasData()) {
        root.copyPointVertices(root.pointVerticesCache.offset, root.pointVerticesCache.size, root.pointVerticesCache.data);
        root.pointVerticesCache.reset();
      }
      
      if (root.pointColorsCache != null && root.pointColorsCache.hasData()) {
        root.copyPointColors(root.pointColorsCache.offset, root.pointColorsCache.size, root.pointColorsCache.data);
        root.pointColorsCache.reset();
      }
      
      if (root.pointNormalsCache != null && root.pointNormalsCache.hasData()) {
        root.copyPointNormals(root.pointNormalsCache.offset, root.pointNormalsCache.size, root.pointNormalsCache.data);
        root.pointNormalsCache.reset();
      }
      
      if (root.pointAttributesCache != null && root.pointAttributesCache.hasData()) {
        root.copyPointAttributes(root.pointAttributesCache.offset, root.pointAttributesCache.size, root.pointAttributesCache.data);
        root.pointAttributesCache.reset();
      }        
    }
  }
  
  protected void aggregate() {
    if (root == this && parent == null) {
      // We recursively calculate the total number of vertices and indices.
      lastFillVertexOffset = 0;
      lastFillIndexOffset = 0;
      
      lastLineVertexOffset = 0;
      lastLineIndexOffset = 0;

      lastPointVertexOffset = 0;
      lastPointIndexOffset = 0;      
      
      aggregateImpl();
      
      // Now that we know, we can initialize the buffers with the correct size.
      if (0 < tess.fillVertexCount && 0 < tess.fillIndexCount) {   
        initFillBuffers(tess.fillVertexCount, tess.fillIndexCount);          
        fillVertCopyOffset = 0;
        fillIndCopyOffset = 0;
        copyFillGeometryToRoot();
      }
      
      if (0 < tess.lineVertexCount && 0 < tess.lineIndexCount) {   
        initLineBuffers(tess.lineVertexCount, tess.lineIndexCount);
        lineVertCopyOffset = 0;
        lineIndCopyOffset = 0;
        copyLineGeometryToRoot();
      }
      
      if (0 < tess.pointVertexCount && 0 < tess.pointIndexCount) {   
        initPointBuffers(tess.pointVertexCount, tess.pointIndexCount);
        pointVertCopyOffset = 0;
        pointIndCopyOffset = 0;
        copyPointGeometryToRoot();
      }      
    }
  }
  
  
  // This method is very important, as it is responsible of
  // generating the correct vertex and index values for each
  // level of the shape hierarchy.
  protected void aggregateImpl() {
    if (family == GROUP) {
      tess.reset();
      
      boolean firstGeom = true;
      boolean firstStroke = true;
      boolean firstPoint = true;
      for (int i = 0; i < childCount; i++) {
        PShape3D child = (PShape3D) children[i];
        child.aggregateImpl();

        tess.addCounts(child.tess);
        
        if (0 < child.tess.fillVertexCount) {
          if (firstGeom) {
            tess.setFirstFill(child.tess);
            firstGeom = false;
          }
          tess.setLastFill(child.tess);
        }  

        if (0 < child.tess.lineVertexCount) {
          if (firstStroke) {
            tess.setFirstLine(child.tess);
            firstStroke = false;
          }          
          tess.setLastLine(child.tess);
        }
        
        if (0 < child.tess.pointVertexCount) {
          if (firstPoint) {
            tess.setFirstPoint(child.tess);
            firstPoint = false;
          }
          tess.setLastPoint(child.tess);
        }           
      }
    } else {
      if (0 < tess.fillVertexCount) {
        root.lastFillVertexOffset = tess.setFillVertex(root.lastFillVertexOffset);
      }
      if (0 < tess.fillIndexCount) {
        root.lastFillIndexOffset = tess.setFillIndex(root.lastFillIndexOffset);
      }
            
      if (0 < tess.lineVertexCount) {
        root.lastLineVertexOffset = tess.setLineVertex(root.lastLineVertexOffset);
      }      
      if (0 < tess.lineIndexCount) {
        root.lastLineIndexOffset = tess.setLineIndex(root.lastLineIndexOffset);
      }
            
      if (0 < tess.pointVertexCount) {
        root.lastPointVertexOffset = tess.setPointVertex(root.lastPointVertexOffset);
      }
      if (0 < tess.pointIndexCount) {
        root.lastPointIndexOffset = tess.setPointIndex(root.lastPointIndexOffset);
      }      
    }
    
    hasFill = 0 < tess.fillVertexCount && 0 < tess.fillIndexCount;
    hasLines = 0 < tess.lineVertexCount && 0 < tess.lineIndexCount; 
    hasPoints = 0 < tess.pointVertexCount && 0 < tess.pointIndexCount;    
  }

  
  protected void initFillBuffers(int nvert, int nind) {
    glFillVertexBufferID = ogl.createVertexBufferObject();    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glFillVertexBufferID);    
    getGl().glBufferData(GL.GL_ARRAY_BUFFER, 3 * nvert * PGraphicsOpenGL.SIZEOF_FLOAT, null, glMode);    
    
    glFillColorBufferID = ogl.createVertexBufferObject();
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glFillColorBufferID);
    getGl().glBufferData(GL.GL_ARRAY_BUFFER, 4 * nvert * PGraphicsOpenGL.SIZEOF_FLOAT, null, glMode);    

    glFillNormalBufferID = ogl.createVertexBufferObject();
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glFillNormalBufferID);
    getGl().glBufferData(GL.GL_ARRAY_BUFFER, 3 * nvert * PGraphicsOpenGL.SIZEOF_FLOAT, null, glMode);
    
    glFillTexCoordBufferID = ogl.createVertexBufferObject();
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glFillTexCoordBufferID);
    getGl().glBufferData(GL.GL_ARRAY_BUFFER, 2 * nvert * PGraphicsOpenGL.SIZEOF_FLOAT, null, glMode);
    
    glFillIndexBufferID = ogl.createVertexBufferObject();    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glFillIndexBufferID);    
    getGl().glBufferData(GL.GL_ARRAY_BUFFER, nind * PGraphicsOpenGL.SIZEOF_INT, null, glMode);
    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
  }  
  
  
  protected void copyFillGeometryToRoot() {
    if (family == GROUP) {
      for (int i = 0; i < childCount; i++) {
        PShape3D child = (PShape3D) children[i];        
        child.copyFillGeometryToRoot();
      }    
    } else {
      if (0 < tess.fillVertexCount && 0 < tess.fillIndexCount) {        
        root.copyFillGeometry(root.fillVertCopyOffset, tess.fillVertexCount, 
                              tess.fillVertices, tess.fillColors, tess.fillNormals, tess.fillTexcoords);
        root.fillVertCopyOffset += tess.fillVertexCount;
      
        root.copyFillIndices(root.fillIndCopyOffset, tess.fillIndexCount, tess.fillIndices);
        root.fillIndCopyOffset += tess.fillIndexCount;
      }
    }
  }
  
  protected void updateRootGeometry() {
    if (family == GROUP) {
      for (int i = 0; i < childCount; i++) {
        PShape3D child = (PShape3D) children[i];        
        child.updateRootGeometry();        
      } 
    } else {
 
      if (0 < tess.fillVertexCount) {    
        if (modifiedFillVertices) {
          if (root.fillVerticesCache == null) { 
            root.fillVerticesCache = new VertexCache(3);
          }            
          root.fillVerticesCache.add(root.fillVertCopyOffset, tess.fillVertexCount, tess.fillVertices);
          modifiedFillVertices = false;
        } else if (root.fillVerticesCache != null && root.fillVerticesCache.hasData()) {
          root.copyFillVertices(root.fillVerticesCache.offset, root.fillVerticesCache.size, root.fillVerticesCache.data);
          root.fillVerticesCache.reset();
        }
        
        if (modifiedFillColors) {
          if (root.fillColorsCache == null) { 
            root.fillColorsCache = new VertexCache(4);
          }            
          root.fillColorsCache.add(root.fillVertCopyOffset, tess.fillVertexCount, tess.fillColors);
          modifiedFillColors = false;            
        } else if (root.fillColorsCache != null && root.fillColorsCache.hasData()) {
          root.copyFillColors(root.fillColorsCache.offset, root.fillColorsCache.size, root.fillColorsCache.data);
          root.fillColorsCache.reset();
        }
        
        if (modifiedFillNormals) {
          if (root.fillNormalsCache == null) { 
            root.fillNormalsCache = new VertexCache(3);
          }            
          root.fillNormalsCache.add(root.fillVertCopyOffset, tess.fillVertexCount, tess.fillNormals);            
          modifiedFillNormals = false;            
        } else if (root.fillNormalsCache != null && root.fillNormalsCache.hasData()) {
          root.copyFillNormals(root.fillNormalsCache.offset, root.fillNormalsCache.size, root.fillNormalsCache.data);
          root.fillNormalsCache.reset();
        }
        
        if (modifiedFillTexCoords) {
          if (root.fillTexCoordsCache == null) { 
            root.fillTexCoordsCache = new VertexCache(2);
          }            
          root.fillTexCoordsCache.add(root.fillVertCopyOffset, tess.fillVertexCount, tess.fillTexcoords);            
          modifiedFillTexCoords = false;
        } else if (root.fillTexCoordsCache != null && root.fillTexCoordsCache.hasData()) {
          root.copyFillTexCoords(root.fillTexCoordsCache.offset, root.fillTexCoordsCache.size, root.fillTexCoordsCache.data);
          root.fillTexCoordsCache.reset();
        } 
      } 
      
      if (0 < tess.lineVertexCount) {
        if (modifiedLineVertices) {
          if (root.lineVerticesCache == null) { 
            root.lineVerticesCache = new VertexCache(3);
          }            
          root.lineVerticesCache.add(root.lineVertCopyOffset, tess.lineVertexCount, tess.lineVertices);
          modifiedLineVertices = false;
        } else if (root.lineVerticesCache != null && root.lineVerticesCache.hasData()) {
          root.copyLineVertices(root.lineVerticesCache.offset, root.lineVerticesCache.size, root.lineVerticesCache.data);
          root.lineVerticesCache.reset();
        }
        
        if (modifiedLineColors) {
          if (root.lineColorsCache == null) { 
            root.lineColorsCache = new VertexCache(4);
          }            
          root.lineColorsCache.add(root.lineVertCopyOffset, tess.lineVertexCount, tess.lineColors);
          modifiedLineColors = false;            
        } else if (root.lineColorsCache != null && root.lineColorsCache.hasData()) {
          root.copyLineColors(root.lineColorsCache.offset, root.lineColorsCache.size, root.lineColorsCache.data);
          root.lineColorsCache.reset();
        }
        
        if (modifiedLineNormals) {
          if (root.lineNormalsCache == null) { 
            root.lineNormalsCache = new VertexCache(3);
          }            
          root.lineNormalsCache.add(root.lineVertCopyOffset, tess.lineVertexCount, tess.lineNormals);            
          modifiedLineNormals = false;
        } else if (root.lineNormalsCache != null && root.lineNormalsCache.hasData()) {
          root.copyLineNormals(root.lineNormalsCache.offset, root.lineNormalsCache.size, root.lineNormalsCache.data);
          root.lineNormalsCache.reset();
        }
        
        if (modifiedLineAttributes) {
          if (root.lineAttributesCache == null) { 
            root.lineAttributesCache = new VertexCache(4);
          }            
          root.lineAttributesCache.add(root.lineVertCopyOffset, tess.lineVertexCount, tess.lineAttributes);            
          modifiedLineAttributes = false;
        } else if (root.lineAttributesCache != null && root.lineAttributesCache.hasData()) {
          root.copyLineAttributes(root.lineAttributesCache.offset, root.lineAttributesCache.size, root.lineAttributesCache.data);
          root.lineAttributesCache.reset();
        }      
      }

      if (0 < tess.pointVertexCount) {
        if (modifiedPointVertices) {
          if (root.pointVerticesCache == null) { 
            root.pointVerticesCache = new VertexCache(3);
          }            
          root.pointVerticesCache.add(root.pointVertCopyOffset, tess.pointVertexCount, tess.pointVertices);
          modifiedPointVertices = false;
        } else if (root.pointVerticesCache != null && root.pointVerticesCache.hasData()) {
          root.copyPointVertices(root.pointVerticesCache.offset, root.pointVerticesCache.size, root.pointVerticesCache.data);
          root.pointVerticesCache.reset();
        }
        
        if (modifiedPointColors) {
          if (root.pointColorsCache == null) { 
            root.pointColorsCache = new VertexCache(4);
          }            
          root.pointColorsCache.add(root.pointVertCopyOffset, tess.pointVertexCount, tess.pointColors);
          modifiedPointColors = false;            
        } else if (root.pointColorsCache != null && root.pointColorsCache.hasData()) {
          root.copyPointColors(root.pointColorsCache.offset, root.pointColorsCache.size, root.pointColorsCache.data);
          root.pointColorsCache.reset();
        }
        
        if (modifiedPointNormals) {
          if (root.pointNormalsCache == null) { 
            root.pointNormalsCache = new VertexCache(3);
          }            
          root.pointNormalsCache.add(root.pointVertCopyOffset, tess.pointVertexCount, tess.pointNormals);            
          modifiedPointNormals = false;
        } else if (root.pointNormalsCache != null && root.pointNormalsCache.hasData()) {
          root.copyPointNormals(root.pointNormalsCache.offset, root.pointNormalsCache.size, root.pointNormalsCache.data);
          root.pointNormalsCache.reset();
        }
        
        if (modifiedPointAttributes) {
          if (root.pointAttributesCache == null) { 
            root.pointAttributesCache = new VertexCache(3);
          }            
          root.pointAttributesCache.add(root.pointVertCopyOffset, tess.pointVertexCount, tess.pointAttributes);            
          modifiedPointAttributes = false;
        } else if (root.pointAttributesCache != null && root.pointAttributesCache.hasData()) {
          root.copyPointAttributes(root.pointAttributesCache.offset, root.pointAttributesCache.size, root.pointAttributesCache.data);
          root.pointAttributesCache.reset();
        }        
      }
      
      root.fillVertCopyOffset += tess.fillVertexCount;
      root.lineVertCopyOffset += tess.lineVertexCount;
      root.pointVertCopyOffset += tess.pointVertexCount;      
    }
    
    modified = false;
  }
    
  
  protected void copyFillGeometry(int offset, int size, 
                                  float[] vertices, float[] colors, 
                                  float[] normals, float[] texcoords) {
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glFillVertexBufferID);    
    getGl().glBufferSubData(GL.GL_ARRAY_BUFFER, 3 * offset * PGraphicsOpenGL.SIZEOF_FLOAT, 
                            3 * size * PGraphicsOpenGL.SIZEOF_FLOAT, FloatBuffer.wrap(vertices));
    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glFillColorBufferID);
    getGl().glBufferSubData(GL.GL_ARRAY_BUFFER, 4 * offset * PGraphicsOpenGL.SIZEOF_FLOAT, 
                            4 * size * PGraphicsOpenGL.SIZEOF_FLOAT, FloatBuffer.wrap(colors));   
    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glFillNormalBufferID);
    getGl().glBufferSubData(GL.GL_ARRAY_BUFFER, 3 * offset * PGraphicsOpenGL.SIZEOF_FLOAT, 
                            3 * size * PGraphicsOpenGL.SIZEOF_FLOAT, FloatBuffer.wrap(normals));
    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glFillTexCoordBufferID);
    getGl().glBufferSubData(GL.GL_ARRAY_BUFFER, 2 * offset * PGraphicsOpenGL.SIZEOF_FLOAT, 
                            2 * size * PGraphicsOpenGL.SIZEOF_FLOAT, FloatBuffer.wrap(texcoords));
    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
  }

  
  protected void copyFillVertices(int offset, int size, float[] vertices) {
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glFillVertexBufferID);    
    getGl().glBufferSubData(GL.GL_ARRAY_BUFFER, 3 * offset * PGraphicsOpenGL.SIZEOF_FLOAT, 
                            3 * size * PGraphicsOpenGL.SIZEOF_FLOAT, FloatBuffer.wrap(vertices));
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
  }
  
  
  protected void copyFillColors(int offset, int size, float[] colors) {
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glFillColorBufferID);
    getGl().glBufferSubData(GL.GL_ARRAY_BUFFER, 4 * offset * PGraphicsOpenGL.SIZEOF_FLOAT, 
                            4 * size * PGraphicsOpenGL.SIZEOF_FLOAT, FloatBuffer.wrap(colors));      
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
  }  
  
  
  protected void copyFillNormals(int offset, int size, float[] normals) {
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glFillNormalBufferID);
    getGl().glBufferSubData(GL.GL_ARRAY_BUFFER, 3 * offset * PGraphicsOpenGL.SIZEOF_FLOAT, 
                            3 * size * PGraphicsOpenGL.SIZEOF_FLOAT, FloatBuffer.wrap(normals));
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
  }  

  
  protected void copyFillTexCoords(int offset, int size, float[] texcoords) {
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glFillTexCoordBufferID);
    getGl().glBufferSubData(GL.GL_ARRAY_BUFFER, 2 * offset * PGraphicsOpenGL.SIZEOF_FLOAT, 
                            2 * size * PGraphicsOpenGL.SIZEOF_FLOAT, FloatBuffer.wrap(texcoords));
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
  }   
  
  
  protected void copyFillIndices(int offset, int size, int[] indices) {
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glFillIndexBufferID);
    getGl().glBufferSubData(GL.GL_ARRAY_BUFFER, offset * PGraphicsOpenGL.SIZEOF_INT, 
                            size * PGraphicsOpenGL.SIZEOF_INT, IntBuffer.wrap(indices));
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, 0);    
  }
  
  
  protected void initLineBuffers(int nvert, int nind) {
    glLineVertexBufferID = ogl.createVertexBufferObject();    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glLineVertexBufferID);    
    getGl().glBufferData(GL.GL_ARRAY_BUFFER, 3 * nvert * PGraphicsOpenGL.SIZEOF_FLOAT, null, glMode);    
    
    glLineColorBufferID = ogl.createVertexBufferObject();
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glLineColorBufferID);
    getGl().glBufferData(GL.GL_ARRAY_BUFFER, 4 * nvert * PGraphicsOpenGL.SIZEOF_FLOAT, null, glMode);

    glLineNormalBufferID = ogl.createVertexBufferObject();    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glLineNormalBufferID);    
    getGl().glBufferData(GL.GL_ARRAY_BUFFER, 3 * nvert * PGraphicsOpenGL.SIZEOF_FLOAT, null, glMode);
    
    glLineAttribBufferID = ogl.createVertexBufferObject();
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glLineAttribBufferID);   
    getGl().glBufferData(GL.GL_ARRAY_BUFFER, 4 * nvert * PGraphicsOpenGL.SIZEOF_FLOAT, null, glMode);
    
    glLineIndexBufferID = ogl.createVertexBufferObject();    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glLineIndexBufferID);    
    getGl().glBufferData(GL.GL_ARRAY_BUFFER, nind * PGraphicsOpenGL.SIZEOF_INT, null, glMode);
    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, 0);         
  }
  
  
  protected void copyLineGeometryToRoot() {
    if (family == GROUP) {
      for (int i = 0; i < childCount; i++) {
        PShape3D child = (PShape3D) children[i];
        child.copyLineGeometryToRoot();
      }    
    } else {
      if (hasLines) {
        root.copyLineGeometry(root.lineVertCopyOffset, tess.lineVertexCount, 
                              tess.lineVertices, tess.lineColors, tess.lineNormals, tess.lineAttributes);        
        root.lineVertCopyOffset += tess.lineVertexCount;
        
        root.copyLineIndices(root.lineIndCopyOffset, tess.lineIndexCount, tess.lineIndices);
        root.lineIndCopyOffset += tess.lineIndexCount;        
      }
    }    
  }

  
  protected void copyLineGeometry(int offset, int size, 
                                  float[] vertices, float[] colors, float[] normals, float[] attribs) {
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glLineVertexBufferID);
    getGl().glBufferSubData(GL.GL_ARRAY_BUFFER, 3 * offset * PGraphicsOpenGL.SIZEOF_FLOAT, 
                            3 * size * PGraphicsOpenGL.SIZEOF_FLOAT, FloatBuffer.wrap(vertices));

    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glLineColorBufferID);
    getGl().glBufferSubData(GL.GL_ARRAY_BUFFER, 4 * offset * PGraphicsOpenGL.SIZEOF_FLOAT, 
                            4 * size * PGraphicsOpenGL.SIZEOF_FLOAT, FloatBuffer.wrap(colors));
    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glLineNormalBufferID);
    getGl().glBufferSubData(GL.GL_ARRAY_BUFFER, 3 * offset * PGraphicsOpenGL.SIZEOF_FLOAT, 
                            3 * size * PGraphicsOpenGL.SIZEOF_FLOAT, FloatBuffer.wrap(normals));
    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glLineAttribBufferID);
    getGl().glBufferSubData(GL.GL_ARRAY_BUFFER, 4 * offset * PGraphicsOpenGL.SIZEOF_FLOAT, 
                            4 * size * PGraphicsOpenGL.SIZEOF_FLOAT, FloatBuffer.wrap(attribs));
    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
  }    
  
  
  protected void copyLineVertices(int offset, int size, float[] vertices) {
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glLineVertexBufferID);
    getGl().glBufferSubData(GL.GL_ARRAY_BUFFER, 3 * offset * PGraphicsOpenGL.SIZEOF_FLOAT, 
                            3 * size * PGraphicsOpenGL.SIZEOF_FLOAT, FloatBuffer.wrap(vertices));
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
  }     
  
  
  protected void copyLineColors(int offset, int size, float[] colors) {
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glLineColorBufferID);
    getGl().glBufferSubData(GL.GL_ARRAY_BUFFER, 4 * offset * PGraphicsOpenGL.SIZEOF_FLOAT, 
                            4 * size * PGraphicsOpenGL.SIZEOF_FLOAT, FloatBuffer.wrap(colors));
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, 0);    
  }
  
  
  protected void copyLineNormals(int offset, int size, float[] normals) {
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glLineNormalBufferID);
    getGl().glBufferSubData(GL.GL_ARRAY_BUFFER, 3 * offset * PGraphicsOpenGL.SIZEOF_FLOAT, 
                            3 * size * PGraphicsOpenGL.SIZEOF_FLOAT, FloatBuffer.wrap(normals));
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
  }

  
  protected void copyLineAttributes(int offset, int size, float[] attribs) {
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glLineAttribBufferID);
    getGl().glBufferSubData(GL.GL_ARRAY_BUFFER, 4 * offset * PGraphicsOpenGL.SIZEOF_FLOAT, 
                            4 * size * PGraphicsOpenGL.SIZEOF_FLOAT, FloatBuffer.wrap(attribs));    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, 0);    
  }
  
  
  protected void copyLineIndices(int offset, int size, int[] indices) {
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glLineIndexBufferID);
    getGl().glBufferSubData(GL.GL_ARRAY_BUFFER, offset * PGraphicsOpenGL.SIZEOF_INT, 
                            size * PGraphicsOpenGL.SIZEOF_INT, IntBuffer.wrap(indices));
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, 0);   
  }  
  

  protected void initPointBuffers(int nvert, int nind) {
    glPointVertexBufferID = ogl.createVertexBufferObject();    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glPointVertexBufferID);    
    getGl().glBufferData(GL.GL_ARRAY_BUFFER, 3 * nvert * PGraphicsOpenGL.SIZEOF_FLOAT, null, glMode);    

    glPointColorBufferID = ogl.createVertexBufferObject();
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glPointColorBufferID);
    getGl().glBufferData(GL.GL_ARRAY_BUFFER, 4 * nvert * PGraphicsOpenGL.SIZEOF_FLOAT, null, glMode);    
    
    glPointNormalBufferID = ogl.createVertexBufferObject();    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glPointNormalBufferID);    
    getGl().glBufferData(GL.GL_ARRAY_BUFFER, 3 * nvert * PGraphicsOpenGL.SIZEOF_FLOAT, null, glMode);    

    glPointAttribBufferID = ogl.createVertexBufferObject();
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glPointAttribBufferID);   
    getGl().glBufferData(GL.GL_ARRAY_BUFFER, 2 * nvert * PGraphicsOpenGL.SIZEOF_FLOAT, null, glMode);
    
    glPointIndexBufferID = ogl.createVertexBufferObject();    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glPointIndexBufferID);    
    getGl().glBufferData(GL.GL_ARRAY_BUFFER, nind * PGraphicsOpenGL.SIZEOF_INT, null, glMode);
    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, 0);         
  }  
  
  
  protected void copyPointGeometryToRoot() {
    if (family == GROUP) {
      for (int i = 0; i < childCount; i++) {
        PShape3D child = (PShape3D) children[i];
        child.copyPointGeometryToRoot();
      }    
    } else {
      if (hasPoints) {
        root.copyPointGeometry(root.pointVertCopyOffset, tess.pointVertexCount, 
                               tess.pointVertices, tess.pointColors, tess.pointNormals, tess.pointAttributes);        
        root.pointVertCopyOffset += tess.pointVertexCount;
        
        root.copyPointIndices(root.pointIndCopyOffset, tess.pointIndexCount, tess.pointIndices);
        root.pointIndCopyOffset += tess.pointIndexCount;        
      }
    }
  }
  
  
  protected void copyPointGeometry(int offset, int size, 
                                   float[] vertices, float[] colors, float[] normals, float[] attribs) {
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glPointVertexBufferID);
    getGl().glBufferSubData(GL.GL_ARRAY_BUFFER, 3 * offset * PGraphicsOpenGL.SIZEOF_FLOAT, 
                            3 * size * PGraphicsOpenGL.SIZEOF_FLOAT, FloatBuffer.wrap(vertices));

    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glPointColorBufferID);
    getGl().glBufferSubData(GL.GL_ARRAY_BUFFER, 4 * offset * PGraphicsOpenGL.SIZEOF_FLOAT, 
                            4 * size * PGraphicsOpenGL.SIZEOF_FLOAT, FloatBuffer.wrap(colors));
    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glPointNormalBufferID);
    getGl().glBufferSubData(GL.GL_ARRAY_BUFFER, 3 * offset * PGraphicsOpenGL.SIZEOF_FLOAT, 
                            3 * size * PGraphicsOpenGL.SIZEOF_FLOAT, FloatBuffer.wrap(normals));
    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glPointAttribBufferID);
    getGl().glBufferSubData(GL.GL_ARRAY_BUFFER, 2 * offset * PGraphicsOpenGL.SIZEOF_FLOAT, 
                            2 * size * PGraphicsOpenGL.SIZEOF_FLOAT, FloatBuffer.wrap(attribs));
    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
  }  


  protected void copyPointVertices(int offset, int size, float[] vertices) {
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glPointVertexBufferID);
    getGl().glBufferSubData(GL.GL_ARRAY_BUFFER, 3 * offset * PGraphicsOpenGL.SIZEOF_FLOAT, 
                            3 * size * PGraphicsOpenGL.SIZEOF_FLOAT, FloatBuffer.wrap(vertices));
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, 0);    
  }
    
    
  protected void copyPointColors(int offset, int size, float[] colors) {
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glPointColorBufferID);
    getGl().glBufferSubData(GL.GL_ARRAY_BUFFER, 4 * offset * PGraphicsOpenGL.SIZEOF_FLOAT, 
                            4 * size * PGraphicsOpenGL.SIZEOF_FLOAT, FloatBuffer.wrap(colors));
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, 0);    
  }
    
  
  protected void copyPointNormals(int offset, int size, float[] normals) {
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glPointNormalBufferID);
    getGl().glBufferSubData(GL.GL_ARRAY_BUFFER, 3 * offset * PGraphicsOpenGL.SIZEOF_FLOAT, 
                            3 * size * PGraphicsOpenGL.SIZEOF_FLOAT, FloatBuffer.wrap(normals));
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, 0);    
  }

    
  protected void copyPointAttributes(int offset, int size, float[] attribs) {
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glPointAttribBufferID);
    getGl().glBufferSubData(GL.GL_ARRAY_BUFFER, 2 * offset * PGraphicsOpenGL.SIZEOF_FLOAT, 
                            2 * size * PGraphicsOpenGL.SIZEOF_FLOAT, FloatBuffer.wrap(attribs));    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, 0);    
  }
  
  
  protected void copyPointIndices(int offset, int size, int[] indices) {
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, glPointIndexBufferID);
    getGl().glBufferSubData(GL.GL_ARRAY_BUFFER, offset * PGraphicsOpenGL.SIZEOF_INT, 
                            size * PGraphicsOpenGL.SIZEOF_INT, IntBuffer.wrap(indices));
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, 0);   
  }    

  
  ///////////////////////////////////////////////////////////  
  
  //
  
  // Deletion methods

  
  protected void release() {
    deleteFillBuffers();
    deleteLineBuffers();
    deletePointBuffers();
  }  
  
  protected void deleteFillBuffers() {
    if (glFillVertexBufferID != 0) {    
      ogl.deleteVertexBufferObject(glFillVertexBufferID);   
      glFillVertexBufferID = 0;
    }    
    
    if (glFillColorBufferID != 0) {    
      ogl.deleteVertexBufferObject(glFillColorBufferID);   
      glFillColorBufferID = 0;
    }    

    if (glFillNormalBufferID != 0) {    
      ogl.deleteVertexBufferObject(glFillNormalBufferID);   
      glFillNormalBufferID = 0;
    }     

    if (glFillTexCoordBufferID != 0) {    
      ogl.deleteVertexBufferObject(glFillTexCoordBufferID);   
      glFillTexCoordBufferID = 0;
    }    
    
    if (glFillIndexBufferID != 0) {    
      ogl.deleteVertexBufferObject(glFillIndexBufferID);   
      glFillIndexBufferID = 0;
    }   
  }
  
  protected void deleteLineBuffers() {
    if (glLineVertexBufferID != 0) {    
      ogl.deleteVertexBufferObject(glLineVertexBufferID);   
      glLineVertexBufferID = 0;
    }    
    
    if (glLineColorBufferID != 0) {    
      ogl.deleteVertexBufferObject(glLineColorBufferID);   
      glLineColorBufferID = 0;
    }    

    if (glLineNormalBufferID != 0) {    
      ogl.deleteVertexBufferObject(glLineNormalBufferID);   
      glLineNormalBufferID = 0;
    }     

    if (glLineAttribBufferID != 0) {    
      ogl.deleteVertexBufferObject(glLineAttribBufferID);   
      glLineAttribBufferID = 0;
    }    
    
    if (glLineIndexBufferID != 0) {    
      ogl.deleteVertexBufferObject(glLineIndexBufferID);   
      glLineIndexBufferID = 0;
    }  
  }  
  
  protected void deletePointBuffers() {
    if (glPointVertexBufferID != 0) {    
      ogl.deleteVertexBufferObject(glPointVertexBufferID);   
      glPointVertexBufferID = 0;
    }    
    
    if (glPointColorBufferID != 0) {    
      ogl.deleteVertexBufferObject(glPointColorBufferID);   
      glPointColorBufferID = 0;
    }    

    if (glPointNormalBufferID != 0) {    
      ogl.deleteVertexBufferObject(glPointNormalBufferID);   
      glPointNormalBufferID = 0;
    }     

    if (glPointAttribBufferID != 0) {    
      ogl.deleteVertexBufferObject(glPointAttribBufferID);   
      glPointAttribBufferID = 0;
    }    
    
    if (glPointIndexBufferID != 0) {    
      ogl.deleteVertexBufferObject(glPointIndexBufferID);   
      glPointIndexBufferID = 0;
    }  
  }
  
  ///////////////////////////////////////////////////////////  
  
  //
  
  // Rendering methods
  
  public void draw() {
    draw(ogl);
  }
  
  
  public void draw(PGraphics g) {
    if (visible) {
      
      updateTesselation();      
      updateGeometry();
      
      if (matrix != null && applyMatrix) {
        g.pushMatrix();
        g.applyMatrix(matrix);
      }
    
      if (family == GROUP) {
        
        boolean matrixBelow = childHasMatrix;
        boolean diffTexBelow = textures != null && 1 < textures.size();
        
        if (matrixBelow || diffTexBelow) {
          // Some child shape below this group has a non-null matrix
          // transformation assigned to it, so the group cannot
          // be drawn in a single render call.
          // Or, some child shapes below this group use different
          // texture maps, so they cannot rendered in a single call
          // either.
          
          for (int i = 0; i < childCount; i++) {
            ((PShape3D) children[i]).draw(g);
          }        
        } else {
          // None of the child shapes below this group has a matrix
          // transformation applied to them, so we can render everything
          // in a single block.
          // And all have the same texture applied to them.          
          PImage tex = null;
          if (textures != null && textures.size() == 1) {
            tex = (PImage)textures.toArray()[0];
          }
          render(tex);
        }
              
      } else {
        render(texture);
      }
    
      if (matrix != null) {
        g.popMatrix();
      } 
    }
  }

  
  // Render the geometry stored in the root shape as VBOs, for the vertices 
  // corresponding to this shape. Sometimes we can have root == this.
  protected void render(PImage texture) {
    if (root == null) {
      // Some error. Root should never be null. At least it should be this.
      return; 
    }

    if (hasPoints) {
      renderPoints();
    }
    
    if (hasLines) {    
      renderLines();    
    }    
    
    if (hasFill) { 
      renderFill(texture);
    }
  }


  protected void renderPoints() {
    ogl.startPointShader();
    
    getGl().glEnableClientState(GL2.GL_NORMAL_ARRAY);
    getGl().glEnableClientState(GL2.GL_COLOR_ARRAY);
    getGl().glEnableClientState(GL2.GL_VERTEX_ARRAY);
    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, root.glPointNormalBufferID);
    getGl().glNormalPointer(GL.GL_FLOAT, 0, 0);
              
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, root.glPointColorBufferID);
    getGl().glColorPointer(4, GL.GL_FLOAT, 0, 0);
    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, root.glPointVertexBufferID);
    getGl().glVertexPointer(3, GL.GL_FLOAT, 0, 0);
    
    ogl.setupPointShader(root.glPointAttribBufferID);
    
    getGl().glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, root.glPointIndexBufferID);    
    getGl().glDrawElements(GL.GL_TRIANGLES, tess.lastPointIndex - tess.firstPointIndex + 1, GL.GL_UNSIGNED_INT, 
                           tess.firstPointIndex * PGraphicsOpenGL.SIZEOF_INT);
        
    getGl().glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0);
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, 0);    
    
    getGl().glDisableClientState(GL2.GL_VERTEX_ARRAY);
    getGl().glDisableClientState(GL2.GL_COLOR_ARRAY);
    getGl().glDisableClientState(GL2.GL_NORMAL_ARRAY);
    
    ogl.stopPointShader();
  }  


  protected void renderLines() {
    ogl.startLineShader();
    
    getGl().glEnableClientState(GL2.GL_NORMAL_ARRAY);
    getGl().glEnableClientState(GL2.GL_COLOR_ARRAY);
    getGl().glEnableClientState(GL2.GL_VERTEX_ARRAY);
    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, root.glLineNormalBufferID);
    getGl().glNormalPointer(GL.GL_FLOAT, 0, 0);
              
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, root.glLineColorBufferID);
    getGl().glColorPointer(4, GL.GL_FLOAT, 0, 0);
                    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, root.glLineVertexBufferID);
    getGl().glVertexPointer(3, GL.GL_FLOAT, 0, 0);
    
    ogl.setupLineShader(root.glLineAttribBufferID);
    
    getGl().glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, root.glLineIndexBufferID);    
    getGl().glDrawElements(GL.GL_TRIANGLES, tess.lastLineIndex - tess.firstLineIndex + 1, GL.GL_UNSIGNED_INT, 
                           tess.firstLineIndex * PGraphicsOpenGL.SIZEOF_INT);
    
    getGl().glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0);
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, 0);    
    
    getGl().glDisableClientState(GL2.GL_VERTEX_ARRAY);
    getGl().glDisableClientState(GL2.GL_COLOR_ARRAY);
    getGl().glDisableClientState(GL2.GL_NORMAL_ARRAY);
    
    ogl.stopLineShader();    
  }  
  
  
  protected void renderFill(PImage textureImage) {
    getGl().glEnableClientState(GL2.GL_NORMAL_ARRAY);
    getGl().glEnableClientState(GL2.GL_COLOR_ARRAY);
    getGl().glEnableClientState(GL2.GL_VERTEX_ARRAY);
    getGl().glEnableClientState(GL2.GL_TEXTURE_COORD_ARRAY);
    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, root.glFillNormalBufferID);
    getGl().glNormalPointer(GL.GL_FLOAT, 0, 0);    
    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, root.glFillColorBufferID);
    getGl().glColorPointer(4, GL.GL_FLOAT, 0, 0);
    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, root.glFillVertexBufferID);
    getGl().glVertexPointer(3, GL.GL_FLOAT, 0, 0);    
    
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, root.glFillTexCoordBufferID);
    getGl().glTexCoordPointer(2, GL.GL_FLOAT, 0, 0);    
    
    PTexture tex = null;
    if (textureImage != null) {
      tex = ogl.getTexture(textureImage);
      if (tex != null) {
        getGl().glEnable(tex.glTarget);
        getGl().glActiveTexture(GL.GL_TEXTURE0);
        getGl().glBindTexture(tex.glTarget, tex.glID);
      }
    }
    
    getGl().glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, root.glFillIndexBufferID);    
    getGl().glDrawElements(GL.GL_TRIANGLES, tess.lastFillIndex - tess.firstFillIndex + 1, GL.GL_UNSIGNED_INT, 
                           tess.firstFillIndex * PGraphicsOpenGL.SIZEOF_INT);              
    
    if (tex != null) {
      getGl().glActiveTexture(GL.GL_TEXTURE0);
      getGl().glBindTexture(tex.glTarget, 0);
      getGl().glDisable(tex.glTarget);
    } 
    
    getGl().glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0);
    getGl().glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
    
    getGl().glDisableClientState(GL2.GL_VERTEX_ARRAY);
    getGl().glDisableClientState(GL2.GL_TEXTURE_COORD_ARRAY);
    getGl().glDisableClientState(GL2.GL_COLOR_ARRAY);
    getGl().glDisableClientState(GL2.GL_NORMAL_ARRAY);     
  }
  
  ///////////////////////////////////////////////////////////  
  
  // 
  
  // Internal class to store a cache of vertex data used to copy data
  // to the VBOs with fewer calls.
  protected class VertexCache {
    int ncoords;
    int offset;
    int size;    
    float[] data;
    
    VertexCache(int ncoords) {
      this.ncoords = ncoords;
      this.data = new float[ncoords * DEFAULT_CACHE_SIZE];
      this.offset = 0;
      this.size = 0;      
    }
    
    void reset() {
      offset = 0;
      size = 0;
    }    
    
    void add(int dataOffset, int dataSize, float[] newData) {      
      if (size == 0) {
        offset = dataOffset;
      }
      
      int oldSize = data.length / ncoords;
      if (size + dataSize >= oldSize) {
        int newSize = expandSize(oldSize, size + dataSize);        
        expand(newSize);
      }
      
      if (dataSize <= PGraphicsOpenGL.MIN_ARRAYCOPY_SIZE) {
        // Copying elements one by one instead of using arrayCopy is more efficient for
        // few vertices...
        for (int i = 0; i < dataSize; i++) {
          int srcIndex = ncoords * i;
          int destIndex = ncoords * (size + i);
          
          if (ncoords == 2) {
            data[destIndex++] = newData[srcIndex++];
            data[destIndex  ] = newData[srcIndex  ];
          } else if (ncoords == 3) {
            data[destIndex++] = newData[srcIndex++];
            data[destIndex++] = newData[srcIndex++];
            data[destIndex  ] = newData[srcIndex  ];
          } else if (ncoords == 4) {
            data[destIndex++] = newData[srcIndex++];
            data[destIndex++] = newData[srcIndex++];
            data[destIndex++] = newData[srcIndex++];
            data[destIndex  ] = newData[srcIndex  ];            
          } else {
            for (int j = 0; j < ncoords; j++) {
              data[destIndex++] = newData[srcIndex++];
            }            
          }
        }
      } else {
        PApplet.arrayCopy(newData, 0, data, ncoords * size, ncoords * dataSize);
      }
      
      size += dataSize;
    } 
    
    void expand(int n) {
      float temp[] = new float[ncoords * n];      
      PApplet.arrayCopy(data, 0, temp, 0, ncoords * size);
      data = temp;      
    }
    
    int expandSize(int currSize, int newMinSize) {
      int newSize = currSize; 
      while (newSize < newMinSize) {
        newSize = newSize << 1;
      }
      return newSize;
    }    
    
    boolean hasData() {
      return 0 < size;
    }
    
  }  
  
  ///////////////////////////////////////////////////////////  
  
  //
  
  // Utilities methods  
  
  protected GL2ES1 getGl() {
    return ogl.gl2f;
  }  
}


