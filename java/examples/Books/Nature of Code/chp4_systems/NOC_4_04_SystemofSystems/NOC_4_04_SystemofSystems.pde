// The Nature of Code
// Daniel Shiffman
// http://natureofcode.com
// Simple Particle System

// Particles are generated each cycle through draw(),
// fall with gravity and fade out over time
// A ParticleSystem object manages a variable size (ArrayList) 
// list of particles.

ArrayList<ParticleSystem> systems;

void setup() {
  size(800,200);
  systems = new ArrayList<ParticleSystem>();
  smooth();
}

void draw() {
  background(255);
  for (ParticleSystem ps: systems) {
    ps.run();
    ps.addParticle(); 
  }
}

void mousePressed() {
  systems.add(new ParticleSystem(1,new PVector(mouseX,mouseY)));
}



