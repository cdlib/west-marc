rem
rem Copy benchmark files from the test directory to the cvs root directory.
rem
rem CVS Tracking info:
rem
rem $Revision: 1.1.1.1 $
rem $Author: smcgovrn $
rem $Date: 2003/01/03 21:01:54 $
rem $Id: copyben.bat,v 1.1.1.1 2003/01/03 21:01:54 smcgovrn Exp $
rem

@echo off

setlocal
set status=okay

if defined cdlhome goto :gotcdlhome
echo "no cdlhome defined - paths will be relative to current directory"
set script=.\src\perl\copybench.pl
set source=.\run
set target=.\cvsroot\melvyl\ei\marcconvert\test
goto :testfiles

:gotcdlhome
set script=%cdlhome%\src\perl\copybench.pl
set source=%cdlhome%\run
set target=%cdlhome%\cvsroot\melvyl\ei\marcconvert\test

:testfiles
:testscript
if exist %script% goto testsource
echo "script: %script% does not exist"
set status=error

:testsource
if exist %source%\nul goto testtarget
echo "source: %source% does not exist"
set status=error

:testtarget
if exist %target%\nul goto runperl
echo "source: %target% does not exist"
set status=error

:runperl
if "%status%" == "error" goto :done
set command=perl %script% %source% %target% all
echo %command%
%command%
goto :done

:done