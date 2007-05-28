/**
 * Creating (Homage to Albers). 
 * 
 * Creating variables for colors that may be referred to 
 * in the program by their name, rather than a number. 
 * 
 * Updated 8 May 2007
 */

size(200, 200);
noStroke();

color inside = color(204, 102, 0);
color middle = color(204, 153, 0);
color outside = color(153, 51, 0);

// These statements are equivalent to the statements above.
// Programmers may use the format they prefer.
//color inside = #CC6600;
//color middle = #CC9900;
//color outside = #993300;

fill(outside);
rect(0, 0, 200, 200);
fill(middle);
rect(40, 60, 120, 120);
fill(inside);
rect(60, 90, 80, 80);
