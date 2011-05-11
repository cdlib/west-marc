#! /usr/bin/perl -w
#
# Merge the stats report and the reject messages from the associated
# log file produced by a converter run.
#
# It can operate on individual files or whole directories. Parameters
# may be either named, or positional, but not a mixture of the two.
# Parameters may also be given in a config file.
#
# Usage: cnvreport.pl [named_parameters] | <positional_parameters>
#
# positional parameters:
#   1 directory:
#       basedir
#   2 directories:
#       statsdir logdir
#   3 directories:
#       statsdir logdir reportdir
#   2 files:
#       statsfile logfile
#   3 files:
#       statsfile logfile reportfile
#
# named parameters:
#   --statsfile  stats report to process
#   --logfile    log file to process
#   --reportfile reportfile to produce
#
#   --statsdir   stat directory to process
#   --logdir     log directory to process
#   --reportdir  directory to recieve report files
#
#   --basedir    base directory to process
#                forces: statsdir=basedir/eistats
#                        logdir=basedir/eilog
#                        reportdir=basedir/eireport
#
#   --config     config file
#   --help       print the help text
#
# CVS Tracking info:
#
# $Revision: 1.2 $
# $Author: smcgovrn $
# $Date: 2002/12/13 23:23:34 $
# $Id: cnvreport.pl,v 1.2 2002/12/13 23:23:34 smcgovrn Exp $
#
#-------------------------------------------------------------------------------
use strict;
use DirHandle;
use English;
use File::Basename;
use FileHandle;
use Getopt::Long;

#-------------------------------------------------------------------------------
# Subroutine prototypes
#-------------------------------------------------------------------------------
sub makeReportFile($$$);        # Takes 3 scalars
sub processDirectories($$$);    # Takes 3 scalars
sub getNameTokenNumber(\@$);    # Takes array reference and a scalar
sub getMatchingLogFile(\@\@$);  # Takes two array references and a scalar
sub getLatestLogFile(\@$);      # Takes array reference and a scalar
sub cleanPath($);               # Takes 1 scalar
sub getconfig($\%);             # Takes a scalar and a reference to a hash

#-------------------------------------------------------------------------------
# Declare some global variables
#-------------------------------------------------------------------------------
$main::debug = 0;               # Global debug indicator
$main::suppressOutput = 0;      # Global indicator used to suppress file output

#-------------------------------------------------------------------------------
# Usage statement
#-------------------------------------------------------------------------------
my $usage = "\n"
. "Merge the stats report and the reject messages from the associated\n"
. "log file produced by a converter run.\n"
. "\n"
. "It can operate on individual files or whole directories. Parameters\n"
. "may be either named, or positional, but not a mixture of the two.\n"
. "Parameters may also be given in a config file.\n"
. "\n"
. "Usage: cnvreport.pl [named_parameters] | <positional_parameters>\n"
. "\n"
. "positional parameters:\n"
. "  1 directory:\n"
. "      basedir\n"
. "  2 directories:\n"
. "      statsdir logdir\n"
. "  3 directories:\n"
. "      statsdir logdir reportdir\n"
. "  2 files:\n"
. "      statsfile logfile\n"
. "  3 files:\n"
. "      statsfile logfile reportfile\n"
. "\n"
. "named parameters:\n"
. "  --statsfile  stats report to process\n"
. "  --logfile    log file to process\n"
. "  --reportfile reportfile to produce\n"
. "\n"
. "  --statsdir   stat directory to process\n"
. "  --logdir     log directory to process\n"
. "  --reportdir  directory to recieve report files\n"
. "\n"
. "  --basedir    base directory to process\n"
. "               forces: statsdir=basedir/eistats\n"
. "                       logdir=basedir/eilog\n"
. "                       reportdir=basedir/eireport\n"
. "\n"
. "  --config     config file\n"
. "  --help       print this help text\n"
. "\n";


my $rc;                                 # Return code

my %options = ("statsfile"  => undef,   # stats file
               "logfile"    => undef,   # log file
               "reportfile" => undef,   # report file
               "statsdir"   => undef,   # stats directory
               "logdir"     => undef,   # log directory
               "reportdir"  => undef,   # report directory
               "basedir"    => undef,   # base directory
               "config"     => undef,   # config file
               "debug"      => undef,   # turn on debugging messages
               "help"       => undef    # display the usage message
              );

unless (GetOptions(\%options,
                   'statsfile:s',
                   'logfile:s',
                   'reportfile:s',
                   'statsdir:s',
                   'logdir:s',
                   'reportdir:s',
                   'basedir:s',
                   'config:s',
                   'debug!',
                   'help'))
{
    die "Failed to parse command line\n";
}

#-------------------------------------------------------------------------------
# Set the debug global if that options is specified.
#-------------------------------------------------------------------------------
$main::debug = $options{'debug'} if (defined $options{'debug'});

#-------------------------------------------------------------------------------
# When debugging print the options we found, prior to loading config file
#-------------------------------------------------------------------------------
if ($main::debug)
{
    print STDERR "options prior to loading config file\n";
    foreach my $okey (keys(%options))
    {
        my $oval = $options{$okey} || "undef";
        print STDERR "option $okey ='$oval'\n";
    }
}

#-------------------------------------------------------------------------------
# If a config file is specifed, load options from config that have been
# specified on the command line.
#-------------------------------------------------------------------------------
my $config = ($options{"config"} ? cleanPath($options{"config"}) : undef);

if ($config)
{
    $config = cleanPath($config);
    print STDERR "Processing config file $config\n" if $main::debug;
    getconfig($config, %options);

    #-------------------------------------------------------------------------------
    # When debugging print the options we found, after loading config the file
    #-------------------------------------------------------------------------------
    if ($main::debug)
    {
        print STDERR "options after loading config the file\n";
        foreach my $okey (keys(%options))
        {
            my $oval = $options{$okey} || "undef";
            print STDERR "option $okey ='$oval'\n";
        }
    }
}

#-------------------------------------------------------------------------------
# If help was requsted print the usage statement and exit
#-------------------------------------------------------------------------------
if ($options{"help"})
{
    print STDERR "$usage";
    exit 0;
}

#-------------------------------------------------------------------------------
# Change '\' to '/' and strip trailing '/' for all path like options.
#-------------------------------------------------------------------------------
foreach my $okey (keys(%options))
{
    if (defined $options{$okey}
        && $options{$okey} ne "help"
        && $options{$okey} ne "config")
    {
        $options{$okey} = cleanPath($options{$okey});
    }
}


#-------------------------------------------------------------------------------
# Check to see if we have a valid set of either file or directory parameters,
# but not both. If we do, ignore any remaining command line arguments,
# otherwise try and use them.
#-------------------------------------------------------------------------------
my $haveFileOpts   = 0;
my $statsfile  = undef;
my $logfile    = undef;
my $reportfile = undef;

my $haveDirOpts    = 0;
my $statsdir   = undef;
my $logdir     = undef;
my $reportdir  = undef;
my $basedir    = undef;

#-------------------------------------------------------------------------------
# If we have any file options check they are correctly specified.
# We need both statsfile and logfile, reportfile will default to STDOUT
# if not specified.
#-------------------------------------------------------------------------------
if ($options{"statsfile"} || $options{"logfile"} || $options{"reportfile"})
{
    unless ($options{"statsfile"} && $options{"logfile"})
    {
        die "Incorrect combination of file options specified\n$usage";
    }

    $haveFileOpts = 1;
    $statsfile    = $options{"statsfile"};
    $logfile      = $options{"logfile"};

    if ($options{"reportfile"})
    {
        $reportfile = $options{"reportfile"};
    }
    else
    {
        $reportfile =  "&STDOUT";
    }
}

#-------------------------------------------------------------------------------
# If we have any directory options we should not have any file options.
# In this case both statsdir and logdir are required, but reportdir will
# default relative to statsdir, if it is not specified.
#-------------------------------------------------------------------------------
if ($options{"statsdir"} || $options{"logdir"} || $options{"reportdir"})
{
    if ($haveFileOpts)
    {
        die "Cannot specifiy directory options with file options\n$usage";
    }

    unless ($options{"statsdir"} && $options{"logdir"})
    {
        die "Incorrect combination of directory options specifid\n$usage";
    }

    $haveDirOpts = 1;
    $statsdir    = $options{"statsdir"};
    $logdir      = $options{"logdir"};

    if ($options{"reportdir"})
    {
        $reportdir = $options{"reportdir"};
    }
    else
    {
        $reportdir =  "$options{'statsdir'}/../eireport";
    }

    unless (-e $reportdir)
    {
        mkdir ("$reportdir", 0777);
    }

    unless (-d $reportdir)
    {
        die "Specified report directory exists, but is not a directory\n$usage";
    }
}

#-------------------------------------------------------------------------------
# If base dir is specified no other directory or file options should be given
#-------------------------------------------------------------------------------
if ($options{"basedir"})
{
    die "Cannot specifiy basedir with file options\n$usage" if ($haveFileOpts);
    die "Cannot specifiy basedir with directory options\n$usage" if ($haveDirOpts);

    $haveDirOpts = 1;
    $statsdir    = "$options{'basedir'}/eistats";
    $logdir      = "$options{'basedir'}/eilog";
    $reportdir   = "$options{'basedir'}/eireport";
}

#-------------------------------------------------------------------------------
# If we do not have either file or directory options, attempt to get them
# from the rest of the command line.
#-------------------------------------------------------------------------------
unless ($haveFileOpts || $haveDirOpts)
{
    my $argc = scalar(@ARGV);

    die "No arguments specified\n$usage" if ($argc < 1);

    #-------------------------------------------------------------------------------
    # Change '\' to '/' and strip trailing '/' for all arguments.
    #-------------------------------------------------------------------------------
    for (my $i = 0; $i < $argc; $i++)
    {
        $ARGV[$i] = cleanPath($ARGV[$i]);
    }

    #-------------------------------------------------------------------------------
    # One argument has to be a base directory
    #-------------------------------------------------------------------------------
    if ($argc == 1)
    {
        #-------------------------------------------------------------------------------
        # One argument has to be a base directory
        #-------------------------------------------------------------------------------
        die "Single argument must be a base directory, but $ARGV[0] is not a directory\n$usage"
        unless (-d $ARGV[0]);

        $haveDirOpts = 1;
        $statsdir    = "$ARGV[0]/eistats";
        $logdir      = "$ARGV[0]/eilog";
        $reportdir   = "$ARGV[0]/eireport";
    }
    elsif ($argc == 2)
    {
        #-------------------------------------------------------------------------------
        # two arguments may be either a stats file and a log file
        # or a stats directory and a log directory
        #-------------------------------------------------------------------------------
        if (-f $ARGV[0] and -f $ARGV[1])
        {
            $haveFileOpts = 1;
            $statsfile    = "$ARGV[0]";
            $logfile      = "$ARGV[1]";
            $reportfile   = "&STDOUT";
        }
        elsif (-d $ARGV[0] and -d $ARGV[1])
        {
            $haveDirOpts = 1;
            $statsdir    = "$ARGV[0]";
            $logdir      = "$ARGV[1]";
            $reportdir   = "$ARGV[0]/../eireport";
        }
        else
        {
            die "Two arguments must be either both files or both directories\n$usage";
        }
    }
    else
    {
        #-------------------------------------------------------------------------------
        # Three arguments may be either a stats file, a log file, and a report file
        # or a stats directory, a log directory, and a report directory.
        #-------------------------------------------------------------------------------
        if (-f $ARGV[0] && -f $ARGV[1] && (!-e $ARGV[2] || -f $ARGV[2]))
        {
            $haveFileOpts = 1;
            $statsfile    = "$ARGV[0]";
            $logfile      = "$ARGV[1]";
            $reportfile   = "$ARGV[2]";
        }
        elsif (-d $ARGV[0] && -d $ARGV[1] && (!-e $ARGV[2] || -d $ARGV[2]))
        {
            $haveDirOpts = 1;
            $statsdir    = "$ARGV[0]";
            $logdir      = "$ARGV[1]";
            $reportdir   = "$ARGV[2]";
        }
        else
        {
            print STDERR "arg[0] = $ARGV[0] is", (-f $ARGV[0] ? " " : " not "), "a file\n";
            print STDERR "arg[1] = $ARGV[1] is", (-f $ARGV[1] ? " " : " not "), "a file\n";
            print STDERR "arg[2] = $ARGV[2] is", (-e $ARGV[2] ? " " : " not "), "found\n";
            print STDERR "arg[2] = $ARGV[2] is", (-f $ARGV[2] ? " " : " not "), "a file\n";

            die "Three arguments must be either all files or all directories\n$usage";
        }

        print STDERR "Warning: extra parameters ignored" if ($argc > 3);
    }
}

if ($haveFileOpts)
{
    $rc = makeReportFile($statsfile, $logfile, $reportfile);
}
elsif ($haveDirOpts)
{
    $rc = processDirectories($statsdir, $logdir, $reportdir);
}
else
{
    die "Incorrect options specified\n$usage";
}

print STDERR "cnvreport.pl: return code = $rc\n";

exit $rc;
#-------------------------------------------------------------------------------
# end of top level logic, subroutines follow
#-------------------------------------------------------------------------------

#-------------------------------------------------------------------------------
# Read the configuration file to get the runtime environment. No assumptions
# are made as to which parameters are specified. Those we find are set, the
# others will be set to default values later, unless they have already been
# set using a --option.
#-------------------------------------------------------------------------------
sub getconfig($\%)
{
    my $fname    = shift;               # config file name
    my $runparms = shift;               # reference to parameter hash
    my $name     = undef;               # local name variable
    my $value    = undef;               # local value variable

    unless (-f "$fname" && open(CFIN, "<$fname"))
    {
        die "Cannot open config file: $fname\n"
    }

    while (<CFIN>)
    {
        chomp;                             # remove line terminator

        s/\#.*$//og;                       # trim comments
        s/^\s+//og;                        # trim leading whitespace
        s/\s+$//og;                        # trim trailing whitespace

        next if length($_) == 0;           # skip if nothing left
        ($name,$value) = split(/\s*=\s*/); # get name and value pair
        next unless ($name && $value);     # skip if either is missing
        print STDERR "name = '$name' value = '$value'\n" if $main::debug;
        if (exists $runparms->{$name})     # if this is a name we like
        {
            unless ($runparms->{$name})    # and it is not yet defined
            {
                $runparms->{$name} = $value;   # set the value
            }
        }
    }
    close(CFIN);

    return;
}


#-------------------------------------------------------------------------------
# Process the stats and log files in the directories specifed. For each stats
# file found, find the corresponding log file, and make a corresponding report
# file name. Pass all three file paths to makeReportFile($$$) to create the
# report file.
#-------------------------------------------------------------------------------
sub processDirectories($$$)
{
    my $statsdir = shift;
    my $logdir = shift;
    my $reportdir = shift;
    my $rc = 0;

    print STDERR "DEBUG: processDirectories($statsdir, $logdir, $reportdir)\n" if $main::debug;

    unless (opendir(STATSDIR, $statsdir))
    {
        die "Failed to open stats directory: $statsdir\n";
    }

    my @statsfiles = grep !/^\.\.?$/, readdir STATSDIR;
    closedir STATSDIR;

    unless (opendir(LOGDIR, $logdir))
    {
        die "Failed to open log directory: $logdir\n";
    }

    my @logfiles = grep !/^\.\.?$/, readdir LOGDIR;
    closedir LOGDIR;

    foreach my $statsfile (@statsfiles)
    {
        my @sfTokens = split(/\./, $statsfile);
        if ($main::debug)
        {
            print STDERR "sfTokens(",(scalar @sfTokens),") = ", (join(" ", @sfTokens)), "\n";
        }
        my $nameIndex = getNameTokenNumber(@sfTokens, "eistats");
        print STDERR "name index = $nameIndex\n" if $main::debug;
        if ($nameIndex < 0)
        {
            print STDERR "Badly formatted stats file name: $statsfile\n";
            next;
        }

        my $logfile = getMatchingLogFile(@logfiles, @sfTokens, $nameIndex);
        if ($main::debug)
        {
            print STDERR "Matching log file: ", (defined $logfile ? $logfile : "undef"), "\n";
        }

        my @rfTokens = @sfTokens;
        $rfTokens[$nameIndex] = "eireport";
        my $reportfile = join('.', @rfTokens);
        print "report file name = $reportfile\n" if $main::debug;
        my $sfPath = "$statsdir/$statsfile";
        my $lfPath = "$logdir/$logfile";
        my $rfPath = "$reportdir/$reportfile";
        $rc += makeReportFile($sfPath, $lfPath, $rfPath);
    }

    return $rc;
}


#-------------------------------------------------------------------------------
# Get the index of the name segment for a file. The name segment is the one
# that indicates what type of file it is, e.g. eistats, or eireport.
#-------------------------------------------------------------------------------
sub getNameTokenNumber(\@$)
{
    my $tokens = shift;
    my $name = shift;
    my $match = -1;

    if ($tokens && $name)
    {
        my $tmax = scalar @$tokens;
        for (my $i = 0; $i < $tmax; $i++)
        {
            print STDERR "checking ($i of $tmax) $tokens->[$i] eq $name\n" if $main::debug;

            if ($tokens->[$i] eq $name)
            {
                print STDERR "foung name segment($name) at $i\n" if $main::debug;
                $match = $i;
                last;
            }
        }
    }
    return $match;
}


#-------------------------------------------------------------------------------
# Get the name of the log file the corresponds to a given stats file.
#-------------------------------------------------------------------------------
sub getMatchingLogFile(\@\@$)
{
    my $logFiles = shift;
    my $sfTokens = shift;
    my $nameIndex = shift;
    my $logfile = undef;

    if ($logFiles && $sfTokens && $nameIndex)
    {
        my @logTokens = @$sfTokens;
        $logTokens[$nameIndex] = "log";
        my $lfFront = join('.', @logTokens);
        print "Log file front = $lfFront\n" if $main::debug;
        my @lfSet = grep(/^$lfFront/, @$logFiles);
        if ($main::debug)
        {
            foreach my $lf (@lfSet)
            {
                print STDERR "lf match = $lf\n";
            }
        }

        if (@lfSet)
        {
            my $lfsize = scalar @lfSet;
            if ($lfsize == 1)
            {
                $logfile = $lfSet[0];
            }
            else
            {
                $logfile = getLatestLogFile(@lfSet, $lfFront);
            }
        }
        else
        {
            print STDERR "No matching log files found for: $lfFront\n";
        }
    }

    return $logfile;
}


#-------------------------------------------------------------------------------
# Since log files are not replaced when a file is reprocessed there may be
# more than one log file corresponding to a stats file. So we search for
# the latest one by looking at the runtime segment of the file name, and
# return that one.
#-------------------------------------------------------------------------------
sub getLatestLogFile(\@$)
{
    my $lfSet = shift;
    my $lfFront = shift;
    my $logfile = undef;

    if ($lfSet && $lfFront)
    {
        my $rtCurr = 0;
        my $rtPrev = 0;
        foreach my $lf (@$lfSet)
        {
            unless ($lf =~ /^$lfFront\.RT(\d+)/)
            {
                print STDERR "Log file has no RT segment: $lf\n";
                next;
            }
            $rtCurr = int $1;
            print "rtCurr = $rtCurr  rtPrev = $rtPrev\n" if $main::debug;
            if ($rtCurr > $rtPrev)
            {
                $logfile = $lf;
            }
            if ($main::debug)
            {
                print STDERR ("possible latest log file = ",
                              (defined $logfile ? $logfile : "undef"), "\n");
            }
        }
    }
    return $logfile;
}


#-------------------------------------------------------------------------------
# Reads in a stats file and a log file and produces a report file.
#
# Returns 1 (true) if successful, 0 (false) if any error occurs.
# This is preferable to die, say upon file open failure, because this
# subroutine may be called from inside a loop iterating over a direcory,
# and we don't want to stop the script until all the directory entries
# have been processed.
#-------------------------------------------------------------------------------
sub makeReportFile($$$)
{
    my $statsfile = shift;
    my $logfile = shift;
    my $reportfile = shift;
    my $rc = 0;

    print STDERR "DEBUG: makeReportFile($statsfile, $logfile, $reportfile)\n" if $main::debug;

    print STDERR "Opening stats file '$statsfile'\n" if $main::debug;
    unless ($statsfile && open(STATSFIN, "<$statsfile"))
    {
        print STDERR "Failed to open stats file: '$statsfile'\n";
        $rc = 1;
    }

    if ($rc == 0)
    {
        print STDERR "Opening log file '$logfile'\n" if $main::debug;
        unless ($logfile && open(LOGFIN, "<$logfile"))
        {
            print STDERR "Failed to open log file: '$logfile'\n";
            $rc = 1;
        }
    }

    if ($rc == 0)
    {
        print STDERR "Opening report file '$reportfile'\n" if $main::debug;
        mkdir(dirname($reportfile), 0777);
        unless ($rc == 0 && $reportfile && open(RPTFOUT, ">$reportfile"))
        {
            print STDERR "Failed to open report file: '$reportfile'\n";
            $rc = 1;
        }
    }


    if ($rc == 0 && not $main::suppressOutput)
    {
        print STDERR "Creating report file: $reportfile\n" if $main::debug;

        binmode(STATSFIN);      # prevent spurious character translations
        binmode(LOGFIN);        # prevent spurious character translations
        binmode(RPTFOUT);       # prevent spurious character translations

        # Copy the stats file to the report file
        while (<STATSFIN>)
        {
            print RPTFOUT "$_";
        }

        # Copy the Reject messages to the report file
        my $rcnt = 0;
        my $logline = undef;
        my $rrpos = -1;

        while (<LOGFIN>)
        {
            if (($rrpos = index($_,"Record Rejected:")) > -1)
            {
                if ($rcnt++ == 0)
                {
                    print RPTFOUT "Records rejected details:\n\n";
                }
                print RPTFOUT substr($_, $rrpos);
            }
        }
    }

    close(STATSFIN); # close stats file
    close(LOGFIN);   # close log file
    close(RPTFOUT);  # close report file

    return $rc;
}

#-------------------------------------------------------------------------------
# Fix nasty windows back slashes in path strings, and strip trailing '/',
# unless the it is the only character, i.e. the root directory.
#-------------------------------------------------------------------------------
sub cleanPath($)
{
    my $path = shift;
    print STDERR "cleanPath: path in = '", (defined $path ? $path : "undef"), "'\n"
    if $main::debug;

    if ($path)
    {
        $path =~ s/\\/\//og;
        $path =~ s/\/$//o unless $path eq '/'
    }
    print STDERR "cleanPath: path out = '", (defined $path ? $path : "undef"), "'\n"
    if $main::debug;
    return $path;
}
