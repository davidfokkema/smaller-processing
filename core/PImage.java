/* -*- mode: jde; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  BImage - storage class for pixel data
  Part of the Processing project - http://Proce55ing.net

  Copyright (c) 2001-03 
  Ben Fry, Massachusetts Institute of Technology and 
  Casey Reas, Interaction Design Institute Ivrea

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

import java.awt.*;
import java.awt.image.*;
import java.io.*;


/**
 * [toxi 030722]
 * advanced copying/blitting code 
 *
 * [fry 030918]
 * integrated and modified to fit p5 spec 
 *
 * [toxi 030930]
 * - target pixel buffer doesn't loose alpha channel anymore
 *   with every blitting operation alpha values are increased now
 * - resizing by large factors (>250%) doesn't yield any rounding errors
 *   anymore, changed to 16bit precision (=65536% max or 0.000015% min)
 * - replicate() is now only using REPLACE mode to avoid semantic problems
 * - added blend() methods to use replicate()'s functionality, 
 *   but with blend modes
 *
 * [toxi 031006]
 * blit_resize() is now clipping input coordinates to avoid array 
 * exceptions target dimension can be larger than destination image 
 * object, outside pixels will be skipped
 *
 * [toxi 031017]
 * versions of replicate() and blend() methods which use cross-image 
 * blitting are now called in the destination image and expect a source 
 * image object as parameter. this is to provide an easy syntax for cases 
 * where the main pixel buffer is the destination. as those methods are 
 * overloaded in BApplet, users can call those functions directly without
 * explicitly giving a reference to BGraphics.
 */
public class BImage implements BConstants, Cloneable {

  // note that RGB images still require 0xff in the high byte
  // because of how they'll be manipulated by other functions
  int format;

  int pixels[];
  int width, height;
  // maybe also scan line, etc?

  // note! inherited by BGraphics
  boolean smooth = false; //true;  // for now.. how to fix?

  // for gl subclass / hardware accel
  int cacheIndex;

  // blend mode used for copy et al.
  //int blend_mode = REPLACE;


  // private fields

  private int fracU, ifU, fracV, ifV, u1, u2, v1, v2, sX, sY, iw, iw1, ih1;
  private int ul, ll, ur, lr, cUL, cLL, cUR, cLR;
  private int srcXOffset, srcYOffset;
  private int r, g, b, a;
  private int[] srcBuffer;

  // fixed point precision is limited to 15 bits!!
  static final int PRECISIONB = 15;
  static final int PRECISIONF = 1 << PRECISIONB;
  static final int PREC_MAXVAL = PRECISIONF-1;
  static final int PREC_ALPHA_SHIFT = 24-PRECISIONB;
  static final int PREC_RED_SHIFT = 16-PRECISIONB;
  
  
  /** 
   * Constructor required by java compiler for subclasses.
   */
  public BImage() { }


  /**
   * Create a new (transparent) image of a specific size.
   * All pixels are set to zero, meaning black, but since the
   * alpha is zero, it will be transparent.
   */
  public BImage(int width, int height) {
    this(new int[width * height], width, height, RGBA);
    // toxi: is it maybe better to init the image with max alpha enabled?
    //for(int i=0; i<pixels.length; i++) pixels[i]=0xffffffff;
    // fry: i'm opting for the full transparent image, which is how
    // photoshop works, and our audience oughta be familiar with.
    // also, i want to avoid having to set all those pixels since 
    // in java it's super slow, and most using this fxn will be 
    // setting all the pixels anyway.
    // toxi: agreed and same reasons why i left it out ;)
  }


  public BImage(int pixels[], int width, int height, int format) {
    this.pixels = pixels;
    this.width = width;
    this.height = height;
    this.format = format;
    this.cacheIndex = -1;
  }


  /** 
   * Construct a new BImage from a java.awt.Image
   *
   * this constructor assumes that you've done the work of
   * making sure a MediaTracker has been used to fully
   * download the data and that the img is valid.
   */
  public BImage(java.awt.Image img) {
    width = img.getWidth(null);
    height = img.getHeight(null);

    pixels = new int[width*height];
    PixelGrabber pg = 
      new PixelGrabber(img, 0, 0, width, height, pixels, 0, width);
    try {
      pg.grabPixels();
    } catch (InterruptedException e) { }

    format = RGB;
    cacheIndex = -1;
  }


  /**
   * Set alpha channel for an image.
   */
  public void alpha(int alpha[]) {
    // don't execute if mask image is different size
    if (alpha.length != pixels.length) {
      System.err.println("alpha(): the alpha mask image must be the same size");
      return;
    }
    for (int i = 0; i < pixels.length; i++) {
      pixels[i] = pixels[i] & 0xffffff | ((alpha[i] & 0xff) << 24);
    }
    /*
    if (highbits) {  // grab alpha from the high 8 bits (RGBA style)
      for (int i = 0; i < pixels.length; i++) {
        pixels[i] = pixels[i] & 0xffffff | (alpha[i] & 0xff000000);
      }
    } else {  // alpha is in the low bits (ALPHA style)
      for (int i = 0; i < pixels.length; i++) {
        pixels[i] = pixels[i] & 0xffffff | ((alpha[i] & 0xff) << 24);
      }
    }
    */
    format=RGBA;
  }


  /**
   * Set alpha channel for an image using another image as the source.
   */
  public void alpha(BImage alpha) {
    alpha(alpha.pixels);
  }


  public int blendColor(int c1, int c2, int mode) {
    switch (mode) {
    case BLEND:    return blend_multiply(c1, c2);
    case ADD:      return blend_add_pin(c1, c2);
    case SUBTRACT: return blend_sub_pin(c1, c2);
    case LIGHTEST: return blend_lightest(c1, c2);
    case DARKEST:  return blend_darkest(c1, c2);
    case REPLACE:  return c2;
    }
    return 0;
  }

   // [toxi040115] converts RGB image data into grayscale using weighted RGB components
   // keeps alpha channel intact
   public void toGrayscale() {
        int col,lum,i;
        for(i=0; i<pixels.length; i++) {
            col=pixels[i];
            // luminance = 0.3*red + 0.59*green + 0.11*blue
            // 0.3*256 = 77
            // 0.59*256 = 151
            // 0.11*256 = 28
            lum = ( 77*(col>>16&0xff) + 151*(col>>8&0xff) + 28*(col&0xff) )>>8;
            pixels[i] = (col & ALPHA_MASK) | lum<<16 | lum<<8 | lum;
        }
    }

  //////////////////////////////////////////////////////////////

  // GETTING PIXELS


  public int get(int x, int y) {
    if ((x < 0) || (y < 0) || (x >= width) || (y >= height)) return 0;
    return pixels[y*width + x];
  }


  public BImage get(int x, int y, int w, int h) {
    if (x < 0) x = 0;
    if (y < 0) y = 0;

    if (x + w > width) w = width - x;
    if (y + h > height) h = height - y;

    BImage newbie = new BImage(new int[w*h], w, h, format);

    int index = y*width + x;
    int index2 = 0;
    for (int row = y; row < y+h; row++) {
      System.arraycopy(pixels, index, 
                       newbie.pixels, index2, w);
      index+=width;
      index2+=w;
    }
    return newbie;
  }



  //////////////////////////////////////////////////////////////

  // SETTING PIXELS


  public void set(int x, int y, int c) {
    if ((x < 0) || (y < 0) || (x >= width) || (y >= height)) return;
    pixels[y*width + x] = c;
  }


  // not all variables here are needed.. fix it up later

  public void set(int x, int y, BImage image) {
    // source
    int sx = 0; 
    int sy = 0;
    int sw = image.width; 
    int sh = image.height;

    // target
    int tx = x; // < 0 ? 0 : x;
    int ty = y; // < 0 ? 0 : y;
    int tw = image.width;
    int th = image.height;

    if (tx < 0) {  // say if target x were -3
      sx -= tx;    // source x -(-3) (or add 3)
      sw += tx;    // source width -3
      tw += tx;    // target width -3
      tx = 0;      // target x is zero (upper corner)
    }
    if (ty < 0) {
      sy -= ty;
      sh += ty;
      th += ty;
      ty = 0;
    }
    if (tx + tw > width) {
      int extra = (tx + tw) - width; 
      sw -= extra;
      tw -= extra;
    }
    if (ty + th > height) {
      int extra = (ty + th) - height;
      sh -= extra;
      sw -= extra;
    }

    for (int row = sy; row < sy + sh; row++) {
      System.arraycopy(image.pixels, row*image.width + sx, 
                       pixels, (y+row)*width + tx, sw);
    }
  }



  //////////////////////////////////////////////////////////////
  
  // REPLICATING & BLENDING (AREAS) OF PIXELS


  // copies a pixel from place to another (in the same image)
  // this function is excluded from using any blend modes
  // it always replaces/overwrites the target pixel value

  // should this copy the zbuffer and stencil for this pixel?

  public void copy(int sx, int sy, int dx, int dy) {
    if ((sx >= 0) && (sx < width) && (dx >= 0) && (dx < width) &&
        (sy >= 0) && (sy < height) && (dy >= 0) && (dy < height)) {
      pixels[dy * width + dx] = pixels[sy * width + sx];
    }
  }


  // copies a pixel from place to another (in different images)
  // this function is excluded from using any blend modes
  // it always replaces/overwrites the target pixel value

  public void copy(BImage src, int sx, int sy, int dx, int dy) {
    if ((dx >= 0) && (dx < width) && (sx >= 0) && (sx < src.width) &&
        (dy >= 0) && (dy < height) && (sy >= 0) && (sy < src.height)) {
            pixels[dy * width + dx] = src.pixels[sy * src.width + sx];
    }
  }


  /**
   * Copy things from one area of this image to another area in the same image
   */
  public void copy(int sx1, int sy1, int sx2, int sy2, 
                        int dx1, int dy1, int dx2, int dy2) {
    if (intersect(sx1, sy1, sx2, sy2, dx1, dy1, dx2, dy2)) {
      blit_resize(this.get(sx1, sy1, sx2 - sx1, sy2 - sy1), 
                  0, 0, sx2 - sx1 - 1, sy2 - sy1 - 1,
                  pixels, width, height, dx1, dy1, dx2, dy2, REPLACE);
    } else {
      blit_resize(this, sx1, sy1, sx2, sy2,
                  pixels, width, height, dx1, dy1, dx2, dy2, REPLACE);
    }
  }


  /**
   * Copies area of one image into another BImage object
   */
  public void copy(BImage src, int sx1, int sy1, int sx2, int sy2,
                        int dx1, int dy1, int dx2, int dy2) {
    blit_resize(src, sx1, sy1, sx2, sy2,
                 pixels, width, height, dx1, dy1, dx2, dy2, REPLACE);
  }


  /** 
   * Copies and blends 1 pixel with MODE to pixel in another image
   */
  public void blend(BImage src, int sx, int sy, int dx, int dy, int mode) {
    if ((dx >= 0) && (dx < width) && (sx >= 0) && (sx < src.width) &&
        (dy >= 0) && (dy < height) && (sy >= 0) && (sy < src.height)) {
      pixels[dy * width + dx] = 
        blendColor(pixels[dy * width + dx], src.pixels[sy * src.width + sx], mode);
    }
  }

  public void blend(int sx, int sy, int dx, int dy, int mode) {
    if ((dx >= 0) && (dx < width) && (sx >= 0) && (sx < width) &&
        (dy >= 0) && (dy < height) && (sy >= 0) && (sy < height)) {
      pixels[dy * width + dx] = 
        blendColor(pixels[dy * width + dx], pixels[sy * width + sx], mode);
    }
  }
  
  /**
   * Copy things from one area of this image to another area
   */
  public void blend(int sx1, int sy1, int sx2, int sy2, 
                    int dx1, int dy1, int dx2, int dy2, int mode) {
    if (intersect(sx1, sy1, sx2, sy2, dx1, dy1, dx2, dy2)) {
      blit_resize(this.get(sx1, sy1, sx2 - sx1, sy2 - sy1), 
                  0, 0, sx2 - sx1 - 1, sy2 - sy1 - 1,
                  pixels, width, height, dx1, dy1, dx2, dy2, mode);
    } else {
      blit_resize(this, sx1, sy1, sx2, sy2,
                  pixels, width, height, dx1, dy1, dx2, dy2, mode);
    }
  }


  /**
   * Copies area of one image into another BImage object
   */
  public void blend(BImage src, int sx1, int sy1, int sx2, int sy2, 
                    int dx1, int dy1, int dx2, int dy2, int mode) {
    blit_resize(src, sx1, sy1, sx2, sy2,
                pixels, width, height, dx1, dy1, dx2, dy2, mode);
  }



  //////////////////////////////////////////////////////////////
  
  // COPYING IMAGE DATA

  public Object clone() throws CloneNotSupportedException {
    BImage c = (BImage) super.clone();

    // super.clone() will only copy the reference to the pixels
    // array, so this will do a proper duplication of it instead.
    c.pixels = new int[width * height];
    System.arraycopy(pixels, 0, c.pixels, 0, pixels.length);

    // return the goods
    return c;
  }


  /**
   * Duplicate an image, returns new object
   */
  /*
  public BImage duplicate() {
    BImage c = new BImage(new int[pixels.length], width, height, format);
    System.arraycopy(pixels, 0, c.pixels, 0, pixels.length);
    return c;
  }
  */

  /**
   * Duplicate and resize image
   */
  /*
  public BImage duplicate(int newWidth, int newHeight) {
    BImage dupe = new BImage(new int[newWidth * newHeight], 
                             newWidth, newHeight, format);

    dupe.copy(this, 0, 0, width - 1, height - 1,
              0, 0, newWidth - 1, newHeight - 1);

    return dupe;
  }
  */

  /**
   * Check to see if two rectangles intersect one another
   */
  boolean intersect(int sx1, int sy1, int sx2, int sy2, 
                    int dx1, int dy1, int dx2, int dy2) {
    int sw = sx2 - sx1 + 1;
    int sh = sy2 - sy1 + 1;
    int dw = dx2 - dx1 + 1;
    int dh = dy2 - dy1 + 1;

    if (dx1 < sx1) {
      dw += dx1 - sx1;
      if (dw > sw) {
        dw = sw;
      }
    } else {
      int w = sw + sx1 - dx1;
      if (dw > w) {
        dw = w;
      }
    }
    if (dy1 < sy1) {
      dh += dy1 - sy1;
      if (dh > sh) {
        dh = sh;
      }
    } else {
      int h = sh + sy1 - dy1;
      if (dh > h) {
        dh = h;
      }
    }
    return !(dw <= 0 || dh <= 0);
  }



  //////////////////////////////////////////////////////////////
  
  // internal blitter/resizer/copier from toxi
  // uses bilinear filtering if smooth() has been enabled
  // 'mode' determines the blending mode used in the process

  private void blit_resize(BImage img, 
                           int srcX1, int srcY1, int srcX2, int srcY2, 
                           int[] destPixels, int screenW, int screenH, 
                           int destX1, int destY1, int destX2, int destY2,
                           int mode) {
    if (srcX1<0) srcX1=0;
    if (srcY1<0) srcY1=0;
    if (srcX2>=img.width) srcX2=img.width-1;
    if (srcY2>=img.width) srcY2=img.height-1;

    int srcW = srcX2 - srcX1;
    int srcH = srcY2 - srcY1;
    int destW = destX2 - destX1;
    int destH = destY2 - destY1;

    if (!smooth) {
      srcW++; srcH++;
    }

    if (destW <= 0 || destH <= 0 ||
        srcW <= 0 || srcH <= 0 ||
        destX1 >= screenW || destY1 >= screenH ||
        srcX1 >= img.width || srcY1 >= img.height) {
      return;
    }

    int dx = (int) (srcW / (float) destW * PRECISIONF);
    int dy = (int) (srcH / (float) destH * PRECISIONF);

    srcXOffset = (int) (destX1 < 0 ? -destX1 * dx : srcX1 * PRECISIONF);
    srcYOffset = (int) (destY1 < 0 ? -destY1 * dy : srcY1 * PRECISIONF);

    if (destX1 < 0) {
      destW += destX1;
      destX1 = 0;
    }
    if (destY1 < 0) {
      destH += destY1;
      destY1 = 0;
    }

    destW = low(destW, screenW - destX1);
    destH = low(destH, screenH - destY1);

    int destOffset = destY1 * screenW + destX1;
    srcBuffer = img.pixels;

    if (smooth) {
      // use bilinear filtering
      iw = img.width;
      iw1 = img.width - 1;
      ih1 = img.height - 1;
      
      switch (mode) {

      case BLEND:
        for (int y = 0; y < destH; y++) {
          filter_new_scanline();
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_multiply(destPixels[destOffset + x], filter_bilinear());
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case ADD:
        for (int y = 0; y < destH; y++) {
          filter_new_scanline();
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_add_pin(destPixels[destOffset + x], filter_bilinear());
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case SUBTRACT:
        for (int y = 0; y < destH; y++) {
          filter_new_scanline();
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_sub_pin(destPixels[destOffset + x], filter_bilinear());
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case LIGHTEST:
        for (int y = 0; y < destH; y++) {
          filter_new_scanline();
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_lightest(destPixels[destOffset + x], filter_bilinear());
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case DARKEST:
        for (int y = 0; y < destH; y++) {
          filter_new_scanline();
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_darkest(destPixels[destOffset + x], filter_bilinear());
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case REPLACE:
        for (int y = 0; y < destH; y++) {
          filter_new_scanline();
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] = filter_bilinear();
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;
      }

    } else {
      // nearest neighbour scaling (++fast!)
      switch (mode) {

      case BLEND:
        for (int y = 0; y < destH; y++) {
          sX = srcXOffset;
          sY = (srcYOffset >> PRECISIONB) * img.width;
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_multiply(destPixels[destOffset + x], 
                             srcBuffer[sY + (sX >> PRECISIONB)]);
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case ADD:
        for (int y = 0; y < destH; y++) {
          sX = srcXOffset;
          sY = (srcYOffset >> PRECISIONB) * img.width;
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_add_pin(destPixels[destOffset + x], 
                            srcBuffer[sY + (sX >> PRECISIONB)]);
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case SUBTRACT:
        for (int y = 0; y < destH; y++) {
          sX = srcXOffset;
          sY = (srcYOffset >> PRECISIONB) * img.width;
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_sub_pin(destPixels[destOffset + x], 
                            srcBuffer[sY + (sX >> PRECISIONB)]);
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case LIGHTEST:
        for (int y = 0; y < destH; y++) {
          sX = srcXOffset;
          sY = (srcYOffset >> PRECISIONB) * img.width;
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_lightest(destPixels[destOffset + x], 
                             srcBuffer[sY + (sX >> PRECISIONB)]);
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case DARKEST:
        for (int y = 0; y < destH; y++) {
          sX = srcXOffset;
          sY = (srcYOffset >> PRECISIONB) * img.width;
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_darkest(destPixels[destOffset + x], 
                            srcBuffer[sY + (sX >> PRECISIONB)]);
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case REPLACE:
        for (int y = 0; y < destH; y++) {
          sX = srcXOffset;
          sY = (srcYOffset >> PRECISIONB) * img.width;
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] = srcBuffer[sY + (sX >> PRECISIONB)];
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;
      }
    }
  }


  private void filter_new_scanline() {
    sX = srcXOffset;
    fracV = srcYOffset & PREC_MAXVAL;
    ifV = PREC_MAXVAL - fracV;
    v1 = (srcYOffset >> PRECISIONB) * iw;
    v2 = low((srcYOffset >> PRECISIONB) + 1, ih1) * iw;
  }


  private int filter_bilinear() {
    fracU = sX & PREC_MAXVAL;
    ifU = PREC_MAXVAL - fracU;
    ul = (ifU * ifV) >> PRECISIONB;
    ll = (ifU * fracV) >> PRECISIONB;
    ur = (fracU * ifV) >> PRECISIONB;
    lr = (fracU * fracV) >> PRECISIONB;
    u1 = (sX >> PRECISIONB);
    u2 = low(u1 + 1, iw1);

    // get color values of the 4 neighbouring texels
    cUL = srcBuffer[v1 + u1];
    cUR = srcBuffer[v1 + u2];
    cLL = srcBuffer[v2 + u1];
    cLR = srcBuffer[v2 + u2];

    r = ((ul*((cUL&RED_MASK)>>16) + ll*((cLL&RED_MASK)>>16) + 
          ur*((cUR&RED_MASK)>>16) + lr*((cLR&RED_MASK)>>16))
         << PREC_RED_SHIFT) & RED_MASK;
    g = ( (ul*(cUL&GREEN_MASK) + ll*(cLL&GREEN_MASK) +
           ur*(cUR&GREEN_MASK) + lr*(cLR&GREEN_MASK)) >>> PRECISIONB) & GREEN_MASK;
    b = (ul*(cUL&BLUE_MASK) + ll*(cLL&BLUE_MASK) + 
         ur*(cUR&BLUE_MASK) + lr*(cLR&BLUE_MASK)) >>> PRECISIONB;
    a = ((ul*((cUL&ALPHA_MASK)>>>24) + ll*((cLL&ALPHA_MASK)>>>24) + 
          ur*((cUR&ALPHA_MASK)>>>24) + lr*((cLR&ALPHA_MASK)>>>24)) 
         << PREC_ALPHA_SHIFT) & ALPHA_MASK;

    return a | r | g | b;
  }



  //////////////////////////////////////////////////////////////

  // internal blending methods


  private static int low(int a, int b) {
    return (a < b) ? a : b;
  }


  private static int high(int a, int b) {
    return (a > b) ? a : b;
  }


  private float frac(float x) {
    return (x - (int) x);
  }


  /**
   * generic linear interpolation
   */
  private static int mix(int a, int b, int f) {
    return a + (((b - a) * f) >> 8);
  }



  /////////////////////////////////////////////////////////////
  
  // BLEND MODE IMPLEMENTIONS

  private static int blend_multiply(int a, int b) {
    int f = (b & ALPHA_MASK) >>> 24;

    return (low(((a & ALPHA_MASK) >>> 24) + f, 0xff) << 24 |
            mix(a & RED_MASK, b & RED_MASK, f) & RED_MASK |
            mix(a & GREEN_MASK, b & GREEN_MASK, f) & GREEN_MASK | 
            mix(a & BLUE_MASK, b & BLUE_MASK, f));
  }


  /**
   * additive blend with clipping
   */
  private static int blend_add_pin(int a, int b) {
    int f = (b & ALPHA_MASK) >>> 24;

    return (low(((a & ALPHA_MASK) >>> 24) + f, 0xff) << 24 |
            low(((a & RED_MASK) +
                 ((b & RED_MASK) >> 8) * f), RED_MASK) & RED_MASK |
            low(((a & GREEN_MASK) +
                 ((b & GREEN_MASK) >> 8) * f), GREEN_MASK) & GREEN_MASK |
            low((a & BLUE_MASK) + 
                (((b & BLUE_MASK) * f) >> 8), BLUE_MASK));
  }


  /**
   * subtractive blend with clipping
   */
  private static int blend_sub_pin(int a, int b) {
    int f = (b & ALPHA_MASK) >>> 24;

    return (low(((a & ALPHA_MASK) >>> 24) + f, 0xff) << 24 |
            high(((a & RED_MASK) - ((b & RED_MASK) >> 8) * f),
                 GREEN_MASK) & RED_MASK |
            high(((a & GREEN_MASK) - ((b & GREEN_MASK) >> 8) * f),
                 BLUE_MASK) & GREEN_MASK |
            high((a & BLUE_MASK) - (((b & BLUE_MASK) * f) >> 8), 0));
  }


  /**
   * only returns the blended lightest colour
   */
  private static int blend_lightest(int a, int b) {
    int f = (b & ALPHA_MASK) >>> 24;

    return (low(((a & ALPHA_MASK) >>> 24) + f, 0xff) << 24 |
            high(a & RED_MASK, ((b & RED_MASK) >> 8) * f) & RED_MASK |
            high(a & GREEN_MASK, ((b & GREEN_MASK) >> 8) * f) & GREEN_MASK |
            high(a & BLUE_MASK, ((b & BLUE_MASK) * f) >> 8));
  }


  /**
   * only returns the blended darkest colour
   */
  private static int blend_darkest(int a, int b) {
    int f = (b & ALPHA_MASK) >>> 24;

    return (low(((a & ALPHA_MASK) >>> 24) + f, 0xff) << 24 |
            mix(a & RED_MASK,
                low(a & RED_MASK, 
                    ((b & RED_MASK) >> 8) * f), f) & RED_MASK |
            mix(a & GREEN_MASK, 
                low(a & GREEN_MASK, 
                    ((b & GREEN_MASK) >> 8) * f), f) & GREEN_MASK |
            mix(a & BLUE_MASK, 
                low(a & BLUE_MASK, 
                    ((b & BLUE_MASK) * f) >> 8), f));
  }



  //////////////////////////////////////////////////////////////

  // FILE I/O


  static byte tiff_header[] = {
    77, 77, 0, 42, 0, 0, 0, 8, 0, 9, 0, -2, 0, 4, 0, 0, 0, 1, 0, 0,
    0, 0, 1, 0, 0, 3, 0, 0, 0, 1, 0, 0, 0, 0, 1, 1, 0, 3, 0, 0, 0, 1,
    0, 0, 0, 0, 1, 2, 0, 3, 0, 0, 0, 3, 0, 0, 0, 122, 1, 6, 0, 3, 0,
    0, 0, 1, 0, 2, 0, 0, 1, 17, 0, 4, 0, 0, 0, 1, 0, 0, 3, 0, 1, 21,
    0, 3, 0, 0, 0, 1, 0, 3, 0, 0, 1, 22, 0, 3, 0, 0, 0, 1, 0, 0, 0, 0,
    1, 23, 0, 4, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 8, 0, 8, 0, 8
  };

  static void write_tiff(OutputStream output, int pixels[],
                         int width, int height) throws IOException {

    byte tiff[] = new byte[768];
    System.arraycopy(tiff_header, 0, tiff, 0, tiff_header.length);

    tiff[30] = (byte) ((width >> 8) & 0xff);
    tiff[31] = (byte) ((width) & 0xff);
    tiff[42] = tiff[102] = (byte) ((height >> 8) & 0xff);
    tiff[43] = tiff[103] = (byte) ((height) & 0xff);

    int count = width*height*3;
    tiff[114] = (byte) ((count >> 24) & 0xff);
    tiff[115] = (byte) ((count >> 16) & 0xff);
    tiff[116] = (byte) ((count >> 8) & 0xff);
    tiff[117] = (byte) ((count) & 0xff);

    output.write(tiff);

    for (int i = 0; i < pixels.length; i++) {
      output.write((pixels[i] >> 16) & 0xff);
      output.write((pixels[i] >> 8) & 0xff);
      output.write(pixels[i] & 0xff);
    }
    output.flush();
  }


  /**
   * [toxi 030902]
   * Creates a Targa32 formatted byte sequence of specified pixel buffer
   *
   * [fry 030917] 
   * Modified to write directly to OutputStream, because of 
   * memory issues with first making an array of the data.
   * tga spec: http://organicbit.com/closecombat/formats/tga.html
   */
  static void write_targa(OutputStream output, int pixels[], 
                          int width, int height) throws IOException {

    byte header[] = new byte[18];

    // set header info
    header[2]  = 0x02;
    header[12] = (byte) (width & 0xff);
    header[13] = (byte) (width >> 8);
    header[14] = (byte) (height & 0xff);
    header[15] = (byte) (height >> 8);
    header[16] = 32; // bits per pixel
    header[17] = 8;  // bits per colour component

    output.write(header);

    int index = (height-1) * width;

    for (int y = height-1; y >= 0; y--) {
      for (int x = 0; x < width; x++) {
        int col = pixels[index + x];
        output.write(col       & 0xff);
        output.write(col >> 8  & 0xff);
        output.write(col >> 16 & 0xff);
        output.write(col >>> 24 & 0xff);
      }
      index -= width;
    }
    output.flush();
  }


  public void save(String filename) {
    try {
      OutputStream os = null;

      if (filename.toLowerCase().endsWith(".tga")) {
        os = new BufferedOutputStream(new FileOutputStream(filename), 32768);
        write_targa(os, pixels, width, height);

      } else {
        if (!filename.toLowerCase().endsWith(".tif") &&
            !filename.toLowerCase().endsWith(".tiff")) {
          // if no .tif extension, add it..
          filename += ".tif";
        }
        os = new BufferedOutputStream(new FileOutputStream(filename), 32768);
        write_tiff(os, pixels, width, height);
      }
      os.flush();
      os.close();

    } catch (IOException e) {
      e.printStackTrace();
    }
  }


  /*
  // why is this code here? me confused [fry]
  public void save(OutputStream output, int type) {
    try {
      if (type == TARGA) {
        write_targa(output, pixels, width, height);

      } else if (type == TIFF) {
        write_tiff(output, pixels, width, height);

      } else {
        System.err.println("can't save image as that type");
      }
    } catch (IOException e) {
      System.err.println("error while trying to save image");
      e.printStackTrace();
    }
  }
  */
}

