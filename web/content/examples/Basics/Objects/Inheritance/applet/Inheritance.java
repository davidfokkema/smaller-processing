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

public class Inheritance extends PApplet {

/**
 * Inheritance
 * 
 * A class can be defined using another class as a foundation. In object-oriented
 * programming terminology, one class can inherit fi elds and methods from another. 
 * An object that inherits from another is called a subclass, and the object it 
 * inherits from is called a superclass. A subclass extends the superclass.
 */

SpinSpots spots;
SpinArm arm;

public void setup() 
{
  size(200, 200);
  smooth();
  arm = new SpinArm(width/2, height/2, 0.01f);
  spots = new SpinSpots(width/2, height/2, -0.02f, 33.0f);
}

public void draw() 
{
  background(204);
  arm.update();
  arm.display();
  spots.update();
  spots.display();
}

class Spin 
{
  float x, y, speed;
  float angle = 0.0f;
  Spin(float xpos, float ypos, float s) {
    x = xpos;
    y = ypos;
    speed = s;
  }
  public void update() {
    angle += speed;
  }
}

class SpinArm extends Spin 
{
  SpinArm(float x, float y, float s) {
    super(x, y, s);
  }
  public void display() {
    strokeWeight(1);
    stroke(0);
    pushMatrix();
    translate(x, y);
    angle += speed;
    rotate(angle);
    line(0, 0, 66, 0);
    popMatrix();
  }
}

class SpinSpots extends Spin 
{
  float dim;
  SpinSpots(float x, float y, float s, float d) {
    super(x, y, s);
    dim = d;
  }
  public void display() {
    noStroke();
    pushMatrix();
    translate(x, y);
    angle += speed;
    rotate(angle);
    ellipse(-dim/2, 0, dim, dim);
    ellipse(dim/2, 0, dim, dim);
    popMatrix();
  }
}

  static public void main(String args[]) {
    PApplet.main(new String[] { "Inheritance" });
  }
}
