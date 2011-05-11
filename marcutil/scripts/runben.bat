rem
rem Run the runbench.pl script. This script expects the environment variable
rem "perlbase" to point to the directory containing the runbench.pl script.
rem
rem CVS Tracking info:
rem
rem $Revision: 1.1.1.1 $
rem $Author: smcgovrn $
rem $Date: 2003/01/03 21:01:54 $
rem $Id: runben.bat,v 1.1.1.1 2003/01/03 21:01:54 smcgovrn Exp $
rem

if "%@eval[2+2]%" == "4" goto got4NT
perl %perlbase%runbench.pl %*
goto done

:got4NT
perl %perlbase%runbench.pl %$

:done
