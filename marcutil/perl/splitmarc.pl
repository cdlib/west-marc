#!/usr/bin/perl -w
#
# Split a file of marc data in to several smaller files.
#
# Takes an input file and a number of records per output file
# as parameters.
#
# Usage: splitmarc.pl infile outsize
#
# This script was rewritten for rev. 1.3 to simply read the file and to not
# use MARC.pm, which changes resulted in an order of magnitide performance
# increase on large files.
#
# CVS Tracking info:
#
# $Revision: 1.4 $
# $Author: smcgovrn $
# $Date: 2002/10/22 21:11:29 $
# $Id: splitmarc.pl,v 1.4 2002/10/22 21:11:29 smcgovrn Exp $
#
#-------------------------------------------------------------------------------
use strict;
use English;

$main::debug = 0;                       # Global debug indicator
local $INPUT_RECORD_SEPARATOR = "\035"; # set EOL to 0x1D

#-------------------------------------------------------------------------------
# Handy usage string in case the user requests --help
#-------------------------------------------------------------------------------
my $usage = "\n"
          . "Usage: splitmarc.pl infile outsize (max records per output file)\n"
          . "\n";

#-------------------------------------------------------------------------------
# Make sure we have some arguments
#-------------------------------------------------------------------------------
unless (@ARGV)
{
    print STDOUT $usage;
    exit(0);
}

#-------------------------------------------------------------------------------
# Get our run parameters
#-------------------------------------------------------------------------------
my $infile = shift;  # the marc file to split
my $size   = shift;  # the number of records per output file

#-------------------------------------------------------------------------------
# Handle -h or --help
#-------------------------------------------------------------------------------
if ($infile eq '-h' || $infile eq '--help')
{
    print STDOUT $usage;
    exit(0);
}

#-------------------------------------------------------------------------------
# make sure we have a file to process
# this will fail if larger than 4G so try to open the file if -f fails
#-------------------------------------------------------------------------------
unless (-f "$infile" && open(FIN, "<$infile"))
{
    die "Cannot open input file: $infile\n"
}

#-------------------------------------------------------------------------------
# Check that the size parameter is a positive integer
#-------------------------------------------------------------------------------
unless ($size =~ /^\d+$/ && $size > 0)
{
    die "Size parameter must be a positive integer: size = '$size'\n";
}

#-------------------------------------------------------------------------------
# Set up our runtime variables.
#-------------------------------------------------------------------------------
my $fcount  = 0;                        # the output file count
my $recsin  = 0;                        # number of records read
my $recsout = $size;                    # init to chunk size to get first output open
my $outfile = undef;                    # output file name

#-------------------------------------------------------------------------------
# Read the input file, copy the records to the output, opening a new output
# file whenever our size threshhold is reached.
#-------------------------------------------------------------------------------
while (<FIN>)
{
    $recsin++;

    if ($recsout >= $size)
    {
        $recsout = 0;
        close FOUT if $outfile;
        $fcount++;
        my $ext = sprintf("p%08d", $fcount);
        $outfile = $infile . ".$ext";
        unless (open(FOUT, ">$outfile"))
        {
            die "Cannot open output file: $outfile\n"
        }
    }

    print FOUT $_;
    $recsout++;
}

close FIN;
close FOUT if $outfile;
unlink $outfile if ($outfile && -z "$outfile"); # remove last file if empty
print STDERR "Split $recsin records into $fcount files\n";

exit(0);
