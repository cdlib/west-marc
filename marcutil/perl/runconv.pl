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
#
# CVS Tracking info:
#
# $Revision: 1.4 $
# $Author: smcgovrn $
# $Date: 2002/08/28 22:09:35 $
# $Id: runconv.pl,v 1.4 2002/08/28 22:09:35 smcgovrn Exp $
#
#-------------------------------------------------------------------------------

use strict;

my $debug = 1; # set to true to turn on debug mode

#-------------------------------------------------------------------------------
# Handy usage string in case the user requests --help
#-------------------------------------------------------------------------------
my $usage = "\n"
          . "Usage: runconv.pl site1 file1 file2...\n"
          . "\n"
          . "Where site is in the list:\n"
          . "  chs, gtu, lcn, lbl, oclc, ucb, ucbl, ucd, ucdl,\n"
          . "  uci, ucla, ucr, ucsb, ucsc, ucsd, ucsf, unload\n"
          . "\n"
          . "This script assumes that base config files exists for both the conversion\n"
          . "and the ascii format of the converted data, named 'base.config.txt' and\n"
          . "'base.mf.config.txt'.\n"
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


my $logjPropsIn  = "$ENV{'LOG4JPROPSDIR'}/log4j.base.props";
my $logjPropsOut = "$ENV{'LOG4JPROPSDIR'}/log4j.temp.props";
my $cnvConfigIn  = "base.config.txt";
my $cnvConfigOut = "temp.config.txt";
my $fmtConfigOut = "temp.mf.config.txt";
my $fmtConfigIn  = "base.mf.config.txt";

#-------------------------------------------------------------------------------
# The java command to run the converter
#-------------------------------------------------------------------------------
my @cnvCmd = ("java", "-Dlog4j.configuration=log4j.temp.props",
             "-Dconfig=temp.config.txt");

#-------------------------------------------------------------------------------
# The command to format the converted data as ascii
#-------------------------------------------------------------------------------
my @fmtCmd = ("perl", "-I $ENV{'PERLBASE'}", "$ENV{'PERLBASE'}/marcformat.pl",
             "temp.mf.config.txt");

#-------------------------------------------------------------------------------
# Associate each site with a converter
#-------------------------------------------------------------------------------
my %sitetable = (
                 "chs"    => "org.cdlib.marcconvert.run.RunCHS",
                 "gtu"    => "org.cdlib.marcconvert.run.RunGTU",
                 "lbl"    => "org.cdlib.marcconvert.run.RunLBL",
                 "lcn"    => "org.cdlib.marcconvert.run.RunLCN",
                 "oclc"   => "org.cdlib.marcconvert.run.RunOCLC",
                 "ucb"    => "org.cdlib.marcconvert.run.RunUCB",
                 "ucbl"   => "org.cdlib.marcconvert.run.RunUCBLaw",
                 "ucd"    => "org.cdlib.marcconvert.run.RunUCD",
                 "ucdl"   => "org.cdlib.marcconvert.run.RunUCDL",
                 "uci"    => "org.cdlib.marcconvert.run.RunUCI",
                 "ucla"   => "org.cdlib.marcconvert.run.RunUCLA",
                 "ucr"    => "org.cdlib.marcconvert.run.RunUCR",
                 "ucsb"   => "org.cdlib.marcconvert.run.RunUCSB",
                 "ucsc"   => "org.cdlib.marcconvert.run.RunUCSC",
                 "ucsd"   => "org.cdlib.marcconvert.run.RunUCSD",
                 "ucsf"   => "org.cdlib.marcconvert.run.RunUCSF"
                 "unload" => "org.cdlib.marcconvert.run.RunUnload"
                );

my $site     = undef; # the current site from the command line
my $testfile = undef; # the test file to process from the command line

#-------------------------------------------------------------------------------
# End of variable declarations
#-------------------------------------------------------------------------------


#-------------------------------------------------------------------------------
# Make sure we have some arguments
#-------------------------------------------------------------------------------
unless (@ARGV)
{
    print STDOUT $usage;
    exit(0);
}

#-------------------------------------------------------------------------------
# Get the site to process
#-------------------------------------------------------------------------------
$site = shift;

#-------------------------------------------------------------------------------
# Handle -h or --help
#-------------------------------------------------------------------------------
if ($site eq '-h' || $site eq '--help')
{
    print STDOUT $usage;
    exit(0);
}

#-------------------------------------------------------------------------------
# make sure we have a file to process
#-------------------------------------------------------------------------------
unless (@ARGV)
{
    print STDOUT $usage;
    exit(0);
}

#-------------------------------------------------------------------------------
# Get the java class to use for the specified site. If the site is not
# recognized throw an error message and quit.
#-------------------------------------------------------------------------------
if (exists $sitetable{$site})
{
     push @cnvCmd, $sitetable{$site};
    createTempLog4jProps($logjPropsIn, $logjPropsOut, $site);
}
else
{
    print STDOUT "Unrecognized site: '$site'\n";
    exit(0);
}

#-------------------------------------------------------------------------------
# More debugging - print the java command
#-------------------------------------------------------------------------------
if ($debug)
{
    print STDERR "---------------------------------------------\n";
    print STDERR "Printing java command array...\n";
    my $cmd = join(' ', @cnvCmd);
    print STDERR "  java cmd = $cmd\n";
    print STDERR "---------------------------------------------\n";
}


#-------------------------------------------------------------------------------
# Loop through the remaining arguments. Each should be a file to process
# for the specified site.
#-------------------------------------------------------------------------------
my $i = 0;
while ($testfile = shift)
{

    # Make the sure the file exists before creating unecessary config files
    unless (-f $testfile)
    {
        print STDERR "Cannot locate input file: $testfile\n";
        next;
    }

    createTempConfig($cnvConfigIn, $cnvConfigOut, $testfile);
    createTempConfig($fmtConfigIn, $fmtConfigOut, $testfile);


    # Run the conversion using the temporary config files.
    print STDOUT "Running converstion for file '$testfile'\n";
    $i++;
    print STDOUT "Command $i = " . (join(' ', @cnvCmd)) . "\n";
    system(@cnvCmd);

    # ASCII format the conversion results
    $i++;
    print STDOUT "Command $i = " . (join(' ', @fmtCmd)) . "\n";
    system(@fmtCmd);
}

exit(0);


#-------------------------------------------------------------------------------
# Create a config file for this source using a base template file.
#-------------------------------------------------------------------------------
sub createTempConfig
{
    my $finName = shift;
    my $foutName = shift;
    my $testfile = shift;

    die "Could not locate base config file: '$finName'\n" unless (-f $finName);

    open (FIN,  "<$finName")  || die "Could not open file '<$finName\n";
    open (FOUT, ">$foutName") || die "Could not open file '>$foutName\n";

    while (<FIN>)
    {
        s/testfile/$testfile/;
        print FOUT "$_";
    }
}


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
