#! /usr/bin/perl -w

# ------------------------------------
#
# Project:	Melvyl
#
# Name:		hexit.pl
#
# Function:	To enable people to get unprintable characters into files.
#
# Command line parameters:
#		1 - The name of the input file.  (The contents of this
#			file is not changed.  It is copied to the output
#			file, and the changes to the content of the file
#			are made during the copying.)
#
#		2 - The name of the output file.
#
#		3 - The character to search for in the input file.
#
#		4 - A representation of the character, using hex digits,
#			with which to replace the character specified as
#			command line parameter number 3 in the input file,
#			while copying it to the output file.  It can be
#			specified as either "HEXxx" or "xx" where "xx" is
#			a sequence of two hex characters, and either lower
#			or upper case letters can be used, either for the
#			letters "HEX" or for the hex digits.
#
# Author:	Michael A. Russell
#
# Revision History:
#		3/9/2004 - MAR - Initial writing
#		4/15/2004 - MAR - Change the substitution technique from
#			"s/from/to/g" to a walk through the string.  This
#			allows asterisks to be used.
# 6/17/2011 dbb - Copied from escher:/eianalysis/vls, changed path to perl
#
# ------------------------------------

# Get command name for error messages.
$pos = rindex($0, "/");
$c = ($pos > 0) ? substr($0, $pos + 1) : $0;
undef $pos;

# Examine the command line parameters.
if (scalar(@ARGV) < 4) {
	die "$c:  this command requires 2 parameters, stopped";
	}
if (scalar(@ARGV) > 4) {
	print "$c:  using only the first 2 command line parameters - the ",
		"rest are ignored\n";
	}

# Identify the input and output files.
$input_file = $ARGV[0];
$output_file = $ARGV[1];
$from_char = $ARGV[2];
$to_char = $ARGV[3];

# Make sure that param 3 is a single character.  (This is not strictly
# a requirement, and it would work just fine if param 3 were not a single
# character, but that was in the specs, so I check for it.  All that
# prevents this program from working with a param 3 that is longer than
# one character is the removal of the following check.)
#
# The above is no longer true, now that I don't use "s/from/to" to do
# the substitution.
if (length($from_char) != 1) {
	die "$c:  the length of the 3rd command line parmaeter must be ",
		"one, stopped";
	}

# Now check the 4th param.
unless ($to_char =~ /^([Hh][Ee][Xx])?([0-9A-Fa-f]{2})$/) {
	die "$c:  the format of the 4th command line parameter (\"$ARGV[3]\") ",
		"is invalid, stopped";
	}
$to_char = chr(hex($2));

# Open them.
open(INPFIL, $input_file) ||
	die "$c:  unable to open \"$input_file\", $!, stopped";
open(OUTFIL, ">$output_file") ||
	die "$c:  unable to open \"$output_file\" for output, $!, stopped";

while(<INPFIL>) {
	chomp;
	# Search for the encoding, and replace it if it's found.
#	s/$from_char/$to_char/g;
	for ($i = 0; $i < length($_); $i++) {
		if (substr($_, $i, 1) eq $from_char) {
			substr($_, $i, 1) = $to_char;
			}
		}
	print OUTFIL "$_\n";
	}
close(INPFIL);
close(OUTFIL);
