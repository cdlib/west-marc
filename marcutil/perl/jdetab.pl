#!/usr/bin/perl
#
# Convert all tabs to 4 spaces in a file, or all the files in a directory,
# with an optional extentsion list. The extention list defaults to '.java',
# if no extension are specified.
#
# Usage: jdetab.pl [options] (--file filename | --dir dirname) <extensions>
#  Options:
#    --recurse process direcorites recursively, use --norecurse to force off
#    --help    print this usage message
#    --debug   turn on debugging messages, use --nodebug to force off
#    --quite   turn off informational messages, use --noquite to force on
#
#  The --file and --dir paramaeters may be repeated and are not excusive.
#  The extenstion list is a list of file extentsions to process.
#  The default file extention is .java, if not extensions are specified
#  Directory recursion is off by default
#
#  Example: jdetab.pl --rec --dir somedir .java .pl
#   will process directory 'somedir' recursively,
#   detabbing all .java and .pl files
#
#  Tab width is 4 and is not cofigurable.
#
#
# CVS Tracking info:
#
# $Revision: 1.8 $
# $Author: smcgovrn $
# $Date: 2002/12/13 23:23:34 $
# $Id: jdetab.pl,v 1.8 2002/12/13 23:23:34 smcgovrn Exp $
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
. "Usage: jdetab.pl [options] <extensions>\n"
. "Options:\n"
. "   --file <filename> a single file to process\n"
. "   --dir  <dirname>  a directory to process\n"
. "   --recurse process direcorites recursively, use --norecurse to force off\n"
. "   --help    print this usage message\n"
. "   --debug   turn on debugging messages, use --nodebug to force off\n"
. "   --quiet   turn off informational messages, use --noquiet to force on\n"
. "\n"
. "At least one instance of --file or --directory must be specified.\n"
. "The --file and --dir parameters may be repeated and are not exclusive.\n"
. "The extenstion list is a list of file extentsions to process.\n"
. "The default file extention is .java, if no extensions are specified.\n"
. "Directory recursion is off by default.\n"
. "Tab width is 4 and is not configurable.\n"
. "\n"
. "Example: jdetab.pl --rec --dir somedir .java .pl\n"
. "Will process directory 'somedir' recursively, detabbing all .java and .pl files\n"
. "\n";

#-------------------------------------------------------------------------------
# Command line options
#-------------------------------------------------------------------------------
my $help   = undef;
my $debug  = undef;
my $quiet = undef;
my $recurse = undef;
my @fnames = ();
my @dnames = ();

#-------------------------------------------------------------------------------
# Parse the command line options
#-------------------------------------------------------------------------------
unless (GetOptions('file:s'   => \@fnames,
                   'dir:s'    => \@dnames,
                   'recurse!' => \$recurse,
                   'quiet!'   => \$quiet,
                   'help'     => \$help,
                   'debug!'   => \$debug))
{
    die "Failed to parse command line\n";
}

#-------------------------------------------------------------------------------
# Set the extensions list from the remaining parameters.
# Default the list to .java if none are specified.
#-------------------------------------------------------------------------------
my @extlist = @ARGV;

unless (@extlist)
{
    @extlist = (".java");
    print STDERR "Defaulting to file extension .java for directory processing\n";
}

#-------------------------------------------------------------------------------
# Set the debug global if that option is specified.
#-------------------------------------------------------------------------------
$main::debug = $debug if (defined $debug);

#-------------------------------------------------------------------------------
# When debug is on print the command line parameter before any other processing.
#-------------------------------------------------------------------------------
if ($main::debug)
{
    print STDERR ("options: help = ", (defined $help ? "$help" : "undef"),
                  " debug = ", (defined $debug ? "$debug" : "undef"),
                  " recurse = ", (defined $recurse ? "$recurse" : "undef"), "\n");

    if (@fnames)
    {
        print STDERR "files[", scalar(@fnames), "]: ", (join(' ',@fnames)), "\n";
    }

    if (@dnames)
    {
        print STDERR "directories[", scalar(@dnames), "]: ", (join(' ',@dnames)), "\n";
    }

    if (@extlist)
    {
        print STDERR "extensions[", scalar(@extlist), "]: ", (join(' ',@extlist)), "\n";
    }
}

#-------------------------------------------------------------------------------
# Handle --help or -h.
#-------------------------------------------------------------------------------
if ($help)
{
    print STDERR "$usage";
    exit(0);
}

#-------------------------------------------------------------------------------
# Turn off directory recursion if no option was specified.
# Turn on informational messages if no option was specifed.
#-------------------------------------------------------------------------------
$recurse = 0 unless (defined $recurse);
$quiet   = 0 unless (defined $quiet);

#-------------------------------------------------------------------------------
# Set up the rest of our variables
#-------------------------------------------------------------------------------
my $rc = 0;                             # return code
my $starttime = time;                   # start time
my $endtime = undef;                    # end time
my $runtime = undef;                    # run time
my $name = undef;                       # name of file or directory to process

#-------------------------------------------------------------------------------
# Process the requested files
#-------------------------------------------------------------------------------
foreach $name (@fnames)
{
    if (-f $name)
    {
        $rc = &fixfile($name);
    }
    else
    {
        print STDERR "File $name was not found or is not a regular file\n";
        $rc = 8;
    }
}

#-------------------------------------------------------------------------------
# Process the requested directories
#-------------------------------------------------------------------------------
foreach $name (@dnames)
{
    if (-d $name)
    {
        $rc = &fixdir($recurse, $name, @extlist);
    }
    else
    {
        print STDERR "Directory $name was not found\n";
        $rc = 8;
    }
}

#-------------------------------------------------------------------------------
# Calculate the run time in seconds
#-------------------------------------------------------------------------------
$endtime = time;
$runtime = $endtime - $starttime;
my ($user,$system,$cuser,$csystem) = times;
my $stimestr = localtime($starttime);
my $etimestr = localtime($endtime);

unless ($quiet)
{
    print STDERR "Start time  = $stimestr\n";
    print STDERR "End time    = $etimestr\n";
    print STDERR "Run time    = $runtime seconds\n";
    print STDERR "Process times:\n";
    print STDERR "utime = $user stime = $system cutime = $cuser cstime = $csystem\n";
}

exit $rc;

#-------------------------------------------------------------------------------
# Replace the tabs in the specified file with 4 spaces.
#-------------------------------------------------------------------------------
sub fixfile
{
    my $filename = shift;
    my $out_file = $filename . "_fixttmp";
    my $reccnt   = 0;
    my $chgcnt   = 0;
    my $line     = "";

    # info message - suppress with --quiet
    print STDERR "Detabbing file: $filename\n" unless ($quiet);

    unless (open INFILE, "<$filename")
    {
        print STDERR "could not open file $name for reading\n";
        return 8;
    }

    unless (open OUTFILE, ">$out_file")
    {
        print STDERR "Could not open file $out_file for writing\n";
        return 8;
    }

    binmode INFILE;   # switch to binmode to keep original line ends
    binmode OUTFILE;  # switch to binmode to keep original line ends

    LINE: while (<INFILE>)
    {
        $reccnt++;
        $line   = $_;

        # Substitute spaces for tabs with a tab width of 4
        s/(.*?)\t/$1.(' ' x (4-length($1)%4))/ge;

        if ($_ ne $line)
        {
            $chgcnt++;
        }
        print OUTFILE $_;
    }

    close INFILE;
    close OUTFILE;

    if ($chgcnt > 0)
    {
        unless (unlink $filename)
        {
            print STDERR "Could not unlink $filename\n";
        }

        unless (rename "$out_file", $filename)
        {
            print STDERR "Could not rename $out_file to $filename\n";
        }
    }
    else
    {
        unless (unlink $out_file)
        {
            print STDERR "Could not unlink $out_file\n";
        }
    }

    # info message - suppress with --quiet
    print STDERR " Processed file $filename - Changed $chgcnt of $reccnt Records\n"
    unless ($quiet);

    return 0;
}


#-------------------------------------------------------------------------------
# Examine all the files in the specified directory. If the file name has an
# extension on the extension list pass it to fixfile to remove the tabs.
#-------------------------------------------------------------------------------
sub fixdir
{
    my ($recurse, $dirname, @extlist) = @_;
    my ($rc) = 0;

    # info message - suppress with --quiet
    print STDERR ("Processing directory $dirname ",
                  ($recurse ? "with" : "without"),
                  " recursion\n")
    unless ($quiet);

    # open the directory
    unless (opendir INDIR, $dirname)
    {
        print STDERR "Could not open directory $dirname\n";
        return 8;
    }

    # slurp in the file name, excluding . and ..
    my @filelist = grep !/^\.\.?$/, readdir INDIR;
    closedir INDIR;
    chdir $dirname;

    # iterate over the list of files
    foreach my $filename (@filelist)
    {
        if (-f $filename)
        {
            print STDERR "Looking at file $filename\n" if $main::debug;
            foreach my $ext (@extlist)
            {
                if ($filename =~ /$ext$/)
                {
                    $rc = &fixfile($filename);
                    if ($rc != 0)
                    {
                        return $rc;
                    }
                }
            }
        }
        elsif (-d $filename && $recurse)
        {
            print STDERR "Looking at directory $filename\n" if $main::debug;
            $rc = &fixdir($recurse, $filename, @extlist);
            if ($rc != 0)
            {
                return $rc;
            }
        }
    }

    chdir "..";

    return 0;
}
