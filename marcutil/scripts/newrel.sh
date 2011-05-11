#!/bin/sh
#
# Change the release number in the config files, and place the originals
# into directory "./config.bkp/".
#
# CVS Tracking info:
#
# $Revision: 1.2 $
# $Author: smcgovrn $
# $Date: 2003/01/04 00:17:02 $
# $Id: newrel.sh,v 1.2 2003/01/04 00:17:02 smcgovrn Exp $
#
#-------------------------------------------------------------------------------

perl -wpi'config.bkp/*' -e 's/Release-006-000/Release-007-000/og;' *.config.txt
chmod a+w *.config.txt
