#! /usr/bin/perl
#
# Make the production data directory tree.
# Since the production runconvert.pl makes its own missing directories,
# this script is really only useful as a way to build a directory tree
# for testing, and maybe not even then.
#
# CVS Tracking info:
#
# $Revision: 1.4 $
# $Author: smcgovrn $
# $Date: 2002/11/12 22:35:00 $
# $Id: mkpddir.pl,v 1.4 2002/11/12 22:35:00 smcgovrn Exp $
#
#-------------------------------------------------------------------------------

use strict;
use English;
use Cwd qw(chdir cwd getcwd);

my $basedir = shift;

unless ($basedir && -d $basedir)
{
    print STDERR "Usage mkpddir.pl <base_directory>/n";
    exit 4;
}

chdir "$basedir";

if (-e "ei")
{
    if (! -d "ei")
    {
        print STDERR "Error: '$basedir/ei' exists, but is not a directory\n";
        exit 4;
    }
}
else
{
    mkdir "ei";
}

chdir "ei";

my @sources = ("drcsl", "drema", "drftv", "drla", "evget", "evser", "evsub", "evsum",
               "exsb", "exucd", "glucb", "grclp", "iidla", "iigtu", "iiir", "iiriv",
               "iisc", "iisd", "iisf", "lcnam", "lcsub", "lsgia", "lslll", "occsu",
               "oclc", "rlblt", "rlchs", "rllbl", "sisu", "uncas", "uncrl", "undla",
               "uneer", "ungia", "unhas", "unigs", "units", "unmtz", "unwrc"
              );

foreach my $src (@sources)
{
    mksrcdirs($src);
}

exit 0;


sub mksrcdirs
{
    my $srcdir = shift;
    mkdir "$srcdir";
    mkdir "$srcdir/eierror";
    mkdir "$srcdir/eiin";
    mkdir "$srcdir/eilog";
    mkdir "$srcdir/eiout";
    mkdir "$srcdir/eiprocessed";
    mkdir "$srcdir/eireject";
    mkdir "$srcdir/eireport";
    mkdir "$srcdir/eistats";
    mkdir "$srcdir/eitmp";
    return;
}
