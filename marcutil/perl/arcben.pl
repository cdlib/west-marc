#!/usr/bin/perl -w
#
# Copy the marc convert benchmark files.
#
# Usage: arcben.pl [options] <all | rlin | site1 site2 ...>
# Options:
#   --srcdir  the source directory, defaults to basedir
#   --basedir the target base directory, defaults to $CDLHOME/run or .
#   --debug   turn on debug messages, use --nodebug to force off
#   --help    print this usage message
#
# Where site is in the list:
#   chs, gtu, lcn, lbl, oclc, ucb, ucbl, ucd, ucdl,
#   uci, ucla, ucr, ucsb, ucsc, ucsd, ucsf, unload
#
# If 'all' is specified for the site then files are copied for all known sites.
# If 'rlin' is specified for the site then files are copied for sites 'chs',
# 'lbl', and 'ucbl'.
#
#
# CVS Tracking info:
#
# $Revision: 1.4 $
# $Author: smcgovrn $
# $Date: 2003/01/03 21:24:25 $
# $Id: arcben.pl,v 1.4 2003/01/03 21:24:25 smcgovrn Exp $
#
#-------------------------------------------------------------------------------

use strict;
use Config;
use Cwd qw(chdir cwd getcwd);
use English;
use File::Basename;
use File::Copy;
use Getopt::Long;

$main::debug = undef;    # set to true to turn on debug mode

#-------------------------------------------------------------------------------
# Handy usage string in case the user requests --help
#-------------------------------------------------------------------------------
my $usage = "\n"
          . "Usage: arcben.pl [options] <all | rlin | site1 site2 ...>\n"
          . "Options:\n"
          . "  --srcdir  the source directory, defaults to basedir\n"
          . "  --basedir the target base directory, defaults to \$CDLHOME/run or .\n"
          . "  --debug   turn on debug messages, use --nodebug to force off\n"
          . "  --help    print this usage message\n"
          . "\n"
          . "Where site is in the list:\n"
          . "  chs, gtu, lcn, lbl, oclc, ucb, ucbl, ucd, ucdl,\n"
          . "  uci, ucla, ucr, ucsb, ucsc, ucsd, ucsf, unload\n"
          . "\n"
          . "If 'all' is specified for the site then files are copied for all known sites.\n"
          . "If 'rlin' is specified for the site then files are copied for sites 'chs',\n"
          . "'lbl', and 'ucbl'.\n"
          . "\n";


#-------------------------------------------------------------------------------
# Get the home directory to use
#-------------------------------------------------------------------------------
$ENV{'HOME'} = '.'                unless $ENV{'HOME'};
$ENV{'CDLHOME'} = "$ENV{'HOME'}"  unless $ENV{'CDLHOME'};

#-------------------------------------------------------------------------------
# Fix $HOME and $CDLHOME on windows
#-------------------------------------------------------------------------------
$ENV{'HOME'}    = cleanPath($ENV{'HOME'}) if $ENV{'HOME'};
$ENV{'CDLHOME'} = cleanPath($ENV{'CDLHOME'}) if $ENV{'CDLHOME'};

#-------------------------------------------------------------------------------
# The files to archive.
#-------------------------------------------------------------------------------
my @flist = (
             "config.txt",
             "convert.fmt.txt",
             "convert.log",
             "convert.mrc",
             "error.fmt.txt",
             "error.mrc",
             "reject.fmt.txt",
             "reject.messages.txt",
             "reject.mrc",
             "report.txt"
            );


#-------------------------------------------------------------------------------
# The valid sites.
#-------------------------------------------------------------------------------
my %sitetable = (
                 "chs"    => "chs",
                 "gtu"    => "gtu",
                 "lcn"    => "lcn",
                 "lbl"    => "lbl",
                 "oclc"   => "oclc",
                 "ucb"    => "ucb",
                 "ucbl"   => "ucbl",
                 "ucd"    => "ucd",
                 "ucdl"   => "ucdl",
                 "uci"    => "uci",
                 "ucla"   => "ucla",
                 "ucr"    => "ucr",
                 "ucsb"   => "ucsb",
                 "ucsc"   => "ucsc",
                 "ucsd"   => "ucsd",
                 "ucsf"   => "ucsf",
                 "unload" => "unload"
                );

my %runtable = ();  # built up with sites from the command line
my @sitelist = ();  # The final list of sites to process

my $site      = undef;   # the current site from the command line
my $basedir   = cleanPath((defined $ENV{'CDLHOME'} ? "$ENV{CDLHOME}/run" : "."));
my $srcdir    = undef;
my $targetdir = undef;
my $help      = undef;
my $debug     = undef;

#-------------------------------------------------------------------------------
# Parse the command line options
#-------------------------------------------------------------------------------
unless (GetOptions('srcdir:s'    => \$srcdir,
                   'targetdir:s' => \$targetdir,
                   'help'        => \$help,
                   'debug!'      => \$debug))
{
    die "Failed to parse command line\n";
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

#-------------------------------------------------------------------------------
# Make sure we have some arguments to process.
#-------------------------------------------------------------------------------
unless (@ARGV)
{
    print STDOUT "No site specifed\n$usage";
    exit(0);
}

$srcdir = (defined $srcdir ? cleanPath($srcdir) : $basedir);
$targetdir = (defined $targetdir ? cleanPath($targetdir) : $basedir);

print STDERR "Source directory = $srcdir\n" if $main::debug;
print STDERR "Target directory = $targetdir\n" if $main::debug;

die "Source directory does not exist: $srcdir\n"        unless (-e $srcdir);
die "Source directory is not a directory: $srcdir\n"    unless (-d $srcdir);
die "Target directory does not exist: $targetdir\n"     unless (-e $targetdir);
die "Target directory is not a directory: $targetdir\n" unless (-d $targetdir);

#-------------------------------------------------------------------------------
# Collect the sites specified on the command line in a hashtable after
# validating that they are known sites.
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
}

#-------------------------------------------------------------------------------
# Quit if no sites were selected.
#-------------------------------------------------------------------------------
unless (%runtable)
{
    print STDOUT "No recognized sites to process\n$usage";
    exit(0);
}

@sitelist = sort(keys %runtable);

if ($main::debug)
{
    my $key = undef;
    print STDERR "---------------------------------------------\n";
    print STDERR "Printing site list prior to processing...\n";
    print STDERR "Sites: ", (join(' ', @sitelist)), "\n";
    print STDERR "---------------------------------------------\n";
}

#-------------------------------------------------------------------------------
# Archive the sites we selected.
#-------------------------------------------------------------------------------
foreach $site (sort(keys %runtable))
{
    print STDERR "Copying files for site '$site' from '$srcdir' to '$targetdir/$site'\n"
    if $main::debug;

    my $sitedir = "$targetdir/$site";

    unless (-d $sitedir)
    {
        mkdir "$sitedir";
    }

    unless (opendir INDIR, $sitedir)
    {
        print STDERR "Could not open directory $sitedir\n";
        next;
    }

    my @sflist = grep !/^\.\.?$/, readdir INDIR;
    closedir INDIR;

    print STDERR "Found ", (scalar(@sflist)), " entries in directory $sitedir\n"
    if $main::debug;

    my $lastdir = 0;
    foreach my $sf (@sflist)
    {
        print "sitefile = $sf\n" if $main::debug;

        next unless (-d "$sitedir/$sf");
        next unless ($sf =~ /^(\d+)/);
        my $td = $1;
        $lastdir = $td if ($td > $lastdir);

        print STDERR "tempdir = $td lastdir = $lastdir\n" if $main::debug;
    }
    $lastdir++;

    my $newdir = "$sitedir/$lastdir";
    print "newdir = $newdir\n" if $main::debug;
    unless (mkdir "$newdir")
    {
        print "Failed to make new directory: $newdir\n";
        next;
    }

    my $copycnt = 0;

    foreach my $fn (@flist)
    {
        my $filename = "$site.$fn";
        if (-f "$srcdir/$filename")
        {
            print "copying $srcdir/$filename to $newdir/$filename\n" if $main::debug;
            copy("$srcdir/$filename", "$newdir/$filename") and $copycnt++;
        }
    }
    print STDERR "Copied $copycnt files from '$srcdir' to '$newdir'\n";
}

exit(0);

#-------------------------------------------------------------------------------
# Fix nasty windows back slashes in path strings.
#-------------------------------------------------------------------------------
sub cleanPath
{
    my $path = shift;
    if ($path)
    {
        $path =~ s/\\/\//og;
        $path =~ s/\/$//o unless $path eq '/'
    }
    return $path;
}
