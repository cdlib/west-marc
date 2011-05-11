rem
rem Generate documentation for Melvyl-T java converter and utility classes.
rem
rem CVS Tracking info:
rem
rem $Revision: 1.1.1.1 $
rem $Author: smcgovrn $
rem $Date: 2003/01/03 21:01:54 $
rem $Id: jdoc.bat,v 1.1.1.1 2003/01/03 21:01:54 smcgovrn Exp $
rem

pushd ..\src\java
set jdocPackList=http://java.sun.com/j2se/1.3/docs/api/package-list
set jdocTarget=doc
set jdocOpts=-author -version -protected -use -sourcepath . -classpath %classpath%
set jdocLink=-link http://java.sun.com/j2se/1.3/docs/api/
set jdocTitle=-doctitle "Melvyl-T Marc Record Library and Source Converters"
set jdocWindow=-windowtitle "Melvyl-T Marc Record Library and Source Converters"

if not exist %jdocTarget% mkdir %jdocTarget%

javadoc -d %jdocTarget% %jdocOpts% %jdocLink% %jdocTitle% %jdocWindow% %1% @jpackages.txt

popd
