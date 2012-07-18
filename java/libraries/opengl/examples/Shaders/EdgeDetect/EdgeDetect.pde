// This example shows how to change the default fragment shader used
// in P2D to render textures, by a custom one that applies a simple 
// edge detection filter.
//
// Press the mouse to switch between the custom and the default shader.

PImage img;
PShader edges;  
boolean customShader;
  
void setup() {
  size(400, 400, P2D);
  img = loadImage("berlin-1.jpg");
    
  edges = (PShader)loadShader("edges.glsl", PShader.TEXTURED);
  shader(edges, PShader.TEXTURED);
  customShader = true;
}

public void draw() {
  image(img, 0, 0, width, height);
}
  
public void mousePressed() {
  if (customShader) {
    resetShader(PShader.TEXTURED);
    customShader = false;
  } else {
    shader(edges, PShader.TEXTURED);
    customShader = true;
  }
}
