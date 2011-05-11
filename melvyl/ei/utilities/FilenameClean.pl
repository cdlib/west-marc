#! /usr/local/bin/perl -w

# Purpose: Prepare filename for processing with the TWS.
#          This preparation will remove non standard characters
#          from the filename.  These character currently include:
#          single quote: '
#          double quote: "
#         
#          To configure characters, alter the variable @NONSTANDARD
# Input: Filename(s) to be filtered.  Path name will NOT be filtered.
#        --lowercase will additionally translate upper to lowecase.
# Output: New file w/ non standard characters removed.  File content remains
#         unchanged. 
#         Filename remains unchanged if no non standard characters are present.
#         Confirmation of changed files is logged to stdout.
# Assumptions: User has write permissions of the file(s).
# Programmer: [mjr 20020521]
# Usage: FilenameClean.pl --lowercase <file 1> <file 2> ... <file n>

use File::Basename;
use Getopt::Long;
use strict;

my $USAGE =
'Usage: FilenameClean.pl [options] <file 1> <file 2> ... <file n>

<file>
	File(s) to filter.
[options]
	--lowercase
	Will additionally translate filename(s) from upper to lowercase.
  
File(s) will be renamed with no nonstandard characters in filename.
File(s) with no non standard characters remain unaltered.
Non standard characters are:
    single quote (\')
    double quote (")
';

my $LOWERCASE = 0;
die $USAGE unless GetOptions('--lowercase', \$LOWERCASE);
die $USAGE unless @ARGV > 0;

my @NONSTANDARD = qw/' "/;

foreach my $file (@ARGV) {
    die "File does not exist: $file" unless -e $file;
    my $base = basename($file);
    my $dir = dirname($file);
    my $newbase = $base;

    foreach my $nschar (@NONSTANDARD) {
        $newbase =~ s/$nschar//g;
    }
    if ($LOWERCASE) {
        $newbase =~ tr/[A-Z]/[a-z]/;
    }

    unless ("$base" eq "$newbase") {
        rename "$dir/$base", "$dir/$newbase";
        print "Renaming $dir/$base to $dir/$newbase\n";
    }
}

exit;
