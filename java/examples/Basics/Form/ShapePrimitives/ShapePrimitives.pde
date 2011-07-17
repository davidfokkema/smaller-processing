/**
 * Shape Primitives. 
 * 
 * The basic shape primitive functions are triangle(),
 * rect(), quad(), and ellipse(). Squares are made
 * with rect() and circles are made with
 * ellipse(). Each of these functions requires a number
 * of parameters to determine the shape's position and size. 
 */
 
size(640, 360);
smooth(); 
background(0);
noStroke();
fill(204);
triangle(18, 18, 18, 360, 81, 360);
fill(153);
rect(81, 81, 63, 63);
fill(204);
quad(189, 18, 216, 18, 216, 360, 144, 360);
fill(255);
ellipse(252, 144, 72, 72);
fill(204);
triangle(288, 18, 351, 360, 288, 360); 

