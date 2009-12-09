/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2005-08 Ben Fry and Casey Reas

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation; either
  version 2.1 of the License, or (at your option) any later version.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

package processing.android.core;

import android.graphics.*;
import android.graphics.Bitmap.Config;
import android.graphics.Paint.Style;


/**
 * Subclass for PGraphics that implements the graphics API using Java2D.
 *
 * <p>Pixel operations too slow? As of release 0085 (the first beta),
 * the default renderer uses Java2D. It's more accurate than the renderer
 * used in alpha releases of Processing (it handles stroke caps and joins,
 * and has better polygon tessellation), but it's super slow for handling
 * pixels. At least until we get a chance to get the old 2D renderer
 * (now called P2D) working in a similar fashion, you can use
 * <TT>size(w, h, P3D)</TT> instead of <TT>size(w, h)</TT> which will
 * be faster for general pixel flipping madness. </p>
 *
 * <p>To get access to the Java 2D "Graphics2D" object for the default
 * renderer, use:
 * <PRE>Graphics2D g2 = ((PGraphicsJava2D)g).g2;</PRE>
 * This will let you do Java 2D stuff directly, but is not supported in
 * any way shape or form. Which just means "have fun, but don't complain
 * if it breaks."</p>
 */
public class PGraphicsAndroid2D extends PGraphics {

  Canvas canvas;  // like g2 for PGraphicsJava2D

  /// break the shape at the next vertex (next vertex() call is a moveto())
  boolean breakShape;

  /// coordinates for internal curve calculation
  float[] curveCoordX;
  float[] curveCoordY;
  float[] curveDrawX;
  float[] curveDrawY;

//  int transformCount;
//  Matrix[] transformStack;
  float[] transform;

//  Line2D.Float line = new Line2D.Float();
//  Ellipse2D.Float ellipse = new Ellipse2D.Float();
//  Rectangle2D.Float rect = new Rectangle2D.Float();
//  Arc2D.Float arc = new Arc2D.Float();
  Path path;
  RectF rect;

//  protected Color tintColorObject;

//  protected Color fillColorObject;
//  public boolean fillGradient;
//  public Paint fillGradientObject;

//  protected Color strokeColorObject;
//  public boolean strokeGradient;
//  public Paint strokeGradientObject;

  Paint fillPaint;
  Paint strokePaint;
  Paint tintPaint;


  //////////////////////////////////////////////////////////////

  // INTERNAL


  public PGraphicsAndroid2D() {
//    transformStack = new Matrix[MATRIX_STACK_DEPTH];
//    transform = new float[6];
    transform = new float[9];

    path = new Path();
    rect = new RectF();

    fillPaint = new Paint();
    fillPaint.setStyle(Style.FILL);
    strokePaint = new Paint();
    strokePaint.setStyle(Style.STROKE);
  }


  //public void setParent(PApplet parent)


  //public void setPrimary(boolean primary)


  //public void setPath(String path)


  /**
   * Called in response to a resize event, handles setting the
   * new width and height internally, as well as re-allocating
   * the pixel buffer for the new size.
   *
   * Note that this will nuke any cameraMode() settings.
   */
  public void setSize(int iwidth, int iheight) {  // ignore
    width = iwidth;
    height = iheight;
    width1 = width - 1;
    height1 = height - 1;

    allocate();
    reapplySettings();
  }


  protected void allocate() {
    image = Bitmap.createBitmap(width, height, Config.ARGB_8888);
    canvas = new Canvas(image);
//    image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
//    canvas = (Graphics2D) image.getGraphics();
  }


  public void dispose() {
    // called when paused.
  }



  //////////////////////////////////////////////////////////////

  // FRAME


  public boolean canDraw() {
    return true;
  }


  public void requestDraw() {
    parent.handleDraw();
  }


  public void beginDraw() {
//    if (primarySurface) {
//      canvas = parent.getSurfaceHolder().lockCanvas(null);
//      if (canvas == null) {
//        throw new RuntimeException("canvas is still null");
//      }
//    } else {
//      throw new RuntimeException("not primary surface");
//    }

    checkSettings();

    resetMatrix(); // reset model matrix

    // reset vertices
    vertexCount = 0;
  }


  public void endDraw() {
//    if (primarySurface) {
//      if (canvas != null) {
//        parent.getSurfaceHolder().unlockCanvasAndPost(canvas);
//      }
//    }

    if (primarySurface) {
      Canvas screen = null;
      try {
        screen = parent.getSurfaceHolder().lockCanvas(null);
        if (screen != null) {
          screen.drawBitmap(image, new Matrix(), null);
        }
      } finally {
        if (screen != null) {
          parent.getSurfaceHolder().unlockCanvasAndPost(screen);
        }
      }
    }

    // hm, mark pixels as changed, because this will instantly do a full
    // copy of all the pixels to the surface.. so that's kind of a mess.
    //updatePixels();

    // TODO this is probably overkill for most tasks...
    if (!primarySurface) {
      loadPixels();
    }
    modified = true;
  }



  //////////////////////////////////////////////////////////////

  // SETTINGS


  //protected void checkSettings()


  //protected void defaultSettings()


  //protected void reapplySettings()



  //////////////////////////////////////////////////////////////

  // HINT


  //public void hint(int which)



  //////////////////////////////////////////////////////////////

  // SHAPES


  //public void beginShape(int kind)


  public void beginShape(int kind) {
    //super.beginShape(kind);
    shape = kind;
    vertexCount = 0;
    curveVertexCount = 0;

    // reset the path, because when mixing curves and straight
    // lines, vertexCount will be set back to zero, so vertexCount == 1
    // is no longer a good indicator of whether the shape is new.
    // this way, just check to see if gpath is null, and if it isn't
    // then just use it to continue the shape.
    //path = null;
    path.reset();
  }


  //public boolean edge(boolean e)


  //public void normal(float nx, float ny, float nz) {


  //public void textureMode(int mode)


  public void texture(PImage image) {
    showMethodWarning("texture");
  }


  public void vertex(float x, float y) {
    curveVertexCount = 0;
    //float vertex[];

    if (vertexCount == vertices.length) {
      float temp[][] = new float[vertexCount<<1][VERTEX_FIELD_COUNT];
      System.arraycopy(vertices, 0, temp, 0, vertexCount);
      vertices = temp;
      //message(CHATTER, "allocating more vertices " + vertices.length);
    }
    // not everyone needs this, but just easier to store rather
    // than adding another moving part to the code...
    vertices[vertexCount][X] = x;
    vertices[vertexCount][Y] = y;
    vertexCount++;

    switch (shape) {

    case POINTS:
      point(x, y);
      break;

    case LINES:
      if ((vertexCount % 2) == 0) {
        line(vertices[vertexCount-2][X],
             vertices[vertexCount-2][Y], x, y);
      }
      break;

    case TRIANGLES:
      if ((vertexCount % 3) == 0) {
        triangle(vertices[vertexCount - 3][X],
                 vertices[vertexCount - 3][Y],
                 vertices[vertexCount - 2][X],
                 vertices[vertexCount - 2][Y],
                 x, y);
      }
      break;

    case TRIANGLE_STRIP:
      if (vertexCount >= 3) {
        triangle(vertices[vertexCount - 2][X],
                 vertices[vertexCount - 2][Y],
                 vertices[vertexCount - 1][X],
                 vertices[vertexCount - 1][Y],
                 vertices[vertexCount - 3][X],
                 vertices[vertexCount - 3][Y]);
      }
      break;

    case TRIANGLE_FAN:
      if (vertexCount == 3) {
        triangle(vertices[0][X], vertices[0][Y],
                 vertices[1][X], vertices[1][Y],
                 x, y);
      } else if (vertexCount > 3) {
        path = new Path();
        // when vertexCount > 3, draw an un-closed triangle
        // for indices 0 (center), previous, current
        path.moveTo(vertices[0][X],
                     vertices[0][Y]);
        path.lineTo(vertices[vertexCount - 2][X],
                    vertices[vertexCount - 2][Y]);
        path.lineTo(x, y);
        drawPath();
      }
      break;

    case QUADS:
      if ((vertexCount % 4) == 0) {
        quad(vertices[vertexCount - 4][X],
             vertices[vertexCount - 4][Y],
             vertices[vertexCount - 3][X],
             vertices[vertexCount - 3][Y],
             vertices[vertexCount - 2][X],
             vertices[vertexCount - 2][Y],
             x, y);
      }
      break;

    case QUAD_STRIP:
      // 0---2---4
      // |   |   |
      // 1---3---5
      if ((vertexCount >= 4) && ((vertexCount % 2) == 0)) {
        quad(vertices[vertexCount - 4][X],
             vertices[vertexCount - 4][Y],
             vertices[vertexCount - 2][X],
             vertices[vertexCount - 2][Y],
             x, y,
             vertices[vertexCount - 3][X],
             vertices[vertexCount - 3][Y]);
      }
      break;

    case POLYGON:
      if (path == null) {
        path = new Path();
        path.moveTo(x, y);
      } else if (breakShape) {
        path.moveTo(x, y);
        breakShape = false;
      } else {
        path.lineTo(x, y);
      }
      break;
    }
  }


  public void vertex(float x, float y, float z) {
    showDepthWarningXYZ("vertex");
  }


  public void vertex(float x, float y, float u, float v) {
    showVariationWarning("vertex(x, y, u, v)");
  }


  public void vertex(float x, float y, float z, float u, float v) {
    showDepthWarningXYZ("vertex");
  }


  public void breakShape() {
    breakShape = true;
  }


  public void endShape(int mode) {
    if (path != null) {  // make sure something has been drawn
      if (shape == POLYGON) {
        if (mode == CLOSE) {
          path.close();
        }
        drawPath();
      }
    }
    shape = 0;
  }



  //////////////////////////////////////////////////////////////

  // BEZIER VERTICES


  public void bezierVertex(float x1, float y1,
                           float x2, float y2,
                           float x3, float y3) {
    bezierVertexCheck();
    path.cubicTo(x1, y1, x2, y2, x3, y3);
  }


  public void bezierVertex(float x2, float y2, float z2,
                           float x3, float y3, float z3,
                           float x4, float y4, float z4) {
    showDepthWarningXYZ("bezierVertex");
  }



  //////////////////////////////////////////////////////////////

  // CURVE VERTICES


  protected void curveVertexCheck() {
    super.curveVertexCheck();

    if (curveCoordX == null) {
      curveCoordX = new float[4];
      curveCoordY = new float[4];
      curveDrawX = new float[4];
      curveDrawY = new float[4];
    }
  }


  protected void curveVertexSegment(float x1, float y1,
                                    float x2, float y2,
                                    float x3, float y3,
                                    float x4, float y4) {
    curveCoordX[0] = x1;
    curveCoordY[0] = y1;

    curveCoordX[1] = x2;
    curveCoordY[1] = y2;

    curveCoordX[2] = x3;
    curveCoordY[2] = y3;

    curveCoordX[3] = x4;
    curveCoordY[3] = y4;

    curveToBezierMatrix.mult(curveCoordX, curveDrawX);
    curveToBezierMatrix.mult(curveCoordY, curveDrawY);

    // since the paths are continuous,
    // only the first point needs the actual moveto
    if (path == null) {
      path = new Path();
      path.moveTo(curveDrawX[0], curveDrawY[0]);
    }

    path.cubicTo(curveDrawX[1], curveDrawY[1],
                  curveDrawX[2], curveDrawY[2],
                  curveDrawX[3], curveDrawY[3]);
  }


  public void curveVertex(float x, float y, float z) {
    showDepthWarningXYZ("curveVertex");
  }



  //////////////////////////////////////////////////////////////

  // RENDERER


  //public void flush()



  //////////////////////////////////////////////////////////////

  // POINT, LINE, TRIANGLE, QUAD


  public void point(float x, float y) {
    if (strokeWeight > 1) {
      line(x, y, x + EPSILON, y + EPSILON);
    } else {
      set((int) screenX(x, y), (int) screenY(x, y), strokeColor);
    }
  }


  public void line(float x1, float y1, float x2, float y2) {
//    line.setLine(x1, y1, x2, y2);
//    strokeShape(line);
    if (stroke) {
      canvas.drawLine(x1, y1, x2, y2, strokePaint);
    }
  }


  public void triangle(float x1, float y1, float x2, float y2,
                       float x3, float y3) {
    path.reset();
    path.moveTo(x1, y1);
    path.lineTo(x2, y2);
    path.lineTo(x3, y3);
    path.close();
    drawPath();
  }


  public void quad(float x1, float y1, float x2, float y2,
                   float x3, float y3, float x4, float y4) {
    path.reset();
    path.moveTo(x1, y1);
    path.lineTo(x2, y2);
    path.lineTo(x3, y3);
    path.lineTo(x4, y4);
    path.close();
    drawPath();
  }



  //////////////////////////////////////////////////////////////

  // RECT


  //public void rectMode(int mode)


  //public void rect(float a, float b, float c, float d)


  protected void rectImpl(float x1, float y1, float x2, float y2) {
//    rect.setFrame(x1, y1, x2-x1, y2-y1);
//    drawShape(rect);
    //rect.set(x1, y1, x2, y2);
    if (fill) {
      canvas.drawRect(x1, y1, x2, y2, fillPaint);
    }
    if (stroke) {
      canvas.drawRect(x1, y1, x2, y2, strokePaint);
    }
  }



  //////////////////////////////////////////////////////////////

  // ELLIPSE


  //public void ellipseMode(int mode)


  //public void ellipse(float a, float b, float c, float d)


  protected void ellipseImpl(float x, float y, float w, float h) {
//    ellipse.setFrame(x, y, w, h);
//    drawShape(ellipse);
    rect.set(x, y, x+w, y+h);
    if (fill) {
      canvas.drawOval(rect, fillPaint);
    }
    if (stroke) {
      canvas.drawOval(rect, strokePaint);
    }
  }



  //////////////////////////////////////////////////////////////

  // ARC


  //public void arc(float a, float b, float c, float d,
  //                float start, float stop)


  protected void arcImpl(float x, float y, float w, float h,
                         float start, float stop) {
    // 0 to 90 in java would be 0 to -90 for p5 renderer
    // but that won't work, so -90 to 0?

    if (stop - start >= TWO_PI) {
      start = 0;
      stop = 360;

    } else {
      start = -start * RAD_TO_DEG;
      stop = -stop * RAD_TO_DEG;

      // ok to do this because already checked for NaN
      while (start < 0) {
        start += 360;
        stop += 360;
      }
      if (start > stop) {
        float temp = start;
        start = stop;
        stop = temp;
      }
    }
    float sweep = stop - start;

    // stroke as Arc2D.OPEN, fill as Arc2D.PIE
    rect.set(x, y, x+w, y+h);
    if (fill) {
      //System.out.println("filla");
//      arc.setArc(x, y, w, h, start, span, Arc2D.PIE);
//      fillShape(arc);
      canvas.drawArc(rect, start, sweep, true, fillPaint);
    }
    if (stroke) {
      //System.out.println("strokey");
//      arc.setArc(x, y, w, h, start, span, Arc2D.OPEN);
//      strokeShape(arc);
      canvas.drawArc(rect, start, sweep, true, strokePaint);
    }
  }



  //////////////////////////////////////////////////////////////

  // JAVA2D SHAPE/PATH HANDLING


//  protected void fillShape(Shape s) {
//    if (fillGradient) {
//      canvas.setPaint(fillGradientObject);
//      canvas.fill(s);
//    } else if (fill) {
//      canvas.setColor(fillColorObject);
//      canvas.fill(s);
//    }
//  }


//  protected void strokeShape(Shape s) {
//    if (strokeGradient) {
//      canvas.setPaint(strokeGradientObject);
//      canvas.draw(s);
//    } else if (stroke) {
//      canvas.setColor(strokeColorObject);
//      canvas.draw(s);
//    }
//  }


//  protected void drawShape(Shape s) {
//    if (fillGradient) {
//      canvas.setPaint(fillGradientObject);
//      canvas.fill(s);
//    } else if (fill) {
//      canvas.setColor(fillColorObject);
//      canvas.fill(s);
//    }
//    if (strokeGradient) {
//      canvas.setPaint(strokeGradientObject);
//      canvas.draw(s);
//    } else if (stroke) {
//      canvas.setColor(strokeColorObject);
//      canvas.draw(s);
//    }
//  }


  protected void drawPath() {
    if (fill) {
      canvas.drawPath(path, fillPaint);
    }
    if (stroke) {
      canvas.drawPath(path, strokePaint);
    }
  }



  //////////////////////////////////////////////////////////////

  // BOX


  //public void box(float size)


  public void box(float w, float h, float d) {
    showMethodWarning("box");
  }



  //////////////////////////////////////////////////////////////

  // SPHERE


  //public void sphereDetail(int res)


  //public void sphereDetail(int ures, int vres)


  public void sphere(float r) {
    showMethodWarning("sphere");
  }



  //////////////////////////////////////////////////////////////

  // BEZIER


  //public float bezierPoint(float a, float b, float c, float d, float t)


  //public float bezierTangent(float a, float b, float c, float d, float t)


  //protected void bezierInitCheck()


  //protected void bezierInit()


  /** Ignored (not needed) in Java 2D. */
  public void bezierDetail(int detail) {
  }


  //public void bezier(float x1, float y1,
  //                   float x2, float y2,
  //                   float x3, float y3,
  //                   float x4, float y4)


  //public void bezier(float x1, float y1, float z1,
  //                   float x2, float y2, float z2,
  //                   float x3, float y3, float z3,
  //                   float x4, float y4, float z4)



  //////////////////////////////////////////////////////////////

  // CURVE


  //public float curvePoint(float a, float b, float c, float d, float t)


  //public float curveTangent(float a, float b, float c, float d, float t)


  /** Ignored (not needed) in Java 2D. */
  public void curveDetail(int detail) {
  }

  //public void curveTightness(float tightness)


  //protected void curveInitCheck()


  //protected void curveInit()


  //public void curve(float x1, float y1,
  //                  float x2, float y2,
  //                  float x3, float y3,
  //                  float x4, float y4)


  //public void curve(float x1, float y1, float z1,
  //                  float x2, float y2, float z2,
  //                  float x3, float y3, float z3,
  //                  float x4, float y4, float z4)



  //////////////////////////////////////////////////////////////

  // SMOOTH


  public void smooth() {
    smooth = true;
//    canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
//                        RenderingHints.VALUE_ANTIALIAS_ON);
//    canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
//                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    strokePaint.setAntiAlias(true);
    fillPaint.setAntiAlias(true);
  }


  public void noSmooth() {
    smooth = false;
//    canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
//                        RenderingHints.VALUE_ANTIALIAS_OFF);
//    canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
//                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    strokePaint.setAntiAlias(false);
    fillPaint.setAntiAlias(false);
  }



  //////////////////////////////////////////////////////////////

  // IMAGE


  //public void imageMode(int mode)


  //public void image(PImage image, float x, float y)


  //public void image(PImage image, float x, float y, float c, float d)


  //public void image(PImage image,
  //                  float a, float b, float c, float d,
  //                  int u1, int v1, int u2, int v2)


  /**
   * Handle renderer-specific image drawing.
   */
  protected void imageImpl(PImage who,
                           float x1, float y1, float x2, float y2,
                           int u1, int v1, int u2, int v2) {
//    canvas.drawBitmap(who.pixels, 0, who.width,
//                      x1, y1, (x2-x1), (y2-y1),
//                      who.format == ARGB, tint ? tintPaint : null);
    rect.set(x1, y1, x2, y2);
    canvas.drawBitmap(who.image, null, rect, tint ? tintPaint : null);

//    if (who.getCache(this) == null) {
//      who.setCache(this, new ImageCache(who));
//      who.updatePixels();  // mark the whole thing for update
//      who.modified = true;
//    }
//
//    ImageCache cash = (ImageCache) who.getCache(this);
//    // if image previously was tinted, or the color changed
//    // or the image was tinted, and tint is now disabled
//    if ((tint && !cash.tinted) ||
//        (tint && (cash.tintedColor != tintColor)) ||
//        (!tint && cash.tinted)) {
//      // for tint change, mark all pixels as needing update
//      who.updatePixels();
//    }
//
//    if (who.modified) {
//      cash.update(tint, tintColor);
//      who.modified = false;
//    }
//
//    canvas.drawImage(((ImageCache) who.getCache(this)).image,
//                 (int) x1, (int) y1, (int) x2, (int) y2,
//                 u1, v1, u2, v2, null);
  }


//  class ImageCache {
//    PImage source;
//    boolean tinted;
//    int tintedColor;
//    int tintedPixels[];  // one row of tinted pixels
//    BufferedImage image;
//
//    public ImageCache(PImage source) {
//      this.source = source;
//      // even if RGB, set the image type to ARGB, because the
//      // image may have an alpha value for its tint().
////      int type = BufferedImage.TYPE_INT_ARGB;
//      //System.out.println("making new buffered image");
////      image = new BufferedImage(source.width, source.height, type);
//    }
//
//    /**
//     * Update the pixels of the cache image. Already determined that the tint
//     * has changed, or the pixels have changed, so should just go through
//     * with the update without further checks.
//     */
//    public void update(boolean tint, int tintColor) {
//      int bufferType = BufferedImage.TYPE_INT_ARGB;
//      boolean opaque = (tintColor & 0xFF000000) == 0xFF000000;
//      if (source.format == RGB) {
//        if (!tint || (tint && opaque)) {
//          bufferType = BufferedImage.TYPE_INT_RGB;
//        }
//      }
//      boolean wrongType = (image != null) && (image.getType() != bufferType);
//      if ((image == null) || wrongType) {
//        image = new BufferedImage(source.width, source.height, bufferType);
//      }
//
//      WritableRaster wr = image.getRaster();
//      if (tint) {
//        if (tintedPixels == null || tintedPixels.length != source.width) {
//          tintedPixels = new int[source.width];
//        }
//        int a2 = (tintColor >> 24) & 0xff;
//        int r2 = (tintColor >> 16) & 0xff;
//        int g2 = (tintColor >> 8) & 0xff;
//        int b2 = (tintColor) & 0xff;
//
//        if (bufferType == BufferedImage.TYPE_INT_RGB) {
//          //int alpha = tintColor & 0xFF000000;
//          int index = 0;
//          for (int y = 0; y < source.height; y++) {
//            for (int x = 0; x < source.width; x++) {
//              int argb1 = source.pixels[index++];
//              int r1 = (argb1 >> 16) & 0xff;
//              int g1 = (argb1 >> 8) & 0xff;
//              int b1 = (argb1) & 0xff;
//
//              tintedPixels[x] = //0xFF000000 |
//                (((r2 * r1) & 0xff00) << 8) |
//                ((g2 * g1) & 0xff00) |
//                (((b2 * b1) & 0xff00) >> 8);
//            }
//            wr.setDataElements(0, y, source.width, 1, tintedPixels);
//          }
//          // could this be any slower?
////          float[] scales = { tintR, tintG, tintB };
////          float[] offsets = new float[3];
////          RescaleOp op = new RescaleOp(scales, offsets, null);
////          op.filter(image, image);
//
//        } else if (bufferType == BufferedImage.TYPE_INT_ARGB) {
//          int index = 0;
//          for (int y = 0; y < source.height; y++) {
//            if (source.format == RGB) {
//              int alpha = tintColor & 0xFF000000;
//              for (int x = 0; x < source.width; x++) {
//                int argb1 = source.pixels[index++];
//                int r1 = (argb1 >> 16) & 0xff;
//                int g1 = (argb1 >> 8) & 0xff;
//                int b1 = (argb1) & 0xff;
//                tintedPixels[x] = alpha |
//                  (((r2 * r1) & 0xff00) << 8) |
//                  ((g2 * g1) & 0xff00) |
//                  (((b2 * b1) & 0xff00) >> 8);
//              }
//            } else if (source.format == ARGB) {
//              for (int x = 0; x < source.width; x++) {
//                int argb1 = source.pixels[index++];
//                int a1 = (argb1 >> 24) & 0xff;
//                int r1 = (argb1 >> 16) & 0xff;
//                int g1 = (argb1 >> 8) & 0xff;
//                int b1 = (argb1) & 0xff;
//                tintedPixels[x] =
//                  (((a2 * a1) & 0xff00) << 16) |
//                  (((r2 * r1) & 0xff00) << 8) |
//                  ((g2 * g1) & 0xff00) |
//                  (((b2 * b1) & 0xff00) >> 8);
//              }
//            } else if (source.format == ALPHA) {
//              int lower = tintColor & 0xFFFFFF;
//              for (int x = 0; x < source.width; x++) {
//                int a1 = source.pixels[index++];
//                tintedPixels[x] =
//                  (((a2 * a1) & 0xff00) << 16) | lower;
//              }
//            }
//            wr.setDataElements(0, y, source.width, 1, tintedPixels);
//          }
//          // Not sure why ARGB images take the scales in this order...
////          float[] scales = { tintR, tintG, tintB, tintA };
////          float[] offsets = new float[4];
////          RescaleOp op = new RescaleOp(scales, offsets, null);
////          op.filter(image, image);
//        }
//      } else {
//        wr.setDataElements(0, 0, source.width, source.height, source.pixels);
//      }
//      this.tinted = tint;
//      this.tintedColor = tintColor;
//    }
//  }



  //////////////////////////////////////////////////////////////

  // SHAPE


  //public void shapeMode(int mode)


  //public void shape(PShape shape)


  //public void shape(PShape shape, float x, float y)


  //public void shape(PShape shape, float x, float y, float c, float d)



  //////////////////////////////////////////////////////////////

  // TEXT ATTRIBTUES


  //public void textAlign(int align)


  //public void textAlign(int alignX, int alignY)


  public float textAscent() {
//    Font font = textFont.getFont();
    Typeface font = textFont.getFont();
    if (font == null) {
      return super.textAscent();
    }
//    FontMetrics metrics = parent.getFontMetrics(font);
//    return metrics.getAscent();
    return fillPaint.ascent();
  }


  public float textDescent() {
//    Font font = textFont.getFont();
    Typeface font = textFont.getFont();
    if (font == null) {
      return super.textDescent();
    }
//    FontMetrics metrics = parent.getFontMetrics(font);
//    return metrics.getDescent();
    return fillPaint.descent();
  }


  public void textFont(PFont which) {
    super.textFont(which);
    fillPaint.setTypeface(which.getFont());
  }


  //public void textFont(PFont which, float size)


  //public void textLeading(float leading)


  //public void textMode(int mode)


  protected boolean textModeCheck(int mode) {
    return (mode == MODEL) || (mode == SCREEN);
  }


  /**
   * Same as parent, but override for native version of the font.
   * <p/>
   * Also gets called by textFont, so the metrics
   * will get recorded properly.
   */
  public void textSize(float size) {
//    Font font = textFont.getFont();
    Typeface font = textFont.getFont();
    if (font != null) {
//      Font dfont = font.deriveFont(size);
//      canvas.setFont(dfont);
//      textFont.setFont(dfont);
      fillPaint.setTextSize(size);
    }

    // take care of setting the textSize and textLeading vars
    // this has to happen second, because it calls textAscent()
    // (which requires the native font metrics to be set)
    super.textSize(size);
  }


  //public float textWidth(char c)


  //public float textWidth(String str)


  protected float textWidthImpl(char buffer[], int start, int stop) {
//    Font font = textFont.getFont();
    Typeface font = textFont.getFont();
    if (font == null) {
      return super.textWidthImpl(buffer, start, stop);
    }
    // maybe should use one of the newer/fancier functions for this?
    int length = stop - start;
//    FontMetrics metrics = canvas.getFontMetrics(font);
//    return metrics.charsWidth(buffer, start, length);
    return fillPaint.measureText(buffer, start, length);
  }



  //////////////////////////////////////////////////////////////

  // TEXT

  // None of the variations of text() are overridden from PGraphics.



  //////////////////////////////////////////////////////////////

  // TEXT IMPL


  //protected void textLineAlignImpl(char buffer[], int start, int stop,
  //                                 float x, float y)


  protected void textLineImpl(char buffer[], int start, int stop,
                              float x, float y) {
    Typeface font = textFont.getFont();
    if (font == null) {
      super.textLineImpl(buffer, start, stop, x, y);
      return;
    }

    /*
    // save the current setting for text smoothing. note that this is
    // different from the smooth() function, because the font smoothing
    // is controlled when the font is created, not now as it's drawn.
    // fixed a bug in 0116 that handled this incorrectly.
    Object textAntialias =
      g2.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);

    // override the current text smoothing setting based on the font
    // (don't change the global smoothing settings)
    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        textFont.smooth ?
                        RenderingHints.VALUE_ANTIALIAS_ON :
                        RenderingHints.VALUE_ANTIALIAS_OFF);
    */

//    Object antialias =
//      canvas.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
//    if (antialias == null) {
//      // if smooth() and noSmooth() not called, this will be null (0120)
//      antialias = RenderingHints.VALUE_ANTIALIAS_DEFAULT;
//    }

    // override the current smoothing setting based on the font
    // also changes global setting for antialiasing, but this is because it's
    // not possible to enable/disable them independently in some situations.
//    canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
//                            textFont.smooth ?
//                            RenderingHints.VALUE_ANTIALIAS_ON :
//                            RenderingHints.VALUE_ANTIALIAS_OFF);
    fillPaint.setAntiAlias(textFont.smooth);

    //System.out.println("setting frac metrics");
    //g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
    //                    RenderingHints.VALUE_FRACTIONALMETRICS_ON);

//    canvas.setColor(fillColorObject);
    int length = stop - start;
//    canvas.drawChars(buffer, start, length, (int) (x + 0.5f), (int) (y + 0.5f));
    canvas.drawText(buffer, start, length, x, y, fillPaint);

    // return to previous smoothing state if it was changed
//    canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialias);
    fillPaint.setAntiAlias(smooth);

    textX = x + textWidthImpl(buffer, start, stop);
    textY = y;
    textZ = 0;  // this will get set by the caller if non-zero
  }



  //////////////////////////////////////////////////////////////

  // MATRIX STACK


  public void pushMatrix() {
//    if (transformCount == transformStack.length) {
//      throw new RuntimeException("pushMatrix() cannot use push more than " +
//                                 transformStack.length + " times");
//    }
//    transformStack[transformCount] = canvas.getMatrix();
//    transformCount++;
    canvas.save(Canvas.MATRIX_SAVE_FLAG);
  }


  public void popMatrix() {
//    if (transformCount == 0) {
//      throw new RuntimeException("missing a popMatrix() " +
//                                 "to go with that pushMatrix()");
//    }
//    transformCount--;
//    canvas.setMatrix(transformStack[transformCount]);
    canvas.restore();
  }



  //////////////////////////////////////////////////////////////

  // MATRIX TRANSFORMS


  public void translate(float tx, float ty) {
    canvas.translate(tx, ty);
  }


  //public void translate(float tx, float ty, float tz)


  public void rotate(float angle) {
    canvas.rotate(angle);
  }


  public void rotateX(float angle) {
    showDepthWarning("rotateX");
  }


  public void rotateY(float angle) {
    showDepthWarning("rotateY");
  }


  public void rotateZ(float angle) {
    showDepthWarning("rotateZ");
  }


  public void rotate(float angle, float vx, float vy, float vz) {
    showVariationWarning("rotate");
  }


  public void scale(float s) {
    canvas.scale(s, s);
  }


  public void scale(float sx, float sy) {
    canvas.scale(sx, sy);
  }


  public void scale(float sx, float sy, float sz) {
    showDepthWarningXYZ("scale");
  }



  //////////////////////////////////////////////////////////////

  // MATRIX MORE


  public void resetMatrix() {
//    canvas.setTransform(new AffineTransform());
    canvas.setMatrix(new Matrix());
  }


  //public void applyMatrix(PMatrix2D source)


  public void applyMatrix(float n00, float n01, float n02,
                          float n10, float n11, float n12) {
//    canvas.transform(new AffineTransform(n00, n10, n01, n11, n02, n12));
    // TODO optimize
    Matrix m = new Matrix();
    m.setValues(new float[] {
      n00, n01, n02,
      n10, n11, n12,
      0,   0,   1
    });
    canvas.concat(m);
  }


  //public void applyMatrix(PMatrix3D source)


  public void applyMatrix(float n00, float n01, float n02, float n03,
                          float n10, float n11, float n12, float n13,
                          float n20, float n21, float n22, float n23,
                          float n30, float n31, float n32, float n33) {
    showVariationWarning("applyMatrix");
  }



  //////////////////////////////////////////////////////////////

  // MATRIX GET/SET


  public PMatrix getMatrix() {
    return getMatrix((PMatrix2D) null);
  }


  public PMatrix2D getMatrix(PMatrix2D target) {
    if (target == null) {
      target = new PMatrix2D();
    }
//    canvas.getTransform().getMatrix(transform);
    Matrix m = new Matrix();
    canvas.getMatrix(m);
    m.getValues(transform);
//    target.set((float) transform[0], (float) transform[2], (float) transform[4],
//               (float) transform[1], (float) transform[3], (float) transform[5]);
    target.set((float) transform[0], (float) transform[1], (float) transform[2],
               (float) transform[3], (float) transform[4], (float) transform[5]);
    return target;
  }


  public PMatrix3D getMatrix(PMatrix3D target) {
    showVariationWarning("getMatrix");
    return target;
  }


  //public void setMatrix(PMatrix source)


  public void setMatrix(PMatrix2D source) {
//    canvas.setTransform(new AffineTransform(source.m00, source.m10,
//                                            source.m01, source.m11,
//                                            source.m02, source.m12));
    Matrix matrix = new Matrix();
    matrix.setValues(new float[] {
      source.m00, source.m01, source.m02,
      source.m10, source.m11, source.m12,
      0, 0, 1
    });
    canvas.setMatrix(matrix);
  }


  public void setMatrix(PMatrix3D source) {
    showVariationWarning("setMatrix");
  }


  public void printMatrix() {
    getMatrix((PMatrix2D) null).print();
  }



  //////////////////////////////////////////////////////////////

  // CAMERA and PROJECTION

  // Inherit the plaintive warnings from PGraphics


  //public void beginCamera()
  //public void endCamera()
  //public void camera()
  //public void camera(float eyeX, float eyeY, float eyeZ,
  //                   float centerX, float centerY, float centerZ,
  //                   float upX, float upY, float upZ)
  //public void printCamera()

  //public void ortho()
  //public void ortho(float left, float right,
  //                  float bottom, float top,
  //                  float near, float far)
  //public void perspective()
  //public void perspective(float fov, float aspect, float near, float far)
  //public void frustum(float left, float right,
  //                    float bottom, float top,
  //                    float near, float far)
  //public void printProjection()



  //////////////////////////////////////////////////////////////

  // SCREEN and MODEL transforms

  float[] screenPoint;

  public float screenX(float x, float y) {
//    canvas.getTransform().getMatrix(transform);
//    return (float)transform[0]*x + (float)transform[2]*y + (float)transform[4];
    if (screenPoint == null) {
      screenPoint = new float[2];
    }
    screenPoint[0] = x;
    screenPoint[1] = y;
    canvas.getMatrix().mapPoints(screenPoint);
    return screenPoint[0];
  }


  public float screenY(float x, float y) {
//    canvas.getTransform().getMatrix(transform);
//    return (float)transform[1]*x + (float)transform[3]*y + (float)transform[5];
    if (screenPoint == null) {
      screenPoint = new float[2];
    }
    screenPoint[0] = x;
    screenPoint[1] = y;
    canvas.getMatrix().mapPoints(screenPoint);
    return screenPoint[1];
  }


  public float screenX(float x, float y, float z) {
    showDepthWarningXYZ("screenX");
    return 0;
  }


  public float screenY(float x, float y, float z) {
    showDepthWarningXYZ("screenY");
    return 0;
  }


  public float screenZ(float x, float y, float z) {
    showDepthWarningXYZ("screenZ");
    return 0;
  }


  //public float modelX(float x, float y, float z)


  //public float modelY(float x, float y, float z)


  //public float modelZ(float x, float y, float z)



  //////////////////////////////////////////////////////////////

  // STYLE

  // pushStyle(), popStyle(), style() and getStyle() inherited.



  //////////////////////////////////////////////////////////////

  // STROKE CAP/JOIN/WEIGHT


  public void strokeCap(int cap) {
    super.strokeCap(cap);

    if (strokeCap == ROUND) {
      strokePaint.setStrokeCap(Paint.Cap.ROUND);
    } else if (strokeCap == PROJECT) {
      strokePaint.setStrokeCap(Paint.Cap.SQUARE);
    } else {
      strokePaint.setStrokeCap(Paint.Cap.BUTT);
    }
  }


  public void strokeJoin(int join) {
    super.strokeJoin(join);

    if (strokeJoin == MITER) {
      strokePaint.setStrokeJoin(Paint.Join.MITER);
    } else if (strokeJoin == ROUND) {
      strokePaint.setStrokeJoin(Paint.Join.ROUND);
    } else {
      strokePaint.setStrokeJoin(Paint.Join.BEVEL);
    }
  }


  public void strokeWeight(float weight) {
    super.strokeWeight(weight);
    strokePaint.setStrokeWidth(weight);
  }



  //////////////////////////////////////////////////////////////

  // STROKE

  // noStroke() and stroke() inherited from PGraphics.


  protected void strokeFromCalc() {
    super.strokeFromCalc();
//    strokeColorObject = new Color(strokeColor, true);
    strokePaint.setColor(strokeColor);
//    strokeGradient = false;
    strokePaint.setShader(null);
  }



  //////////////////////////////////////////////////////////////

  // TINT

  // noTint() and tint() inherited from PGraphics.


  protected void tintFromCalc() {
    super.tintFromCalc();
//    tintColorObject = new Color(tintColor, true);
    tintPaint.setColor(tintColor);
  }



  //////////////////////////////////////////////////////////////

  // FILL

  // noFill() and fill() inherited from PGraphics.


  protected void fillFromCalc() {
    super.fillFromCalc();
//    fillColorObject = new Color(fillColor, true);
    fillPaint.setColor(fillColor);
//    fillGradient = false;
    fillPaint.setShader(null);
  }



  //////////////////////////////////////////////////////////////

  // MATERIAL PROPERTIES


  //public void ambient(int rgb)
  //public void ambient(float gray)
  //public void ambient(float x, float y, float z)
  //protected void ambientFromCalc()
  //public void specular(int rgb)
  //public void specular(float gray)
  //public void specular(float x, float y, float z)
  //protected void specularFromCalc()
  //public void shininess(float shine)
  //public void emissive(int rgb)
  //public void emissive(float gray)
  //public void emissive(float x, float y, float z )
  //protected void emissiveFromCalc()



  //////////////////////////////////////////////////////////////

  // LIGHTS


  //public void lights()
  //public void noLights()
  //public void ambientLight(float red, float green, float blue)
  //public void ambientLight(float red, float green, float blue,
  //                         float x, float y, float z)
  //public void directionalLight(float red, float green, float blue,
  //                             float nx, float ny, float nz)
  //public void pointLight(float red, float green, float blue,
  //                       float x, float y, float z)
  //public void spotLight(float red, float green, float blue,
  //                      float x, float y, float z,
  //                      float nx, float ny, float nz,
  //                      float angle, float concentration)
  //public void lightFalloff(float constant, float linear, float quadratic)
  //public void lightSpecular(float x, float y, float z)
  //protected void lightPosition(int num, float x, float y, float z)
  //protected void lightDirection(int num, float x, float y, float z)



  //////////////////////////////////////////////////////////////

  // BACKGROUND

  // background() methods inherited from PGraphics, along with the
  // PImage version of backgroundImpl(), since it just calls set().


  //public void backgroundImpl(PImage image)


//  int[] clearPixels;

  public void backgroundImpl() {
    canvas.drawColor(backgroundColor);

//    if (backgroundAlpha) {
//      WritableRaster raster = ((BufferedImage) image).getRaster();
//      if ((clearPixels == null) || (clearPixels.length < width)) {
//        clearPixels = new int[width];
//      }
//      java.util.Arrays.fill(clearPixels, backgroundColor);
//      for (int i = 0; i < height; i++) {
//        raster.setDataElements(0, i, width, 1, clearPixels);
//      }
//    } else {
//      //new Exception().printStackTrace(System.out);
//      // in case people do transformations before background(),
//      // need to handle this with a push/reset/pop
//      pushMatrix();
//      resetMatrix();
//      canvas.setColor(new Color(backgroundColor)); //, backgroundAlpha));
//      canvas.fillRect(0, 0, width, height);
//      popMatrix();
//    }
  }



  //////////////////////////////////////////////////////////////

  // COLOR MODE

  // All colorMode() variations are inherited from PGraphics.



  //////////////////////////////////////////////////////////////

  // COLOR CALC

  // colorCalc() and colorCalcARGB() inherited from PGraphics.



  //////////////////////////////////////////////////////////////

  // COLOR DATATYPE STUFFING

  // final color() variations inherited.



  //////////////////////////////////////////////////////////////

  // COLOR DATATYPE EXTRACTION

  // final methods alpha, red, green, blue,
  // hue, saturation, and brightness all inherited.



  //////////////////////////////////////////////////////////////

  // COLOR DATATYPE INTERPOLATION

  // both lerpColor variants inherited.



  //////////////////////////////////////////////////////////////

  // BEGIN/END RAW


  public void beginRaw(PGraphics recorderRaw) {
    showMethodWarning("beginRaw");
  }


  public void endRaw() {
    showMethodWarning("endRaw");
  }



  //////////////////////////////////////////////////////////////

  // WARNINGS and EXCEPTIONS

  // showWarning and showException inherited.



  //////////////////////////////////////////////////////////////

  // RENDERER SUPPORT QUERIES


  //public boolean displayable()  // true


  //public boolean is2D()  // true


  //public boolean is3D()  // false



  //////////////////////////////////////////////////////////////

  // PIMAGE METHODS


  // getImage, setCache, getCache, removeCache, isModified, setModified


  public void loadPixels() {
    if ((pixels == null) || (pixels.length != width * height)) {
      pixels = new int[width * height];
    }
//    WritableRaster raster = ((BufferedImage) image).getRaster();
//    raster.getDataElements(0, 0, width, height, pixels);
    image.getPixels(pixels, 0, width, 0, 0, width, height);
  }


  /**
   * Update the pixels[] buffer to the PGraphics image.
   * <P>
   * Unlike in PImage, where updatePixels() only requests that the
   * update happens, in PGraphicsJava2D, this will happen immediately.
   */
  public void updatePixels() {
//    WritableRaster raster = ((BufferedImage) image).getRaster();
//    raster.setDataElements(0, 0, width, height, pixels);
    image.setPixels(pixels, 0, width, 0, 0, width, height);
  }


  /**
   * Update the pixels[] buffer to the PGraphics image.
   * <P>
   * Unlike in PImage, where updatePixels() only requests that the
   * update happens, in PGraphicsJava2D, this will happen immediately.
   */
  public void updatePixels(int x, int y, int c, int d) {
    //if ((x == 0) && (y == 0) && (c == width) && (d == height)) {
    if ((x != 0) || (y != 0) || (c != width) || (d != height)) {
      // Show a warning message, but continue anyway.
      showVariationWarning("updatePixels(x, y, w, h)");
    }
    updatePixels();
  }


  public void resize(int wide, int high) {
    showMethodWarning("resize");
  }



  //////////////////////////////////////////////////////////////

  // GET/SET


  static int getset[] = new int[1];


  public int get(int x, int y) {
    if ((x < 0) || (y < 0) || (x >= width) || (y >= height)) return 0;
//    WritableRaster raster = ((BufferedImage) image).getRaster();
//    raster.getDataElements(x, y, getset);
//    return getset[0];
    return image.getPixel(x, y);
  }


  //public PImage get(int x, int y, int w, int h)


  public PImage getImpl(int x, int y, int w, int h) {
    PImage output = new PImage(w, h);
    output.parent = parent;

//    WritableRaster raster = ((BufferedImage) image).getRaster();
//    raster.getDataElements(x, y, w, h, output.pixels);
    Bitmap bitsy = Bitmap.createBitmap(image, x, y, w, h);
    bitsy.getPixels(output.pixels, 0, w, 0, 0, w, h);

    return output;
  }


  public PImage get() {
    return get(0, 0, width, height);
  }


  public void set(int x, int y, int argb) {
    if ((x < 0) || (y < 0) || (x >= width) || (y >= height)) return;
//    getset[0] = argb;
//    WritableRaster raster = ((BufferedImage) image).getRaster();
//    raster.setDataElements(x, y, getset);
    image.setPixel(x, y, argb);
  }


//  protected void setImpl(int dx, int dy, int sx, int sy, int sw, int sh,
//                         PImage src) {
//    WritableRaster raster = ((BufferedImage) image).getRaster();
//    if ((sx == 0) && (sy == 0) && (sw == src.width) && (sh == src.height)) {
//      raster.setDataElements(dx, dy, src.width, src.height, src.pixels);
//    } else {
//      // TODO Optimize, incredibly inefficient to reallocate this much memory
//      PImage temp = src.get(sx, sy, sw, sh);
//      raster.setDataElements(dx, dy, temp.width, temp.height, temp.pixels);
//    }
//  }



  //////////////////////////////////////////////////////////////

  // MASK


  public void mask(int alpha[]) {
    showMethodWarning("mask");
  }


  public void mask(PImage alpha) {
    showMethodWarning("mask");
  }



  //////////////////////////////////////////////////////////////

  // FILTER

  // Because the PImage versions call loadPixels() and
  // updatePixels(), no need to override anything here.


  //public void filter(int kind)


  //public void filter(int kind, float param)



  //////////////////////////////////////////////////////////////

  // COPY


  public void copy(int sx, int sy, int sw, int sh,
                   int dx, int dy, int dw, int dh) {
//    Bitmap bitsy = Bitmap.createBitmap(image, sx, sy, sw, sh);
//    rect.set(dx, dy, dx + dw, dy + dh);
//    canvas.drawBitmap(bitsy,
    rect.set(sx, sy, sx+sw, sy+sh);
    Rect src = new Rect(dx, dy, dx+dw, dy+dh);
    canvas.drawBitmap(image, src, rect, null);

//    if ((sw != dw) || (sh != dh)) {
//      // use slow version if changing size
//      copy(this, sx, sy, sw, sh, dx, dy, dw, dh);
//
//    } else {
//      dx = dx - sx;  // java2d's "dx" is the delta, not dest
//      dy = dy - sy;
//      canvas.copyArea(sx, sy, sw, sh, dx, dy);
//    }
  }


//  public void copy(PImage src,
//                   int sx1, int sy1, int sx2, int sy2,
//                   int dx1, int dy1, int dx2, int dy2) {
//    loadPixels();
//    super.copy(src, sx1, sy1, sx2, sy2, dx1, dy1, dx2, dy2);
//    updatePixels();
//  }



  //////////////////////////////////////////////////////////////

  // BLEND


//  static public int blendColor(int c1, int c2, int mode)


//  public void blend(int sx, int sy, int sw, int sh,
//                    int dx, int dy, int dw, int dh, int mode)


//  public void blend(PImage src,
//                    int sx, int sy, int sw, int sh,
//                    int dx, int dy, int dw, int dh, int mode)



  //////////////////////////////////////////////////////////////

  // SAVE


//  public void save(String filename) {
//    loadPixels();
//    super.save(filename);
//  }
}