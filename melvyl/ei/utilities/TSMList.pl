#!/usr/local/bin/perl -w

my $USAGE = '
USAGE:
TSMList.pl [Command-options]

 	[Command-options]
		--node <node>	Define node. (Default: hostname of client)
		--file  	List file spaces and files.
		--filespace  	List file spaces only. (Overrides --file)
		--help		This message.

	Sample calls:
                perl TSMList.pl --node cdlsnaparc	# Displays all files on CDLSNAPARC
                perl TSMList.pl --filespace		# Displays file spaces on local archive

DESCRIPTION:
Query contents of the Tivoli Storage Manager.

ENVIRONMENT:
- Host machine has the TSM client software installed and properly configured.
- CDLSNAPARC password is contained in a file named "cdlsnaparc.dat".  For security
  reasons, this should not be world readable.

[mjr 20020603]
';

use Getopt::Long;				# For command line switches
use strict;

# Get command line arguments
my ($NODE,$FILESPACE,$FILE,$HELP) = (0,0,0,0);		# Defaults
GetOptions('--node:s'  	=> \$NODE,
           '--file'	=> \$FILE,
           '--filespace'=> \$FILESPACE,
           '--help'	=> \$HELP);

die $USAGE if (@ARGV != 0 or $HELP);
my $command = "";

# Build arguments to the TSM query command.
chomp( my $host = `hostname`);

# Can only connect to CDLSNAPARC virtually.
if ($NODE) {
    die "Can not connect to virtual node: $NODE" unless
        ($host =~ m/$NODE/i or $NODE =~ m/CDLSNAPARC/i);
    # CDLSNAPARC needs password authorization.
    if ($NODE =~ m/CDLSNAPARC/i) {
        die "password file for archive CDLSNAPARC does not exit: cdlsnaparc.dat" unless
            (-e "cdlsnaparc.dat");
        chomp (my $password = `cat cdlsnaparc.dat`);
        $command = "-virtualnodename=CDLSNAPARC -password=$password";
    }
} else { $NODE = $host; }

my ($filespace, @filespace);
my $EndOfHeader = 0;

# Extract file space names.
open (FILESPACE, "dsmc query filespace $command|");
while (<FILESPACE>) {
    chomp;
    next if $_ !~ m/-{15}$/ and $EndOfHeader eq 0;	# End of Header
    $EndOfHeader = 1;
    next if $_ =~ m/-{15}$/;				# Handle final header line
    ($filespace) = (split ' ', $_)[4];
    push @filespace, $filespace; 
}
close (FILESPACE);

print "Node: $NODE\n";
print "File space(s): @filespace\n";
exit if $FILESPACE;

# Print files for each file space.
$EndOfHeader = 0;
foreach my $file (@filespace) {
    $EndOfHeader = 0;
    open (FILE, "dsmc query archive $command $file/ 2> /dev/null|");
    while (<FILE>) {
        chomp;
        next if $_ !~ m/-{15}$/ and $EndOfHeader eq 0;	# End of Header
        $EndOfHeader = 1;
        next if $_ =~ m/-{15}$/;			# Handle final header line
        print "$_\n";
    }
}
close (FILE);

exit;
