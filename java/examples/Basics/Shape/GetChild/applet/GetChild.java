import processing.core.*; 
import processing.xml.*; 

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

public class GetChild extends PApplet {

/**
 * Get Child. 
 * 
 * SVG files can be made of many individual shapes. 
 * Each of these shapes (called a "child") has its own name 
 * that can be used to extract it from the "parent" file.
 * This example loads a map of the United States and creates
 * two new PShape objects by extracting the data from two states.
 */

PShape usa;
PShape michigan;
PShape ohio;

public void setup() {
  size(640, 360);  
  usa = loadShape("usa-wikipedia.svg");
  michigan = usa.getChild("MI");
  ohio = usa.getChild("OH");
  smooth();  // Improves the drawing quality of the SVG
  noLoop();
}

public void draw() {
  background(255);
  
  // Draw the full map
  shape(usa, -600, -180);
  
  // Disable the colors found in the SVG file
  michigan.disableStyle();
  // Set our own coloring
  fill(0, 51, 102);
  noStroke();
  // Draw a single state
  shape(michigan, -600, -180); // Boo Wolverines!
  
  // Disable the colors found in the SVG file
  ohio.disableStyle();
  // Set our own coloring
  fill(153, 0, 0);
  noStroke();
  // Draw a single state
  shape(ohio, -600, -180);  // Go Buckeyes!
}

  static public void main(String args[]) {
    PApplet.main(new String[] { "GetChild" });
  }
}
