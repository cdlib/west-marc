#! /usr/bin/perl -w
#
# Send email notification of the results of marc conversion run.
#
# Usage: cnvnotify [options] --contacts contactsfile --source source <files_to_send>
#
# Options:
#   --contacts (required) the contact list of email adresses for each source
#   --source   (required) the data source, e.g. glucb
#   --recurse  process directories recursively, use --norecurse to force off
#   --help     print this usage message
#   --debug    turn on debugging messages, use --nodebug to force off
#   --quite    turn off informational messages, use --noquite to force on
#
# The files will be sent to each contact found in the contact list
# for the specifed source, and to each address listed under source 'all'.
#
#
# CVS Tracking info:
#
# $Revision: 1.1 $
# $Author: smcgovrn $
# $Date: 2002/10/29 22:43:32 $
# $Id: cnvnotify.pl,v 1.1 2002/10/29 22:43:32 smcgovrn Exp $
#
#-------------------------------------------------------------------------------
use strict;
use Cwd qw(chdir cwd getcwd);
use English;
use File::Basename;
use Getopt::Long;

#-------------------------------------------------------------------------------
# Declare some global variables
#-------------------------------------------------------------------------------
$main::debug = 0;               # Global debug indicator

#-------------------------------------------------------------------------------
# Handy usage string in case the user requests --help
#-------------------------------------------------------------------------------
my $usage = "\n"
. "Usage:\n"
. " cnvnotify.pl [options] --contacts contactsfile --source source  <files_to_send>\n"
. "\n"
. " Options:\n"
. "   --contacts (required) the contact list of email adresses for each source\n"
. "   --source   (required) the data source, e.g. glucb\n"
. "   --recurse  process directories recursively, use --norecurse to force off\n"
. "   --help     print this usage message\n"
. "   --debug    turn on debugging messages, use --nodebug to force off\n"
. "   --quite    turn off informational messages, use --noquite to force on\n"
. "\n"
. " The files will be sent to each contact found in the contact list\n"
. " for the specifed source, and to each address listed under source 'all'\n"
. "\n";

#-------------------------------------------------------------------------------
# Command line options
#-------------------------------------------------------------------------------
my $contacts = undef;
my $source   = undef;
my $recurse  = undef;
my $help     = undef;
my $debug    = undef;
my $quiet    = undef;
my @fnames   = ();
my %clist    = (); # contacts hash keyed by source

#-------------------------------------------------------------------------------
# Parse the command line options
#-------------------------------------------------------------------------------
unless (GetOptions('contacts=s' => \$contacts,
                   'source=s'   => \$source,
                   'recurse!'   => \$recurse,
                   'quiet!'     => \$quiet,
                   'help'       => \$help,
                   'debug!'     => \$debug))
{
    die "Failed to parse command line\n";
}

#-------------------------------------------------------------------------------
# Set the debug global if that option is specified.
#-------------------------------------------------------------------------------
$main::debug = $debug if (defined $debug);

#-------------------------------------------------------------------------------
# Set the files to send list from the remaining parameters.
#-------------------------------------------------------------------------------
@fnames = @ARGV;

#-------------------------------------------------------------------------------
# When debug is on print the command line parameter before any other processing.
#-------------------------------------------------------------------------------
if ($main::debug)
{
    print STDERR ("options:",
                  " contacts = ", (defined $contacts ? "$contacts" : "undef"),
                  " source = ", (defined $source ? "$source" : "undef"),
                  " help = ", (defined $help ? "$help" : "undef"),
                  " debug = ", (defined $debug ? "$debug" : "undef"),
                  " recurse = ", (defined $recurse ? "$recurse" : "undef"), "\n");

    if (@fnames)
    {
        print STDERR "files[", scalar(@fnames), "]: ", (join(' ',@fnames)), "\n";
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
# Issue an error message and quit if no files to send are specified.
#-------------------------------------------------------------------------------
unless (@fnames)
{
    print STDERR "No files to send - exiting\n";
    exit(0);
}

#-------------------------------------------------------------------------------
# If no source is supplied then issue an error message and quit.
# Otherwise cast it to lower case for canonical hash lookups.
#-------------------------------------------------------------------------------
die "Required parameter --source was not supplied\n$usage" unless (defined $source);
$source = lc($source);

#-------------------------------------------------------------------------------
# If no contacts file is supplied then issue an error message and quit.
# Otherwise parse the contacts file. If no contacts were found, issue
# issue an error message and quit.
#-------------------------------------------------------------------------------
die "Required parameter --contacts not supplied\n$usage" unless (defined $contacts);
parseContacts($contacts, \%clist);
die "Failed to parse contacts list" unless (%clist);

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

#-------------------------------------------------------------------------------
# Iterate over the file names from command line.
#-------------------------------------------------------------------------------
foreach my $name (@fnames)
{
    if (-f "$name")
    {
        $rc = sendFile($name, $source, \%clist);
    }
    elsif (-d "$name")
    {
        $rc = processDir($recurse, $name, $source, \%clist);
    }
    else
    {
        print STDERR "File '$name' was not found or is not a regular file or directory\n";
        $rc = 8;
    }
}

print STDERR "cnvnotify.pl: return code = $rc\n";

exit $rc;
#-------------------------------------------------------------------------------
# end of top level logic, subroutines follow
#-------------------------------------------------------------------------------


#-------------------------------------------------------------------------------
# Parse the contacts list and store the results in a hash.
#-------------------------------------------------------------------------------
sub parseContacts
{
    my $rc       = 0;                   # return code
    my $contacts = shift;               # contacts file name
    my $clist    = shift;               # contacts hash: source -> Email addresses
    my $src      = undef;               # source, e.g. glucb or drla
    my @email    = undef;               # email addresses for a source

    die "Contacts list is not a regular file" unless (-f $contacts);
    die "Failed to open contacts list: $contacts" unless (open CFIN, "<$contacts");

    while (<CFIN>)
    {
        chomp;                          # remove line terminator

        s/\#.*$//og;                    # trim comments
        s/^\s+//og;                     # trim leading whitespace
        s/\s+$//og;                     # trim trailing whitespace

        next if length($_) == 0;        # skip if nothing left
        ($src,@email) = split(/\s+/);   # get name and value pair
        next unless ($src && @email);   # skip if either is missing
        $src = lc($src);                # lower case the key

        print STDERR "source = '$src' email = '", join(' ', @email), "'\n" if $main::debug;

        if (exists $clist->{$src})      # if we already have this source
        {
            push @{$clist->{$src}}, @email; # add to the emails for this source
        }
        else
        {
            $clist->{$src} = [];        # otherwise, create a new entry
            push @{$clist->{$src}}, @email; # and add the emails for this source
        }
    }

    if ($main::debug)
    {
        print STDERR "parsed contacts list:\n";
        foreach $src (keys %$clist)
        {
            print STDERR "source = $src\n";
            my @recips = @{$clist->{$src}};
            print STDERR "  eaddrs: ", (join(' ', @recips)), "\n";
        }
    }
    return $rc;
}

#-------------------------------------------------------------------------------
# Send a file to the recipients on the contact list.
#-------------------------------------------------------------------------------
sub sendFile
{
    my $rc = 0;
    my $file = shift;
    my $src = shift;
    my $clist = shift;
    my @recips = ();
    print STDERR "Sending file $file for source '$src'\n" if $main::debug;

    if (exists $clist->{$src})
    {
        print STDERR "sf: for '$src' found '", (join(' ',@{$clist->{$src}})), "'\n"
        if $main::debug;
        push @recips, @{$clist->{$src}};
    }

    unless (@recips)
    {
        print STDERR "Warning: no recipients found for source: '$src' - relying on 'all'\n";
    }

    if (exists $clist->{'all'})
    {
        print "sf: for 'all' found '", (join(' ',@{$clist->{'all'}})), "'\n"
        if $main::debug;
        push @recips, @{$clist->{'all'}};
    }

    unless (@recips)
    {
        print STDERR "No recipients found for source '$src' or 'all'\n";
        return $rc;
    }

    my $rlist = join(',', @recips);
    my $subject = "CDL Conversion Report - " . basename($file);

    unless (open(RPT, "<$file"))
    {
        print STDERR "Cannot open report file for reading: $file\n";
        return 4;
    }

    #my $mcmd = "mail -t $rlist"; # pinatubo
    my $mcmd = "mail -s '$subject' $rlist"; # aix

    unless (open(MCMD, "|$mcmd"))
    {
        print STDERR "Cannot open pipe to the mail command: $mcmd\n";
        return 4;
    }
    print STDERR "opened mail command: $mcmd\n" if $main::debug;

    #print MCMD "Subject: $subject\n"; # pinatubo

    while (<RPT>)
    {
        print MCMD "$_";
    }

    close RPT;
    close MCMD;

    print STDERR "Sent file: $file\nTo: $rlist\n";

    return $rc;
}

#-------------------------------------------------------------------------------
# Process a directory.
#-------------------------------------------------------------------------------
sub processDir
{
    my $rc = 0;

    print "Warning: Directories are not processed at this time\n";

    return $rc;
}
