rem
rem Compile all the marc utility and converter classes using jikes
rem
rem CVS Tracking info:
rem
rem $Revision: 1.1.1.1 $
rem $Author: smcgovrn $
rem $Date: 2003/01/03 21:01:54 $
rem $Id: jcall.bat,v 1.1.1.1 2003/01/03 21:01:54 smcgovrn Exp $
rem

javac -d %home%/classes -sourcepath %cdlhome%/src/java -g org/cdlib/util/cc/*.java
javac -d %home%/classes -sourcepath %cdlhome%/src/java -g org/cdlib/util/io/*.java
javac -d %home%/classes -sourcepath %cdlhome%/src/java -g org/cdlib/util/marc/*.java
javac -d %home%/classes -sourcepath %cdlhome%/src/java -g org/cdlib/util/rpt/*.java
javac -d %home%/classes -sourcepath %cdlhome%/src/java -g org/cdlib/util/string/*.java
javac -d %home%/classes -sourcepath %cdlhome%/src/java -g org/cdlib/util/*.java
javac -d %home%/classes -sourcepath %cdlhome%/src/java -g org/cdlib/marcconvert/*.java
javac -d %home%/classes -sourcepath %cdlhome%/src/java -g org/cdlib/marcconvert/run/*.java
