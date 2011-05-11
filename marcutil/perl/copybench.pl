#!/usr/bin/perl -w
#
# Copy the marc convert benchmark files.
#
# Usage: copybench.pl srcdir targetbasedir <all | rlin | site1 site2 ...>
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
# $Revision: 1.6 $
# $Author: smcgovrn $
# $Date: 2002/09/10 00:05:52 $
# $Id: copybench.pl,v 1.6 2002/09/10 00:05:52 smcgovrn Exp $
#
#-------------------------------------------------------------------------------

use strict;
use Config;
use Cwd qw(chdir cwd getcwd);
use English;
use File::Basename;
use File::Copy;

$main::debug = undef;    # set to true to turn on debug mode

#-------------------------------------------------------------------------------
# Handy usage string in case the user requests --help
#-------------------------------------------------------------------------------
my $usage = "\n"
          . "Usage: copybench.pl srcdir targetbasedir <all | rlin | site1 site2 ...>\n"
          . "\n"
          . "Where site is in the list:\n"
          . "  chs, gtu, lcn, lbl, oclc, ucb, ucbl, ucd, ucdl,\n"
          . "  uci, ucla, ucr, ucsb, ucsc, ucsd, ucsf, unload\n"
          . "\n"
          . "\n";


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

my $site = undef;   # the current site from the command line


#-------------------------------------------------------------------------------
# Make sure we have some arguments to process.
#-------------------------------------------------------------------------------
unless (@ARGV)
{
    print STDOUT $usage;
    exit(0);
}

my $srcdir = cleanPath(shift);
my $targetdir = cleanPath(shift);

#-------------------------------------------------------------------------------
# Handle -h or --help
#-------------------------------------------------------------------------------
if ($srcdir eq '-h' || $srcdir eq '--help')
{
        print STDOUT $usage;
        exit(0);
}

die "Source does not exist" unless (-e $srcdir);
die "Source is not a directory" unless (-d $srcdir);
die "Target does not exist" unless (-e $targetdir);
die "Target is not a directory" unless (-d $targetdir);

print STDERR "Source directory = $srcdir\n" if $main::debug;
print STDERR "Target directory = $targetdir\n" if $main::debug;

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
# Quit if no sites were selected.
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
# Run the commands we selected.
#-------------------------------------------------------------------------------
foreach $site (sort(keys %runtable))
{
    print STDERR "Copying files for site '$site' from '$srcdir' to '$targetdir/$site'\n" if $main::debug;

    my $sitedir = "$targetdir/$site";

    unless (opendir INDIR, $sitedir)
    {
        print STDERR "Could not open directory $sitedir\n";
        next;
    }

    my @filelist = grep !/^\.\.?$/, readdir INDIR;
    closedir INDIR;

    print STDERR "Found ", (scalar(@filelist)), " entries in directory $sitedir\n" if $main::debug;
    my $copycnt = 0;

    foreach my $filename (@filelist)
    {
        if (-f "$sitedir/$filename")
        {
            copy("$srcdir/$filename", "$sitedir/$filename") and $copycnt++;
        }
    }
    print STDERR "Copied $copycnt files from '$srcdir' to '$targetdir/$site'\n";
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
