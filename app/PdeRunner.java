public class PdeRunner implements Runnable {
  //DbnGraphics graphics;
  PdeEnvironment env;
  String program;

  PdeEngine engine;
  // dbn definitely needs an engine, 
  // for the others it's just an interface

  static final int RUNNER_STARTED = 0;
  static final int RUNNER_FINISHED = 1;
  static final int RUNNER_ERROR = -1;
  static final int RUNNER_STOPPED = 2;
  int state = RUNNER_FINISHED;

  Thread thread;
  boolean forceStop;


  public PdeRunner(PdeEnvironment env) {
    this(env, "");
  }

  public PdeRunner(PdeEnvironment env, String program) { 
    this.program = program;
    //this.graphics = graphics;
    this.env = env;
  }


  public void setProgram(String program) {
    this.program = program;
  }


  public void start() {
    if (thread != null) {
      try { 
	thread.stop(); 
      } catch (Exception e) { }
      thread = null;
    }
    thread = new Thread(this, "PdeRunner");
    thread.start();
  }


  public void run() {
    state = RUNNER_STARTED;
    //graphics.reset();  // remove for pde

    try {
      if (program.length() == 0) {

	/*
      } else if (program.indexOf('#') < 2) { //charAt(0) == '#') {
#ifdef PYTHON

#ifdef OPENGL
	program = "#\r\n" + 
	  "import DbnEditorGraphics3D\r\n" +
	  "import ExperimentalCanvas\r\n" +
	  "g = DbnEditorGraphics3D.getCurrentGraphics()\r\n" +
	  "glc = g.canvas\r\n" +
	  "gl = glc.getGL()\r\n" +
	  "glj = glc.getGLJ()\r\n" + program;
#endif

	forceStop = true;
	engine = new PythonEngine(program);
	engine.start();
	forceStop = false;
#else
	throw new Exception("python support not included");
#endif
	*/

      } else if (program.indexOf("extends ProcessingApplet") != -1) {
#ifdef JAVAC
	engine = new JavacEngine(program, graphics);
	engine.start();
#else
	throw new Exception("javac support not included");
#endif

      } else if (program.indexOf("// dbn") < 2) {
#ifdef DBN
	String pre = "set red 0; set green 1; set blue 2; " + 
	  "set quicktime 0; set tiff 1; set illustrator 2; ";
	DbnParser parser = 
	  new DbnParser(DbnPreprocessor.process(pre + program));

	DbnToken root = parser.getRoot();
	//root.print();
	if (!root.findToken(DbnToken.SIZE)) {
	  graphics.size(101, 101, 1);
	}
	if (root.findToken(DbnToken.REFRESH)) {
	  graphics.aiRefresh = false;
	}
	engine = new DbnEngine(root, graphics);
	engine.start();
#else
	throw new Exception("dbn support not included");
#endif

      } else {
	forceStop = true;
	engine = new PythonEngine(program);
	engine.start();
	forceStop = false;

	
      }
      //System.out.println("finished");
      state = RUNNER_FINISHED;
      env.finished();
      //graphics.update();  // removed for pde

    } catch (PdeException e) { 
      state = RUNNER_ERROR;
      forceStop = false;
      this.stop();
      env.error(e);

    } catch (Exception e) {
#ifndef KVM
      e.printStackTrace();
#endif
      this.stop();
    }	
  }


  public void stop() {
    if (engine != null) {
      engine.stop();
      if (forceStop) {
	thread.stop();
	thread = null;
      }
      engine = null;
    }
  }
}
