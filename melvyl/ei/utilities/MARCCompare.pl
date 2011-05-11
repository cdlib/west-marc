#! /usr/local/bin/perl -w

use lib "/home/mreyes/Perl/lib";
use MARC;
use Getopt::Long;                               # For command line switches
use strict;

my $USAGE = '
USAGE:
MARCCompare.pl [Command-options] <MARC file 1> <MARC file 2>

        [Command-options]
                --verbose	Display processing information.
		--range a:b	Compare records range. (a=first b=last)
		--exclude field	Exclude field from comparison.
                --help          This message.

        Arguments
		MARC file 1	MARC data file in USMARC format.
		MARC file 2	MARC data file in USMARC format.

        Sample calls:
                perl MARCCompare.pl --range 1:20 ucb1.mc ucb2.mc		# Compare the first 20 records of UCB files
                perl MARCCompare.pl --verbose -exclude 998 ucb1.mc ucb2.mc	# Compare all records of UCB files, excluding 998 fields, but displaying processing information.

DESCRIPTION:
Compare two MARC record data files.

ENVIRONMENT:
- MARC.pm is installed on host machine.

RETURN:
- Status of comparison. (0=match, 1=non match)

TO DO:
The --range switch is very dumb.  It reads in all records, then prunes the data
    which is incredibly time consuming on large files.
The implementation should prune data before doing the MARC read.

[mjr 20020606]
';

# Get command line arguments
my ($VERBOSE,$RANGE,$HELP,$EXCLUDE) = (0,0,0,0);	# Defaults
GetOptions('--verbose'	=> \$VERBOSE,
           '--range:s'	=> \$RANGE,
           '--exclude:s'=> \$EXCLUDE,
           '--help'	=> \$HELP);

(@ARGV == 2 and not $HELP) || die $USAGE;
$| = 1;		# Flush output
my $file1 = shift @ARGV;
my $file2 = shift @ARGV;
my $status = 0;		# exit status

my (@RANGE, %RANGE);
my ($first, $last);

my ($anthony, $chagall) = (undef, undef);	# MARC record objects.
$EXCLUDE = sprintf("%3.3d", $EXCLUDE) if $EXCLUDE;	# exclude field is 3 digits, like MARC.pm.
if ($RANGE) {		# parse range if specified.
    ($first, $last) = range($RANGE);
    @RANGE = ($first..$last);
    foreach my $idx (@RANGE) { $RANGE{$idx} = 1; }		# Create hash of valid records
}

# Read in first file, skipping --range records if necessary.
my @rec1 = ();
my $rec1;
$anthony = new MARC;
$anthony->openmarc({file=>"$file1",'format'=>"usmarc"}) || die "Error opening MARC file: $file1";
while ($anthony->nextmarc(1)) {
    $rec1=$anthony->output({'format'=>"ascii"});
    push @rec1, $rec1;
    $anthony->deletemarc();
}
$anthony->closemarc();

# Read in first file, skipping --range records if necessary.
my @rec2 = ();
my $rec2;
$chagall = new MARC;
$chagall->openmarc({file=>"$file2",'format'=>"usmarc"}) || die "Error opening MARC file: $file2";
while ($chagall->nextmarc(1)) {
    $rec2=$chagall->output({'format'=>"ascii"});
    push @rec2, $rec2;
    $chagall->deletemarc();
}
$chagall->closemarc();

# Compare MARC data
#  - Use the first file as the driver.
#  - Compare each field of every record as a string comparison.
#  - Include only records in -range for comparison.
#  - Exclude fields that match -exclude for comparison.
my $processedrecnum=0;
for (my $recnum=0; $recnum < @rec1; $recnum++) {
    next if not defined $rec2[$recnum];		# Case where first MARC file > second MARC file
    if ($RANGE) {
        next if not $RANGE{$recnum};		# Range of valid records
    }
    $processedrecnum++;
    my @rectmp1 = split(/\n/, $rec1[$recnum]);
    my @rectmp2 = split(/\n/, $rec2[$recnum]);
    print $file1, ": Record " , $recnum+1 , " has ", $#rectmp1+1, " fields\n" if $VERBOSE;
    print $file2, ": Record " , $recnum+1 , " has ", $#rectmp2+1, " fields\n" if $VERBOSE;
    for (my $fieldnum=0; $fieldnum < $#rectmp1; $fieldnum++) {
        next if not defined $rectmp2[$fieldnum];		# Case where first MARC record > second MARC record
        if ($EXCLUDE and $rectmp1[$fieldnum] =~ m/^$EXCLUDE/) {
            print "Excluding field $EXCLUDE in record " , $recnum+1 , "\n" if $VERBOSE;
            next;
        }
        if ($rectmp1[$fieldnum] ne $rectmp2[$fieldnum]) {
            $status = 1;
            print "Mismatch: Record ", $recnum+1 , " Field $fieldnum\n".
                  "$file1: $rectmp1[$fieldnum]\n".
                  "$file2: $rectmp2[$fieldnum]\n" if $VERBOSE;
        }
    }
}

print "Total number of MARC records checked: $processedrecnum\n" if $VERBOSE;

exit $status;


sub range () {
    my ($first, $last);
    die "Range format is not correct" unless $_[0] =~ m/(\d+):(\d+)/;
    $first = $1-1;	# Record count starts at 0
    $last = $2-1;	# Record count starts at 0
    die "Range specifiers are not correct" unless ($first <= $last);

    return ($first, $last);
}
