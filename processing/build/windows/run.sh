#!/bin/sh

#if test -d /cygdrive/c/WINNT
#then
  # windows 2000 or nt
#  QT_JAVA_PATH=C:\\WINNT\\system32\\QTJava.zip
#else
  # other versions of windows, including xp
#  QT_JAVA_PATH=C:\\WINDOWS\\system32\\QTJava.zip
#fi
# another alternative
#QT_JAVA_PATH=..\\build\\shared\\lib\\qtjava.zip

# #$(*&@(#*$& quicktime for java
QT_JAVA_PATH=`perl -e '$qt = $ENV{'QTJAVA'}; $qt =~ s/\"//g; print $qt'`;

# rxtx testing
#CLASSPATH=java\\lib\\rt.jar\;lib\;lib\\build\;lib\\pde.jar\;lib\\kjc.jar\;lib\\oro.jar\;lib\\RXTXcomm.jar\;${QT_JAVA_PATH}

CLASSPATH=\"java\\lib\\rt.jar\;lib\;lib\\build\;lib\\pde.jar\;lib\\kjc.jar\;lib\\antlr.jar\;lib\\oro.jar\;lib\\comm.jar\;lib\\RXTXcomm.jar\;${QT_JAVA_PATH}\"

#echo $CLASSPATH

# will this one work? or do the quotes have to be chopped?
#CLASSPATH=java\\lib\\rt.jar\;lib\;lib\\build\;lib\\pde.jar\;lib\\kjc.jar\;lib\\antlr.jar\;lib\\oro.jar\;lib\\comm.jar\;lib\\RXTXcomm.jar\;${QTJAVA}

# version for javac/1.1 testing
#CLASSPATH=java\\lib\\rt.jar\;lib\;lib\\build\;lib\\pde.jar\;lib\\kjc.jar\;lib\\oro.jar\;java\\lib\\ext\\comm.jar\;${QT_JAVA_PATH}\;..\\..\\macos9\\JDKClasses.zip\;..\\..\\macos9\\JDKToolsClasses.zip

#cd work && ./java/bin/java -cp ${CLASSPATH} PdeBase
#cd work && ./java/bin/java -cp ${CLASSPATH} PdeBase
cd work && ./java/bin/java PdeBase
