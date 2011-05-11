#! /usr/bin/perl
#
# Print the rejected records messages from a marc conversion log file.
# Usage: prtrej.pl <infile> <outfile>
#
# If outfile is not specified messages are written to STDOUT.
# If infile is not specified STDIN is use as the input source.
#
# CVS Tracking info:
#
# $Revision: 1.4 $
# $Author: smcgovrn $
# $Date: 2002/12/13 23:27:39 $
# $Id: prtrej.pl,v 1.4 2002/12/13 23:27:39 smcgovrn Exp $
#
#-------------------------------------------------------------------------------

use strict;
use English;
use Getopt::Long;

$main::debug = 0;

#-------------------------------------------------------------------------------
# Handy usage statement.
#-------------------------------------------------------------------------------
my $usage = "\n"
          . "Usage: prtrej.pl <infile> <outfile>\n"
          . "  If outfile is not specified messages are written to STDOUT.\n"
          . "  If infile is not specified STDIN is use as the input source.\n"
          . "\n";

my $help = undef; # help requested indicator

#-------------------------------------------------------------------------------
# Get the --<thingy> options. Barf if any unknown options are specified.
#-------------------------------------------------------------------------------
unless (GetOptions('help' => \$help))
{
    die "Failed to parse command line\n$usage";
}

#-------------------------------------------------------------------------------
# If --help was specified print the usage message and exit.
#-------------------------------------------------------------------------------
if ($help)
{
    print STDERR "$usage";
    exit(0);
}

#-------------------------------------------------------------------------------
# Get the input and output file names. Default to STDIN and STDOUT.
#-------------------------------------------------------------------------------
my $inname  = shift || "&STDIN";
my $outname = shift || "&STDOUT";

#-------------------------------------------------------------------------------
# Open the input file.
#-------------------------------------------------------------------------------
print STDERR "opening input file $inname\n" if $main::debug;
unless (open(INFILE, "<$inname") )
{
    die "Failed to open '$inname' for reading\n";
}

#-------------------------------------------------------------------------------
# Open the output file.
#-------------------------------------------------------------------------------
print STDERR "opening output file $outname\n" if $main::debug;
unless (open(OUTFILE, ">$outname") )
{
    die "Failed to open '$outname' for writing\n";
}

#-------------------------------------------------------------------------------
# Extract the reject messages from the log file.
#-------------------------------------------------------------------------------
my $reccnt = 0;
my $rejcnt = 0;
while (<INFILE>)
{
    $reccnt++;
    if (/(Record Rejected:.*)$/)
    {
        $rejcnt++;
        print OUTFILE "$1\n";
    }
}

print STDERR "Found $rejcnt reject messages of $reccnt log lines\n" if $main::debug;

close INFILE;
close OUTFILE;
exit (0);
