/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

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

import java.nio.IntBuffer;
import processing.core.PApplet;
import processing.core.PConstants;

/**
 * Encapsulates a Frame Buffer Object for offscreen rendering.
 * When created with onscreen == true, it represents the normal
 * framebuffer. Needed by the stack mechanism in OPENGL2 to return
 * to onscreen rendering after a sequence of pushFramebuffer calls.
 * It transparently handles the situations when the FBO extension is
 * not available.
 * 
 * By Andres Colubri.
 */
public class PFramebuffer implements PConstants {  
  protected PApplet parent;
  protected PGraphicsOpenGL ogl;
  protected PGL pgl;
  
  public int glFboID;
  public int glDepthBufferID;
  public int glStencilBufferID;
  public int glDepthStencilBufferID;
  public int glColorBufferMultisampleID;
  public int width;
  public int height;

  protected int depthBits;
  protected int stencilBits;
  protected boolean combinedDepthStencil;
  
  protected boolean multisample;
  protected int nsamples;
  
  protected int numColorBuffers;
  //protected int[] colorBufferAttchPoints;
  protected PTexture[] colorBufferTex;

  protected boolean screenFb;
  protected boolean noDepth;
  protected boolean fboMode;  
   
  protected PTexture backupTexture;
  protected IntBuffer pixelBuffer;

  PFramebuffer(PApplet parent, int w, int h) {
    this(parent, w, h, 1, 1, 0, 0, false, false);
  }  
  
  PFramebuffer(PApplet parent, int w, int h, boolean screen) {    
    this(parent, w, h, 1, 1, 0, 0, false, screen);
  }

  PFramebuffer(PApplet parent, int w, int h, int samples, int colorBuffers, 
               int depthBits, int stencilBits, boolean combinedDepthStencil, 
               boolean screen) {
    this.parent = parent;
    ogl = (PGraphicsOpenGL)parent.g;
    pgl = ogl.pgl;
    //gl.registerPGLObject(this);
    
    glFboID = 0;
    glDepthBufferID = 0;
    glStencilBufferID = 0;
    glDepthStencilBufferID = 0;    
    glColorBufferMultisampleID = 0;
        
    fboMode = PGraphicsOpenGL.fboSupported;
    
    if (screen) {
      // If this framebuffer is used to represent a on-screen buffer,
      // then it doesn't make it sense for it to have multisampling,
      // color, depth or stencil buffers.
      depthBits = stencilBits = samples = colorBuffers = 0; 
    }
    
    width = w;
    height = h;
    
    if (1 < samples) {
      multisample = true;
      nsamples = samples;      
    } else {
      multisample = false;
      nsamples = 1;      
    }
        
    numColorBuffers = colorBuffers;
    //colorBufferAttchPoints = new int[numColorBuffers];
    colorBufferTex = new PTexture[numColorBuffers];
    for (int i = 0; i < numColorBuffers; i++) {
      //colorBufferAttchPoints[i] = GL.GL_COLOR_ATTACHMENT0 + i; 
      colorBufferTex[i] = null;
    }    
    
    if (depthBits < 1 && stencilBits < 1) {
      this.depthBits = 0;
      this.stencilBits = 0;
      this.combinedDepthStencil = false;
    } else {
      if (combinedDepthStencil) {
        // When combined depth/stencil format is required, the depth and stencil bits
        // are overriden and the 24/8 combination for a 32 bits surface is used. 
        this.depthBits = 24;
        this.stencilBits = 8;
        this.combinedDepthStencil = true;        
      } else {
        this.depthBits = depthBits;
        this.stencilBits = stencilBits;
        this.combinedDepthStencil = false;        
      }
    }
    
    screenFb = screen;

    allocate();
    noDepth = false;
    
    pixelBuffer = null;
    
    if (!screenFb && !fboMode) {
      // When FBOs are not available, rendering to texture is implemented by saving a portion of
      // the screen, doing the "offscreen" rendering on this portion, copying the screen color 
      // buffer to the texture bound as color buffer to this PFramebuffer object and then drawing 
      // the backup texture back on the screen.
      backupTexture = new PTexture(parent, width, height, new PTexture.Parameters(ARGB, POINT));       
    }
  }

  
  protected void finalize() throws Throwable {
    try {
      if (glFboID != 0) {
        ogl.finalizeFrameBufferObject(glFboID);
      }      
      if (glDepthBufferID != 0) {
        ogl.finalizeRenderBufferObject(glDepthBufferID);
      }      
      if (glStencilBufferID != 0) {
        ogl.finalizeRenderBufferObject(glStencilBufferID);
      }
      if (glColorBufferMultisampleID != 0) {
        ogl.finalizeRenderBufferObject(glColorBufferMultisampleID);
      }
      if (glDepthStencilBufferID != 0) {
        ogl.finalizeRenderBufferObject(glDepthStencilBufferID);
      }      
    } finally {
      super.finalize();
    }
  }  
  
  public void clear() {
    ogl.pushFramebuffer();
    ogl.setFramebuffer(this);
//    getGl().glClearColor(0f, 0f, 0f, 0.0f);
//    getGl().glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT | GL.GL_STENCIL_BUFFER_BIT);
    pgl.setClearColor(0, 0, 0, 0);
    pgl.clearAllBuffers();
    ogl.popFramebuffer();    
  }
  
  public void copy(PFramebuffer dest) {
//    getGl().glBindFramebuffer(GL2.GL_READ_FRAMEBUFFER, this.glFboID);
//    getGl().glBindFramebuffer(GL2.GL_DRAW_FRAMEBUFFER, dest.glFboID);
    pgl.bindReadFramebuffer(this.glFboID);
    pgl.bindWriteFramebuffer(dest.glFboID);
    
//    getGl2().glBlitFramebuffer(0, 0, this.width, this.height, 0, 0, dest.width, dest.height, GL.GL_COLOR_BUFFER_BIT, GL.GL_NEAREST);    
    pgl.copyFramebuffer(this.width, this.height, dest.width, dest.height);
  }
  
  public void bind() {
    if (screenFb) {
      if (PGraphicsOpenGL.fboSupported) {
//        getGl().glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
        pgl.bindFramebuffer(0);
      }
    } else if (fboMode) {
//      getGl().glBindFramebuffer(GL.GL_FRAMEBUFFER, glFboID);
      pgl.bindFramebuffer(glFboID);
    } else {
      backupScreen();
      
      if (0 < numColorBuffers) {
        // Drawing the current contents of the first color buffer to emulate
        // front-back buffer swap.
        ogl.drawTexture(colorBufferTex[0].glTarget, colorBufferTex[0].glID, width, height, 0, 0, width, height, 0, 0, width, height);
      }
      
      if (noDepth) {
//        getGl().glDisable(GL.GL_DEPTH_TEST); 
        pgl.disableDepthTest();
      }
    }
  }
  
  public void disableDepthTest() {
    noDepth = true;  
  }
  
  public void finish() {
    if (noDepth) {
      // No need to clear depth buffer because depth testing was disabled.
      if (ogl.hintEnabled(DISABLE_DEPTH_TEST)) {
//        getGl().glDisable(GL.GL_DEPTH_TEST);
        pgl.disableDepthTest();
      } else {
//        getGl().glEnable(GL.GL_DEPTH_TEST);
        pgl.enableDepthTest();
      }        
    }
    
    if (!screenFb && !fboMode) {
      copyToColorBuffers();
      restoreBackup();
      if (!noDepth) {
        // Reading the contents of the depth buffer is not possible in OpenGL ES:
        // http://www.idevgames.com/forum/archive/index.php?t-15828.html
        // so if this framebuffer uses depth and is offscreen with no FBOs, then
        // the depth buffer is cleared to avoid artifacts when rendering more stuff
        // after this offscreen render.
        // A consequence of this behavior is that all the offscreen rendering when
        // no FBOs are available should be done before any onscreen drawing.
        pgl.setClearColor(0, 0, 0, 0);
        pgl.clearDepthBuffer();
//        getGl().glClearColor(0, 0, 0, 0);
//        getGl().glClear(GL.GL_DEPTH_BUFFER_BIT);
      }
    }
  }
    
  // Saves content of the screen into the backup texture.
  public void backupScreen() {  
    if (pixelBuffer == null) createPixelBuffer();    
    pixelBuffer.rewind();
    //getGl().glReadPixels(0, 0, width, height, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, pixelBuffer);
    pgl.readPixels(pixelBuffer, 0, 0, width, height);
    
    copyToTexture(pixelBuffer, backupTexture.glID, backupTexture.glTarget);
  }

  // Draws the contents of the backup texture to the screen.
  public void restoreBackup() {
    ogl.drawTexture(backupTexture, 0, 0, width, height, 0, 0, width, height);
  }
  
  // Copies current content of screen to color buffers.
  public void copyToColorBuffers() {
    if (pixelBuffer == null) createPixelBuffer();
    pixelBuffer.rewind();
    //getGl().glReadPixels(0, 0, width, height, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, pixelBuffer);
    pgl.readPixels(pixelBuffer, 0, 0, width, height);
    for (int i = 0; i < numColorBuffers; i++) {
      copyToTexture(pixelBuffer, colorBufferTex[i].glID, colorBufferTex[i].glTarget);
    }
  }  
  
  public void readPixels() {
    if (pixelBuffer == null) createPixelBuffer();
    pixelBuffer.rewind();
//    getGl().glReadPixels(0, 0, width, height, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, pixelBuffer);
    pgl.readPixels(pixelBuffer, 0, 0, width, height);
  }
  
  public void getPixels(int[] pixels) {
    if (pixelBuffer != null) {
      pixelBuffer.get(pixels);
      pixelBuffer.rewind();    
    }
  }
  
  public IntBuffer getPixelBuffer() {
    return pixelBuffer;
  }
  
  public boolean hasDepthBuffer() {
    return 0 < depthBits;
  }

  public boolean hasStencilBuffer() {
    return 0 < stencilBits;
  }
  
  ///////////////////////////////////////////////////////////  

  // Color buffer setters.
  
  
  public void setColorBuffer(PTexture tex) {
    setColorBuffers(new PTexture[] { tex }, 1);
  }
  

  public void setColorBuffers(PTexture[] textures) {
    setColorBuffers(textures, textures.length);
  }
  
  
  public void setColorBuffers(PTexture[] textures, int n) {
    if (screenFb) return;

    if (numColorBuffers != PApplet.min(n, textures.length)) {
      throw new RuntimeException("Wrong number of textures to set the color buffers.");
    }
        
    for (int i = 0; i < numColorBuffers; i++) {
      colorBufferTex[i] = textures[i];
    }
      
    if (fboMode) {
      ogl.pushFramebuffer();
      ogl.setFramebuffer(this);

      // Making sure nothing is attached.
      for (int i = 0; i < numColorBuffers; i++) {
//        getGl().glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0 + i, GL.GL_TEXTURE_2D, 0, 0);
        pgl.cleanFramebufferTexture(i);
      }

      for (int i = 0; i < numColorBuffers; i++) {
        pgl.setFramebufferTexture(i, colorBufferTex[i].glTarget, colorBufferTex[i].glID);        
//        getGl().glFramebufferTexture2D(GL.GL_FRAMEBUFFER, colorBufferAttchPoints[i], colorBufferTex[i].glTarget, colorBufferTex[i].glID, 0);
      }

      validateFbo();

      ogl.popFramebuffer();
    }
  }  
  
  
  ///////////////////////////////////////////////////////////  

  // Allocate/release framebuffer.   
  
  
  protected void allocate() {
    release(); // Just in the case this object is being re-allocated.    
    
    if (screenFb) {
      glFboID = 0;
    } else if (fboMode) {
      //glFboID = ogl.createGLResource(PGraphicsOpenGL.GL_FRAME_BUFFER); 
      glFboID = ogl.createFrameBufferObject();
    }  else {
      glFboID = 0;
    }
    
    // create the rest of the stuff...
    if (multisample) {
      createColorBufferMultisample();
    }
   
    if (combinedDepthStencil) {
      createCombinedDepthStencilBuffer();
    } else {
      if (0 < depthBits) {
        createDepthBuffer();
      }
      if (0 < stencilBits) {
        createStencilBuffer();
      }      
    }    
  }
  
  
  protected void release() {
    if (glFboID != 0) {
      ogl.finalizeFrameBufferObject(glFboID);
      glFboID = 0;
    }
    if (glDepthBufferID != 0) {
      ogl.finalizeRenderBufferObject(glDepthBufferID);
      glDepthBufferID = 0;
    }
    if (glStencilBufferID != 0) {
      ogl.finalizeRenderBufferObject(glStencilBufferID);
      glStencilBufferID = 0;
    }
    if (glColorBufferMultisampleID != 0) {
      ogl.finalizeRenderBufferObject(glColorBufferMultisampleID);
      glColorBufferMultisampleID = 0;
    }
    if (glDepthStencilBufferID != 0) {
      ogl.finalizeRenderBufferObject(glDepthStencilBufferID);
      glDepthStencilBufferID = 0;
    }     
  }
  
  
  protected void createColorBufferMultisample() {
    if (screenFb) return;
    
    if (fboMode) {
      ogl.pushFramebuffer();
      ogl.setFramebuffer(this);      

      glColorBufferMultisampleID = ogl.createRenderBufferObject();
      pgl.bindRenderbuffer(glColorBufferMultisampleID);
      pgl.setRenderbufferNumSamples(nsamples, width, height);
      pgl.setRenderbufferColorAttachment(glColorBufferMultisampleID);
      
//      getGl().glBindRenderbuffer(GL.GL_RENDERBUFFER, glColorBufferMultisampleID);
//      getGl2().glRenderbufferStorageMultisample(GL.GL_RENDERBUFFER, nsamples, GL.GL_RGBA8, width, height);            
//      getGl().glFramebufferRenderbuffer(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_RENDERBUFFER, glColorBufferMultisampleID);
      
      ogl.popFramebuffer();      
    }
  }
  
  
  protected void createCombinedDepthStencilBuffer() {
    if (screenFb) return;
    
    if (width == 0 || height == 0) {
      throw new RuntimeException("PFramebuffer: size undefined.");
    }
    
    if (fboMode) {    
      ogl.pushFramebuffer();
      ogl.setFramebuffer(this);
      
      glDepthStencilBufferID = ogl.createRenderBufferObject();
      pgl.bindRenderbuffer(glDepthStencilBufferID);      
      //getGl().glBindRenderbuffer(GL.GL_RENDERBUFFER, glDepthStencilBufferID);
      
      if (multisample) { 
//        getGl2().glRenderbufferStorageMultisample(GL.GL_RENDERBUFFER, nsamples, GL.GL_DEPTH24_STENCIL8, width, height);
        pgl.setRenderbufferNumSamples(nsamples, width, height);
      } else {
        pgl.setRenderbufferStorage(PGL.DEPTH_24BIT_STENCIL_8BIT, width, height);
//        getGl().glRenderbufferStorage(GL.GL_RENDERBUFFER, GL.GL_DEPTH24_STENCIL8, width, height);
      }
      
      pgl.setRenderbufferDepthAttachment(glDepthStencilBufferID);
      pgl.setRenderbufferStencilAttachment(glDepthStencilBufferID);
//      getGl().glFramebufferRenderbuffer(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_RENDERBUFFER, glDepthStencilBufferID);
//      getGl().glFramebufferRenderbuffer(GL.GL_FRAMEBUFFER, GL.GL_STENCIL_ATTACHMENT, GL.GL_RENDERBUFFER, glDepthStencilBufferID);
      
      ogl.popFramebuffer();  
    }    
  }
  
  
  protected void createDepthBuffer() {
    if (screenFb) return;
    
    if (width == 0 || height == 0) {
      throw new RuntimeException("PFramebuffer: size undefined.");
    }
    
    if (fboMode) {
      ogl.pushFramebuffer();
      ogl.setFramebuffer(this);

      glDepthBufferID = ogl.createRenderBufferObject();
      //getGl().glBindRenderbuffer(GL.GL_RENDERBUFFER, glDepthBufferID);
      pgl.bindRenderbuffer(glDepthBufferID);

      int glConst = PGL.DEPTH_16BIT;
      if (depthBits == 16) {
        glConst = PGL.DEPTH_16BIT; 
      } else if (depthBits == 24) {
        glConst = PGL.DEPTH_24BIT;
      } else if (depthBits == 32) {
        glConst = PGL.DEPTH_32BIT;              
      }
      
      if (multisample) { 
        //getGl2().glRenderbufferStorageMultisample(GL.GL_RENDERBUFFER, nsamples, glConst, width, height);
        pgl.setRenderbufferNumSamples(nsamples, width, height);
      } else {
        //getGl().glRenderbufferStorage(GL.GL_RENDERBUFFER, glConst, width, height);
        pgl.setRenderbufferStorage(glConst, width, height);
      }                    

      pgl.setRenderbufferDepthAttachment(glDepthBufferID);
      //getGl().glFramebufferRenderbuffer(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_RENDERBUFFER, glDepthBufferID);

      ogl.popFramebuffer();
    }
  }
    
  
  protected void createStencilBuffer() {
    if (screenFb) return;
    
    if (width == 0 || height == 0) {
      throw new RuntimeException("PFramebuffer: size undefined.");
    }

    if (fboMode) {    
      ogl.pushFramebuffer();
      ogl.setFramebuffer(this);

      glStencilBufferID = ogl.createRenderBufferObject();
//      getGl().glBindRenderbuffer(GL.GL_RENDERBUFFER, glStencilBufferID);
      pgl.bindRenderbuffer(glStencilBufferID);

      int glConst = PGL.STENCIL_1BIT;
      if (stencilBits == 1) {
        glConst = PGL.STENCIL_1BIT; 
      } else if (stencilBits == 4) {
        glConst = PGL.STENCIL_4BIT;
      } else if (stencilBits == 8) {
        glConst = PGL.STENCIL_8BIT;              
      }
      if (multisample) { 
//        getGl2().glRenderbufferStorageMultisample(GL.GL_RENDERBUFFER, nsamples, glConst, width, height);
        pgl.setRenderbufferNumSamples(nsamples, width, height);
      } else {      
//        getGl().glRenderbufferStorage(GL.GL_RENDERBUFFER, glConst, width, height);
        pgl.setRenderbufferStorage(glConst, width, height);
      }
      
      pgl.setRenderbufferStencilAttachment(glStencilBufferID);
//      getGl().glFramebufferRenderbuffer(GL.GL_FRAMEBUFFER, GL.GL_STENCIL_ATTACHMENT, GL.GL_RENDERBUFFER, glStencilBufferID);

      ogl.popFramebuffer();
    }
  }  
  
  
  protected void createPixelBuffer() {
    pixelBuffer = IntBuffer.allocate(width * height);
    pixelBuffer.rewind();     
  }  
  
  ///////////////////////////////////////////////////////////  

  // Utilities.  
  
  // Internal copy to texture method.
  protected void copyToTexture(IntBuffer buffer, int glid, int gltarget) {
    pgl.enableTexturing(gltarget);
    pgl.bindTexture(gltarget, glid);    
    pgl.copyTexSubImage(buffer, gltarget, 0, 0, width, height);
    pgl.unbindTexture(gltarget);
    pgl.disableTexturing(gltarget);
    
//    getGl().glEnable(gltarget);
//    getGl().glBindTexture(gltarget, glid);    
//    getGl().glTexSubImage2D(gltarget, 0, 0, 0, width, height, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, buffer);
//    getGl().glBindTexture(gltarget, 0);
//    getGl().glDisable(gltarget);
  }  
  
  public boolean validateFbo() {
//    int status = getGl().glCheckFramebufferStatus(GL.GL_FRAMEBUFFER);
    int status = pgl.getFramebufferStatus();
    if (status == PGL.FRAMEBUFFER_COMPLETE) {
      return true;
    } else if (status == PGL.FRAMEBUFFER_INCOMPLETE_ATTACHMENT) {
      throw new RuntimeException("PFramebuffer: GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT (" + Integer.toHexString(status) + ")");
    } else if (status == PGL.FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT) {
      throw new RuntimeException("PFramebuffer: GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT (" + Integer.toHexString(status) + ")");
    } else if (status == PGL.FRAMEBUFFER_INCOMPLETE_DIMENSIONS) {
      throw new RuntimeException("PFramebuffer: GL_FRAMEBUFFER_INCOMPLETE_DIMENSIONS (" + Integer.toHexString(status) + ")");      
    } else if (status == PGL.FRAMEBUFFER_INCOMPLETE_FORMATS) {
      throw new RuntimeException("PFramebuffer: GL_FRAMEBUFFER_INCOMPLETE_FORMATS (" + Integer.toHexString(status) + ")");
    } else if (status == PGL.FRAMEBUFFER_UNSUPPORTED) {
      throw new RuntimeException("PFramebuffer: GL_FRAMEBUFFER_UNSUPPORTED" + Integer.toHexString(status));      
    } else {
      throw new RuntimeException("PFramebuffer: unknown framebuffer error (" + Integer.toHexString(status) + ")");
    }
  }

//  protected GL getGl() {
//    return ogl.gl;
//  }
//  
//  protected GL2GL3 getGl2() {
//    return ogl.gl2x;
//  }  
}
