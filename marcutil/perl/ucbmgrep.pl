#!/usr/bin/perl
#
# Extract marc records with wonky characters.
#
# Takes a list of file names to process.
#
# Prints the record to standard output and the report to standard error.
# Since these can be quite large they should both be either redirected
# to files, or piped to another process.
#
#
# CVS Tracking info:
#
# $Revision: 1.2 $
# $Author: smcgovrn $
# $Date: 2002/08/16 00:02:48 $
# $Id: ucbmgrep.pl,v 1.2 2002/08/16 00:02:48 smcgovrn Exp $
#
#-------------------------------------------------------------------------------
use strict;
use English;

local $INPUT_RECORD_SEPARATOR = "\035"; # set EOL to 0x1D

my $inrecs = 0;
my $grecs = 0;
#my $pat="GLAD17064824";
#my $pat="\\\[";     # note "[" == "\133" = 0x5B
#my $pat="\\\133";   # note "[" == "\133" = 0x5B
#my $pat="\\\x5B";   # note "[" == "\133" = 0x5B

my @patlist = ("\\xA0", "\\xBE", "\\xC7", "\\xC8", "\\xCA",
               "\\xCD", "\\xD0", "\\xD1", "\\xD3", "\\xD4");

while(<>)
{
    $inrecs++;
    my @hitlist = ();
    #last if $inrecs > 10000;

    foreach my $pat (@patlist)
    {
        if ( /$pat/ )
        {
            push(@hitlist, $pat);
        }
    }

    if (scalar @hitlist > 0)
    {
        my $pats = join('::', @hitlist);
        $grecs++;
        print STDOUT $_;
        print STDERR "input record = $inrecs output record = $grecs patterns = '$pats'\n";
    }
}

print STDERR "File contains $inrecs records\n";
print STDERR "Found $grecs records with some target pattern\n";
