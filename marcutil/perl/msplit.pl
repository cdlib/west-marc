#!/usr/bin/perl -w
#
# Split a file of marc data in to several smaller files.
#
# Takes an input file and a number of records per output file
# as parameters.
#
# Usage: msplit.pl [options] infile outsize (max records per output file)
#  Options:
#    --help  print this usage message
#    --debug turn on debugging messages, use --nodebug to force off
#
# This script was rewritten for rev. 1.3 to simply read the file and to not
# use MARC.pm, which changes resulted in an order of magnitide performance
# increase on large files.
#
# CVS Tracking info:
#
# $Revision: 1.1 $
# $Author: smcgovrn $
# $Date: 2002/10/22 21:23:56 $
# $Id: msplit.pl,v 1.1 2002/10/22 21:23:56 smcgovrn Exp $
#
#-------------------------------------------------------------------------------
use strict;
use English;
use Getopt::Long;

$main::debug = 0;                       # Global debug indicator
local $INPUT_RECORD_SEPARATOR = "\035"; # set EOL to 0x1D

#-------------------------------------------------------------------------------
# Handy usage string in case the user requests --help
#-------------------------------------------------------------------------------
my $usage = "\n"
          . "Usage: msplit.pl [options] infile outsize (max records per output file)\n"
          . " Options:\n"
          . "   --help  print this usage message\n"
          . "   --debug turn on debugging messages, use --nodebug to force off\n"
          . "\n";


#-------------------------------------------------------------------------------
# Parse the command line options
#-------------------------------------------------------------------------------
my $help  = undef;
my $debug = undef;

unless (GetOptions('help'   => \$help,
                   'debug!' => \$debug))
{
    die "Failed to parse command line\n";
}

if ($main::debug)
{
    print STDERR ("options: help = ", (defined $help ? "$help" : "undef"),
                  " debug = ", (defined $debug ? "$debug" : "undef"), "\n");
}

#-------------------------------------------------------------------------------
# Set the debug global if that option is specified.
#-------------------------------------------------------------------------------
$main::debug = $debug if (defined $debug);

#-------------------------------------------------------------------------------
# Handle --help or -h
#-------------------------------------------------------------------------------
if ($help)
{
    print STDERR "$usage";
    exit(0);
}

#-------------------------------------------------------------------------------
# Make sure we have a file to process
#-------------------------------------------------------------------------------
unless (scalar(@ARGV) > 1)
{
    print STDERR "Incorrect number of arguments: ", scalar(@ARGV), "\n$usage";
    exit(0);
}

#-------------------------------------------------------------------------------
# Get our run parameters
#-------------------------------------------------------------------------------
my $infile = shift;  # the marc file to split
my $size   = shift;  # the number of records per output file

#-------------------------------------------------------------------------------
# make sure we have a file to process
# this will fail if larger than 4G so try to open the file if -f fails
#-------------------------------------------------------------------------------
unless (-f "$infile" && open(FIN, "<$infile"))
{
    die "Cannot open input file: $infile\n"
}

#-------------------------------------------------------------------------------
# Check that the size parameter has been specified
#-------------------------------------------------------------------------------
unless (defined $size)
{
    die "Size parameter not specified. Plesee specify a positive integer.\n$usage";
}

#-------------------------------------------------------------------------------
# Check that the size parameter is a positive integer
#-------------------------------------------------------------------------------
unless ($size =~ /^\d+$/ && $size > 0)
{
    die "Size parameter must be a positive integer: size = '$size'\n$usage";
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
        print STDERR "Next output file = $outfile\n" if $main::debug;
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
