#!/usr/local/bin/perl 

# What this program will do:
#
#   1. Read records in usmarc format from an input file.
#
#   2. Manipulate records based on the following arguments:
#      a.  Ignore: Ignore a number of records.
#                  Default is 0.
#      b.  Max input: Maximum number of records to process.
#                     Default is all.
#      c.  Max output: Maximum number of records to output.
#                      Default is all.
#      d.  Tag: Tag to look for. 
#               If no tag specified the record will be selected and output  
#               without further checking.
#      e.  Subfield: Subfield character to look for.
#      f.  Regular expression: regular expression in Perl syntax.
#      g.  Format: the output format, in usmarc or ascii.
#
#   3. Create output files.
#      a.  Separate(yes/no): 'yes' if you want to save records not been selected;
#                            Otherwise, only records selected will be saved.
#      b.  Output file: File name to save records selected.
#      c.  Output format: Specify one of the three options: usmarc, ascii and marcmaker.
#      d.  Separate file: File name to save records not selected.
#      e.  Separate format: Specify one of the three options: usmarc, ascii and marcmaker.
#
# You can search for:
#   1) records that contain a particular field, or field and subfield;
#   2) records that have fields or subfields that match a regular expression;
#   3) and records that have fields or subfields that do not match a regular expression.
#
# Note:
#   * If you did not provide the field(tag) to look for, no further evaluation will
#     be performed. This means input records are simply selected and moved to output
#     file. Of course, the input records will still be controled by ignore, Max input
#     and Max output parameters.
#     Even if you specified the subfield, Regular expression or NOT regular expression,
#     they will be ignored.
#
#   * If you did not provide the output file or the separate file if you chose to 
#     save the separated records, you may see these message:
#    
#      Couldn't open file: No such file or directory at select.pl line ??? 
#      Couldn't close file: Bad file number at select.pl line ???
#     
#     The output will not be saved.
#
# Error messages                  
# If you specify the input format as usmarc, but the file is not; you'll see this message: 
#
#    Invalid record, leader size not numeric at select.pl line nnn
#
# If you specify the input format as marcmaker, but the file is not; you'll see this message: 
#
#    Invalid record, prefix "=LDR  " not found at select.pl line nnn 

use MARC;
use strict;

my @opt_nums = (0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15);
my ($exit,$infile,$fmtin,$outfile,$fmtout,$separate,$sepfile,$fmtsep,
    $ignore,$maxin,$maxout,$field,$subfield,$reg,$nreg,$run) = @opt_nums;

my $menu = [{opt=>$exit,text=>'Exit',value=>undef},
            {opt=>$infile,text=>'Input file',value=>undef},
            {opt=>$fmtin,text=>'Input format',value=>'usmarc'},
            {opt=>$outfile,text=>'Output file',value=>undef},
            {opt=>$fmtout,text=>'Output format',value=>'ascii'},
            {opt=>$separate,text=>'Separate?(yes/no)',value=>'no'},
            {opt=>$sepfile,text=>'Separate file',value=>undef},
            {opt=>$fmtsep,text=>'Separate format',value=>'usmarc'},
            {opt=>$ignore,text=>'Ignore',value=>0},
            {opt=>$maxin,text=>'Max input',value=>undef},
            {opt=>$maxout,text=>'Max output',value=>undef},
            {opt=>$field,text=>'Field',value=>undef},
            {opt=>$subfield,text=>'Subfield',value=>undef},
            {opt=>$reg,text=>'Regular expression',value=>undef},
            {opt=>$nreg,text=>'NOT regular expression',value=>undef},
            {opt=>$run,text=>'Ready to run',value=>undef}
           ];
my $resp;
my $lineend = "\n";
my $continue = 1;

while ($continue) {
   _disp_menu($menu);
   $resp = _get_resp($menu);
 
   if ($resp eq $exit) {last;}
   elsif ($resp eq $infile) {_get_infile($menu);}
   elsif ($resp eq $fmtin) {_get_fmtin($menu);}
   elsif ($resp eq $outfile) {_get_outfile($menu);}
   elsif ($resp eq $fmtout) {_get_fmtout($menu);}
   elsif ($resp eq $separate) {_get_separate($menu);}
   elsif ($resp eq $sepfile) {_get_sepfile($menu);}
   elsif ($resp eq $fmtsep) {_get_fmtsep($menu);}
   elsif ($resp eq $ignore) {_get_ignore($menu);}
   elsif ($resp eq $maxin) {_get_maxin($menu);}
   elsif ($resp eq $maxout) {_get_maxout($menu);}
   elsif ($resp eq $field) {_get_field($menu);}
   elsif ($resp eq $subfield) {_get_subfield($menu);}
   elsif ($resp eq $reg) {_get_reg($menu);}
   elsif ($resp eq $nreg) {_get_nreg($menu);}
   elsif ($resp eq $run) {_get_run($menu);}
   
}
 
sub _disp_menu {
   my $clear = `clear`;
   my $mref = shift;
   my $i;
   my $num = @$mref;
   print $clear;
   print "\n\tExtract MARC Records\n\n";

   for ($i = 0; $i < $num; $i++){
   #   print "\n";
      print "\t$mref->[$i]->{opt}\t$mref->[$i]->{text}";
      if (defined $mref->[$i]->{value}) {print "  :$mref->[$i]->{value}\n";}
      else {print "\n";}
   }
}

sub _get_resp {
   my $mref = shift;
   my ($good, $resp);

   print "\n\tEnter your choice:";
   chomp($resp = <STDIN>);
   $good = _check_resp($mref, $resp);

   if (! $good) { 
      print "\n$resp is an invalid response! Hit any key to continue."; 
      <>;
   }
   return $resp;
}

sub _check_resp {
   my $mref = shift;
   my $resp = shift;
   my $i;
   my $num = @$mref;

   return 0 if ("@opt_nums" !~ /$resp/);

   for ($i = 0; $i < $num; $i++) {
      if ($mref->[$i]->{opt} == $resp) {
         return 1;
      }
   }
   return 0;
}

sub _get_infile {
   my $mref = shift;
   my $file;
   my $fmt = $mref->[$fmtin]->{value};

   print "\n\tInput file:";
   chomp($file = <>);
   _trim($file);
   unless (-e $file) {
      print "\n$file does not exist. Hit any key to continue.";
      <>; return;
   }
   
   $mref->[$infile]->{value} = $file; 
}

sub _get_fmtin {
   my $mref = shift;
   my $fmt;
   print "\n\tInput format(1 for usmarc; 2 for marcmaker):";
   chomp($fmt = <>);
   _trim($fmt);
   if ($fmt != 1 && $fmt != 2) {     
      print "\nEnter only 1 or 2. Press any key to continue.";
      <>;
   }
   elsif ($fmt == 1) {$mref->[$fmtin]->{value} = 'usmarc';}
   else {$mref->[$fmtin]->{value} = 'marcmaker';}
}

sub _get_outfile {
   my $mref = shift;
   my $file;
   print "\n\tOutput file:";
   chomp($file = <>);
   _trim($file);
   $mref->[$outfile]->{value} = $file;
}

sub _get_fmtout {
   my $mref = shift;
   my $fmt;
   print "\n\tOutput format(1 for usmarc; 2 for ascii; 3 for marcmaker):";
   chomp($fmt = <>);
   _trim($fmt);
   if ($fmt != 1 && $fmt != 2 && $fmt != 3) {     
      print "\nEnter only 1, 2 or 3. Press any key to continue.";
      <>;
   }
   elsif ($fmt == 1) {$mref->[$fmtout]->{value} = 'usmarc';}
   elsif ($fmt == 2) {$mref->[$fmtout]->{value} = 'ascii';}
   else {$mref->[$fmtout]->{value} = 'marcmaker';}
}

sub _get_separate {
   my $mref = shift;
   my $sep;
   print "\n\tSeparate file?(1 for yes; 2 for no):";
   chomp($sep = <>);
   _trim($sep);
   if ($sep != 1 && $sep != 2) {
      print "\nEnter only 1 or 2. Press any key to continue.";
      <>;
   }
   elsif ($sep == 1) {$mref->[$separate]->{value} = 'yes';}
   else {$mref->[$separate]->{value} = 'no';}
}

sub _get_sepfile {
   my $mref = shift;
   my $file;
   print "\n\tSeparate file:";
   chomp($file = <>);
   _trim($file);
   $mref->[$sepfile]->{value} = $file;
}

sub _get_fmtsep {
   my $mref = shift;
   my $fmt;
   print "\n\tSeparate file format(1 for usmarc; 2 for ascii):";
   chomp($fmt = <>);
   _trim($fmt);
   if ($fmt != 1 && $fmt != 2) {
      print "\nEnter only 1 or 2. Press any key to continue.";
      <>;
   }
   elsif ($fmt == 1) {$mref->[$fmtsep]->{value} = 'usmarc';}
   else {$mref->[$fmtsep]->{value} = 'ascii';}
}


sub _get_ignore {
   my $mref = shift;
   my $ig;
   print "\n\tRecords to ignore <cr = 0>:";
   chomp($ig = <>);
   _trim($ig);
   print "ig=$ig\n";
   $mref->[$ignore]->{value} = $ig ? $ig : 0;
}

sub _get_maxin {
   my $mref = shift;
   my $min;
   print "\n\tMaximum records to input <cr = all>:";
   chomp ($min = <>);
   _trim($min);
   $mref->[$maxin]->{value} = $min ? $min : undef;
}

sub _get_maxout {
   my $mref = shift;
   my $mout;
   print "\n\tMaximum records to output <cr = all>:";
   chomp ($mout = <>);
   _trim($mout);
   $mref->[$maxout]->{value} = $mout ? $mout : undef;
}

sub _get_field {
   my $mref = shift;
   my $fld;
   print "\n\tTag to extract(3 digits or RETURN):";
   chomp ($fld = <>);
   _trim($fld);
   $mref->[$field]->{value} = $fld ? $fld : undef;
}

sub _get_subfield {
   my $mref = shift;
   my $sfld;
   print "\n\tSubfield(1 character or RETURN):";
   chomp ($sfld = <>);
   _trim($sfld);
   $mref ->[$subfield]->{value} = $sfld ? $sfld : undef;
}

sub _get_reg {
   my $mref = shift;
   my $regex;
   print "\n\tRegular expression for inclusion:";
   chomp ($regex = <>);
   _trim($regex);
   $mref->[$reg]->{value} = $regex ? $regex : undef;
}

sub _get_nreg {
   my $mref = shift;
   my $nregex;
   print "\n\tRegular expression for exclusion:";
   chomp ($nregex = <>);
   _trim($nregex);
   $mref->[$nreg]->{value} = $nregex ? $nregex : undef;
}

sub _get_run {
   my $mref = shift;

   my ($found, $incnt);
   my $outcnt = 0;
   my $sepcnt = 0;
   my $infile = $mref->[$infile]->{value};
   my $fmtin = $mref->[$fmtin]->{value};
   my $outfile = $mref->[$outfile]->{value};
   my $fmtout = $mref->[$fmtout]->{value};
   my $separate = $mref->[$separate]->{value};
   my $sepfile = $mref->[$sepfile]->{value};
   my $fmtsep = $mref->[$fmtsep]->{value};
   my $ignore = $mref->[$ignore]->{value};
   my $maxin = defined ($mref->[$maxin]->{value}) ? $mref->[$maxin]->{value} : 999999;
   my $maxout = defined ($mref->[$maxout]->{value}) ? $mref->[$maxout]->{value} : 999999;
   my $field = $mref->[$field]->{value};
   my $subfield = $mref->[$subfield]->{value};
   my $regex = $mref->[$reg]->{value};
   my $notregex = $mref->[$nreg]->{value};

   #open input file
   my $x = MARC->new;
   $x->openmarc({file=>$infile,'format'=>$fmtin,lineterm=>"$lineend"})
                               || die "Can't open $infile: $!\n";

   #reinitialize output file
   unlink $outfile;
   open (HDROUT, ">>$outfile");
   if ($separate eq 'yes') {
      unlink $sepfile;
      open(SEPOUT, ">>$sepfile");
   }
 
   #ignore records
   #my $i;
   #for ($i = 0; $i < $ignore; $i++) {
   #   $x->nextmarc(1);
   #   $x->deletemarc();
   #}
   if ($ignore > 0) {
      $x->nextmarc($ignore);
      $x->deletemarc();
   }

   #extract records
   my @results;
   my $s;
   print "In progress....,\n";
   while (($s=$x->nextmarc(1)) && ($outcnt < $maxout) && ($incnt < $maxin)) {
      #unless ($s) {print "Read failed\n"; return;}
      $incnt++;
      if (defined $field) {
         @results = $x->searchmarc({field=>$field, subfield=>$subfield,
                                    regex=>$regex, notregex=>$notregex});
      }
      else {$results[0] = 1}; #record will always be selected if tag is not defined.

      if (defined @results[0]) {
         $outcnt++;

         # if the format is ascii, print record #.
         if ($fmtout eq 'ascii') {
            open (HDROUT, ">>$outfile");
            print HDROUT "Record $outcnt\n"; 
            close HDROUT;
         }
         $x->output({file=>">>$outfile", 'format'=>$fmtout, lineterm=>"\n"});
      }
      elsif ($separate eq 'yes') { #dump records not been selected here!!
         $sepcnt++;
         if ($fmtsep eq 'ascii') {
            open (SEPOUT, ">>$sepfile");
            print SEPOUT "Record $sepcnt\n";
            close SEPOUT;
         }
         $x->output({file=>">>$sepfile", 'format'=>$fmtsep, lineterm=>"\n"});
      }
      $x->deletemarc();
   }
   print "\nRead $incnt records;  Ignored $ignore records;  Selected $outcnt records\n";
   print "Hit any key to continue."; <>;
}
 
sub _trim {
   my @out = @_;
   for (@_) {
      s/^\s+//;
      s/\s+$//;
   }
   return wantarray ? @out : @out[0];
}


package MARC;
####################################################################
# This searchmarc() is copied from MARC.pm module. It fixes a bug  #
# that is causing the following error message if a field or        #
# subfield value to be searched against by a regular expression    #
# contains a "$" sign:                                             #
#                                                                  #
#  Global symbol "$aZZ00002195" requires explicit package name at (#
#                                                                  #
# This is the fix:   $$y =~ s/\$/\\\$/g;                           #
# It basically prefixes all "$" signs with the escape character.   #
####################################################################
####################################################################
# searchmarc() is method for searching a MARC object for specific  #
# values. It will return an array which contains the record        #
# numbers that matched.                                            #
####################################################################
sub searchmarc {
    my $marc=shift;
    my $template=shift;
    return unless (ref($template) eq "HASH");
    my $params = _params($template,@_);

    my $field=$params->{field} || return;
    my $subfield=$params->{subfield};
    my $regex=$params->{regex};
    my $notregex=$params->{notregex};
    my @results;
    my $searchtype;

       #determine the type of search 
    if ($field and not($subfield) and not($regex) and not($notregex)) {
	$searchtype="fieldpresence"}
    elsif ($field and $subfield and not($regex) and not($notregex)) {
	$searchtype="subfieldpresence"}
    elsif ($field and not($subfield) and $regex) {
	$searchtype="fieldvalue"}
    elsif ($field and $subfield and $regex) {
	$searchtype="subfieldvalue"}
    elsif ($field and not($subfield) and $notregex) {
	$searchtype="fieldnotvalue"}
    elsif ($field and $subfield and $notregex) {
	$searchtype="subfieldnotvalue"}

       #do the search by cycling through each record
    for (my $i=1; $i<=$#$marc; $i++) {

	my $flag=0;
	if ($searchtype eq "fieldpresence") {
	    next unless exists $marc->[$i]{$field};
	    push(@results,$i);
	}
	elsif ($searchtype eq "subfieldpresence") {
	    next unless exists $marc->[$i]{$field};
	    next unless exists $marc->[$i]{$field}{$subfield};
	    push(@results,$i);
	}
	elsif ($searchtype eq "fieldvalue") {
	    next unless exists $marc->[$i]{$field};
	    next unless exists $marc->[$i]{$field}{field};
	    my $x=$marc->[$i]{$field}{field};
	    foreach my $y (@$x) {
		my $z=_joinfield($y,$field);
                $z =~ s/\$/\\\$/g;
		if (eval qq("$z" =~ $regex)) {$flag=1}
	    }
	    if ($flag) {push (@results,$i)}
	}
	elsif ($searchtype eq "subfieldvalue") {
	    next unless exists $marc->[$i]{$field};
	    next unless exists $marc->[$i]{$field}{$subfield};
	    my $x=$marc->[$i]{$field}{$subfield};
	    foreach my $y (@$x) {
                my $temp = $$y;
	        $temp =~ s/\$/\\\$/g;
		if (eval qq("$temp" =~ $regex)) {$flag=1}
	    }
	    if ($flag) {push (@results,$i)}
	}
	elsif ($searchtype eq "fieldnotvalue" ) {
	    next unless exists $marc->[$i]{$field};
	    next unless exists $marc->[$i]{$field}{field};
	    my $x=$marc->[$i]{$field}{field};
	    if (not($x)) {push(@results,$i); next}
	    foreach my $y (@$x) {
		my $z=_joinfield($y,$field);
                $z =~ s/\$/\\\$/g;
		if (eval qq("$z" =~ $notregex)) {$flag=1}
	    }
	    if (not($flag)) {push (@results,$i)}
	}
	elsif ($searchtype eq "subfieldnotvalue") {
	    next unless exists $marc->[$i]{$field};
	    next unless exists $marc->[$i]{$field}{$subfield};
	    my $x=$marc->[$i]{$field}{$subfield};
	    if (not($x)) {push (@results,$i); next}
	    foreach my $y (@$x) {
                my $temp = $$y;
                $temp =~ s/\$/\\\$/g;
		if (eval qq("$temp" =~ $notregex)) {$flag=1}
	    }
	    if (not($flag)) {push (@results,$i)}
	}
    }
    return @results;
}

