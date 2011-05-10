package org.cdlib.marcconvert.run;

import java.io.FileInputStream;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Hashtable;
import java.util.Properties;

import org.apache.log4j.Logger;

import org.cdlib.marcconvert.ConvertFile;
import org.cdlib.util.marc.MarcException;
import org.cdlib.util.marc.MarcParmException;
import org.cdlib.util.marc.MarcRecord;
import org.cdlib.util.marc.MarcStream;
import org.cdlib.util.string.F;
import org.cdlib.util.string.StringUtil;

/**
 * NOTE: This class is unused and is a likely cadidate for removal.
 * Equivalent functionality may be obtained by using the mutil.pl
 * wrapper for the the Perl MARC.pm module.
 *
 * This class is used to display Marc Records to a report file
 *
 * configuration file given as java -Dconfig=<name>
 *
 * configuration file is a set of name value pairs
 *     filedate=<date of this file>
 *     inname=<name of marc input file>
 *     report=<name of output report file>
 *    skip=<number to skip before input OR "none">
 *    output=<number of records to convert OR "all">
 *    hex=yes - dump the records in hex
 *
 *
 * @author <a href="mailto:david.loy@ucop.edu">David Loy</a>
 * @version $Id: MarcDisplay.java,v 1.3 2002/10/22 22:37:12 smcgovrn Exp $
 */
public class MarcDisplay
{
	/**
	 * log4j Logger for this class.
	 */
    private static Logger log = Logger.getLogger(MarcDisplay.class);

	/**
	 * CVS header string.
	 */
    public static final String cvsHeader = "$Header: /cvs/root/melvyl/ei/marcconvert/java/org/cdlib/marcconvert/run/MarcDisplay.java,v 1.3 2002/10/22 22:37:12 smcgovrn Exp $";

	/**
	 * CVS version string.
	 */
    public static final String version = "$Revision: 1.3 $";

	/**
	 * Private indicator used to assure CVS information is logged
	 * only once.
	 */
    private static boolean versionLogged = false;

    /*
     * Static initializer used to log cvs header info.
     */
    {
        if ( !versionLogged )
        {
            log.info(cvsHeader);
            versionLogged = true;
        }
    }

    //====================================================
    //       VARIABLES
    //====================================================
    private int statarr[] = new int[100];
    private Hashtable stats = new Hashtable();
    private int inputCnt = 0;
    private int outputCnt = 0;
    private int skip = 0;
    private int numberOut = 0;
    private int verbose = 0;
    private String inName = null;
    private String logName = null;
    private String reportName = null;
    private ConvertFile logfile = null;
    private ConvertFile report = null;
    private Properties config = new Properties();
    private String startTime = new String();
    private String endTime = new String();
    private boolean hexflag = false;

    public static String EOL = System.getProperty("line.separator");


    //====================================================
    //       CONSTRUCTORS
    //====================================================
    public MarcDisplay() {}

    //====================================================
    //       PUBLIC METHODS
    //====================================================
    public static void main(String args[])
    {
        try {

            MarcDisplay run = new MarcDisplay();
            //RunConvert.process handles all I/O and the calling
            //of the MarcConvert routines to perform the conversion
            run.display();
        }

            catch (Exception except) {
            System.out.println(except);
            except.printStackTrace();
        }
    }

    /**
          * display - records
     */
    public void display()
    {

        MarcRecord marcRec = null;
        MarcStream marcReader = null;

        // the following try/catch is used for trapping
        // initial file handling exceptions
        try {
            startTime = getDateTime();
            processConfiguration();
            report = new ConvertFile("report", reportName);
            if (logName != null) {
                logfile = new ConvertFile("log", logName);
            }

            //dumpRecord(mbr, fout, "Original");
            marcReader = new MarcStream(inName);
        }

            catch (MarcParmException except) {
            System.out.println("MarcParmException:" + except.getDetail());
            except.printStackTrace();
            return;
        }

            catch (Exception except) {
            System.out.println(except);
            except.printStackTrace();
            return;
        }

        marcReader.start();
        startReport();
        for (int i=skip; true; i++) {
            try {
                //System.out.println("start process:" + i);
                if (outputCnt >= numberOut) break;
                marcRec = marcReader.getRecord(i);
                if (marcRec == null) break;
                inputCnt++;
                dumpMarc(marcRec, "Record:" +  i);
            }

            catch (MarcException except) {
                System.out.println(except);
                except.printStackTrace();
                report.write( "getRecord reverse: " + i + " ***** Exception:" + except.getDetail() + EOL);
            }

            catch (Exception except) {
                System.out.println(except);
                except.printStackTrace();
            }
        }
        endTime = getDateTime();
        endReport();
        close();

    }
    //====================================================
    //       PRIVATE METHODS
    //====================================================



    /**
     * close - close open files to flush buffers
     */
    private void close()
    {
        try {
            if (report != null) report.close();
            report = null;
        }

        catch (Exception except) {
            System.out.println(except);
            except.printStackTrace();
        }
        //System.out.println("CLOSE called");
    }


    /**
     * doReport - generate report for this run
     */
    private void startReport()
    {
        endTime = getDateTime();
        report.write(EOL + EOL + "MARC DISPLAY REPORT" + EOL + EOL);
        report.write("Start:" + startTime + EOL);
        report.write("End:  " + endTime + EOL);
        report.write("Input file:" + inName + EOL);
        report.write("------------------------------------------" + EOL);
        reportConfiguration();
        report.write("------------------------------------------" + EOL);
    }

    /**
     * doReport - generate report for this run
     */
    private void endReport()
    {
        report.write("------------------------------------------" + EOL);
        reportResults();
    }

    /**
     * dumpMarc - dump Marc record based on debug level
     * @param inDebug - debug level
     * @param marcRecord - record to dump
     * @header - header for this dumped record
     */
    private void dumpMarc(MarcRecord marcRec, String header)
    {
        if (marcRec == null) {
            report.write( header + "--MarcRecord null" + EOL);
        }
        else {
            String dumpStr = null;
            if (hexflag) dumpStr = marcRec.formatHexDump(header);
            else dumpStr = marcRec.formatDump(header);
            report.write( dumpStr);
        }
        outputCnt++;
    }

    /**
     * get string containing date/time for this call
     * @return formatted date time string
     */
    private String getDateTime()
    {
        StringBuffer buff = new StringBuffer("");
        Calendar calendar = new GregorianCalendar();

        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        int second = calendar.get(Calendar.SECOND);
        buff.append(
            year
            + "-" + month
            + "-" + day
            + " " + hour
            + ":" + minute
            + ":" + second);
        return buff.toString();
    }

    /**
     * processConfiguration - get configuration properties from
     * configuration file given as java -Dconfig=<name>
     *
     * configuration file is a set of name value pairs
     *     filedate=<date of this file>
      *     inname=<name of marc input file>
     *     report=<name of output report file>
     *    skip=<number to skip before input OR "none">
     *    output=<number of records to convert OR "all">
     *    hex=yes - dump the records in hex
     */
    private void processConfiguration()
    {
        String test = null;
        Properties props;
        props = System.getProperties();
        String strprop = props.getProperty("config", "none");
        System.out.println("Configuration file:" + strprop);
        if (strprop.equals("none"))
            throw new MarcParmException(this, "-Dconfig=<name> required on java command");

        try {

            FileInputStream inFile = new FileInputStream(strprop);
            config.load(inFile);
        }
        catch (Exception exception) {
            throw new MarcParmException(this, "configuration: error: " + exception);
        }

        inName = config.getProperty("inname", "");
        if (inName.length() == 0) {
            throw new MarcParmException(this, "inname - input file name not supplied");
        }

        test = config.getProperty("hex", "no");
        if (test.equals("yes")) hexflag = true;

        reportName = config.getProperty("report", "");
        if (reportName.length() == 0) {
            throw new MarcParmException(this, "report - report file name not supplied");
        }

        test = config.getProperty("logname", "none");
        if (test.equals("none")) logName = null;
        else logName = test;

        test = config.getProperty("skip", "none");
        if (test.equals("none")) skip = 0;
        else skip = StringUtil.parseInt(test);

        test = config.getProperty("output", "all");
        if (test.equals("all")) numberOut = 1000000;
        else numberOut = StringUtil.parseInt(test);

        test = config.getProperty("debug", "none");
        if (test.equals("none")) verbose = 0;
        else verbose = StringUtil.parseInt(test);

        System.out.println( //!!!
            " inname:" + inName + EOL
            + " report:" + reportName + EOL
            + " skip:" + skip + EOL
            + " numberOut:" + numberOut + EOL
        );

       }


    /**
     * reportConfiguration - report configuration settings
     */
    private void reportConfiguration()
    {
        report.write(EOL);
        report.write( "Configuration:" + EOL
            + " inname:" + inName + EOL
            + " report:" + reportName + EOL
            + " skip:" + skip + EOL
            + " output:" + numberOut + EOL
            );
        report.write(EOL);
    }

    /**
     * reportResults - report the statistics and status of run
     */
    private void reportResults()
    {
        report.write(EOL);
        report.write( "Run Statistics:" + EOL);
        reportEntry("Records Displayed", outputCnt);
        report.write(EOL + EOL);
    }
    /**
     * reportEntry - format key value pair
     * @param name - name of pair
     * @param value - number of occurrences
     */
    private void reportEntry(String name, int value)
    {
            report.write(
                "    "
                + F.f(value,6, F.RJ)
                + " - "
                + F.f(name,30,F.LJ) + EOL);
    }

}
