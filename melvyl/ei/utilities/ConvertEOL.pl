#! /usr/local/bin/perl -w

# Purpose: Text conversion utility. Different OS represent end of line
#          differently.  THis utility will convert amongst formats.
#          DOS:  <CR><LF>
#          UNIX: <LF>
#          Mac:  <CR>
#          NULL: No EOL character (Target only).
# Input: Standard input stream to be stripped.
# Output: Stripped standard output stream.
# Usage: ConvertEOL.pl <src> <target> 
#        Src - DOS|MAC|UNIX
#        Target - DOS|MAC|UNIX|NULL
# 

use strict;
my $USAGE =
"ConvertEOL.pl <src> <target>
   <src> - DOS|MAC|UNIX
   <target> - DOS|MAC|UNIX|NULL

Input is supplied through <stdin>
Output is given to <stdout>\n";

die $USAGE unless @ARGV == 2;
my $src = shift @ARGV;
my $target = shift @ARGV;

die $USAGE unless $src =~ m/DOS|MAC|UNIX/;
die $USAGE unless $target =~ m/DOS|MAC|UNIX|NULL/;

if ($src eq "DOS") { $src = "\r\n"; }
elsif ($src eq "MAC") { $src = "\r"; }
else { $src = "\n"; }

if ($target eq "DOS") { $target = "\r\n"; }
elsif ($target eq "MAC") { $target = "\r"; }
elsif ($target eq "NULL") { $target = ""; }
else { $target = "\n"; }

while (<>) {
  s/$src$/$target/;
  print;
}

exit;
