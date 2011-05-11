@echo off

rem
rem Customized script to start X under Cygwin.
rem
rem CVS Tracking info:
rem
rem $Revision: 1.1.1.1 $
rem $Author: smcgovrn $
rem $Date: 2003/01/03 21:01:54 $
rem $Id: startxwin.smg.bat,v 1.1.1.1 2003/01/03 21:01:54 smcgovrn Exp $
rem


SET DISPLAY=127.0.0.1:0.0

REM
REM Get the window manager
REM Default to wmaker
REM

SETLOCAL
if not "%1" == "" (SET WINDOWMANAGER=%1)
if not defined WINDOWMANAGER (SET WINDOWMANAGER=wmaker)

REM
REM The path in the CYGWIN_ROOT environment variable assignment assume
REM that Cygwin is installed in a directory called 'cygwin' in the root
REM directory of the current drive.  You will only need to modify
REM CYGWIN_ROOT if you have installed Cygwin in another directory.  For
REM example, if you installed Cygwin in \foo\bar\baz\cygwin, you will need
REM to change \cygwin to \foo\bar\baz\cygwin.
REM
REM This batch file will almost always be run from the same drive (and
REM directory) as the drive that contains Cygwin/XFree86, therefore you will
REM not need to add a drive letter to CYGWIN_ROOT.  For example, you do
REM not need to change \cygwin to c:\cygwin if you are running this
REM batch file from the C drive.
REM

SET CYGWIN_ROOT=\cygwin

SET PATH=.;%CYGWIN_ROOT%\usr\X11R6\bin;%CYGWIN_ROOT%\bin;%PATH%


REM
REM Cleanup after last run.
REM

if not exist %CYGWIN_ROOT%\tmp\.X11-unix\X0 goto CLEANUP-FINISH
attrib -s %CYGWIN_ROOT%\tmp\.X11-unix\X0
del %CYGWIN_ROOT%\tmp\.X11-unix\X0

:CLEANUP-FINISH
if exist %CYGWIN_ROOT%\tmp\.X11-unix rmdir %CYGWIN_ROOT%\tmp\.X11-unix


REM
REM Startup the X Server, the twm window manager, and an xterm.
REM
REM Notice that the window manager and the xterm will wait for
REM the server to finish starting before trying to connect; the
REM error "Cannot Open Display: 127.0.0.1:0.0" is not due to the
REM clients attempting to connect before the server has started, rather
REM that error is due to a bug in some versions of cygwin1.dll.  Upgrade
REM to the latest cygwin1.dll if you get the "Cannot Open Display" error.
REM See the Cygwin/XFree86 FAQ for more information:
REM http://xfree86.cygwin.com/docs/faq/
REM
REM The error "Fatal server error: could not open default font 'fixed'" is
REM caused by using a DOS mode mount for the mount that the Cygwin/XFree86
REM fonts are accessed through.  See the Cygwin/XFree86 FAQ for more
REM information:
REM http://xfree86.cygwin.com/docs/faq/cygwin-xfree-faq.html#q-error-font-eof
REM

if "%OS%" == "Windows_NT" goto OS_NT

REM Windows 95/98/Me
echo startxwin.bat - Starting on Windows 95/98/Me

goto STARTUP

:OS_NT

REM Windows NT/2000/XP
echo startxwin.bat - Starting on Windows NT/2000/XP

:STARTUP


REM
REM Startup the programs
REM

REM Startup the X Server.

start XWin

REM Startup an xterm, using bash as the shell.
run xterm -sl 1000 -sb -rightbar -ms red -fg yellow -bg black -e /usr/bin/bash

REM Startup the twm window manager.
run %WINDOWMANAGER%


REM Set a background color.

run xsetroot -solid aquamarine4

:EXIT_SCRIPT
ENDLOCAL
