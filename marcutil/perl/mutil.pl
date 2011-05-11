#! /usr/bin/perl
#
# Usage: mutil.pl [options] <configfile>
# Options and config file parameters for mutil.pl
# All of these parameters may be specified as command line options
# prefixing the name with '--', e.g. --outfmt usmarc.
#
#   infile:   An input file of records in usmarc format.
#                 Defaults to STDIN.
#
#   infmt:    The input format, in usmarc or marcmaker.
#                 Default is usmarc.
#
#   outfile:  The output file to create.
#                 Defaults to STDOUT.
#
#   outfmt:   The output format, in usmarc or ascii.
#                 Default is ascii.
#                 Aliased to 'format' for backward compatiblity
#
#   skip:     Skip the first n records.
#                 Default is 0.
#   maxin:    Maximum number of records to process.
#                 Default is all.
#   maxout:   Maximum number of records to output.
#                 Default is all.
#
#   field:    Field to look for.
#                 If no field is specified the record will be selected
#                 for output without further checking.
#   subfield: Subfield character to look for.
#   regex:    Regular expression in Perl syntax to use for selection.
#   nregex:   Regular expression in Perl syntax to use for bypass.
#
# Additional command line options:
#   --config file Config file name
#   --debug       Turn on debugging messages, force off with --nodebug
#   --quiet       Turn off runtime info messages
#   --help        Print the usage message
#   --chelp       Print this config parameters message
#
#
# CVS Tracking info:
#
# $Revision: 1.3 $
# $Author: smcgovrn $
# $Date: 2002/12/13 23:23:34 $
# $Id: mutil.pl,v 1.3 2002/12/13 23:23:34 smcgovrn Exp $
#
#-------------------------------------------------------------------------------
use strict;
use Getopt::Long;
use IO::Handle;
use MARC;

#-------------------------------------------------------------------------------
# Set some wretched global variables.
#-------------------------------------------------------------------------------
$main::debug = 0;               # set to true to turn on debug mode
$main::quiet = 0;               # set to true to turn off info messages

#-------------------------------------------------------------------------------
# Set autoflush now, before we do any printing.
#-------------------------------------------------------------------------------
autoflush STDERR 1;

my $usage = "\n"
          . "Usage: mutil.pl [options] <configfile>\n"
          . "\n"
          . "The config file is optional. All parametetes may be specified\n"
          . "as options. All parameters have defaults.\n"
          . "Use the --chelp switch to get help with the config file,\n"
          . "and also the allowable options.\n"
          . "\n";


my $chelp = "\n"
          . "Options and config file parameters for mutil.pl\n"
          . "All of these parameters may be specified as command line options\n"
          . "by prefixing the name with '--', e.g. --outfmt usmarc.\n"
          . "\n"
          . "  infile:   An input file of records in usmarc format.\n"
          . "                Defaults to STDIN.\n"
          . "\n"
          . "  infmt:    The input format. Either usmarc, marcmaker, or unimarc.\n"
          . "                Default is usmarc.\n"
          . "\n"
          . "  outfile:  The output file to create.\n"
          . "                Defaults to STDOUT.\n"
          . "\n"
          . "  outfmt:   The output format. Either ascii, usmarc, marcmaker, or html.\n"
          . "                Default is ascii.\n"
          . "                Aliased to 'format' for backward compatiblity\n"
          . "\n"
          . "  skip:     Skip the first n records.\n"
          . "                Default is 0.\n"
          . "\n"
          . "  maxin:    Maximum number of records to process.\n"
          . "                Default is all.\n"
          . "\n"
          . "  maxout:   Maximum number of records to output.\n"
          . "                Default is all.\n"
          . "\n"
          . "  field:    Field to look for.\n"
          . "                If no field is specified the record will be selected\n"
          . "                for output without further checking.\n"
          . "\n"
          . "  subfield: Subfield character to look for.\n"
          . "                Requires the field option to be specified.\n"
          . "\n"
          . "  regex:    Regular expression in Perl syntax to use for selection.\n"
          . "                Requires the field option to be specified.\n"
          . "\n"
          . "  nregex:   Regular expression in Perl syntax to use for bypass.\n"
          . "                Requires the field option to be specified.\n"
          . "\n"
          . "Additional command line options:\n"
          . "  --config file Config file name\n"
          . "  --debug       Turn on debugging messages, force off with --nodebug\n"
          . "  --quiet       Turn off runtime info messages\n"
          . "  --help        Print the usage message\n"
          . "  --chelp       Print this config parameters message\n"
          . "\n";

my %runparms = (
                'infile' => undef,
                'infmt' => undef,
                'outfile' => undef,
                'outfmt' => undef,
                'format' => undef,
                'skip' => undef,
                'maxin' => undef,
                'maxout' => undef,
                'field' => undef,
                'subfield' => undef,
                'regex' => undef,
                'nregex' => undef,
                'config' => undef,
                'help' => undef,
                'chelp' => undef,
                'debug' => undef,
                'quiet' => undef
               );

#-------------------------------------------------------------------------------
# Get the options from the command line.
#-------------------------------------------------------------------------------
unless (GetOptions(\%runparms,
                   'infile:s',
                   'infmt:s',
                   'outfile:s',
                   'outfmt:s',
                   'format:s',
                   'skip:i',
                   'maxin:i',
                   'maxout:i',
                   'field:s',
                   'subfield:s',
                   'regex:s',
                   'nregex:s',
                   'config:s',
                   'help',
                   'chelp',
                   'debug!',
                   'quiet!'
                  ))
{
    die "Failed to parse command line\n";
}

#-------------------------------------------------------------------------------
# Set the debug and quiet globals if those options are specified.
#-------------------------------------------------------------------------------
$main::debug = $runparms{'debug'} if (defined $runparms{'debug'});
$main::quiet = $runparms{'quiet'} if (defined $runparms{'quiet'});

#-------------------------------------------------------------------------------
# Print the command line options if debugging is turned on
#-------------------------------------------------------------------------------
printparms(\%runparms) if $main::debug;

#-------------------------------------------------------------------------------
# Handle --help, or -h, and --chelp, or -ch (config help) requests.
#-------------------------------------------------------------------------------
if ($runparms{'help'} || $runparms{'chelp'})
{
    print STDERR $usage if ($runparms{'help'});
    print STDERR $chelp if ($runparms{'chelp'});
    exit(0);
}

#-------------------------------------------------------------------------------
# Set the debug and quiet globals if those options are specified.
#-------------------------------------------------------------------------------
$main::debug = $runparms{'debug'} if (defined $runparms{'debug'});
$main::quiet = $runparms{'quiet'} if (defined $runparms{'quiet'});

#-------------------------------------------------------------------------------
# Optional command line parameter, overrides --config value, if such was
# specified, or defaults to --config value, if not arguments remain.
#-------------------------------------------------------------------------------
my $config = shift || $runparms{'config'};

#-------------------------------------------------------------------------------
# If we have been given a parameter, check if they are asking for help,
# otherwise treat it as a configuration file name.
#-------------------------------------------------------------------------------
if ($config)
{
    print STDERR "Processing config file $config\n" if $main::debug;
    getconfig($config, \%runparms);

    print STDERR "Got config file okay\n" if $main::debug;
    printparms(\%runparms) if $main::debug;
}



#-------------------------------------------------------------------------------
# Set the defaults for options not specified, and if that goes okay, process
# the input file.
#-------------------------------------------------------------------------------
if (startrun(\%runparms))
{
    print STDERR "Start run okay\n" if $main::debug;
    printparms(\%runparms) if $main::debug;

    processfile(\%runparms);
    endrun(\%runparms);
}
else
{
    print STDERR "Start run failed\n";
    printparms(\%runparms) if $main::debug;
}

exit(0);

#-------------------------------------------------------------------------------
# Read the configuration file to get the runtime environment. No assumptions
# are made as to which parameters are specified. Those we find are set, the
# others will be set to default values later, unless they have already been
# set using a --option.
#-------------------------------------------------------------------------------
sub getconfig
{
    my $fname    = shift;               # config file name
    my $runparms = shift;               # reference to parameter hash
    my $name     = undef;               # local name variable
    my $value    = undef;               # local value variable

    unless (-f "$fname" && open(CFIN, "<$fname"))
    {
        die "Cannot open config file: $fname\n"
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
        if (exists $runparms->{$name})     # if this is a name we like
        {
            unless ($runparms->{$name})    # and it is not yet defined
            {
                $runparms->{$name} = $value;   # set the value
            }
        }
    }
    close(CFIN);

    return;
}

#-------------------------------------------------------------------------------
# Set unspecifed parameters to default values. If an input file was specified,
# verify it exists and we can read it. If no input file was specified default
# it to STDIN. If an output file is specified, check that it exists and we can
# write to it. If no outup file is specifed default it to STDOUT.
#
# Return 1 upon success, 0 for failure.
#-------------------------------------------------------------------------------
sub startrun
{
    my $runparms = shift;
    my $rc = 1;
    my $infile = $runparms->{'infile'};
    my $outfile = $runparms->{'outfile'};

  infile:
    {
        if ($infile)
        {
            unless (-e $infile)
            {
                print STDERR "Input file '$infile' not found\n";
                $rc = 0;
                last infile;
            }
            unless (-f $infile)
            {
                print STDERR "Input file '$infile' is not a regular file\n";
                $rc = 0;
                last infile;
            }
            unless (-r $infile)
            {
                print STDERR "Input file '$infile' is not readable\n";
                $rc = 0;
                last infile;
            }
        }
        else
        {
            $runparms->{'infile'} = "&STDIN";
        }
    }

  outfile:
    {
        if ($outfile)
        {
            if (-e $outfile)
            {
                unless (-f $outfile)
                {
                    print STDERR "Output file '$outfile' is not a regular file\n";
                    $rc = 0;
                    last outfile;
                }
                unless (-w $outfile)
                {
                    print STDERR "Output file '$outfile' is not writable\n";
                    $rc = 0;
                    last outfile;
                }
            }
        }
       else
       {
            $runparms->{'outfile'} = "&STDOUT";
       }
    }

    #--------------------------------------------------------------------------------
    # Take the default for 'infmt' if none specified.
    #--------------------------------------------------------------------------------
    unless ($runparms->{'infmt'})
    {
        $runparms->{'infmt'} = 'usmarc';
    }

    #--------------------------------------------------------------------------------
    # Validate 'infmt' - must be either 'usmarc' or 'marcmaker'.
    #--------------------------------------------------------------------------------
    unless ($runparms->{'infmt'} eq 'usmarc'
            || $runparms->{'infmt'} eq 'marcmaker'
            || $runparms->{'infmt'} eq 'unimarc')
    {
        print STDERR ("Invalid input format specified - must be either 'usmarc', 'marcmaker', ",
                      "or 'unimarc': infmt = $runparms->{'infmt'}\n");
        $rc = 0;
    }

    #--------------------------------------------------------------------------------
    # Process 'format' option for backward compatibility.
    # If both 'outfmt' and 'format' are specfied, use 'outfmt', and issue a warning.
    # If only 'format' is specified, use it to set 'outfmt, and issue a warning.
    #--------------------------------------------------------------------------------
    if ($runparms->{'format'})
    {
        if ($runparms->{'outfmt'})
        {
            print STDERR ("Warning: Values specified for both 'outfmt' and 'format'; ",
                          "using value from 'outfmt' = $runparms->{'outfmt'}\n");
        }
        else
        {
            $runparms->{'outfmt'} = $runparms->{'format'};
            print STDERR ("Obsolete parm 'format' used to set 'outfmt' = $runparms->{'format'}\n");
        }
    }

    #--------------------------------------------------------------------------------
    # Take the default for 'outfmt' if none specified.
    #--------------------------------------------------------------------------------
    unless ($runparms->{'outfmt'})
    {
        $runparms->{'outfmt'} = 'ascii';
    }

    #--------------------------------------------------------------------------------
    # Validate 'outfmt' - must be either 'ascii' or 'usmarc'
    #--------------------------------------------------------------------------------
    unless ($runparms->{'outfmt'} eq 'ascii'
            || $runparms->{'outfmt'} eq 'usmarc'
            || $runparms->{'outfmt'} eq 'marcmaker'
            || $runparms->{'outfmt'} eq 'html')
    {
        print STDERR ("Invalid format specified - must either 'ascii', 'usmarc', 'marcmaker, ",
                      "or 'html': outfmt = $runparms->{'outfmt'}\n");
        $rc = 0;
    }

    unless ($runparms->{'skip'})
    {
        $runparms->{'skip'} = 0;
    }

    if ($runparms->{'skip'} && $runparms->{'skip'} !~ /^\d+$/)
    {
        print STDERR ("Invalid skip specified - skip must be numeric: ",
                      "skip = $runparms->{'skip'}\n");
        $rc = 0;
    }

    unless ($runparms->{'maxin'})
    {
        $runparms->{'maxin'} = 999999999;
    }

    if ($runparms->{'maxin'} && $runparms->{'maxin'} !~ /^\d+$/)
    {
        print STDERR ("Invalid maxin specified - maxin must be numeric: ",
                      "maxin = $runparms->{'maxin'}\n");
        $rc = 0;
    }

    unless ($runparms->{'maxout'})
    {
        $runparms->{'maxout'} = 999999999;
    }

    if ($runparms->{'maxout'} && $runparms->{'maxout'} !~ /^\d+$/)
    {
        print STDERR ("Invalid maxout specified - maxout must be numeric: ",
                      "maxout = $runparms->{'maxout'}\n");
        $rc = 0;
    }

    if ($runparms->{'field'} && length($runparms->{'field'}) != 3)
    {
        print STDERR ("Invalid field specified: length not equal to 3 ",
                      "field = $runparms->{'field'}\n");
        $rc = 0;
    }

    if ($runparms->{'subfield'} && length($runparms->{'subfield'}) != 1)
    {
        print STDERR ("Invalid subfield specified: length not equal to 1 ",
                      "field = $runparms->{'subfield'}\n");
        $rc = 0;
    }

    return $rc;
}

#-------------------------------------------------------------------------------
# Use Marc processing facilities defined in MARC.pm to format the output
# as specifed in the config file.
#-------------------------------------------------------------------------------
sub processfile
{
    my $runparms = shift;

    my $infile   = $runparms->{'infile'};
    my $infmt    = $runparms->{'infmt'};
    my $outfile  = $runparms->{'outfile'};
    my $outfmt   = $runparms->{'outfmt'};
    my $skip     = $runparms->{'skip'};
    my $maxin    = $runparms->{'maxin'};
    my $maxout   = $runparms->{'maxout'};
    my $field    = $runparms->{'field'};
    my $subfield = $runparms->{'subfield'};
    my $regex    = $runparms->{'regex'};
    my $nregex   = $runparms->{'nregex'};

    my $found     = undef;
    my $marcrec   = undef;
    my $incnt     = 0;
    my $outcnt    = 0;
    my $starttime = time;
    my $endtime   = undef;
    my $runtime   = undef;

    # open input file
    my $x = MARC->new;
    my $tr = $x->openmarc({'file'=>$infile, 'format'=>$infmt, 'skip'=>$skip})
    || die "Can't open input file '$infile': $!\n";

    open (FHOUT, ">$outfile") || die "Can't open output file '$outfile': $!\n";
    binmode FHOUT;
    select FHOUT;

    # extract records
    my @results = ();
    while (($tr = $x->nextmarc(-1)) && ($outcnt < $maxout) && ($incnt < $maxin))
    {
        $incnt += $tr;
        if (defined $field)
        {
            @results = $x->searchmarc({field=>$field, subfield=>$subfield,
                                       regex=>$regex, notregex=>$nregex});
        }
        else
        {
            $results[0] = 1; # record will always be selected if tag is not defined.
        }

        if (defined $field)
        {
            if (scalar(@results) > 0)
            {
                $outcnt += scalar(@results);

                # if the format is ascii, print record #.
                if ($outfmt eq 'ascii')
                {
                    print "Record $outcnt\n";
                }
                print $x->output({'format'=>$outfmt, 'records'=>\@results});
            }
        }
        else
        {
            # print everything if not selecting on field
            $outcnt += $tr;

            # if the format is ascii, print record #.
            if ($outfmt eq 'ascii')
            {
                print "Record $outcnt\n";
            }
            print $x->output({'format'=>$outfmt});
        }
    }
    close (FHOUT);

    # calculate the run time
    $endtime = time;
    $runtime = $endtime - $starttime;
    my ($user,$system,$cuser,$csystem) = times;
    my $stimestr = localtime($starttime);
    my $etimestr = localtime($endtime);

    unless ($main::quiet)
    {
        print STDERR "Read $incnt records; Skipped $skip records; Selected $outcnt records\n";
        print STDERR "Start time  = $stimestr\n";
        print STDERR "End time    = $etimestr\n";
        print STDERR "Run time    = $runtime seconds\n";
        print STDERR "Process times:\n";
        print STDERR "utime = $user stime = $system cutime = $cuser cstime = $csystem\n";
    }

    return;
}

#-------------------------------------------------------------------------------
# Close the input and output files, we opened any, but leave standard input
# and standard output alone.
#
# This is only a stub, since at this point MARC.pm does not operate upon
# file handles, only file names.
#
#-------------------------------------------------------------------------------
sub endrun
{
    my $runparms = shift;
    my $infile = $runparms->{'infile'};
    my $outfile = $runparms->{'outfile'};
    my ($format,$skip,$maxin,$maxout,$field,$subfield,$reg,$nreg) = undef;
    return;
}

#-------------------------------------------------------------------------------
# Print the runparms hashtable.
#-------------------------------------------------------------------------------
sub printparms
{
    my $runparms = shift;
    my $name = undef;
    my $value = undef;

    print STDERR "---------------------------------------------\n";
    print STDERR "Printing runparms hash...\n";
    foreach $name (sort(keys %{$runparms}))
    {
        $value = $runparms->{$name};
        if (! defined $value)
        {
            $value = "undef";
        }
        print STDERR "name = '$name' value = '$value'\n";
    }
    print STDERR "---------------------------------------------\n";
    return;
}
