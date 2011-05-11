#! /usr/bin/perl -w
#
# Substitute strings across a list of files.
#
# Usage: ss.pl [options] oldstring newstring filenames...
#  Options:
#    --help  print this usage message
#    --debug turn on debugging messages, use --nodebug to force off
#
#
# CVS Tracking info:
#
# $Revision: 1.2 $
# $Author: smcgovrn $
# $Date: 2002/12/13 23:27:39 $
# $Id: ss.pl,v 1.2 2002/12/13 23:27:39 smcgovrn Exp $
#
#-------------------------------------------------------------------------------
use strict;
use English;
use Getopt::Long;

$main::debug = 0;  # Global debug indicator

#-------------------------------------------------------------------------------
# Handy usage string in case the user requests --help
#-------------------------------------------------------------------------------
my $usage = "\n"
          . "Usage: ss.pl [options]oldstring newstring filenames...\n"
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
# Handle --help or -h.
#-------------------------------------------------------------------------------
if ($help)
{
    print STDERR "$usage";
    exit(0);
}

print STDERR "DEBUG: ARGV = ", (join(':',@ARGV)), "\n" if $main::debug;

#-------------------------------------------------------------------------------
# Make sure we have enough arguments.
#-------------------------------------------------------------------------------
unless (scalar(@ARGV) > 2)
{
    print STDOUT "At least 3 must be arguments specified\n$usage";
    exit(0);
}

my $oldstring = shift;
my $newstring = shift;

print STDERR "DEBUG: oldstring = $oldstring\n" if $main::debug;
print STDERR "DEBUG: newstring = $newstring\n" if $main::debug;

#-------------------------------------------------------------------------------
# Assemble the command to execute.
#-------------------------------------------------------------------------------
my @command = ();
push(@command, "perl");
push(@command, "-w");                   # issue warnings
push(@command, "-i.bak");               # edit in place, with backups using .bak extension
push(@command, "-p");                   # print lines processed
push(@command, "-e");                   # get script from the command line
push(@command, "\"s/$oldstring/$newstring/g;\""); # perl code to execute
push(@command, @ARGV);                  # files to act upon
my $cmd = join(' ',@command);           # the full command

print STDERR "DEBUG: command = $cmd\n" if $main::debug;

#-------------------------------------------------------------------------------
# Note: the "system(@command)" syntax works under with ActiveState,
# but not Cygwin or Unix.
# However, the "system($cmd)" syntax seems to work everywhere.
#-------------------------------------------------------------------------------
my $rc = system($cmd);
print "return code = $rc\n";
exit($rc);
