#!/usr/local/bin/perl

# Programmer:	Debra Bartling
# Date: 	7/2/02
# Usage:	clean_tws.pl [debug]
# Description:	Archive and cleanup for TWS. Based on cleanup.pl. 
# Adds support for archiving before deletion and deleting directories.
#
# Usage: cleanup.pl --dir=directory [--days=n] [--archive] [--debug]
# 
# Delete files in a directory based on age or number of files. 
#     Directory = the directory to cleanup 
#     Days = number of days to keep files (default 0) 
#     Archive = archive file or directory before deleting
#     Debug = turn on debugging 
# 
# Note that deleting a file requires write permission in the parent directory.
# Permissions on the file itself do not matter. 

use Getopt::Long;		# For command line options
use File::stat;			# To get mtime for files
use Time::Local;		# For conversion to and from epoch seconds
use strict;

my $debug = 0;			# Non-zero prints debug messages
my %filelist = ();		# Hash of files and mtime
my $date = time();

my $days = 0;
my $files = 0;
my $dir = "";
my $debug = 0;
my $archive = 0;
GetOptions (	"days=i"   => \$days,
                "dir=s"    => \$dir,
		"archive"    => \$archive, 
		"debug"    => \$debug)  
	|| die "Usage: $0 [--days=n] [--dir=s] [--archive] [--debug] \n";

print "Days: $days\n" if $debug;
print "Directory: $dir\n" if $debug;
print "Debug: $debug\n" if $debug;

# Convert days to seconds
my $sec = $days * 24 * 60 * 60;

print scalar(localtime($date)), " \n" if $debug;
print "Seconds: $sec \n" if $debug;

# Get the list of files in the directory and their time last modified 
# Files over the age limit are candidates for deletion

print "Checking for files over the age limit ...\n" if $debug;
opendir(DIR,$dir) || die "Cannot open $dir: $! \n";
while (defined (my $file=readdir(DIR)) ){

    # Skip . (the directory) and .. (the parent)
    next if $file=~ /^\.\.?$/;

    my $fullname = "$dir/$file";
    my $inode = stat($fullname);
    my $mtime = $inode->mtime;
    print "$fullname ", scalar(localtime($mtime)), " \n" if $debug;
    my $age = $date - $mtime;
    print "Age in seconds: $age\n" if $debug; 

    if ( $age > $sec ) {
        print "File is a candidate for deletion. \n" if $debug;
        # Test that this is a plain file : underscore checks last stat call
        if ( -f _ ) {
            $filelist{$fullname} = $age;
        }
        else {
            warn "$fullname is not a plain file \n";
        }
    }
}  
closedir(DIR);

# Sort the files on the list in order of age, newest first
# Skip the number of files to keep and delete the others

print "Checking for files over the number to keep ...\n" if $debug;
my $count = 0;
foreach my $item (sort { $filelist{$a} <=> $filelist{$b} } keys %filelist) {
    print "Processing item: $item \n" if $debug;
    $count++;
    if ( $count > $files ) {
       print "File will be deleted age: $filelist{$item} \n" if $debug; 
       print "Deleting $item \n";
       unlink($item) || warn "Cannot delete $item: $!\n";
    }
}
