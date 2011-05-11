#!/usr/bin/perl

if(($ARGV[0] =~ /^-[hH]/) || (@ARGV > 1)) { die "
Usage ps_fancy <optional user name> ";
}

if(@ARGV==1) { $user = $ARGV[0]; }
else { $user = `whoami`; chop $user; }

@a=`ps -f -u $user`;
shift @a;

for $line (@a) {
  ($u, $p, $pp) = split(' ',$line);
  push(@plist,$p);
  $child{$pp} .= " $p";
  $parent{$p}=$pp;
  $pdata{$p}=$line;
}

for $i (0..$#a) {
  $p=$plist[$i];
  if($parent{$p} == 1 || $parent{$parent{$p}} eq '') { &print_desc($p,-1) }
}

sub print_desc {
  my($p,$ind)=@_;
  my($x,@kids);
  $ind++;
  print " "x($ind*4),$pdata{$p};
  @kids=split(' ',$child{$p});
  for $x (@kids) { &print_desc($x,$ind) } 
}
