/* -*- mode: jde; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  PGraphics - main graphics and rendering context for bagel
  Part of the Processing project - http://processing.org

  Copyright (c) 2001-04 Massachusetts Institute of Technology 
  (Except where noted that the author is not Ben Fry)

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

package processing.core;

import java.applet.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.net.*;
import java.util.zip.*;


public class PGraphics extends PImage implements PConstants {
  public Applet applet;  // not comfortable with this being static

  // ........................................................

  //int width, height;
  public int width1, height1;    // minus 1

  int pixelCount;
  //public int pixels[];  // wtf? appletviewer wants this pubic
  int stencil[];        // stencil buffer used to antialias polygons
  float zbuffer[];
  //boolean zbufferTainted;

  boolean depthTest;

  //int frameCount;

  // ........................................................

  // specifics for java memoryimagesource
  DirectColorModel cm;
  MemoryImageSource mis;
  Image image;

  // ........................................................

  // needs to happen before background() is called
  // and resize.. so it's gotta be outside
  static boolean hints[] = new boolean[HINT_COUNT];
  static {
    //hints[NEW_GRAPHICS] = true;
  }


  // ........................................................

  // underscored_names are used for private functions or variables

  // internal values.. i.e. the var set by colorMode() 
  // becomes color_mode
  // if it's a one-word feller like 'fill' or 'stroke'
  // then an underscore is placed in front: _fill

  // color parameters
  // internally, colors are 0..1 for the floats
  // this makes the lighting computations clearer
  int color_mode;
  boolean color_scale;
  float colorMaxX;
  float colorMaxY;
  float colorMaxZ;
  float colorMaxA;

  // tint color
  boolean _tint, tint_alpha;
  float tintR, tintG, tintB, tintA;
  int tintRi, tintGi, tintBi, tintAi;
  int tint;

  // fill color
  boolean _fill, fill_alpha;
  float fillR, fillG, fillB, fillA;
  int fillRi, fillGi, fillBi, fillAi;
  int fill;

  // stroke color
  boolean _stroke, stroke_alpha;
  float strokeR, strokeG, strokeB, strokeA;
  int strokeRi, strokeGi, strokeBi, strokeAi;
  int stroke;

  float strokeWeight;
  int strokeJoin;
  int strokeMiter;  

  // background color
  boolean _background;
  float backR, backG, backB;
  int backRi, backGi, backBi;
  int background;

  // internal color for setting/calculating
  float calcR, calcG, calcB, calcA;
  int calcRi, calcGi, calcBi, calcAi;
  int calci;
  boolean calc_alpha;

  // cache for hsb conversion values for get/set pixel
  int cacheHsbKey;
  float cacheHsbValue[] = new float[3]; // init to zero

  // lighting
  static final int MAX_LIGHTS = 10;
  boolean lighting;
  float lightR[], lightG[], lightB[];
  float lightX[], lightY[], lightZ[];
  int lightKind[];

  // inherited from PImage
  //boolean smooth = false;  // antialiasing

  // projection
  //float prevProjX, prevProjY, prevProjZ;
  //float projX,  projY,  projZ;

  // ........................................................

  /** 
   * Model transformation of the form m[row][column],
   * which is a "column vector" (as opposed to "row vector") matrix.
   */
  float m00, m01, m02, m03;  
  float m10, m11, m12, m13;
  float m20, m21, m22, m23;
  float m30, m31, m32, m33;

  static final int MATRIX_STACK_DEPTH = 32;
  float matrixStack[][] = new float[MATRIX_STACK_DEPTH][16];
  int matrixStackDepth;

  // ........................................................

  // current 3D transformation matrix 
  int camera_mode;
  //int projection;  // none, perspective, or isometric
  int dimensions;  // 0, 2 (affine 2d), 3 (perspective/isometric)

  // perspective setup
  float fov;
  float eyeX, eyeY;
  float eyeDist, nearDist, farDist;
  float aspect;

  float p00, p01, p02, p03; // projection matrix
  float p10, p11, p12, p13;
  float p20, p21, p22, p23;
  float p30, p31, p32, p33;

  // ........................................................

  // shapes

  boolean shape;
  int shapeKind;


  // ........................................................

  // OLD_GRAPHICS
  
  PPolygon polygon;     // general polygon to use for shape
  PPolygon fpolygon;    // used to fill polys for tri or quad strips
  PPolygon spolygon;    // stroke/line polygon
  float svertices[][];  // temp vertices used for stroking end of poly

  PPolygon tpolygon;    // for calculating concave/convex
  int TPOLYGON_MAX_VERTICES = 512;
  int tpolygon_vertex_order[]; // = new int[MAX_VERTICES];

  // ........................................................

  // NEW_GRAPHICS
  
  int shape_index;

  // vertices
  static final int DEFAULT_VERTICES = 512;
  float vertices[][] = new float[DEFAULT_VERTICES][VERTEX_FIELD_COUNT];
  int vertex_count;  // total number of vertices
  int vertex_start;  // pos of first vertex of current shape in vertices array
  int vertex_end;    // total number of vertex in current shape
  // used for sorting points when triangulating a polygon
  // warning - maximum number of vertices for a polygon is DEFAULT_VERTICES
  int vertex_order[] = new int[DEFAULT_VERTICES];

  // lines
  static final int DEFAULT_LINES = 512;
  PLine line;  // used for drawing
  int lines[][] = new int[DEFAULT_LINES][LINE_FIELD_COUNT];
  int lines_count;

  // triangles
  static final int DEFAULT_TRIANGLES = 256;
  PTriangle triangle; // used for rendering
  int triangles[][] = new int[DEFAULT_TRIANGLES][TRIANGLE_FIELD_COUNT];
  int triangles_count;   // total number of triangles

  // other options
  public boolean clip = true;
  public boolean z_order = true;

  // six planes
  // (A,B,C in plane eq + D)
  float frustum[][] = new float[6][4];
  float   cp[] = new float[16]; // temporary


  // ........................................................

  // texture images

  int texture_mode = IMAGE_SPACE;
  float textureU, textureV;
  float normalX, normalY, normalZ;

  // OLD_GRAPHICS
  boolean texture;

  // NEW_GRAPHICS
  private PImage textureImage;
  static final int DEFAULT_TEXTURES = 3;
  PImage textures[] = new PImage[DEFAULT_TEXTURES];
  int texture_index;
  

  // ........................................................

  // changes

  boolean unchangedZ;
  boolean strokeChanged;
  boolean fillChanged;
  boolean normalChanged;


  // ........................................................

  // curve vertices

  static final int CVERTEX_ALLOC = 128;
  float cvertex[][] = new float[CVERTEX_ALLOC][VERTEX_FIELD_COUNT];
  int cvertexIndex;
  boolean cverticesFlat;


  // ........................................................

  // precalculate sin/cos lookup tables [toxi]
  // circle resolution is determined from the actual used radii 
  // passed to ellipse() method. this will automatically take any 
  // scale transformations into account too

  // [toxi 031031] 
  // changed table's precision to 0.5 degree steps
  // introduced new vars for more flexible code
  static final float sinLUT[], cosLUT[];
  static final float SINCOS_PRECISION = 0.5f;
  static final int SINCOS_LENGTH=(int)(360f/SINCOS_PRECISION);
  static {
    sinLUT = new float[SINCOS_LENGTH];
    cosLUT = new float[SINCOS_LENGTH];
    for (int i=0; i<SINCOS_LENGTH; i++) {
      sinLUT[i] = (float) Math.sin(i * DEG_TO_RAD * SINCOS_PRECISION);
      cosLUT[i] = (float) Math.cos(i * DEG_TO_RAD * SINCOS_PRECISION);
    }
  }


  // ........................................................


  int image_mode   = CORNER;
  int rect_mode    = CORNER;
  int ellipse_mode = CORNER;

  // [toxi031031] new & faster sphere code w/ support flexibile resolutions
  // will be set by sphereDetail() or 1st call to sphere()
  int sphere_detail = 0; 
  float sphereX[], sphereY[], sphereZ[];

  int text_mode = ALIGN_LEFT;
  int text_space = OBJECT_SPACE;
  PFont text_font;
  boolean drawing_text = false;


  //////////////////////////////////////////////////////////////

  // INTERNAL


  /**
   * Constructor for the PGraphics object.
   * This prototype only exists because of annoying 
   * java compilers, and should not be used.
   */
  public PGraphics() { }


  /**
   * Constructor for the PGraphics object
   *
   * @param  iwidth   viewport width
   * @param  iheight  viewport height
   */
  public PGraphics(int iwidth, int iheight) {
    resize(iwidth, iheight);

    // init color/stroke/fill
    defaults();

    // clear geometry for loading later
    //circleX = null;  // so that bagel knows to init these
    //sphereX = null;  // diff from cpp b/c mem in cpp is preallocated
  }


  public void resize(int iwidth, int iheight) {  // ignore
    //System.out.println("resize " + iwidth + " " + iheight);

    width = iwidth;
    height = iheight;
    width1 = width - 1;
    height1 = height - 1;

    allocate();

    // clear the screen with the old background color
    background(background);

    // init perspective projection based on new dimensions
    fov = 60; // at least for now
    eyeX = width / 2.0f;
    eyeY = height / 2.0f;
    eyeDist = eyeY / ((float) tan(PI * fov / 360f));
    nearDist = eyeDist / 10.0f;
    farDist = eyeDist * 10.0f;
    aspect = (float)width / (float)height;

    //beginCamera();
    //perspective(fov, aspect, nearDist, farDist);
    //lookat(eyeX, eyeY, eyeDist,  eyeX, eyeY, 0,  0, 1, 0);
    //endCamera();
    //camera_mode = PERSPECTIVE;
    cameraMode(PERSPECTIVE);
  }


  // broken out because of subclassign for opengl
  private void allocate() {
    pixelCount = width * height;
    pixels = new int[pixelCount];

    // create and clear the zbuffer
    // needs to be cleared here once, because background()
    // will only clear the zbuffer if dimensions is 3, meaning
    // that no initial clear will happen without this one.
    zbuffer = new float[pixelCount];
    //for (int i = 0; i < pixelCount; i++) {
    //zbuffer[i] = MAX_FLOAT;
    //}

    if (hints[NEW_GRAPHICS]) stencil = new int[pixelCount];

    // because of a java 1.1 bug.. unless pixels are registered as
    // opaque before their first run, the memimgsrc will flicker
    // and run very slowly.
    for (int i = 0; i < pixelCount; i++) pixels[i] = 0xffffffff;

    // setup MemoryImageSource
    cm = new DirectColorModel(32, 0x00ff0000, 0x0000ff00, 0x000000ff);;
    mis = new MemoryImageSource(width, height, pixels, 0, width);
    mis.setFullBufferUpdates(true); // does this help ipaq?
    mis.setAnimated(true);
    image = Toolkit.getDefaultToolkit().createImage(mis);

    // use now in the old engine too
    line = new PLine(this);

    // moved from PGraphics constructor since not needed by opengl
    if (hints[NEW_GRAPHICS]) {  
      //line = new PLine(this);
      triangle = new PTriangle(this);

      /*
      // these are all done on beginFrame(), so not doing them here [fry]
      // or they're already set to zero on init [fry]
      // shapes
      shape_index = 0;

      // init vertices
      vertex_count = 0;
      vertex_start = 0;
      vertex_end = 0;

      // init lines
      line = new PLine(this);
      lines_count = 0;

      // init triangles
      triangle = new PTriangle(this);
      triangles_count = 0;

      // textures
      texture_index = 0;
      */
    }
  }


  /**
   *  set engine's default values
   */
  public void defaults() {
    //frameCount = 0; 
    depthTest = true;
    colorMode(RGB, TFF);
    fill(TFF);
    stroke(0);
    strokeWeight(ONE);
    background(204);

    // init shape stuff
    shape = false;
    shapeKind = 0;

    if (!hints[NEW_GRAPHICS]) {
      polygon  = new PPolygon(this);
      fpolygon = new PPolygon(this);
      spolygon = new PPolygon(this);
      spolygon.vertexCount = 4;
      svertices = new float[2][];
    }
    
    text_font = null;

    // better to leave this turned off by default
    noLights();

    // init matrices (must do before lighting)
    matrixStackDepth = 0;

    // init lights
    lightR = new float[MAX_LIGHTS];
    lightG = new float[MAX_LIGHTS];
    lightB = new float[MAX_LIGHTS];
    lightX = new float[MAX_LIGHTS];
    lightY = new float[MAX_LIGHTS];
    lightZ = new float[MAX_LIGHTS];
    lightKind = new int[MAX_LIGHTS];

    lightKind[0] = AMBIENT;
    lightR[0] = 0;
    lightG[0] = 0;
    lightB[0] = 0;
    lightX[0] = 0;
    lightY[0] = 0;
    lightZ[0] = 0;

    lightKind[1] = DIFFUSE;
    lightX[1] = eyeX;
    lightY[1] = eyeY;
    lightZ[1] = eyeDist;
    lightR[1] = ONE;
    lightG[1] = ONE;
    lightB[1] = ONE;

    for (int i = 2; i < MAX_LIGHTS; i++) {
      lightKind[i] = DISABLED;
    }
  }



  //////////////////////////////////////////////////////////////

  // FRAME


  /**
   *  initializes engine before drawing a new frame
   */
  public void beginFrame() {
    if (PApplet.THREAD_DEBUG) System.out.println(" 1 beginFrame");
    /*
    if (camera_mode == -1) {
      //System.out.println("setting up camera");
      beginCamera();
      //setupProjection(PERSPECTIVE);
      perspective(fov, aspect, nearDist, farDist);
      lookat(eyeX, eyeY, eyeDist,  eyeX, eyeY, 0,  0, 1, 0);
      endCamera();
      camera_mode = PERSPECTIVE;
    }
    */

    /*
    if ((_background) && hints[OLD_BACKGROUND]) {
      clear();
    }
    */

    resetMatrix(); // reset model matrix

    normalX = 0;
    normalY = 0;
    normalZ = ONE;

    if (hints[NEW_GRAPHICS]) {
      // reset shapes
      shape_index = 0;

      // reset vertices
      vertex_count = 0;
      vertex_start = 0;
      vertex_end = 0;

      // reset lines
      lines_count = 0;
      line.reset();

      // reset triangles
      triangles_count = 0;
      triangle.reset();

      // reset textures
      texture_index = 0;
    }
  }


  /**
   *  indicates a completed frame
   */
  public void endFrame() {
    if (PApplet.THREAD_DEBUG) System.out.println("  2 endFrame");

    if (hints[NEW_GRAPHICS]) {

      // no need to z order and render
      // shapes were already rendered in endShape();
      // (but can't return, since needs to update memimgsrc
      if (z_order) {

        // SORT TRIANGLES
        //quick_sort_triangles();

        // SORT LINES
        //quick_sort_triangles();

        // RENDER TRIANGLES
        for (int i = 0; i < triangles_count; i ++) {
          //System.out.println("rendering triangle " + i);

          float a[] = vertices[triangles[i][VA]];
          float b[] = vertices[triangles[i][VB]];
          float c[] = vertices[triangles[i][VC]];
          int tex = triangles[i][TEX];
          int index = triangles[i][TI];

          triangle.reset();

          if (tex > -1 && textures[tex] != null) {
            triangle.setTexture(textures[tex]);
            triangle.setUV(a[U], a[V], b[U], b[V], c[U], c[V]);
          }

          triangle.setIntensities(a[R], a[G], a[B], a[A],
                                  b[R], b[G], b[B], b[A],
                                  c[R], c[G], c[B], c[A]);

          triangle.setVertices(a[X], a[Y], a[Z],
                               b[X], b[Y], b[Z],
                               c[X], c[Y], c[Z]);

          triangle.setIndex(index);
          triangle.render();
        }

        // RENDER LINES
        for (int i = 0; i < lines_count; i ++) {
          float a[] = vertices[lines[i][PA]];
          float b[] = vertices[lines[i][PB]];
          int index = lines[i][LI];

          line.reset();

          line.setIntensities(a[SR], a[SG], a[SB], a[SA],
                              b[SR], b[SG], b[SB], b[SA]);

          line.setVertices(a[X], a[Y], a[Z],
                           b[X], b[Y], b[Z]);

          line.setIndex(index);

          line.draw();
        }
      }
    }

    // note that the zbuffer was messed with for the next frame
    //zbufferTainted = (dimensions != 0);

    // BLIT TO IMAGE (SCREEN)
    //mis.newPixels(pixels, cm, 0, width);
    //frameCount++;

    // moving this back here (post-68) because of macosx thread problem
    mis.newPixels(pixels, cm, 0, width);
  }


  //////////////////////////////////////////////////////////////

  // MEMORY HANDLING (NEW_GRAPHICS)


  public final float[] nextVertex() {
    if (!hints[NEW_GRAPHICS]) return polygon.nextVertex();

    if (vertex_count == vertices.length) {
      float temp[][] = new float[vertex_count<<1][VERTEX_FIELD_COUNT];
      System.arraycopy(vertices, 0, temp, 0, vertex_count);
      vertices = temp;
      System.out.println("allocating more vertices " + vertices.length);
    }

    return vertices[vertex_count++];
  }


  public final void addTexture(PImage image) {

    if (texture_index == textures.length - 1) {
      PImage temp[] = new PImage[texture_index<<1];
      System.arraycopy(textures, 0, temp, 0, texture_index);
      textures = temp;
      System.out.println("allocating more textures " + textures.length);
    }

    if(textures[0] != null) {
      texture_index++;
    }

    textures[texture_index] = image;

    return;
  }


  public final void addLine(int a, int b) {

    if (lines_count == lines.length) {
      int temp[][] = new int[lines_count<<1][LINE_FIELD_COUNT];
      System.arraycopy(lines, 0, temp, 0, lines_count);
      lines = temp;
      System.out.println("allocating more lines " + lines.length);
    }

    lines[lines_count][PA] = a;
    lines[lines_count][PB] = b;

    // index -1 means line is a normal stroke
    // other values indicate special blender mode
    if(smooth && !_stroke) {
      lines[lines_count][LI] = shape_index;
    } else {
      lines[lines_count][LI] = -1;
    }

    lines[lines_count][SM] = strokeMiter | strokeJoin; //_strokeMode;

    lines_count ++;

    return;
  }

  public final void addTriangle(int a, int b, int c) {

    if (triangles_count == triangles.length) {
      int temp[][] = new int[triangles_count<<1][TRIANGLE_FIELD_COUNT];
      System.arraycopy(triangles, 0, temp, 0, triangles_count);
      triangles = temp;
      System.out.println("allocating more triangles " + triangles.length);
    }

    triangles[triangles_count][VA] = a;
    triangles[triangles_count][VB] = b;
    triangles[triangles_count][VC] = c;

    if (textureImage == null) {
      triangles[triangles_count][TEX] = -1;
    } else {
      triangles[triangles_count][TEX] = texture_index;
    }

    triangles[triangles_count][TI] = shape_index;

    triangles_count ++;

    return;
  }


  //////////////////////////////////////////////////////////////

  // LIGHTING AND COLOR

  /**
   * lighting calculation of final colour.
   * for now, ip is being done in screen space (transformed), 
   * because the normals are also being transformed
   *
   * @param  r        red component of object's colour
   * @param  g        green of object's colour
   * @param  b        blue of object's colour
   * @param  ix       x coord of intersection
   * @param  iy       y coord of intersection
   * @param  iz       z coord of intersection
   * @param  nx       x coord of normal vector
   * @param  ny       y coord of normal
   * @param  nz       z coord of normal
   * @param  target   float array to store result
   * @param  toffset  starting index in target array
   */
  private void calc_lighting(float r, float g, float b,
                             float ix, float iy, float iz,
                             float nx, float ny, float nz, 
                             float target[], int toffset) {
    //System.out.println("calc_lighting normals " + nx + " " + ny + " " + nz);

    if (!lighting) {
      target[toffset + 0] = r;
      target[toffset + 1] = g;
      target[toffset + 2] = b;
      return;
    }

    float nlen = mag(nx, ny, nz);
    if (nlen != 0) {
      nx /= nlen; ny /= nlen; nz /= nlen;
    }

    // get direction based on inverse of perspective(?) matrix
    //screenToWorld.getDirection(x + 0.5, y + 0.5, d);
    
    /*
      // q in screen space
      double qs[] = new double[4];
      qs[0] = x;
      qs[1] = y;
      qs[2] = 0;
      qs[3] = 1;

      // q in world space
      // transformed 4 vector (homogenous coords)
      double qw[] = new double[4];
      multiply(mat, qs, qw);
      dw.x = qw[0] * mat[3][2] - qw[3] * mat[0][2];
      dw.y = qw[1] * mat[3][2] - qw[3] * mat[1][2];
      dw.z = qw[2] * mat[3][2] - qw[3] * mat[2][2];
    */
    // multiply (inverse matrix) x (x y 0 1) = qw

    /*
      // CALC OF DIRECTION OF EYE TO SCREEN/OBJECT
      // !!! don't delete this code.. used for specular
      float qwx = i00*sx + i01*sy + i03;
      float qwy = i10*sx + i11*sy + i13;
      float qwz = i20*sx + i21*sy + i23;
      float qww = i30*sx + i31*sy + i33;

      float dwx = qwx*i32 - qww*i02;
      float dwy = qwy*i32 - qww*i12;
      float dwz = qwz*i32 - qww*i22;
    */

    //double kdr = material.kDiffuseReflection;   == 1
    //double ksr = material.kSpecularReflection;  == 0
    //double e = material.shadingExponent;        == 0
    //RgbColor Cmat = material.color;             == r, g, b

    // Direction of light i from ip, Li = L[i].position - ip
    //Vector3 Li = new Vector3();

    // Radiance of a light source, a color 
    //RgbColor Ii = new RgbColor();

    // The halfway vector
    //Vector3 Hi = new Vector3();

    //float N_dot_Li, N_dot_Hi, N_dot_Hi_e;

    float diffuse_r = 0; // = lights[0].r;  // sum in ambient term
    float diffuse_g = 0; // = lights[0].g;
    float diffuse_b = 0; // = lights[0].b;

    //float specular_r = 0;
    //float specular_g = 0;
    //float specular_b = 0;

    for (int i = 1; i < MAX_LIGHTS; i++) {
      if (lightKind[i] == DISABLED) break;

      //Light light = (Light) list.value;
      //Ii = light.color;

      //Vector3.subtract(light.position, ip, Li);
      //Li.normalize();
      // li is the vector of the light as it points towards the point
      // at which it intersects the object
      float lix = lightX[i] - ix;
      float liy = lightY[i] - iy;
      float liz = lightZ[i] - iz;
      float m = mag(lix, liy, liz);
      if (m != 0) {
        lix /= m; liy /= m; liz /= m;
      }
      float n_dot_li = (nx*lix + ny*liy + nz*liz);
      //N_dot_Li = Vector3.dotProduct(N, Li);

      //if (N_dot_Li > 0.0) {
      if (n_dot_li > 0) {
        //System.out.println("n_dot_li = " + n_dot_li);
        diffuse_r += lightR[i] * n_dot_li;
        diffuse_g += lightG[i] * n_dot_li;
        diffuse_b += lightB[i] * n_dot_li;

        /*
          // not doing any specular for now

          //Vector3.subtract(light.position, direction, Hi);
          float hix = lights[i].x - dwx;
          float hiy = lights[i].y - dwy;
          float hiz = lights[i].z - dwz;
          float n_dot_hi = (nx*hix + ny*hiy + nz*hiz);
          //N_dot_Hi = Vector3.dotProduct(N, Hi);
          if (n_dot_hi > 0) {
          //N_dot_Hi_e = pow(N_dot_Hi / Hi.getLength(), e);
          // since e == 1 for now, this can be simplified
          //float n_dot_hi_e = pow(n_dot_hi / sqrt(hix*hix + hiy*hiy + hiz*hiz), e);
          float n_dot_hi_e = n_dot_hi / 
          sqrt(hix*hix + hiy*hiy + hiz*hiz);
          specular_r += lights[i].r * n_dot_hi_e;
          specular_g += lights[i].g * n_dot_hi_e;
          specular_b += lights[i].b * n_dot_hi_e;
          //specular_r += Ii.r * N_dot_Hi_e;
          //specular_g += Ii.g * N_dot_Hi_e;
          //specular_b += Ii.b * N_dot_Hi_e;
          }
        */
      }
    }
    // specular reflection (ksr) is set to zero, so simplify
    //I.r = (kdr * Cmat.r * diffuse_r) + (ksr * specular_r);
    //I.g = (kdr * Cmat.g * diffuse_g) + (ksr * specular_g);
    //I.b = (kdr * Cmat.b * diffuse_b) + (ksr * specular_b);

    //System.out.println(r + " " + g + " " + b + "  " + 
    //               diffuse_r + " " + diffuse_g + " " + diffuse_b);

    // TODO ** this sucks! **
    //System.out.println(lights[0].r + " " + lights[0].g + " " +
    //               lights[0].b);

    target[toffset+0] = lightR[0] + (r * diffuse_r); 
    target[toffset+1] = lightG[0] + (g * diffuse_g); 
    target[toffset+2] = lightB[0] + (b * diffuse_b); 

    if (target[toffset+0] > ONE) target[toffset+0] = ONE;
    if (target[toffset+1] > ONE) target[toffset+1] = ONE;
    if (target[toffset+2] > ONE) target[toffset+2] = ONE;

    //if (calc1) {
    //calcR1 = lights[0].r + (r * diffuse_r); if (calcR1 > 1) calcR1 = 1;
    //calcG1 = lights[0].g + (g * diffuse_g); if (calcG1 > 1) calcG1 = 1;
    //calcB1 = lights[0].b + (b * diffuse_b); if (calcB1 > 1) calcB1 = 1;

    //System.out.println(255*calcR1 + " " + 255*calcG1 + " " + 255*calcB1);
    //} else {
    //calcR2 = lights[0].r + (r * diffuse_r); if (calcR2 > 1) calcR2 = 1;
    //calcG2 = lights[0].g + (g * diffuse_g); if (calcG2 > 1) calcG2 = 1;
    //calcB2 = lights[0].b + (b * diffuse_b); if (calcB2 > 1) calcB2 = 1;
    //System.out.println(255*calcR2 + " " + 255*calcG2 + " " + 255*calcB2);
    //}
  }


  //////////////////////////////////////////////////////////////

  // SHAPES

  /**
   * start a new shape of type POLYGON
   */
  public void beginShape() {
    beginShape(POLYGON);
  }


  /**
   * start a new shape
   *
   * @param  kind  indicates shape type
   */
  public void beginShape(int kind) {
    shape = true;
    shapeKind = kind;
    
    if (hints[NEW_GRAPHICS]) {
      shape_index = shape_index + 1;
      if (shape_index == -1) {
        shape_index = 0;
      }

      if (z_order == true) {
        // continue with previous vertex, line and triangle count
        // all shapes are rendered at endFrame();
        vertex_start = vertex_count;
        vertex_end = 0;

      } else {
        // reset vertex, line and triangle information
        // every shape is rendered at endShape();
        vertex_count = 0;
        line.reset();
        lines_count = 0;
        triangle.reset();
        triangles_count = 0;
      }

      textureImage = null;
    } else {  // OLD_GRAPHICS
      polygon.reset(0);
      fpolygon.reset(4);
      spolygon.reset(4);

      texture = false;
      polygon.interpUV = false;
    }
    cvertexIndex = 0;
    cverticesFlat = true;

    unchangedZ = true;
    strokeChanged = false;
    fillChanged = false;
    normalChanged = false;
  }


  /**
   *  set texture image for current shape
   *  needs to be called between @see beginShape and @see endShape
   *
   * @param  image  reference to a PImage object
   */
  //public void textureImage(PImage image) {
  public void texture(PImage image) {
    if (hints[NEW_GRAPHICS]) {
      if (z_order == true) {
        addTexture(image);
      } else {
        triangle.setTexture(image);
      }
      textureImage = image;

    } else {  // OLD_GRAPHICS
      texture = true;    polygon.texture(image);
    }
  }


  /**
   * set texture mode to either IMAGE_SPACE (more intuitive
   * for new users) or NORMAL_SPACE (better for advanced chaps)
   */
  public void textureMode(int texture_mode) {
    this.texture_mode = texture_mode;
  }


  /**
   * set UV coords for the next vertex in the current shape.
   * this is ugly as its own fxn, and will almost always be
   * coincident with a call to vertex, so it's being moved
   * to be an optional param of and overloaded vertex()
   *
   * @param  u  U coordinate (X coord in image 0<=X<=image width)
   * @param  v  V coordinate (Y coord in image 0<=Y<=image height)
   */
  //public void vertexTexture(float u, float v) {
  protected void vertex_texture(float u, float v) {
    if (hints[NEW_GRAPHICS]) {
      if (textureImage == null) {
        message(PROBLEM, "gotta use texture() " + 
                "after beginShape() and before vertexTexture()");
        return;
      }
      if (texture_mode == IMAGE_SPACE) {
        textureU = (u < textureImage.width) ? u : textureImage.width;
        if (textureU < 0) textureU = 0;
        textureV = (v < textureImage.height) ? v : textureImage.height;
        if (textureV < 0) textureV = 0;
        textureU = u / (float) textureImage.width;
        textureV = v / (float) textureImage.height;

      } else {  // NORMAL_SPACE
        textureU = u;
        textureV = v;
        if (textureU < 0) textureU = 0;
        if (textureV < 0) textureV = 0;
        if (textureU > ONE) textureU = ONE;
        if (textureV > ONE) textureV = ONE;
      }   

    } else {  // OLD_GRAPHICS
      if (!texture) {
        message(PROBLEM, "gotta use texture() " + 
                "after beginShape() and before vertex()");
        return;
      }
      if (texture_mode == IMAGE_SPACE) {
        textureU = (u < polygon.twidth) ? u : polygon.twidth;
        if (textureU < 0) textureU = 0;

        textureV = (v < polygon.theight) ? v : polygon.theight;
        if (textureV < 0) textureV = 0;

      } else {
        if (textureU < 0) textureU = 0;
        if (textureV < 0) textureV = 0;
        if (textureU > ONE) textureU = ONE;
        if (textureV > ONE) textureV = ONE;

        textureU = u * polygon.twidth;
        textureV = v * polygon.theight; 
      }
    }
  }


  /**
   * sets the current normal.. may apply to vertices if inside
   * a beginShape, or to whatever else if outside 
   */
  //public void vertexNormal(float nx, float ny, float nz) {
  public void normal(float nx, float ny, float nz) {
    if (shape) {  // if inside shape
      if (!normalChanged) {
        if (hints[NEW_GRAPHICS]) {
          // set normals for vertices till now to the same thing
          for (int i = vertex_start; i < vertex_end; i++) {
            vertices[i][NX] = normalX;
            vertices[i][NY] = normalY;
            vertices[i][NZ] = normalZ;
          }

          // [vertex change]
          for (int i = vertex_start; i < vertex_end; i++) {
            vertices[i][NX] = normalX;
            vertices[i][NY] = normalY;
            vertices[i][NZ] = normalZ;
          }

        } else {  // OLD_GRAPHICS
          // set normals for vertices till now to the same thing
          for (int i = 0; i < polygon.vertexCount; i++) {
            polygon.vertices[i][NX] = normalX;
            polygon.vertices[i][NY] = normalY;
            polygon.vertices[i][NZ] = normalZ;
          }
        }
        normalChanged = true;
      }
    }

    normalX = nx;
    normalY = ny;
    normalZ = nz;
  }


  public void vertex(float x, float y) {
    setup_vertex(nextVertex(), x, y, 0);
  }


  public void vertex(float x, float y, float u, float v) {
    vertex_texture(u, v);
    setup_vertex(nextVertex(), x, y, 0);
  }


  public void vertex(float x, float y, float z) {
    unchangedZ = false;
    dimensions = 3;
    setup_vertex(nextVertex(), x, y, z);
  }


  public void vertex(float x, float y, float z,  
                     float u, float v) {
    vertex_texture(u, v);
    unchangedZ = false;
    dimensions = 3;
    setup_vertex(nextVertex(), x, y, z);
  }


  private void setup_vertex(float vertex[], float x, float y, float z) {
    cvertexIndex = 0; // reset curves to start

    vertex[MX] = x;
    vertex[MY] = y;
    vertex[MZ] = z;

    if (_fill) {
      vertex[R] = fillR;
      vertex[G] = fillG;
      vertex[B] = fillB;
      vertex[A] = fillA;
    }

    if (_stroke) {
      vertex[SR] = strokeR;
      vertex[SG] = strokeG;
      vertex[SB] = strokeB;
      vertex[SA] = strokeA;
      vertex[WT] = strokeWeight;
    }

    // this complicated if construct may defeat the purpose
    if (((hints[NEW_GRAPHICS]) && (textureImage != null)) ||
        ((!hints[NEW_GRAPHICS]) && texture)) {
      vertex[U] = textureU;
      vertex[V] = textureV;
    }
    
    if (normalChanged) {
      vertex[NX] = normalX;
      vertex[NY] = normalY; 
      vertex[NZ] = normalZ;
    }
  }


  /* 
     CURVES

     'curve' is a catmull-rom curve

     curve(x1, y1, x2, y2, x3, y3, x4, y4) 
     // is equivalent to:
     beginShape();
     curveVertex(x1, y1);
     curveVertex(x1, y1);
     curveVertex(x2, y2);
     curveVertex(x3, y3);
     curveVertex(x4, y4);
     curveVertex(x4, y4);
     endShape();
     // the first and last points are doubled, because the catmull-rom
     // algorithm needs the point 'before' and 'after' the segment to 
     // guide how the curve will begin. it's six calls because without
     // doubling the first and last point, curve() produces mostly 
     // straight lines. not much fun.


     'bezier' is a bezier curve

     bezier(x1, y1, x2, y2, x3, y3, x4, y4)
     // is equivalent to:
     beginShape();
     bezierVertex(x1, y1);
     bezierVertex(x2, y2);
     bezierVertex(x3, y3);
     bezierVertex(x4, y4);
     endShape();
     // the first and last points are the on-curve points
     // the middle two are the 'control' points, or 'handles' 
     // in an application like illustrator.

     // in postscript-speak, this would be:
     moveto(x1, y1);
     curveto(x2, y2, x3, y3, x4, y4);
     // if you were to try and continue that curve like so:
     curveto(x5, y5, x6, y6, x7, y7);
     // this would be done in bagel by adding these statements:
     curveVertex(x4, y4);
     curveVertex(x5, y5);
     curveVertex(x6, y6);
     curveVertex(x7, y7);
     // note that x4/y4 are being pulled from the previous
     // curveto and used again.

     this may be a bit more verbose, but in general, decisions opted 
     for maximum flexibility, since these beginShape() commands are 
     intended as a bit lower-level. rather than having many types of 
     curveto (curve to corner, and several others described in the 
     postscript and illustrator specs) let someone else implement a 
     nice moveto/lineto/curveto library on top. in fact, it's tempting 
     that we may put one in there ourselves.

     another method for bezier (though not implemented this way)
     1. first start with a call to vertex()
     2. every three calls to bezierVertex produce a new segment
     this option seemed no good because of the confusion of mixing
     vertex and bezierVertex calls. 
  */

  private void c_vertex(float x, float y, float z, boolean bezier) {
    // if more than 128 points, shift everything back to the beginning
    if (cvertexIndex == CVERTEX_ALLOC) {
      System.arraycopy(cvertex[CVERTEX_ALLOC-3], 0, 
                       cvertex[0], 0, VERTEX_FIELD_COUNT);
      System.arraycopy(cvertex[CVERTEX_ALLOC-2], 0, 
                       cvertex[1], 0, VERTEX_FIELD_COUNT);
      System.arraycopy(cvertex[CVERTEX_ALLOC-1], 0, 
                       cvertex[2], 0, VERTEX_FIELD_COUNT);
      cvertexIndex = 3;
    }
    // add the vertex here
    // cvertexIndex and cvertexCount are reset to zero 
    // when regular vertex() is called, so store it
    int savedIndex = cvertexIndex + 1;
    if (cverticesFlat && (z != 0)) cverticesFlat = false;
    setup_vertex(cvertex[cvertexIndex], x, y, z);
    cvertexIndex = savedIndex; // restore cvertexIndex

    // draw a segment if there are enough points
    if (cvertexIndex > 3) {
      if (bezier) {
        if ((cvertexIndex % 4) == 0) {
          if (!bezier_inited) bezier_init();

          if (cverticesFlat) {
            spline_segment(cvertex[cvertexIndex-4][MX], 
                           cvertex[cvertexIndex-4][MY], 
                           cvertex[cvertexIndex-3][MX], 
                           cvertex[cvertexIndex-3][MY], 
                           cvertex[cvertexIndex-2][MX], 
                           cvertex[cvertexIndex-2][MY], 
                           cvertex[cvertexIndex-1][MX], 
                           cvertex[cvertexIndex-1][MY],
                           cvertex[cvertexIndex-4][MX], 
                           cvertex[cvertexIndex-4][MY], 
                           bezier_draw, bezier_segments);
          } else {
            spline_segment(cvertex[cvertexIndex-4][MX], 
                           cvertex[cvertexIndex-4][MY], 
                           cvertex[cvertexIndex-4][MZ], 
                           cvertex[cvertexIndex-3][MX], 
                           cvertex[cvertexIndex-3][MY], 
                           cvertex[cvertexIndex-3][MZ], 
                           cvertex[cvertexIndex-2][MX], 
                           cvertex[cvertexIndex-2][MY], 
                           cvertex[cvertexIndex-2][MZ], 
                           cvertex[cvertexIndex-1][MX], 
                           cvertex[cvertexIndex-1][MY],
                           cvertex[cvertexIndex-1][MZ], 
                           cvertex[cvertexIndex-4][MX], 
                           cvertex[cvertexIndex-4][MY], 
                           cvertex[cvertexIndex-4][MZ], 
                           bezier_draw, bezier_segments);
          }
        }
      } else {  // !bezier
        if (!curve_inited) curve_init();

        if (cverticesFlat) {
          spline_segment(cvertex[cvertexIndex-4][MX], 
                         cvertex[cvertexIndex-4][MY], 
                         cvertex[cvertexIndex-3][MX], 
                         cvertex[cvertexIndex-3][MY], 
                         cvertex[cvertexIndex-2][MX], 
                         cvertex[cvertexIndex-2][MY], 
                         cvertex[cvertexIndex-1][MX], 
                         cvertex[cvertexIndex-1][MY],
                         cvertex[cvertexIndex-3][MX], 
                         cvertex[cvertexIndex-3][MY], 
                         curve_draw, curve_segments);
        } else {
          spline_segment(cvertex[cvertexIndex-4][MX], 
                         cvertex[cvertexIndex-4][MY], 
                         cvertex[cvertexIndex-4][MZ], 
                         cvertex[cvertexIndex-3][MX], 
                         cvertex[cvertexIndex-3][MY], 
                         cvertex[cvertexIndex-3][MZ], 
                         cvertex[cvertexIndex-2][MX], 
                         cvertex[cvertexIndex-2][MY], 
                         cvertex[cvertexIndex-2][MZ], 
                         cvertex[cvertexIndex-1][MX], 
                         cvertex[cvertexIndex-1][MY],
                         cvertex[cvertexIndex-1][MZ],
                         cvertex[cvertexIndex-3][MX], 
                         cvertex[cvertexIndex-3][MY], 
                         cvertex[cvertexIndex-3][MZ], 
                         curve_draw, curve_segments);
        }
      }
    }
    // spline_segment() calls vertex(), which clears cvertexIndex
    cvertexIndex = savedIndex; 
  }


  public void bezierVertex(float x, float y) {
    c_vertex(x, y, 0, true);
  }

  public void bezierVertex(float x, float y, float z) {
    c_vertex(x, y, z, true);
  }

  public void curveVertex(float x, float y) {
    c_vertex(x, y, 0, false);
  }

  public void curveVertex(float x, float y, float z) {
    c_vertex(x, y, z, false);
  }


  public void endShape_newgraphics() {
    // clear the 'shape drawing' flag in case of early exit
    shape = false;

    vertex_end = vertex_count;

    // ------------------------------------------------------------------
    // CREATE LINES

    int increment = 1;
    int stop = 0;
    int counter = 0;

    // make lines for both stroke triangles
    // and antialiased triangles
    boolean check = _stroke || smooth;

    // quick fix [rocha]
    // antialiasing fonts with lines causes some artifacts
    if (textureImage != null && textureImage.format == ALPHA) {
      check = false;
    }

    if (check) {
      switch (shapeKind) {

        case POINTS:
        {
          stop = vertex_end;
          for (int i = vertex_start; i < stop; i++) {
            addLine(i,i);
          }
        }
        break;

        case LINES:
        case LINE_STRIP:
        case LINE_LOOP:
        {
          // store index of first vertex
          int first = lines_count;
          stop = vertex_end-1;
          increment = (shapeKind == LINES) ? 2 : 1;

          for (int i = vertex_start; i < stop; i+=increment) {
            addLine(i,i+1);
          }

          if (shapeKind == LINE_LOOP) {
            addLine(stop,lines[first][PA]);
          }
        }
        break;

        case TRIANGLES:
        case TRIANGLE_STRIP:
        {
          // first draw all vertices as a line strip
          stop = vertex_end-1;

          for (int i = vertex_start; i < stop; i++) {
            counter = i - vertex_start;
            addLine(i,i+1);
            if ((shapeKind == TRIANGLES) && (counter%3 == 1)) {
              i++;
            }
          }

          // then draw from vertex (n) to (n+2)
          stop = vertex_end-2;
          increment = (shapeKind == TRIANGLE_STRIP) ? 1 : 3;

          for (int i = vertex_start; i < stop; i+=increment) {
            addLine(i,i+2);
          }
        }
        break;

        case QUADS:
        case QUAD_STRIP:
        {
          // first draw all vertices as a line strip
          stop = vertex_end - 1;

          for (int i = vertex_start; i < stop; i++) {
            counter = i - vertex_start;
            addLine(i,i+1);
            if ((shapeKind == QUADS) && (counter%4 == 2)) {
              i++;
            }
          }

          // then draw from vertex (n) to (n+3)
          stop = vertex_end-2;
          increment = (shapeKind == QUAD_STRIP) ? 2 : 4;

          for (int i=vertex_start; i < stop; i+=increment) {
            addLine(i,i+3);
          }
        }
        break;

        case POLYGON:
        case CONCAVE_POLYGON:
        case CONVEX_POLYGON:
        {
          // store index of first vertex
          int first = lines_count;
          stop = vertex_end - 1;

          for (int i=vertex_start; i < stop; i++) {
            addLine(i,i+1);
          }
          // draw the last line connecting back to the first point in poly
          addLine(stop,lines[first][PA]);
        }
        break;
      }
    }

    // ------------------------------------------------------------------
    // CREATE TRIANGLES

    if (_fill) {
      switch (shapeKind) {
        case TRIANGLES:
        case TRIANGLE_STRIP:
        {
          stop = vertex_end - 2;
          increment = (shapeKind == TRIANGLES) ? 3 : 1;
          for (int i = vertex_start; i < stop; i += increment) {
            addTriangle(i, i+1, i+2);
          }
        }
        break;

        case QUADS:
        case QUAD_STRIP:
        {
          stop = vertex_count-3;
          increment = (shapeKind == QUADS) ? 4 : 2;

          for (int i = vertex_start; i < stop; i += increment) {
            // first triangle
            addTriangle(i, i+1, i+2);
            // second triangle
            addTriangle(i, i+2, i+3);
          }
        }
        break;

        case POLYGON:
        case CONCAVE_POLYGON:
        case CONVEX_POLYGON:
        {
            triangulate_polygon();
        }
        break;
      }
    }

    // ------------------------------------------------------------------
    // POINTS FROM MODEL (MX, MY, MZ) TO VIEW SPACE (VX, VY, VZ)

    //if ((camera_mode == PERSPECTIVE) && (dimensions == 0)) {
    if ((camera_mode != CUSTOM) && (dimensions == 0)) {
      // flat 2D
      for (int i = vertex_start; i < vertex_end; i++) {
        vertices[i][X] = vertices[i][MX];
        vertices[i][Y] = vertices[i][MY];
      }

    } else if ((camera_mode != CUSTOM) && (dimensions == 2)) {

      // affine transform, ie rotated 2D
      for (int i = vertex_start; i < vertex_end; i++) {
        vertices[i][X] = m00*vertices[i][MX] + m01*vertices[i][MY] + m03;
        vertices[i][Y] = m10*vertices[i][MX] + m11*vertices[i][MY] + m13;
      }

    } else {
      // dimension = 3 or camera mode is custom

      for (int i = vertex_start; i < vertex_end; i++) {
        float vertex[] = vertices[i];

        vertex[VX] = m00*vertex[MX] + m01*vertex[MY] + m02*vertex[MZ] + m03;
        vertex[VY] = m10*vertex[MX] + m11*vertex[MY] + m12*vertex[MZ] + m13;
        vertex[VZ] = m20*vertex[MX] + m21*vertex[MY] + m22*vertex[MZ] + m23;
        vertex[VW] = m30*vertex[MX] + m31*vertex[MY] + m32*vertex[MZ] + m33;
      }
    }


    // ------------------------------------------------------------------
    // CULLING

    // simple culling
    // if they share the same clipping code, then cull
    /*
    boolean clipped = true;
    float x = vertices[vertex_start][X];
    float y = vertices[vertex_start][Y];
    int clipCode = ((y < 0 ? 8 : 0) | (y > height1 ? 4 : 0) |
            (x < 0 ? 2 : 0) | (x > width1 ? 1 : 0));
    for (int i = vertex_start + 1; i < vertex_end; i++) {
      x = vertices[i][X];
      y = vertices[i][Y];
      int code = ((y < 0 ? 8 : 0) | (y > height1 ? 4 : 0) |
            (x < 0 ? 2 : 0) | (x > width1 ? 1 : 0));
      if (code != clipCode) {
        clipped = false;
        break;
      }
    }
    if ((clipCode != 0) && clipped) return;
    */

    // ------------------------------------------------------------------
    // NORMALS

    if (!normalChanged) {
      // fill first vertext w/ the normal
      vertices[vertex_start][NX] = normalX;
      vertices[vertex_start][NY] = normalY;
      vertices[vertex_start][NZ] = normalZ;
      // homogenousNormals saves time from below, which is expensive
    }

    for (int i = vertex_start; i < (normalChanged ? vertex_end : 1); i++) {
      float v[] = vertices[i];
      float nx = m00*v[NX] + m01*v[NY] + m02*v[NZ] + m03;
      float ny = m10*v[NX] + m11*v[NY] + m12*v[NZ] + m13;
      float nz = m20*v[NX] + m21*v[NY] + m22*v[NZ] + m23;
      float nw = m30*v[NX] + m31*v[NY] + m32*v[NZ] + m33;

      if (nw != 0) {
        // divide by perspective coordinate
        v[NX] = nx/nw; v[NY] = ny/nw; v[NZ] = nz/nw;
      } else {
        // can't do inline above
        v[NX] = nx; v[NY] = ny; v[NZ] = nz;
      }

      float nlen = mag(v[NX], v[NY], v[NZ]);  // normalize
      if (nlen != 0) {
        v[NX] /= nlen; v[NY] /= nlen; v[NZ] /= nlen;
      }
    }


    // ------------------------------------------------------------------
    // COLORS

    // calculate RGB for each vertex
    //if (homogenousColors && !lighting) {  // if no lighting, do only once

    if (!lighting) {

      // all the values for r, g, b have been set with calls to vertex()
      // (no need to re-calculate anything here)

    } else {

      float f[] = vertices[vertex_start];

      for (int i = vertex_start; i < vertex_end; i++) {
        float v[] = vertices[i];
        if (normalChanged) {
          if (_fill) {
            calc_lighting(v[R],  v[G], v[B], v[MX], v[MY], v[MZ],
                       v[NX], v[NY], v[NZ], v, R);
          }

          if (_stroke) {
            calc_lighting(v[SR], v[SG], v[SB], v[MX], v[MY], v[MZ],
                       v[NX], v[NY], v[NZ], v, SR);
          }
        } else {
          if (_fill) {
            calc_lighting(v[R],  v[G], v[B], v[MX], v[MY], v[MZ],
                       f[NX], f[NY], f[NZ], v, R);
          }
          if (_stroke) {
            calc_lighting(v[SR], v[SG], v[SB], v[MX], v[MY], v[MZ],
                       f[NX], f[NY], f[NZ], v, SR);
          }
        }
      }
    }

    // ------------------------------------------------------------------
    // NEAR PLANE CLIPPING AND CULLING

    //if ((camera_mode == PERSPECTIVE) && (dimensions == 3) && clip) {
      //float z_plane = eyeDist + ONE;

      //for (int i = 0; i < lines_count; i ++) {
          //line3dClip();
      //}

      //for (int i = 0; i < triangles_count; i ++) {
      //}
    //}

    // ------------------------------------------------------------------
    // POINTS FROM VIEW SPACE (MX, MY, MZ) TO SCREEN SPACE (X, Y, Z)

    if ((camera_mode == PERSPECTIVE) && (dimensions == 3)) {

      for (int i = vertex_start; i < vertex_end; i++) {
        float vertex[] = vertices[i];

        float ox = p00*vertex[VX] + p01*vertex[VY] + p02*vertex[VZ] + p03*vertex[VW];
        float oy = p10*vertex[VX] + p11*vertex[VY] + p12*vertex[VZ] + p13*vertex[VW];
        float oz = p20*vertex[VX] + p21*vertex[VY] + p22*vertex[VZ] + p23*vertex[VW];
        float ow = p30*vertex[VX] + p31*vertex[VY] + p32*vertex[VZ] + p33*vertex[VW];

        if (ow != 0) {
          ox /= ow; oy /= ow; oz /= ow;
        }

        vertex[X] = width * (ONE + ox) / 2.0f;
        vertex[Y] = height * (ONE + oy) / 2.0f;
        vertex[Z] = (oz + ONE) / 2.0f;
      }
    }

    // ------------------------------------------------------------------
    // RENDER SHAPES FILLS HERE WHEN NOT Z_ORDERING

    if (z_order == true) {
      return;
    }

    // render all triangles in current shape
    if (_fill) {

      for (int i = 0; i < triangles_count; i ++) {
        float a[] = vertices[triangles[i][VA]];
        float b[] = vertices[triangles[i][VB]];
        float c[] = vertices[triangles[i][VC]];
        int index = triangles[i][TI];

        if (textureImage != null) {
          triangle.setUV(a[U], a[V], b[U], b[V], c[U], c[V]);
        }

        triangle.setIntensities(a[R], a[G], a[B], a[A],
                                b[R], b[G], b[B], b[A],
                                c[R], c[G], c[B], c[A]);

        triangle.setVertices(a[X], a[Y], a[Z],
                             b[X], b[Y], b[Z],
                             c[X], c[Y], c[Z]);

        triangle.setIndex(index);

        triangle.render();
      }
    }

    // ------------------------------------------------------------------
    // DRAW POINTS, LINES AND SHAPE STROKES

    // draw all lines in current shape
    if (_stroke || smooth) {

      for (int i = 0; i < lines_count; i ++) {
        float a[] = vertices[lines[i][PA]];
        float b[] = vertices[lines[i][PB]];
        int index = lines[i][LI];

        line.setIntensities(a[SR], a[SG], a[SB], a[SA],
                            b[SR], b[SG], b[SB], b[SA]);

        line.setVertices( a[X], a[Y], a[Z],
                          b[X], b[Y], b[Z]);

        line.setIndex(index);

        line.draw();
      }
    }

    shapeKind = 0;
  }



  //////////////////////////////////////////////////////////////

  // GEOMETRY STUFF


  // triangulate the current polygon
  private void triangulate_polygon() {

    // simple ear clipping polygon triangulation
    // addapted from code by john w. ratcliff (jratcliff@verant.com)

    // first we check if the polygon goes clockwise or counterclockwise
    float area = 0.0f;
    for (int p = vertex_end - 1, q = vertex_start; q < vertex_end; p = q++) {
      area += vertices[q][X] * vertices[p][Y] - vertices[p][X] * vertices[q][Y];
    }

    // then we sort the vertices so they are always in a counterclockwise order
    int j = 0;
    if ( 0.0f < area ){
      // def <
      for (int i = vertex_start; i < vertex_end; i++) {
        j = i - vertex_start;
        vertex_order[j] = i;
      }

    } else {

      for (int i = vertex_start; i < vertex_end; i++) {
        j = i - vertex_start;
        vertex_order[j] = (vertex_end - 1) - j;
      }
    }


    // remove vc-2 Vertices, creating 1 triangle every time
    int vc = vertex_end - vertex_start;
    int count = 2*vc;  // complex polygon detection

    for (int m = 0, v = vc - 1; vc > 2; ) {
      boolean snip = true;

      // if we start over again, is a complex polygon
      if (0 >= (count--)) {
        break; // triangulation failed
      }

      // get 3 consecutive vertices <u,v,w>
      int u = v ; if (vc <= u) u = 0; // previous
      v = u+1; if (vc <= v) v = 0;    // current
      int w = v+1; if (vc <= w) w = 0;  // next

      // triangle A B C
      float Ax, Ay, Bx, By, Cx, Cy, Px, Py;

      Ax =  -vertices[vertex_order[u]][X];
      Ay =   vertices[vertex_order[u]][Y];
      Bx =  -vertices[vertex_order[v]][X];
      By =   vertices[vertex_order[v]][Y];
      Cx =  -vertices[vertex_order[w]][X];
      Cy =   vertices[vertex_order[w]][Y];

      // first we check if <u,v,w> continues going ccw
      if ( EPSILON > (((Bx-Ax) * (Cy-Ay)) - ((By-Ay) * (Cx-Ax)))) {
        continue;
    }

      for (int p = 0; p < vc; p++) {

        float ax, ay, bx, by, cx, cy, apx, apy, bpx, bpy, cpx, cpy;
        float cCROSSap, bCROSScp, aCROSSbp;

        if( (p == u) || (p == v) || (p == w) ) {
          continue;
        }

        Px = -vertices[vertex_order[p]][X];
        Py =  vertices[vertex_order[p]][Y];

        ax = Cx - Bx; ay = Cy - By;
        bx = Ax - Cx; by = Ay - Cy;
        cx = Bx - Ax; cy = By - Ay;
        apx= Px - Ax; apy= Py - Ay;
        bpx= Px - Bx; bpy= Py - By;
        cpx= Px - Cx; cpy= Py - Cy;

        aCROSSbp = ax * bpy - ay * bpx;
        cCROSSap = cx * apy - cy * apx;
        bCROSScp = bx * cpy - by * cpx;

        if ((aCROSSbp >= 0.0f) && (bCROSScp >= 0.0f) && (cCROSSap >= 0.0f)) {
          snip = false;
        }
      }

      if (snip) {
        int a,b,c,s,t;

        // true names of the vertices
        a = vertex_order[u]; b = vertex_order[v]; c = vertex_order[w];

        // create triangle
        addTriangle(a, b, c);

        m++;

        // remove v from remaining polygon
        for( s = v, t = v + 1; t < vc; s++, t++) {
          vertex_order[s] = vertex_order[t];
        }

        vc--;

        // resest error detection counter
        count = 2 * vc;
      }
    }
  }


  //private void sortTriangles() {
  //}


  //////////////////////////////////////////////////////////////

  
  public void endShape() {
    if (hints[NEW_GRAPHICS]) {
      endShape_newgraphics();
      return;
    }
    // could initialize unchangedZ if false, 
    //   model matrix is not identity
    // same with homoegenousColors and !lighting

    // clear the 'shape drawing' flag in case of early exit
    shape = false;

    int vertexCount = polygon.vertexCount;
    float vertices[][] = polygon.vertices;

    // ------------------------------------------------------------------
    // POINTS FROM MODEL (MX, MY, MZ) TO SCREEN SPACE (X, Y, Z)

    if ((camera_mode == PERSPECTIVE) && (dimensions == 0)) {
      polygon.interpZ = false;
      spolygon.interpZ = false;
      for (int i = 0; i < vertexCount; i++) {
        vertices[i][X] = vertices[i][MX];
        vertices[i][Y] = vertices[i][MY];
      }

    } else if ((camera_mode == PERSPECTIVE) && (dimensions == 2)) {
      polygon.interpZ = false;
      spolygon.interpZ = false;
      for (int i = 0; i < vertexCount; i++) {
        vertices[i][X] = m00*vertices[i][MX] + m01*vertices[i][MY] + m03;
        vertices[i][Y] = m10*vertices[i][MX] + m11*vertices[i][MY] + m13;
      }

      /*
    } else if (camera_mode == ISOMETRIC) {
      for (int i = 0; i < vertexCount; i++) {
        float v[] = vertices[i];
        v[X] =  v[MX] - v[MZ];
        v[Y] = -v[MX]/2f + v[MY] - v[MZ]/2f;
        v[Z] =  v[MZ];
      }
      */

    } else {  // dimension = 3 or camera mode is custom
      polygon.interpZ = true;
      spolygon.interpZ = true;

      for (int i = 0; i < vertexCount; i++) {
        float vertex[] = vertices[i];

        float ax = m00*vertex[MX] + m01*vertex[MY] + m02*vertex[MZ] + m03;
        float ay = m10*vertex[MX] + m11*vertex[MY] + m12*vertex[MZ] + m13;
        float az = m20*vertex[MX] + m21*vertex[MY] + m22*vertex[MZ] + m23;
        float aw = m30*vertex[MX] + m31*vertex[MY] + m32*vertex[MZ] + m33;

        float ox = p00*ax + p01*ay + p02*az + p03*aw;
        float oy = p10*ax + p11*ay + p12*az + p13*aw;
        float oz = p20*ax + p21*ay + p22*az + p23*aw;
        float ow = p30*ax + p31*ay + p32*az + p33*aw;

        if (ow != 0) {
          ox /= ow; oy /= ow; oz /= ow;
        }

        vertex[X] = width  * (ONE + ox) / 2.0f;
        vertex[Y] = height * (ONE + oy) / 2.0f;
        vertex[Z] = (oz + ONE) / 2.0f;
      }
    }


    // simple clipping.. if they share the same clipping code, then cull
    boolean clipped = true;
    int clipCode = thin_flat_lineClipCode(vertices[0][X], vertices[0][Y]);
    for (int i = 1; i < vertexCount; i++) {
      int code = thin_flat_lineClipCode(vertices[i][X], vertices[i][Y]);
      if (code != clipCode) {
        clipped = false;
        break;
      }
    }
    if ((clipCode != 0) && clipped) return;


    // ------------------------------------------------------------------
    // NORMALS

    if (!normalChanged) {  // fill first vertext w/ the normal
      vertices[0][NX] = normalX;
      vertices[0][NY] = normalY;
      vertices[0][NZ] = normalZ;
      // homogenousNormals saves time from below, which is expensive
    }

    for (int i = 0; i < (normalChanged ? vertexCount : 1); i++) {
      float v[] = vertices[i];
      float nx = m00*v[NX] + m01*v[NY] + m02*v[NZ] + m03;
      float ny = m10*v[NX] + m11*v[NY] + m12*v[NZ] + m13;
      float nz = m20*v[NX] + m21*v[NY] + m22*v[NZ] + m23;
      float nw = m30*v[NX] + m31*v[NY] + m32*v[NZ] + m33;

      if (nw != 0) {  // divide by perspective coordinate
        v[NX] = nx/nw; v[NY] = ny/nw; v[NZ] = nz/nw;
      } else {  // can't do inline above
        v[NX] = nx; v[NY] = ny; v[NZ] = nz;
      }
      float nlen = mag(v[NX], v[NY], v[NZ]);  // normalize
      if (nlen != 0) {
        v[NX] /= nlen; v[NY] /= nlen; v[NZ] /= nlen;
      }
    }

    // ------------------------------------------------------------------
    // TEXTURES

    // inherit UV characteristics from polygon
    // this is an uglyish sort of hack
    if (polygon.interpUV) {
      fpolygon.texture(polygon.timage);
    }

    // ------------------------------------------------------------------
    // COLORS

    // calculate RGB for each vertex
    //if (homogenousColors && !lighting) {  // if no lighting, do only once

    if (!lighting) {
      //polygon.interpRGB  = //false;
      spolygon.interpRGBA = strokeChanged; //false;
      fpolygon.interpRGBA = fillChanged; //false;

      // all the values for r, g, b have been set with calls to vertex()
      // (no need to re-calculate anything here)

    } else {
      //polygon.interpRGB = true;
      spolygon.interpRGBA = true;
      fpolygon.interpRGBA = true;

      float f[] = polygon.vertices[0];

      for (int i = 0; i < vertexCount; i++) {
        float v[] = polygon.vertices[i];
        if (normalChanged) {
          if (_fill) {
            calc_lighting(v[R],  v[G],  v[B],
                       v[MX], v[MY], v[MZ],
                       v[NX], v[NY], v[NZ],  v, R);
          }
          if (_stroke) {
            calc_lighting(v[SR], v[SG], v[SB],
                       v[MX], v[MY], v[MZ],
                       v[NX], v[NY], v[NZ],  v, SR);
          }
        } else {
          if (_fill) {
            calc_lighting(v[R],  v[G],  v[B],
                       v[MX], v[MY], v[MZ],
                       f[NX], f[NY], f[NZ],  v, R);
          }
          if (_stroke) {
            calc_lighting(v[SR], v[SG], v[SB],
                       v[MX], v[MY], v[MZ],
                       f[NX], f[NY], f[NZ],  v, SR);
          }
        }
      }
    }

    // ------------------------------------------------------------------
    // RENDER SHAPES

    int increment;
        
    // test for concave-convex
    if (shapeKind == POLYGON)  {
      shapeKind = isConvex() ? CONVEX_POLYGON : CONCAVE_POLYGON;
    }

    switch (shapeKind) {
    case POINTS:
      if ((dimensions == 0) && unchangedZ && 
          (strokeWeight == ONE) && !lighting) {
        if (!strokeChanged) {
          for (int i = 0; i < vertexCount; i++) {
            thin_point((int) vertices[i][X], (int) vertices[i][Y], 
                       0, stroke);
          }
        } else {
          for (int i = 0; i < vertexCount; i++) {
            thin_point((int) vertices[i][X], (int) vertices[i][Y],
                       0, float_color(vertices[i][SR], 
                                      vertices[i][SG], 
                                      vertices[i][SB]));
          }
          //strokei = strokeiSaved;
        }
      } else {
        float f[] = vertices[0];

        for (int i = 0; i < vertexCount; i++) {
          float v[] = vertices[i];

          // if this is the first time (i == 0)
          // or if lighting is enabled
          // or the stroke color has changed inside beginShape/endShape
          // then re-calculate the color at this vertex
          if ((i == 0) || lighting || strokeChanged) {
            // push calculated color into 'f' (this way, f is always valid)
            calc_lighting(v[SR], v[SG], v[SB], 
                       v[X],  v[Y],  v[Z],
                       v[NX], v[NY], v[NZ],  f, R);
          }
          // uses [SA], since stroke alpha isn't moved into [A] the 
          // way that [SR] goes to [R] etc on the calc_lighting call
          // (there's no sense in copying it to [A], except consistency
          // in the code.. but why the extra slowness?)
          thick_point(v[X], v[Y], v[Z],  f[R], f[G], f[B], f[SA]);
        }
      }
      break;

    case LINES:
    case LINE_STRIP:
    case LINE_LOOP:
      if (!_stroke) return;

      // if it's a line loop, copy the vertex data to the last element
      if (shapeKind == LINE_LOOP) {
        float v0[] = polygon.vertices[0];
        float v1[] = polygon.nextVertex();
        vertexCount++; // since it had already been read above

        v1[X] = v0[X]; v1[Y] = v0[Y]; v1[Z] = v0[Z];
        v1[SR] = v0[SR]; v1[SG] = v0[SG]; v1[SB] = v0[SB];
      }

      // increment by two for individual lines
      increment = (shapeKind == LINES) ? 2 : 1;
      draw_lines(vertices, vertexCount-1, 1, increment, 0);
      break;

    case TRIANGLES:
    case TRIANGLE_STRIP:
      increment = (shapeKind == TRIANGLES) ? 3 : 1;
      // do fill and stroke separately because otherwise
      // the lines will be stroked more than necessary
      if (_fill) {
        fpolygon.vertexCount = 3;
        for (int i = 0; i < vertexCount-2; i += increment) {
          for (int j = 0; j < 3; j++) {
            fpolygon.vertices[j][R] = vertices[i+j][R];
            fpolygon.vertices[j][G] = vertices[i+j][G];
            fpolygon.vertices[j][B] = vertices[i+j][B];
            fpolygon.vertices[j][A] = vertices[i+j][A];

            fpolygon.vertices[j][X] = vertices[i+j][X];
            fpolygon.vertices[j][Y] = vertices[i+j][Y];
            fpolygon.vertices[j][Z] = vertices[i+j][Z];

            if (polygon.interpUV) {
              fpolygon.vertices[j][U] = vertices[i+j][U];
              fpolygon.vertices[j][V] = vertices[i+j][V];
            }
          }
          fpolygon.render();
        }
      }
      if (_stroke) {
        // first draw all vertices as a line strip
        if (shapeKind == TRIANGLE_STRIP) {
          draw_lines(vertices, vertexCount-1, 1, 1, 0);
        } else {
          draw_lines(vertices, vertexCount-1, 1, 1, 3);
        }
        // then draw from vertex (n) to (n+2) 
        // incrementing n using the same as above
        draw_lines(vertices, vertexCount-2, 2, increment, 0);
        // changed this to vertexCount-2, because it seemed
        // to be adding an extra (nonexistant) line
      }
      break;

    case QUADS:
    case QUAD_STRIP:
      //System.out.println("pooping out a quad");
      increment = (shapeKind == QUADS) ? 4 : 2;
      if (_fill) {
        fpolygon.vertexCount = 4;
        for (int i = 0; i < vertexCount-3; i += increment) {
          for (int j = 0; j < 4; j++) {
            fpolygon.vertices[j][R] = vertices[i+j][R];
            fpolygon.vertices[j][G] = vertices[i+j][G];
            fpolygon.vertices[j][B] = vertices[i+j][B];
            fpolygon.vertices[j][A] = vertices[i+j][A];

            fpolygon.vertices[j][X] = vertices[i+j][X];
            fpolygon.vertices[j][Y] = vertices[i+j][Y];
            fpolygon.vertices[j][Z] = vertices[i+j][Z];

            if (polygon.interpUV) {
              fpolygon.vertices[j][U] = vertices[i+j][U];
              fpolygon.vertices[j][V] = vertices[i+j][V];
            }
          }
          fpolygon.render();
        }
      }
      if (_stroke) {
        // first draw all vertices as a line strip
        if (shapeKind == QUAD_STRIP) {
          draw_lines(vertices, vertexCount-1, 1, 1, 0);
        } else {  // skip every few for quads
          draw_lines(vertices, vertexCount, 1, 1, 4);
        }
        // then draw from vertex (n) to (n+3) 
        // incrementing n by the same increment as above
        draw_lines(vertices, vertexCount-2, 3, increment, 0);
      }
      break;

    case POLYGON:
    case CONCAVE_POLYGON:
      if (_fill) {
        // the triangulator produces polygons that don't align
        // when smoothing is enabled. but if there is a stroke around
        // the polygon, then smoothing can be temporarily disabled.
        boolean smoov = smooth;
        if (_stroke && !hints[DISABLE_SMOOTH_HACK]) smooth = false;
        concaveRender();
        if (_stroke && !hints[DISABLE_SMOOTH_HACK]) smooth = smoov;
      }

      if (_stroke) {
        draw_lines(vertices, vertexCount-1, 1, 1, 0);
        // draw the last line connecting back 
        // to the first point in poly
        svertices[0] = vertices[vertexCount-1];
        svertices[1] = vertices[0];
        draw_lines(svertices, 1, 1, 1, 0);
      }
      break;

    case CONVEX_POLYGON:
      if (_fill) {
        polygon.render();
        if (_stroke) polygon.unexpand();
      }

      if (_stroke) {
        draw_lines(vertices, vertexCount-1, 1, 1, 0);
        // draw the last line connecting back to the first point in poly
        svertices[0] = vertices[vertexCount-1];
        svertices[1] = vertices[0];
        draw_lines(svertices, 1, 1, 1, 0);
      }
      break;
    }
    // to signify no shape being drawn
    //shapeKind = 0;
  }


  //////////////////////////////////////////////////////////////

  // CONCAVE/CONVEX POLYGONS

  // pile of shit hack from rocha that cost us piles of $$


  private boolean isConvex() {
    float v[][] = polygon.vertices;
    int n = polygon.vertexCount;
    int j,k;
    int flag = 0;
    float z;
    //float tol = 0.001f;

    if (n < 3)
      // ERROR: this is a line or a point, render with CONVEX
      return true;

    // iterate along border doing dot product.
    // if the sign of the result changes, then is concave
    for (int i=0;i<n;i++) {
      j = (i + 1) % n;
      k = (i + 2) % n;
      z  = (v[j][X] - v[i][X]) * (v[k][Y] - v[j][Y]);
      z -= (v[j][Y] - v[i][Y]) * (v[k][X] - v[j][X]);
      if (z < 0)
         flag |= 1;
      else if (z > 0)
         flag |= 2;
      if (flag == 3)
         return false;  // CONCAVE
    }
    if (flag != 0)
      return true;    // CONVEX
    else
      // ERROR: colinear points, self intersection
      // treat as CONVEX
      return true;
  }


  // triangulate the current polygon
  private void concaveRender() {
    // WARNING: code is not in optimum form
    // local initiations of some variables are made to
    // keep the code modular and easy to integrate
    // restet triangle
    float vertices[][] = polygon.vertices;

    if (tpolygon == null) {
      // allocate on first use, rather than slowing 
      // the startup of the class.
      tpolygon = new PPolygon(this);
      tpolygon_vertex_order = new int[TPOLYGON_MAX_VERTICES];
    }
    tpolygon.reset(3);

    // copy render parameters

    if (texture) {
      tpolygon.texture(polygon.timage);
    }

    tpolygon.interpX = polygon.interpX;
    tpolygon.interpZ = polygon.interpZ;
    tpolygon.interpUV = polygon.interpUV;
    tpolygon.interpRGBA = polygon.interpRGBA;

    // simple ear clipping polygon triangulation
    // addapted from code by john w. ratcliff (jratcliff@verant.com)

    // 1 - first we check if the polygon goes CW or CCW
    // CW-CCW ordering adapted from code by 
    //        Joseph O'Rourke orourke@cs.smith.edu
    // 1A - we start by finding the lowest-right most vertex

    boolean ccw = false; // clockwise

    int n = polygon.vertexCount;
    int mm; // postion for LR vertex
    float min[] = new float[2];

    min[X] = vertices[0][X];
    min[Y] = vertices[0][Y];
    mm = 0;

    for(int i = 0; i < n; i++ ) {
      if( (vertices[i][Y] < min[Y]) ||
          ( (vertices[i][Y] == min[Y]) && (vertices[i][X] > min[X]) )
          ) {
        mm = i;
        min[X] = vertices[mm][X];
        min[Y] = vertices[mm][Y];
      }
    }

    // 1B - now we compute the cross product of the edges of this vertex
    float cp;
    int mm1;

    // just for renaming
    float a[] = new float[2];
    float b[] = new float[2];
    float c[] = new float[2];

    mm1 = (mm + (n-1)) % n;

    // assign a[0] to point to poly[m1][0] etc.
    for(int i = 0; i < 2; i++ ) {
      a[i] = vertices[mm1][i];
      b[i] = vertices[mm][i];
      c[i] = vertices[(mm+1)%n][i];
    }

    cp = a[0] * b[1] - a[1] * b[0] +
        a[1] * c[0] - a[0] * c[1] +
        b[0] * c[1] - c[0] * b[1];

    if ( cp > 0 )
      ccw = true;   // CCW
    else
      ccw = false;  // CW

    // 1C - then we sort the vertices so they 
    // are always in a counterclockwise order
    int j = 0;
    if (!ccw) {
      // keep the same order
      for (int i = 0; i < n; i++) {
        tpolygon_vertex_order[i] = i;
      }

    } else {
      // invert the order
      for (int i = 0; i < n; i++) {
        tpolygon_vertex_order[i] = (n - 1) - i;
      }
    }

    // 2 - begin triangulation
    // resulting triangles are stored in the triangle array
    // remove vc-2 Vertices, creating 1 triangle every time
    int vc = n;
    int count = 2*vc;  // complex polygon detection

    for (int m = 0, v = vc - 1; vc > 2; ) {
      boolean snip = true;

      // if we start over again, is a complex polygon
      if (0 >= (count--)) {
        break; // triangulation failed
      }

      // get 3 consecutive vertices <u,v,w>
      int u = v ; if (vc <= u) u = 0; // previous
      v = u+1; if (vc <= v) v = 0;    // current
      int w = v+1; if (vc <= w) w = 0;  // next

      // triangle A B C
      float Ax, Ay, Bx, By, Cx, Cy, Px, Py;
    
      Ax =  -vertices[tpolygon_vertex_order[u]][X];
      Ay =   vertices[tpolygon_vertex_order[u]][Y];
      Bx =  -vertices[tpolygon_vertex_order[v]][X];
      By =   vertices[tpolygon_vertex_order[v]][Y];
      Cx =  -vertices[tpolygon_vertex_order[w]][X];
      Cy =   vertices[tpolygon_vertex_order[w]][Y];

      if ( EPSILON > (((Bx-Ax) * (Cy-Ay)) - ((By-Ay) * (Cx-Ax)))) {
        continue;
      }

      for (int p = 0; p < vc; p++) {

        // this part is a bit osbscure, basically what it does
        // is test if this tree vertices are and ear or not, looking for
        // intersections with the remaining vertices using a cross product
        float ax, ay, bx, by, cx, cy, apx, apy, bpx, bpy, cpx, cpy;
        float cCROSSap, bCROSScp, aCROSSbp;

        if( (p == u) || (p == v) || (p == w) ) {
          continue;
        }

        Px = -vertices[tpolygon_vertex_order[p]][X];
        Py =  vertices[tpolygon_vertex_order[p]][Y];

        ax = Cx - Bx; ay = Cy - By;
        bx = Ax - Cx; by = Ay - Cy;
        cx = Bx - Ax; cy = By - Ay;
        apx= Px - Ax; apy= Py - Ay;
        bpx= Px - Bx; bpy= Py - By;
        cpx= Px - Cx; cpy= Py - Cy;

        aCROSSbp = ax * bpy - ay * bpx;
        cCROSSap = cx * apy - cy * apx;
        bCROSScp = bx * cpy - by * cpx;

        if ((aCROSSbp >= 0.0f) && (bCROSScp >= 0.0f) && (cCROSSap >= 0.0f)) {
          snip = false;
        }
      }

      if (snip) {
        // yes, the trio is an ear, render it and cut it

        int triangle_vertices[] = new int[3];
        int s,t;

        // true names of the vertices
        triangle_vertices[0] = tpolygon_vertex_order[u];
        triangle_vertices[1] = tpolygon_vertex_order[v];
        triangle_vertices[2] = tpolygon_vertex_order[w];

        // create triangle
        render_triangle(triangle_vertices);

        m++;

        // remove v from remaining polygon
        for( s = v, t = v + 1; t < vc; s++, t++) {
          tpolygon_vertex_order[s] = tpolygon_vertex_order[t];
        }

        vc--;

        // resest error detection counter
        count = 2 * vc;
      }
    }
  }


  private final void render_triangle(int[] triangle_vertices) {
    // copy all fields of the triangle vertices
    for (int i = 0; i < 3; i++) {
      float[] src = polygon.vertices[triangle_vertices[i]];
      float[] dest = tpolygon.vertices[i];
      for (int j = 0; j < VERTEX_FIELD_COUNT; j++) {
        dest[j] = src[j];
      }
    }

    // render triangle
    tpolygon.render();
  }


  //////////////////////////////////////////////////////////////

  // RENDERING


  // expects properly clipped coords, hence does 
  // NOT check if x/y are in bounds [toxi]
  private void thin_pointAt(int x, int y, float z, int color) {
    int index = y*width+x; // offset values are pre-calced in constructor
    pixels[index] = color;
    zbuffer[index] = z;
  }

  // expects offset/index in pixelbuffer array instead of x/y coords
  // used by optimized parts of thin_flat_line() [toxi]
  private void thin_pointAtIndex(int offset, float z, int color) {
    pixels[offset] = color;
    zbuffer[offset] = z;
  }

  // points are inherently flat, but always tangent 
  // to the screen surface. the z is only so that things 
  // get scaled properly if the pt is way in back
  private void thick_point(float x, float y, float z, // note floats
                           float r, float g, float b, float a) {
    spolygon.reset(4);
    spolygon.interpRGBA = false;  // no changes for vertices of a point

    float strokeWidth2 = strokeWeight/2.0f;

    float svertex[] = spolygon.vertices[0];
    svertex[X] = x - strokeWidth2;
    svertex[Y] = y - strokeWidth2;
    svertex[Z] = z;

    svertex[R] = r;
    svertex[G] = g;
    svertex[B] = b;
    svertex[A] = a; 

    svertex = spolygon.vertices[1];
    svertex[X] = x + strokeWidth2;
    svertex[Y] = y - strokeWidth2;
    svertex[Z] = z;

    svertex = spolygon.vertices[2];
    svertex[X] = x + strokeWidth2;
    svertex[Y] = y + strokeWidth2;
    svertex[Z] = z;

    svertex = spolygon.vertices[3];
    svertex[X] = x - strokeWidth2;
    svertex[Y] = y + strokeWidth2;
    svertex[Z] = z;

    spolygon.render();
  }


  // new bresenham clipping code, as old one was buggy [toxi]
  private void thin_flat_line(int x1, int y1, int x2, int y2) {
    int nx1,ny1,nx2,ny2;

    // get the "dips" for the points to clip
    int code1 = thin_flat_lineClipCode(x1, y1); 
    int code2 = thin_flat_lineClipCode(x2, y2);

    if ((code1 & code2)!=0) {
      return;
    } else {
      int dip = code1 | code2;
      if (dip != 0) {
        // now calculate the clipped points
        float a1 = 0, a2 = 1, a = 0;
        for (int i=0;i<4;i++) {
          if (((dip>>i)%2)==1) {
            a = thin_flat_lineSlope((float)x1, (float)y1,
                                    (float)x2, (float)y2, i+1);
            if (((code1>>i)%2)==1) {
              a1 = (float)Math.max(a, a1);
            } else {
              a2 = (float)Math.min(a, a2);
            }
          }
        }
        if (a1>a2) return;
        else {
          nx1=(int) (x1+a1*(x2-x1));
          ny1=(int) (y1+a1*(y2-y1));
          nx2=(int) (x1+a2*(x2-x1));
          ny2=(int) (y1+a2*(y2-y1));
        }
        // line is fully visible/unclipped
      } else {
        nx1=x1; nx2=x2;
        ny1=y1; ny2=y2;
      }
    }

    // new "extremely fast" line code
    // adapted from http://www.edepot.com/linee.html

    boolean yLonger=false;
    int shortLen=ny2-ny1;
    int longLen=nx2-nx1;
    if (Math.abs(shortLen)>Math.abs(longLen)) {
      int swap=shortLen;
      shortLen=longLen;
      longLen=swap;
      yLonger=true;
    }
    int decInc;
    if (longLen==0) decInc=0;
    else decInc = (shortLen << 16) / longLen;

    if (nx1==nx2) {
      // special case: vertical line
      if (ny1>ny2) { int ty=ny1; ny1=ny2; ny2=ty; }
      int offset=ny1*width+nx1;
      for(int j=ny1; j<=ny2; j++) { 
        thin_pointAtIndex(offset,0,stroke); 
        offset+=width; 
      }
      return;
    } else if (ny1==ny2) {
      // special case: horizontal line
      if (nx1>nx2) { int tx=nx1; nx1=nx2; nx2=tx; }
      int offset=ny1*width+nx1;
      for(int j=nx1; j<=nx2; j++) thin_pointAtIndex(offset++,0,stroke);
      return;
    } else if (yLonger) {
      if (longLen>0) {
        longLen+=ny1;
        for (int j=0x8000+(nx1<<16);ny1<=longLen;++ny1) {
          thin_pointAt(j>>16, ny1, 0, stroke);
          j+=decInc;
        }
        return;
      }
      longLen+=ny1;
      for (int j=0x8000+(nx1<<16);ny1>=longLen;--ny1) {        
        thin_pointAt(j>>16, ny1, 0, stroke);
        j-=decInc;
      }
      return;        
    } else if (longLen>0) {
      longLen+=nx1;
      for (int j=0x8000+(ny1<<16);nx1<=longLen;++nx1) {
        thin_pointAt(nx1, j>>16, 0, stroke);
        j+=decInc;
      }
      return;
    }
    longLen+=nx1;
    for (int j=0x8000+(ny1<<16);nx1>=longLen;--nx1) {
      thin_pointAt(nx1, j>>16, 0, stroke);
      j-=decInc;
    }
  }

  private int thin_flat_lineClipCode(float x, float y) {
    return ((y < 0 ? 8 : 0) | (y > height1 ? 4 : 0) |
            (x < 0 ? 2 : 0) | (x > width1 ? 1 : 0));
  }

  private float thin_flat_lineSlope(float x1, float y1, 
                                    float x2, float y2, int border) {
    switch (border) {
    case 4: {
      return (-y1)/(y2-y1);
    }
    case 3: {
      return (height1-y1)/(y2-y1);
    }
    case 2: {
      return (-x1)/(x2-x1);
    }
    case 1: {
      return (width1-x1)/(x2-x1);
    }
    }
    return -1f;
  }


  private boolean flat_line_retribution(float x1, float y1, 
                                        float x2, float y2,
                                        float r1, float g1, float b1) {
    // assume that if it is/isn't big in one dir, then the 
    // other doesn't matter, cuz that's a weird case
    float lwidth  = m00*strokeWeight + m01*strokeWeight;
    //float lheight = m10*strokeWeight + m11*strokeWeight;
    // lines of stroke thickness 1 can be anywhere from -1.41 to 1.41
    if ((strokeWeight < TWO) && (!hints[SCALE_STROKE_WIDTH])) {
      //if (abs(lwidth) < 1.5f) {
      //System.out.println("flat line retribution " + r1 + " " + g1 + " " + b1);
      int strokeSaved = stroke;
      stroke = float_color(r1, g1, b1); 
      thin_flat_line((int)x1, (int)y1, (int)x2, (int)y2);
      stroke = strokeSaved;
      return true;
    }
    return false;
  }


  private void thick_flat_line(float ox1, float oy1, 
                               float r1, float g1, float b1, float a1,
                               float ox2, float oy2, 
                               float r2, float g2, float b2, float a2) {
    spolygon.interpRGBA = (r1 != r2) || (g1 != g2) || (b1 != b2) || (a1 != a2);
    spolygon.interpZ = false;

    if (!spolygon.interpRGBA && 
        flat_line_retribution(ox1, oy1, ox2, oy2, r1, g1, b1)) {
      return;
    }

    float dX = ox2-ox1 + EPSILON;
    float dY = oy2-oy1 + EPSILON;
    float len = sqrt(dX*dX + dY*dY);

    // TODO strokeWidth should be transformed!
    float rh = strokeWeight / len;

    float dx0 = rh * dY;
    float dy0 = rh * dX;
    float dx1 = rh * dY;
    float dy1 = rh * dX;

    spolygon.reset(4);

    float svertex[] = spolygon.vertices[0];
    svertex[X] = ox1+dx0;
    svertex[Y] = oy1-dy0;
    svertex[R] = r1;
    svertex[G] = g1;
    svertex[B] = b1;
    svertex[A] = a1;

    svertex = spolygon.vertices[1];
    svertex[X] = ox1-dx0;
    svertex[Y] = oy1+dy0;
    svertex[R] = r1;
    svertex[G] = g1;
    svertex[B] = b1;
    svertex[A] = a1;

    svertex = spolygon.vertices[2];
    svertex[X] = ox2-dx1;
    svertex[Y] = oy2+dy1;
    svertex[R] = r2;
    svertex[G] = g2;
    svertex[B] = b2;
    svertex[A] = a2;

    svertex = spolygon.vertices[3];
    svertex[X] = ox2+dx1;
    svertex[Y] = oy2-dy1;
    svertex[R] = r2;
    svertex[G] = g2;
    svertex[B] = b2;
    svertex[A] = a2;

    spolygon.render();
  }


  // OPT version without z coords can save 8 multiplies and some other
  private void spatial_line(float x1, float y1, 
                            float r1, float g1, float b1, 
                            float x2, float y2, 
                            float r2, float g2, float b2) {
    spatial_line(x1, y1, 0, r1, g1, b1, 
                 x2, y2, 0, r2, g2, b2);
  }


  // the incoming values are transformed,
  // and the colors have been calculated

  private void spatial_line(float x1, float y1, float z1,
                            float r1, float g1, float b1, 
                            float x2, float y2, float z2, 
                            float r2, float g2, float b2) {
    spolygon.interpRGBA = (r1 != r2) || (g1 != g2) || (b1 != b2);
    if (!spolygon.interpRGBA && 
        flat_line_retribution(x1, y1, x2, y2, r1, g1, b1)) {
      return;
    }

    spolygon.interpZ = true;

    float ox1 = x1; float oy1 = y1; float oz1 = z1;
    float ox2 = x2; float oy2 = y2; float oz2 = z2;

    float dX = ox2-ox1 + 0.0001f;
    float dY = oy2-oy1 + 0.0001f;
    float len = sqrt(dX*dX + dY*dY);

    //float x0 = m00*0 + m01*0 + m03;

    float rh = strokeWeight / len;

    float dx0 = rh * dY;
    float dy0 = rh * dX;
    float dx1 = rh * dY;
    float dy1 = rh * dX;

    spolygon.reset(4);

    float svertex[] = spolygon.vertices[0];
    svertex[X] = ox1+dx0;
    svertex[Y] = oy1-dy0;
    svertex[Z] = oz1;
    svertex[R] = r1; //calcR1;
    svertex[G] = g1; //calcG1;
    svertex[B] = b1; //calcB1;

    svertex = spolygon.vertices[1];
    svertex[X] = ox1-dx0;
    svertex[Y] = oy1+dy0;
    svertex[Z] = oz1;
    svertex[R] = r1; //calcR1;
    svertex[G] = g1; //calcG1;
    svertex[B] = b1; //calcB1;

    svertex = spolygon.vertices[2];
    svertex[X] = ox2-dx1;
    svertex[Y] = oy2+dy1;
    svertex[Z] = oz2;
    svertex[R] = r2; //calcR2;
    svertex[G] = g2; //calcG2;
    svertex[B] = b2; //calcB2;

    svertex = spolygon.vertices[3];
    svertex[X] = ox2+dx1;
    svertex[Y] = oy2-dy1;
    svertex[Z] = oz2;
    svertex[R] = r2; //calcR2;
    svertex[G] = g2; //calcG2;
    svertex[B] = b2; //calcB2;

    spolygon.render();
  }


  // max is what to count to
  // offset is offset to the 'next' vertex
  // increment is how much to increment in the loop
  private void draw_lines(float vertices[][], int max, 
                          int offset, int increment, int skip) {

    if (strokeWeight < 2) {
      for (int i = 0; i < max; i += increment) {
        if ((skip != 0) && (((i+offset) % skip) == 0)) continue;

        float a[] = vertices[i];
        float b[] = vertices[i+offset];

        line.reset();

        line.setIntensities(a[SR], a[SG], a[SB], a[SA],
                            b[SR], b[SG], b[SB], b[SA]);

        line.setVertices(a[X], a[Y], a[Z],
                         b[X], b[Y], b[Z]);

        line.draw();
      }

    } else {  // use old line code for thickness > 1

      if ((dimensions != 3) && unchangedZ) {
        if ((strokeWeight < TWO) && !lighting && !strokeChanged) {
          // need to set color at least once?

          // THIS PARTICULAR CASE SHOULD NO LONGER BE REACHABLE

          for (int i = 0; i < max; i += increment) {
            if ((skip != 0) && (((i+offset) % skip) == 0)) continue;
            thin_flat_line((int) vertices[i][X], 
                           (int) vertices[i][Y], 
                           (int) vertices[i+offset][X], 
                           (int) vertices[i+offset][Y]);
          }
        } else { 
          for (int i = 0; i < max; i += increment) {
            if ((skip != 0) && (((i+offset) % skip) == 0)) continue;
            float v1[] = vertices[i];
            float v2[] = vertices[i+offset];
            thick_flat_line(v1[X], v1[Y],  v1[SR], v1[SG], v1[SB], v1[SA], 
                            v2[X], v2[Y],  v2[SR], v2[SG], v2[SB], v2[SA]);
          }
        }
      } else {
        for (int i = 0; i < max; i += increment) {
          if ((skip != 0) && (((i+offset) % skip) == 0)) continue;
          float v1[] = vertices[i];
          float v2[] = vertices[i+offset];
          spatial_line(v1[X],  v1[Y],  v1[Z],  v1[SR], v1[SG], v1[SB], 
                       v2[X],  v2[Y],  v2[Z],  v2[SR], v2[SG], v2[SB]);
        }
      }
    }
  }



  //////////////////////////////////////////////////////////////

  // UGLY RENDERING SHIT


  private void thin_point(int x, int y, float z, int color) {
    // necessary? [fry] yes! [toxi]
    if (x<0 || x>width1 || y<0 || y>height1) return; 

    int index = y*width + x;
    if ((color & 0xff000000) == 0xff000000) {  // opaque
      pixels[index] = color; 

    } else {  // transparent
      // couldn't seem to get this working correctly

      //pixels[index] = _blend(pixels[index], 
      //                     color & 0xffffff, (color >> 24) & 0xff);

      // a1 is how much of the orig pixel
      int a2 = (color >> 24) & 0xff;
      int a1 = a2 ^ 0xff;

      int p2 = stroke;
      int p1 = pixels[index];

      int r = (a1 * ((p1 >> 16) & 0xff) + a2 * ((p2 >> 16) & 0xff)) & 0xff00;
      int g = (a1 * ((p1 >>  8) & 0xff) + a2 * ((p2 >>  8) & 0xff)) & 0xff00;
      int b = (a1 * ( p1        & 0xff) + a2 * ( p2        & 0xff)) >> 8;

      pixels[index] =  0xff000000 | (r << 8) | g | b;

      //pixels[index] = _blend(pixels[index], 
      //                     color & 0xffffff, (color >> 24) & 0xff);
      /*
      pixels[index] = 0xff000000 | 
        ((((a1 * ((pixels[index] >> 16) & 0xff) + 
            a2 * ((color         >> 16) & 0xff)) & 0xff00) << 24) << 8) |
        (((a1 * ((pixels[index] >>  8) & 0xff) + 
           a2 * ((color         >>  8) & 0xff)) & 0xff00) << 16) |
        (((a1 * ( pixels[index]        & 0xff) + 
           a2 * ( color                & 0xff)) >> 8));
      */
    }
    zbuffer[index] = z;
  }


  // optimized because it's used so much
  private void flat_rect(int x1, int y1, int x2, int y2) {
    //System.out.println("flat quad");
    if (y2 < y1) { 
      int temp = y1; y1 = y2; y2 = temp; 
    }
    if (x2 < x1) {
      int temp = x1; x1 = x2; x2 = temp;
    }
    // checking to watch out for boogers
    if ((x1 > width1) || (x2 < 0) ||
        (y1 > height1) || (y2 < 0)) return;

    if (_fill) {
      int fx1 = x1;
      int fy1 = y1; 
      int fx2 = x2;
      int fy2 = y2;

      // these only affect the fill, not the stroke
      // (otherwise strange boogers at edges b/c frame changes shape)
      if (fx1 < 0) fx1 = 0;
      if (fx2 > width) fx2 = width;
      if (fy1 < 0) fy1 = 0;
      if (fy2 > height) fy2 = height;
      
      // [toxi 031223] 
      // on avg. 20-25% faster fill routine using System.arraycopy()
      int ww = fx2 - fx1;
      int hh = fy2 - fy1;
      int[] row = new int[ww];
      for (int i = 0; i < ww; i++) row[i] = fill;
      int idx = fy1 * width + fx1;
      for (int y = 0; y < hh; y++) {
        System.arraycopy(row, 0, pixels, idx, ww);
        idx += width;
      }
      row = null;
    }

    // broken in the new graphics engine
    if (!hints[NEW_GRAPHICS]) {
      if (_stroke) {
        if (strokeWeight == 1) {
          thin_flat_line(x1, y1, x2, y1);
          thin_flat_line(x2, y1, x2, y2);
          thin_flat_line(x2, y2, x1, y2);
          thin_flat_line(x1, y2, x1, y1);

        } else {
          thick_flat_line(x1, y1, fillR, fillG, fillB, fillA,
                          x2, y1, fillR, fillG, fillB, fillA);
          thick_flat_line(x2, y1, fillR, fillG, fillB, fillA,
                          x2, y2, fillR, fillG, fillB, fillA);
          thick_flat_line(x2, y2, fillR, fillG, fillB, fillA, 
                          x1, y2, fillR, fillG, fillB, fillA);
          thick_flat_line(x1, y2, fillR, fillG, fillB, fillA,
                          x1, y1, fillR, fillG, fillB, fillA);
        }
      }
    }
  }


  private void flat_circle(int centerX, int centerY, int radius) {
    if (dimensions == 2) {  // translate but no scale
      centerX = (int) screenX(centerX, centerY, 0);
      centerY = (int) screenY(centerX, centerY, 0);
    }
    if (_fill) flat_circle_fill(centerX, centerY, radius);
    if (_stroke) flat_circle_stroke(centerX, centerY, radius);
  }


  /**
   * Draw the outline around a flat circle using a bresenham-style
   * algorithm. Adapted from drawCircle function in "Computer Graphics
   * for Java Programmers" by Leen Ammeraal, p. 110
   *
   * This function is included because the quality is so much better,
   * and the drawing significantly faster than with adaptive ellipses
   * drawn using the sine/cosine tables.
   *
   * Circle quadrants break down like so:
   *              |
   *        \ NNW | NNE /  
   *          \   |   / 
   *       WNW  \ | /  ENE
   *     -------------------
   *       WSW  / | \  ESE
   *          /   |   \
   *        / SSW | SSE \
   *              |
   *
   * @param xc x center
   * @param yc y center
   * @param r radius
   */
  private void flat_circle_stroke(int xC, int yC, int r) {
    int x = 0, y = r, u = 1, v = 2 * r - 1, E = 0;
    while (x < y) {
      thin_point(xC + x, yC + y, 0, stroke); // NNE
      thin_point(xC + y, yC - x, 0, stroke); // ESE
      thin_point(xC - x, yC - y, 0, stroke); // SSW
      thin_point(xC - y, yC + x, 0, stroke); // WNW

      x++; E += u; u += 2;
      if (v < 2 * E) {
        y--; E -= v; v -= 2;
      }
      if (x > y) break;

      thin_point(xC + y, yC + x, 0, stroke); // ENE
      thin_point(xC + x, yC - y, 0, stroke); // SSE
      thin_point(xC - y, yC - x, 0, stroke); // WSW
      thin_point(xC - x, yC + y, 0, stroke); // NNW
    }
  }

  /**
   * Heavily adapted version of the above algorithm that handles 
   * filling the ellipse. Works by drawing from the center and 
   * outwards to the points themselves. Has to be done this way 
   * because the values for the points are changed halfway through
   * the function, making it impossible to just store a series of
   * left and right edges to be drawn more quickly.
   *
   * @param xc x center
   * @param yc y center
   * @param r radius
   */
  private void flat_circle_fill(int xc, int yc, int r) {
    int x = 0, y = r, u = 1, v = 2 * r - 1, E = 0;
    while (x < y) {
      for (int xx = xc; xx < xc + x; xx++) {  // NNE
        thin_point(xx, yc + y, 0, fill);
      }
      for (int xx = xc; xx < xc + y; xx++) {  // ESE
        thin_point(xx, yc - x, 0, fill);
      }
      for (int xx = xc - x; xx < xc; xx++) {  // SSW
        thin_point(xx, yc - y, 0, fill);
      }
      for (int xx = xc - y; xx < xc; xx++) {  // WNW
        thin_point(xx, yc + x, 0, fill);
      }

      x++; E += u; u += 2;
      if (v < 2 * E) {
        y--; E -= v; v -= 2;
      }
      if (x > y) break;

      for (int xx = xc; xx < xc + y; xx++) {  // ENE
        thin_point(xx, yc + x, 0, fill);
      }
      for (int xx = xc; xx < xc + x; xx++) {  // SSE
        thin_point(xx, yc - y, 0, fill);
      }
      for (int xx = xc - y; xx < xc; xx++) {  // WSW
        thin_point(xx, yc - x, 0, fill);
      }
      for (int xx = xc - x; xx < xc; xx++) {  // NNW
        thin_point(xx, yc + y, 0, fill);
      }
    }
  }

  // unfortunately this can't handle fill and stroke simultaneously,
  // because the fill will later replace some of the stroke points

  private final void flat_ellipse_symmetry(int centerX, int centerY, 
                                           int ellipseX, int ellipseY,
                                           boolean filling) {
    if (filling) {
      for (int i = centerX - ellipseX + 1; i < centerX + ellipseX; i++) {
        thin_point(i, centerY - ellipseY, 0, fill);
        thin_point(i, centerY + ellipseY, 0, fill);
      }
    } else {
      thin_point(centerX - ellipseX, centerY + ellipseY, 0, stroke);
      thin_point(centerX + ellipseX, centerY + ellipseY, 0, stroke);
      thin_point(centerX - ellipseX, centerY - ellipseY, 0, stroke);
      thin_point(centerX + ellipseX, centerY - ellipseY, 0, stroke);
    }
  }


  /**
   * Bresenham-style ellipse drawing function, adapted from a posting to 
   * comp.graphics.algortihms.
   *
   * This function is included because the quality is so much better,
   * and the drawing significantly faster than with adaptive ellipses
   * drawn using the sine/cosine tables.
   *
   * @param centerX x coordinate of the center
   * @param centerY y coordinate of the center
   * @param a horizontal radius
   * @param b vertical radius
   */
  private void flat_ellipse_internal(int centerX, int centerY, 
                                     int a, int b, boolean filling) {
    int x, y, a2, b2, s, t;

    a2 = a*a;
    b2 = b*b;
    x = 0;
    y = b;
    s = a2*(1-2*b) + 2*b2;
    t = b2 - 2*a2*(2*b-1);
    flat_ellipse_symmetry(centerX, centerY, x, y, filling);

    do {
      if (s < 0) {
        s += 2*b2*(2*x+3);
        t += 4*b2*(x+1);
        x++;
      } else if (t < 0) {
        s += 2*b2*(2*x+3) - 4*a2*(y-1);
        t += 4*b2*(x+1) - 2*a2*(2*y-3);
        x++;
        y--;
      } else {
        s -= 4*a2*(y-1);
        t -= 2*a2*(2*y-3);
        y--;
      }
      flat_ellipse_symmetry(centerX, centerY, x, y, filling);

    } while (y > 0);
  }


  private void flat_ellipse(int centerX, int centerY, int a, int b) {
    if (dimensions == 2) {  // probably a translate but no scale
      centerX = (int) screenX(centerX, centerY, 0);
      centerY = (int) screenY(centerX, centerY, 0);
    }
    if (_fill) flat_ellipse_internal(centerX, centerY, a, b, true);
    if (_stroke) flat_ellipse_internal(centerX, centerY, a, b, false);
  }


  /**
   * Image drawn in flat "screen space", with no scaling or warping.
   * this is so common that a special routine is included for it, 
   * because the alternative is much slower.
   *
   * @param  image  image to be drawn
   * @param  sx1    x coordinate of upper-lefthand corner in screen space
   * @param  sy1    y coordinate of upper-lefthand corner in screen space
   */
  public void flat_image(PImage image, int sx1, int sy1) {
    int ix1 = 0;
    int iy1 = 0;
    int ix2 = image.width;
    int iy2 = image.height;

    if (image_mode == CENTER_DIAMETER) {
      sx1 -= image.width / 2;
      sy1 -= image.height / 2;
    }

    int sx2 = sx1 + image.width;
    int sy2 = sy1 + image.height;

    // don't draw if completely offscreen
    // (without this check, ArrayIndexOutOfBoundsException)
    if ((sx1 > width1) || (sx2 < 0) || 
        (sy1 > height1) || (sy2 < 0)) return;

    if (sx1 < 0) {  // off left edge
      ix1 -= sx1;
      sx1 = 0;
    }
    if (sy1 < 0) {  // off top edge
      iy1 -= sy1;
      sy1 = 0;
    }
    if (sx2 > width) {  // off right edge
      ix2 -= sx2 - width;
      sx2 = width;
    }
    if (sy2 > height) {  // off bottom edge
      iy2 -= sy2 - height;
      sy2 = height;
    }

    int source = iy1 * image.width + ix1;
    int target = sy1 * width;

    if (image.format == RGBA) {
      for (int y = sy1; y < sy2; y++) {
        int tx = 0;

        for (int x = sx1; x < sx2; x++) {
          pixels[target + x] = 
            _blend(pixels[target + x], 
                   image.pixels[source + tx], 
                   image.pixels[source + tx++] >>> 24);
        }
        source += image.width;
        target += width;
      }
    } else if (image.format == ALPHA) {
      for (int y = sy1; y < sy2; y++) {
        int tx = 0;

        for (int x = sx1; x < sx2; x++) {
          pixels[target + x] = 
            _blend(pixels[target + x], 
                   fill, 
                   image.pixels[source + tx++]);
        }
        source += image.width;
        target += width;
      }

    } else if (image.format == RGB) {
      target += sx1;
      int tw = sx2 - sx1;
      for (int y = sy1; y < sy2; y++) {
        System.arraycopy(image.pixels, source, pixels, target, tw);
        // should set z coordinate in here
        // or maybe not, since dims=0, meaning no relevant z
        source += image.width;
        target += width;
      }
    }
  }



  //////////////////////////////////////////////////////////////

  // SIMPLE SHAPES WITH ANALOGUES IN beginShape()


  public void point(float x, float y) {
    //line(x, y, x, y);
    beginShape(POINTS);
    vertex(x, y);
    endShape();
  }


  public void point(float x, float y, float z) {
    //line(x, y, z, x, y, z);
    beginShape(POINTS);
    vertex(x, y, z);
    endShape();
  }


  public void line(float x1, float y1, float x2, float y2) {
    beginShape(LINES);
    vertex(x1, y1);
    vertex(x2, y2);
    endShape();
  }


  public void line(float x1, float y1, float z1, 
                   float x2, float y2, float z2) {
    beginShape(LINES);
    vertex(x1, y1, z1);
    vertex(x2, y2, z2);
    endShape();
  }


  public void triangle(float x1, float y1, float x2, float y2,
                       float x3, float y3) {
    beginShape(TRIANGLES);
    vertex(x1, y1);
    vertex(x2, y2);
    vertex(x3, y3);
    endShape();
  }


  public void quad(float x1, float y1, float x2, float y2,
                   float x3, float y3, float x4, float y4) {
    beginShape(QUADS);
    vertex(x1, y1);
    vertex(x2, y2);
    vertex(x3, y3);
    vertex(x4, y4);
    endShape();
  }


  //////////////////////////////////////////////////////////////

  // 2D SHAPES, DRAWN WITH x/y/w/h


  public void rectMode(int mode) {
    rect_mode = mode;
  }


  public void rect(float x1, float y1, float x2, float y2) {
    float hradius, vradius;
    switch (rect_mode) {
    case CORNERS: 
      break;
    case CORNER: 
      x2 += x1; y2 += y1; 
      break;
    case CENTER_RADIUS: 
      hradius = x2; 
      vradius = y2;
      x2 = x1 + hradius; 
      y2 = y1 + vradius;
      x1 -= hradius; 
      y1 -= vradius;
      break;
    case CENTER_DIAMETER:
      hradius = x2 / 2.0f;
      vradius = y2 / 2.0f;
      x2 = x1 + hradius;
      y2 = y1 + vradius;
      x1 -= hradius;
      y1 -= vradius;
    }

    if ((dimensions == 0) && !lighting && !fill_alpha) {
      // draw in 2D
      flat_rect((int) x1, (int) y1, (int) x2, (int) y2);

    } else {
      // draw in 3D
      beginShape(QUADS);
      vertex(x1, y1);
      vertex(x2, y1);
      vertex(x2, y2);
      vertex(x1, y2);
      endShape();
    }
  } 


  public void ellipseMode(int mode) {
    ellipse_mode = mode;
  }


  // adaptive ellipse accuracy contributed by toxi
  public void ellipse(float x, float y, float hradius, float vradius) {
    switch (ellipse_mode) {
    case CENTER_RADIUS: 
      break;
    case CENTER_DIAMETER:
      hradius /= 2f; vradius /= 2f;
      break;
    case CORNER:
      hradius /= 2f; vradius /= 2f; 
      x += hradius; y += vradius;
      break;
    case CORNERS:
      hradius -= x; 
      vradius -= y; 
      break;
    }

    // adapt accuracy to radii used w/ a minimum of 4 segments [toxi]
    // now uses current scale factors to determine "real" transformed radius

    //System.out.println(m00 + " " + m11);
    //int cAccuracy = (int)(4+Math.sqrt(hradius*abs(m00)+vradius*abs(m11))*2);
    //int cAccuracy = (int)(4+Math.sqrt(hradius+vradius)*2);

    // notched this up to *3 instead of *2 because things were
    // looking a little rough, i.e. the calculate->arctangent example [fry]

    // also removed the m00 and m11 because those were causing weirdness
    // need an actual measure of magnitude in there [fry]

    int cAccuracy = (int)(4+Math.sqrt(hradius+vradius)*3);

    boolean plain = 
      !lighting && !smooth && (strokeWeight == 1) && 
      !fill_alpha && !stroke_alpha;

    boolean flat = (dimensions == 0) || 
      ((dimensions == 2) && (m00 == m11) && (m00 == 1));

    if (plain && flat) {
      if (hradius == vradius) {
        //if ((dimensions == 0) && 
        //!lighting && !smooth && (hradius == vradius)) {
        flat_circle((int)x, (int)y, (int)hradius);
        //if (_fill) flat_circle_fill((int)x, (int)y, (int)hradius);
        //if (_stroke) flat_circle_stroke((int)x, (int)y, (int)hradius);

        //} else if (((dimensions == 0) || ((dimensions == 2) && 
        //                     (m00 == m11) && (m00 == 1))) &&
        //     !lighting && !smooth) {
      } else {
        flat_ellipse((int)x, (int)y, (int)hradius, (int)vradius);
      }

    } else {
      // [toxi031031] adapted to use new lookup tables
      float inc = (float)SINCOS_LENGTH / cAccuracy;

      float val = 0;
      beginShape(POLYGON);
      for (int i = 0; i < cAccuracy; i++) {
        vertex(x+cosLUT[(int)val]*hradius, y+sinLUT[(int)val]*vradius);
        val += inc;
      }
      // unnecessary extra point that spoiled triangulation [rocha]
      if (!hints[NEW_GRAPHICS]) {
        vertex(x + cosLUT[0]*hradius, y + sinLUT[0]*vradius);
      }
      endShape();
    }
  }


  //////////////////////////////////////////////////////////////

  // 3D SHAPES, DRAWN FROM CENTER


  // solid or wire depends on settings for stroke and fill
  // slices/stacks can be set by an advanced option

  //public void cube(float size) {
  public void box(float size) {
    //box(-size/2, -size/2, -size/2,  size/2, size/2, size/2);
    box(size, size, size);
  }


  // OPT this isn't the least bit efficient
  //     because it redraws lines along the vertices
  //     ugly ugly ugly!
  //public void box(float x1, float y1, float z1,
  //          float x2, float y2, float z2) {
  public void box(float w, float h, float d) {
    float x1 = -w/2f; float x2 = w/2f;
    float y1 = -h/2f; float y2 = h/2f;
    float z1 = -d/2f; float z2 = d/2f;

    if (hints[NEW_GRAPHICS]) triangle.setCulling(true);
    
    beginShape(QUADS);

    // front
    vertex(x1, y1, z1);
    vertex(x2, y1, z1);
    vertex(x2, y2, z1);
    vertex(x1, y2, z1);

    // right
    vertex(x2, y1, z1);
    vertex(x2, y1, z2);
    vertex(x2, y2, z2);
    vertex(x2, y2, z1);

    // back
    vertex(x2, y1, z2);
    vertex(x1, y1, z2);
    vertex(x1, y2, z2);
    vertex(x2, y2, z2);

    // left
    vertex(x1, y1, z2);
    vertex(x1, y1, z1);
    vertex(x1, y2, z1);
    vertex(x1, y2, z2);

    // top
    vertex(x1, y1, z2);
    vertex(x2, y1, z2);
    vertex(x2, y1, z1);
    vertex(x1, y1, z1);

    // bottom
    vertex(x1, y2, z1);
    vertex(x2, y2, z1);
    vertex(x2, y2, z2);
    vertex(x1, y2, z2);

    endShape(); 

    if (hints[NEW_GRAPHICS]) triangle.setCulling(false);
  }


  // [toxi031031] used by the new sphere code below
  // precompute vertices along unit sphere with new detail setting
  
  public void sphereDetail(int res) {
    if (res<3) res=3; // force a minimum res
    if (res != sphere_detail) {
      float delta = (float)SINCOS_LENGTH/res;
      float[] cx = new float[res];
      float[] cz = new float[res];
      // calc unit circle in XZ plane
      for (int i = 0; i < res; i++) {
        cx[i] = cosLUT[(int) (i*delta) % SINCOS_LENGTH];
        cz[i] = sinLUT[(int) (i*delta) % SINCOS_LENGTH];
      }
      // computing vertexlist
      // vertexlist starts at south pole
      int vertCount = res * (res-1) + 2;
      int currVert = 0;

      // re-init arrays to store vertices
      sphereX = new float[vertCount];
      sphereY = new float[vertCount];
      sphereZ = new float[vertCount];

      float angle_step = (SINCOS_LENGTH*0.5f)/res;
      float angle = angle_step;

      // step along Y axis
      for (int i = 1; i < res; i++) {
        float curradius = sinLUT[(int) angle % SINCOS_LENGTH];
        float currY = -cosLUT[(int) angle % SINCOS_LENGTH];
        for (int j = 0; j < res; j++) {
          sphereX[currVert] = cx[j] * curradius;
          sphereY[currVert] = currY;
          sphereZ[currVert++] = cz[j] * curradius;
        }
        angle += angle_step;
      }
      sphere_detail = res;
    }
  }


  // cache all the points of the sphere in a static array
  // top and bottom are just a bunch of triangles that land
  // in the center point

  // sphere is a series of concentric circles who radii vary
  // along the shape, based on, er.. cos or something

  // [toxi031031] new sphere code. removed all multiplies with 
  // radius, as scale() will take care of that anyway

  // [toxi031223] updated sphere code (removed modulos) 
  // and introduced sphereAt(x,y,z,r)
  // to avoid additional translate()'s on the user/sketch side
  public void sphere(float r) {
    sphere(0, 0, 0, r);
  }

  public void sphere(float x, float y, float z, float r) {
    if (sphere_detail == 0) {
      sphereDetail(30);
    }

    int v1,v11,v2;
    push();
    if (x!=0f && y!=0f && z!=0f) translate(x,y,z);
    scale(r);

    if (hints[NEW_GRAPHICS]) triangle.setCulling(true);

    // 1st ring from south pole
    beginShape(TRIANGLE_STRIP);
    for (int i = 0; i < sphere_detail; i++) {
      vertex(0, -1, 0);
      vertex(sphereX[i], sphereY[i], sphereZ[i]);
    }
    vertex(0, -1, 0);
    vertex(sphereX[0], sphereY[0], sphereZ[0]);
    endShape();

    // middle rings
    int voff = 0;
    for(int i = 2; i < sphere_detail; i++) {
      v1=v11=voff;
      voff += sphere_detail;
      v2=voff;
      beginShape(TRIANGLE_STRIP);
      for (int j = 0; j < sphere_detail; j++) {
        vertex(sphereX[v1], sphereY[v1], sphereZ[v1++]);
        vertex(sphereX[v2], sphereY[v2], sphereZ[v2++]);
      }
      // close each ring
      v1=v11;
      v2=voff;
      vertex(sphereX[v1], sphereY[v1], sphereZ[v1]);
      vertex(sphereX[v2], sphereY[v2], sphereZ[v2]);
      endShape();
    }

    // add the northern cap
    beginShape(TRIANGLE_STRIP);
    for (int i = 0; i < sphere_detail; i++) {
      v2 = voff + i;
      vertex(0, 1, 0);
      vertex(sphereX[v2], sphereY[v2], sphereZ[v2]);
    }
    vertex(0, 1, 0);
    vertex(sphereX[voff], sphereY[voff], sphereZ[voff]);
    endShape();
    pop();

    if (hints[NEW_GRAPHICS]) triangle.setCulling(false);
    //triangle.setCulling(false);
  }



  //////////////////////////////////////////////////////////////

  // CURVES


  /** 
   * Evalutes quadratic bezier at point t for points a, b, c, d.
   * t varies between 0 and 1, and a and d are the on curve points,
   * b and c are the control points. this can be done once with the
   * x coordinates and a second time with the y coordinates to get 
   * the location of a bezier curve at t.
   *
   * for instance, to convert the following example:<code>
   * stroke(255, 102, 0); 
   * line(85, 20, 10, 10); 
   * line(90, 90, 15, 80); 
   * stroke(0, 0, 0); 
   * bezier(85, 20, 10, 10, 90, 90, 15, 80); 
   * 
   * // draw it in gray, using 10 steps instead of the default 20
   * // this is a slower way to do it, but useful if you need
   * // to do things with the coordinates at each step
   * stroke(128);
   * beginShape(LINE_STRIP);
   * for (int i = 0; i <= 10; i++) {
   *   float t = i / 10.0f;
   *   float x = bezier(85, 10, 90, 15, t);
   *   float y = bezier(20, 10, 90, 80, t);
   *   vertex(x, y); 
   * }
   * endShape();</code>
   */
  public float bezierPoint(float a, float b, float c, float d,
                           float t) {
    float t1 = 1.0f - t;

    // quadratic bezier
    //return a*t1*t1 + 2*b*t*t1 + c*t*t;

    // cubic bezier
    //return a*t*t*t + 3*b*t*t*t1 + 3*c*t*t1*t1 + d*t1*t1*t1;
    return a*t1*t1*t1 + 3*b*t*t1*t1 + 3*c*t*t*t1 + d*t*t*t;
  }


  /**
   * Provide the tangent at the given point on the bezier curve.
   * Based on code from v3ga's wordstree sketch.
   */
  public float bezierTangent(float a, float b, float c, float d,
                             float t) {
    float t1 = 1.0f - t;

    return (a *  3 * t*t + 
            b *  3 * t * (2 - 3*t) + 
            c *  3 * (3*t*t - 4*t + 1) +
            d * -3 * t1*t1);
  }


  public void bezier(float x1, float y1, 
                     float x2, float y2,
                     float x3, float y3,
                     float x4, float y4) {
    beginShape(LINE_STRIP);
    bezierVertex(x1, y1);
    bezierVertex(x2, y2);
    bezierVertex(x3, y3);
    bezierVertex(x4, y4);
    endShape();
  }


  public void bezier(float x1, float y1, float z1,
                     float x2, float y2, float z2,
                     float x3, float y3, float z3,
                     float x4, float y4, float z4) {
    beginShape(LINE_STRIP);
    bezierVertex(x1, y1, z1);
    bezierVertex(x2, y2, z2);
    bezierVertex(x3, y3, z3);
    bezierVertex(x4, y4, z4);
    endShape();
  }


  //static final int BEZIER_SEGMENTS = 20;
  private boolean bezier_inited = false;
  private int bezier_segments = 20; //BEZIER_SEGMENTS;
  // msjvm complained when bezier_basis was final
  private float bezier_basis[][] = {  
    { -1,  3, -3,  1},
    {  3, -6,  3,  0},
    { -3,  3,  0,  0},
    {  1,  0,  0,  0}
  };
  private float bezier_forward[][]; // = new float[4][4];
  private float bezier_draw[][]; // = new float[4][4];


  private void bezier_init() {
    bezierSegments(bezier_segments); //BEZIER_SEGMENTS);
    //bezier_inited = true;
  }


  public void bezierSegments(int segments) {
    if (bezier_forward == null) {
      bezier_forward = new float[4][4];
      bezier_draw = new float[4][4];
    }
    bezier_segments = segments;
    bezier_inited = true;

    // setup matrix for forward differencing to speed up drawing
    setup_spline_forward(segments, bezier_forward);

    // multiply the basis and forward diff matrices together
    // saves much time since this needn't be done for each curve
    mult_spline_matrix(bezier_forward, bezier_basis, bezier_draw, 4);
  }


  // catmull-rom basis matrix, perhaps with optional s parameter
  private boolean curve_inited = false;
  //static final int CURVE_SEGMENTS = 20;
  private int curve_segments = 20; //CURVE_SEGMENTS;
  private float curve_tightness = 0;
  private float curve_basis[][]; // = new float[4][4];
  private float curve_forward[][]; // = new float[4][4];
  private float curve_draw[][];


  private void curve_init() {
    curve_mode(curve_segments, curve_tightness);
    //curve_inited = true;
  }


  public void curveSegments(int segments) {
    curve_mode(segments, curve_tightness);
    //curve_inited = true;
  }


  public void curveTightness(float tightness) {
    curve_mode(curve_segments, tightness);
    //curve_inited = true;
  }


  /** 
   * Set the number of segments to use when drawing a Catmull-Rom
   * curve, and setting the s parameter, which defines how tightly
   * the curve fits to each vertex. Catmull-Rom curves are actually
   * a subset of this curve type where the s is set to zero.
   * 
   * (This function is not optimized, since it's not expected to 
   * be called all that often. there are many juicy and obvious 
   * opimizations in here, but it's probably better to keep the 
   * code more readable)
   */
  private void curve_mode(int segments, float s) {
    curve_segments = segments;
    //curve_mode = ((curve_tightness != 0) ||
    //            (curve_segments != CURVE_SEGMENTS));

    if (curve_basis == null) {
      // allocate these when used, to save startup time
      curve_basis = new float[4][4];
      curve_forward = new float[4][4]; 
      curve_draw = new float[4][4]; 
      curve_inited = true;
    }

    float c[][] = curve_basis;

    c[0][0] = s-1;     c[0][1] = s+3;  c[0][2] = -3-s;    c[0][3] = 1-s;
    c[1][0] = 2*(1-s); c[1][1] = -5-s; c[1][2] = 2*(s+2); c[1][3] = s-1;
    c[2][0] = s-1;     c[2][1] = 0;    c[2][2] = 1-s;     c[2][3] = 0;
    c[3][0] = 0;       c[3][1] = 2;    c[3][2] = 0;       c[3][3] = 0;

    for (int i = 0; i < 4; i++) {
      for (int j = 0; j < 4; j++) {
        c[i][j] /= 2f;
      }
    }
    setup_spline_forward(segments, curve_forward);

    // multiply the basis and forward diff matrices together
    // saves much time since this needn't be done for each curve
    mult_spline_matrix(curve_forward, curve_basis, curve_draw, 4);
  }


  /**
   * Get a location along a catmull-rom curve segment.
   * 
   * @param t Value between zero and one for how far along the segment
   */
  public float curvePoint(float a, float b, float c, float d,
                          float t) {
    if (!curve_inited) curve_init();

    float tt = t * t; 
    float ttt = t * tt;
    float m[][] = curve_basis;

    // not optimized (and probably need not be)
    return (a * (ttt*m[0][0] + tt*m[1][0] + t*m[2][0] + m[3][0]) +
            b * (ttt*m[0][1] + tt*m[1][1] + t*m[2][1] + m[3][1]) +
            c * (ttt*m[0][2] + tt*m[1][2] + t*m[2][2] + m[3][2]) +
            d * (ttt*m[0][3] + tt*m[1][3] + t*m[2][3] + m[3][3]));
  }


  public float curveTangent(float a, float b, float c, float d,
                            float t) {
    System.err.println("curveTangent not yet implemented");
    return 0;
  }


  /**
   * Draws a segment of Catmull-Rom curve.
   *
   * This function doubles the first and last points to use them 
   * as control points, because four vertices for a Catmull-Rom
   * is basically a line. So in the end, this will produce three
   * curve segments (where bezier would only produce one with its
   * complementary function).
   */
  public void curve(float x1, float y1, 
                    float x2, float y2,
                    float x3, float y3,
                    float x4, float y4) {
    beginShape(LINE_STRIP);
    curveVertex(x1, y1);
    curveVertex(x1, y1);
    curveVertex(x2, y2);
    curveVertex(x3, y3);
    curveVertex(x4, y4);
    curveVertex(x4, y4);
    endShape();
  }


  public void curve(float x1, float y1, float z1,
                    float x2, float y2, float z2,
                    float x3, float y3, float z3,
                    float x4, float y4, float z4) {
    beginShape(LINE_STRIP);
    curveVertex(x1, y1, z1);
    curveVertex(x1, y1, z1);
    curveVertex(x2, y2, z2);
    curveVertex(x3, y3, z3);
    curveVertex(x4, y4, z4);
    curveVertex(x4, y4, z4);
    endShape();
  }


  /**
   * Setup forward-differencing matrix to be used for speedy 
   * curve rendering. It's based on using a specific number 
   * of curve segments and just doing incremental adds for each
   * vertex of the segment, rather than running the mathematically
   * expensive cubic equation. 
   * @param segments number of curve segments to use when drawing
   */
  private void setup_spline_forward(int segments, float fwd[][]) {
    float f  = 1.0f / segments;
    float ff = f * f;
    float fff = ff * f;

    fwd[0][0] = 0;     fwd[0][1] = 0;    fwd[0][2] = 0; fwd[0][3] = 1;
    fwd[1][0] = fff;   fwd[1][1] = ff;   fwd[1][2] = f; fwd[1][3] = 0;
    fwd[2][0] = 6*fff; fwd[2][1] = 2*ff; fwd[2][2] = 0; fwd[2][3] = 0;
    fwd[3][0] = 6*fff; fwd[3][1] = 0;    fwd[3][2] = 0; fwd[3][3] = 0;
  }


  // internal matrix multiplication routine used by the spline code
  // should these go to 4 instead of 3?
  //void mult_curve_matrix(float m[4][4], float g[4][3], float mg[4][3]);
  private void mult_spline_matrix(float m[][], float g[][], 
                                  float mg[][], int dimensions) {
    for (int i = 0; i < 4; i++) {
      for (int j = 0; j < dimensions; j++) {
        mg[i][j] = 0;
      }
    }
    for (int i = 0; i < 4; i++) {
      for (int j = 0; j < dimensions; j++) {
        for (int k = 0; k < 4; k++) {
          mg[i][j] = mg[i][j] + (m[i][k] * g[k][j]);
        }
      }
    }
  }


  /** 
   * Draw a segment of spline (bezier or catmull-rom curve) 
   * using the matrix m, which is the basis matrix already 
   * multiplied with the forward differencing matrix. 
   *
   * the x0, y0, z0 points are the point that's being used as
   * the start, and also as the accumulator. for bezier curves,
   * the x1, y1, z1 are the first point drawn, and added to.
   * for catmull-rom curves, the first control point (x2, y2, z2)
   * is the first drawn point, and is accumulated to.
   */
  private void spline_segment(float x1, float y1, float x2, float y2, 
                              float x3, float y3, float x4, float y4,
                              float x0, float y0, float m[][], int segments) {

    float xplot1 = m[1][0]*x1 + m[1][1]*x2 + m[1][2]*x3 + m[1][3]*x4;
    float xplot2 = m[2][0]*x1 + m[2][1]*x2 + m[2][2]*x3 + m[2][3]*x4;
    float xplot3 = m[3][0]*x1 + m[3][1]*x2 + m[3][2]*x3 + m[3][3]*x4;

    float yplot1 = m[1][0]*y1 + m[1][1]*y2 + m[1][2]*y3 + m[1][3]*y4;
    float yplot2 = m[2][0]*y1 + m[2][1]*y2 + m[2][2]*y3 + m[2][3]*y4;
    float yplot3 = m[3][0]*y1 + m[3][1]*y2 + m[3][2]*y3 + m[3][3]*y4;

    vertex(x0, y0);
    for (int j = 0; j < segments; j++) {
      x0 += xplot1; xplot1 += xplot2; xplot2 += xplot3;
      y0 += yplot1; yplot1 += yplot2; yplot2 += yplot3;
      vertex(x0, y0);
    }
  }


  private void spline_segment(float x1, float y1, float z1, 
                              float x2, float y2, float z2,
                              float x3, float y3, float z3, 
                              float x4, float y4, float z4,
                              float x0, float y0, float z0,
                              float m[][], int segments) {

    float xplot1 = m[1][0]*x1 + m[1][1]*x2 + m[1][2]*x3 + m[1][3]*x4;
    float xplot2 = m[2][0]*x1 + m[2][1]*x2 + m[2][2]*x3 + m[2][3]*x4;
    float xplot3 = m[3][0]*x1 + m[3][1]*x2 + m[3][2]*x3 + m[3][3]*x4;

    float yplot1 = m[1][0]*y1 + m[1][1]*y2 + m[1][2]*y3 + m[1][3]*y4;
    float yplot2 = m[2][0]*y1 + m[2][1]*y2 + m[2][2]*y3 + m[2][3]*y4;
    float yplot3 = m[3][0]*y1 + m[3][1]*y2 + m[3][2]*y3 + m[3][3]*y4;

    float zplot1 = m[1][0]*z1 + m[1][1]*z2 + m[1][2]*z3 + m[1][3]*z4;
    float zplot2 = m[2][0]*z1 + m[2][1]*z2 + m[2][2]*z3 + m[2][3]*z4;
    float zplot3 = m[3][0]*z1 + m[3][1]*z2 + m[3][2]*z3 + m[3][3]*z4;

    vertex(x0, y0, z0);
    for (int j = 0; j < segments; j++) {
      x0 += xplot1; xplot1 += xplot2; xplot2 += xplot3;
      y0 += yplot1; yplot1 += yplot2; yplot2 += yplot3;
      z0 += zplot1; zplot1 += zplot2; zplot2 += zplot3;
      //vertex(x1, y1, z1);
      vertex(x0, y0, z0);
    }
  }



  //////////////////////////////////////////////////////////////

  // IMAGES


  private Image gimmeImage(URL url, boolean force) {
    Toolkit tk = Toolkit.getDefaultToolkit();

    URLConnection conn = null;
    try {
      //conn = new URLConnection(url);
      conn = url.openConnection();

      // i don't think this does anything, 
      // but just set the fella for good measure
      conn.setUseCaches(false);
      // also had a note from zach about parent.obj.close() on url
      // but that doesn't seem to be needed anymore...

      // throws an exception if it doesn't exist
      conn.connect();

      if (!force) {
        // how do you close the bastard?
        conn = null;
        // close connection and just use regular method
        return tk.getImage(url);
      }

      // slurp contents of that stream
      InputStream stream = conn.getInputStream();

      BufferedInputStream bis = new BufferedInputStream(stream);
      ByteArrayOutputStream out = new ByteArrayOutputStream();

      try {
        int c = bis.read();
        while (c != -1) {
          out.write(c);
          c = bis.read();
        }
      } catch (IOException e) {
        return null;
      }
      bis.close();  // will this help?
      //byte bytes[] = out.toByteArray();

      // build an image out of it
      //return tk.createImage(bytes);
      return tk.createImage(out.toByteArray());

    } catch (Exception e) {  // null pointer or i/o ex
      //System.err.println("error loading image: " + url);
      return null;
    }
  }

  public PImage loadImage(String filename) {
    if (filename.toLowerCase().endsWith(".tga")) {
      return loadTargaImage(filename);
    }
    return loadImage(filename, true);
  }

  // returns null if no image of that name is found
  public PImage loadImage(String filename, boolean force) {
    Image awtimage = null;
    //String randomizer = "?" + nf((int) (random()*10000), 4);

    if (filename.startsWith("http://")) {
      try {
        URL url = new URL(filename);
        awtimage = gimmeImage(url, force);

      } catch (MalformedURLException e) { 
        System.err.println("error loading image from " + filename);
        e.printStackTrace();
        return null;
      }

    } else {
      awtimage = gimmeImage(getClass().getResource(filename), force);
      if (awtimage == null) {
        awtimage = 
          gimmeImage(getClass().getResource("data/" + filename), force);
      }
      /*
        boolean insideBrowser = true;
        try {
        applet.getAppletContext();
        } catch (NullPointerException e) {
        insideBrowser = false;
        force = false;  // the ?2394 trick won't work
        }

        System.out.println("get dat way");
        URL url = force ? 
        getClass().getResource(filename);
        try {
        url.openConnection();
        } catch (Exception e) {
        System.out.println("unhappy");
        e.printStackTrace();
        }
         //awtimage = tk.getImage();
        System.out.println("got url");
        awtimage = tk.getImage(url);

        if (awtimage == null) {
        System.out.println("get dis way");
        awtimage = tk.getImage(getClass().getResource("data/" + filename));
        }
      */
    }
    if (awtimage == null) {
      System.err.println("could not load image " + filename);
      return null;
    }

    Component component = applet;
    if (component == null) {
      component = new Frame();
      ((Frame)component).pack();
      // now we have a peer! yay!
    }

    MediaTracker tracker = new MediaTracker(component);
    tracker.addImage(awtimage, 0);
    try {
      tracker.waitForAll();
    } catch (InterruptedException e) { 
      e.printStackTrace();
    }

    int jwidth = awtimage.getWidth(null);
    int jheight = awtimage.getHeight(null);

    int jpixels[] = new int[jwidth*jheight];
    PixelGrabber pg = 
      new PixelGrabber(awtimage, 0, 0, jwidth, jheight, jpixels, 0, jwidth);
    try {
      pg.grabPixels();
    } catch (InterruptedException e) { 
      e.printStackTrace();
    }

    //int format = RGB;
    if (filename.toLowerCase().endsWith(".gif")) {
      // if it's a .gif image, test to see if it has transparency
      for (int i = 0; i < jpixels.length; i++) {
        // since transparency is often at corners, hopefully this
        // will find a non-transparent pixel quickly and exit
        if ((jpixels[i] & 0xff000000) != 0xff000000) {
          return new PImage(jpixels, jwidth, jheight, RGBA);
          //format = RGBA;
          //break;
        }
      }
    }
    return new PImage(jpixels, jwidth, jheight, RGB);
  }


  /*
  public PImage loadImage(String file) { 
    try { 
      byte[] imgarray=loadBytes(file); 
      java.awt.Image awtimage = 
        Toolkit.getDefaultToolkit().createImage(imgarray); 
      MediaTracker tracker = new MediaTracker(this); 
      tracker.addImage(awtimage, 0); 
      try { 
        tracker.waitForAll(); 
      } catch (InterruptedException e) { 
        e.printStackTrace(); 
      } 
  
      int w = awtimage.getWidth(null); 
      int h = awtimage.getHeight(null); 
      int[] pix = new int[w*h]; 
  
      PixelGrabber pg = new PixelGrabber(awtimage, 0, 0, w, h, pix, 0, w); 
  
      try { 
        pg.grabPixels(); 
      } catch (InterruptedException e) { 
        e.printStackTrace(); 
      } 
  
      PImage img=new PImage(pix,w,h,RGB); 
  
      if (file.toLowerCase().endsWith(".gif")) { 
        for (int i = 0; i < pix.length; i++) { 
          if ((pix[i] & 0xff000000) != 0xff000000) { 
            img.format=RGBA; 
            break; 
          } 
        } 
      } 
      return img; 
    } 
    catch(Exception e) { 
      return null; 
    } 
  } 
   */


  // [toxi 040304] Targa bitmap loader for 24/32bit RGB(A) 

  // [fry] this could be optimized to not use loadBytes
  // which would help out memory situations with large images

  protected PImage loadTargaImage(String filename) {
    // load image file as byte array 
    byte[] buffer = loadBytes(filename); 
  
   // check if it's a TGA and has 8bits/colour channel 
   if (buffer[2] == 2 && buffer[17] == 8) { 
     // get image dimensions 
     //int w=(b2i(buffer[13])<<8) + b2i(buffer[12]); 
     int w = ((buffer[13] & 0xff) << 8) + (buffer[12] & 0xff);
     //int h=(b2i(buffer[15])<<8) + b2i(buffer[14]); 
     int h = ((buffer[15] & 0xff) << 8) + (buffer[14] & 0xff);
     // check if image has alpha 
     boolean hasAlpha=(buffer[16] == 32); 
  
     // setup new image object 
     PImage img = new PImage(w,h); 
     img.format = (hasAlpha ? RGBA : RGB); 
  
     // targa's are written upside down, so we need to parse it in reverse 
     int index = (h-1) * w; 
     // actual bitmap data starts at byte 18 
     int offset = 18; 
  
     // read out line by line 
     for (int y = h-1; y >= 0; y--) { 
       for (int x = 0; x < w; x++) { 
         img.pixels[index + x] = 
           ((buffer[offset++] & 0xff)) | 
           ((buffer[offset++] & 0xff) << 8) | 
           ((buffer[offset++] & 0xff) << 16) | 
           (hasAlpha ? ((buffer[offset++] & 0xff) << 24) : 0xff000000);
           
         //b2i(buffer[offset++]) | b2i(buffer[offset++])<<8 | b2i(buffer[offset++])<<16; 
         // set alpha based on alpha data or revert to 100% (if there's only a 24bit image) 
         //if (hasAlpha) img.pixels[index + x]|=b2i(buffer[offset++])<<24; 
         //else img.pixels[index + x] |= 0xff000000; 
       } 
       index -= w; 
     }
     return img; 
   } 
   System.err.println("loadImage(): bad targa image format"); 
   return null; 
  }


  public void imageMode(int mode) {
    image_mode = mode;
  }

  public void image(PImage image, float x1, float y1) {
    if ((dimensions == 0) && !lighting && !_tint &&
        (image_mode != CENTER_RADIUS)) {
      // if drawing a flat image with no warping, 
      // use faster routine to draw direct to the screen
      flat_image(image, (int)x1, (int)y1);

    } else {
      image(image, x1, y1, image.width, image.height, 
            0, 0, image.width, image.height);
    }
  }


  public void image(PImage image, 
                    float x1, float y1, float x2, float y2) {
    image(image, x1, y1, x2, y2, 0, 0, image.width, image.height);
  }


  public void image(PImage image, 
                    float x1, float y1, float x2, float y2,
                    float u1, float v1, float u2, float v2) {
    switch (image_mode) {
    case CORNERS: 
      break;
    case CORNER: 
      x2 += x1; y2 += y1; 
      break;
    case CENTER_DIAMETER:
      x2 /= 2f;
      y2 /= 2f;
    case CENTER_RADIUS:
      float hr = x2; 
      float vr = y2;
      x2 = x1 + hr; 
      y2 = y1 + vr;
      x1 -= hr; 
      y1 -= vr;
      break;
    }

    // fill must be set to 'true' for image to show up
    // (although need to do some sort of color blending)
    // stroke should be set to false or it gets confusing
    // (and annoying) because one has to keep disabling stroke

    boolean savedStroke = _stroke;
    boolean savedFill = _fill;

    float savedFillR = fillR;
    float savedFillG = fillG;
    float savedFillB = fillB;
    float savedFillA = fillA;

    _stroke = false;
    _fill = true;

    if (_tint) {
      fillR = tintR;
      fillG = tintG;
      fillB = tintB;
      fillA = tintA;

    } else {
      fillR = fillG = fillB = fillA = 1;
    }

    beginShape(QUADS);
    texture(image); // moved outside.. make javagl happier?
    vertex(x1, y1, u1, v1);
    vertex(x1, y2, u1, v2);
    vertex(x2, y2, u2, v2);
    vertex(x2, y1, u2, v1);
    endShape();

    _stroke = savedStroke;
    _fill = savedFill;

    fillR = savedFillR;
    fillG = savedFillG;
    fillB = savedFillB;
    fillA = savedFillA;
  }


  public void cache(PImage image) {
  }

  public void cache(PImage images[]) {    
  }

  protected void cache(PImage image, int index) {
  }


  //////////////////////////////////////////////////////////////

  // TEXT/FONTS


  public PFont loadFont(String name) {
    try {
      PFont font = new PFont(name, this);
      return font;

    } catch (IOException e) {
      System.err.println("Could not load font " + name);
      System.err.println("Make sure that the font has been copied");
      System.err.println("to the data folder of your sketch.");
      e.printStackTrace();
    }
    return null;
  }


  public void textFont(PFont which) {
    if (which == null) {
      System.err.println("Ignoring improperly loaded font in textFont()");
      return;
    }
    text_font = which;
    if (text_space != SCREEN_SPACE) {
      text_font.size();
    } else {
      text_font.size(text_font.iwidth);
    }
    text_font.leading();
  }

  public void textFont(PFont which, float size) {
    if (which == null) {
      System.err.println("Ignoring improperly loaded font in textFont()");
      return;
    }
    text_font = which;
    if (text_space != SCREEN_SPACE) {
      text_font.size(size);
    } else {
      System.err.println("Cannot set size of SCREEN_SPACE fonts");
      text_font.size(text_font.iwidth);
    }
    text_font.leading();
  }

  public void textSize(float size) {
    if (text_font == null) {
      System.err.println("First set a font before setting its size.");
      return;
    }
    if (text_space == SCREEN_SPACE) {
      System.err.println("Cannot set size of SCREEN_SPACE fonts.");
      return;
    }
    text_font.size(size);
  }

  public void textLeading(float leading) {
    if (text_font == null) {
      System.err.println("First set a font before setting its leading.");
      return;
    }
    text_font.leading(leading);
  }

  public void textMode(int mode) {
    text_mode = mode;
  }

  public void textSpace(int space) {
    text_space = space;

    if ((space == SCREEN_SPACE) && (text_font != null)) {
      text_font.size(text_font.iwidth);
      text_font.leading();
    }
  }


  public void text(char c, float x, float y) {
    text(c, x, y, 0);
  }

  public void text(char c, float x, float y, float z) {
    if (text_font == null) {
      System.err.println("text(): first set a font before drawing text");
      return;
    }
    if (text_mode == ALIGN_CENTER) {
      x -= text_font.width(c) / 2f;

    } else if (text_mode == ALIGN_RIGHT) {
      x -= text_font.width(c); 
    }
    text_font.text(c, x, y, z, this);
  }


  public void text(String s, float x, float y) {
    text(s, x, y, 0);
  }

  public void text(String s, float x, float y, float z) {
    if (text_font == null) {
      System.err.println("text(): first set a font before drawing text");
      return;
    }
    if (text_mode == ALIGN_CENTER) {
      x -= text_font.width(s) / 2f;

    } else if (text_mode == ALIGN_RIGHT) {
      x -= text_font.width(s); 
    }
    text_font.text(s, x, y, z, this);
  }


  public void text(int num, float x, float y) {
    text(String.valueOf(num), x, y, 0);
  }

  public void text(int num, float x, float y, float z) {
    text(String.valueOf(num), x, y, z);
  }


  public void text(float num, float x, float y) {
    text(PApplet.nfs(num, 0, 3), x, y, 0);
  }

  /**
   * This does a basic number formatting, to avoid the 
   * generally ugly appearance of printing floats.
   * Users who want more control should use their own nfs() cmmand.
   */
  public void text(float num, float x, float y, float z) {
    text(PApplet.nfs(num, 0, 3), x, y, z);
  }


  //////////////////////////////////////////////////////////////

  // MATRIX MATH


  public void push() {
    if (matrixStackDepth+1 == MATRIX_STACK_DEPTH) {
      message(COMPLAINT, "matrix stack overflow, to much pushmatrix");
      return;
    }
    float cm[] = matrixStack[matrixStackDepth];
    cm[ 0] = m00; cm[ 1] = m01; cm[ 2] = m02; cm[ 3] = m03;
    cm[ 4] = m10; cm[ 5] = m11; cm[ 6] = m12; cm[ 7] = m13;
    cm[ 8] = m20; cm[ 9] = m21; cm[10] = m22; cm[11] = m23;
    cm[12] = m30; cm[13] = m31; cm[14] = m32; cm[15] = m33;
    matrixStackDepth++;
  }


  public void pop() {
    if (matrixStackDepth == 0) {
      message(COMPLAINT, "matrix stack underflow, to many popmatrix");
      return;
    }
    matrixStackDepth--;
    float cm[] = matrixStack[matrixStackDepth];
    m00 = cm[ 0]; m01 = cm[ 1]; m02 = cm[ 2]; m03 = cm[ 3];
    m10 = cm[ 4]; m11 = cm[ 5]; m12 = cm[ 6]; m13 = cm[ 7];
    m20 = cm[ 8]; m21 = cm[ 9]; m22 = cm[10]; m23 = cm[11];
    m30 = cm[12]; m31 = cm[13]; m32 = cm[14]; m33 = cm[15];

    if ((matrixStackDepth == 0) &&
        (m00 == 1) && (m01 == 0) && (m02 == 0) && (m03 == 0) &&
        (m10 == 0) && (m11 == 1) && (m12 == 0) && (m13 == 0) &&
        (m20 == 0) && (m21 == 0) && (m22 == 1) && (m23 == 0) &&
        (m30 == 0) && (m31 == 0) && (m32 == 0) && (m33 == 1)) {
      dimensions = 0;
    }
  }


  public void resetMatrix() {
    dimensions = 0;
    m00 = 1; m01 = 0; m02 = 0; m03 = 0;
    m10 = 0; m11 = 1; m12 = 0; m13 = 0;
    m20 = 0; m21 = 0; m22 = 1; m23 = 0;
    m30 = 0; m31 = 0; m32 = 0; m33 = 1;
  }


  public void applyMatrix(float n00, float n01, float n02, float n03,
                          float n10, float n11, float n12, float n13,
                          float n20, float n21, float n22, float n23,
                          float n30, float n31, float n32, float n33) {
    //modelMatrixIsIdentity = false;

    float r00 = m00*n00 + m01*n10 + m02*n20 + m03*n30;
    float r01 = m00*n01 + m01*n11 + m02*n21 + m03*n31;
    float r02 = m00*n02 + m01*n12 + m02*n22 + m03*n32;
    float r03 = m00*n03 + m01*n13 + m02*n23 + m03*n33;

    float r10 = m10*n00 + m11*n10 + m12*n20 + m13*n30;
    float r11 = m10*n01 + m11*n11 + m12*n21 + m13*n31;
    float r12 = m10*n02 + m11*n12 + m12*n22 + m13*n32;
    float r13 = m10*n03 + m11*n13 + m12*n23 + m13*n33;

    float r20 = m20*n00 + m21*n10 + m22*n20 + m23*n30;
    float r21 = m20*n01 + m21*n11 + m22*n21 + m23*n31;
    float r22 = m20*n02 + m21*n12 + m22*n22 + m23*n32;
    float r23 = m20*n03 + m21*n13 + m22*n23 + m23*n33;

    float r30 = m30*n00 + m31*n10 + m32*n20 + m33*n30;
    float r31 = m30*n01 + m31*n11 + m32*n21 + m33*n31;
    float r32 = m30*n02 + m31*n12 + m32*n22 + m33*n32;
    float r33 = m30*n03 + m31*n13 + m32*n23 + m33*n33;

    m00 = r00; m01 = r01; m02 = r02; m03 = r03;
    m10 = r10; m11 = r11; m12 = r12; m13 = r13;
    m20 = r20; m21 = r21; m22 = r22; m23 = r23;
    m30 = r30; m31 = r31; m32 = r32; m33 = r33;
  }


  public void printMatrix() {
    int big = (int) max(max(max(max(abs(m00), abs(m01)), max(abs(m02), abs(m03))),
                            max(max(abs(m10), abs(m11)), max(abs(m12), abs(m13)))),
                        max(max(max(abs(m20), abs(m21)), max(abs(m22), abs(m23))),
                            max(max(abs(m30), abs(m31)), max(abs(m32), abs(m33)))));
    int d = 1;
    while ((big /= 10) != 0) d++;

    PApplet.println(PApplet.nfs(m00, d, 4) + " " + PApplet.nfs(m01, d, 4) + " " + 
                    PApplet.nfs(m02, d, 4) + " " + PApplet.nfs(m03, d, 4));

    PApplet.println(PApplet.nfs(m10, d, 4) + " " + PApplet.nfs(m11, d, 4) + " " + 
                    PApplet.nfs(m12, d, 4) + " " + PApplet.nfs(m13, d, 4));

    PApplet.println(PApplet.nfs(m20, d, 4) + " " + PApplet.nfs(m21, d, 4) + " " + 
                    PApplet.nfs(m22, d, 4) + " " + PApplet.nfs(m23, d, 4));

    PApplet.println(PApplet.nfs(m30, d, 4) + " " + PApplet.nfs(m31, d, 4) + " " + 
                    PApplet.nfs(m32, d, 4) + " " + PApplet.nfs(m33, d, 4));

    PApplet.println();
  }


  //////////////////////////////////////////////////////////////


  public void beginCamera() {
    resetMatrix();
  }

  public void cameraMode(int icameraMode) {
    camera_mode = icameraMode;  // this doesn't do much

    if (camera_mode == PERSPECTIVE) {
      beginCamera();
      perspective(fov, aspect, nearDist, farDist);
      lookat(eyeX, eyeY, eyeDist,  eyeX, eyeY, 0,  0, 1, 0);
      endCamera();

    } else if (camera_mode == ORTHOGRAPHIC) {
      beginCamera();
      ortho(0, width, 0, height, -10, 10);
      endCamera();
    }
  }

  public void endCamera() {
    p00 = m00; p01 = m01; p02 = m02; p03 = m03;
    p10 = m10; p11 = m11; p12 = m12; p13 = m13;
    p20 = m20; p21 = m21; p22 = m22; p23 = m23;
    p30 = m30; p31 = m31; p32 = m32; p33 = m33;    
    resetMatrix();
  }


  public void printCamera() {
    int big = (int) max(max(max(max(abs(p00), abs(p01)), max(abs(p02), abs(p03))),
                            max(max(abs(p10), abs(p11)), max(abs(p12), abs(p13)))),
                        max(max(max(abs(p20), abs(p21)), max(abs(p22), abs(p23))),
                            max(max(abs(p30), abs(p31)), max(abs(p32), abs(p33)))));
    int d = 1;
    while ((big /= 10) != 0) d++;

    PApplet.println(PApplet.nfs(p00, d, 4) + " " + PApplet.nfs(p01, d, 4) + " " + 
                    PApplet.nfs(p02, d, 4) + " " + PApplet.nfs(p03, d, 4));

    PApplet.println(PApplet.nfs(p10, d, 4) + " " + PApplet.nfs(p11, d, 4) + " " + 
                    PApplet.nfs(p12, d, 4) + " " + PApplet.nfs(p13, d, 4));

    PApplet.println(PApplet.nfs(p20, d, 4) + " " + PApplet.nfs(p21, d, 4) + " " + 
                    PApplet.nfs(p22, d, 4) + " " + PApplet.nfs(p23, d, 4));

    PApplet.println(PApplet.nfs(p30, d, 4) + " " + PApplet.nfs(p31, d, 4) + " " + 
                    PApplet.nfs(p32, d, 4) + " " + PApplet.nfs(p33, d, 4));

    PApplet.println();
  }


  // all the screenX/Y/Z and objectX/Y/Z functions return
  // values based on there being a 3D scene. the assumption is
  // that even if dimensions isn't necessarily 3, the only
  // time you'll want to use these functions is when there
  // has been a transformation, and they're not intended to be
  // fast anyway, so it should be just fine that way.

  public float screenX(float x, float y, float z) {
    float ax = m00*x + m01*y + m02*z + m03;
    float ay = m10*x + m11*y + m12*z + m13;
    float az = m20*x + m21*y + m22*z + m23;
    float aw = m30*x + m31*y + m32*z + m33;

    float ox = p00*ax + p01*ay + p02*az + p03*aw;
    float ow = p30*ax + p31*ay + p32*az + p33*aw;

    if (ow != 0) ox /= ow;
    return width * (1 + ox) / 2.0f;
  }


  public float screenY(float x, float y, float z) {
    float ax = m00*x + m01*y + m02*z + m03;
    float ay = m10*x + m11*y + m12*z + m13;
    float az = m20*x + m21*y + m22*z + m23;
    float aw = m30*x + m31*y + m32*z + m33;

    float oy = p10*ax + p11*ay + p12*az + p13*aw;
    float ow = p30*ax + p31*ay + p32*az + p33*aw;

    if (ow != 0) oy /= ow;
    return height * (1 + oy) / 2.0f;
  }


  public float screenZ(float x, float y, float z) {
    float ax = m00*x + m01*y + m02*z + m03;
    float ay = m10*x + m11*y + m12*z + m13;
    float az = m20*x + m21*y + m22*z + m23;
    float aw = m30*x + m31*y + m32*z + m33;

    float oz = p20*ax + p21*ay + p22*az + p23*aw;
    float ow = p30*ax + p31*ay + p32*az + p33*aw;

    if (ow != 0) oz /= ow;
    return (oz + 1) / 2.0f;
  }


  public float objectX(float x, float y, float z) {
    float ax = m00*x + m01*y + m02*z + m03;
    float aw = m30*x + m31*y + m32*z + m33;
    return (aw != 0) ? ax / aw : ax;
  }


  public float objectY(float x, float y, float z) {
    float ay = m10*x + m11*y + m12*z + m13;
    float aw = m30*x + m31*y + m32*z + m33;
    return (aw != 0) ? ay / aw : ay;
  }


  public float objectZ(float x, float y, float z) {
    float az = m20*x + m21*y + m22*z + m23;
    float aw = m30*x + m31*y + m32*z + m33;
    return (aw != 0) ? az / aw : az;
  }


  //////////////////////////////////////////////////////////////


  // based on mesa, matrix.c
  public void ortho(float left, float right, 
                    float bottom, float top,
                    float near, float far) {
    float x =  2.0f / (right - left);
    float y =  2.0f / (top - bottom);
    float z = -2.0f / (far - near);

    float tx = -(right + left) / (right - left);
    float ty = -(top + bottom) / (top - bottom);
    float tz = -(far + near) / (far - near);

    applyMatrix(x, 0, 0, tx,
                0, y, 0, ty,
                0, 0, z, tz, 
                0, 0, 0, 1);
  }


  // based on mesa, glu.c
  public void perspective(float fovy, float aspect, float zNear, float zFar) {
    //System.out.println("perspective: " + fovy + " " + aspect + " " + 
    //               zNear + " " + zFar);
    float ymax = zNear * tan(fovy * PI / 360.0f);
    float ymin = -ymax;

    float xmin = ymin * aspect;
    float xmax = ymax * aspect;

    frustum(xmin, xmax, ymin, ymax, zNear, zFar);
  }


  // implemented based on gl ref book
  public void frustum(float left, float right, float bottom,
                      float top, float znear, float zfar) {
    //System.out.println("frustum: " + left + " " + right + "  " + 
    //               bottom + " " + top + "  " + znear + " " + zfar);
    applyMatrix((2*znear)/(right-left), 0, (right+left)/(right-left), 0,
                0, (2*znear)/(top-bottom), (top+bottom)/(top-bottom), 0,
                0, 0, -(zfar+znear)/(zfar-znear),-(2*zfar*znear)/(zfar-znear),
                0, 0, -1, 0);
  }


  // based on mesa, glu.c
  public void lookat(float eyeX, float eyeY, float eyeZ,
                     float centerX, float centerY, float centerZ,
                     float upX, float upY, float upZ) {
    float z0 = eyeX - centerX;
    float z1 = eyeY - centerY;
    float z2 = eyeZ - centerZ;
    float mag = sqrt(z0*z0 + z1*z1 + z2*z2);

    if (mag != 0) {
      z0 /= mag;
      z1 /= mag;
      z2 /= mag;
    }

    float y0 = upX;
    float y1 = upY;
    float y2 = upZ;

    float x0 =  y1*z2 - y2*z1;
    float x1 = -y0*z2 + y2*z0;
    float x2 =  y0*z1 - y1*z0;

    y0 =  z1*x2 - z2*x1;
    y1 = -z0*x2 + z2*x0;
    y2 =  z0*x1 - z1*x0;

    mag = sqrt(x0*x0 + x1*x1 + x2*x2); 
    if (mag != 0) {
      x0 /= mag;
      x1 /= mag;
      x2 /= mag;
    }

    mag = sqrt(y0*y0 + y1*y1 + y2*y2);
    if (mag != 0) {
      y0 /= mag;
      y1 /= mag;
      y2 /= mag;
    }

    applyMatrix(x0, x1, x2, 0,  
                y0, y1, y2, 0,
                z0, z1, z2, 0,
                0,  0,  0,  1);
    translate(-eyeX, -eyeY, -eyeZ);
  }


  //////////////////////////////////////////////////////////////


  public void translate(float tx, float ty) {
    if (dimensions == 3) {
      translate(tx, ty, 0);

    } else {
      if (dimensions == 0) dimensions = 2;  // otherwise already 2 or higher

      m03 += tx*m00 + ty*m01 + m02;
      m13 += tx*m10 + ty*m11 + m12;
      m23 += tx*m20 + ty*m21 + m22;
      m33 += tx*m30 + ty*m31 + m32;
    }
  }


  public void translate(float tx, float ty, float tz) {
    dimensions = 3;

    m03 += tx*m00 + ty*m01 + tz*m02;
    m13 += tx*m10 + ty*m11 + tz*m12;
    m23 += tx*m20 + ty*m21 + tz*m22;
    m33 += tx*m30 + ty*m31 + tz*m32;
  }


  // OPT could save several multiplies for the 0s and 1s by just
  //     putting the multMatrix code here and removing uneccessary terms

  public void rotateX(float angle) {
    //rotate(angle, 1, 0, 0);
    dimensions = 3;
    float c = cos(angle);
    float s = sin(angle);
    applyMatrix(1, 0, 0, 0,  0, c, -s, 0,  0, s, c, 0,  0, 0, 0, 1);
  }


  public void rotateY(float angle) {
    //rotate(angle, 0, 1, 0);
    dimensions = 3;
    float c = cos(angle);
    float s = sin(angle);
    applyMatrix(c, 0, s, 0,  0, 1, 0, 0,  -s, 0, c, 0,  0, 0, 0, 1);   
  }


  // two dimensional rotation is the same as rotating along the z-axis
  public void rotate(float angle) {
    rotateZ(angle);
  }

  // note that this doesn't make things 3D
  public void rotateZ(float angle) {
    //rotate(angle, 0, 0, 1);
    if (dimensions == 0) dimensions = 2;  // otherwise already 2 or higher
    float c = cos(angle);
    float s = sin(angle);
    applyMatrix(c, -s, 0, 0,  s, c, 0, 0,  0, 0, 1, 0,  0, 0, 0, 1);
  }


  // should be in radians (i think), instead of degrees (gl uses degrees)
  // based on 15-463 code, but similar to opengl ref p.443

  public void rotate(float angle, float v0, float v1, float v2) {
    //modelMatrixIsIdentity = false;
    dimensions = 3;

    // TODO should make sure this vector is normalized

    float c = cos(angle);
    float s = sin(angle);
    float t = 1.0f - c;

    applyMatrix((t*v0*v0) + c, (t*v0*v1) - (s*v2), (t*v0*v2) + (s*v1), 0,
                (t*v0*v1) + (s*v2), (t*v1*v1) + c, (t*v1*v2) - (s*v0), 0,
                (t*v0*v2) - (s*v1), (t*v1*v2) + (s*v0), (t*v2*v2) + c, 0, 
                0, 0, 0, 1);
  }


  public void scale(float s) {
    if (dimensions == 3) {
      applyMatrix(s, 0, 0, 0,  0, s, 0, 0,  0, 0, s, 0,  0, 0, 0, 1);

    } else {
      dimensions = 2;
      applyMatrix(s, 0, 0, 0,  0, s, 0, 0,  0, 0, 1, 0,  0, 0, 0, 1);
    }

    // figure out whether 2D or 3D matrix
    //scale(xyz, xyz, xyz); 
  }


  public void scale(float sx, float sy) {
    if (dimensions == 0) dimensions = 2;
    applyMatrix(sx, 0, 0, 0,  0, sy, 0, 0,  0, 0, 1, 0,  0, 0, 0, 1);    
  }


  // OPTIMIZE: same as above
  public void scale(float x, float y, float z) {
    //modelMatrixIsIdentity = false;
    dimensions = 3;
    applyMatrix(x, 0, 0, 0,  0, y, 0, 0,  0, 0, z, 0,  0, 0, 0, 1);
  }


  public void transform(float n00, float n01, float n02, float n03,
                        float n10, float n11, float n12, float n13,
                        float n20, float n21, float n22, float n23,
                        float n30, float n31, float n32, float n33) {
    dimensions = 3;
    applyMatrix(n00, n01, n02, n03,  n10, n11, n12, n13,
                n20, n21, n22, n23,  n30, n31, n32, n33);
  }


  //////////////////////////////////////////////////////////////

  // COLOR


  public void colorMode(int icolorMode) {
    color_mode = icolorMode;
  }


  public void colorMode(int icolorMode, float max) {
    colorMode(icolorMode, max, max, max, max);
  }


  // note that this doesn't set the alpha color max.. 
  // so colorMode(RGB, 255, 255, 255) would retain the previous max alpha
  // could be a problem when colorMode(HSB, 360, 100, 100); 

  public void colorMode(int icolorMode, 
                        float maxX, float maxY, float maxZ) {
    colorMode(icolorMode, maxX, maxY, maxZ, colorMaxA); //maxX); //ONE);
  }


  public void colorMode(int icolorMode, 
                        float maxX, float maxY, float maxZ, float maxA) {
    color_mode = icolorMode;

    colorMaxX = maxX;  // still needs to be set for hsb
    colorMaxY = maxY;
    colorMaxZ = maxZ;
    colorMaxA = maxA;

    // if color max values are all 1, then no need to scale
    color_scale = ((maxA != ONE) || (maxX != maxY) || 
                   (maxY != maxZ) || (maxZ != maxA));
  }


  //////////////////////////////////////////////////////////////


  protected void calc_color(float gray) {
    calc_color(gray, colorMaxA);
  }


  protected void calc_color(float gray, float alpha) {
    if (gray > colorMaxX) gray = colorMaxX;
    if (alpha > colorMaxA) alpha = colorMaxA;

    if (gray < 0) gray = 0;
    if (alpha < 0) alpha = 0;

    calcR = color_scale ? (gray / colorMaxX) : gray;
    calcG = calcR; 
    calcB = calcR;
    calcA = color_scale ? (alpha / colorMaxA) : alpha;

    calcRi = (int)(calcR*255); calcGi = (int)(calcG*255);
    calcBi = (int)(calcB*255); calcAi = (int)(calcA*255);
    calci = (calcAi << 24) | (calcRi << 16) | (calcGi << 8) | calcBi;
    calc_alpha = (calcAi != 255);    
  }


  protected void calc_color(float x, float y, float z) {
    calc_color(x, y, z, colorMaxA);
  }


  protected void calc_color(float x, float y, float z, float a) {
    if (x > colorMaxX) x = colorMaxX;
    if (y > colorMaxY) y = colorMaxY;
    if (z > colorMaxZ) z = colorMaxZ;
    if (a > colorMaxA) a = colorMaxA;

    if (x < 0) x = 0;
    if (y < 0) y = 0;
    if (z < 0) z = 0;
    if (a < 0) a = 0;

    switch (color_mode) {
    case RGB:
      if (color_scale) {
        calcR = x / colorMaxX;
        calcG = y / colorMaxY;
        calcB = z / colorMaxZ;
        calcA = a / colorMaxA;
      } else {
        calcR = x; calcG = y; calcB = z; calcA = a;
      }
      break;

    case HSB:
      x /= colorMaxX; // h
      y /= colorMaxY; // s
      z /= colorMaxZ; // b

      calcA = color_scale ? (a/colorMaxA) : a;

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
    calci = (calcAi << 24) | (calcRi << 16) | (calcGi << 8) | calcBi;
    calc_alpha = (calcAi != 255);
  }


  /**
   * unpacks AARRGGBB color for direct use with calc_color.
   * handled here with its own function since this is independent 
   * of the color mode.
   * 
   * strangely the old version of this code ignored the alpha 
   * value. not sure if that was a bug or what.
   *
   * (note: no need for bounds check since it's a 32 bit number)
   */
  protected void unpack_for_calc(int rgb) {
    calci = rgb;
    calcAi = (rgb >> 24) & 0xff;
    calcRi = (rgb >> 16) & 0xff;
    calcGi = (rgb >> 8) & 0xff;
    calcBi = rgb & 0xff;
    calcA = (float)calcAi / 255.0f;
    calcR = (float)calcRi / 255.0f;
    calcG = (float)calcGi / 255.0f;
    calcB = (float)calcBi / 255.0f;
    calc_alpha = (calcAi != 255);

    /*
    calci = rgb;
    calcRi = (rgb >> 16) & 0xff;
    calcGi = (rgb >> 8) & 0xff;
    calcBi = rgb & 0xff;
    calcAi = 255;
    calcR = (float)calcRi / 255.0f;
    calcG = (float)calcGi / 255.0f;
    calcB = (float)calcBi / 255.0f;
    calcA = 1;
    calc_alpha = false;
    */
  }


  protected void calc_tint() {
    _tint = true;
    tintR = calcR; 
    tintG = calcG; 
    tintB = calcB; 
    tintA = calcA;
    tintRi = calcRi; 
    tintGi = calcGi; 
    tintBi = calcBi; 
    tintAi = calcAi;
    tint = calci; 
    tint_alpha = calc_alpha;
  }


  protected void calc_fill() {
    _fill = true;
    fillChanged = true;
    fillR = calcR; 
    fillG = calcG; 
    fillB = calcB; 
    fillA = calcA;
    fillRi = calcRi; 
    fillGi = calcGi; 
    fillBi = calcBi; 
    fillAi = calcAi;
    fill = calci; 
    fill_alpha = calc_alpha;
  }


  protected void calc_stroke() {
    _stroke = true;
    strokeChanged = true;
    strokeR = calcR; 
    strokeG = calcG; 
    strokeB = calcB; 
    strokeA = calcA;
    strokeRi = calcRi; 
    strokeGi = calcGi; 
    strokeBi = calcBi; 
    strokeAi = calcAi;
    stroke = calci; 
    stroke_alpha = calc_alpha;
  }


  protected void calc_background() {
    _background = true;
    backR = calcR; 
    backG = calcG; 
    backB = calcB; 
    backRi = calcRi; 
    backGi = calcGi; 
    backBi = calcBi; 
    background = calci; 
  }


  //////////////////////////////////////////////////////////////


  public void noTint() {
    _tint = false;
  }


  // if high bit isn't set, then it's not a #ffcc00 style web color
  // so redirect to the float version, b/c they want a gray.
  // only danger is that someone would try to set the color to a
  // zero alpha.. which would be kooky but not unlikely
  // (i.e. if it were in a loop) so in addition to checking the high
  // bit, check to see if the value is at least just below the 
  // colorMaxX (i.e. 0..255). can't just check the latter since
  // if the high bit is > 0x80 then the int value for rgb will be
  // negative. yay for no unsigned types in java!

  public void tint(int rgb) {
    if (((rgb & 0xff000000) == 0) && (rgb <= colorMaxX)) {
      tint((float) rgb);

    } else {
      unpack_for_calc(rgb);
      calc_tint();
    }
  }

  public void tint(float gray) {
    calc_color(gray);
    calc_tint();
  }


  public void tint(float gray, float alpha) {
    calc_color(gray, alpha);
    calc_tint();
  }


  public void tint(float x, float y, float z) {
    calc_color(x, y, z);
    calc_tint();
  }


  public void tint(float x, float y, float z, float a) {
    calc_color(x, y, z, a);
    calc_tint();
  }


  //////////////////////////////////////////////////////////////


  public void noFill() {
    _fill = false;
  }


  public void fill(int rgb) {
    if (((rgb & 0xff000000) == 0) && (rgb <= colorMaxX)) {  // see above
      fill((float) rgb);

    } else {
      unpack_for_calc(rgb);
      calc_fill();
    }
  }

  public void fill(float gray) {
    calc_color(gray);
    calc_fill();
  }


  public void fill(float gray, float alpha) {
    calc_color(gray, alpha);
    calc_fill();
  }


  public void fill(float x, float y, float z) {
    calc_color(x, y, z);
    calc_fill();
  }


  public void fill(float x, float y, float z, float a) {
    calc_color(x, y, z, a);
    calc_fill();
  }


  //////////////////////////////////////////////////////////////


  public void strokeWeight(float weight) {
    strokeWeight = weight;
  }


  public void strokeJoin(int join) {
    strokeJoin = join;
  }


  public void strokeMiter(int miter) {
    strokeMiter = miter;
  }


  public void noStroke() {
    _stroke = false;
  }


  public void stroke(int rgb) {
    if (((rgb & 0xff000000) == 0) && (rgb <= colorMaxX)) {  // see above
      stroke((float) rgb);

    } else {
      unpack_for_calc(rgb);
      calc_stroke();
    }
  }


  public void stroke(float gray) {
    //System.out.println("stroke " + gray);
    calc_color(gray);
    calc_stroke();
  }


  public void stroke(float gray, float alpha) {
    calc_color(gray, alpha);
    calc_stroke();
  }


  public void stroke(float x, float y, float z) {
    calc_color(x, y, z);
    calc_stroke();
  }


  public void stroke(float x, float y, float z, float a) {
    calc_color(x, y, z, a);
    calc_stroke();
  }


  //////////////////////////////////////////////////////////////


  public void background(int rgb) {
    if (((rgb & 0xff000000) == 0) && (rgb <= colorMaxX)) {  // see above
      background((float) rgb);

    } else {
      unpack_for_calc(rgb);
      calc_background();
    }
    clear();
  }


  public void background(float gray) {
    calc_color(gray);
    calc_background();
    clear();
  }


  public void background(float x, float y, float z) {
    calc_color(x, y, z);
    calc_background();
    clear();
  }


  public void background(PImage image) {
    if ((image.width != width) || (image.height != height)) {
      System.err.println("background image must be the same size " + 
                         "as your application");
      return;
    }
    if ((image.format != RGB) && (image.format != RGBA)) {
      System.err.println("background images should be RGB or RGBA");
      return;
    }

    // blit image to the screen
    System.arraycopy(image.pixels, 0, pixels, 0, pixels.length);

    // clear the zbuffer
    //if (dimensions == 3) {
    for (int i = 0; i < pixelCount; i++) {
      zbuffer[i] = MAX_FLOAT;
    }
    //}
  }


  /**
   * Clears pixel and z-buffer. If dimensions not set to 3, 
   * then the zbuffer doesn't get cleared. Some values might be
   * zero, but since it's <= when comparing zbuffer (because of
   * 2D drawing) that's no problem. Saves a lotta time to not 
   * reset the zbuffer if it's not used.
   *
   * Actually had to punt on that optimization because it was
   * producing weird results and it was close to a release deadline.
   * A little sleep would probably show why it wasn't working.
   */
  public void clear() {
    //zbufferTainted = true;

    //if (dimensions == 3) {
    //if (zbufferTainted) {
      //System.out.println("clearing zbuffer");
    for (int i = 0; i < pixelCount; i++) {
      pixels[i] = background;
      zbuffer[i] = MAX_FLOAT;
    }
    //zbufferTainted = false;
    //} else {
    //System.out.println("not clearing zbuffer");
    //for (int i = 0; i < pixelCount; i++) {
    //  pixels[i] = background;
    //}
    //}
  }


  //////////////////////////////////////////////////////////////

  // LIGHTS


  public void lights() {
    lighting = true;
  }

  public void noLights() {
    lighting = false;
  }


  //////////////////////////////////////////////////////////////

  // SMOOTHING (ANTIALIASING)


  public void smooth() {
    smooth = true;
  }

  public void noSmooth() {
    smooth = false;
  }


  //////////////////////////////////////////////////////////////

  // HINTS

  // for the most part, hints are temporary api quirks, 
  // for which a proper api hasn't been properly worked out.
  // for instance SMOOTH_IMAGES existed because smooth()
  // wasn't yet implemented, but it will soon go away.

  // they also exist for obscure features in the graphics
  // engine, like enabling/disabling single pixel lines
  // that ignore the zbuffer, the way they do in alphabot.

  public void hint(int which) {
    hints[which] = true;
  }

  public void unhint(int which) {
    hints[which] = false;
  }


  //////////////////////////////////////////////////////////////

  // MESSAGES / ERRORS / LOGGING


  public void message(int level, String message) {
    switch (level) {
    case CHATTER:
      //System.err.println("bagel chatter: " + message);
      break;
    case COMPLAINT:
      System.err.println("bagel complaint: " + message);
      break;
    case PROBLEM:
      System.err.println("bagel problem: " + message);
      break;
    }
  }

  public void message(int level, String message, Exception e) {
    message(level, message);
    e.printStackTrace();
  }


  //////////////////////////////////////////////////////////////

  // FILE I/O


  public InputStream openStream(String filename) throws IOException {
    InputStream stream = null;
    boolean gz = filename.toLowerCase().endsWith(".gz");

    if (filename.startsWith("http://")) {
      try {
        URL url = new URL(filename);
        stream = url.openStream();
        return gz ? new GZIPInputStream(stream) : stream;

      } catch (MalformedURLException e) { 
        e.printStackTrace();
        return null;
      }
    }

    try {
      stream = getClass().getResourceAsStream(filename);
      if (stream != null) return gz ? new GZIPInputStream(stream) : stream;
    } catch (IOException e) { }

    try {
      stream = getClass().getResourceAsStream("data/" + filename);
      if (stream != null) return gz ? new GZIPInputStream(stream) : stream;
    } catch (IOException e) { }

    try {
      try {
        stream = new FileInputStream(new File("data", filename));
        if (stream != null) return gz ? new GZIPInputStream(stream) : stream;
      } catch (IOException e2) { }

      try {
        stream = new FileInputStream(filename);
        if (stream != null) return gz ? new GZIPInputStream(stream) : stream;
      } catch (IOException e1) { }

    } catch (SecurityException se) { }  // online, whups

    if (stream == null) {
      throw new IOException("openStream() could not open " + filename);
    }
    return null;  // #$(*@ compiler
  }


  public byte[] loadBytes(String filename) {
    try {
      return loadBytes(openStream(filename));

    } catch (IOException e) {
      System.err.println("problem loading bytes from " + filename);
      e.printStackTrace();
    }
    return null;
  }

  static public byte[] loadBytes(InputStream input) {
    try {
      BufferedInputStream bis = new BufferedInputStream(input);
      ByteArrayOutputStream out = new ByteArrayOutputStream();

      int c = bis.read();
      while (c != -1) {
        out.write(c);
        c = bis.read();
      }
      return out.toByteArray();

    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }


  public String[] loadStrings(String filename) {
    try {
      return loadStrings(openStream(filename));

    } catch (IOException e) {
      System.err.println("problem loading strings from " + filename);
      e.printStackTrace();
    }
    return null;
  }

  static public String[] loadStrings(InputStream input) {
    try {
      BufferedReader reader = 
        new BufferedReader(new InputStreamReader(input));

      String lines[] = new String[100];
      int lineCount = 0;
      String line = null;
      while ((line = reader.readLine()) != null) {
        if (lineCount == lines.length) {
          String temp[] = new String[lineCount << 1];
          System.arraycopy(lines, 0, temp, 0, lineCount);
          lines = temp;
        }
        lines[lineCount++] = line;
      }
      reader.close();

      if (lineCount == lines.length) {
        return lines;
      }

      // resize array to appropraite amount for these lines
      String output[] = new String[lineCount];
      System.arraycopy(lines, 0, output, 0, lineCount);
      return output;

    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }


  public void saveBytes(String filename, byte buffer[]) {
    try {
      FileOutputStream fos = new FileOutputStream(filename);
      saveBytes(fos, buffer);
      fos.close();

    } catch (IOException e) {
      System.err.println("error saving bytes to " + filename);
      e.printStackTrace();
    }
  }

  public void saveBytes(OutputStream output, byte buffer[]) {
    try {
      //BufferedOutputStream bos = new BufferedOutputStream(output);
      output.write(buffer);
      output.flush();

    } catch (IOException e) {
      System.err.println("error while saving bytes");
      e.printStackTrace();
    }
  }


  public void saveStrings(String filename, String strings[]) {
    try {
      FileOutputStream fos = new FileOutputStream(filename);
      saveStrings(fos, strings);
      fos.close();

    } catch (IOException e) {
      System.err.println("error while saving strings");
      e.printStackTrace();
    }
  }

  public void saveStrings(OutputStream output, String strings[]) {
    //try {
    PrintWriter writer = 
      new PrintWriter(new OutputStreamWriter(output));
    for (int i = 0; i < strings.length; i++) {
      writer.println(strings[i]);
    }
    writer.flush();
    //} catch (IOException e) {
    //System.err.println("error while saving strings");
    //e.printStackTrace();
    //}
  }


  //////////////////////////////////////////////////////////////

  // SORT

  int sort_mode;

  static final int STRINGS = 0;
  static final int INTS = 1;
  static final int FLOATS = 2;
  static final int DOUBLES = 3;

  String sort_strings[];
  int sort_ints[];
  float sort_floats[];
  double sort_doubles[];
  Object sort_objects[];


  /**
   * Sort an array of String objects.
   */
  public void sort(String what[]) {
    sort(what, what.length, null);
  }

  /**
   * Sort an array of String objects, along with a generic
   * array of type Object. 
   *
   * String names[] = { "orange", "black", "red" };
   * Object colors[] = { Color.orange, Color.black, Color.red };
   * sort(names, colors);
   * 
   * result is 'names' alphabetically sorted
   * and the colors[] array sorted along with it.
   */
  public void sort(String what[], Object objects[]) {
    sort(what, what.length, objects);
  }

  public void sort(int what[]) {
    sort(what, what.length, null);
  }

  public void sort(int what[], Object objects[]) {
    sort(what, what.length, objects);
  }

  public void sort(float what[]) {
    sort(what, what.length, null);
  }

  public void sort(float what[], Object objects[]) {
    sort(what, what.length, objects);
  }

  public void sort(double what[]) {
    sort(what, what.length, null);
  }

  public void sort(double what[], Object objects[]) {
    sort(what, what.length, objects);
  }


  public void sort(String what[], int count, Object objects[]) {
    if (count == 0) return;
    sort_mode = STRINGS;
    sort_strings = what;
    sort_objects = objects;
    sort_internal(0, count-1);
  }

  public void sort(int what[], int count, Object objects[]) {
    if (count == 0) return;
    sort_mode = INTS;
    sort_ints = what;
    sort_objects = objects;
    sort_internal(0, count-1);
  }

  public void sort(float what[], int count, Object objects[]) {
    if (count == 0) return;
    sort_mode = FLOATS;
    sort_floats = what;
    sort_objects = objects;
    sort_internal(0, count-1);
  }

  public void sort(double what[], int count, Object objects[]) {
    if (count == 0) return;
    sort_mode = DOUBLES;
    sort_doubles = what;
    sort_objects = objects;
    sort_internal(0, count-1);
  }


  protected void sort_internal(int i, int j) {
    int pivotIndex = (i+j)/2;
    sort_swap(pivotIndex, j);
    int k = sort_partition(i-1, j);
    sort_swap(k, j);
    if ((k-i) > 1) sort_internal(i, k-1);
    if ((j-k) > 1) sort_internal(k+1, j);
  }


  protected int sort_partition(int left, int right) {
    int pivot = right;
    do {
      while (sort_compare(++left, pivot) < 0) { }
      while ((right != 0) && (sort_compare(--right, pivot) > 0)) { }
      sort_swap(left, right);
    } while (left < right);
    sort_swap(left, right);
    return left;
  }


  protected void sort_swap(int a, int b) {
    switch (sort_mode) {
    case STRINGS:
      String stemp = sort_strings[a];
      sort_strings[a] = sort_strings[b];
      sort_strings[b] = stemp;
      break;
    case INTS:
      int itemp = sort_ints[a];
      sort_ints[a] = sort_ints[b];
      sort_ints[b] = itemp;
      break;
    case FLOATS:
      float ftemp = sort_floats[a];
      sort_floats[a] = sort_floats[b];
      sort_floats[b] = ftemp;
      break;
    case DOUBLES:
      double dtemp = sort_doubles[a];
      sort_doubles[a] = sort_doubles[b];
      sort_doubles[b] = dtemp;
      break;
    }
    if (sort_objects != null) {
      Object otemp = sort_objects[a];
      sort_objects[a] = sort_objects[b];
      sort_objects[b] = otemp;
    }
  }

  protected int sort_compare(int a, int b) {
    switch (sort_mode) {
    case STRINGS:    
      return sort_strings[a].compareTo(sort_strings[b]);
    case INTS:
      if (sort_ints[a] < sort_ints[b]) return -1;
      return (sort_ints[a] == sort_ints[b]) ? 0 : 1;
    case FLOATS:
      if (sort_floats[a] < sort_floats[b]) return -1;
      return (sort_floats[a] == sort_floats[b]) ? 0 : 1;
    case DOUBLES:
      if (sort_doubles[a] < sort_doubles[b]) return -1;
      return (sort_doubles[a] == sort_doubles[b]) ? 0 : 1;
    }
    return 0;
  }


  //////////////////////////////////////////////////////////////

  // PIXELS

  // these functions are really slow, but easy to use
  // if folks are advanced enough to want something faster, 
  // they can write it themselves (not difficult)


  public final int color(int x, int y, int z) {
    if ((color_mode == RGB) && (!color_scale)) {
      return 0xff000000 | (x << 16) | (y << 8) | z;
    }
    calc_color(x, y, z);
    return calci;
  }

  public final int color(float x, float y, float z) {
    calc_color(x, y, z);
    return calci;
  }


  public final int color(int x, int y, int z, int a) {
    if ((color_mode == RGB) && (!color_scale)) {
      return (a << 24) | (x << 16) | (y << 8) | z;
    } 
    calc_color(x, y, z, a);
    return calci;
  }

  public final int color(float x, float y, float z, float a) {
    calc_color(x, y, z, a);
    return calci;
  }


  public final float alpha(int what) {
    float c = (what >> 24) & 0xff;
    if (colorMaxA == 255) return c;
    return (c / 255.0f) * colorMaxA;
  }

  public final float red(int what) {
    float c = (what >> 16) & 0xff;
    if ((color_mode == RGB) && (!color_scale)) return c;
    return (c / 255.0f) * colorMaxX;
  }

  public final float green(int what) {
    float c = (what >> 8) & 0xff;
    if ((color_mode == RGB) && (!color_scale)) return c;
    return (c / 255.0f) * colorMaxY;
  }

  public final float blue(int what) {
    float c = (what) & 0xff;
    if ((color_mode == RGB) && (!color_scale)) return c;
    return (c / 255.0f) * colorMaxZ;
  }


  public final float hue(int what) {
    if (what != cacheHsbKey) {
      Color.RGBtoHSB((what >> 16) & 0xff, (what >> 8) & 0xff, 
                     what & 0xff, cacheHsbValue);
      cacheHsbKey = what;
    }
    return cacheHsbValue[0] * colorMaxX;
  }

  public final float saturation(int what) {
    if (what != cacheHsbKey) {
      Color.RGBtoHSB((what >> 16) & 0xff, (what >> 8) & 0xff, 
                     what & 0xff, cacheHsbValue);
      cacheHsbKey = what;
    }
    return cacheHsbValue[1] * colorMaxY;
  }

  public final float brightness(int what) {
    if (what != cacheHsbKey) {
      Color.RGBtoHSB((what >> 16) & 0xff, (what >> 8) & 0xff, 
                     what & 0xff, cacheHsbValue);
      cacheHsbKey = what;
    }
    return cacheHsbValue[2] * colorMaxZ;
  }


  // should only be used by other parts of the bagel library


  static private final int float_color(float r, float g, float b) {
    return (0xff000000 | 
            ((int) (255.0f * r)) << 16 |
            ((int) (255.0f * g)) << 8 |
            ((int) (255.0f * b)));
  }


  public final static int _blend(int p1, int p2, int a2) {
    // scale alpha by alpha of incoming pixel
    a2 = (a2 * (p2 >>> 24)) >> 8;

    int a1 = a2 ^ 0xff;
    int r = (a1 * ((p1 >> 16) & 0xff) + a2 * ((p2 >> 16) & 0xff)) & 0xff00;
    int g = (a1 * ((p1 >>  8) & 0xff) + a2 * ((p2 >>  8) & 0xff)) & 0xff00;
    int b = (a1 * ( p1        & 0xff) + a2 * ( p2        & 0xff)) >> 8;

    return 0xff000000 | (r << 8) | g | b;
  }


  //////////////////////////////////////////////////////////////

  // MATH

  // these are *only* the functions used internally
  // the real math functions are inside PApplet

  // these have been made private so as not to conflict
  // with the versions found in PApplet when fxn importing happens
  // also might be faster that way. hmm. 


  private final float mag(float a, float b) {
    return (float)Math.sqrt(a*a + b*b);
  }

  private final float mag(float a, float b, float c) {
    return (float)Math.sqrt(a*a + b*b + c*c);
  }

  private final float max(float a, float b) {
    return (a > b) ? a : b;
  }

  private final float sq(float a) {
    return a*a;
  }

  private final float sqrt(float a) {
    return (float)Math.sqrt(a);
  }


  private final float abs(float a) {
    return (a < 0) ? -a : a;
  }


  private final float sin(float angle) {
    return (float)Math.sin(angle);
  }

  private final float cos(float angle) {
    return (float)Math.cos(angle);
  }

  private final float tan(float angle) {
    return (float)Math.tan(angle);
  }
}

