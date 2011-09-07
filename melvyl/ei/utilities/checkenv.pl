#!/usr/local/bin/perl

# checkenv.pl
# Programmer: Debra Bartling
# Date: 12/19/2001
# Description: Checks and reports key environment variables for Aleph 
# instances. Oracle database, A tree, U tree, library, and CVS.
# 1/10/02: Fixed bug when A tree and U tree are both ../a5x_5/. This 
# program should be re-checked whenever the instances change. (DB)

use Env;

# Tie environment variables to global perl variables with the same name.
Env::import();

print ("ORACLE_SID=$ORACLE_SID");
if ( $ORACLE_SID eq "aleph0" ) {
    print (": You are using the old Oracle database.\n");
}
elsif ( $ORACLE_SID eq "aleph1" ) {
    print (": You are using the 630K Oracle database.\n"); 
}
elsif ( $ORACLE_SID eq "aleph2" ) {
    print (": You are using the Ex Libris database.\n"); 
}
else {
    print ("undefined: Your Oracle database is not set.\n"); 
}

print ("Active library=$active_library\n");

# A tree begins at $aleph_dev
if ($aleph_dev eq "") {
    print ("A tree is not defined!\n");
} 
else {
    print ("A tree=$aleph_dev\n");

    # Check for environment variables that may be inconsistent.
    # If the A tree matches a5x_5, create a pattern to match a5[^x]_5 
    # Assume for this version that all environment variables for the A trees 
    # are in the form .../a5x_5/... where x is a digit 0,1,2 ...

    $flag = 0;
    $a_pattern = $aleph_dev;
    if ( $a_pattern =~ /(.*)a5(\d)_5(.*)/ ) {
	$a_replace = $2;
    }
    $a_pattern =~ s/$a_replace/[^$a_replace]/;
    while (($key,$value) = each %ENV) {
        if ( $value =~ /$a_pattern/ ) {
            if ( $flag == 0 ) {
                print("Some environment variables are inconsistent!\n");
            }
            print ("    $key=$value\n");
	    $flag++;
        }
    }
}

# U tree begins at $alephe_dev
if ($alephe_dev eq "") {
    print ("U tree is not defined!\n");
} 
else {
    print ("U tree=$alephe_dev\n");

    # Check for environment variables that may be inconsistent.
    # The "U" tree may not match the pattern /u5x_5/ ....
    # For example, the m525 instance has /a52_5/ as the U tree.

    $flag = 0;
    $u_pattern = $alephe_dev;
    if ( $u_pattern =~ /(.*)u5(\d)_5(.*)/ ) {
	$u_replace = $2;
    }
    elsif ( $u_pattern =~ /(.*)a5(\d)_5(.*)/ ) {
	$u_replace = $2;
        print("A tree and U tree are the same.\n");
    }
    $u_pattern =~ s/$u_replace/[^$u_replace]/;
    while (($key,$value) = each %ENV) {
        if ( $value =~ /$u_pattern/ ) {
            if ( $flag == 0 ) {
                print("Some environment variables are inconsistent!\n");
            }
            print ("    $key=$value\n");
	    $flag++;
        }
    }
}

print ("CVSROOT=$CVSROOT\n");
