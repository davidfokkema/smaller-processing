@echo off

REM --- if you're running out of memory, change the 128m 
REM --- (which means 128 megabytes) to something higher. 

set SAVEDCP=%CLASSPATH%
set CLASSPATH=lib;lib\build;lib\pde.jar;lib\core.jar;lib\antlr.jar;lib\oro.jar;lib\mrj.jar;%windir%\system32\qtjava.zip;%windir%\system\qtjava.zip

start javaw -ms128m -mx128m PdeBase

set CLASSPATH=%SAVEDCP%
