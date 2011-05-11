#! /usr/local/bin/perl -w

# PURPOSE:  Transfer List Server email data to the Qube document server.
#
# INPUT: List Server email data coming in from Eudora 5.1 with the
#        "Notify Application" filter option applied with all 6 arguments
#        sent using '::::' as the delimiter.  For example:
#
#        perl c:\programs\Transfer.pl %1 :::: %2 :::: .... %5 :::: %6
#
#        Currently handles the following list server types:
#        CDLFTP-L	-> FTP uploads to the virtual FTP host
#        CDLSNAPSHOT	-> FTP snapshot loads to the virtual ftp host
#        CDLPROJECT	-> EL and UCOP addressees for Melvyl-T
#        CDLTECH	-> CDL Technologies list
#        MELVYL-T	-> CDL addressees for Melvyl-T
#
#
# OUTPUT: Email housed correctly on the CDL's document server (Qube).
#         Data will be put into a file structure that defines the
#         list server in which it is addressed. 
#
# ASSUMPTIONS: Intranet to Qube is available.  Program will be called from
#              the "notify application" feature of Eudora 5.1, which will
#              provide the appropiate arguments.  Delimiter from Eudora is
#              supplied as '::::', which should not normally appear in any
#              email headers.
#
#              Drive E: is mapped to the appropiate repository.  Current
#              mapping is to: \\cdlqube\mreyes
#              Drive F: is mapped to the production documentation site.
#              Current mapping is to: \\cdlqube\melvyl\web\Production
#
# PROGRAMMER: Mark Reyes [20020515]
#
use File::Copy;
require HTML::TreeBuilder;
require HTML::FormatText;
use strict;

# Copy the following listservers to mreyes's private area.
my $qube	= 'E:\Production\ListServer Archive';
my $Cdlproject	= $qube . '\CDL-Project';
my $CdlTech	= $qube . '\CDL-Tech';
my $MelvylT	= $qube . '\Melvyl-T';

# Put Snapshot and Update record counts onto the
# Production documentation site.
my $Cdlsnapshot	= 'F:\1-FTP\Logs\Snapshot';
my $Cdlftp	= 'F:\1-FTP\Logs\FTP';

# Read in command line data.  Eudora is set up to deliver:
# - Time/Date of email
# - Author of email
# - Recipient of email
# - Subject of email
# - Filename of local copy of email
my ($time, $to, $from, $subject, $cc, $file) = ParseCommandLine(@ARGV);

# Determine where to put data on Qube.
my $mailbox;
if ($to =~ m/cdlftp-l/i or $cc =~ m/cdlftp-l/i ) {
    $mailbox = $Cdlftp;
} elsif ($to =~ m/cdlsnap-l/i or $cc =~ m/cdlsnap-l/i) {
    $mailbox = $Cdlsnapshot;
} elsif ($to =~ m/cdlproject/i or $cc =~ m/cdlproject/i) {
    $mailbox = $Cdlproject;
} elsif ($to =~ m/mel-t-l/i or $cc =~ m/mel-t-l/i) {
    $mailbox = $MelvylT;
} elsif ($to =~ m/cdltech-l/i or $cc =~ m/cdltech-l/i) {
    $mailbox = $CdlTech;
} else { $mailbox = $qube . '\Misc'; }

# Create unique filename of subject and time/date for Qube archvie.  
# Must strip off all illegal DOS filename character. Uggh!
my $filename = ToDOS("${subject}-${time}");
my $email = "$mailbox\\$filename.txt";

open (EMAIL, ">$email") || die "Can not open email document: $email";
print EMAIL "--- HEADER start ---\n";
print EMAIL "Time: $time\n";
print EMAIL "From: $from\n";
print EMAIL "To: $to\n";
print EMAIL "Subject: $subject\n";
print EMAIL "CC: $cc\n";
print EMAIL "Mailbox: $mailbox\n";
print EMAIL "Local File: $file\n";
print EMAIL "Remote File: $email\n";
print EMAIL "--- HEADER end ---\n";
die "Email file does not exist: $file" unless -e $file;
print EMAIL "\n\n--- BODY start ---\n";

# Ignore until delimiter (<x-flowed> or X-UIDL).
# Add a new last line if more delimiters are discovered.
my $temp = "";
my $HTML = 0;
open (FILE, "<$file") || die "could not open email file: $file";
while (<FILE>) { 
    $HTML = 1 if $_ =~ m/x-html/;
    last if $_ =~ m/^<x-flowed>/;
    last if $_ =~ m/^X-UIDL/;
}
while (<FILE>) {
    $HTML = 1 if $_ =~ m/x-html/;
    last if $_ =~ m/^<\/x-flowed>/;	
    $temp = $temp . $_;
}
close (FILE);

# Strip out HTML tages (if any)
if ($HTML) {
    # Create temp file to house the HTML data.
    # The HTML module needs a file as input.
    open(TEMP, ">temporary.$$");
    print TEMP "$temp";
    close (TEMP);
    my $tree = HTML::TreeBuilder->new->parse_file("temporary.$$");
    my $formatter = HTML::FormatText->new(leftmargin=>0, rightmargin=>50);
    print EMAIL $formatter->format($tree);
    unlink "temporary.$$";	# remove temp file
} else {
    print EMAIL "$temp";
}
    
print EMAIL "--- BODY end ---\n";
close (EMAIL);

exit;


sub ParseCommandLine {
    my ($time, $to, $from, $subject, $cc, $file);
    my $delimiter = '::::';

    # AppendArgs will create a string from the delimted portion
    # of the command argument array with with either a single space 
    # or a null as padding between the array elements.  For example,
    # the array qw( hello world &&&) called with a space padding will
    # return the string "hello world"
    $time = AppendArgs('', $delimiter, \@_);
    $time =~ s/-\d{4}$//;
    $to = AppendArgs(' ', $delimiter, \@_);
    $from = AppendArgs(' ', $delimiter, \@_);
    $subject = AppendArgs(' ', $delimiter, \@_);
    $cc = AppendArgs(' ', $delimiter, \@_);
    $file = shift @_;

    return $time, $to, $from, $subject, $cc, $file;
}

sub AppendArgs {
    # Extract \S+ type regular expressions from input array, returning
    # character string of delimited text.
    my $padding = shift @_;
    my $delimiter = shift @_;
    my $cmdref = shift @_;
    my $arg = "";
    my $temp = shift @$cmdref;
    while ($temp ne $delimiter) {
        $arg = $arg . $temp . $padding; 
        $temp = shift @$cmdref;
    }

    return $arg;
}

sub ToDOS {
    # Eliminate special DOS characters.
    my $dosname = shift @_;
    $dosname =~ s/\\|\/|\||\*|\?|\"|<|>|://g;

    return $dosname;
}
