rem
rem Compile all the marc utility and converter classes using jikes
rem
rem CVS Tracking info:
rem
rem $Revision: 1.1.1.1 $
rem $Author: smcgovrn $
rem $Date: 2003/01/03 21:01:54 $
rem $Id: jkall.bat,v 1.1.1.1 2003/01/03 21:01:54 smcgovrn Exp $
rem

jikes -d %home%/classes -depend +P -sourcepath %home%/src/java -g org/cdlib/util/cc/*.java
jikes -d %home%/classes -depend +P -sourcepath %home%/src/java -g org/cdlib/util/io/*.java
jikes -d %home%/classes -depend +P -sourcepath %home%/src/java -g org/cdlib/util/marc/*.java
jikes -d %home%/classes -depend +P -sourcepath %home%/src/java -g org/cdlib/util/rpt/*.java
jikes -d %home%/classes -depend +P -sourcepath %home%/src/java -g org/cdlib/util/string/*.java
jikes -d %home%/classes -depend +P -sourcepath %home%/src/java -g org/cdlib/util/*.java
jikes -d %home%/classes -depend +P -sourcepath %home%/src/java -g org/cdlib/marcconvert/*.java
jikes -d %home%/classes -depend +P -sourcepath %home%/src/java -g org/cdlib/marcconvert/run/*.java
