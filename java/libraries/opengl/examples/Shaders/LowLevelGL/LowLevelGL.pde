// Draws a triangle using low-level OpenGL calls.
import java.nio.FloatBuffer;

PGraphicsOpenGL pg;
PGL pgl;

PShader shader;

int vertLoc;
int colorLoc;

float[] vertices;
float[] colors;

FloatBuffer vertData;
FloatBuffer colorData;

void setup() {
  size(400, 400, P3D);
  
  pg = (PGraphicsOpenGL)g;
  // Get the default shader that Processing uses to
  // render flat geometry (w/out textures and lights).
  shader = pg.getShader(PShader.FLAT);

  vertices = new float[12];
  vertData = PGL.allocateDirectFloatBuffer(12);

  colors = new float[12];
  colorData = PGL.allocateDirectFloatBuffer(12);
}

void draw() {
  background(0);
  
  // The geometric transformations will be automatically passed 
  // to the shader.
  rotate(frameCount * 0.01f, width, height, 0);
  
  updateGeometry();
  
  pgl = pg.beginPGL(); 
  shader.bind();

  vertLoc = pgl.glGetAttribLocation(shader.glProgramObjectID, "inVertex");
  colorLoc = pgl.glGetAttribLocation(shader.glProgramObjectID, "inColor");
  
  pgl.glEnableVertexAttribArray(vertLoc);
  pgl.glEnableVertexAttribArray(colorLoc);
  
  pgl.glVertexAttribPointer(vertLoc, 4, PGL.GL_FLOAT, false, 0, vertData);
  pgl.glVertexAttribPointer(colorLoc, 4, PGL.GL_FLOAT, false, 0, colorData);

  pgl.glDrawArrays(PGL.GL_TRIANGLES, 0, 3);

  pgl.glDisableVertexAttribArray(vertLoc);
  pgl.glDisableVertexAttribArray(colorLoc);
  
  shader.unbind();  
  pg.endPGL();
}

void updateGeometry() {
  // Vertex 1
  vertices[0] = 0;
  vertices[1] = 0;
  vertices[2] = 0;
  vertices[3] = 1;
  colors[0] = 1;
  colors[1] = 0;
  colors[2] = 0;
  colors[3] = 1;

  // Corner 2
  vertices[4] = width/2;
  vertices[5] = height;
  vertices[6] = 0;
  vertices[7] = 1;
  colors[4] = 0;
  colors[5] = 1;
  colors[6] = 0;
  colors[7] = 1;
  
  // Corner 3
  vertices[8] = width;
  vertices[9] = 0;
  vertices[10] = 0;
  vertices[11] = 1;
  colors[8] = 0;
  colors[9] = 0;
  colors[10] = 1;
  colors[11] = 1;
  
  vertData.rewind();
  vertData.put(vertices);
  vertData.position(0);
  
  colorData.rewind();
  colorData.put(colors);
  colorData.position(0);  
}
