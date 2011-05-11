@echo off

rem
rem Run the marcformat.pl script. Note: runformat.pl has been superceded
rem by the mutil.pl script, so this script is probably not very useful.
rem
rem CVS Tracking info:
rem
rem $Revision: 1.1.1.1 $
rem $Author: smcgovrn $
rem $Date: 2003/01/03 21:01:54 $
rem $Id: runmf.bat,v 1.1.1.1 2003/01/03 21:01:54 smcgovrn Exp $
rem

perl -I C:/home/smcgovern/src/perl %perlbase%marcformat.pl %*
