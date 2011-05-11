#! /bin/sh
#
# Run the org.cdlib.util.io.FileStat class
#
# CVS Tracking info:
#
# $Revision: 1.1.1.1 $
# $Author: smcgovrn $
# $Date: 2003/01/03 21:01:54 $
# $Id: filestat.sh,v 1.1.1.1 2003/01/03 21:01:54 smcgovrn Exp $
#
#-------------------------------------------------------------------------------

java -Dlog4j.configuration=log4j.debug.props org.cdlib.util.io.FileStat $*
