#!/usr/bin/perl -w
#
# Update the location using a new version from CVS.
#
# Usage: updloctab.pl [options] args
# Usage: updloctab.pl [named_parameters]
# Named Parameters:
#    --cvstag      Cvs revision tag to use in cvs export command.
#                  This parameter has no default and must be specified.
#    --cvslt       Cvs location of the location table.
#    --cvsz39lt    Cvs location of the z39 location table.
#    --targetlt    File system target for the location table.
#    --targetz39lt File system target for the z39 location table.
#    --z124pgm     Program to run to update the z124 oracle table.
#    --exclpgm     Programs to look for before updating the location table.
#                  This parameter may be repeated to specifiy multiple programs.
#                  If any of these programs are found to be running this process stops.
#    --workdir     Working directory to use for cvs export. The files are exported
#                  to this directory and copied to their final locations.
#
#    --config      Config file containing any of the above options.
#                  Options specifed on the command line take prcedence over those
#                  specified in a config file.
#
#    --debug       Turn on debugging messages, use --nodebug to force off.
#    --help        Print this help text.
#
#
# CVS Tracking info:
#
# $Revision: 1.6 $
# $Author: smcgovrn $
# $Date: 2002/12/20 00:34:27 $
# $Id: updloctbl.pl,v 1.6 2002/12/20 00:34:27 smcgovrn Exp $
#
#-------------------------------------------------------------------------------
use strict;
use Cwd qw(chdir cwd getcwd);
use English;
use File::Basename;
use File::Compare;
use File::Copy;
use Getopt::Long;

#-------------------------------------------------------------------------------
# Declare some global variables
#-------------------------------------------------------------------------------
$main::debug = 0;                       # Global debug indicator
$main::version = '$Revision: 1.6 $';    # Cvs revision

#-------------------------------------------------------------------------------
# Handy usage string in case the user requests --help
#-------------------------------------------------------------------------------
my $usage = "\n"
. "Usage: updloctab.pl [named_parameters]\n"
. "Named Parameters:\n"
. "   --cvstag      Cvs revision tag to use in cvs export command.\n"
. "                 This parameter has no default and must be specified.\n"
. "   --cvslt       Cvs location of the location table.\n"
. "   --cvsz39lt    Cvs location of the z39 location table.\n"
. "   --targetlt    File system target for the location table.\n"
. "   --targetz39lt File system target for the z39 location table.\n"
. "   --z124pgm     Program to run to update the z124 oracle table.\n"
. "   --exclpgm     Programs to look for before updating the location table.\n"
. "                 This parameter may be repeated to specifiy multiple programs.\n"
. "                 If any of these programs are found to be running this process stops.\n"
. "   --workdir     Working directory to use for cvs export. The files are exported\n"
. "                 to this directory and copied to their final locations.\n"
. "\n"
. "   --config      Config file containing any of the above options.\n"
. "                 Options specifed on the command line take prcedence over those\n"
. "                 specified in a config file.\n"
. "\n"
. "   --debug       Turn on debugging messages, use --nodebug to force off.\n"
. "   --help        Print this help text.\n"
. "\n"
. "Program version: $main::version\n"
. "\n";


#-------------------------------------------------------------------------------
# Option defaults
#-------------------------------------------------------------------------------
my $cvstag       = undef; # no default - required parameter
my $cvslt        = "melvyl/u51_5/cdl90/tab/tab_locations";
my $cvsz39lt     = "melvyl/u51_5/alephe/tab/union/z39.50_location_table";
my $targetlt     = "/exlibris1/u50_5/cdl90/tab/tab_locations";
my $targetz39lt  = "/exlibris1/u50_5/alephe/tab/union/z39.50_location_table";
my $z124pgm      = (defined $ENV{'aleph_proc'}
                    ? "csh -f $ENV{'aleph_proc'}/p_load_z124 cdl90,$targetlt,z124,n,n,n,n"
                    : undef);
my @exclpgm      = ("p_union_13");
my $workdir      = "/tmp/updloctbl";
my $help         = undef;
my $debug        = undef;

#-------------------------------------------------------------------------------
# Command line options hash
#-------------------------------------------------------------------------------
my %options = (
               "cvstag"      => undef,
               "cvslt"       => undef,
               "cvsz39lt"    => undef,
               "targetlt"    => undef,
               "targetz39lt" => undef,
               "z124pgm"     => undef,
               "exclpgm"     => \@exclpgm,
               "workdir"     => undef,
               "config"      => undef,
               "help"        => \$help,
               "debug"       => \$debug
              );

#-------------------------------------------------------------------------------
# Parse the command line options
#-------------------------------------------------------------------------------
unless (GetOptions(\%options,
                   'cvstag:s',
                   'cvslt:s',
                   'cvsz39lt:s',
                   'targetlt:s',
                   'targetz39lt:s',
                   'z124pgm:s',
                   'exclpgm:s',
                   'workdir:s',
                   'config:s',
                   'help',
                   'debug!'
                  ))
{
    die "Failed to parse command line\n";
}

#-------------------------------------------------------------------------------
# Print the parsed command line
#-------------------------------------------------------------------------------
if ($main::debug)
{
    printOptions(\%options);

    if (@ARGV)
    {
        print STDERR "args:\n";
        for my $i (0..$#ARGV)
        {
            print STDERR "  arg[$i] = ", (defined $ARGV[$i] ? "'$ARGV[$i]'" : "undef"), "\n";
        }
    }
    else
    {
        print STDERR "no remaining args\n";
    }

    print "help = ", (defined $help ? $help : "undef"), "\n";
    print "debug = ", (defined $debug ? $debug : "undef"), "\n";
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
# If a config file is specifed, load options from config that have been
# specified on the command line.
#-------------------------------------------------------------------------------
my $config = ($options{"config"} ? cleanPath($options{"config"}) : undef);

if ($config)
{
    $config = cleanPath($config);
    print STDERR "Processing config file $config\n" if $main::debug;
    getconfig($config, \%options);

    #-------------------------------------------------------------------------------
    # When debugging print the options we found, after loading config the file
    #-------------------------------------------------------------------------------
    if ($main::debug)
    {
        print STDERR "options after loading config the file\n";
        printOptions(\%options);
    }
}

#-------------------------------------------------------------------------------
# Remove any duplicate entries from the exclude list.
#-------------------------------------------------------------------------------
deduplist(\@exclpgm);
printOptions(\%options) if $main::debug;

#-------------------------------------------------------------------------------
# Use the options set to override the default values.
#-------------------------------------------------------------------------------
$cvstag       = $options{"cvstag"}      if (defined $options{"cvstag"});
$cvslt        = $options{"cvslt"}       if (defined $options{"cvslt"});
$cvsz39lt     = $options{"cvsz39lt"}    if (defined $options{"cvsz39lt"});
$targetlt     = $options{"targetlt"}    if (defined $options{"targetlt"});
$targetz39lt  = $options{"targetz39lt"} if (defined $options{"targetz39lt"});
$z124pgm      = $options{"z124pgm"}     if (defined $options{"z124pgm"});
$workdir      = $options{"workdir"}     if (defined $options{"workdir"});


#-------------------------------------------------------------------------------
# Now lets set all the options to point back to the scalers so we can
# easily pass them around, should that become necessary.
#-------------------------------------------------------------------------------
$options{"cvstag"}      = \$cvstag;
$options{"cvslt"}       = \$cvslt;
$options{"cvsz39lt"}    = \$cvsz39lt;
$options{"targetlt"}    = \$targetlt;
$options{"targetz39lt"} = \$targetz39lt;
$options{"z124pgm"}     = \$z124pgm;
$options{"workdir"}     = \$workdir;

#-------------------------------------------------------------------------------
# And, finally, lets print the darn things one last time.
#-------------------------------------------------------------------------------
printOptions(\%options) if $main::debug;

#-------------------------------------------------------------------------------
# Finally finished gathering our runtime, let's get something done.
#-------------------------------------------------------------------------------

#-------------------------------------------------------------------------------
# Check that we have a cvs tag. Without that we do not proceed.
# First assume the option value is file name, which file contains the
# the actual tag value to use. If the option does not refer to a file,
# then the option value is used as the revision tag for the cvs export.
#-------------------------------------------------------------------------------
unless ($cvstag)
{
	print STDERR "No cvs tag provided, assuming nothing to do, and exiting\n";
	exit(0);
}

if (-f $cvstag)
{

    die "Error: failed to open cvs tag file: $cvstag\n" unless (open(CVSTAGF, "<$cvstag"));
    my $tmptag = <CVSTAGF>;
    chomp $tmptag;
    $tmptag =~ s/^\s+//og;  # trim leading whitespace
    $tmptag =~ s/\s+$//og;  # trim trailing whitespace
    die "Error: no tag found in cvs tag file: $cvstag\n" unless ($tmptag);
    $cvstag = $tmptag;
    print "Found cvs tag: '$cvstag'\n" if $main::debug;
}
else
{
    print "Warning: cvs tag does not point to a file, using actual value: $cvstag\n";
}

#-------------------------------------------------------------------------------
# Check for conflicting processes before continuing. If any are found,
# notify the user, and exit.
#-------------------------------------------------------------------------------
unless (open(PSLIST, "ps -ef|"))
{
    print "Error - failed to open ps list - exiting\n";
    exit(0);
}

my @pslist = <PSLIST>;
my $xcnt   = 0;
print "Printing ps list\n" if $main::debug;
foreach my $psline (@pslist)
{
    print "$psline" if $main::debug;
    foreach my $pgm (@exclpgm)
    {
        if ($psline =~ /$pgm/)
        {
            print "Conflicting process $pgm is running\n";
            $xcnt++;
        }
    }
}

if ($xcnt > 0)
{
    print "Conflicting processes found, so exiting\n";
    exit(0);
}


#-------------------------------------------------------------------------------
# Save the current working direcory and switch to the working directory.
#-------------------------------------------------------------------------------
my $savecwd = getcwd();
mkdir "$workdir" unless (-e $workdir);
die "Cannot chdir to working directory: $workdir\n" unless (chdir "$workdir");


#-------------------------------------------------------------------------------
# Remove any existing versions of the files to get from cvs before going to cvs,
# or cvs complains during the export. :(
#-------------------------------------------------------------------------------
my $rc = 0;
my $cvsltfile  = "tables/" . basename($cvslt);
my $cvsz39file = "tables/" . basename($cvsz39lt);
$rc = unlink $cvsltfile, $cvsz39file;
print "removed $rc files from $workdir/tables prior to exporting from cvs\n";

#-------------------------------------------------------------------------------
# Get the location tables from cvs
#-------------------------------------------------------------------------------
my $cmd = undef;
$cmd = "cvs export -d tables -r $cvstag $cvslt";
print STDERR "Executing: $cmd\n" if $main::debug;
$rc = system($cmd);
print "get loctab rc = $rc\n" if $main::debug;
if ($rc != 0)
{
    print "Error: failed to get file from cvs, rc = $rc, cmd = $cmd\n";
    exit(0);
}

$cmd = "cvs export -d tables -r $cvstag $cvsz39lt";
print STDERR "Executing: $cmd\n" if $main::debug;
$rc = system($cmd);
print "get tabz39 rc = $rc\n" if $main::debug;
if ($rc != 0)
{
    print "Error: failed to get file from cvs, rc = $rc, cmd = $cmd\n";
    exit(0);
}

#-------------------------------------------------------------------------------
# Check for file differences before updating.
# First check the base location table.
#-------------------------------------------------------------------------------
if ($main::debug)
{
    print "target loctab ", (-f $targetlt ? "exists" : "does not exist"), "\n";
    print "cvs loctab ", (-f $cvsltfile ? "exists" : "does not exist"), "\n";
}
my $drc1 = 0;
unless (-f $cvsltfile)
{
    print "Error - loctab file from cvs not found: $cvsltfile\n";
    exit(0);
}

stripCR($cvsltfile); # strip carriage returns from location table

if (-f $targetlt)
{
    print "comparing $targetlt to $cvsltfile\n" if $main::debug;
    $drc1 = compare("$targetlt", "$cvsltfile");
    print "  rc = $drc1\n" if $main::debug;
}
else
{
    print "Warning - bypassing compare, target loctab file not found: $targetlt\n";
    $drc1 = 2;
}

#-------------------------------------------------------------------------------
# Check the z39 location table
#-------------------------------------------------------------------------------
if ($main::debug)
{
    print "target tabz39 ", (-f $targetz39lt ? "exists" : "does not exist"), "\n";
    print "cvs tabz39 ", (-f $cvsz39file ? "exists" : "does not exist"), "\n";
}
my $drc2 = 0;
unless (-f $cvsz39file)
{
    print "Error - loctab file from cvs not found: $cvsltfile\n";
    exit(0);
}

stripCR($cvsz39file);; # strip carriage returns from z39 location table

if (-f $targetz39lt)
{
    print "comparing $targetz39lt to $cvsz39file\n" if $main::debug;
    $drc2 = compare("$targetz39lt", "$cvsz39file");
    print "  rc = $drc1\n" if $main::debug;
}
else
{
    print "Warning - bypassing compare, target loctab file not found: $targetz39lt\n";
    $drc2 = 1;
}

#-------------------------------------------------------------------------------
# Check the compare results. If the files are the same we don't need
# to do anything else. If a system error occured alert the user and exit.
#-------------------------------------------------------------------------------
if ($drc1 == 0 && $drc2 == 0)
{
	print "Both location table files have not changed, no update required, so exiting\n";
	exit(0);
}
elsif ($drc1 < 0 || $drc2 < 0)
{
    print "System error occured during file compare, so exiting\n";
    exit(0);
}
else
{
    print "Files changed so proceeding\n" if $main::debug;
}

#-------------------------------------------------------------------------------
# Copy the new files to their home. (Only if differences found.)
# File::copy returns 1 for success or 0 for failure.
#-------------------------------------------------------------------------------
if ($drc1 > 0)
{
    $rc = copy("$cvsltfile", "$targetlt");
    print "copy tabloc: $cvsltfile to $targetlt rc = $rc\n" if $main::debug;
    if ($rc == 0)
    {
        print "Error: failed to copy '$cvsltfile' to '$targetlt'\n";
        exit(0);
    }
}
else
{
    print "No differences found for location table - update bypassed\n";
}

if ($drc2 > 0)
{
    $rc = copy("$cvsz39file", "$targetz39lt");
    print "copy tabz39: $cvsz39file to $targetz39lt rc = $rc\n" if $main::debug;
    if ($rc == 0)
    {
        print "Error: failed to copy '$cvsz39file' to '$targetz39lt'\n";
        exit(0);
    }
}
else
{
    print "No differences found for z39 location table - update bypassed\n";
}

#-------------------------------------------------------------------------------
# Run the aleph process to rebuild the location table in oracle.
# We should really be exporting the affected tables first, and
# possibly should not be doing this at all, since we have no easy
# way to verify correctness, but ours is not to reason why...
#
# Note: only proceed if differences were found for the location table.
#-------------------------------------------------------------------------------
if ($drc1 > 0)
{
    #
    # Talk to mark about the specifics of making this work...
    #
    $rc = system("$z124pgm"); # This program always return 0! Arghh!
    if ($rc != 0)
    {
        print "Error: Aleph update of location tables failed, rc = $rc, cmd = $z124pgm\n";
        print "Need to restore oracle location tables.\n";
        exit(4);
    }
}

chdir "$savecwd"; # Change  back to original directory before exiting
print "Location tables succesfully updated\n";

exit(0);
#-------------------------------------------------------------------------------
# end of top level logic, subroutines follow
#-------------------------------------------------------------------------------

#-------------------------------------------------------------------------------
# Print the options hash.
#-------------------------------------------------------------------------------
sub printOptions
{
    my $options = shift;
    print STDERR "options:\n";
    my $key;
    my $value;
    while (($key, my $value) = each %$options)
    {
        if (defined $value)
        {
            unless (ref($value))
            {
                print STDERR "  $key = $value\n";
                next;
            }
            if (ref($value) eq "ARRAY")
            {
                # print list values  from reference
                print STDERR "  $key(ra)) = (", (join(', ', @$value)), ")\n";
                next;
            }
            if (ref($value) eq "HASH")
            {
                # print hash value from reference
                next;
            }
            if (ref($value) eq "SCALAR")
            {
                # print scalar value from reference
                print STDERR "  $key(rs) = ", (defined $$value ? $$value : "undef"), "\n";
                next;
            }
            print STDERR "  $key(ro) = $value\n";
            next;
        }
        else
        {
            print STDERR "  $key = undef\n";
        }
    }
}


#-------------------------------------------------------------------------------
# Remove duplicate items from a list.
# Takes a reference to the list, and so, operates directly on the list.
# Return the number of items in the resulting list.
#-------------------------------------------------------------------------------
sub deduplist
{
    my $list = shift;
    return -1 unless (ref($list) eq "ARRAY");

    my %hash = ();
    my $item = undef;

    while (defined ($item = shift @$list))
    {
        $hash{$item} = 1;
    }
    push @$list, keys %hash;
    return (scalar @$list);
}


#-------------------------------------------------------------------------------
# Read the configuration file to get the runtime environment. No assumptions
# are made as to which parameters are specified. Those we find are set, the
# others will be set to default values later, unless they have already been
# set using a --option.
#-------------------------------------------------------------------------------
sub getconfig
{
    my $fname    = shift;               # config file name
    my $options  = shift;               # reference to parameter hash
    my $name     = undef;               # local name variable
    my $value    = undef;               # local value variable

    unless (-f "$fname" && open(CFIN, "<$fname"))
    {
        die "Cannot open config file: $fname\n";
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

        if (exists $options->{"$name"})    # if this is a name we like
        {
            unless ($options->{$name})     # and it is not yet defined
            {
                $options->{$name} = $value;   # set the value
            }

            if (ref($options->{$name}) eq "ARRAY")
            {
                push @{$options->{$name}}, (split(/,/,$value));
            }
        }
    }
    close(CFIN);

    return;
}


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


#-------------------------------------------------------------------------------
# Remove those pesky '\r' characters from a file.
#-------------------------------------------------------------------------------
sub stripCR
{
	my $infile  = shift;
	my $outfile = undef;

	print "Stripping file $infile\n" if $main::debug;

	{
		$outfile = "$infile" . "-tmp";
		next unless( -f $infile );
		next if (-f $outfile );
		next unless( open (INFILE, "<$infile"));
		next unless( open (OUTFILE, ">$outfile"));
		binmode (OUTFILE);
		while(<INFILE>)
		{
			s/\r//g;
			print OUTFILE $_;
		}
		close (INFILE);
		close (OUTFILE);
		unlink ($infile);
		rename ("$outfile", $infile);
	}

	return;
}
