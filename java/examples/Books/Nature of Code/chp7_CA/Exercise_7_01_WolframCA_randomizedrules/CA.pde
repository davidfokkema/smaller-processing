// The Nature of Code
// Daniel Shiffman
// http://natureofcode.com

// Wolfram Cellular Automata

// A class to manage the CA

class CA {

  int[] cells;     // An array of 0s and 1s 
  int generation;  // How many generations?

  int[] ruleset;     // An array to store the ruleset, for example {0,1,1,0,1,1,0,1}

  int w = 5;

  CA(int[] r) {
    ruleset = r;
    cells = new int[width/w];
    restart();
  }

  // Make a random ruleset
  void randomize() {
    for (int i = 0; i < 8; i++) {
      ruleset[i] = int(random(2));
    }
  }

  // Reset to generation 0
  void restart() {
    for (int i = 0; i < cells.length; i++) {
      cells[i] = 0;
    }
    cells[cells.length/2] = 1;    // We arbitrarily start with just the middle cell having a state of "1"
    generation = 0;
  }


  // The process of creating the new generation
  void generate() {
    // First we create an empty array for the new values
    int[] nextgen = new int[cells.length];
    // For every spot, determine new state by examing current state, and neighbor states
    // Ignore edges that only have one neighor
    for (int i = 1; i < cells.length-1; i++) {
      int left = cells[i-1];   // Left neighbor state
      int me = cells[i];       // Current state
      int right = cells[i+1];  // Right neighbor state
      nextgen[i] = rules(left, me, right); // Compute next generation state based on ruleset
    }
    // The current generation is the new generation
    cells = nextgen;
    generation++;
  }

  // This is the easy part, just draw the cells, fill 255 for '1', fill 0 for '0'
  void display() {
    for (int i = 0; i < cells.length; i++) {
      if (cells[i] == 1) fill(0);
      else               fill(255);
      noStroke();
      rect(i*w, generation*w, w, w);
    }
  }

  // Implementing the Wolfram rules
  // This is the concise conversion to binary way
  /*int rules (int a, int b, int c) {
   String s = "" + a + b + c;
   int index = Integer.parseInt(s, 2);
   return ruleset[index];
   }*/
  // For JavaScript Mode
  int rules (int a, int b, int c) {
    if (a == 1 && b == 1 && c == 1) return ruleset[0];
    if (a == 1 && b == 1 && c == 0) return ruleset[1];
    if (a == 1 && b == 0 && c == 1) return ruleset[2];
    if (a == 1 && b == 0 && c == 0) return ruleset[3];
    if (a == 0 && b == 1 && c == 1) return ruleset[4];
    if (a == 0 && b == 1 && c == 0) return ruleset[5];
    if (a == 0 && b == 0 && c == 1) return ruleset[6];
    if (a == 0 && b == 0 && c == 0) return ruleset[7];
    return 0;
  }

  // The CA is done if it reaches the bottom of the screen
  boolean finished() {
    if (generation > height/w) {
      return true;
    } 
    else {
      return false;
    }
  }
}

