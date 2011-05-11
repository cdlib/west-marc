#!/usr/bin/perl
#
# Count marc delete records in a file. This depends entirely upon the marc
# records being properly terminated with 0x1D, and makes no use of the byte
# count in the leader. If the file contains records that lack a record
# terminator character, the count will be low.
#
# Only the status byte in the leader is inspected, so deletes specified
# elsewhere are ignored.
#
# If more that one file is specifed on the command line a count is produced
# for each file.
#
# CVS Tracking info:
#
# $Revision: 1.3 $
# $Author: smcgovrn $
# $Date: 2002/09/24 19:00:25 $
# $Id: mdcnt.pl,v 1.3 2002/09/24 19:00:25 smcgovrn Exp $
#
#-------------------------------------------------------------------------------
use strict;
use English;

$main::debug = 0;                       # Global debug indicator
local $INPUT_RECORD_SEPARATOR = "\035"; # set EOL to 0x1D

my $recs    = 0;                        # Number of records in the current file
my $delcnt  = 0;                        # Number of delete records
my $totdel  = 0;                        # Total number of deletes across all files
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
        $delcnt++ if (length($_) > 5 && substr($_,5,1) eq 'd' );
    }
    close (FIN);
    print "File $infile contains $delcnt deletes out of $recs records\n";
    $totrecs += $recs;
    $totdel  += $delcnt;
    $recs     = 0;
    $delcnt   = 0;
}

print ("Processed $fcount ", ($fcount == 1 ? "file" :"files"),
       " containing $totdel deleted out of $totrecs records\n");
