#!/bin/sh

CLASSPATH=/System/Library/Frameworks/JavaVM.framework/Versions/1.4/Classes/classes.jar:/System/Library/Frameworks/JavaVM.framework/Versions/1.4/Classes/ui.jar:/System/Library/Frameworks/JavaVM.framework/Versions/1.4/Home/lib/ext/comm.jar:/System/Library/Java/Extensions/QTJava.zip:lib:lib/build:lib/pde.jar:lib/kjc.jar:lib/antlr.jar:lib/oro.jar:../comm.jar
export CLASSPATH

cd work && /System/Library/Frameworks/JavaVM.framework/Versions/1.4/Commands/java PdeBase

