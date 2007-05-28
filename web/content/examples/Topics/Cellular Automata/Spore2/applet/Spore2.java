import processing.core.*; import java.applet.*; import java.awt.*; import java.awt.image.*; import java.awt.event.*; import java.io.*; import java.net.*; import java.text.*; import java.util.*; import java.util.zip.*; public class Spore2 extends PApplet {/**
 * Spore 2 
 * by Mike Davis. 
 * 
 * A short program for alife experiments. Click in the window to restart. 
 * Each cell is represented by a pixel on the display as well as an entry in
 * the array 'cells'. Each cell has a run() method, which performs actions
 * based on the cell's surroundings.  Cells run one at a time (to avoid conflicts
 * like wanting to move to the same space) and in random order. 
 * 
 * Created 9 January 2003
 */
 
World w;
int maxcells = 4000;
int numcells;
Cell[] cells = new Cell[maxcells];
int spore1, spore2, spore3, spore4;
int black = color(0, 0, 0);
// set lower for smoother animation, higher for faster simulation
int runs_per_loop = 10000;

public void setup()
{
  size(200, 200);
  frameRate(24);
  clearscr();
  w = new World();
  spore1 = color(128, 172, 255);
  spore2 = color(64, 128, 255);
  spore3 = color(255, 128, 172);
  spore4 = color(255, 64, 128);
  numcells = 0;
  seed();
}

public void seed()
{
  // Add cells at random places
  for (int i = 0; i < maxcells; i++)
  {
    int cX = PApplet.parseInt(random(width));
    int cY = PApplet.parseInt(random(height));
    int c;
    float r = random(1);
    if (r < 0.25f) c = spore1;
    else if (r < 0.5f) c = spore2;
    else if (r < 0.75f) c = spore3;
    else c = spore4;
    if (w.getpix(cX, cY) == black)
    {
      w.setpix(cX, cY, c);
      cells[numcells] = new Cell(cX, cY);
      numcells++;
    }
  }
}

public void draw()
{
  // Run cells in random order
  for (int i = 0; i < runs_per_loop; i++) {
    int selected = min((int)random(numcells), numcells - 1);
    cells[selected].run();
  }
}

public void clearscr()
{
  for (int y = 0; y < height; y++) {
    for (int x = 0; x < width; x++) {
      set(x, y, color(0));
    }
  }
}

class Cell
{
  int x, y;
  Cell(int xin, int yin)
  {
    x = xin;
    y = yin;
  }
  // Perform action based on surroundings
  public void run()
  {
    // Fix cell coordinates
    while(x < 0) {
      x+=width;
    }
    while(x > width - 1) {
      x-=width;
    }
    while(y < 0) {
      y+=height;
    }
    while(y > height - 1) {
      y-=height;
    }

    // Cell instructions
    int myColor = w.getpix(x, y);
    if (myColor == spore1) {
      if (w.getpix(x - 1, y + 1) == black && w.getpix(x + 1, y + 1) == black && w.getpix(x, y + 1) == black) move(0, 1);
      else if (w.getpix(x - 1, y) == spore2 && w.getpix(x - 1, y - 1) != black) move(0, -1);
      else if (w.getpix(x - 1, y) == spore2 && w.getpix(x - 1, y - 1) == black) move(-1, -1);
      else if (w.getpix(x + 1, y) == spore1 && w.getpix(x + 1, y - 1) != black) move(0, -1);
      else if (w.getpix(x + 1, y) == spore1 && w.getpix(x + 1, y - 1) == black) move(1, -1);
      else move((int)random(3) - 1, 0);
    } else if (myColor == spore2) {
      if (w.getpix(x - 1, y + 1) == black && w.getpix(x + 1, y + 1) == black && w.getpix(x, y + 1) == black) move(0, 1);
      else if (w.getpix(x + 1, y) == spore1 && w.getpix(x + 1, y - 1) != black) move(0, -1);
      else if (w.getpix(x + 1, y) == spore1 && w.getpix(x + 1, y - 1) == black) move(1, -1);
      else if (w.getpix(x - 1, y) == spore2 && w.getpix(x - 1, y - 1) != black) move(0, -1);
      else if (w.getpix(x - 1, y) == spore2 && w.getpix(x - 1, y - 1) == black) move(-1, -1);
      else move((int)random(3) - 1, 0);
    }
    else if (myColor == spore3)
    {
      if (w.getpix(x - 1, y - 1) == black && w.getpix(x + 1, y - 1) == black && w.getpix(x, y - 1) == black) move(0, -1);
      else if (w.getpix(x - 1, y) == spore4 && w.getpix(x - 1, y + 1) != black) move(0, 1);
      else if (w.getpix(x - 1, y) == spore4 && w.getpix(x - 1, y + 1) == black) move(-1, 1);
      else if (w.getpix(x + 1, y) == spore3 && w.getpix(x + 1, y + 1) != black) move(0, 1);
      else if (w.getpix(x + 1, y) == spore3 && w.getpix(x + 1, y + 1) == black) move(1, 1);
      else move((int)random(3) - 1, 0);
    }
    else if (myColor == spore4)
    {
      if (w.getpix(x - 1, y - 1) == black && w.getpix(x + 1, y - 1) == black && w.getpix(x, y - 1) == black) move(0, -1);
      else if (w.getpix(x + 1, y) == spore3 && w.getpix(x + 1, y + 1) != black) move(0, 1);
      else if (w.getpix(x + 1, y) == spore3 && w.getpix(x + 1, y + 1) == black) move(1, 1);
      else if (w.getpix(x - 1, y) == spore4 && w.getpix(x - 1, y + 1) != black) move(0, 1);
      else if (w.getpix(x - 1, y) == spore4 && w.getpix(x - 1, y + 1) == black) move(-1, 1);
      else move((int)random(3) - 1, 0);
    }
  }
  
  // Will move the cell (dx, dy) units if that space is empty
  public void move(int dx, int dy) {
    if (w.getpix(x + dx, y + dy) == black) {
      w.setpix(x + dx, y + dy, w.getpix(x, y));
      w.setpix(x, y, color(0));
      x += dx;
      y += dy;
    }
  }
}

//  The World class simply provides two functions, get and set, which access the
//  display in the same way as getPixel and setPixel.  The only difference is that
//  the World class's get and set do screen wraparound ("toroidal coordinates").
class World
{
  public void setpix(int x, int y, int c) {
    while(x < 0) x+=width;
    while(x > width - 1) x-=width;
    while(y < 0) y+=height;
    while(y > height - 1) y-=height;
    set(x, y, c);
  }
  
  public int getpix(int x, int y) {
    while(x < 0) x+=width;
    while(x > width - 1) x-=width;
    while(y < 0) y+=height;
    while(y > height - 1) y-=height;
    return get(x, y);
  }
}

public void mousePressed()
{
  setup();
}

static public void main(String args[]) {   PApplet.main(new String[] { "Spore2" });}}