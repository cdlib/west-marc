#!/usr/bin/perl
#
# Usage: mcnt.pl file1 file2...
#
# Count marc records in a file. This depends entirely upon the marc records
# being properly terminated with 0x1D, and makes no use of the byte count
# in the leader. If the file contains records that lack a record terminator
# character, the count will be low.
#
# Processes all files on the command line, or STDIN if no files specified.
# A count for each file is written to STDOUT and a total for all files is provided.
#
# CVS Tracking info:
#
# $Revision: 1.7 $
# $Author: smcgovrn $
# $Date: 2002/12/13 23:23:34 $
# $Id: mcnt.pl,v 1.7 2002/12/13 23:23:34 smcgovrn Exp $
#
#-------------------------------------------------------------------------------
use strict;
use English;

$main::debug = 0;                       # Global debug indicator
local $INPUT_RECORD_SEPARATOR = "\035"; # set EOL to 0x1D

my $recs    = 0;                        # Number of records in the current file
my $totrecs = 0;                        # Total records for all files
my $fcount  = 0;                        # Number of file processed
my $infile  = undef;                    # The current file name

unshift(@ARGV, "&STDIN") unless ($ARGV[0]);

while ($infile = shift)
{
    unless (open(FIN, "<$infile"))
    {
        die "Cannot open input file: $infile\n"
    }
    $fcount++;
    binmode(FIN);                       # prevent premature eof under windows
    while (<FIN>)
    {
        $recs++;
        print STDERR "Record #$recs has length ", length($_), "\n"
        if $main::debug && $recs < 12;
    }
    close (FIN);
    print "File $infile contains $recs records\n";
    $totrecs += $recs;
    $recs = 0;
}

print "Processed $fcount ", ($fcount == 1 ? "file" :"files"), " containing $totrecs records\n";
