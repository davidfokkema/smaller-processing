/* -*- mode: jde; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  BFont - font object for text rendering
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

import java.io.*;
import java.util.*;


// value[] could be used to build a char to byte mapping table
// as the font is loaded..
// when generating, use the native char mapping. 

public class PFont implements PConstants {

  //int firstChar = 33; // always
  int charCount;
  PImage images[];

  // image width, a power of 2
  // note! these will always be the same
  int iwidth, iheight; 
  // float versions of the above
  float iwidthf, iheightf;

  // mbox is just the font size (i.e. 48 for most vlw fonts)
  int mbox;

  int value[];  // char code
  int height[]; // height of the bitmap data
  int width[];  // width of bitmap data
  int setWidth[];  // width displaced by the char
  int topExtent[];  // offset for the top
  int leftExtent[];  // offset for the left

  // scaling, for convenience
  float size; 
  float leading;

  int ascii[];  // quick lookup for the ascii chars
  boolean cached;


  public PFont() { }  // for PFontAI subclass and font builder


  public PFont(InputStream input) throws IOException {
    DataInputStream is = new DataInputStream(input);

    charCount   = is.readInt();
    int numBits = is.readInt();
    int mboxX   = is.readInt();  // not used, just fontsize (48)
    int mboxY   = is.readInt();  // also just fontsize (48)

    // only store this one for leading calc
    mbox = mboxY;  

    // size for image ("texture") is next power of 2
    // over the font size. for most vlw fonts, the size is 48
    // so the next power of 2 is 64. 
    iwidth = (int) 
      Math.pow(2, Math.ceil(Math.log(mboxX) / Math.log(2)));
    iheight = (int) 
      Math.pow(2, Math.ceil(Math.log(mboxY) / Math.log(2)));

    iwidthf = (float) iwidth;
    iheightf = (float) iheight;

    // font size is 48, so default leading is 48 * 1.2
    // this is same as what illustrator uses for the default
    //defaultLeading = ((float)mboxY / iheightf) * 1.2f;

    int baseHt = is.readInt(); // zero, ignored
    is.readInt(); // ignore 4 for struct padding

    // allocate enough space for the character info
    value       = new int[charCount];
    height      = new int[charCount];
    width       = new int[charCount];
    setWidth    = new int[charCount];
    topExtent   = new int[charCount];
    leftExtent  = new int[charCount];

    ascii = new int[128];
    for (int i = 0; i < 128; i++) ascii[i] = -1;

    // read the information about the individual characters
    for (int i = 0; i < charCount; i++) {
      value[i]      = is.readInt();
      height[i]     = is.readInt();
      width[i]      = is.readInt();
      setWidth[i]   = is.readInt();
      topExtent[i]  = is.readInt();
      leftExtent[i] = is.readInt();

      // pointer in the c version, ignored
      is.readInt(); 

      // cache locations of the ascii charset
      if (value[i] < 128) ascii[value[i]] = i;
    }

    images = new PImage[charCount];
    for (int i = 0; i < charCount; i++) {
      //int pixels[] = new int[64 * 64];
      int pixels[] = new int[iwidth * iheight];
      //images[i] = new PImage(pixels, 64, 64, ALPHA);
      images[i] = new PImage(pixels, iwidth, iheight, ALPHA);
      int bitmapSize = height[i] * width[i];

      byte temp[] = new byte[bitmapSize];
      is.readFully(temp);

      // convert the bitmap to an alpha channel
      int w = width[i];
      int h = height[i];
      for (int x = 0; x < w; x++) {
        for (int y = 0; y < h; y++) {
          int valu = temp[y*w + x] & 0xff;
          //images[i].pixels[y*64 + x] = valu;
          images[i].pixels[y * iwidth + x] = valu;
          // the following makes javagl more happy.. 
          // not sure what's going on
          //(valu << 24) | (valu << 16) | (valu << 8) | valu; //0xffffff;
          //System.out.print((images[i].pixels[y*64+x] > 128) ? "*" : ".");
        }
        //System.out.println();
      }
      //System.out.println();
    }
    cached = false;
    resetSize();
    resetLeading(); // ??
  }


  public void write(OutputStream output) throws IOException {
    DataOutputStream os = new DataOutputStream(output);

    os.writeInt(charCount); 
    os.writeInt(8);     // numBits
    os.writeInt(mbox);  // mboxX (font size)
    os.writeInt(mbox);  // mboxY (font size)
    os.writeInt(0);     // baseHt, ignored
    os.writeInt(0);     // struct padding for c version

    for (int i = 0; i < charCount; i++) {
      os.writeInt(value[i]);
      os.writeInt(height[i]);
      os.writeInt(width[i]);
      os.writeInt(setWidth[i]);
      os.writeInt(topExtent[i]);
      os.writeInt(leftExtent[i]);
      os.writeInt(0); // padding
    }

    for (int i = 0; i < charCount; i++) {
      //int bitmapSize = height[i] * width[i];
      //byte bitmap[] = new byte[bitmapSize];

      for (int y = 0; y < height[i]; y++) {
        for (int x = 0; x < width[i]; x++) {
          os.write(images[i].pixels[y * width[i] + x] & 0xff);
        }
      }
    }
    os.flush();
    os.close();  // can/should i do this?
  }


  /**
   * Get index for the char (convert from unicode to bagel charset).
   * @return index into arrays or -1 if not found
   */
  public int index(char c) {
    // these chars required in all fonts
    //if ((c >= 33) && (c <= 126)) {
    //return c - 33;
    //}
    // quicker lookup for the ascii fellers
    if (c < 128) return ascii[c];

    // some other unicode char, hunt it out
    return index_hunt(c, 0, value.length-1);
  }


  // whups, this used the p5 charset rather than what was inside the font
  // meaning that old fonts would crash.. fixed for 0069

  private int index_hunt(int c, int start, int stop) {
    //System.err.println("checking between " + start + " and " + stop);
    int pivot = (start + stop) / 2;

    // if this is the char, then return it
    if (c == value[pivot]) return pivot;

    // char doesn't exist, otherwise would have been the pivot
    //if (start == stop) return -1; 
    if (start >= stop) return -1;

    // if it's in the lower half, continue searching that 
    if (c < value[pivot]) return index_hunt(c, start, pivot-1);

    // if it's in the upper half, continue there
    return index_hunt(c, pivot+1, stop);
  }


  float kern(char a, char b) { 
    return 0;  // * size, but since zero..
  }


  public void resetSize() {
    size = 12;
  }


  public void size(float isize) {
    size = isize;
  }


  public void resetLeading() {
    leading = size * ((float)mbox / iheightf) * 1.2f;
  }


  public void leading(float ileading) {
    leading = ileading;
  }


  // supposedly this should be ok even in SCREEN_SPACE mode
  // since the applet will set the 'size' of the font to iwidth
  // (though this prolly breaks any sort of 'height' measurements)
  public float width(char c) {
    if (c == 32) return width('i');

    int cc = index(c);
    if (cc == -1) return 0;

    return ((float)setWidth[cc] / iwidthf) * size;
  }


  public float width(String string) {
    //if (!valid) return 0;
    float wide = 0;
    float pwide = 0;
    char previous = 0;

    char s[] = string.toCharArray();
    for (int i = 0; i < s.length; i++) {
      if (s[i] == '\n') {
        if (wide > pwide) pwide = wide;
        wide = 0;
        previous = 0;

      } else {
        wide += width(s[i]);
        if (previous != 0) {
          wide += kern(previous, s[i]);
        }
        previous = s[i];
      }
    }
    return (pwide > wide) ? pwide : wide;
  }


  public void text(char c, float x, float y, PGraphics parent) {
    text(c, x, y, 0, parent);
  }


  public void text(char c, float x, float y, float z, PGraphics parent) {
    //if (!valid) return;
    //if (!exists(c)) return;

    // eventually replace this with a table
    // to convert the > 127 coded chars
    //int glyph = c - 33;
    int glyph = index(c);
    if (glyph == -1) return;

    if (!cached) {
      // cache on first run, to ensure a graphics context exists
      parent.cache(images);
      cached = true;
    }

    if (parent.text_space == OBJECT_SPACE) {
      float high    = (float) height[glyph]     / iheightf;
      float bwidth  = (float) width[glyph]      / iwidthf;
      float lextent = (float) leftExtent[glyph] / iwidthf;
      float textent = (float) topExtent[glyph]  / iheightf;

      int savedTextureMode = parent.texture_mode;
      //boolean savedSmooth = parent.smooth;
      boolean savedStroke = parent._stroke;

      parent.texture_mode = IMAGE_SPACE;
      //parent.smooth = true;
      parent.drawing_text = true;
      parent._stroke = false;

      float x1 = x + lextent * size;
      float y1 = y - textent * size;
      float x2 = x1 + bwidth * size;
      float y2 = y1 + high * size;

      // this code was moved here (instead of using parent.image)
      // because now images use tint() for their coloring, which
      // internally is kind of a hack because it temporarily sets
      // the fill color to the tint values when drawing text.
      // rather than doubling up the hack with this hack, the code
      // is just included here instead.

      //System.out.println(x1 + " " + y1 + " " + x2 + " " + y2);

      parent.beginShape(QUADS);
      parent.texture(images[glyph]);
      parent.vertex(x1, y1, z, 0, 0);
      parent.vertex(x1, y2, z, 0, height[glyph]);
      parent.vertex(x2, y2, z, width[glyph], height[glyph]);
      parent.vertex(x2, y1, z, width[glyph], 0);
      parent.endShape();

      parent.texture_mode = savedTextureMode;
      //parent.smooth = savedSmooth;
      parent.drawing_text = false;
      parent._stroke = savedStroke;

    } else {  // SCREEN_SPACE
      int xx = (int) x + leftExtent[glyph];;
      int yy = (int) y - topExtent[glyph];

      int x0 = 0;
      int y0 = 0;
      int w0 = width[glyph];
      int h0 = height[glyph];

      if ((xx >= parent.width) || (yy >= parent.height) ||
          (xx + w0 < 0) || (yy + h0 < 0)) return;

      if (xx < 0) {
        x0 -= xx;
        w0 += xx;
        //System.out.println("x " + xx + " " + x0 + " " + w0);
        xx = 0;
      }
      if (yy < 0) {
        y0 -= yy;
        h0 += yy;
        //System.out.println("y " + yy + " " + y0 + " " + h0);
        yy = 0;
      }
      if (xx + w0 > parent.width) {
        //System.out.println("wide " + x0 + " " + w0);
        w0 -= ((xx + w0) - parent.width);
      }
      if (yy + h0 > parent.height) {
        h0 -= ((yy + h0) - parent.height);
      }

      int fr = parent.fillRi;
      int fg = parent.fillGi;
      int fb = parent.fillBi;
      int fa = parent.fillAi;

      int pixels1[] = images[glyph].pixels;
      int pixels2[] = parent.pixels;

      //int index1 = y0 * iwidth; //0;
      //int index2 = 0;

      //for (int row = 0; row < height[glyph]; row++) {
      for (int row = y0; row < y0 + h0; row++) {
        //for (int col = 0; col < width[glyph]; col++) {
        for (int col = x0; col < x0 + w0; col++) {
          int a1 = (fa * pixels1[row * iwidth + col]) >> 8;
          //System.out.println(index1 + col);
          //int a1 = (fa * pixels1[index1 + col]) >> 8;
          int a2 = a1 ^ 0xff;
          int p1 = pixels1[row * width[glyph] + col];
          int p2 = pixels2[(yy + row-y0)*parent.width + (xx+col-x0)];

          pixels2[(yy + row-y0)*parent.width + xx+col-x0] = 
            (0xff000000 | 
             (((a1 * fr + a2 * ((p2 >> 16) & 0xff)) & 0xff00) << 8) |
             (( a1 * fg + a2 * ((p2 >>  8) & 0xff)) & 0xff00) |
             (( a1 * fb + a2 * ( p2        & 0xff)) >> 8));
        }
        //index1 += iwidth;
      }
    }
  }


  // used by the text() functions to avoid over-allocation of memory
  private char c[] = new char[8192];


  public void text(String str, float x, float y, PGraphics parent) {
    text(str, x, y, 0, parent);
  }


  public void text(String str, float x, float y, float z, PGraphics parent) {
    float startX = x;
    int index = 0;
    char previous = 0;

    int length = str.length();
    if (length > c.length) {
      c = new char[length + 10];
    }
    str.getChars(0, length, c, 0);

    while (index < length) {
      if (c[index] == '\n') {
        x = startX;
        y += leading;
        previous = 0;
      } else {
        text(c[index], x, y, z, parent);
        x += width(c[index]);
        if (previous != 0)
          x += kern(previous, c[index]);
        previous = c[index];
      }
      index++;
    }
  }


  /**
   * Same as below, just without a z coordinate.
   */
  public void text(String str, float x, float y, 
                   float w, float h, PGraphics parent) {
    text(str, x, y, 0, w, h, parent);
  }


  /**
   * Draw text in a text both that is constrained to a 
   * particular width and height
   */
  public void text(String str, float x, float y, float z, 
                   float w, float h, PGraphics parent) {
    float space = width(' ');
    float xx = x;
    float yy = y;
    float right = x + w;

    String paragraphs[] = PApplet.split(str, '\n');
    for (int i = 0; i < paragraphs.length; i++) {
      String words[] = PApplet.split(paragraphs[i], ' ');
      float wide = 0;
      for (int j = 0; j < words.length; j++) {
        float size = width(words[j]);
        if (xx + size > right) {
          // this goes on the next line
          xx = 0; 
          yy += leading;
          if (yy > h) return;  // too big for box
        }
        text(words[j], xx, yy, z, parent);
        xx += size + space;
      }
      // end of paragraph, move to left and increment leading
      xx = 0; 
      yy += leading;
      if (yy > h) return;  // too big for box
    }
  }
}
