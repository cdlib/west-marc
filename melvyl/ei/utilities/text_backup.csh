#!/bin/csh

# Programmer: Debra Bartling
# Date: 6/25/02
# Description: Program to export Tivoli Workload Scheduler databases to text
# format which can be used for reference and to restore corrupted databases. 
# See TWS Reference Guide Appendix A. Runs through the job scheduler as part
# of a TWS stream. 

# Set up the Tivoli environment
source /etc/Tivoli/setup_env.csh

# Define the directory where files are stored
set CDLDIR = "/opt/Tivoli/tws/cdl/files"

# Delete the backup copies of the files
echo "Removing old backups from $CDLDIR" 
rm $CDLDIR/*.bak

# Create new backup copies from previous version
echo "Backing up previous versions of text files in $CDLDIR" 
foreach file ($CDLDIR/*) 
	echo $file
	mv $file $file.bak
end

# Use composer to create the new text backups in CDLDIR
echo "Using composer to create new text files in $CDLDIR" 
composer create $CDLDIR/cpu.txt from cpu=\@
composer create $CDLDIR/jobs.txt from jobs=\@\#\@
composer create $CDLDIR/sched.txt from sched=\@\#\@
composer create $CDLDIR/calendars.txt from calendars
composer create $CDLDIR/parms.txt from parms
composer create $CDLDIR/prompts.txt from prompts
composer create $CDLDIR/resources.txt from resources

# If we ever add NT users, add this line (generates an error if no users exist)
# composer create users.txt from users

echo "Contents of $CDLDIR"
ls -l $CDLDIR

# Archive data to TSM
tar -cvf $CDLDIR/text_backup.tar $CDLDIR/cpu.txt $CDLDIR/jobs.txt $CDLDIR/sched.txt $CDLDIR/calendars.txt $CDLDIR/parms.txt  $CDLDIR/prompts.txt $CDLDIR/resources.txt
dsmc archive $CDLDIR/text_backup.tar
