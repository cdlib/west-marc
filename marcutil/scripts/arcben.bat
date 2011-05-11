rem
rem Run the script arcben.pl for all sources.
rem
rem CVS Tracking info:
rem
rem $Revision: 1.1.1.1 $
rem $Author: smcgovrn $
rem $Date: 2003/01/03 21:01:54 $
rem $Id: arcben.bat,v 1.1.1.1 2003/01/03 21:01:54 smcgovrn Exp $
rem

if "%@eval[2+2]%" == "4" goto got4NT
perl ..\src\perl\arcben.pl %* all
goto done

:got4NT
perl ..\src\perl\arcben.pl %$ all

:done


