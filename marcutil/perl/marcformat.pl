#! /usr/bin/perl
#
# Usage: marcformat.pl <configfile>
#
#
# CVS Tracking info:
#
# $Revision: 1.5 $
# $Author: smcgovrn $
# $Date: 2002/09/20 22:12:12 $
# $Id: marcformat.pl,v 1.5 2002/09/20 22:12:12 smcgovrn Exp $
#
#-------------------------------------------------------------------------------

#print "inc = ", (join(':',@INC)), "\n";

use strict;
use MARC;
use IO::Handle;

#-------------------------------------------------------------------------------
# Set autoflush now, before we do any printing.
#-------------------------------------------------------------------------------
autoflush STDERR 1;
#autoflush STDOUT 1;

my $usage = "\n"
          . "Usage: marcformat.pl configfile\n"
          . "\n"
          . "The config file must be specified, and must contain at least\n"
          . "an entry for infile. All other parameters will default.\n"
          . "Use the --chelp switch to get help with the config file.\n"
          . "\n";


my $chelp = "\n"
          . "Config file parameters for marcformat.pl\n"
          . "\n"
          . "  infile:   An input file of records in usmarc format.\n"
          . "                Defaults to STDIN.\n"
          . "\n"
          . "  infmt:    The input format, in usmarc or marcmaker.\n"
          . "                Default is ascii.\n"
          . "\n"
          . "  outfile:  The output file to create.\n"
          . "                Defaults to STDOUT.\n"
          . "\n"
          . "  outfmt:   The output format, in usmarc or ascii.\n"
          . "                Default is ascii.\n"
          . "                Aliased to 'format' for backward compatiblity\n"
          . "\n"
          . "  skip:     Skip the first n records.\n"
          . "                Default is 0.\n"
          . "  maxin:    Maximum number of records to process.\n"
          . "                Default is all.\n"
          . "  maxout:   Maximum number of records to output.\n"
          . "                Default is all.\n"
          . "\n"
          . "  field:    Field to look for.\n"
          . "                If no field is specified the record will be selected\n"
          . "                for output without further checking.\n"
          . "  subfield: Subfield character to look for.\n"
          . "  regex:    Regular expression in Perl syntax to use for selection.\n"
          . "  nregex:   Regular expression in Perl syntax to use for bypass.\n"
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
                'nregex' => undef
               );

my $param = shift;    # Command line parameter
my $debug = undef;    # set to true to turn on debug mode

#-------------------------------------------------------------------------------
# If we have been given a parameter, check if they are asking for help,
# otherwise treat it as a configuration file name.
#-------------------------------------------------------------------------------
if ($param)
{
    # Handle -h or --help
    if (lc($param) eq '-h' || lc($param) eq '--help')
    {
        print STDERR $usage;
        exit(0);
    }

    # Handle -c or --chelp
    if (lc($param) eq '-c' || lc($param) eq '--chelp')
    {
        print STDERR $chelp;
        exit(0);
    }

    print STDERR "Processing config file $param\n" if $debug;
    getconfig($param, \%runparms);
}
else
{
    print STDERR "\nConfig file not specified\n";
    print STDERR $usage;
    exit(0);
}

printparms(\%runparms) if $debug;

print STDERR "Got config file okay\n" if $debug;

if (startrun(\%runparms))
{
    print STDERR "Start run okay\n" if $debug;
    printparms(\%runparms) if $debug;

    processfile(\%runparms);
    endrun(\%runparms);
}
else
{
    print STDERR "Start run failed\n";
    printparms(\%runparms) if $debug;
}

exit(0);

#-------------------------------------------------------------------------------
# Read the configuration file to get the runtime environment. No assumptions
# are made as to which parameters are specified. Those we find are set, the
# others will be set to default values later.
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
        print STDERR "name = '$name' value = '$value'\n" if $debug;
        if (exists $runparms->{$name})     # if this is a name we like
        {
            $runparms->{$name} = $value;   # set the value
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

    my $infile = $runparms->{'infile'};
    my $infmt = $runparms->{'infmt'};
    my $outfile = $runparms->{'outfile'};
    my $outfmt = $runparms->{'outfmt'};
    my $skip = $runparms->{'skip'};
    my $maxin = $runparms->{'maxin'};
    my $maxout = $runparms->{'maxout'};
    my $field = $runparms->{'field'};
    my $subfield = $runparms->{'subfield'};
    my $regex = $runparms->{'regex'};
    my $nregex = $runparms->{'nregex'};

    my $found = undef;
    my $marcrec = undef;
    my $incnt = 0;
    my $outcnt = 0;
    my $starttime = time;
    my $endtime = undef;
    my $runtime = undef;

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

    print STDERR "Read $incnt records; Skipped $skip records; Selected $outcnt records\n";
    print STDERR "Start time  = $stimestr\n";
    print STDERR "End time    = $etimestr\n";
    print STDERR "Run time    = $runtime seconds\n";
    print STDERR "Process times:\n";
    print STDERR "utime = $user stime = $system cutime = $cuser cstime = $csystem\n";

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
