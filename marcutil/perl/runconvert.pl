#!/usr/bin/perl -w
#
# Run a marc converter in production.
#
# This script will parse the input file name and use the tokens to build
# the output file names. All the output files will go to the appropriate
# directories. The production directory tree for a source looks like so:
#
# <source>/
#     eierror/       error files
#     eiin/          input files to process
#     eilog/         log files
#     eiout/         converted marc files
#     eiprocessed/   processed input files
#     eireject/      reject files
#     eistats/       statistics reports
#     eitmp/         temporary files
#
# Usage: runconvert.pl [options] <directory | file>
# Options:
#    --help                    Prints this message and exits.
#    --basedir  <directory>    The base directory for the output file. Use
#                              this option when the input directory is not
#                              a direct child of the base directory.
#    --config   <configfile>   Contents used to override generated values
#                              put in the converter config file.
#    --contacts <contactsfile> The file containing the email contacts
#                              to receive the report file. If this is not
#                              supplied then the report file is not sent.
#
#
# CVS Tracking info:
#
# $Revision: 1.28 $
# $Author: mreyes $
# $Date: 2005/06/01 22:16:37 $
# $Id: runconvert.pl,v 1.28 2005/06/01 22:16:37 mreyes Exp $
#
#-------------------------------------------------------------------------------
use strict;
use Config;
use Cwd qw(chdir cwd getcwd);
use English;
use File::Basename;
use File::Spec::Functions;
use Getopt::Long;

#-------------------------------------------------------------------------------
# Set some wretched global variables.
#-------------------------------------------------------------------------------
$main::debug = 0;                       # set to true to turn on debug mode
$main::rtStr = "RT" . time;             # save the runtime
$main::pid = $$;                        # save the process id

print STDERR "path_sep = $Config{path_sep}\n" if $main::debug;

#-------------------------------------------------------------------------------
# Handy usage string in case the user requests --help
#-------------------------------------------------------------------------------
my $usage = "\n"
. "Usage: runconvert.pl [options] <directory | file>\n"
. "Options:\n"
. "   --help,                   Prints this message and exits.\n"
. "   --basedir  <directory>    The base directory for the output file. Use\n"
. "                             this option when the input directory is not\n"
. "                             a direct child of the base directory.\n"
. "   --config   <configfile>   Contents used to override generated values\n"
. "                             put in the converter config file.\n"
. "   --contacts <contactsfile> The file containing the email contacts\n"
. "                             to receive the report file. If this is not\n"
. "                             supplied then the report file is not sent.\n"
. "\n";

#-------------------------------------------------------------------------------
# Be sure we have some arguments to process
#-------------------------------------------------------------------------------
unless (@ARGV)
{
    print STDOUT "No parameters specified\n";
    print STDOUT $usage;
    exit(0);
}

#-------------------------------------------------------------------------------
# Check that we have all the required environment variables
#-------------------------------------------------------------------------------
die "java CLASSPATH environment variable not set\n" unless $ENV{'CLASSPATH'};
die "LOG4JPROPSBASE environment variable not set\n" unless $ENV{'LOG4JPROPSBASE'};
die "LOCTBL environment variable not set\n"         unless $ENV{'LOCTBL'};
die "SPTBL environment variable not set\n"          unless $ENV{'SPTBL'};
die "CDLPERL environment variable not set\n"        unless $ENV{'CDLPERL'};

#-------------------------------------------------------------------------------
# Fix '\' in directory variables on windows
#-------------------------------------------------------------------------------
$ENV{'CLASSPATH'}      = cleanPath($ENV{'CLASSPATH'})      if $ENV{'CLASSPATH'};
$ENV{'LOG4JPROPSBASE'} = cleanPath($ENV{'LOG4JPROPSBASE'}) if $ENV{'LOG4JPROPSBASE'};
$ENV{'LOCTBL'}         = cleanPath($ENV{'LOCTBL'})         if $ENV{'LOCTBL'};
$ENV{'SPTBL'}          = cleanPath($ENV{'SPTBL'})          if $ENV{'SPTBL'};
$ENV{'CDLPERL'}        = cleanPath($ENV{'CDLPERL'})        if $ENV{'CDLPERL'};


my $rc = 0;                             # Return code - 0 = success, 1 = failure

my %parms = (
             "help"       => undef,     # Should we display the help message?
             "config"     => undef,     # Name of a file containing alternate config parameters
             "singlefile" => undef,     # Are we prcocessing just one input file?
             "basedir"    => undef,     # The base directory for the conversion
             "indir"      => undef,     # The conversion input directory
             "infile"     => undef,     # The file name to convert
             "contacts"   => undef,     # The name of the contacts file
             "parmsValid" => undef      # Do we have valid parameters?
            );

my %cvtconf = ();                       # Conversion config parameters

#-------------------------------------------------------------------------------
# Associate each site with a converter
#-------------------------------------------------------------------------------
my %sitetable = (
                 "drcsl" => "org.cdlib.marcconvert.run.RunCSL",
                 "drema" => "org.cdlib.marcconvert.run.RunUCLA",
                 "drftv" => "org.cdlib.marcconvert.run.RunUCLA",
                 "drla"  => "org.cdlib.marcconvert.run.RunUCLA",
                 "enema" => "org.cdlib.marcconvert.run.RunENLA",
                 "enftv" => "org.cdlib.marcconvert.run.RunENLA",
                 "enla"  => "org.cdlib.marcconvert.run.RunENLA",
                 "evget" => "org.cdlib.marcconvert.run.RunNull",
                 "evser" => "org.cdlib.marcconvert.run.RunNull",
                 "evsub" => "org.cdlib.marcconvert.run.RunNull",
                 "evsum" => "org.cdlib.marcconvert.run.RunNull",
                 "exsb"  => "org.cdlib.marcconvert.run.RunUCSB",
                 "exucd" => "org.cdlib.marcconvert.run.RunUCD",
                 "glucb" => "org.cdlib.marcconvert.run.RunUCB",
                 "grclp" => "org.cdlib.marcconvert.run.RunNull",
                 "iidla" => "org.cdlib.marcconvert.run.RunUCDL",
                 "iigtu" => "org.cdlib.marcconvert.run.RunGTU",
                 "iiir"  => "org.cdlib.marcconvert.run.RunUCI",
                 "iiuci" => "org.cdlib.marcconvert.run.RunUCIR",
                 "iiriv" => "org.cdlib.marcconvert.run.RunUCR",
                 "iisc"  => "org.cdlib.marcconvert.run.RunUCSC",
                 "iisd"  => "org.cdlib.marcconvert.run.RunUCSD",
                 "iisf"  => "org.cdlib.marcconvert.run.RunUCSF",
                 "lcnam" => "org.cdlib.marcconvert.run.RunLCN",
                 "lcsub" => "org.cdlib.marcconvert.run.RunNull",
                 "lsgia" => "org.cdlib.marcconvert.run.RunNull",
                 "lslll" => "org.cdlib.marcconvert.run.RunNull",
                 "oc"    => "org.cdlib.marcconvert.run.RunOCLC",
                 "rlblt" => "org.cdlib.marcconvert.run.RunUCBL21",
                 "rlchs" => "org.cdlib.marcconvert.run.RunCHS21",
                 "rllbl" => "org.cdlib.marcconvert.run.RunLBL21",
                 "sisu"  => "org.cdlib.marcconvert.run.RunNull",
                 "uncas" => "org.cdlib.marcconvert.run.RunUnload",
                 "uncrl" => "org.cdlib.marcconvert.run.RunUnload",
                 "undla" => "org.cdlib.marcconvert.run.RunUnload",
                 "uneer" => "org.cdlib.marcconvert.run.RunUnload",
                 "ungia" => "org.cdlib.marcconvert.run.RunUnload",
                 "unhas" => "org.cdlib.marcconvert.run.RunUnload",
                 "unigs" => "org.cdlib.marcconvert.run.RunUnload",
                 "units" => "org.cdlib.marcconvert.run.RunUnload",
                 "unmtz" => "org.cdlib.marcconvert.run.RunUnload",
                 "unwrc" => "org.cdlib.marcconvert.run.RunUnload"
                );

#-------------------------------------------------------------------------------
# Days in months - used to validate the date from file name
# prior to using it to build the 005 date.
#-------------------------------------------------------------------------------
@main::daystbl = (
                  [31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31],
                  [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
                 );

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
# Parse the arguments.
#-------------------------------------------------------------------------------
if (scalar(@ARGV) == 1)
{
    parseSingleParm(\@ARGV, \%parms);
}
else
{
    parseParms(\@ARGV, \%parms);
}

#-------------------------------------------------------------------------------
# Handle -h or --help
#-------------------------------------------------------------------------------
if ($parms{'help'})
{
    print STDOUT $usage;
    exit(0);
}

#-------------------------------------------------------------------------------
# Handle an invalid or missing parameter conditions.
#-------------------------------------------------------------------------------
unless ($parms{'parmsValid'})
{
    print STDOUT "Invalid parameters specified\n";
    print STDOUT $usage;
    exit(0);
}

unless (defined $parms{'basedir'} && defined $parms{'singlefile'} && defined $parms{'indir'} )
{
    print STDOUT "Could not determine the following runtime parameters:\n";
    print STDOUT "  basedir\n" unless $parms{'basedir'};
    print STDOUT "  indir\n" unless $parms{'indir'};
    print STDOUT "  singlefile\n" unless $parms{'singlefile'};
    print STDOUT "  infile\n" if $parms{'singlefile'} && ! $parms{'infile'};
    print STDOUT "Invalid parameters specified\n";
    print STDOUT $usage;
    exit(0);
}

if ($main::debug)
{
    print STDERR ("\nAll set:\n");
    print STDERR ("  parms = ",
                  join(' ', @ARGV),
                  "\n");
    print STDERR ("  singlefile = ",
                  ($parms{'singlefile'} ? "true" : "false"), "\n");
    print STDERR ("  indir   = ",
                  (defined $parms{'indir'} ? "$parms{'indir'}" : "undefined"),
                  "\n");
    print STDERR ("  basedir = ",
                  (defined $parms{'basedir'} ? "$parms{'basedir'}" : "undefined"),
                  "\n");
    print STDERR ("  config  = ",
                  (defined $parms{'config'} ? "$parms{'config'}" : "undefined"),
                  "\n");
    print STDERR ("  contacts  = ",
                  (defined $parms{'contacts'} ? "$parms{'contacts'}" : "undefined"),
                  "\n");
    print STDERR ("  infile  = ",
                  (defined $parms{'infile'} ? "$parms{'infile'}" : "undefined"),
                  "\n");
}

#-------------------------------------------------------------------------------
# Make sure we have all the directories we will need.
#-------------------------------------------------------------------------------
mksrcdirs($parms{'basedir'});

#-------------------------------------------------------------------------------
# Parse the config file, if one was specified.
#-------------------------------------------------------------------------------
parseConfig($parms{'config'}, \%cvtconf) if $parms{'config'};


#-------------------------------------------------------------------------------
# Run the conversion, either for a single file, or over a directory.
#-------------------------------------------------------------------------------
if ($parms{'singlefile'})
{
    my %fnTokens = ();
    if (parseFileName($parms{'infile'}, \%fnTokens))
    {
        print STDERR "File name okay: $parms{'infile'}\n";
        my $convClass = getConvertClass(\%fnTokens, \%sitetable);

        if ($convClass)
        {
            $rc = processFile($parms{'basedir'}, # the base directory for this source
                              $parms{'indir'},   # the input directory to process
                              $parms{'infile'},  # the input file to process
                              $convClass,        # the name of the class to invoke
                              \%fnTokens,        # reference to the file name tokens hash
                              \%cvtconf,         # reference to the config hash
                              $parms{'contacts'} # the contacts file
                             );
        }
        else
        {
            print STDERR "No converter found for input source";
            $rc = 1;
        }
    }
    else
    {
        print STDERR "File name bad: $parms{'infile'}\n";
    }
}
else
{
    # Convert all files in a direcotry
    $rc = processDir($parms{'basedir'}, # the base directory for this source
                     $parms{'indir'},   # the input directory to process
                     \%sitetable,       # the map of sites to classname
                     \%cvtconf,         # reference to the config hash
                     $parms{'contacts'} # the contacts file
                    );
}

if ($rc > 0) { $rc = 1; }	# 512 gives success to TWS
exit($rc);

#-------------------------------------------------------------------------------
# End of runconvert main - Subroutines follow.
#-------------------------------------------------------------------------------


#-------------------------------------------------------------------------------
# Parse runtime parameters.
#-------------------------------------------------------------------------------
sub parseParms
{
    my $argv = shift;           # Reference to @ARGV
    my $parms = shift;          # Reference to %parms
    my $parm = undef;           # The invocation parameter
    my $parg = undef;           # The argument to the parameter

    my $argcnt = scalar(@$argv);
    print STDERR "Arg count = $argcnt\n" if $main::debug;

  getParam:
    while ( @$argv )
    {
        print STDERR "DEBUG(parseParms - top):\n" if ($main::debug);
        print STDERR "  parms = ", join(' ', @$argv), "\n" if $main::debug;
        last getParam unless ($parm = shift @$argv);
        print STDERR "DEBUG(parseParms - top): parm = '$parm'\n" if $main::debug;

        if ($parm eq '--help')
        {
            $parms->{'help'} = 1;
            $parms->{'parmsValid'} = 1;
            last getParam;
        }

        if ($parm eq '--config')
        {
            $parg = shift @$argv;
            print STDERR "DEBUG(parseParms - config): parg = '$parg'\n" if $main::debug;
            unless ($parg)
            {
                print STDOUT "No file specified for config parameter\n";
                $parms->{'parmsValid'} = 0;
                last getParam;
            }

            unless (-e $parg)
            {
                print STDOUT "File specified for config parameter '$parg' not found\n";
                $parms->{'parmsValid'} = 0;
                last getParam;
            }

            unless (-f $parg)
            {
                print STDOUT "File specified for config parameter '$parg' not a file\n";
                $parms->{'parmsValid'} = 0;
                last getParam;
            }

            $parms->{'config'} = $parg;
            $parms->{'parmsValid'} = 1;
            next getParam;
        }

        if ($parm eq '--contacts')
        {
            $parg = shift @$argv;
            print STDERR "DEBUG(parseParms - contacts): parg = '$parg'\n" if $main::debug;
            unless ($parg)
            {
                print STDOUT "No file specified for contacts parameter\n";
                $parms->{'parmsValid'} = 0;
                last getParam;
            }

            unless (-e $parg)
            {
                print STDOUT "File specified for contacts parameter '$parg' not found\n";
                $parms->{'parmsValid'} = 0;
                last getParam;
            }

            unless (-f $parg)
            {
                print STDOUT "File specified for contacts parameter '$parg' not a file\n";
                $parms->{'parmsValid'} = 0;
                last getParam;
            }

            $parms->{'contacts'} = $parg;
            $parms->{'parmsValid'} = 1;
            next getParam;
        }

        if ($parm eq '--basedir')
        {
            $parg = shift @$argv;
            print STDERR "DEBUG(parseParms - basedir): parg = '$parg'\n" if $main::debug;
            unless ($parg)
            {
                print STDOUT "No file specified for basedir parameter\n";
                $parms->{'parmsValid'} = 0;
                last getParam;
            }

            unless (-e $parg)
            {
                print STDOUT "Directory specified for basedir parameter '$parg not found\n";
                $parms->{'parmsValid'} = 0;
                last getParam;
            }

            unless (-d $parg)
            {
                print STDOUT "Directory specified for basedir parameter '$parg not a directory\n";
                $parms->{'parmsValid'} = 0;
                last getParam;
            }


            #-------------------------------------------------------------------------------
            # In order to avoid problems with relative paths for indir and basedir when we
            # switch working directory we need to get the cononical names for these.
            # When basedir will be the new current working direcory.
            #-------------------------------------------------------------------------------
            my $tmpcwd = getcwd(); # Save the current working direcory
            print STDERR "DEBUG(parseParms - basedir): starting CWD = $tmpcwd\n" if $main::debug;
            chdir "$parg";                  # Make base directory the working directory
            $parms->{'basedir'} = getcwd(); # Get the canonicial base directory
            chdir "$tmpcwd";                # Switch back to our starting CWD

            $parms->{'parmsValid'} = 1;
            next getParam;
        }

        print STDERR "DEBUG(parseParms - bottom): parm = '$parm'\n" if $main::debug;

        if ($parm =~ /^-\w$/ || $parm =~ /^--\w+$/)
        {
            print STDOUT "Invalid parameter specified: '$parm'\n";
            $parms->{'parmsValid'} = 0;
            last getParam;
        }

        parseFileParm($parm, $parms);
        last getParam;
    }
    # end of getParam block

    if ($main::debug)
    {
        print STDERR ("\nDEBUG(parseParms - end): Exiting parseParms:\n");
        print STDERR ("DEBUG(parseParms - end):   parms = ",
                      join(' ', @$argv),
                      "\n");
        print STDERR ("DEBUG(parseParms - end):   singlefile = ",
                      ($parms->{'singlefile'} ? "true" : "false"),
                      "\n");
        print STDERR ("DEBUG(parseParms - end):   indir   = ",
                      (defined $parms{'indir'} ? "$parms{'indir'}" : "undefined"),
                      "\n");
        print STDERR ("DEBUG(parseParms - end):   basedir = ",
                      (defined $parms{'basedir'} ? "$parms{'basedir'}" : "undefined"),
                      "\n");
        print STDERR ("DEBUG(parseParms - end):   config  = ",
                      (defined $parms{'config'} ? "$parms{'config'}" : "undefined"),
                      "\n");
        print STDERR ("DEBUG(parseParms - end):   infile  = ",
                      (defined $parms{'infile'} ? "$parms{'infile'}" : "undefined"),
                      "\n");
    }

    return;
}

#-------------------------------------------------------------------------------
# Parse a single runtime parameter.
#-------------------------------------------------------------------------------
sub parseSingleParm
{
    my $argv = shift;           # Reference to @ARGV
    my $parms = shift;          # Reference to %parms
    my $parm = undef;           # The invocation parameter

    $parm = $argv->[0];
    if ($parm eq '-h' || $parm eq '--help')
    {
        $parms->{'help'} = 1;
        $parms->{'parmsValid'} = 1;
    }
    else
    {
        parseFileParm($parm, $parms);
    }

    return;
}

#-------------------------------------------------------------------------------
# Parse a single runtime parameter.
#-------------------------------------------------------------------------------
sub parseFileParm
{
    my $parm = shift;           # File parameter
    my $parms = shift;          # Reference to %parms
    my $indir = undef;
    my $basedir = undef;
    my $tmpcwd = undef;

    $parm = cleanPath($parm);
    if (-e $parm)
    {
        $parms->{'singlefile'} = (-f $parm);
        print STDERR "DEBUG(parseFileParm): parm = $parm\n" if ($main::debug);
        print STDERR ("DEBUG(parseFileParm): singlefile = ",
                      ($parms->{'singlefile'} ? "true" : "false"),
                      "\n") if ($main::debug);

        if ($parms->{'singlefile'})
        {
            $parms->{'infile'} = basename($parm);
            $indir = dirname($parm);
            $basedir = dirname($indir);
            if ($main::debug)
            {
                print STDERR "DEBUG(parseFileParm - var): indir   = $indir\n";
                print STDERR "DEBUG(parseFileParm - var): basedir = $basedir\n";
                print STDERR "DEBUG(parseFileParm - var): infile  = $parms->{'infile'}\n";
            }
        }
        else
        {
            $indir = $parm;
            $basedir = dirname($parm);
            if ($main::debug)
            {
                print STDERR "DEBUG(parseFileParm - var): indir   = $indir\n";
                print STDERR "DEBUG(parseFileParm - var): basedir = $basedir\n";
            }
        }

        #-------------------------------------------------------------------------------
        # In order to avoid problems with relative paths for indir and basedir when we
        # switch working directory we need to get the cononical names for these.
        #-------------------------------------------------------------------------------
        $tmpcwd = getcwd();     # Save the current working direcory
        print STDERR "DEBUG(parseFileParm): starting CWD = $tmpcwd\n" if $main::debug;
        chdir "$indir";         # Make input directory the working directory
        $parms->{'indir'} = getcwd(); # Get the canonicial input directory
        chdir "$tmpcwd";        # Switch back to our starting CWD

        unless ($parms->{'basedir'}) # Only calculate basedir when necessary
        {
            chdir "$basedir";   # Make base directory the working directory
            $parms->{'basedir'} = getcwd(); # Get the canonicial base directory
            chdir "$tmpcwd";    # Switch back to our starting CWD
        }

        $parms->{'parmsValid'} = 1;
        if ($main::debug)
        {
            print STDERR ("DEBUG(hash): indir   = ",
                          (defined $parms{'indir'} ? "$parms{'indir'}" : "undefined"),
                          "\n");
            print STDERR ("DEBUG(hash): basedir = ",
                          (defined $parms{'basedir'} ? "$parms{'basedir'}" : "undefined"),
                          "\n");
            print STDERR ("DEBUG(hash): infile  = ",
                          (defined $parms{'infile'} ? "$parms{'infile'}" : "undefined"),
                          "\n");
        }
    }
    else
    {
        print STDOUT "Paramter: '$parm' not found\n";
        $parms->{'parmsValid'} = 0;
    }

    return;
}

#-------------------------------------------------------------------------------
# Parse a config file.
# If the file specified cannot be opened for input the script will die.
#-------------------------------------------------------------------------------
sub parseConfig
{
    my $config  = shift;
    my $cvtconf = shift;
    unless (open(CFILE, "<$config"))
    {
        die "Failed to open specified config file: '$config'\n";
    }

    while (<CFILE>)
    {
        chomp;
        my $name = undef;
        my $value = undef;
        ($name, $value) = parseConfigLine($_);
        if (defined $name)
        {
            $cvtconf->{$name} = $value;
        }
    }
}

#-------------------------------------------------------------------------------
# Parse a line from a config file.
#
# The format of a name value pair is:
#     Name = Value
#
# The Name is the first word in the line, not part of a comment.
# The '=' is optional, and is dropped.
# The Value is everything remaining to the right of the Name,
# that is not part of a comment.
#
# Comments begin with '#' and extend to the end of the line.
#-------------------------------------------------------------------------------
sub parseConfigLine
{
    my $line  = shift;
    my $name  = undef;
    my $value = undef;

    my $tmp = shift;

    print STDERR "DEBUG:(parseConfigLine)              line = '$line'\n" if $main::debug;

    $tmp = $line;
    $tmp =~ s/\#.*$//o; # Strip comments
    $tmp =~ s/^\s*//og; # Strip leading white space
    $tmp =~ s/\s*$//og; # Strip trailing white space

    print STDERR "DEBUG:(parseConfigLine - no cmt, spc) tmp = '$tmp'\n" if $main::debug;

    if (length($tmp) > 0)
    {
        if ($tmp =~ /=/)
        {
            # line contains an equal sign, so split on that
            ($name, $value) = split(/=/, $tmp, 2);
        }
        else
        {
            # No equal sign, so split on first non-leading space
            $tmp =~ s/(\S+)\s+//o;
            $name = $1;
            $value = $tmp;
        }
        print STDERR "DEBUG:(parseConfigLine - parsed) name = '$name'\n" if $main::debug;
        print STDERR "DEBUG:(parseConfigLine - parsed) value = '$value'\n" if $main::debug;

        if ($name)
        {
            $name =~ s/^\s*//og; # Strip leading white space
            $name =~ s/\s*$//og; # Strip trailing white space
        }
        if ($value)
        {
            $value =~ s/^\s*//og; # Strip leading white space
            $value =~ s/\s*$//og; # Strip trailing white space
        }
    }

    $name  = undef unless (defined $name && length($name) > 0);
    $value = undef unless (defined $value && length($value) > 0);

    return ($name, $value);
}

#-------------------------------------------------------------------------------
# Process a single file.
#-------------------------------------------------------------------------------
sub processFile
{
    my $rc        = 0;                  # Return code
    my $basedir   = shift;              # the base directory for this source
    my $indir     = shift;              # the input directory to process
    my $infile    = shift;              # the input file to process
    my $convClass = shift;              # the name of the class to invoke
    my $fnTokens  = shift;              # reference to the file name tokens hash
    my $cvtconf   = shift;              # reference to the config hash
    my $contacts  = shift;              # the contacts file

    if ($main::debug)
    {
        print STDERR "processFile: Base directory $basedir\n";
        print STDERR "processFile: Input directory $indir\n";
        print STDERR "processFile: Processing file $infile\n";
        print STDERR "processFile: Converter class $convClass\n";
        print STDERR "processFile: fnTokens = $fnTokens\n";

        print STDERR "processFile: fnTokens count = ", scalar(keys %$fnTokens), "\n";
        foreach my $key (keys %$fnTokens)
        {
            print STDERR "processFile: fnToken[$key] = $fnTokens->{$key}\n";
        }
    }

    my $site = undef;
    $site  = $fnTokens->{'system'} if $fnTokens->{'system'};
    $site .= $fnTokens->{'source'} if $fnTokens->{'source'};
    my $dtTokens = $fnTokens->{'dtTokens'};   # reference to array holding the date tokens

    if ($main::debug)
    {
        print STDERR "processFile: dtTokens size = ", scalar(@$dtTokens), "\n";
        for (my $i = 0; $i < scalar(@$dtTokens); $i++)
        {
            print STDERR "processFile: dtTokens[$i] = ", $dtTokens->[$i], "\n";
        }
    }

    my $fdatepre  = $dtTokens->[0];
    my $fdate_yy  = $dtTokens->[1];
    my $fdate_mm  = $dtTokens->[2];
    my $fdate_dd  = $dtTokens->[3];
    my $fdatepost = $dtTokens->[4];

    print STDERR "returned date tokens: '$fdatepre, $fdate_yy, $fdate_mm, $fdate_dd, $fdatepost'\n"
    if ($main::debug);

    my $filedate = make005Date($fdate_yy, $fdate_mm, $fdate_dd);

    # Build log4j file
    my ($logconf, $logfile) = createTempLog4jProps($basedir, $fnTokens);
    print STDERR "processFile: logconf = $logconf\n" if ($main::debug);

    # Build config file
    my $cp = dirname($logconf) . "$Config{path_sep}" . "$ENV{'CLASSPATH'}";
    print STDERR "rt classpath = $cp\n" if ($main::debug);

    my ($convconf, $statsfile, $reportfile) = createTempConfig($basedir, $indir, $filedate,
                                                               $fnTokens, $cvtconf);

    # construct preconvert filename
    my $reportfilePreconvert;
    ($reportfilePreconvert = $reportfile) =~ s/cdl_convert/cdl_preconvert/;
    $reportfilePreconvert =~ s/eireport\./mrgreport\./;

    print STDERR "processFile: convconf = $convconf\n" if ($main::debug);

    if ($logconf && $convconf)
    {
        # Assemble java command
        my @cnvCmd = ("java",
                      "-Dlog4j.configuration=" . basename($logconf),
                      "-Dconfig=$convconf",
                      "-cp", "$cp",
                      "$convClass");

        print STDERR "\nprocessFile: cmd = ", (join("\n", @cnvCmd)), "\n\n" if ($main::debug);

        # Run converter - quit on error
        #----smg
        # Remove for testing
        #
        $rc = system(@cnvCmd);
	$rc = int($rc / 256);

        # Rename input file to processed directory
        if ($rc == 0)
        {
            rename("$indir/$infile", "$basedir/eiprocessed/$infile");
            $rc = system("perl $ENV{'CDLPERL'}/cnvreport.pl $statsfile $logfile $reportfile");
            if ($rc == 0 && defined $contacts)
            {
		my $working = $reportfile;
		if ($ENV{PRECONVERT} eq "TRUE")
		{
			$working = "/tmp/" . basename($reportfile);
			open (WORK, ">$working") || die "could not create temporary file: $working";
			
			# Add preconvert report file
			if (-e $reportfilePreconvert)
			{
				open(RFP, "<$reportfilePreconvert");
				my @rfp = <RFP>;
				close(RFP);
				print WORK "@rfp";
				print WORK "=" x 80;
			}
			else
			{
                    		print STDERR "Warning: preconverter report file not found, so not sent\n";
			}
			# Add convert report file
			if (-e $reportfile)
			{
				open(RFC, "<$reportfile");
				my @rfc = <RFC>;
				close(RFC);
				print WORK "@rfc";
			}
			else
			{
                    		print STDERR "Warning: converter report file not found, so not sent\n";
			}
			close (WORK);
		}
                if (-e $working)
                {
                    $rc = system("perl $ENV{'CDLPERL'}/cnvnotify.pl --source "
                                 . "$site --contacts $contacts $working");
                    if ($ENV{PRECONVERT} eq "TRUE") { unlink $working; }
                }
                else
                {
                    print STDERR "Warning: report file not created, so not sent\n";
                }
            }
        }

        # Delete the temp files
        unlink $logconf unless $main::debug;
        unlink $convconf unless $main::debug;
    }
    else
    {
        $rc = 1;
    }

    return $rc;
}

#-------------------------------------------------------------------------------
# Process an entire directory.
#-------------------------------------------------------------------------------
sub processDir
{
    my $rc        = 0;                  # Return code
    my $basedir   = shift;              # the base directory for this source
    my $indir     = shift;              # the input directory to process
    my $sitetable = shift;              # reference to the site table hash
    my $cvtconf   = shift;              # reference to the config hash
    my $contacts  = shift;              # the contacts file

#   if ($main::debug)
#   {
#       print STDERR "processDir: indir = $indir\n";
#       print STDERR "processDir: basedir = $basedir\n";
#       #print STDERR "printing sitetable:\n";
#       #foreach my $site (keys(%$sitetable))
#       #{
#       #   print STDERR "  $site -> $sitetable->{$site}\n";
#       #}
#   }

    print STDERR "processDir: Processing directory $indir\n" if ($main::debug);

    unless (opendir INDIR, $indir)
    {
        print STDERR "Could not open directory $indir\n";
        return 1;
    }

    my @filelist = grep !/^\.\.?$/, readdir INDIR;
    closedir INDIR;

    foreach my $infile (@filelist)
    {
        last if ($rc > 0);

        if (-f "$indir/$infile")
        {
            print STDERR "processDir: Looking at file $indir/$infile\n" if $main::debug;
            my %fnTokens = ();
            if (parseFileName($infile, \%fnTokens))
            {
                print STDERR "processDir: File name okay: $indir/$infile\n" if $main::debug;
                my $convClass = getConvertClass(\%fnTokens, $sitetable);

                if ($convClass)
                {
                    $rc = processFile($basedir,   # the base directory for this source
                                      $indir,     # the input directory to process
                                      $infile,    # the input file to process
                                      $convClass, # the name of the class to invoke
                                      \%fnTokens, # reference to the file name tokens hash
                                      $cvtconf,   # reference to the config hash
                                      $contacts   # the contacts file
                                     );
                }
                else
                {
                    print STDERR "No converter found for input source";
                    $rc = 1;
                }
            }
            else
            {
                print STDERR "File name bad: $indir/$infile\n";
            }
        }
    }

    return $rc;

}

#-------------------------------------------------------------------------------
# Lookup the class name associated with this source using the site
# info from the file name.
#-------------------------------------------------------------------------------
sub getConvertClass
{
    my $fnTokens = shift;
    my $sitetable = shift;
    my $site = undef;
    $site  = $fnTokens->{'system'} if $fnTokens->{'system'};
    $site .= $fnTokens->{'source'} if $fnTokens->{'source'};
    my $convClass = $sitetable->{lc($site)} if $site;
    return $convClass

}


#-------------------------------------------------------------------------------
# Parse the file name.
# Return 1 (true) if the file name is valid, otherwise 0 (false).
# Set the tokens in the array passed as a reference in the second argument.
#-------------------------------------------------------------------------------
sub parseFileName
{
    my $rc       = 1;                   # Assume success, any error will set it to failure.
    my $fname    = shift;               # the file name to parse
    my $fnTokens = shift;               # reference to hash table to hold file name tokens
    my @dtTokens = ();                  # array to hold the date tokens
    my @tokens   = ();                  # array to hold the raw file name tokens

    @tokens = split(/\./, $fname, 6);

    if ($main::debug)
    {
        print STDERR "sj_fname = '", (join(":", @tokens)), "'\n";
    }

    #---------------------------------------------------------------------------
    # Because oclc has no source token it requires one fewer token then all
    # other systems, so we handle that case separately.
    #
    # For oclc:
    # Check that we have at least four tokens in the file name and that each
    # of them exists. Otherwise the file name is invalid and no further
    # processing takes place.
    #
    # For all others:
    # Check that we have at least five tokens in the file name and that each
    # of them exists. Otherwise the file name is invalid and no further
    # processing takes place.
    #---------------------------------------------------------------------------
    if (lc($tokens[1]) eq 'oc')
    {
        if (! (scalar(@tokens) > 3 && $tokens[0] && $tokens[1]
               && $tokens[2] && $tokens[3]) )
        {
            print STDERR "parseFileName: Invalid file name: $fname\n";
            $rc = 0;
        }
    }
    else
    {
        if (! (scalar(@tokens) > 4 && $tokens[0] && $tokens[1]
               && $tokens[2] && $tokens[3] && $tokens[4]))
        {
            print STDERR "parseFileName: Invalid file name: $fname\n";
            $rc = 0;
        }
    }

    if ($rc != 0)
    {
        #-----------------------------------------------------------------------
        # Check the prefix token, and if we like it, save it
        #-----------------------------------------------------------------------
        if (lc($tokens[0]) ne 'cls' && lc($tokens[0]) ne 'eip')
        {
            print STDERR "File name ($fname) prefix token is not 'cls' or 'eip': $tokens[0]\n";
            $rc = 0;
        }
        else
        {
            $fnTokens->{'prefix'} = $tokens[0];
        }

        #-----------------------------------------------------------------------
        # Check the system and source tokens. If the system is 'oc' then
        # there will be not source token, and all the other tokens will
        # appear sooner in the array, so of course we have to handle that
        # case separately.
        #
        # First we handle the all the non-oclc systems, the we handle oclc.
        #-----------------------------------------------------------------------
        if (lc($tokens[1]) ne 'oc')
        {
            $fnTokens->{'system'} = $tokens[1]; # Save the system token
            $fnTokens->{'source'} = $tokens[2]; # Save the source token

            #-------------------------------------------------------------------
            # Check that tye type token is valid, and if it is, save it.
            #-------------------------------------------------------------------
            if (lc($tokens[3]) ne 'trans')
            {
                print STDERR "File name ($fname) type token is not 'trans': $tokens[3]\n";
                $rc = 0;
            }
            else
            {
                $fnTokens->{'type'} = $tokens[3];
            }

            #-------------------------------------------------------------------
            # Parse the date token. If we have a valid date save the date token
            # array in the name token hash.
            #-------------------------------------------------------------------
            if (parseFDate($tokens[4], \@dtTokens))
            {
                $fnTokens->{'fdate'} = $tokens[4];
                $fnTokens->{'dtTokens'} = \@dtTokens;
            }
            else
            {
                print STDERR "File name ($fname) date token ($tokens[4]) invalid\n";
                $rc = 0;
            }

            #-------------------------------------------------------------------
            # Save the suffix token, if it exists.
            #-------------------------------------------------------------------
            if (defined $tokens[5])
            {
                $fnTokens->{'suffix'} = $tokens[5];
            }
        }
        else
        {
            #-------------------------------------------------------------------
            # Processing oclc, which has no source token.
            #-------------------------------------------------------------------
            $fnTokens->{'system'} = $tokens[1]; # Save the system token

            #-------------------------------------------------------------------
            # Check that tye type token is valid, and if it is, save it.
            #-------------------------------------------------------------------
            if (lc($tokens[2]) ne 'trans')
            {
                print STDERR "File name ($fname) type token is not 'trans': $tokens[2]\n";
                $rc = 0;
            }
            else
            {
                $fnTokens->{'type'} = $tokens[2];
            }

            #-------------------------------------------------------------------
            # Parse the date token. If we have a valid date save the date token
            # array in the name token hash.
            #-------------------------------------------------------------------
            if (parseFDate($tokens[3], \@dtTokens))
            {
                $fnTokens->{'fdate'} = $tokens[3];
                $fnTokens->{'dtTokens'} = \@dtTokens;
            }
            else
            {
                print STDERR "File name ($fname) date token ($tokens[3]) invalid\n";
                $rc = 0;
            }

            #-------------------------------------------------------------------
            # Save the suffix token, if it exists. If the suffix contains more
            # than one part, i.e. "foo.bar", then everything after the "." will
            # be in token[5], so append that to the suffix, along with the ".".
            #-------------------------------------------------------------------
            if (defined $tokens[4])
            {
                $fnTokens->{'suffix'} = $tokens[4];
                if (defined $tokens[5])
                {
                    $fnTokens->{'suffix'} .= "." . $tokens[5];
                }
            }
        }

        if ($main::debug)
        {
            print STDERR "parseFileName: dtTokens size = ", scalar(@dtTokens), "\n";

            for (my $i = 0; $i < scalar(@dtTokens); $i++)
            {
                print STDERR "parseFileName: dtTokens[$i] = ", $dtTokens[$i], "\n";
            }
        }
    }

    return $rc;
}


#-------------------------------------------------------------------------------
# Parse the date token in the file name.
#
# The first character must be either 'd' or 'r', followed by 6 digits,
# of the form YYMMDD, and optionally a trailing character suffix.
#
# Return 1 (true) if the file date is valid, otherwise 0 (false).
#
# The tokens are set in the array passed a reference in the second
# argument.
#-------------------------------------------------------------------------------
sub parseFDate
{
    my $rc = 1;                         # Assume success, any error will set it to failure.
    my $fdToken   = shift;              # the file date token
    my $dtTokens  = shift;              # reference to array to hold the date tokens

    my $fdatepre  = undef;              # File date prefix
    my $fdate_yy  = undef;              # File date year
    my $fdate_mm  = undef;              # File date month
    my $fdate_dd  = undef;              # File date day
    my $fdatepost = undef;              # File date suffix (optional)

    if ($fdToken =~ /^([dr])(\d\d)(\d\d)(\d\d)(.*)$/ix)
    {
        $fdatepre = $1;
        $fdate_yy = $2;
        $fdate_mm = $3;
        $fdate_dd = $4;
        $fdatepost = $5;

        print STDERR "parsed date token: '$fdatepre, $fdate_yy, $fdate_mm, $fdate_dd, $fdatepost'\n"
        if $main::debug;

        #-----------------------------------------------------------------------
        # Validate the year, month, and day. If we have a valid date, save the
        # tokens in the caller's token array.
        #-----------------------------------------------------------------------
        if (validateDate($fdate_yy, $fdate_mm, $fdate_dd))
        {
            print STDERR "parseFDate: pre push size dtTokens = ", scalar(@$dtTokens), "\n"
            if $main::debug;

            @$dtTokens = ($fdatepre, $fdate_yy, $fdate_mm, $fdate_dd, $fdatepost);

            print STDERR "parseFDate: post push size dtTokens = ", scalar(@$dtTokens), "\n"
            if $main::debug;
        }
        else
        {
            print STDERR "Date token ($fdToken) specifies invalid date\n";
            $rc = 0;
        }
    }
    else
    {
        print "Date token ($fdToken) has invalid format\n";
        $rc = 0;
    }

    return $rc;
}

#-------------------------------------------------------------------------------
# Validate the month is in the range 1 throught 12, and the day is in the
# allowable range for the month. Uses a global variable for the days lookup
# to prevent allocating it with each call.
#
# Note: since the year is only 2 digits a simple mod 4 suffices for
# leap year detection.
#-------------------------------------------------------------------------------
sub validateDate
{
    my $rc = 1; # Assume success, any error will set it to failure.
    my $yy = shift;
    my $mm = shift;
    my $dd = shift;

    my $leap = $yy % 4;
    $leap = 1 if $leap > 0;

    if ($mm > 0 && $mm < 13)
    {
        my $ddmax = $main::daystbl[$leap][$mm - 1];
        if ($dd < 1  || $dd > $ddmax)
        {
            print STDERR ("Days ($dd) out of range for month ($mm) - max = ",
                          ($ddmax), "\n");
            $rc = 0;
        }
    }
    else
    {
        print STDERR "Invalid month ($mm) )specified\n";
        $rc = 0;
    }

    return $rc;
}

#-------------------------------------------------------------------------------
# Create a date field the conforms to the Marc standard for the 005 field.
#-------------------------------------------------------------------------------
sub make005Date
{
    my $yy = shift;
    my $mm = shift;
    my $dd = shift;
    print STDERR "make005Date: yy = $yy mm = $mm dd = $dd\n" if ($main::debug);

    my $marcDate = "20";
    $marcDate .= substr(('0' . "$yy"), -2);
    $marcDate .= substr(('0' . "$mm"), -2);
    $marcDate .= substr(('0' . "$dd"), -2);
    $marcDate .= "000000.0";
    print STDERR "make005Date: marcDate = $marcDate\n" if ($main::debug);

    return $marcDate;
}

#-------------------------------------------------------------------------------
# Create a config file for this source using a base template file.
#-------------------------------------------------------------------------------
sub createTempConfig
{
    my $basedir  = shift;       # Conversion base directory
    my $indir    = shift;       # The input directory
    my $filedate = shift;       # Filedate in 005 format
    my $fnTokens = shift;       # refence to the file name tokens hash
    my $cvtconf  = shift;       # Optional reference to hastable of config parameters

    my %config   = ();          # Local config hash

    #---------------------------------------------------------------------------
    # Assemble the front end and back end of the output file names from the
    # segments of the input file name.
    #---------------------------------------------------------------------------

    #---------------------------------------------------------------------------
    # First pull all the segments out of the file name token hash.
    # This is just a convenience to cut down on all those dereferences and
    # hopefully make the code a little easier to read.
    #---------------------------------------------------------------------------
    my $ft1 = $fnTokens->{'prefix'} if $fnTokens->{'prefix'};
    my $ft2 = $fnTokens->{'system'} if $fnTokens->{'system'};
    my $ft3 = $fnTokens->{'source'} if $fnTokens->{'source'};
    my $ft4 = $fnTokens->{'type'}   if $fnTokens->{'type'};
    my $ft5 = $fnTokens->{'fdate'}  if $fnTokens->{'fdate'};
    my $ft6 = $fnTokens->{'suffix'} if $fnTokens->{'suffix'};

    #---------------------------------------------------------------------------
    # The front end consists of the prefix and system tokens, and the source
    # token, if it exists. The souce token should be there for most sources,
    # OCLC being, for now, the one exception, although more may follow.
    #---------------------------------------------------------------------------
    my $fnfront = "$ft1.$ft2";
    $fnfront .= ".$ft3" if $ft3;
    print STDERR "createTempConfig: fnfront = '$fnfront'\n" if $main::debug;

    #---------------------------------------------------------------------------
    # The back end consists of the file data token, and any suffix that may
    # exist. Usually the suffix will be empty, but the campuses may use it,
    # so we allow for it.
    #---------------------------------------------------------------------------
    my $fnback = "$ft5";
    $fnback .= ".$ft6" if $ft6;
    print STDERR "createTempConfig: fnback = '$fnback'\n" if $main::debug;

    my $configFile = cleanPath(catfile("$basedir", "eitmp",
                                       "$fnfront.convconf.$fnback.$main::rtStr.$main::pid"));

    #-------------------------------------------------------------------------------
    # Fill in the config hash. First we copy the hash assemble from the --config
    # option, which may be empty, then we fill in any missing required entries
    # with our local values.
    #-------------------------------------------------------------------------------
    %config = %$cvtconf;

    #-------------------------------------------------------------------------------
    # Put any missing required paramaters, which may be all, into the config hash.
    #-------------------------------------------------------------------------------
    unless ($config{'infile'})
    {
        $config{'infile'} = cleanPath(catfile("$indir", "$fnfront.$ft4.$fnback"));
    }

    unless ($config{'outfile'})
    {
        $config{'outfile'} = cleanPath(catfile("$basedir", "eiout",
                                               "$fnfront.eiout.$fnback"));
    }

    unless ($config{'errorfile'})
    {
        $config{'errorfile'} = cleanPath(catfile("$basedir", "eierror",
                                                 "$fnfront.eierror.$fnback"));
    }

    unless ($config{'rejectfile'})
    {
        $config{'rejectfile'} = cleanPath(catfile("$basedir", "eireject",
                                                  "$fnfront.eireject.$fnback"));
    }

    unless ($config{'reportfile'})
    {
        $config{'reportfile'} = cleanPath(catfile("$basedir", "eistats",
                                                  "$fnfront.eistats.$fnback"));
    }

    unless ($config{'loctabfile'})
    {
        $config{'loctabfile'} = "$ENV{'LOCTBL'}";
    }

    unless ($config{'sharedprintfile'})
    {
        $config{'sharedprintfile'} = "$ENV{'SPTBL'}";
    }

    unless ($config{'filedate'})
    {
        $config{'filedate'} = "$filedate";
    }

    unless ($config{'forcedate'})
    {
        $config{'forcedate'} = "N";
    }

    unless ($config{'validateout'})
    {
        $config{'validateout'} = "Y";
    }

    #-------------------------------------------------------------------------------
    # Get the stats and report file names to return.
    #-------------------------------------------------------------------------------
    my $statsfile = $config{'reportfile'};
    my $reportfile = cleanPath(catfile("$basedir", "eireport",
                                       "$fnfront.eireport.$fnback"));

    #-------------------------------------------------------------------------------
    # Dump the config hash into the config file.
    #-------------------------------------------------------------------------------
    open (FOUT, ">$configFile") || die "Could not open file '>$configFile\n";

    foreach my $key (sort keys(%config))
    {
        my $value = $config{$key};
        print STDERR "createTempConfig(fout): key = '$key' -> value = '$value'\n" if $main::debug;

        if ($key && $value)
        {
            print FOUT "$key = $value\n";
        }
    }

    close FOUT;

    return ($configFile, $statsfile, $reportfile);
}

#-------------------------------------------------------------------------------
# Create a log4j properties file for this source using a base template file.
#-------------------------------------------------------------------------------
sub createTempLog4jProps
{
    my $basedir  = shift; # Conversion base directory
    my $fnTokens = shift; # refence to the file name tokens hash

    #---------------------------------------------------------------------------
    # Assemble the front end and back end of the log file names from the
    # segments of the input file name.
    #
    # NOTE: This is the same process as in createTempConfig(), and as such
    # it may be a candidate for a separate subroutine.
    #---------------------------------------------------------------------------

    #---------------------------------------------------------------------------
    # First pull all the segments out of the file name token hash.
    # This is just a convenience to cut down on all those dereferences and
    # hopefully make the code a little easier to read.
    #---------------------------------------------------------------------------
    my $ft1 = $fnTokens->{'prefix'} if $fnTokens->{'prefix'};
    my $ft2 = $fnTokens->{'system'} if $fnTokens->{'system'};
    my $ft3 = $fnTokens->{'source'} if $fnTokens->{'source'};
    my $ft4 = $fnTokens->{'type'}   if $fnTokens->{'type'};
    my $ft5 = $fnTokens->{'fdate'}  if $fnTokens->{'fdate'};
    my $ft6 = $fnTokens->{'suffix'} if $fnTokens->{'suffix'};

    #---------------------------------------------------------------------------
    # The front end consists of the prefix and system tokens, and the source
    # token, if it exists. The souce token should be there for most sources,
    # OCLC being, for now, the one exception, although more may follow.
    #---------------------------------------------------------------------------
    my $fnfront = "$ft1.$ft2";
    $fnfront .= ".$ft3" if $ft3;
    print STDERR "createTempLog4jProps: fnfront = '$fnfront'\n" if $main::debug;

    #---------------------------------------------------------------------------
    # The back end consists of the file data token, and any suffix that may
    # exist. Usually the suffix will be empty, but the campuses may use it,
    # so we allow for it.
    #---------------------------------------------------------------------------
    my $fnback = "$ft5";
    $fnback .= ".$ft6" if $ft6;
    print STDERR "createTempLog4jProps: fnback = '$fnback'\n" if $main::debug;

    my $log4jPropsIn  = $ENV{'LOG4JPROPSBASE'};

    die "Could not locate base log4j props file: '$log4jPropsIn'\n" unless (-f $log4jPropsIn);

    my $log4jPropsName = basename($log4jPropsIn);
    my $log4jPropsOut  = cleanPath(catfile("$basedir", "eitmp",
                                           "$fnfront.log4jprops.$fnback.$main::rtStr.$main::pid"));

    my $lfName  = cleanPath(catfile("$basedir", "eilog", "$fnfront.log.$fnback.$main::rtStr"));
    my $rlfName = cleanPath(catfile("$basedir", "eilog", "$fnfront.rolling.log"));

    open (FIN,  "<$log4jPropsIn")  || die "Could not open file '<$log4jPropsIn\n";
    open (FOUT, ">$log4jPropsOut") || die "Could not open file '>$log4jPropsOut\n";

    while (<FIN>)
    {
        s/\%source\%/$lfName/g;
        s/\%rollingsource\%/$rlfName/g;
        print FOUT "$_";
    }

    close FIN;
    close FOUT;

    return ($log4jPropsOut, $lfName);
}

#-------------------------------------------------------------------------------
# Fix nasty windows back slashes in path strings, and strip trailing '/',
# unless the it is the only character, i.e. the root directory.
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
# Make any directories missing from our directory tree.
#-------------------------------------------------------------------------------
sub mksrcdirs
{
    my $srcdir = shift;
    mkdir("$srcdir", 0777);
    mkdir("$srcdir/eierror", 0777);
    mkdir("$srcdir/eiin", 0777);
    mkdir("$srcdir/eilog", 0777);
    mkdir("$srcdir/eiout", 0777);
    mkdir("$srcdir/eiprocessed", 0777);
    mkdir("$srcdir/eireject", 0777);
    mkdir("$srcdir/eireport", 0777);
    mkdir("$srcdir/eistats", 0777);
    mkdir("$srcdir/eitmp", 0777);
    return;
}
