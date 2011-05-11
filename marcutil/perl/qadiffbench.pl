#!/usr/bin/perl -w
#
# Run the marc convert benchmark test results.
#
# Usage: qadiffbench.pl basedir newdir <all | site1 site2 ...>
#
# Where site is in the list:
#   chs, gtu, lcn, lbl, oclc, ucb, ucbl, ucd, ucdl,
#   uci, ucla, ucr, ucsb, ucsc, ucsd, ucsf, csl, unload
#
# This script assumes that an approriate config file exists for each site
# and has a name of the form 'site.config.txt', e.g. 'chs.config.txt'.
#
# CVS Tracking info:
#
# $Revision: 1.15 $
# $Author: aleph16 $
# $Date: 2010/04/12 22:28:57 $
# $Id: qadiffbench.pl,v 1.15 2010/04/12 22:28:57 aleph16 Exp $
#
#-------------------------------------------------------------------------------

use strict;
use Config;
use Cwd qw(chdir cwd getcwd);
use English;
use File::Basename;
use File::Copy;
use Getopt::Long;
use IO::File;

$main::debug = 0;    # set to true to turn on debug mode

#-------------------------------------------------------------------------------
# Handy usage string in case the user requests --help
#-------------------------------------------------------------------------------
my $usage = "\n"
          . "Usage: qadiffbench.pl <--difflist file> basedir testdir diffdir <all | site1 site2 ...>\n"
          . "\n"
          . "Where site is in the list:\n"
          . "  chs, gtu, lcn, lbl, oclc, ucb, ucbl, ucd, ucdl,\n"
          . "  uci, ucla, ucr, ucsb, ucsc, ucsd, ucsf, csl, unload\n"
          . "\n"
          . "The --difflist option is not required."
          . "\n"
          . "This script diffs the following files for each site:\n"
          . "  site.convert.fmt.txt\n"
          . "  site.error.fmt.txt\n"
          . "  site.reject.fmt.txt\n"
          . "  site.reject.messages.txt\n"
          . "  site.report.txt\n"
          . "\n";

#-------------------------------------------------------------------------------
# Handy usage string in case the user requests --help
#-------------------------------------------------------------------------------
$ENV{'MB_HOME'} = '.'                     unless $ENV{'MB_HOME'};
$ENV{'MB_PERL'} = "$ENV{'MB_HOME'}/perl"  unless $ENV{'MB_PERL'};

#-------------------------------------------------------------------------------
# Fix $HOME and $PERLBASE on windows
#-------------------------------------------------------------------------------
$ENV{'MB_HOME'} = cleanPath($ENV{'MB_HOME'}) if $ENV{'MB_HOME'};
$ENV{'MB_PERL'} = cleanPath($ENV{'MB_PERL'}) if $ENV{'MB_PERL'};


#-------------------------------------------------------------------------------
# Setup the diff program name - this needs to be a wrapper script so we
# can get the shell to redirect the output.
#-------------------------------------------------------------------------------
print STDERR "osname = $Config{'osname'}\n" if $main::debug;
my $diffprog = ("$Config{'osname'}" eq 'MSWin32'
                ? uncleanPath($ENV{'MB_HOME'}) . "\\scripts\\diffwrap.bat"
                : "$ENV{'MB_HOME'}/scripts/diffwrap.sh");

my @diffparams = ('--strip-trailing-cr', '--ignore-matching-lines="\(Start\)\|\(End\)"');

print STDERR "diff wrapper = $diffprog\n" if $main::debug;
print STDERR "diff params = ", (join(' ',@diffparams)), "\n";

#-------------------------------------------------------------------------------
# The set of files to diff
#-------------------------------------------------------------------------------
my @dffiles = (
               "convert.fmt.txt",
               "error.fmt.txt",
               "reject.fmt.txt",
               "reject.messages.txt",
               "report.txt"
              );


#-------------------------------------------------------------------------------
# The sites valid to process
#-------------------------------------------------------------------------------
my %sitetable = (
                 "ber"    => "ber",
                 "chs"    => "chs",
                 "csl"    => "csl",
                 "gtu"    => "gtu",
             "enla-merge" => "enla-merge",
                 "enla"   => "enla",
                 "lcn"    => "lcn",
                 "lbl"    => "lbl",
                 "mer"    => "mer",
                 "oclc"   => "oclc",
                 "ucb"    => "ucb",
                 "ucbl"   => "ucbl",
                 "ucdl"   => "ucdl",
                 "ucd"    => "ucd",
                 "uci"    => "uci",
                 #"ucla"   => "ucla",
                 "ucr"    => "ucr",
                 "ucsb"   => "ucsb",
                 "ucsc"   => "ucsc",
                 "ucsd"   => "ucsd",
                 "ucsf"   => "ucsf",
                 "unload" => "unload"
                );


my (%runtable);        # built up with sites from the command line
my $basedir  = undef;  # the directory containing the base benchmark results
my $testdir  = undef;  # the directory containing the new test results
my $diffdir  = undef;  # the directory to place the diff output
my $site     = undef;  # the site to process, this may be repeated
my $difflist = undef;  # file to contain a list of files with non-empy diffs
my $help     = undef;  # --help indicator
my $rc       = 0;      # Return code
my $dlfh     = undef;  # file handle for difflist

#-------------------------------------------------------------------------------
# End of variable declarations
#-------------------------------------------------------------------------------

if ($main::debug)
{
    my $key = undef;
    print STDERR "---------------------------------------------\n";
    print STDERR "Printing sitetable hash...\n";
    foreach $key (sort(keys %sitetable))
    {
        my $value = $sitetable{$key};
        print STDERR "key = '$key'  value = '$value'\n";
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

if ($main::debug)
{
    print STDERR "cmd line = $0 ", (join(' ', @ARGV)), "\n";
}

#-------------------------------------------------------------------------------
# Get the --<thingy> options
#-------------------------------------------------------------------------------
GetOptions('difflist:s' => \$difflist,
           'help'       => \$help);

#-------------------------------------------------------------------------------
# If --help was specified print the usage and exit
#-------------------------------------------------------------------------------
if ($help)
{
    print STDERR $usage;
    exit(0);
}

#-------------------------------------------------------------------------------
# Clean up difflist if --difflist was specified
#-------------------------------------------------------------------------------
$difflist = cleanPath($difflist) if $difflist;

#-------------------------------------------------------------------------------
# Make sure we have some positional arguments
#-------------------------------------------------------------------------------
unless (@ARGV)
{
    print STDERR $usage;
    exit(0);
}

#-------------------------------------------------------------------------------
# Get our directories
#-------------------------------------------------------------------------------
$basedir  = cleanPath(shift);
$testdir  = cleanPath(shift);
$diffdir  = cleanPath(shift);

#-------------------------------------------------------------------------------
# Check that our input directories exist, and that our output directory either
# does not exist, or exists as a directory.
#-------------------------------------------------------------------------------
unless (-d $basedir )
{
    print STDERR "Base directory: '$basedir' does not exist or is not a directory\n";
    exit(1);
}

unless (-d $testdir )
{
    print STDERR "Test directory: '$testdir' does not exist or is not a directory\n";
    exit(1);
}

unless (-d $diffdir )
{
    if (-e $diffdir)
    {
        print STDERR "Diff directory: '$diffdir' exists but is not a directory\n";
        exit(1);
    }
    else
    {
        mkdir $diffdir;
    }
}


#-------------------------------------------------------------------------------
# Collect the sites to process in a hashtable after verifying they are
# known sites.
#-------------------------------------------------------------------------------
while ($site = lc(shift))
{
    # Handle 'all'
    if ($site eq 'all')
    {
        %runtable = %sitetable;
        last;
    }

    if ($site eq 'rlin')
    {
        unshift(@ARGV, ('chs', 'lbl', 'ucbl'));
        next;
    }

    # Get the command table entry if we recognize this site.
    # Otherwise print an error message.
    if (exists $sitetable{$site})
    {
        $runtable{$site} = $sitetable{$site};
    }
    else
    {
        print STDOUT "Unrecognized site: '$site'\n";
        next;
    }

    if ($main::debug)
    {
        print STDERR "---------------------------------------------\n";
        print STDERR "Printing runtable hash after adding entry '$site'...\n";
        foreach my $key (sort(keys %runtable))
        {
            my $value = $runtable{$key};
            print STDERR "key = '$key'  value = '$value'\n";
        }
        print STDERR "---------------------------------------------\n";
    }
}

#-------------------------------------------------------------------------------
# Quit if no sites were selected.
#-------------------------------------------------------------------------------
unless (%runtable)
{
    print STDOUT "No recognized sites to process\n\n";
    exit(0);
}

if ($main::debug)
{
    print STDERR "---------------------------------------------\n";
    print STDERR "Printing runtable hash prior to processing...\n";
    foreach my $key (sort(keys %runtable))
    {
        my $value = $runtable{$key};
        print STDERR "key = '$key'  value = '$value'\n";
    }
    print STDERR "---------------------------------------------\n";
}

#-------------------------------------------------------------------------------
# Open the difflist file if one was specified.
#-------------------------------------------------------------------------------
if ($difflist)
{
    $dlfh = new IO::File;
    unless ($dlfh->open(">$difflist"))
    {
        print STDERR "Failed to open difflist: '$difflist'\n";
    }
}

#-------------------------------------------------------------------------------
# Diff the files for the sites we selected.
#-------------------------------------------------------------------------------
foreach $site (sort(keys %runtable))
{
    print STDERR "Running diffs for site '$site'\n";
    unless (-d "$basedir/$site")
    {
        print STDERR "Base site directory: $basedir/$site not found\n";
        exit(4);
    }

    unless (-d "$testdir/$site")
    {
        print STDERR "Test site directory: $testdir/$site not found\n";
        exit(4);
    }

    mkdir "$diffdir/$site"; # make directory for diff output

    my $i = 0;
    foreach my $file (@dffiles)
    {
        $i++;
        my @dcmd = ("$diffprog",
                    @diffparams,
                    "$basedir/$site/$site.$file",
                    "$testdir/$site/$site.$file",
                    "$diffdir/$site/$site.$file");
        my $dcmd = join(' ', @dcmd);
        print STDERR "Diff command $i = $dcmd\n" if $main::debug;
        unlink "$diffdir/$site/$site.$file";
        system($dcmd);

        #-------------------------------------------------------------------------------
        # If the diff command resulted in a non-empty file set a non-zero return code
        #-------------------------------------------------------------------------------
        if ( -f "$diffdir/$site/$site.$file" && not -z "$diffdir/$site/$site.$file" )
        {
            $dlfh->print("$diffdir/$site/$site.$file\n") if ($dlfh && $dlfh->opened);
            $rc = 8;
        }
    }
}

print STDERR "Return code = $rc\n";

exit($rc);


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
# Restore nasty windows back slashes in path strings.
#-------------------------------------------------------------------------------
sub uncleanPath
{
    my $path = shift;
    print STDERR "uncleanPath: path in = '$path'\n" if $main::debug;
    if ($path)
    {
        $path =~ s/\//\\\\/og;
        #$path =~ s/\\$//o unless $path eq '/'
    }
    print STDERR "uncleanPath: path out = '$path'\n" if $main::debug;
    return $path;
}
