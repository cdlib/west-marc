#! /bin/sh
#
# Wrapper for diff to redirect the output when run from perl.
#-------------------------------------------------------------------------------

#-------------------------------------------------------------------------------
# If we don't have at least two arguments issue an error message and quit
#-------------------------------------------------------------------------------
if [ $# -lt 2 ]
then
	echo "At least two arguments must be specified"
	exit
fi

#-------------------------------------------------------------------------------
# If we have exactly two arguments run diff with them and exit
#-------------------------------------------------------------------------------
if [ $# -eq 2 ]
then
	#echo "diff $1 $2"
	diff $1 $2
	exit
fi

#-------------------------------------------------------------------------------
# We have at least three arguments.
# Use all but the last to build the parameter string to send to diff,
# and use the last the target for the diff output.
#-------------------------------------------------------------------------------
args=

while [ $# -gt 1 ]
do
	args="$args $1"
	shift
done

diff $args > $1
