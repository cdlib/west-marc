#!/usr/bin/perl
#
# Find marc records in a file that contain particular characters.
# Each entry in an array of regular expressions is matched against
# each marc record in the file. If the record matches it is written
# to STDOUT and a line listing input count, output count, and the
# list of expressions it matched, is printed to STDERR.
#
# This is a crude little utility. In particular the pattern list
# is hard coded, so it will have to be changed to suit the user.
# It was created to serve a specific need, and lives on more in
# the guise of an example, than as a full fledged tool.
#
# CVS Tracking info:
#
# $Revision: 1.1 $
# $Author: smcgovrn $
# $Date: 2002/08/16 00:02:47 $
# $Id: mgrep.pl,v 1.1 2002/08/16 00:02:47 smcgovrn Exp $
#
#-------------------------------------------------------------------------------

use strict;
use English;

local $INPUT_RECORD_SEPARATOR = "\035"; # set EOL to 0x1D
my $inrecs = 0;
my $grecs = 0;
#my $pat="GLAD17064824";
#my $pat="\\[";       # note "[" == "\133" = 0x5B
#my $pat="\\133";     # note "[" == "\133" = 0x5B
#my $pat="\\x5B";   # note "[" == "\133" = 0x5B
#my $pat="\\xE2";   # note "[" == "\133" = 0x5B
my @patlist = ("\\xD0", "\\xE2");

while(<>)
{
    $inrecs++;
    #last if $inrecs > 100;

    my @hitlist = ();

    foreach my $pat (@patlist)
    {
        if ( /$pat/ )
        {
            push(@hitlist, $pat);
        }
    }

#   print "hitlist size = ", scalar @hitlist, "\n";

    if (scalar @hitlist > 0)
    {
        my $pats = join('::', @hitlist);
        $grecs++;
        print STDOUT $_;
        print STDERR "input record = $inrecs output record = $grecs patterns = '$pats'\n";
    }
#   if ( /$pat/ )
#   {
#       $grecs++;
#       print STDOUT $_;
#   }
}

print STDERR "File contains $inrecs records\n";
print STDERR "Found $grecs records with some target pattern\n";
