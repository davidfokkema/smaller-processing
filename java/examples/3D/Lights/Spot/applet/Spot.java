import processing.core.*; 

import java.applet.*; 
import java.awt.*; 
import java.awt.image.*; 
import java.awt.event.*; 
import java.io.*; 
import java.net.*; 
import java.text.*; 
import java.util.*; 
import java.util.zip.*; 
import java.util.regex.*; 

public class Spot extends PApplet {

/**
 * Spot. 
 * 
 * Move the mouse the change the position and concentation
 * of a blue spot light. 
 */
 
int concentration = 600; // Try values 1 -> 10000

public void setup() 
{
  //size(200, 200, P3D);
  size(640, 360, P3D);
  noStroke();
  fill(204);
  sphereDetail(60);
}

public void draw() 
{
  background(0); 
  
  // Light the bottom of the sphere
  directionalLight(51, 102, 126, 0, -1, 0);
  
  // Orange light on the upper-right of the sphere
  spotLight(204, 153, 0, 360, 160, 600, 0, 0, -1, PI/2, 600); 
  
  // Moving spotlight that follows the mouse
  spotLight(102, 153, 204, 360, mouseY, 600, 0, 0, -1, PI/2, 600); 
  
  translate(width/2, height/2, 0); 
  sphere(120); 
}


  static public void main(String args[]) {
    PApplet.main(new String[] { "Spot" });
  }
}
