#! /bin/sh
#
# Run ant with the specifed target. If no target is specified,
# issue an error message, and quit

if [ -z "$1" ]
then
	echo "No ant target specified"
	exit
fi

if [ ! -z "$OS" -a "$OS" = "Windows_NT" ]
then
	if [ "`hostname`" = "CDLTEMP" ]
	then
		export MB_HOME=/home/smcgovern/qahome
	else
		export MB_HOME=$/work/cdl/qahome
	fi
fi


if [ -z "$MB_HOME" ]
then
	export MB_HOME=.
fi
echo MB_HOME=$MB_HOME

if [ ! -f setupdone.txt ]
then
	setup.sh
fi

if [ ! -f setupdone.txt ]
then
	echo "Setup failed - exiting"
	exit
fi

mycwd=`pwd`
cd $MB_HOME/scripts
ant_target=$1
echo "Running ant for target $ant_target"

#
# set ANT_PARMS to include extra parameters.
# In particular -debug will turn on debug messages.
#

echo "ant.sh $ANT_PARMS -buildfile build-marc.xml $ant_target >${ant_target}.out.txt 2>&1"
ant.sh $ANT_PARMS -buildfile build-marc.xml $ant_target >${ant_target}.out.txt 2>&1
cd $mycwd
