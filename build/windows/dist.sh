#!/bin/sh

REVISION=`head -c 4 ../../todo.txt`

# check to see if the version number in the app is correct
# so that mikkel doesn't kick my ass
VERSIONED=`cat ../../app/PdeBase.java | grep $REVISION`
if [ -z "$VERSIONED" ]
then
  echo Fix the revision number in PdeBase.java
  exit
fi

./make.sh

echo Creating P5 distribution for revision $REVISION...
echo

# remove any old boogers
rm -rf processing
rm -rf processing-*

# use 'shared' files as starting point
cp -r ../shared processing

# add the libraries folder with source
cp -r ../../lib processing/libraries

# new style examples thing ala reas
cd processing
unzip -q examples.zip
rm examples.zip
cd ..

# new style reference
cd processing
unzip -q reference.zip
# necessary for launching reference from shell/command prompt
# which is done internally to view reference
chmod +x reference/*.html
# needed by 'help' menu
chmod +x reference/environment/*.html
# get rid of the zip file
rm reference.zip
cd ..

# clean out the cvs entries
find processing -name "CVS" -exec rm -rf {} ';'
#find processing -name "CVS" -exec echo {} ';'

# add java (jre) files
unzip -q -d processing jre.zip

# directories used by the app
mkdir processing/lib/build

# grab pde.jar and export from the working dir
cp work/lib/pde.jar processing/lib/
cp work/lib/core.jar processing/lib/
#cp -r work/lib/export processing/lib/
#rm -rf processing/lib/export/CVS

# get jikes and depedencies
#gunzip < dist/jikes.gz > processing/jikes.exe
cp dist/jikes.exe processing/
chmod +x processing/jikes.exe

# get platform-specific goodies from the dist dir
cp launcher/processing.exe processing/
cp dist/run.bat processing/
#cp dist/lib/pde_windows.properties processing/lib/

# get serial stuff from the bagel dir
#cp ../../bagel/serial/comm.jar processing/lib/
#cp ../../bagel/serial/javax.comm.properties processing/lib/
#cp ../../bagel/serial/win32com.dll processing/
#chmod +x processing/win32com.dll

# convert notes.txt to windows LFs
# the 2> is because the app is a little chatty
unix2dos processing/readme.txt 2> /dev/null
unix2dos processing/revisions.txt 2> /dev/null
unix2dos processing/lib/preferences.txt 2> /dev/null
unix2dos processing/lib/keywords.txt 2> /dev/null

# remove boogers
find processing -name "*~" -exec rm -f {} ';'
find processing -name ".DS_Store" -exec rm -f {} ';'
find processing -name "._*" -exec rm -f {} ';'

# zip it all up for release
echo Packaging standard release...
echo
P5=processing-$REVISION
mv processing $P5
zip -rq $P5.zip $P5
# nah, keep the new directory around
#rm -rf $P5

# zip up another for experts
echo Packaging expert release...
echo

# can't use the run.bat that's tied to a local jre
rm $P5/run.bat
cp dist/run-expert.bat $P5/

# remove enormous java runtime
rm -rf $P5/java
zip -rq $P5-expert.zip $P5

echo Done.

