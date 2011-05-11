rem
rem Make the marc utility and converter jar files.
rem
rem CVS Tracking info:
rem
rem $Revision: 1.1.1.1 $
rem $Author: smcgovrn $
rem $Date: 2003/01/03 21:01:54 $
rem $Id: mj.bat,v 1.1.1.1 2003/01/03 21:01:54 smcgovrn Exp $
rem

pushd ..\classes
jar cvf ..\jarlib\cdl\marcconvert.jar org/cdlib/marcconvert
jar cvf ..\jarlib\cdl\marcutil.jar org/cdlib/util
jar cvf ..\jarlib\cdl\marc.jar org/cdlib
popd
