/**
 * Datatype Conversion. 
 * 
 * It is sometimes beneficial to convert a value from one type of 
 * data to another. Each of the conversion functions converts its parameter 
 * to an equivalent representation within its datatype. 
 * The conversion functions include int(), float(), char(), byte(), and others. 
 */
 
size(640, 360);
background(0);
noStroke();

char c;    // Chars are used for storing alphanumeric symbols
float f;   // Floats are decimal numbers
int i;     // Integers are values between 2,147,483,647 and -2147483648
byte b;    // Bytes are values between -128 and 128

c = 'A';
f = float(c);      // Sets f = 65.0
i = int(f * 1.4);  // Sets i to 91
b = byte(c / 2);   // Sets b to 32

println(f);
println(i);
println(b);

textSize(24);
text("The value of c is " + c, 50, 100);
text("The value of f is " + f, 50, 150);
text("The value of i is " + i, 50, 200);
text("The value of b is " + b, 50, 250);
