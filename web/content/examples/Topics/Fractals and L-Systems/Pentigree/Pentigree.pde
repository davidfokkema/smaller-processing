/** 
 * Pentigree L-System 
 * by Geraldine Sarmiento (NYU ITP). 
 * 
 * This code was based on Patrick Dwyer's L-System class. 
 */

PentigreeLSystem ps;

void setup() 
{
  size(200, 200);
  frameRate(24);
  smooth();
  ps = new PentigreeLSystem();
  ps.simulate(3);
}

void draw() 
{
  background(0);
  ps.render();
}


class LSystem {

  int steps = 0;

  String axiom;
  String rule;
  String production;

  float startLength;
  float drawLength;
  float theta;

  int generations;

  LSystem() {

    axiom = "F";
    rule = "F+F-F";
    startLength = 90.0f;
    theta = radians(120.0);
    reset();
  }

  void reset() {
    production = axiom;
    drawLength = startLength;
    generations = 0;
  }

  int getAge() {
    return generations;
  }
  
  void render() {
    translate(width/2, height/2);
    steps += 5;          
    if (steps > production.length()) {
      steps = production.length();
    }
    for (int i = 0; i < steps; i++) {
      char step = production.charAt(i);
      if (step == 'F') {
        rect(0, 0, -drawLength, -drawLength);
        noFill();
        translate(0, -drawLength);
      } 
      else if (step == '+') {
        rotate(theta);
      } 
      else if (step == '-') {
        rotate(-theta);
      } 
      else if (step == '[') {
        pushMatrix();
      } 
      else if (step == ']') {
        popMatrix();            
      }
    }
  }
  
  void simulate(int gen) {
    while (getAge() < gen) {
      production = iterate(production, rule);
    }
  }

  String iterate(String prod_, String rule_) {
    drawLength = drawLength * 0.6;
    generations++;
    String newProduction = prod_;          
    newProduction = newProduction.replaceAll("F", rule_);
    return newProduction;
  }
}


class PentigreeLSystem extends LSystem {

  int steps = 0;
  float somestep = 0.1;
  float xoff = 0.01;

  PentigreeLSystem() {
    axiom = "F-F-F-F-F";
    rule = "F-F++F+F-F-F";
    startLength = 40.0;
    theta = radians(72);  
    reset();
  }

  void useRule(String r_) {
    rule = r_;
  }

  void useAxiom(String a_) {
    axiom = a_;
  }

  void useLength(float l_) {
    startLength = l_;
  }

  void useTheta(float t_) {
    theta = radians(t_);
  }

  void reset() {
    production = axiom;
    drawLength = startLength;
    generations = 0;
  }

  int getAge() {
    return generations;
  }

  void render() {
    translate(width/4, height/2);
    steps += 3;          
    if (steps > production.length()) {
      steps = production.length();
    }

    for (int i = 0; i < steps; i++) {
      char step = production.charAt(i);
      if (step == 'F') {
        noFill();
        stroke(255);
        line(0, 0, 0, -drawLength);
        translate(0, -drawLength);
      } 
      else if (step == '+') {
        rotate(theta);
      } 
      else if (step == '-') {
        rotate(-theta);
      } 
      else if (step == '[') {
        pushMatrix();
      } 
      else if (step == ']') {
        popMatrix();
      }
    }
  }

}
