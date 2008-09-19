class Line
{
  String myString;
  int xPosition;
  int yPosition;
  int highlightNum;
  PFont f;
  float speed;
  float curlInX;
  Letter myLetters[];
  
  Line(String s, int i, int j, PFont bagelfont) 
  {
    myString = s;
    xPosition = i;
    yPosition = j;
    f = bagelfont;
    myLetters = new Letter[s.length()];
    float f1 = 0.0;
    for(int k = 0; k < s.length(); k++)
    {
      char c = s.charAt(k);
      f1 += textWidth(c);
      Letter letter = new Letter(c, f1, 0.0);
      myLetters[k] = letter;
    }

    curlInX = 0.1;
  }
}
