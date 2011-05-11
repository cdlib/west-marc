#!/usr/bin/perl
#
# Count marc records in each benchmark file.
#
# CVS Tracking info:
#
# $Revision: 1.2 $
# $Author: smcgovrn $
# $Date: 2002/08/16 00:02:47 $
# $Id: cntben.pl,v 1.2 2002/08/16 00:02:47 smcgovrn Exp $
#
#-------------------------------------------------------------------------------

use English;
use Strict;

#
# Toggle debug mode.
#
my $debug = undef;

#
# List of benchmark files.
#
my @benfiles = ("dr.la.benchjav.mrc",
                "gl.ucb.benchjav.mrc",
                "ii.gtu.benchjav.mrc",
                "ii.ir.benchjav.mrc",
                "ii.riv.benchjav.mrc",
                "ii.sc.benchjav.mrc",
                "ii.sd.benchjav.mrc",
                "ii.sf.benchjav.mrc",
                "ii.ucdl.benchjav.mrc",
                "lcn.benchjav.mrc",
                "oc.benchjav.mrc",
                "rlin.benchjav.mrc",
                "unload.benchjav.mrc"
               );

my $recs   = 0;
my $totrec = 0;

#
# Iterate over the list of benchmark files.
#
foreach my $fname (@benfiles)
{
    print STDERR "Processing '$fname'\n" if $debug;
    $recs = cntrecs($fname);
    print "File $fname contains $recs records\n";
    $totrecs += $recs;
}

print STDOUT "Total benchmark records counted = $totrecs\n";

exit(0);

#-------------------------------------------------------------------------------
# Count the records in a marc file
#-------------------------------------------------------------------------------
sub cntrecs
{
    my $fname = shift;  # file name
    #my FIN    = undef;  # file handle
    my $recs  = 0;      # record count
    my $debug = undef;  # local debug indicator

    unless (open (FIN, "<$fname"))
    {
        warn("Warning: Could not open benchmark file $fname\n");
        return 0;
    }

    #
    # Set the line end to the marc record separator
    #
    local $INPUT_RECORD_SEPARATOR = "\035"; # set EOL to 0x1D

    while(<FIN>)
    {
        $recs++;
    }

    close FIN;
    return $recs;
}
