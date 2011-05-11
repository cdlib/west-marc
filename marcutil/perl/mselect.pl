#!/usr/bin/perl
#
# Copy marc records from one file to another using the specified leader value
# selection criteria. A number of input records to skip at the beginning may
# be specified, and a maximum number of records to write may be given.
# The actual number of record written is reported.
#
# Usage: mselect.pl [options] <infile> <outfile>
#   options: --help print this usage information
#            --skip    <int> the number of input records to skip
#            --maxout  <int> the maximum number of records to write
#            --infile  <filename> the input file, defaults to STDIN\n"
#            --outfile <filename> the outupt file, defaults to STDOUT\n";
#            --select key=valuelist (this option may be repeated)
#                     where key is one of: status, type, or bib
#                     and valuelist is a series of characters to match
#                     character matches may be negated by putting ! before the
#                     match character, e.g. --select status=!d
#
# CVS Tracking info:
#
# $Revision: 1.2 $
# $Author: smcgovrn $
# $Date: 2002/10/22 21:20:31 $
# $Id: mselect.pl,v 1.2 2002/10/22 21:20:31 smcgovrn Exp $
#
#-------------------------------------------------------------------------------
use strict;
use English;
use Getopt::Long;

#-------------------------------------------------------------------------------
# Subroutine prototypes
#-------------------------------------------------------------------------------
sub validateSelect(\%);         # Force hash reference
sub buildSelectStrings(\%);     # Force hash reference
sub parseSelect($);             # Takes a single scalar
sub checkSelect($$);            # Takes two scalars
sub checkSkip($$);              # Takes two scalars


#-------------------------------------------------------------------------------
# Declare some global variables
#-------------------------------------------------------------------------------
$main::debug = 0;                       # Global debug indicator
$main::stats = 0;                       # Global print stats indicator
local $INPUT_RECORD_SEPARATOR = "\035"; # set EOL to 0x1D

#-------------------------------------------------------------------------------
# Usage statement
#-------------------------------------------------------------------------------
my $usage = "Usage: mcpy.pl [options] <infile> <outfile>\n"
          . "  options: --help print this usage information\n"
          . "           --skip    <int>      the number of input records to skip\n"
          . "           --maxout  <int>      the maximum number of records to write\n"
          . "           --infile  <filename> the input file, defaults to STDIN\n"
          . "           --outfile <filename> the outupt file, defaults to STDOUT\n"
          . "           --select  key=valuelist (this option may be repeated)\n"
          . "                       where key is one of: status, type, or bib\n"
          . "                       and valuelist is a series of characters to match;\n"
          . "                       character matches may be negated by putting ! before\n"
          . "                       the match character, e.g. --select status=!d\n"
          . "\n"
          . "options may be abbreviated up to uniqueness, thus:\n"
          . "--h for --help, --i for --infile, --o for --outfile, --se for --select,\n"
          . "and --sk for --skip\n"
          . "\n";

#-------------------------------------------------------------------------------
# Control counters
#-------------------------------------------------------------------------------
my $recsin  = 0;
my $recsout = 0;

#-------------------------------------------------------------------------------
# Command line option variables
#-------------------------------------------------------------------------------
my $skip    = 0;                        # Number of input records to skip
my $maxout  = 999999999;                # Maximum number of records to write
my %select  = ();                       # Selection options
my $infile  = undef;                    # Input file name
my $outfile = undef;                    # Output file name
my $help    = undef;

#-------------------------------------------------------------------------------
# Parse the command line options
#-------------------------------------------------------------------------------
unless (GetOptions('skip:i'    => \$skip,
                   'maxout:i'  => \$maxout,
                   'infile:s'  => \$infile,
                   'outfile:s' => \$outfile,
                   'select:s'  => \%select,
                   'help'      => \$help))
{
    die "Failed to parse command line\n";
}

#-------------------------------------------------------------------------------
# Get files from remaining command line args if not already set
#-------------------------------------------------------------------------------
$infile  = shift unless $infile;
$outfile = shift unless $outfile;

#-------------------------------------------------------------------------------
# Default files to STDIN and STDOUT if not set
#-------------------------------------------------------------------------------
$infile  = "&STDIN"  unless $infile;
$outfile = "&STDOUT" unless $outfile;


#-------------------------------------------------------------------------------
# When debugging print the options we found
#-------------------------------------------------------------------------------
if ($main::debug)
{
    print STDERR "infile  = '$infile'\n";
    print STDERR "outfile = '$outfile'\n";
    print STDERR "skip    = '$skip'\n";
    print STDERR "maxout  = '$maxout'\n";

    if (%select)
    {
        my @skeys = keys(%select);
        print STDERR "select entries:\n";
        for my $skey (sort(@skeys))
        {
            print STDERR "  '$skey'='$select{$skey}'\n";
        }
    }
    else
    {
        print STDERR "no select entries\n";
    }
}

#-------------------------------------------------------------------------------
# If help was requsted print the usage statement and exit
#-------------------------------------------------------------------------------
if ($help)
{
    print STDERR "$usage";
    exit 0;
}

#-------------------------------------------------------------------------------
# Validate the select hash if any select option were specified.
#-------------------------------------------------------------------------------
unless (not(%select) || validateSelect(%select))
{
    die "Unkown select options specifed\nValid keys are: status, type, bib\n";
}

#-------------------------------------------------------------------------------
# Parse the selection strings
#-------------------------------------------------------------------------------
my ($selstat, $nselstat, $seltype, $nseltype, $selbib, $nselbib) = buildSelectStrings(%select);

#-------------------------------------------------------------------------------
# When debugging print all the selection strings
#-------------------------------------------------------------------------------
if ($main::debug)
{
    print STDERR "status sel string = ", (defined $selstat ? "'$selstat'" : "undef"), "\n";
    print STDERR "status not string = ", (defined $nselstat ? "'$nselstat'" : "undef"), "\n";
    print STDERR "type sel string   = ", (defined $seltype ? "'$seltype'" : "undef"), "\n";
    print STDERR "type not string   = ", (defined $nseltype ? "'$nseltype'" : "undef"), "\n";
    print STDERR "biblvl sel string = ", (defined $selbib ? "'$selbib'" : "undef"), "\n";
    print STDERR "biblvl not string = ", (defined $nselbib ? "'$nselbib'" : "undef"), "\n";
}

#-------------------------------------------------------------------------------
# Determine if we need to process the marc header.
#-------------------------------------------------------------------------------
my $processHeader = ($selstat || $nselstat || $seltype || $nseltype || $selbib || $nselbib);
print STDERR "processHeader = $processHeader\n" if $main::debug;
print STDERR (($processHeader ? "use" : "skip"), " header\n") if $main::debug;


#-------------------------------------------------------------------------------
# Make sure we have files to process, and we can open them
#-------------------------------------------------------------------------------
unless (open(FIN, "<$infile"))
{
    die "Cannot open input file: $infile\n"
}

unless (open(FOUT, ">$outfile"))
{
    die "Cannot open output file: $outfile\n"
}

#-------------------------------------------------------------------------------
# Copy the marc records
#-------------------------------------------------------------------------------
binmode(FIN);                           # prevent premature eof under windows
binmode(FOUT);                          # prevent spurious character translations
while (<FIN>)
{
    my $accept = 0;

    $recsin++;
    next unless $recsin > $skip;
    if ($processHeader)
    {
        if (length($_) > 7)
        {
            #($selstat, $nselstat, $seltype, $nseltype, $selbib, $nselbib)
            my $hdrStatus = substr($_,5,1);
            my $hdrType   = substr($_,6,1);
            my $hdrBiblvl = substr($_,7,1);
            if ($main::stats)
            {
                printf STDERR ("Record #%06d:  status='$hdrStatus'  type='$hdrType'  biblvl='$hdrBiblvl'\n", $recsin);
            }
            if ((checkSelect($selstat, $hdrStatus) # status in select list
                 && checkSkip($nselstat, $hdrStatus)) #   and not in skip list
                && (checkSelect($seltype, $hdrType) # and type in select list
                    && checkSkip($nseltype, $hdrType)) #      and not in skip list
                && (checkSelect($selbib, $hdrBiblvl) # and biblvl in select list
                    && checkSkip($nselbib, $hdrBiblvl))) #      and not in skip list
            {
                $accept = 1;
            }
            else
            {
                $accept = 0;
            }
        }
        else
        {
            print STDERR "Record $$recsin - lengh < 8, bypassed\n";
            $accept = 0;
        }
    }
    else
    {
        print STDERR "Read record #$recsin\n" if $main::debug;
        $accept = 1;
    }

    if ($accept)
    {
        print FOUT $_;
        $recsout++;
        print STDERR "Wrote record #$recsout of $maxout\n" if $main::debug;
    }
    last if $recsout >= $maxout;
}

#-------------------------------------------------------------------------------
# Close the files and report the number of records copied
#-------------------------------------------------------------------------------
close FIN;
close FOUT;

print STDERR "Copied $recsout records from $infile to $outfile\n";
exit 0;


#-------------------------------------------------------------------------------
# Check that a base string contains a subsring, only if the base string exists.
#-------------------------------------------------------------------------------
sub checkSelect($$)
{
    my $base = shift;           # the list to search
    my $item = shift;           # the item to search for
    my $rc = 1;                 # assume success

    if ($base)
    {
        if ((index($base, $item) == -1))
        {
            $rc = 0;            # item not in list, so failure
        }
    }

    if ($main::debug)
    {
        print STDERR "checking ", (defined $base ? "'$base'" : "undef"), " for '$item': rc=$rc\n"
    }
    return $rc;
}


#-------------------------------------------------------------------------------
# Check that a base string does not contain a substring,
# only if the base string exists.
#-------------------------------------------------------------------------------
sub checkSkip($$)
{
    my $base = shift;           # the list to search
    my $item = shift;           # the item to search for
    my $rc = 1;                 # assume success

    if ($base)
    {
        if ((index($base, $item) > -1))
        {
            $rc = 0;            # item is in list, so failure
        }
    }

    if ($main::debug)
    {
        print STDERR "checking ", (defined $base ? "'$base'" : "undef"), " for not $item: rc=$rc\n"
    }
    return $rc;
}



#-------------------------------------------------------------------------------
# Validate the selection hash. Keys must be one of: status, type, or bib.
# Valuse are not validate and so may be any character, optionally preceeded
# by a ! to indicate negation.
#-------------------------------------------------------------------------------
sub validateSelect(\%)
{
    my $selref = shift;
    my $rc = (ref($selref) eq "HASH");

    if ($rc)
    {
        for my $skey (keys(%$selref))
        {
            unless ($skey eq "status" || $skey eq "type" || $skey eq "bib")
            {
                print STDERR "Unkown select option: '$skey'='$selref->{$skey}'\n";
                $rc = 0;
                last;
            }
        }
    }
    return $rc;
}

#-------------------------------------------------------------------------------
# Build select strings for each of the select option specified.
#-------------------------------------------------------------------------------
sub buildSelectStrings(\%)
{
    my $selref   = shift;
    my $selstat  = undef;
    my $nselstat = undef;
    my $seltype  = undef;
    my $nseltype = undef;
    my $selbib   = undef;
    my $nselbib  = undef;

    if (ref($selref) eq "HASH")
    {
        foreach my $skey (keys(%$selref))
        {
            if ($skey eq "status")
            {
                ($selstat, $nselstat) = parseSelect($selref->{$skey});
                next;
            }
            if ($skey eq "type")
            {
                ($seltype, $nseltype) = parseSelect($selref->{$skey});
                next;
            }
            if ($skey eq "bib")
            {
                ($selbib, $nselbib) = parseSelect($selref->{$skey});
                next;
            }
        }
    }

    return ($selstat, $nselstat, $seltype, $nseltype, $selbib, $nselbib);
}

#-------------------------------------------------------------------------------
# Split the selection string into positive and negative select criteria.
#-------------------------------------------------------------------------------
sub parseSelect($)
{
    my $selval = shift;
    my $selstr = "";
    my $nselstr = "";

    print STDERR "parsing select string: ", (defined $selval ? "'$selval'" : "undef"), "\n"
    if $main::debug;

    if ($selval)
    {
        my @selchars = split ('', $selval);
        my $max = scalar @selchars;

        for (my $i = 0; $i < $max; $i++)
        {
            my $schar = $selchars[$i];
            if ($schar eq '!')
            {
                $nselstr .= $selchars[++$i];
            }
            else
            {
                $selstr .= $schar;
            }
        }
    }
    print STDERR "selstr = '$selstr' nselstr = '$nselstr'\n" if $main::debug;

    return ($selstr, $nselstr);
}
