#!/bin/sh

# Programmer: Debra Bartling
# Date: 6/14/2011
# Description: Build melvyl converters for papr project
#
# PAPR is using the jar files copied from marc.git (downloaded from CVS)
# This program just verifies we can build them in our environment.
#
# To run: set BASE to directory containing marc.git clone

#Note: Some input files use or override a deprecated API.
#Note: Recompile with -Xlint:deprecation for details.
#Note: Some input files use unchecked or unsafe operations.
#Note: Recompile with -Xlint:unchecked for details.

# BASE is the top-level directory for git repository clone (marc.git)
BASE=$HOME/dbb-work/marc-local-copy

MARCUTIL=$BASE/marcutil/java
MARCCONVERT=$BASE/melvyl/ei/marcconvert/java
MARCLIB=$BASE/marclib

# Rebuild the jar files in marclib/cdl
# marcutil.jar contains the common marc processing utilities

cd $MARCUTIL

javac -classpath $MARCLIB/ext/jakarta-regexp-1.2.jar:$MARCLIB/ext/log4j-1.2.5.jar:$MARCLIB/ext/sax.jar:. \
  org/cdlib/util/cc/*.java org/cdlib/util/cc/*.java org/cdlib/util/io/*.java org/cdlib/util/marc/*.java \
  org/cdlib/util/rpt/*.java org/cdlib/util/string/*.java org/cdlib/util/*.java

jar cf $MARCLIB/cdl/marcutil.jar \
  org/cdlib/util/cc/*.class org/cdlib/util/cc/*.class org/cdlib/util/io/*.class org/cdlib/util/marc/*.class \
  org/cdlib/util/rpt/*.class org/cdlib/util/string/*.class org/cdlib/util/*.class

# marcconvert.jar contains melvyl converters

cd $MARCCONVERT

javac -classpath $MARCLIB/ext/jakarta-regexp-1.2.jar:$MARCLIB/ext/log4j-1.2.5.jar:$MARCLIB/cdl/marcutil.jar:. \
  org/cdlib/marcconvert/*.java org/cdlib/marcconvert/run/*.java 

jar cf $MARCLIB/cdl/marcconvert.jar \
  org/cdlib/marcconvert/*.class org/cdlib/marcconvert/run/*.class

# Build the Stanford and UCSD Merge programs - moved to papr repository

#javac -classpath $MARCLIB/ext/jakarta-regexp-1.2.jar:$MARCLIB/ext/log4j-1.2.5.jar:$MARCLIB/cdl/marcutil.jar:$MARCLIB/cdl/marcconvert.jar:. \
#  org/cdlib/marcconvert/STFMerge.java org/cdlib/marcconvert/run/RunSTFMerge.java \
#  org/cdlib/marcconvert/UCSDMerge.java org/cdlib/marcconvert/run/RunUCSDMerge.java 
