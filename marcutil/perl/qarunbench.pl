#!/usr/bin/perl -w
#
# Run the marc convert benchmark tests.
#
# Usage: qarunbench.pl <all | site1 site2 ...>
#
# Where site is in the list:
#   chs, gtu, lcn, lbl, oclc, ucb, ucbl, ucd, ucdl,
#   uci, ucla, ucr, ucsb, ucsc, ucsd, ucsf, ,csl, unload
#
# This script assumes that an approriate config file exists for each site
# and has a name of the form 'site.config.txt', e.g. 'chs.config.txt'.
#
# CVS Tracking info:
#
# $Revision: 1.30 $
# $Author: aleph16 $
# $Date: 2010/04/12 22:28:57 $
# $Id: qarunbench.pl,v 1.30 2010/04/12 22:28:57 aleph16 Exp $
#
#-------------------------------------------------------------------------------

use strict;
use Config;
use English;
use File::Basename;
use File::Copy;
use Cwd qw(chdir cwd getcwd);
use IO::Handle;

$main::debug = 0;    # set to true to turn on debug mode

#-------------------------------------------------------------------------------
# Set autoflush now, before we do any printing.
#-------------------------------------------------------------------------------
autoflush STDERR 1;
autoflush STDOUT 1;

#-------------------------------------------------------------------------------
# Handy usage string in case the user requests --help.
#-------------------------------------------------------------------------------
my $usage = "\n"
          . "Usage: qarunbench.pl <all | site1 site2 ...>\n"
          . "\n"
          . "Where site is in the list:\n"
          . "  chs, gtu, lcn, lbl, oclc, ucb, ucbl, ucdl,\n"
          . "  uci, ucla, ucr, ucsb, ucsc, ucsd, ucsf, csl, unload\n"
          . "\n"
          . "This script assumes that approriate config files exists for each site\n"
          . "and has a names of the form:\n"
          . "  site.config.txt\n"
          . "  site.mf.config.txt\n"
          . "  site.mf.error.config.txt\n"
          . "  site.mf.reject.config.txt\n"
          . "\n";

#-------------------------------------------------------------------------------
# Default our required environment variables.
#-------------------------------------------------------------------------------
$ENV{'MB_HOME'}          = '.'                                        unless $ENV{'MB_HOME'};
$ENV{'MB_PERL'}          = "$ENV{'MB_HOME'}/perl"                     unless $ENV{'MB_PERL'};
$ENV{'MB_LOG4JPROPSDIR'} = "$ENV{'MB_HOME'}/marc/cvsroot/marclib/cdl" unless $ENV{'MB_LOG4JPROPSDIR'};
$ENV{'LOCTBL'}           = "tab_locations.txt"                        unless $ENV{'LOCTBL'};
$ENV{'SPTBL'}            = "shared_print_table.txt"                   unless $ENV{'SPTBL'};

#-------------------------------------------------------------------------------
# Fix '\' in directory variables on windows.
#-------------------------------------------------------------------------------
$ENV{'MB_HOME'}          = cleanPath($ENV{'MB_HOME'})          if $ENV{'MB_HOME'};
$ENV{'MB_PERL'}          = cleanPath($ENV{'MB_PERL'})          if $ENV{'MB_PERL'};
$ENV{'MB_LOG4JPROPSDIR'} = cleanPath($ENV{'MB_LOG4JPROPSDIR'}) if $ENV{'MB_LOG4JPROPSDIR'};
$ENV{'LOCTBL'}           = cleanPath($ENV{'LOCTBL'})           if $ENV{'LOCTBL'};
$ENV{'SPTBL'}            = cleanPath($ENV{'SPTBL'})            if $ENV{'SPTBL'};

#-------------------------------------------------------------------------------
# Setup our classpath - The support libraries and jdk location are set there,
# so if the list of support libraries changes the setClassPath subroutine
# will need to be changed.
#-------------------------------------------------------------------------------
my $basecp = setClassPath();

#-------------------------------------------------------------------------------
# Set up the log4j properties file variables.
#-------------------------------------------------------------------------------
my $log4jPropsIn   = "$ENV{'MB_LOG4JPROPSDIR'}/log4j.devl.base.props";
my $log4jPropsOut  = "$ENV{'MB_LOG4JPROPSDIR'}/log4j.devl.temp.props";
my $log4jPropsName = basename($log4jPropsOut);
my $log4jPropsDir  = dirname($log4jPropsOut);

#-------------------------------------------------------------------------------
# Set the names of the marc converter jar files.
#-------------------------------------------------------------------------------
my $utiljar = "$ENV{'MB_HOME'}/marc/build/lib/marcutil.jar";
my $convjar = "$ENV{'MB_HOME'}/marc/build/lib/marcconvert.jar";

#-------------------------------------------------------------------------------
# Set the final runtime classpath.
# $cpsep and $hd are a couple of hacks so we can run under Cygwin.
#-------------------------------------------------------------------------------
my $cpsep = ("$Config{'osname'}" eq 'cygwin' ? ";" : "$Config{'path_sep'}");
my $hd = ("$Config{'osname'}" eq 'cygwin' ? "$ENV{'HOMEDRIVE'}" : "");
my $cp = join("$cpsep", ("$hd$log4jPropsDir",
                         "$hd$convjar",
                         "$hd$utiljar",
                         "$basecp"));

print STDERR "\nbasecp = $basecp\n" if $main::debug;
print STDERR "cpsep = '$cpsep'\n" if $main::debug;
print STDERR "hd = '$hd'\n" if $main::debug;
print STDERR "cp = $cp\n\n" if $main::debug;

my $rundir = "$ENV{MB_HOME}/marc/run/test";
my $loctab = "$ENV{'LOCTBL'}";
my $sptab  = "$ENV{'SPTBL'}";

#-------------------------------------------------------------------------------
# Table of commands to run for each site.
#-------------------------------------------------------------------------------
my %cmdtable = (
                'ber'  => [["java", "-Dlog4j.configuration=$log4jPropsName",
                            "-Dconfig=ber.config.txt",  "-cp", "$cp",
                            "org.cdlib.marcconvert.run.RunBER"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/prtrej.pl",
                            "chs.convert.log", "ber.reject.messages.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "ber.mf.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "ber.mf.error.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "ber.mf.reject.config.txt"]],
                'chs'  => [["java", "-Dlog4j.configuration=$log4jPropsName",
                            "-Dconfig=chs.config.txt",  "-cp", "$cp",
                            "org.cdlib.marcconvert.run.RunCHS21"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/prtrej.pl",
                            "chs.convert.log", "chs.reject.messages.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "chs.mf.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "chs.mf.error.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "chs.mf.reject.config.txt"]],
           "enla-merge" => [["java", "-Dlog4j.configuration=$log4jPropsName",
                            "-Dconfig=enla-merge.config.txt",  "-cp", "$cp",
                            "org.cdlib.marcconvert.run.RunENLAMerge"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/prtrej.pl",
                            "enla-merge.convert.log", "enla-merge.reject.messages.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "enla-merge.mf.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "enla-merge.mf.error.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "enla-merge.mf.reject.config.txt"]],
                "enla" => [["java", "-Dlog4j.configuration=$log4jPropsName",
                            "-Dconfig=enla.config.txt",  "-cp", "$cp",
                            "org.cdlib.marcconvert.run.RunENLA"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/prtrej.pl",
                            "enla.convert.log", "enla.reject.messages.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "enla.mf.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "enla.mf.error.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "enla.mf.reject.config.txt"]],
                "gtu"  => [["java", "-Dlog4j.configuration=$log4jPropsName",
                            "-Dconfig=gtu.config.txt",  "-cp", "$cp",
                            "org.cdlib.marcconvert.run.RunGTU"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/prtrej.pl",
                            "gtu.convert.log", "gtu.reject.messages.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "gtu.mf.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "gtu.mf.error.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "gtu.mf.reject.config.txt"]],
                "lcn"  => [["java", "-Dlog4j.configuration=$log4jPropsName",
                            "-Dconfig=lcn.config.txt",  "-cp", "$cp",
                            "org.cdlib.marcconvert.run.RunLCN"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/prtrej.pl",
                            "lcn.convert.log", "lcn.reject.messages.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "lcn.mf.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "lcn.mf.error.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "lcn.mf.reject.config.txt"]],
                "lbl"  => [["java", "-Dlog4j.configuration=$log4jPropsName",
                            "-Dconfig=lbl.config.txt",  "-cp", "$cp",
                            "org.cdlib.marcconvert.run.RunLBL21"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/prtrej.pl",
                            "lbl.convert.log", "lbl.reject.messages.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "lbl.mf.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "lbl.mf.error.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "lbl.mf.reject.config.txt"]],
                "mer"  => [["java", "-Dlog4j.configuration=$log4jPropsName",
                            "-Dconfig=mer.config.txt",  "-cp", "$cp",
                            "org.cdlib.marcconvert.run.RunUCM"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/prtrej.pl",
                            "mer.convert.log", "mer.reject.messages.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "mer.mf.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "mer.mf.error.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "mer.mf.reject.config.txt"]],
                "oclc"  => [["java", "-Dlog4j.configuration=$log4jPropsName",
                            "-Dconfig=oclc.config.txt",  "-cp", "$cp",
                             "org.cdlib.marcconvert.run.RunOCLC"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/prtrej.pl",
                            "oclc.convert.log", "oclc.reject.messages.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "oclc.mf.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "oclc.mf.error.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "oclc.mf.reject.config.txt"]],
                "ucb"  => [["java", "-Dlog4j.configuration=$log4jPropsName",
                            "-Dconfig=ucb.config.txt",  "-cp", "$cp",
                            "org.cdlib.marcconvert.run.RunUCB"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/prtrej.pl",
                            "ucb.convert.log", "ucb.reject.messages.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "ucb.mf.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "ucb.mf.error.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "ucb.mf.reject.config.txt"]],
                "ucbl" => [["java", "-Dlog4j.configuration=$log4jPropsName",
                            "-Dconfig=ucbl.config.txt",  "-cp", "$cp",
                            "org.cdlib.marcconvert.run.RunUCBL21"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/prtrej.pl",
                            "ucbl.convert.log", "ucbl.reject.messages.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "ucbl.mf.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "ucbl.mf.error.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "ucbl.mf.reject.config.txt"]],
                "ucd"  => [["java", "-Dlog4j.configuration=$log4jPropsName",
                            "-Dconfig=ucd.config.txt",  "-cp", "$cp",
                            "org.cdlib.marcconvert.run.RunUCD"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/prtrej.pl",
                            "ucd.convert.log", "ucd.reject.messages.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "ucd.mf.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "ucd.mf.error.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "ucd.mf.reject.config.txt"]],
                "ucdl" => [["java", "-Dlog4j.configuration=$log4jPropsName",
                            "-Dconfig=ucdl.config.txt",  "-cp", "$cp",
                            "org.cdlib.marcconvert.run.RunUCDL"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/prtrej.pl",
                            "ucdl.convert.log", "ucdl.reject.messages.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "ucdl.mf.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "ucdl.mf.error.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "ucdl.mf.reject.config.txt"]],
                "uci"  => [["java", "-Dlog4j.configuration=$log4jPropsName",
                            "-Dconfig=uci.config.txt",  "-cp", "$cp",
                            "org.cdlib.marcconvert.run.RunUCIR"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/prtrej.pl",
                            "uci.convert.log", "uci.reject.messages.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "uci.mf.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "uci.mf.error.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "uci.mf.reject.config.txt"]],
                #"ucla" => [["java", "-Dlog4j.configuration=$log4jPropsName",
                            #"-Dconfig=ucla.config.txt",  "-cp", "$cp",
                            #"org.cdlib.marcconvert.run.RunUCLA"],
                           #["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/prtrej.pl",
                            #"ucla.convert.log", "ucla.reject.messages.txt"],
                           #["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            #"ucla.mf.config.txt"],
                           #["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            #"ucla.mf.error.config.txt"],
                           #["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            #"ucla.mf.reject.config.txt"]],
                "ucr"  => [["java", "-Dlog4j.configuration=$log4jPropsName",
                            "-Dconfig=ucr.config.txt",  "-cp", "$cp",
                            "org.cdlib.marcconvert.run.RunUCR"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/prtrej.pl",
                            "ucr.convert.log", "ucr.reject.messages.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "ucr.mf.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "ucr.mf.error.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "ucr.mf.reject.config.txt"]],
                "ucsb" => [["java", "-Dlog4j.configuration=$log4jPropsName",
                            "-Dconfig=ucsb.config.txt",  "-cp", "$cp",
                            "org.cdlib.marcconvert.run.RunUCSB"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/prtrej.pl",
                            "ucsb.convert.log", "ucsb.reject.messages.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "ucsb.mf.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "ucsb.mf.error.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "ucsb.mf.reject.config.txt"]],
                "ucsc" => [["java", "-Dlog4j.configuration=$log4jPropsName",
                            "-Dconfig=ucsc.config.txt",  "-cp", "$cp",
                            "org.cdlib.marcconvert.run.RunUCSC"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/prtrej.pl",
                            "ucsc.convert.log", "ucsc.reject.messages.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "ucsc.mf.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "ucsc.mf.error.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "ucsc.mf.reject.config.txt"]],
                "ucsd" => [["java", "-Dlog4j.configuration=$log4jPropsName",
                            "-Dconfig=ucsd.config.txt",  "-cp", "$cp",
                            "org.cdlib.marcconvert.run.RunUCSD"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/prtrej.pl",
                            "ucsd.convert.log", "ucsd.reject.messages.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "ucsd.mf.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "ucsd.mf.error.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "ucsd.mf.reject.config.txt"]],
                "ucsf" => [["java", "-Dlog4j.configuration=$log4jPropsName",
                            "-Dconfig=ucsf.config.txt",  "-cp", "$cp",
                            "org.cdlib.marcconvert.run.RunUCSF"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/prtrej.pl",
                            "ucsf.convert.log", "ucsf.reject.messages.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "ucsf.mf.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "ucsf.mf.error.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "ucsf.mf.reject.config.txt"]],
                "unload" => [["java", "-Dlog4j.configuration=$log4jPropsName",
                            "-Dconfig=unload.config.txt",   "-cp", "$cp",
                              "org.cdlib.marcconvert.run.RunUnload"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/prtrej.pl",
                            "unload.convert.log", "unload.reject.messages.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "unload.mf.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "unload.mf.error.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "unload.mf.reject.config.txt"]],
                "csl" => [["java", "-Dlog4j.configuration=$log4jPropsName",
                            "-Dconfig=csl.config.txt",   "-cp", "$cp",
                              "org.cdlib.marcconvert.run.RunCSL"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/prtrej.pl",
                            "csl.convert.log", "csl.reject.messages.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "csl.mf.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "csl.mf.error.config.txt"],
                           ["perl", "-I $ENV{'MB_PERL'}", "$ENV{'MB_PERL'}/marcformat.pl",
                            "csl.mf.reject.config.txt"]]
               );

my (%runtable);       # built up with sites from the command line
my $site = undef;     # the current site from the command line

if ($main::debug)
{
    my $key = undef;
    print STDERR "---------------------------------------------\n";
    print STDERR "Printing cmdtable hash...\n";
    foreach $key (sort(keys %cmdtable))
    {
        print STDERR "key = $key\n";
        my(@cmdlist) = @{$cmdtable{$key}};
        foreach my $cmdarray (@cmdlist)
        {
            my $cmd = join(' ', @{$cmdarray});
            print STDERR "  cmd = $cmd\n";
        }
    }
    print STDERR "---------------------------------------------\n";
}

if ($main::debug)
{
    print STDERR "---------------------------------------------\n";
    print STDERR "Printing environment hash...\n";
    foreach my $key (sort(keys %ENV))
    {
        my $value = $ENV{$key};
        print STDERR "key = '$key'  value = '$value'\n";
    }
    print STDERR "---------------------------------------------\n";
}

#-------------------------------------------------------------------------------
# Be sure we have some arguments to process.
#-------------------------------------------------------------------------------
unless (@ARGV)
{
    print STDOUT $usage;
    exit(0);
}

#-------------------------------------------------------------------------------
# Get the the command array for each site site specified on the command line.
# Add the array to a hashtable to iterate over later.
#-------------------------------------------------------------------------------
while ($site = lc(shift))
{
    # Handle -h or --help
    if ($site eq '-h' || $site eq '--help')
    {
        print STDOUT $usage;
        exit(0);
    }

    # Handle 'all'
    if ($site eq 'all')
    {
        %runtable = %cmdtable;
        last;
    }

    if ($site eq 'rlin')
    {
        unshift(@ARGV, ('chs', 'lbl', 'ucbl'));
        next;
    }

    # Get the command table entry if we recognize this site.
    # Otherwise print an error message.
    if (exists $cmdtable{$site})
    {
        $runtable{$site} = $cmdtable{$site};
    }
    else
    {
        print STDOUT "Unrecognized site: '$site'\n";
        next;
    }

    if ($main::debug)
    {
        my $key = undef;
        print STDERR "---------------------------------------------\n";
        print STDERR "Printing runtable hash after adding entry '$site'...\n";
        foreach $key (sort(keys %runtable))
        {
            print STDERR "key = $key\n";
            my(@cmdlist) = @{$runtable{$key}};
            foreach my $cmdarray (@cmdlist)
            {
                my $cmd = join(' ', @{$cmdarray});
                print STDERR "  cmd = $cmd\n";
            }
        }
        print STDERR "---------------------------------------------\n";
    }
}

#-------------------------------------------------------------------------------
# Quit if no commands were selected.
#-------------------------------------------------------------------------------
unless (%runtable)
{
    print STDOUT "No recognized sites to process\n\n";
    exit(0);
}

if ($main::debug)
{
    my $key = undef;
    print STDERR "---------------------------------------------\n";
    print STDERR "Printing runtable hash prior to processing...\n";
    foreach $key (sort(keys %runtable))
    {
        print STDERR "key = $key\n";
        my(@cmdlist) = @{$runtable{$key}};
        foreach my $cmdarray (@cmdlist)
        {
            my $cmd = join(' ', @{$cmdarray});
            print STDERR "  cmd = $cmd\n";
        }
    }
    print STDERR "---------------------------------------------\n";
}

#-------------------------------------------------------------------------------
# Run the commands we selected after changing our working directory.
#-------------------------------------------------------------------------------
chdir "$rundir";
print STDERR "CWD = ", getcwd(), "\n" if ($main::debug);

foreach $site (sort(keys %runtable))
{
    print STDERR "Running converstion for site '$site'\n";
    createTempLog4jProps($log4jPropsIn, $log4jPropsOut, $site);
    print STDERR "Location table = '$loctab'\n" if $main::debug;
    if ( -f $loctab )
    {
        copy("$loctab", "$site/tab_locations.txt");
    }
    else
    {
        print STDERR "Could not find location table: $loctab\n";
        system("touch $site/tab_locations.txt"); # last resort
    }
    print STDERR "Shared Print table = '$sptab'\n" if $main::debug;
    if ( -f $sptab )
    {
        copy("$sptab", "$site/shared_print_table.txt");
    }
    else
    {
        print STDERR "Could not find shared print table: $sptab\n";
        system("touch $site/shared_print_table.txt"); # last resort
    }
    my(@cmdlist) = @{$runtable{$site}};
    my $i = 0;
    chdir "$site";
    print STDERR "CWD = ", getcwd(), "\n" if ($main::debug);
    foreach my $cmdarray (@cmdlist)
    {
        $i++;
        print STDERR "Command $i = " . (join(' ', @{$cmdarray})) . "\n" if $main::debug;
        system(@{$cmdarray});
    }
    print STDERR "Completed converstion for site '$site'\n";
    chdir "..";
}

exit(0);


#-------------------------------------------------------------------------------
# Create a log4j properties file for this source using a base template file.
#-------------------------------------------------------------------------------
sub createTempLog4jProps
{
    my $finName = shift;
    my $foutName = shift;
    my $source = shift;

    die "Could not locate base log4j props file: '$finName'\n" unless (-f $finName);

    open (FIN,  "<$finName")  || die "Could not open file '<$finName\n";
    open (FOUT, ">$foutName") || die "Could not open file '>$foutName\n";

    while (<FIN>)
    {
        s/\%source\%/$source./g;
        print FOUT "$_";
    }
}

#-------------------------------------------------------------------------------
# Fix nasty windows back slashes in path strings.
#-------------------------------------------------------------------------------
sub cleanPath
{
    my $path = shift;
    print STDERR "cleanPath: path in = '$path'\n" if $main::debug;
    if ($path)
    {
        $path =~ s/\\/\//og;
        $path =~ s/\/$//o unless $path eq '/'
    }
    print STDERR "cleanPath: path out = '$path'\n" if $main::debug;
    return $path;
}

#-------------------------------------------------------------------------------
# Setup our classpath.
# If we have a $JAVA_HOME environment variable, build the classpath from
# scratch, using the support libraries from cvs, otherwise, since we have no
# way to identify where the jre base classes live, us the system classpath.
#
# If the list of required support jar files changes the list here will need
# to be changed. This includes version changes.
#-------------------------------------------------------------------------------
sub setClassPath
{
    my $cp = undef;

    if ($ENV{'JAVA_HOME'})
    {
        print STDERR "Setting classpath from scratch\n" if $main::debug;

        my @suppjars = ("sax.jar", "jakarta-regexp-1.2.jar", "log4j-1.2.5.jar");
        my $jrelib = cleanPath($ENV{'JAVA_HOME'}) . "/jre/lib/rt.jar";
        my $subjarhome = "$ENV{'MB_HOME'}/marc/cvsroot/marclib/ext";

        $cp = "$jrelib";

        #-------------------------------------------------------------------------------
        # $cpsep and $hd are a couple of hacks so we can run under Cygwin.
        #-------------------------------------------------------------------------------
        my $cpsep = ("$Config{'osname'}" eq 'cygwin' ? ";" : "$Config{'path_sep'}");
        my $hd = ("$Config{'osname'}" eq 'cygwin' ? "$ENV{'HOMEDRIVE'}" : "");

        #"$ENV{'MB_HOME'}/perl"

        foreach my $supjar (@suppjars)
        {
            $cp = "$hd$subjarhome/$supjar$cpsep$cp";
        }
    }
    else
    {
        print STDERR "Setting classpath from system classpath\n" if $main::debug;

        $cp = cleanPath($ENV{'CLASSPATH'}) if $ENV{'CLASSPATH'};
    }

    die "Unable to set any classpath\n" unless $cp;

    return $cp;
}
