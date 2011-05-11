#! /bin/bash
#
# This script will run a series of diff against a specific set
# of files, and so as such is neither very interesting, nor very useful.
# But it also makes use of a bash function which will echo each command
# prior to executing the command, and that is both interesting and useful.
#
# Bash users may want to add the saydo function to their .bashrc startup
# files.
#
# CVS Tracking info:
#
# $Revision: 1.1.1.1 $
# $Author: smcgovrn $
# $Date: 2003/01/03 21:01:54 $
# $Id: diffrlin.sh,v 1.1.1.1 2003/01/03 21:01:54 smcgovrn Exp $
#
#-------------------------------------------------------------------------------
#diffopts=--strip-trailing-cr --ignore-matching-lines="\(Start\)\|\(End\)\|\(Conf\)"
diffopt1=--strip-trailing-cr
diffopt2=--ignore-matching-lines="\(Start\)\|\(End\)\|\(Conf\)"
echo $diffopts
echo "rundiffs"

function saydo {
	cmd=$1;
	shift;
	if [ ! -z "$cmd" ]
	then
		echo $cmd "$@";
		$cmd "$@";
	fi
}

saydo diff chs.convert.fmt.txt chs/6/chs.convert.fmt.txt
saydo diff chs.error.fmt.txt chs/6/chs.error.fmt.txt
saydo diff chs.reject.fmt.txt chs/6/chs.reject.fmt.txt
saydo diff chs.reject.messages.txt chs/6/chs.reject.messages.txt
saydo diff $diffopt1 $diffopt2 chs.report.txt chs/6/chs.report.txt


saydo diff lbl.convert.fmt.txt lbl/7/lbl.convert.fmt.txt
saydo diff lbl.error.fmt.txt lbl/7/lbl.error.fmt.txt
saydo diff lbl.reject.fmt.txt lbl/7/lbl.reject.fmt.txt
saydo diff lbl.reject.messages.txt lbl/7/lbl.reject.messages.txt
saydo diff $diffopt1 $diffopt2 lbl.report.txt lbl/7/lbl.report.txt


saydo diff ucbl.convert.fmt.txt ucbl/6/ucbl.convert.fmt.txt
saydo diff ucbl.error.fmt.txt ucbl/6/ucbl.error.fmt.txt
saydo diff ucbl.reject.fmt.txt ucbl/6/ucbl.reject.fmt.txt
saydo diff ucbl.reject.messages.txt ucbl/6/ucbl.reject.messages.txt
saydo diff $diffopt1 $diffopt2 ucbl.report.txt ucbl/6/ucbl.report.txt

