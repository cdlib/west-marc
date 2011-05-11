@echo off

rem
rem Run the org.cdlib.util.rpt.MarcStatsReport class.
rem
rem CVS Tracking info:
rem
rem $Revision: 1.1.1.1 $
rem $Author: smcgovrn $
rem $Date: 2003/01/03 21:01:54 $
rem $Id: ms.bat,v 1.1.1.1 2003/01/03 21:01:54 smcgovrn Exp $
rem

if "%@eval[2+2]%" == "4" goto got4NT
java -Dlog4j.configuration=log4j.debug.props org.cdlib.util.rpt.MarcStatsReport %*
goto done

:got4NT
java -Dlog4j.configuration=log4j.debug.props org.cdlib.util.rpt.MarcStatsReport %$

:done
