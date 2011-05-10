pushd %MB_HOME%

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

if not defined MARCCONV_RELEASE set MARCCONV_RELEASE=Release-007-000
if not defined MARCLIB_RELEASE  set MARCLIB_RELEASE=Release-007-000
if not defined MARCUTIL_RELEASE set MARCUTIL_RELEASE=Release-007-000

cvs export -r %MARCLIB_RELEASE% -d scripts marclib/build/build-marc.xml
cvs export -r %MARCLIB_RELEASE% -d scripts marclib/build/diffwrap.bat
cvs export -r %MARCLIB_RELEASE% -d scripts marclib/build/diffwrap.sh
cvs export -r %MARCLIB_RELEASE% -d scripts marclib/build/marc.ant.props
cvs export -r %MARCUTIL_RELEASE% -d perl marcutil/perl/MARC.pm
cvs export -r %MARCUTIL_RELEASE% -d perl marcutil/perl/marcformat.pl
cvs export -r %MARCUTIL_RELEASE% -d perl marcutil/perl/prtrej.pl
cvs export -r %MARCUTIL_RELEASE% -d perl marcutil/perl/qadiffbench.pl
cvs export -r %MARCUTIL_RELEASE% -d perl marcutil/perl/qarunbench.pl

echo set up done >setupdone.txt

popd
