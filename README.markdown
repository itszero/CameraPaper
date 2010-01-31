CameraPaper
===========

the `live' wallpaper experience on your nexus one (well... it works best on nexus one, however, it's also tested on the G1.)

Features
--------

  - Ripple effect for every touch
  - Double tap to pause camera activity
  - Triple tap to take the snapshot
  - Automatic power management

How it works?
-------------

This software is installed as an Service, which you can set as the wallpaper in the Live Wallpaper Picker. When it is running, it activates camera and use it's RAW preview callback as video source. Unfortunately, it uses YUV422SP format for now, and only serving it in the landscape mode. For now, we use a JNI helper to convert YUV422SP to RGBA8888 and rotate it. This routine runs in a seperated thread, and another thread will in charge of state monitoring and perform screen redraw.

Decoding is heavy even for nexus one, it currently need ~250ms for 1frame, thus you get 4~5fps for now. I'm looking for any other ways to workaround this, like using OpenGL|ES shader to perform decoding or tap into SurfaceFlinger to use hardware-accelerated compositing.

Power management
----------------

Camera consumes a lot of power for a phone, so I implemented a smart power mangement mechanism. Once you open a app, camera goes off. You press the power button, camera goes off. Furthermore, when you put your phone on the desk, it basically always gets a black screen. The camera will be turned off if it detects over 90% of screen is very close to black, it'll then register for the G-sensor events, camera can turn back on if you pick up your phone. If your phone does not have an G-sensor, it'll re-open the camera for a certain interval. 

License
-------

Copyright (c) 2010 Chien-An "Zero" Cho

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
