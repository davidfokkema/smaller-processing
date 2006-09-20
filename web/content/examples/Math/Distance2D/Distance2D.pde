/**
 * Distance 2D. 
 * 
 * Move the mouse across the image to obscure and reveal the matrix.  
 * Measures the distance from the mouse to each square and sets the
 * size proportionally. 
 * 
 * Updated 21 August 2002
 */
 
float max_distance;

void setup() {
  size(200, 200); 
  rectMode(CENTER);  
  noStroke();
  max_distance = dist(0, 0, width, height);
}

void draw() 
{
  background(51);

  for(int i=20; i<width; i+=20) {
    for(int j=20; j<width; j+=20) {
      float size = dist(mouseX, mouseY, i, j);
      size = size/max_distance * 66;
      rect(i, j, size, size);
    }
  }
}
