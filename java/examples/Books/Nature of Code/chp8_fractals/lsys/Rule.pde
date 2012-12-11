// The Nature of Code
// Daniel Shiffman
// http://natureofcode.com

// A Class to describe an LSystem Rule            

class Rule {
  char a;
  String b;

  Rule(char a_, String b_) {
    a = a_;
    b = b_; 
  }

  char getA() {
    return a;
  }

  String getB() {
    return b;
  }

}


