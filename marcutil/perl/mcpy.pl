#!/usr/bin/perl
#
# Copy marc records from one file to another. A number of input records
# to skip at the beggining may be specified, and a maximum number of records
# to write may be given. The actual number of record written is reported.
#
# Usage: mcpy.pl [options] <infile> <outfile>
# Options:"
#    --infile  Input file, defaults to STDIN
#    --outfile Outupt file, defaults to STDOUT
#    --skip    Number of input records to skip, defaults to 0
#    --maxout  Maximum number of records to writedefaults to all
#
#    --debug   Turn on debugging messages, use --nodebug to force off.
#    --help    Print this help text.
#
# CVS Tracking info:
#
# $Revision: 1.8 $
# $Author: smcgovrn $
# $Date: 2002/12/13 23:35:14 $
# $Id: mcpy.pl,v 1.8 2002/12/13 23:35:14 smcgovrn Exp $
#
#-------------------------------------------------------------------------------
use strict;
use English;
use Getopt::Long;

$main::debug = 0;                       # Global debug indicator
local $INPUT_RECORD_SEPARATOR = "\035"; # set EOL to 0x1D

#-------------------------------------------------------------------------------
# Usage statement
#-------------------------------------------------------------------------------
my $usage = "Usage: mcpy.pl [options] <infile> <outfile>\n"
. "Options:\n"
. "   --infile  Input file, defaults to STDIN\n"
. "   --outfile Outupt file, defaults to STDOUT\n"
. "   --skip    Number of input records to skip, defaults to 0\n"
. "   --maxout  Maximum number of records to writedefaults to all\n"
. "\n"
. "   --debug   Turn on debugging messages, use --nodebug to force off.\n"
. "   --help    Print this help text.\n"
          . "\n";

#-------------------------------------------------------------------------------
# Control counters
#-------------------------------------------------------------------------------
my $recsin  = 0;
my $recsout = 0;

#-------------------------------------------------------------------------------
# Command line option variables
#-------------------------------------------------------------------------------
my $skip    = 0;                        # Number of input records to skip
my $maxout  = 999999999;                # Maximum number of records to write
my $infile  = undef;                    # Input file name
my $outfile = undef;                    # Output file name
my $help    = undef;                    # display help text
my $debug   = undef;                    # toggle debugging

unless (GetOptions('skip:i'    => \$skip,
                   'maxout:i'  => \$maxout,
                   'infile:s'  => \$infile,
                   'outfile:s' => \$outfile,
                   'debug!'    => \$debug,
                   'help'      => \$help
                  ))
{
    die "Failed to parse command line\n\n$usage";
}

#-------------------------------------------------------------------------------
# Set the debug global if that option is specified.
#-------------------------------------------------------------------------------
$main::debug = $debug if (defined $debug);

#-------------------------------------------------------------------------------
# Get files from remaining command line args if not already set
#-------------------------------------------------------------------------------
$infile  = shift unless $infile;
$outfile = shift unless $outfile;

#-------------------------------------------------------------------------------
# Default files to STDIN and STDOUT if not set
#-------------------------------------------------------------------------------
$infile  = "&STDIN"  unless $infile;
$outfile = "&STDOUT" unless $outfile;

#-------------------------------------------------------------------------------
# When debugging print the options we found
#-------------------------------------------------------------------------------
if ($main::debug)
{
    print STDERR "infile  = '$infile'\n";
    print STDERR "outfile = '$outfile'\n";
    print STDERR "skip    = '$skip'\n";
    print STDERR "maxout  = '$maxout'\n";
}

#-------------------------------------------------------------------------------
# If help was requsted print the usage statement and exit
#-------------------------------------------------------------------------------
if ($help)
{
    print STDERR "$usage";
    exit 0;
}

#-------------------------------------------------------------------------------
# Make sure we have files to process, and we can open them
#-------------------------------------------------------------------------------
unless (open(FIN, "<$infile"))
{
    die "Cannot open input file: $infile\n"
}

unless (open(FOUT, ">$outfile"))
{
    die "Cannot open output file: $outfile\n"
}

#-------------------------------------------------------------------------------
# Copy the marc records
#-------------------------------------------------------------------------------
binmode(FIN);                           # prevent premature eof under windows
binmode(FOUT);                          # prevent spurious character translations
while (<FIN>)
{
    $recsin++;
    print STDERR "Read record #$recsin\n" if $main::debug;
    next unless $recsin > $skip;
    print FOUT $_;
    $recsout++;
    print STDERR "Wrote record #$recsout of $maxout\n" if $main::debug;
    last if $recsout >= $maxout;
}

#-------------------------------------------------------------------------------
# Close the files and report the number of records copied
#-------------------------------------------------------------------------------
close FIN;
close FOUT;

print STDERR "Copied $recsout records from $infile to $outfile\n";
exit 0;
