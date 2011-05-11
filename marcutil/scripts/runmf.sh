#! /bin/sh
#
# Run the marcformat.pl script. Note: runformat.pl has been superceded
# by the mutil.pl script, so this script is probably not very useful.
#
# CVS Tracking info:
#
# $Revision: 1.1.1.1 $
# $Author: smcgovrn $
# $Date: 2003/01/03 21:01:54 $
# $Id: runmf.sh,v 1.1.1.1 2003/01/03 21:01:54 smcgovrn Exp $
#
#-------------------------------------------------------------------------------

perl -I /home/smcgovrn/src/perl /home/smcgovrn/src/perl/marcformat.pl $*
