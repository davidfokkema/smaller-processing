/* -*- mode: jde; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  PSound2 - java 1.3 audio loader and player
  Part of the Processing project - http://processing.org

  Copyright (c) 2004 Ben Fry and Casey Reas

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation; either
  version 2.1 of the License, or (at your option) any later version.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

package processing.core;

import java.io.*;
import javax.sound.sampled.*;

// add check for reflection in host applet for sound completion

// also needs to register for stop events with applet

// http://javaalmanac.com/egs/javax.sound.sampled/pkg.html



public class PSound2 extends PSound {
  PApplet applet;

  Clip clip;
  FloatControl gainControl;


  public PSound2(PApplet applet, InputStream input) {
    this.applet = applet;

    try {
      AudioInputStream stream =
        AudioSystem.getAudioInputStream(input);

      // *** this code appears as though it may just be faulty ***

      // At present, ALAW and ULAW encodings must be converted
      // to PCM_SIGNED before it can be played
      AudioFormat format = stream.getFormat();
      if (format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
        format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                                 format.getSampleRate(),
                                 format.getSampleSizeInBits()*2,
                                 format.getChannels(),
                                 format.getFrameSize()*2,
                                 format.getFrameRate(),
                                 true);  // big endian
        stream = AudioSystem.getAudioInputStream(format, stream);
        //} else {
        //System.out.println("no conversion necessary");
      }

      int frameLength = (int) stream.getFrameLength();
      int frameSize = format.getFrameSize();
      DataLine.Info info =
        new DataLine.Info(Clip.class, stream.getFormat(),
                          frameLength * frameSize);

      clip = (Clip) AudioSystem.getLine(info);

      // seems that you can't make more than one of these
      gainControl =
        (FloatControl)clip.getControl(FloatControl.Type.MASTER_GAIN);

      // This method does not return until completely loaded
      clip.open(stream);

      // determining when a sample is done
      // Add a listener for line events
      /*
        clip.addLineListener(new LineListener() {
        public void update(LineEvent evt) {
        if (evt.getType() == LineEvent.Type.STOP) {
        }
        }
        });
      */

      applet.registerDispose(this);

    } catch (Exception e) {
      error("<init>", e);
    }
  }


  public void play() {
    clip.start();
  }


  /**
   * either sets repeat flag, or begins playing (and sets)
   */
  public void loop() {
    clip.loop(Clip.LOOP_CONTINUOUSLY);
  }


  /**
   * ala java 1.3 loop docs:
   * "any current looping should cease and playback should
   * continue to the end of the clip."
   */
  public void noLoop() {
    clip.loop(0);
  }


  // Play and repeat for a certain number of times
  //int numberOfPlays = 3;
  //clip.loop(numberOfPlays-1);


  public void pause() {
    clip.stop();
  }


  /**
   * Stops the audio and rewinds to the beginning.
   */
  public void stop() {
    // clip may become null in the midst of this method
    //if (clip != null) clip.stop();
    //if (clip != null) clip.setFramePosition(0);
    clip.stop();
    clip.setFramePosition(0);
  }


  /**
   * This is registered externally so that the host applet
   * will kill off the playback thread.
   */
  public void dispose() {
    stop();
    clip = null;
  }


  /**
   * current position inside the clip (in seconds, just like video)
   */
  public float time() {
    return (float) (clip.getMicrosecondPosition()/1000000.0d);
  }


  /**
   * duration of the clip in seconds
   */
  public float duration() {
    return (float) (clip.getBufferSize() /
                    (clip.getFormat().getFrameSize() *
                     clip.getFormat().getFrameRate()));
  }


  public void volume(float v) {  // ranges 0..1
    float dB = (float)(Math.log(v)/Math.log(10.0)*20.0);
    gainControl.setValue(dB);
  }

  /** mute */
  /*
  public void noVolume() {
    BooleanControl muteControl =
      (BooleanControl)clip.getControl(BooleanControl.Type.MUTE);
    muteControl.setValue(true);
  }
  */

  /** disable mute */
  /*
  public void volume() {
    muteControl.setValue(false);
  }
  */


  /**
   * General error reporting, all corraled here just in case
   * I think of something slightly more intelligent to do.
   */
  protected void error(String where, Exception e) {
    applet.die("Error inside PSound2." + where + "()", e);
    //e.printStackTrace();
  }
}
