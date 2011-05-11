#!/usr/bin/perl -w
#
# Run the marc convert benchmark tests.
#
# Usage: runbench.pl all | site1 site2 ...
#
# Where site is in the list:
#   chs, gtu, lcn, lbl, oclc, ucb, ucbl, ucd, ucdl,
#   uci, ucla, ucr, ucsb, ucsc, ucsd, ucsf, unload
#
# This script assumes that an approriate config file exists for each site
# and has a name of the form 'site.config.txt', e.g. 'chs.config.txt'.
#
# CVS Tracking info:
#
# $Revision: 1.5 $
# $Author: smcgovrn $
# $Date: 2002/08/28 22:09:35 $
# $Id: runbench.pl,v 1.5 2002/08/28 22:09:35 smcgovrn Exp $
#
#-------------------------------------------------------------------------------

use strict;

#-------------------------------------------------------------------------------
# Handy usage string in case the user requests --help
#-------------------------------------------------------------------------------
my $usage = "\n"
          . "Usage: runbench.pl all | site1 site2 ...\n"
          . "\n"
          . "Where site is in the list:\n"
          . "  chs, gtu, lcn, lbl, oclc, ucb, ucbl, ucd, ucdl,\n"
          . "  uci, ucla, ucr, ucsb, ucsc, ucsd, ucsf, unload\n"
          . "\n"
          . "This script assumes that an approriate config file exists for each site\n"
          . "and has a name of the form 'site.config.txt', e.g. 'chs.config.txt'.\n"
          . "\n";

#-------------------------------------------------------------------------------
# Default our required environment variables
#-------------------------------------------------------------------------------
$ENV{'PERLBASE'}      = '.' unless $ENV{'PERLBASE'};
$ENV{'LOG4JPROPSDIR'} = '.' unless $ENV{'LOG4JPROPSDIR'};

#-------------------------------------------------------------------------------
# Fix '\' in directory variables on windows
#-------------------------------------------------------------------------------
$ENV{'PERLBASE'}      = cleanPath($ENV{'PERLBASE'})      if $ENV{'PERLBASE'};
$ENV{'LOG4JPROPSDIR'} = cleanPath($ENV{'LOG4JPROPSDIR'}) if $ENV{'LOG4JPROPSDIR'};

#-------------------------------------------------------------------------------
# Set the log4j properties files names
#-------------------------------------------------------------------------------
my $log4jPropsIn  = $ENV{'LOG4JPROPSDIR'} . "/log4j.base.props";
my $log4jPropsOut = $ENV{'LOG4JPROPSDIR'} . "/log4j.temp.props";

#-------------------------------------------------------------------------------
# Table of commands to run for each site
#-------------------------------------------------------------------------------
my %cmdtable = (
                'chs'  => [["java", "-Dlog4j.configuration=log4j.temp.props",
                            "-Dconfig=chs.config.txt", "org.cdlib.marcconvert.run.RunCHS"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/prtrej.pl",
                            "chs.convert.log", "chs.reject.messages.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "chs.mf.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "chs.mf.error.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "chs.mf.reject.config.txt"]],
                "gtu"  => [["java", "-Dlog4j.configuration=log4j.temp.props",
                            "-Dconfig=gtu.config.txt", "org.cdlib.marcconvert.run.RunGTU"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/prtrej.pl",
                            "gtu.convert.log", "gtu.reject.messages.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "gtu.mf.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "gtu.mf.error.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "gtu.mf.reject.config.txt"]],
                "lcn"  => [["java", "-Dlog4j.configuration=log4j.temp.props",
                            "-Dconfig=lcn.config.txt", "org.cdlib.marcconvert.run.RunLCN"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/prtrej.pl",
                            "lcn.convert.log", "lcn.reject.messages.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "lcn.mf.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "lcn.mf.error.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "lcn.mf.reject.config.txt"]],
                "lbl"  => [["java", "-Dlog4j.configuration=log4j.temp.props",
                            "-Dconfig=lbl.config.txt", "org.cdlib.marcconvert.run.RunLBL"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/prtrej.pl",
                            "lbl.convert.log", "lbl.reject.messages.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "lbl.mf.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "lbl.mf.error.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "lbl.mf.reject.config.txt"]],
                "oclc"  => [["java", "-Dlog4j.configuration=log4j.temp.props",
                            "-Dconfig=oclc.config.txt", "org.cdlib.marcconvert.run.RunOCLC"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/prtrej.pl",
                            "oclc.convert.log", "oclc.reject.messages.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "oclc.mf.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "oclc.mf.error.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "oclc.mf.reject.config.txt"]],
                "ucb"  => [["java", "-Dlog4j.configuration=log4j.temp.props",
                            "-Dconfig=ucb.config.txt", "org.cdlib.marcconvert.run.RunUCB"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/prtrej.pl",
                            "ucb.convert.log", "ucb.reject.messages.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "ucb.mf.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "ucb.mf.error.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "ucb.mf.reject.config.txt"]],
                "ucbl" => [["java", "-Dlog4j.configuration=log4j.temp.props",
                            "-Dconfig=ucbl.config.txt", "org.cdlib.marcconvert.run.RunUCBL"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/prtrej.pl",
                            "ucbl.convert.log", "ucbl.reject.messages.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "ucbl.mf.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "ucbl.mf.error.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "ucbl.mf.reject.config.txt"]],
                "ucd"  => [["java", "-Dlog4j.configuration=log4j.temp.props",
                            "-Dconfig=ucd.config.txt", "org.cdlib.marcconvert.run.RunUCD"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/prtrej.pl",
                            "ucd.convert.log", "ucd.reject.messages.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "ucd.mf.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "ucd.mf.error.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "ucd.mf.reject.config.txt"]],
                "ucdl" => [["java", "-Dlog4j.configuration=log4j.temp.props",
                            "-Dconfig=ucdl.config.txt", "org.cdlib.marcconvert.run.RunUCDL"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/prtrej.pl",
                            "ucdl.convert.log", "ucdl.reject.messages.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "ucdl.mf.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "ucdl.mf.error.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "ucdl.mf.reject.config.txt"]],
                "uci"  => [["java", "-Dlog4j.configuration=log4j.temp.props",
                            "-Dconfig=uci.config.txt", "org.cdlib.marcconvert.run.RunUCI"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/prtrej.pl",
                            "uci.convert.log", "uci.reject.messages.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "uci.mf.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "uci.mf.error.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "uci.mf.reject.config.txt"]],
                "ucla" => [["java", "-Dlog4j.configuration=log4j.temp.props",
                            "-Dconfig=ucla.config.txt", "org.cdlib.marcconvert.run.RunUCLA"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/prtrej.pl",
                            "ucla.convert.log", "ucla.reject.messages.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "ucla.mf.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "ucla.mf.error.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "ucla.mf.reject.config.txt"]],
                "ucr"  => [["java", "-Dlog4j.configuration=log4j.temp.props",
                            "-Dconfig=ucr.config.txt", "org.cdlib.marcconvert.run.RunUCR"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/prtrej.pl",
                            "ucr.convert.log", "ucr.reject.messages.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "ucr.mf.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "ucr.mf.error.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "ucr.mf.reject.config.txt"]],
                "ucsb" => [["java", "-Dlog4j.configuration=log4j.temp.props",
                            "-Dconfig=ucsb.config.txt", "org.cdlib.marcconvert.run.RunUCSB"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/prtrej.pl",
                            "ucsb.convert.log", "ucsb.reject.messages.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "ucsb.mf.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "ucsb.mf.error.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "ucsb.mf.reject.config.txt"]],
                "ucsc" => [["java", "-Dlog4j.configuration=log4j.temp.props",
                            "-Dconfig=ucsc.config.txt", "org.cdlib.marcconvert.run.RunUCSC"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/prtrej.pl",
                            "ucsc.convert.log", "ucsc.reject.messages.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "ucsc.mf.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "ucsc.mf.error.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "ucsc.mf.reject.config.txt"]],
                "ucsd" => [["java", "-Dlog4j.configuration=log4j.temp.props",
                            "-Dconfig=ucsd.config.txt", "org.cdlib.marcconvert.run.RunUCSD"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/prtrej.pl",
                            "ucsd.convert.log", "ucsd.reject.messages.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "ucsd.mf.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "ucsd.mf.error.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "ucsd.mf.reject.config.txt"]],
                "ucsf" => [["java", "-Dlog4j.configuration=log4j.temp.props",
                            "-Dconfig=ucsf.config.txt", "org.cdlib.marcconvert.run.RunUCSF"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/prtrej.pl",
                            "ucsf.convert.log", "ucsf.reject.messages.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "ucsf.mf.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "ucsf.mf.error.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "ucsf.mf.reject.config.txt"]],
                "unload" => [["java", "-Dlog4j.configuration=log4j.temp.props",
                            "-Dconfig=unload.config.txt", "org.cdlib.marcconvert.run.RunUnload"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/prtrej.pl",
                            "unload.convert.log", "unload.reject.messages.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "unload.mf.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "unload.mf.error.config.txt"],
                           ["perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
                            "unload.mf.reject.config.txt"]]
               );

my (%runtable);       # built up with sites from the command line
my $site = undef;     # the current site from the command line
my $debug = undef;    # set to true to turn on debug mode

#-------------------------------------------------------------------------------
# End of variable declarations
#-------------------------------------------------------------------------------

if ($debug)
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

if ($debug)
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
# Make sure we have some arguments to process.
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

    if ($debug)
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

if ($debug)
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
# Run the commands we selected.
#-------------------------------------------------------------------------------
foreach $site (sort(keys %runtable))
{
    print STDOUT "Running converstion for site '$site'\n";
    createTempLog4jProps($log4jPropsIn, $log4jPropsOut, $site);
    my(@cmdlist) = @{$runtable{$site}};
    my $i = 0;

    foreach my $cmdarray (@cmdlist)
    {
        $i++;
        print STDOUT "Command $i = " . (join(' ', @{$cmdarray})) . "\n";
        system(@{$cmdarray});
    }
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
