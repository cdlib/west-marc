#! /bin/sh
#
# Build setup for Java Marc Converters
#-------------------------------------------------------------------------------

mycwd=`pwd`
cd $MB_HOME

rm -rf marc
rm -f scripts/build-marc.xml
rm -f scripts/diffwrap.bat
rm -f scripts/diffwrap.sh
rm -f scripts/marc.ant.props
rm -f perl/MARC.pm
rm -f perl/marcformat.pl
rm -f perl/prtrej.pl
rm -f perl/qadiffbench.pl
rm -f perl/qarunbench.pl

if [ -z "$MARCCONV_RELEASE" ]
then
	export MARCCONV_RELEASE=Release-008-000
fi

if [ -z "$MARCLIB_RELEASE" ]
then
	export MARCLIB_RELEASE=Release-008-000
fi

if [ -z "$MARCUTIL_RELEASE" ]
then
	export MARCUTIL_RELEASE=Release-008-000
fi

cvs export -r $MARCLIB_RELEASE -d scripts marclib/build/build-marc.xml || exit
cvs export -r $MARCLIB_RELEASE -d scripts marclib/build/diffwrap.bat   || exit
cvs export -r $MARCLIB_RELEASE -d scripts marclib/build/diffwrap.sh    || exit
cvs export -r $MARCLIB_RELEASE -d scripts marclib/build/marc.ant.props || exit
cvs export -r $MARCUTIL_RELEASE -d perl marcutil/perl/MARC.pm          || exit
cvs export -r $MARCUTIL_RELEASE -d perl marcutil/perl/marcformat.pl    || exit
cvs export -r $MARCUTIL_RELEASE -d perl marcutil/perl/prtrej.pl        || exit
cvs export -r $MARCUTIL_RELEASE -d perl marcutil/perl/qadiffbench.pl   || exit
cvs export -r $MARCUTIL_RELEASE -d perl marcutil/perl/qarunbench.pl    || exit

echo set up done >setupdone.txt

cd $mycwd
