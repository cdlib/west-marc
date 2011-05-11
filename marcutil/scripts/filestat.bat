@echo off

rem
rem Run the org.cdlib.util.io.FileStat class.
rem
rem CVS Tracking info:
rem
rem $Revision: 1.1.1.1 $
rem $Author: smcgovrn $
rem $Date: 2003/01/03 21:01:54 $
rem $Id: filestat.bat,v 1.1.1.1 2003/01/03 21:01:54 smcgovrn Exp $
rem

java -Dlog4j.configuration=log4j.debug.props org.cdlib.util.io.FileStat %*
