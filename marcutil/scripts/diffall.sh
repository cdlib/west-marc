#! /bin/bash
#
# diff files of the format <source>.convert.fmt.txt in the current
# directory against those in a subdirectory <source>/base.
#
# This crude little script has been superceded by the diffben.pl script.
#-------------------------------------------------------------------------------

srclist="chs gtu lbl ucb ucbl ucdl uci ucla ucr ucsc ucsd ucsf"

for src in $srclist;do
	rmcr $src.convert.fmt.txt
	rmcr $src/base/$src.convert.fmt.txt
	diff $src.convert.fmt.txt $src/base/$src.convert.fmt.txt > $src.diff.out
done
