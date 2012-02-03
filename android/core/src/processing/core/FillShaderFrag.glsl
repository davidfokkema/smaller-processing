/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2011 Andres Colubri

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

precision mediump float;

uniform sampler2D textureSampler;

uniform int textured;

varying vec4 vertColor;
varying vec2 vertTexcoord;

void main() {
  gl_FragColor = vertColor;
  if (0 < textured) {
    gl_FragColor = texture2D(textureSampler, vertTexcoord) * vertColor;
  } else {
    gl_FragColor = vertColor;
  }
}