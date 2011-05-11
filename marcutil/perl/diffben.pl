#!/usr/bin/perl -w
#
# Copy the marc convert benchmark files.
#
# Usage: diffben.pl [options] <all | rlin | site1 site2 ...>
#
# Where site is in the list:
#   chs, gtu, lcn, lbl, oclc, ucb, ucbl, ucd, ucdl,
#   uci, ucla, ucr, ucsb, ucsc, ucsd, ucsf, unload
#
# If 'all' is specified for the site then files are copied for all known sites.
# If 'rlin' is specified for the site then files are copied for sites 'chs',
# 'lbl', and 'ucbl'.
#
# Options:\n"
#   srcdir    - the directory containing the new benchmark results
#   targetdir - the directory containing the previous benchmark results
#   diffdir   - the directory to place the diff output
#   difflist  - file to contain a list of files with non-empy diffs
#   debug     - turn on debug messages, use --nodebug to force off
#   help      - print this usage message
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

$main::debug = undef;    # set to true to turn on debug mode

#-------------------------------------------------------------------------------
# Handy usage string in case the user requests --help
#-------------------------------------------------------------------------------
my $usage = "\n"
          . "Usage: diffben.pl [options] <all | rlin | site1 site2 ...>\n"
          . "\n"
          . "Where site is in the list:\n"
          . "  chs, gtu, lcn, lbl, oclc, ucb, ucbl, ucd, ucdl,\n"
          . "  uci, ucla, ucr, ucsb, ucsc, ucsd, ucsf, unload\n"
          . "\n"
          . "If 'all' is specified for the site then files are copied for all known sites.\n"
          . "If 'rlin' is specified for the site then files are copied for sites 'chs',\n"
          . "'lbl', and 'ucbl'.\n"
          . "\n"
          . "Options:\n"
          . "  srcdir    - the directory containing the new benchmark results\n"
          . "  targetdir - the directory containing the previous benchmark results\n"
          . "  diffdir   - the directory to place the diff output\n"
          . "  difflist  - file to contain a list of files with non-empy diffs\n"
          . "  debug     - turn on debug messages, use --nodebug to force off\n"
          . "  help      - print this usage message\n"
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
# Setup the diff program name - this needs to be a wrapper script so we
# can get the shell to redirect the output.
#-------------------------------------------------------------------------------
print STDERR "osname = $Config{'osname'}\n" if $main::debug;
my $diffprog = ("$Config{'osname'}" eq 'MSWin32'
                ? uncleanPath($ENV{'CDLHOME'}) . "\\src\\build\\diffwrap.bat"
                : "$ENV{'MB_HOME'}/src/build/diffwrap.sh");

my @diffparams = ('--strip-trailing-cr', '--ignore-matching-lines="\(Start\)\|\(End\)"');


#-------------------------------------------------------------------------------
# The files to diff.
#-------------------------------------------------------------------------------
my @dffiles = (
               "convert.fmt.txt",
               "error.fmt.txt",
               "reject.fmt.txt",
               "reject.messages.txt",
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

my $basedir   = cleanPath((defined $ENV{'CDLHOME'} ? "$ENV{CDLHOME}/run" : "."));
my $srcdir    = undef;  # the directory containing the new benchmark results
my $targetdir = undef;  # the directory containing the previous benchmark results
my $diffdir   = undef;  # the directory to place the diff output
my $difflist  = undef;  # file to contain a list of files with non-empy diffs
my $help      = undef;  # --help indicator
my $debug     = undef;  # --debug indicator
my $site      = undef;  # the current site from the command line
my $dlfh     = undef;   # file handle for difflist
my $rc       = 0;       # Return code

#-------------------------------------------------------------------------------
# Parse the command line options
#-------------------------------------------------------------------------------
unless (GetOptions('srcdir:s'    => \$srcdir,
                   'targetdir:s' => \$targetdir,
                   'diffdir:s'   => \$diffdir,
                   'difflist:s'  => \$difflist,
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

$srcdir    = (defined $srcdir ? cleanPath($srcdir) : "$basedir");
$targetdir = (defined $targetdir ? cleanPath($targetdir) : "$basedir");
$diffdir   = (defined $diffdir ? cleanPath($diffdir) : "$basedir/diff");

print STDERR "Source directory = $srcdir\n" if $main::debug;
print STDERR "Target directory = $targetdir\n" if $main::debug;
print STDERR "Diff directory = $diffdir\n" if $main::debug;

die "Source directory does not exist: $srcdir\n"        unless (-e $srcdir);
die "Source directory is not a directory: $srcdir\n"    unless (-d $srcdir);
die "Target directory does not exist: $targetdir\n"     unless (-e $targetdir);
die "Target directory is not a directory: $targetdir\n" unless (-d $targetdir);

if (-e $diffdir)
{
	die "Diff directory exists, but is not a diretcory: $diffdir\n" unless (-d $diffdir);
}
else
{
	mkdir "$diffdir"; # make the diff directory if it does not exist
}

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
# Diff the files for the sites we selected. We assume the source directory is
# flat, in that the source files for all the sites are in that directory.
# For each site:
#   Locate the latest benchmarks in the target dir.
#   If there are no entries then skip that site.
#   Make the site directory in the diff dir.
#   Diff the source files against the target file and place the output
#   in the diff dir.
#-------------------------------------------------------------------------------
foreach $site (sort(keys %runtable))
{
	my $skipsite = 0;
    my $sitedir = "$targetdir/$site";

    unless (-d $sitedir)
    {
        print "No site directory in target directory($sitedir) - site skipped: $site\n";
		next;
    }

    unless (opendir INDIR, $sitedir)
    {
        print STDERR "Could not open site directory $sitedir\n";
        next;
    }

    my @sflist = grep !/^\.\.?$/, readdir INDIR;
    closedir INDIR;

    print STDERR "Found ", (scalar(@sflist)), " entries in directory $sitedir\n"
    if $main::debug;

    unless (@sflist)
    {
        print "No entries in target site directory($sitedir) - site skipped: $site\n";
		next;
    }

    my $lastdirnbr = 0;
	my $lastdir = undef;
    foreach my $sf (@sflist)
    {
        print STDERR "sitefile = $sf\n" if $main::debug;

        next unless (-d "$sitedir/$sf");
        next unless ($sf =~ /^(\d+)/);
        my $td = $1;
		if ($td > $lastdirnbr)
		{
			$lastdirnbr = $td;
			$lastdir = $sf;
		}

        print STDERR "tempdirnbr = $td lastdirnbr = $lastdirnbr lastdir = $lastdir\n" if $main::debug;
    }

    unless ($lastdir)
    {
        print "No suitable entry in target site directory($sitedir) - site skipped: $site\n";
		next;
    }

    print STDERR "Running diffs for site '$site'\n";

    mkdir "$diffdir/$site"; # make directory for diff output

    my $i = 0;
    foreach my $file (@dffiles)
    {
        $i++;
        my @dcmd = ("$diffprog",
                    @diffparams,
                    "$srcdir/$site.$file",
                    "$sitedir/$lastdir/$site.$file",
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
