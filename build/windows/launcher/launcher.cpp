// -*- Mode: C++; tab-width: 2; indent-tabs-mode: nil; c-basic-offset: 2 -*-

// launcher.cpp : Defines the class behaviors for the application.
//

// The size of all of the strings was made sort of ambiguously large, since
// 1) nothing is hurt by allocating an extra few bytes temporarily and
// 2) if the user has a long path, and it gets copied five times over for the
// classpath, the program runs the risk of crashing. Bad bad.

#define JAVA_ARGS "-Xms64m -Xmx64m "
#define JAVA_MAIN_CLASS "PdeBase"

#include <windows.h>
#include <stdio.h>
#include <stdlib.h>

int STDCALL
WinMain (HINSTANCE hInst, HINSTANCE hPrev, LPSTR lpCmd, int nShow)
{
  // all these malloc statements... things may need to be larger.

  // what was passed to this application
  char *incoming_cmdline = (char *)malloc(256 * sizeof(char));
  strcpy (incoming_cmdline, lpCmd);

  // what gets put together to pass to jre
  char *outgoing_cmdline = (char *)malloc(16384 * sizeof(char));
        
  // prepend the args for -mx and -ms
  strcpy(outgoing_cmdline, JAVA_ARGS);

  // append the classpath and launcher.Application
  char *loaddir = (char *)malloc(MAX_PATH * sizeof(char));
  *loaddir = 0;

  GetModuleFileName(NULL, loaddir, MAX_PATH);
  // remove the application name
  *(strrchr(loaddir, '\\')) = '\0';

  char *cp = (char *)malloc(8 * strlen(loaddir) + 200);


  // if this code looks shitty, that's because it is.
  // people put the durndest things in their CLASSPATH and
  // QTJAVA environment variables. who knows where and
  // when the quotes will show up. this is a guess at 
  // dealing with the things, without spending a whole
  // day to make it overly robust. [fry]


  // test to see if running with a java runtime nearby or not
  char *testpath = (char *)malloc(MAX_PATH * sizeof(char));
  *testpath = 0;
  strcpy(testpath, loaddir);
  strcat(testpath, "\\java\\bin\\java.exe");
  FILE *fp = fopen(testpath, "rb");
  int local_jre_installed = (fp != NULL);
  //char *rt_jar = (fp == NULL) ? "" : "java\\lib\\rt.jar;";
  fclose(fp); // argh! this was probably causing trouble


  //const char *envClasspath = getenv("CLASSPATH");
  char *env_classpath = (char *)malloc(256 * sizeof(char));
  if (getenv("CLASSPATH") != NULL) {
    strcpy(env_classpath, getenv("CLASSPATH"));
    if (env_classpath[0] == '\"') {
      // starting quote in classpath.. yech
      env_classpath++;  // shitty.. i know..

      int len = strlen(env_classpath);
      if (env_classpath[len-1] == '\"') {
        env_classpath[len-1] = 0;
      } else {
        // a starting quote but no ending quote.. ugh
        // maybe throw an error
      }
    }
    int last = strlen(env_classpath);
    env_classpath[last++] = ';';
    env_classpath[last] = 0;
  } else {
    env_classpath[0] = 0;
  }


  char *env_qtjava = (char *)malloc(256 * sizeof(char));
  if (getenv("QTJAVA") != NULL) {
    strcpy(env_qtjava, getenv("QTJAVA"));
    if (env_qtjava[0] == '\"') {
      // starting quote in qtjava.. almost always
      env_qtjava++;

      int len = strlen(env_qtjava);
      if (env_qtjava[len-1] == '\"') {
        env_qtjava[len-1] = 0;
      } else {
        // a starting quote but no ending quote..
      }
    }
    int last = strlen(env_qtjava);
    env_qtjava[last++] = ';';
    env_qtjava[last] = 0;
  } else {
    env_qtjava[0] = 0;
  }


  // put quotes around contents of cp, 
  // because %s might have spaces in it.
  sprintf(cp,	
          "\""   // begin quote
          "%s"
          "%s"
          "%s"
          "%s\\lib;"
          "%s\\lib\\build;"
          "%s\\lib\\pde.jar;"
          "%s\\lib\\kjc.jar;"
          "%s\\lib\\oro.jar;"
          "%s\\lib\\antlr.jar;"
          "%s\\lib\\comm.jar;"
          //"C:\\WINNT\\system32\\QTJava.zip;"  // worthless
          //"C:\\WINDOWS\\system32\\QTJava.zip;"
          "\"",   // end quote
          //localJreInstalled ? "java\\lib\\rt.jar;" : "", 
          local_jre_installed ? "java\\lib\\rt.jar;" : "", //rt_jar, 
          env_qtjava,
          env_classpath, 
          //envClasspath ? envClasspath : "",
          //envClasspath ? ";" : "",
          loaddir, loaddir, loaddir, loaddir, loaddir, loaddir, loaddir);

  if (!SetEnvironmentVariable("CLASSPATH", cp)) {
    MessageBox(NULL, "Could not set CLASSPATH environment variable",
               "Processing Error", MB_OK);
    return 0;
  }

  //MessageBox(NULL, cp, "whaadddup", MB_OK);

  // add the name of the class to execute and a space before the next arg
  strcat(outgoing_cmdline, JAVA_MAIN_CLASS " ");

  // append additional incoming stuff (document names), if any
  strcat(outgoing_cmdline, incoming_cmdline);

  char *executable = (char *)malloc(256 * sizeof(char));
  // loaddir is the name path to the current application

  //if (localJreInstalled) {
  if (local_jre_installed) {
    strcpy(executable, loaddir);
    // copy in the path for javaw, relative to launcher.exe
    strcat(executable, "\\java\\bin\\javaw.exe");
  } else {
    strcpy(executable, "javaw.exe");
  }

  SHELLEXECUTEINFO ShExecInfo;

  // set up the execution info
  ShExecInfo.cbSize = sizeof(SHELLEXECUTEINFO);
  ShExecInfo.fMask = 0;
  ShExecInfo.hwnd = 0;
  ShExecInfo.lpVerb = "open";
  ShExecInfo.lpFile = executable;
  ShExecInfo.lpParameters = outgoing_cmdline;
  ShExecInfo.lpDirectory = loaddir;
  ShExecInfo.nShow = SW_SHOWNORMAL;
  ShExecInfo.hInstApp = NULL;

  if (!ShellExecuteEx(&ShExecInfo)) {
    MessageBox(NULL, "Error calling ShellExecuteEx()", 
               "Processing Error", MB_OK);
    return 0;
  }

  if (reinterpret_cast<int>(ShExecInfo.hInstApp) <= 32) {

    // some type of error occurred
    switch (reinterpret_cast<int>(ShExecInfo.hInstApp)) {
    case ERROR_FILE_NOT_FOUND:
    case ERROR_PATH_NOT_FOUND:
	    MessageBox(NULL, "A required file could not be found. \n"
                 "You may need to install a Java runtime\n"
                 "or re-install Processing.",
                 "Processing Error", MB_OK);
	    break;
    case 0:
    case SE_ERR_OOM:
	    MessageBox(NULL, "Not enough memory or resources to run at"
                 " this time.", "Processing Error", MB_OK);
	    
	    break;
    default:
	    MessageBox(NULL, "There is a problem with your installation.\n"
                 "If the problem persists, re-install the program.", 
                 "Processing Error", MB_OK);
	    break;
    }
  }

  return 0;
}
