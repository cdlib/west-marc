/**
 * 
 */


/**
  * @author pdoshi
 *
 */
package org.cdlib.marcconvert;


import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Properties;
import java.util.TreeMap;
import java.util.Vector;
import java.util.Arrays;
import java.text.DateFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import org.cdlib.util.marc.Field;
import org.cdlib.util.marc.FieldList;
import org.cdlib.util.marc.MarcFieldList;
import org.cdlib.util.marc.MarcException;
import org.cdlib.util.marc.LocationTable;
import org.cdlib.util.marc.MarcConstants;
import org.cdlib.util.marc.MarcEndOfFileException;
import org.cdlib.util.marc.MarcFormatException;
import org.cdlib.util.marc.MarcParmException;
import org.cdlib.util.marc.MarcRecord;
import org.cdlib.util.marc.MarcStream;
import org.cdlib.util.marc.MarcFixedLengthField;
import org.cdlib.util.string.F;
import org.cdlib.util.string.StringUtil;


/**
 * This class runs the Marc conversion using a subclass
 * of MarcConvert
 *
 * References:
 * https://diva.cdlib.org/projects/melvyl/ei/ConverterSpecifications/En.STF.Jav.doc
 * https://diva.cdlib.org/projects/melvyl/ei/ConverterSpecifications/sort_separate_records.doc
 *
 * @author <a href="mailto:david.loy@ucop.edu">David Loy</a>
 * @author <a href="mailto:shawnm@splorkin.com">Shawn McGovern</a>
 * @version $Id: ENLAMerge.java,v 1.3 2007/06/22 18:54:14 aleph Exp $
 */

/* 
 * Change history:
 *   06/03/2011 dbb - Added comments re: processing specific to STF
 */

public class STFMerge
    implements ConvertConstants, MarcConstants
{
	     static final int CONVERT_EOF = -2;
        static final int CONVERT_INITIALIZE_OK = -3;
        static final int CONVERT_SORTOUT_SUCCESS = -4;
        static final int CONVERT_REC_SORTOUT = -5;

	/**
	 * log4j Logger for this class.
	 */
    private static Logger log = Logger.getLogger(STFMerge.class);

	/**
	 * CVS header string.
	 */
    public static final String cvsHeader = "$Header: /cvs/root/melvyl/ei/marcconvert/java/org/cdlib/marcconvert/ENLAMerge.java,v 1.3 2007/06/22 18:54:14 aleph Exp $";

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

    public  static String EOL = System.getProperty("line.separator");

    private static TreeMap runStats    = new TreeMap();
    private static TreeMap dirStats    = new TreeMap();
    private static TreeMap errorStats  = new TreeMap();
    private static TreeMap rejectStats = new TreeMap();

    //====================================================
    //       VARIABLES
    //====================================================

    // Class variables set in the config file
    private String  inFileName      = null;
    private String  outFileName      = null;
    private String  outDirName     = null;
    private String  errorFileName   = null;
    private String  rejectFileName  = null;
    private String  reportFileName  = null;
    private String  loctabFileName  = null;
    private String  fileDate        = null;
    private boolean forceDate       = false;
    private int     skip            = 0;
    private int     maxIn           = 0;
    private int     maxOut          = 0;
    private int     debugLevel      = 0;
    private boolean validateOut     = true;
    private boolean benchRun        = true;
    private String  benchRunDateStr = null;

    private OutputStreamWriter marcOut   = null;
    private OutputStreamWriter errors    = null;
    private OutputStreamWriter rejects   = null;
    private ConvertFile        report    = null;
    private LocationTable      loctab    = null;
    //private MarcRecord marcRecOut = null;
    private String statusMsg = null;

    private Properties config      = new Properties();
    private String     startTime   = new String();
    private String     endTime     = new String();
    private int        inputCnt    = 0;
    private int        outputCnt   = 0;
    private int        sortCnt      = 0;
    private int        mergeCnt    = 0;
    private int        errorCnt    = 0;
    private int        rejectCnt   = 0;
    private int        skipCnt     = 0;
    private int        okCnt       = 0;
    //private int        runStatus   = -1;


    //====================================================
    //       CONSTRUCTORS
    //====================================================
    /**
     * ENLAMerge - contructor - set subclass for marc conversion
     * @param inMarcConvert - Marc conversion subclass of MarcConvert
     */
    public STFMerge()
        throws MarcParmException
    {
    }

    

    //====================================================
    //       PUBLIC METHODS
    //====================================================


    /**
     * Handle the conversion process. This method replaces the obsolete method process().
     */
    public int initialize()
    {
        int runStatus = CONVERT_INITIALIZE_OK;

        // the following try/catch is used for trapping
        // initial file handling exceptions
        try
        {
            startTime = getPrintDateTime();
            config = processConfiguration();
            report = new ConvertFile("report", reportFileName);

            // Buffer the converted output in a 32k buffer
            marcOut =
                new OutputStreamWriter
                (new BufferedOutputStream
                 (new FileOutputStream(outFileName), 32 * 1024), "iso-8859-1");

            // Buffer the records converted with errors in a 32k buffer
            errors =
                new OutputStreamWriter
                (new BufferedOutputStream
                 (new FileOutputStream(errorFileName), 32 * 1024), "iso-8859-1");

            // Buffer the rejected records in a 32k buffer
            rejects =
                new OutputStreamWriter
                (new BufferedOutputStream
                 (new FileOutputStream(rejectFileName), 32 * 1024), "iso-8859-1");
            return CONVERT_INITIALIZE_OK;

        }
        catch (MarcParmException e)
        {
            log.error("MarcParmException: " + e.getMessage(), e);
            runStatus = Math.max(runStatus, CONVERT_JOB_FAILURE);
            return runStatus;
        }
        catch (Exception e)
        {
            log.error("Exception: " + e.getMessage(), e);
            runStatus = Math.max(runStatus, CONVERT_JOB_FAILURE);
            return runStatus;
        }
    }

/* 
 * 6/3/2011 dbb
 * Program is invoked by org.cdlib.marcconvert.run.RunENLAMerge.java
 *   ENLAMerge ucla = new ENLAMerge();
 *   int rc = ucla.doCombine();
 * Configuration from a properties file specified on command line
 */
    public int doCombine()
    {
        int runStat = CONVERT_JOB_SUCCESS;
        runStat = initialize();

        if (runStat != CONVERT_INITIALIZE_OK) {
            return CONVERT_JOB_FAILURE;
        }
        runStat = merge();
        if ( log.isDebugEnabled() ) {
            log.debug("**** after merge: " + runStat);
        }

        if (runStat == CONVERT_SORTOUT_SUCCESS) {
            runStat = processFiles();
            if ( log.isDebugEnabled() ) {
                log.debug("**** after processFiles: " + runStat);
            }
        }
        endTime = getPrintDateTime();
        doReport();
        close();
        deleteDir(outDirName);
        return runStat;
    }

    public MarcStream initMarcReader(String inFileName)
    {

        // the following try/catch is used for trapping
        // initial file handling exceptions
        try
        {
            return new MarcStream(inFileName);
        }
        catch (MarcParmException e)
        {
            log.error("MarcParmException: " + e.getMessage(), e);
            return null;
        }
        catch (Exception e)
        {
            log.error("Exception: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Handle the conversion process. 
     */
    public int merge()
    {
        int runStatus = CONVERT_SORTOUT_SUCCESS;
        int convertStatus = CONVERT_REC_SORTOUT;
        MarcRecord marcRecIn  = null;
        MarcStream marcReader = initMarcReader(inFileName);
        if (marcReader == null) return CONVERT_JOB_SUCCESS;
        Status status = null;
        debugOut(2, "DEBUG" + EOL);
        marcReader.start();
        File outDirFile = new File(outDirName);
        deleteDir(outDirFile);
        outDirFile.mkdir();

        if ( log.isDebugEnabled() )
        {
            log.debug("skip = " + skip);
            log.debug("maxIn = " + maxIn);
            log.debug("maxOut = " + maxOut);
        }

        int readStatus = -1;

        int max = skip + maxIn;

        for ( int i = skip; i < maxIn; i++ ) {
            if ( outputCnt >= maxOut ) break;
            status = processMergeRecord(i, marcReader);

            if (status.recordStatus == CONVERT_EOF) break;
            if (status.recordStatus == CONVERT_REC_FAILURE) {
                runStatus = CONVERT_JOB_FAILURE;
                break;
            }
            convertStatus = addStat(status);
            if (convertStatus == CONVERT_REC_FAILURE) {
                runStatus = CONVERT_JOB_FAILURE;
                break;
            }
        }

        return runStatus;
    }

    public int addStat(Status status)
    {
        String marcStr = status.marcStr;
        MarcRecord marc = status.marc;
        String statusMsg = status.msg;
      
        if ( log.isDebugEnabled() ) {
            log.debug("addStat:" + status.recordStatus);
        }

        int runStatus = status.recordStatus;
        try {
            // check the conversion status after validation
            switch ( status.recordStatus ) {
                case CONVERT_REC_REJECT:
                    writeReject(rejects, marcStr);
                    rejectCnt = addRunStat(MarcConvert.REJECTED);
                    addRejectStat(statusMsg);
                    if (status.id != null) {
                        log.error("Record Rejected: (id=" + status.id + ") R-" + rejectCnt + " "
                            + statusMsg);
                    } else {
                        log.error("Record Rejected: I-" + inputCnt + " R-" + rejectCnt + " "
                            + statusMsg);
                    }
                    break;

                case CONVERT_REC_SUCCESS:
                    writeMarc(marcOut, marcStr);
                    okCnt = addRunStat(MarcConvert.OK);
                    break;

                case CONVERT_REC_SORTOUT:
                    sortCnt = addRunStat("Sort");
                    break;

                case CONVERT_REC_ERROR:
                    writeError(errors, marcStr);
                    errorCnt = addRunStat(MarcConvert.ERROR);
                    break;

                default:
                    log.error("Process halted: Invalid validation return code = "
		    + status.recordStatus);
                    runStatus = Math.max(status.recordStatus, CONVERT_REC_FAILURE);

            }


        } catch (Exception e) {
            log.error("Exception: for record (I-" + inputCnt + "): " + e.getMessage(), e);
            runStatus = CONVERT_REC_FAILURE;
        }
        return runStatus;
    }

/* 
 * 6/3/2011 dbb
 * Processing MAY be specific to STF.
 */

    private Status processMergeRecord(int inx, MarcStream marcReader)
    {
        statusMsg = null;
        Status status = new Status();
        status.recordStatus = CONVERT_REC_SUCCESS;

        try {
            MarcRecord  marcRecIn = new MarcRecord();

            inputCnt++; // increment now so it is correct for exception handling
            status.recordStatus = marcReader.getRecord(inx, marcRecIn);

            if (marcRecIn == null) {
                log.error("Record # " + inputCnt + " input routine returned null MarcRecord");
                status.recordStatus = CONVERT_REC_FAILURE;
                return status;
            }

            dumpMarc(2, marcRecIn, "Input:" + inx);
            status.recordStatus = marcToDir(marcRecIn);
            status.marcStr = marcRecIn.marcDump();
            status.marc = marcRecIn;

            if ( log.isDebugEnabled() ) {
                    log.debug("rec # " + inputCnt + " convert status = " + status.recordStatus);
            }

            // check the conversion status after validation
            return status;

        } catch (MarcEndOfFileException e) {
            inputCnt--; // adjust for adding prior to read, this is easier than
                            // accounting for all the extra adds required to get the
                            // count right for all the other exceptions.
            log.debug("Trapped MarcEndOfFileException - rec count = " + inputCnt);
            status.recordStatus = CONVERT_EOF;

        } catch (MarcFormatException e) {
            try {
                writeReject(rejects, marcReader.getReadBuffer());
                rejectCnt = addRunStat(MarcConvert.REJECTED);
                addRejectStat(e.getMessage());
                log.error("Record Rejected: I-" + inputCnt + " R-" + rejectCnt + " "
                              + e.getMessage());

                if ( log.isDebugEnabled() ) {
                    log.error("MarcFormatException: printing stack trace", e);
                }

            } catch (IOException ioe) {
                log.error("Exception: " + ioe.getMessage(), ioe);
                status.recordStatus = CONVERT_REC_FAILURE;
            }

        } catch (MarcDropException e) {
            try {
                writeReject(rejects, marcReader.getReadBuffer());
                rejectCnt = addRunStat(MarcConvert.REJECTED);
                addRejectStat(e.getMessage());
                log.error("Record Rejected: I-" + inputCnt + " R-" + rejectCnt + " "
                              + e.getMessage());

            } catch (IOException ioe) {
                log.error("Exception: " + ioe.getMessage(), ioe);
                status.recordStatus = CONVERT_REC_FAILURE;
            }

        } catch (MarcException e) {
            log.error("MarcException for record (I-" + inputCnt + "): " + e.getMessage(), e);
            status.recordStatus = CONVERT_REC_FAILURE;

        } catch (Exception e) {
            log.error("Exception: for record (I-" + inputCnt + "): " + e.getMessage(), e);
            status.recordStatus = CONVERT_REC_FAILURE;
        }

        return status;
    }


    /**
     * Increment counter in run statistics table
     * @param key - name of counter element
     */
    public static int addRunStat(String key)
    {
        int ival = 0;
        if ( key != null && key.length() > 0 )
        {
            String mykey = key.toLowerCase();
            Integer i = (Integer)runStats.get(mykey);

            if ( i != null )
            {
                ival = i.intValue();
            }
            ival++;
            runStats.put(mykey, new Integer(ival));
        }

        return ival;
    }


    /**
     * Increment counter in directory statistics table
     * @param key - error code to accumulate
     */
    public static int addDirStat(String key)
    {
        int ival = 0;
        if ( key != null && key.length() > 0 )
        {
            Integer i = (Integer)errorStats.get(key);

            if ( i != null )
            {
                ival = i.intValue();
            }
            ival++;
            dirStats.put(key, new Integer(ival));
        }

        return ival;
    }

    /**
     * Increment counter in error statistics table
     * @param key - error code to accumulate
     */
    public static int addErrorStat(String key)
    {
        int ival = 0;
        if ( key != null && key.length() > 0 )
        {
            Integer i = (Integer)errorStats.get(key);

            if ( i != null )
            {
                ival = i.intValue();
            }
            ival++;
            errorStats.put(key, new Integer(ival));
        }

        return ival;
    }


    /**
     * Increment counter in reject statistics table
     * @param key -  reject code to accumulate
     */
    public static int addRejectStat(String key)
    {
        int ival = 0;
        if ( key != null && key.length() > 0 )
        {
            Integer i = (Integer)rejectStats.get(key);

            if ( i != null )
            {
                ival = i.intValue();
            }
            ival++;
            rejectStats.put(key, new Integer(ival));
        }

        return ival;
    }

    //====================================================
    //       PRIVATE METHODS
    //====================================================

    /**
     * close - close open files to flush buffers
     */
    private void close()
    {
        try
        {
            if (marcOut != null) marcOut.close();
        }
        catch (Exception e)
        {
            log.error("Exception: " + e.getMessage(), e);
        }

        try
        {
            if (report != null) report.close();
        }
        catch (Exception e)
        {
            log.error("Exception: " + e.getMessage(), e);
        }

        try
        {
            if (errors != null) errors.close();
        }
        catch (Exception e)
        {
            log.error("Exception: " + e.getMessage(), e);
        }

        try
        {
            if (rejects != null) rejects.close();
        }
        catch (Exception e)
        {
            log.error("Exception: " + e.getMessage(), e);
        }
    }

    /**
     * debugOut - based on debug level output line to report
     * @param inDebug - level for this output
     * @param str - string to output
     */
    private void debugOut(int inDebug, String str)
    {
        if (inDebug <= debugLevel )
        {
            report.write(str);
        }
    }

    /**
     * doReport - generate report for this run
     */
    private void doReport()
    {
        report.write(EOL + EOL + "ENLA SORT REPORT" + EOL + EOL);
        report.write("Start: " + startTime + EOL);
        report.write("End:   " + endTime + EOL);
        report.write(EOL + "------------------------------------------" + EOL);
        reportResults();
        report.write(EOL + "------------------------------------------" + EOL);
        reportConfiguration();
        report.write(EOL + "------------------------------------------" + EOL);
    }

    /**
     * Dump Marc record based on debug level.
     *
     * @param inDebug    debug level
     * @param marcRecord record to dump
     * @param header     header for this dumped record
     */
    private void dumpMarc(int inDebug, MarcRecord marcRec, String header)
    {
        if ( inDebug <= debugLevel )
        {
            if ( marcRec == null )
            {
                report.write(header + "--MarcRecord null" + EOL);
            }
            else
            {
                String dumpStr = marcRec.formatDump(header);
                report.write(dumpStr);
            }
        }
    }

    /**
     * Get current date and time string formatted for printing.
     *
     * @return formatted date time string
     */
    private String getPrintDateTime()
    {
        StringBuffer buff = new StringBuffer(30);
        Calendar calendar = new GregorianCalendar();

        int year   = calendar.get(Calendar.YEAR);
        int month  = calendar.get(Calendar.MONTH) + 1;
        int day    = calendar.get(Calendar.DAY_OF_MONTH);
        int hour   = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        int second = calendar.get(Calendar.SECOND);

        buff.append(year).append("-");
        buff.append(month).append("-");
        buff.append(day).append(" ");
		if ( hour < 10 )
		{
			buff.append("0");
		}
        buff.append(hour).append(":");

		if ( minute < 10 )
		{
			buff.append("0");
		}
        buff.append(minute).append(":");

		if ( second < 10 )
		{
			buff.append("0");
		}
        buff.append(second);

        return buff.toString();
    }

    /**
     * Get current date and time string compliant with ANSI X3.30 (data)
     * and ANSI X3.43 (time). This is the format required for Marc tag 005.
     *
     * @return formatted date time string
     */
    private String getAnsiDateTime()
    {
        SimpleDateFormat ansiFormatter = new SimpleDateFormat("yyyyMMddHHmmss'.0'");
        String datetime = ansiFormatter.format(new Date());

        return datetime;
    }


    private boolean isValidAnsiDateTime(String datetime)
    {
        boolean bRet = true;
        if ( datetime == null || datetime.length() != 16 )
        {
            log.error("Invalid ANSI date: '" + datetime + "' - length must be 16");
            bRet = false;
        }
        else if ( ! datetime.startsWith("19") && ! datetime.startsWith("20") )
        {
            log.error("Invalid ANSI date: Century must be 19 or 20");
            bRet = false;
        }
        else
        {
            SimpleDateFormat ansiFormatter = (SimpleDateFormat)DateFormat.getDateTimeInstance();
            ansiFormatter.applyPattern("yyyyMMddHHmmss'.0'");
            ansiFormatter.setLenient(false);
            ParsePosition pp = new ParsePosition(0);

            try
            {
                Date pdate = ansiFormatter.parse(datetime, pp);
                if ( pdate == null || pp.getIndex() < 16 || pp.getErrorIndex() > -1 )
                {
                    log.error("Invalid ANSI date: '" + datetime
                              + "' - error in position " + pp.getErrorIndex());
                    bRet = false;
                }
            }
            catch (RuntimeException re)
            {
                log.error("RuntimeException: " + re.getMessage(), re);
                bRet = false;
            }
            catch (Exception e)
            {
                log.error("Exception: " + e.getMessage(), e);
                bRet = false;
            }
        }

        return bRet;
    }


    /**
     * Get configuration properties from configuration file given on the command
     * line as java -Dconfig=<name>
     *
     * Configuration file is a set of name value pairs.
     *
     * Required entries:
     *
     *   infile      - The marc data file to convert
     *   outfile     - The converted marc data file
     *   errorfile   - A file to hold those records converted with errors
     *   rejectfile  - A file to hold reject records
     *   reportfile  - The report file for this run
     *
     *   filedate    - The 005 to use for this run. The format is CCYYMMDDhhmmss.0
     *                 and the century must be either 19 or 20.
     *
     * Optional entries:
     *
     *   forcedate   - If 'Y' the filedate will be used to create all 005 fields,
     *                 overriding the 005 in any input records. If 'N' the filedate
     *                 will only be used to create 005 fields for those input records
     *                 that do not contain a 005. Default is 'N'.
     *
     *   skip        - The number of input records to skip before conversion starts.
     *                 Default's to 0, if the entry is not specified or is not numeric.
     *                 Keyword values 'none' and 'all' may be specified.
     *
     *   maxin       - The maximum number of records to read from the file. This includes
     *                 the number of records to skip, so if skip is greater than or equal
     *                 maxin no records will be processed.
     *                 Default's to 'all', if the entry is not specified or is not numeric.
     *                 Keyword values 'none' and 'all' may be specified.
     *
     *   maxout      - The maximum number of records to convert. Rejected records are not
     *                 included in this count.
     *                 Default's to 'all', if the entry is not specified or is not numeric.
     *                 Keyword values 'none' and 'all' may be specified.
     *
     *   debuglevel  - Controls the level of debug information written to the report file.
     *                 A higher number will get more debug information. The actual meaning
     *                 of this number is varies among conversion programs. The highest
     *                 meaningful value is 3, which will ascii print each input and output
     *                 marc records. To suppress debugging output specify 0.
     *                 Default's to 0, if the entry is not specified or is not numeric.
     *                 Keyword value 'none' may be specified.
     *
     *   validateout - Perform post-conversion validation when this is 'Y', and byapss
     *                 validation of output records when it is 'N'. Default is 'Y'.
     *
     */
    private Properties processConfiguration()
    {
        Properties pin = new Properties();
        Properties pout = new Properties();

        String test = null;
        System.setProperty("config", "C:/PAPR/config/STFconfig.txt" );
        String configfile = System.getProperties().getProperty("config");
        log.info("Config file: " + configfile);

        /*
         * Get the config file
         */
        if ( configfile == null || configfile.length() == 0 )
        {
            throw new MarcParmException(this, "-Dconfig=<name> required on java command");
        }

        try
        {
            pin.load(new FileInputStream(configfile));
        }
        catch (Exception exception)
        {
            throw new MarcParmException(this, "configuration error: " + exception);
        }
        pout.setProperty("configfile", configfile);

        /*
         * Get input file name
         */
        inFileName = pin.getProperty("infile", "");
        if ( inFileName.length() == 0 )
        {
            throw new MarcParmException(this, "infile - input file name not supplied");
        }
        pout.setProperty("infile", inFileName);

        /*
         * Get output file name
         */
        outFileName = pin.getProperty("outfile", "");
        if ( outFileName.length() == 0 )
        {
            throw new MarcParmException(this, "outfile - output file name not supplied");
        }
        pout.setProperty("outfile", outFileName);

        /*
         * Get output directory name
         */
        outDirName = pin.getProperty("outdir", "");
        if ( outDirName.length() == 0 )
        {
            throw new MarcParmException(this, "outdir - output file name not supplied");
        }
        pout.setProperty("outdir", outDirName);

        /*
         * Get report file name
         */
        reportFileName = pin.getProperty("reportfile", "");
        if ( reportFileName.length() == 0 )
        {
            throw new MarcParmException(this, "reportfile - report file name not supplied");
        }
        pout.setProperty("reportfile", reportFileName);

        /*
         * Get error file name
         */
        errorFileName = pin.getProperty("errorfile", "");
        if ( errorFileName.length() == 0 )
        {
            throw new MarcParmException(this, "errorfile - error file name not supplied");
        }
        pout.setProperty("errorfile", errorFileName);

        /*
         * Get reject file name
         */
        rejectFileName = pin.getProperty("rejectfile", "");
        if ( rejectFileName.length() == 0 )
        {
            throw new MarcParmException(this, "rejectfile - reject file name not supplied");
        }
        pout.setProperty("rejectfile", rejectFileName);

        /*
         * Get location table file name
         */
        loctabFileName = pin.getProperty("loctabfile", "");
        if ( loctabFileName.length() == 0 )
        {
            log.warn("loctabfile - location table file name not supplied");
        }
        pout.setProperty("loctabfile", loctabFileName);

        /*
         * Get filedate
         */
        if ( (fileDate = pin.getProperty("filedate")) == null )
        {
            throw new MarcParmException(this, "filedate - filedate not supplied");
        }

        if ( ! isValidAnsiDateTime(fileDate) )
        {
            throw new MarcParmException(this, "filedate: '" + fileDate
                                        + "' - format invalid (yyyyMMddHHmmss'.0')");
        }

        pout.setProperty("filedate", fileDate);

        /*
         * Get forcedate
         */
        forceDate = pin.getProperty("forcedate", "N").equalsIgnoreCase("Y");
        pout.setProperty("forcedate", (forceDate ? "Y" : "N"));

        /*
         * Set rundate
         */
        pout.setProperty("rundate", getAnsiDateTime());

        /*
         * Get the number of records to skip over
         */
        test = pin.getProperty("skip", "none");
        if (test.equalsIgnoreCase("none"))
        {
            skip = 0;
        }
        else if (test.equalsIgnoreCase("all"))
        {
            skip = Integer.MAX_VALUE;
        }
        else
        {
            try
            {
                skip = StringUtil.parseInt(test);
            }
            catch (NumberFormatException e)
            {
                skip = 0;
            }
        }

        if ( skip < 0 )
        {
            skip = 0;
        }

        pout.setProperty("skip", Integer.toString(skip));

        /*
         * Get maximum number of input records to process.
         */
        test = pin.getProperty("maxin", "all");
        if (test.equalsIgnoreCase("none"))
        {
            maxIn = 0;
        }
        else if (test.equalsIgnoreCase("all"))
        {
            maxIn = Integer.MAX_VALUE;
        }
        else
        {
            try
            {
                maxIn = StringUtil.parseInt(test);
            }
            catch (NumberFormatException e)
            {
                maxIn = 0;
            }
        }

        if ( maxIn < 0 )
        {
            maxIn = 0;
        }

        pout.setProperty("maxin", Integer.toString(maxIn));

        /*
         * Get maximum number of records to create
         */
        test = pin.getProperty("maxout", "all");
        if (test.equalsIgnoreCase("none"))
        {
            maxOut = 0;
        }
        else if (test.equalsIgnoreCase("all"))
        {
            maxOut = Integer.MAX_VALUE;
        }
        else
        {
            try
            {
                maxOut = StringUtil.parseInt(test);
            }
            catch (NumberFormatException e)
            {
                maxOut = 0;
            }
        }

        if ( maxOut < 0 )
        {
            maxOut = 0;
        }

        pout.setProperty("maxout", Integer.toString(maxOut));

        /*
         * Get the debug level. This affects the report output, not the
         * logging level (which is set in the log4j properties file.
         */
        test = pin.getProperty("debuglevel", "none");
        if (test.equalsIgnoreCase("none"))
        {
            debugLevel = 0;
        }
        else
        {
            try
            {
                debugLevel = StringUtil.parseInt(test);
            }
            catch (NumberFormatException e)
            {
                debugLevel = 0;
            }
        }

        if ( debugLevel < 0 )
        {
            debugLevel = 0;
        }

        pout.setProperty("debuglevel", Integer.toString(maxOut));

        /*
         * Get validateOut
         */
        validateOut = !(pin.getProperty("validateout", "Y").equalsIgnoreCase("N"));
        pout.setProperty("validateout", (validateOut ? "Y" : "N"));

        /*
         * Set rundate to use in 998 creation
         */
        pout.setProperty("rundate", getAnsiDateTime());


        /*
         * Get benchRun and benchDate
         */
        benchRun = pin.getProperty("benchrun", "N").equalsIgnoreCase("Y");
        pout.setProperty("benchrun", (benchRun ? "Y" : "N"));
        benchRunDateStr = pin.getProperty("benchdate", "BenchMarkDate");
        pout.setProperty("benchdate", benchRunDateStr);

        if ( log.isDebugEnabled() )
        {
            log.debug
                (
                 " configfile:" + configfile
                 + " infile:" + inFileName
                 + " outfile:" + outDirName
                 + " reportfile:" + reportFileName
                 + " errorfile:" + errorFileName
                 + " rejectfile:" + rejectFileName
                 + " loctabfile:" + loctabFileName
                 + " filedate:" + fileDate
                 + " forcedate:'" + (forceDate ? "Y" : "N") + "'"
                 + " skip:" + skip
                 + " maxin:" + maxIn
                 + " maxout:" + maxOut
                 + " debuglevel:" + debugLevel
                 + " validateout:" + (validateOut ? "Y" : "N")
                 + " benchrun:" + (benchRun ? "Y" : "N")
                 + " benchdate:" + benchRunDateStr
                 );
        }

        return pout;
    }


    /**
     * reportConfiguration - report configuration settings
     */
    private void reportConfiguration()
    {
        report.write("Configuration:" + EOL);
        report.write("  infile:      " + inFileName + EOL);
        report.write("  outfile:     " + outFileName + EOL);
        report.write("  reportfile:  " + reportFileName + EOL);
        report.write("  errorfile:   " + errorFileName + EOL);
        report.write("  rejectfile:  " + rejectFileName + EOL);
        report.write("  loctabfile:  " + loctabFileName + EOL);
        report.write("  outdir:      " + outDirName + EOL);
        report.write("  filedate:    " + fileDate + EOL);
        report.write("  forcedate:   " + (forceDate ? "Y" : "N") + EOL);
        report.write("  skip:        " + skip + EOL);
        report.write("  maxin:       " + maxIn + EOL);
        report.write("  maxout:      " + maxOut + EOL);
        report.write("  debuglevel:  " + debugLevel + EOL);
        report.write("  validateout: " + (validateOut ? "Y" : "N") + EOL);
        report.write("  benchrun:    " + (benchRun ? "Y" : "N") + EOL);
        report.write("  benchdate:   " + benchRunDateStr + EOL);
    }

    /**
     * reportResults - report the statistics and status of run
     */
    private void reportResults()
    {
        report.write("Run Statistics:" + EOL);
        reportRunStat(inputCnt, "Records Input");
        reportRunStat(sortCnt, "Records written to sort");
        reportRunStat(okCnt, "Records Output");
        reportRunStat(skipCnt, "Records Skipped");
        reportRunStat(rejectCnt, "Records Rejected");
        reportRunStat(errorCnt, "Records with non-fatal errors");

		if ( rejectStats.size() > 0 )
		{
			report.write(EOL + EOL);
			report.write("               Reject records" + EOL);

			for (Iterator iter = (rejectStats.keySet()).iterator(); iter.hasNext(); )
			{
				String  key  = (String)iter.next();
				Integer iInt = (Integer)rejectStats.get(key);
				int     ival = iInt.intValue();
				if ( ival > 0 )
				{
					reportRejectStat(ival, key);
				}
			}
		}

		if ( errorStats.size() > 0 )
		{
			report.write(EOL + EOL);
			report.write("               Error records" + EOL);

			for (Iterator iter = errorStats.keySet().iterator(); iter.hasNext(); )
			{
				String  key = (String)iter.next();
				Integer iInt = (Integer)errorStats.get(key);
				int     ival = iInt.intValue();
				if ( ival > 0 )
				{
					reportErrStat(ival, key);
				}
			}
        }
    }

    /**
     * Print a run stats entry
     * @param value - number to print
     * @param name - text to print
     */
    private void reportRunStat(int value, String text)
    {
        report.write("    "
                     + F.f(value,8, F.RJ)
                     + " - "
                     + text
                     + EOL);
    }

    /**
     * Print a reject stats entry
     * @param value - number to print
     * @param name - text to print
     */
    private void reportRejectStat(int value, String rejectMsg)
    {
        report.write("        "
                     + F.f(value, 8, F.RJ)
                     + (value == 1 ? " occurrence:  " : " occurrences: ")
                     + rejectMsg
                     + EOL);
    }

    /**
     * Print an error stats entry
     * @param value - number to print
     * @param errorCode - code of text to print
     */
    private void reportErrStat(int value, String errorCode)
    {
        report.write("        "
                     + F.f(value, 8, F.RJ)
                     + (value == 1 ? " occurrence:  " : " occurrences: ")
                     + F.f(errorCode, 4, F.RJ)
                     + ": "
                     + ConvertMessages.lookupShortMessage(errorCode)
                     + EOL);
    }

    /**
     * Write a marc record to the output file.
     * @param marcStr - String containing the marc record to output
     */
    private boolean writeMarc(Writer marcOut, String marcStr)
        throws IOException
    {
        boolean bRet = false;
        if ( marcStr != null )
        {
            marcOut.write(marcStr);
            outputCnt++;
            bRet = true;
        }
        return bRet;
    }

    /**
     * Write a marc record to the error file.
     * @param errors - the Writer to use for output
     * @param marcStr - String containing the marc record to output
     */
    private boolean writeError(Writer errors, String marcStr)
        throws IOException
    {
        boolean bRet = false;
        if ( marcStr != null )
        {
            errors.write(marcStr);
            errorCnt++;
            bRet = true;
        }
        return bRet;
    }

    /**
     * Write a buffer to the reject file.
     * @param rejects - the Writer to use for output
     * @param buffer - StringBuffer containing the marc record to output
     */
    private boolean writeReject(Writer rejects, StringBuffer buffer)
        throws IOException
    {
        boolean bRet = false;
        if ( buffer != null )
        {
            rejects.write(buffer.toString());
            bRet = true;
        }
        return bRet;
    }

    /**
     * Write a buffer to the reject file.
     * @param rejects - the Writer to use for output
     * @param marcStr - String containing the marc record to output
     */
    private boolean writeReject(Writer rejects, String marcStr)
        throws IOException
    {
        boolean bRet = false;
        if ( marcStr != null )
        {
            rejects.write(marcStr);
            bRet = true;
        }
        return bRet;
    }

/* 
 * 6/3/2011 dbb
 * We are working with a single file that contains both bibliographic records
 * and holdings records, not necessarily in order. 
 *
 * Step 1: Write MARC records to output files based on the bib record number
 * so that all records with the same bib record number are in the same file.
 *  
 * The code that sets id = bib record number is specific to STF:
 *
 * STF bib records:
 *    001 contains the bib record number
 *    004 is not present
 * STF holdings records:
 *    001 contains some other number (if present)
 *    004 contains the bib record number
 *
 * Intermediate output is to a temporary directory (deleted later). 
 * Directory structure is defined by the id (bib record number). For example: 
 *   All bib and holdings records for bib record number: 3806866 
 *   Are in this output file: outDirName/380/686/6/marc.txt
 * The program invokes writeAppendMarc to append matching records to the file
 */

    private int marcToDir(MarcRecord marcIn)
        throws Exception
    {
        try {
            String id = marcIn.getFirstValue("001", null);
         //   String id004 = marcIn.getFirstValue("004", null);
          //  if (id004 != null) id = id004;
            StringBuffer buf = new StringBuffer(100);
            buf.append(outDirName);
            String remain = id;
            while (remain != null) {
                if (remain.length() < 3) {
                    buf.append(File.separator + remain);
                    (new File(buf.toString())).mkdir();
                    remain = null;
                } else {
                    buf.append(File.separator + remain.substring(0,3));
                    (new File(buf.toString())).mkdir();
                    remain = remain.substring(3);
                }
            }
            //buf.append(File.separator + outputCnt + ".txt");
            buf.append(File.separator + "marc.txt");
            writeAppendMarc(buf.toString(), marcIn);
            return CONVERT_REC_SORTOUT;

        } catch (Exception ex) {
            throw ex;
        }
    }

/* 
 * 6/3/2011 dbb
 * To append records (boolean append = true):
 * FileOutputStream fos = new FileOutputStream(outFileName, true)
 */

    /**
     * Write a marc record to the output file.
     * @param marcStr - String containing the marc record to output
     */
    private boolean writeAppendMarc(String outFileName, MarcRecord marcRecOut)
        throws Exception
    {
        try {
            boolean bRet = false;
            String marcStr = marcRecOut.marcDump();
            if ( log.isInfoEnabled() ) {
                    log.info("Add file: " + outFileName);
            }

            if ( marcStr != null ) {
                String dispExists = "";
                if ( (new File(outFileName)).exists() ) dispExists = " exists ";

                FileOutputStream fos = new FileOutputStream(outFileName, true);
                byte [] bMarc = marcStr.getBytes();
                fos.write(bMarc);
                outputCnt++;
                bRet = true;
                fos.close();
            }

            return bRet;

        } catch (Exception ex) {
            System.out.println(ex);
            ex.printStackTrace();
            throw ex;
        }
    }

/* 
 * 6/3/2011 dbb
 * This step invokes processAllFiles to recursively process the temporary 
 * files written by marcToDir.
 */

    public int processFiles()
    {
        try {
            if (outputCnt == 0) return CONVERT_JOB_FAILURE;
            File dir = new File(outDirName);
            processAllFiles(dir);
            return CONVERT_JOB_SUCCESS;
        } catch (Exception ex) {
            return CONVERT_JOB_FAILURE;
        }
    }

/* 
 * 6/3/2011 dbb
 * This step walks the directory structure and calls combineFile to process 
 * each marc.txt file. Each file at this point contains all the MARC bib and 
 * holdings records for a bib record number (in no particular order).
 */

    private void processAllFiles(File dir)
        throws MarcFailException
    {
        if ( log.isDebugEnabled() ) {
            log.debug("processAllFiles:" + dir.getPath());
        }

        if (dir.isDirectory()) {
            String [] children = dir.list();
	    Arrays.sort (children);	
            for (int i=0; i<children.length; i++) {
                processAllFiles(new File(dir, children[i]));
            } 
        } else {
            if (combineFile(dir) == CONVERT_REC_FAILURE) {
                log.error("processAllFiles - Run terminated because of status");
                throw new MarcFailException("Convert failure");
            }
        }
    }

/* 
 * 6/3/2011 dbb
 * This step invokes buildCombinedRecord to build a single MARC record 
 * containing both bib records and holdings.
 */

    private int combineFile(File dir)
    {
        statusMsg = null;
        int convertStatus = CONVERT_REC_SUCCESS;

        try {
            mergeCnt++; // increment now so it is correct for exception handling
            Vector arr = getRecords(dir);
            if (arr == null) {
                log.error("combineFile -null array - Run terminated");
                return CONVERT_REC_FAILURE;
            }
            if ( log.isDebugEnabled() ) {
                    log.debug("FOUND:" + dir.getPath() + "=" + arr.size());
            }
            MarcRecord marcOut = new MarcRecord();
            Status status = buildCombinedRecord(dir, arr, marcOut);

            if ( log.isDebugEnabled() ) {
                    log.debug("rec # " + mergeCnt + " convert status = " + status.recordStatus);
            }

            // check the conversion status after validation
            return addStat(status);

        } catch (MarcEndOfFileException e) {
            inputCnt--; // adjust for adding prior to read, this is easier than
                            // accounting for all the extra adds required to get the
                            // count right for all the other exceptions.
            log.debug("Trapped MarcEndOfFileException - rec count = " + inputCnt);
            convertStatus = CONVERT_EOF;

        } catch (MarcException e) {
            log.error("MarcException for record (I-" + inputCnt + "): " + e.getMessage(), e);
            convertStatus = CONVERT_REC_FAILURE;

        } catch (Exception e) {
            log.error("Exception: for record (I-" + inputCnt + "): " + e.getMessage(), e);
            convertStatus = CONVERT_REC_FAILURE;
        }

        return convertStatus;

    }

/* 
 * 6/3/2011 dbb
 * This step includes processing specific to STF in the logic to identify
 * which of the records is the bib record. For STF records, the bib record
 * (or base record) does not have an 004. (See marcToDir.)
 */

    private Status buildCombinedRecord(File dir, Vector arr, MarcRecord outMarc)
    {
        MarcRecord marcRec = null;
        int baseCnt = 0;
        int holdCnt = 0;
     //  String id001 = null;
        String bibId001 = null;
        String holdId001 = null;
        Status status = new Status();
        status.pathName = dir.getPath();

        if ((arr == null) || (arr.size() == 0)) {
            status.recordStatus = CONVERT_REC_FAILURE;
            return status;
        }
        status.recordStatus = CONVERT_JOB_SUCCESS;

        // append bib record(s)
        for (int i=0; i < arr.size(); i++) {
            marcRec = (MarcRecord)arr.elementAt(i);
             bibId001 = marcRec.getLeaderValue();
            System.out.println(bibId001);

            if (!(bibId001.charAt(6) == 'y' || bibId001.charAt(6) == 'u'
				|| bibId001.charAt(6) == 'v' || bibId001.charAt(6) == 'x')){ // base record
        	appendBibMarc(outMarc, marcRec);
                baseCnt++;
            }
        }

        // append holding records
        for (int i=0; i < arr.size(); i++) {
            marcRec = (MarcRecord)arr.elementAt(i);
    
            holdId001= marcRec.getLeaderValue();
            if ((holdId001.charAt(6) == 'y' || holdId001.charAt(6) == 'u'
				|| holdId001.charAt(6) == 'v' || holdId001.charAt(6) == 'x')) {
        	appendHoldMarc(outMarc, marcRec);
                holdCnt++;
            }
        }

        if (baseCnt == 0) {
            setReject(status, outMarc, "Record Rejected - no bib record");

        } else if (holdCnt == 0) {
            setReject(status, outMarc, "Record Rejected - no holding record");

        } else if (baseCnt > 1) {
            setReject(status, outMarc, "Record Rejected - more than one bib record");
        }
            
        if ( log.isInfoEnabled() ) {
            log.info("Merge:" + dir.getPath() 
                    + " base:" + baseCnt 
                    + " - hold:" + holdCnt
                    + " - status:" + status.recordStatus);
        }
        dumpMarc(2, outMarc, "Sort:" + okCnt);
        status.marcStr = outMarc.marcDump();
        status.marc = outMarc;
        return status;
    }

    private void setReject(Status status, MarcRecord marc, String msg)
    {
        status.msg = msg;
        log.error(status.msg + "(" + status.pathName + ")");
        status.recordStatus = CONVERT_REC_REJECT;
//        marc.setField("997",
//            "  -$a" + msg, 
//            "-$", MarcRecord.END_LIST);

    //    status.id = marc.getFirstValue("001", null);
      //  if (status.id == null) status.id = marc.getFirstValue("001", null);
      //  status.marc = marc;
    }

    private void appendBibMarc(MarcRecord outMarc, MarcRecord inMarc)
    {

         MarcFieldList list = inMarc.allFields("852");
       	outMarc.deleteFields(list);
        appendAll(outMarc, inMarc);
    }

/* 
 * 6/3/2011 dbb
 * Details of this step (constructing 009 and appending holdings) are specific 
 * to STF.
 */

 private void appendHoldMarc(MarcRecord outMarc, MarcRecord inMarc)
    {
        MarcFieldList list = inMarc.allFields("001", "009");
	    outMarc.deleteFields(list);
        appendAll(outMarc, inMarc);
    }

    private void appendAll(MarcRecord outMarc, MarcRecord inMarc)
    {
        MarcFieldList mfl = inMarc.allFields();
        outMarc.setFields(mfl, MarcRecord.END_LIST);
    }

    private Vector getRecords(File dir)
    {
        Vector arr = new Vector(100);
        MarcStream marcReader = null;

        try {
            marcReader = new MarcStream(dir);
            if (marcReader == null) return null;
            MarcRecord marcRec = null;
            for ( int i = 0; i < maxIn; i++ ) {
                marcRec = marcReader.getRecord(i);;
                if (marcRec == null) break;
                arr.addElement(marcRec);
                if ( log.isDebugEnabled() ) {
                    log.debug("Read merge:" + dir.getPath() + "-" + i);
                }
            }

        } catch (MarcEndOfFileException e) {

        } catch (Exception ex) {
            log.error("Exception: for record " + dir.getPath());
            throw new RuntimeException("Exception: for record " + dir.getPath() + " error:" + ex.toString());
        }
        if ( log.isDebugEnabled() ) {
            log.debug("getRecords return:" + dir.getPath() + " - " + arr.size());
        }

        return arr;
    }

    /** Deletes all files and subdirectories under dir.
     * Returns true if all deletions were successful.
     * If a deletion fails, the method stops attempting to delete and returns false.
     * @param dir File entry to directory to be deleted
     * @return true=directory deleted, false=directory not deleted
     */
    private static boolean deleteDir(File dir) 
    {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i=0; i<children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }
    
        // The directory is now empty so delete it
        return dir.delete();
    }

    /** Deletes all files and subdirectories under dir.
     * Returns true if all deletions were successful.
     * If a deletion fails, the method stops attempting to delete and returns false.
     * @param dir File entry to directory to be deleted
     * @return true=directory deleted, false=directory not deleted
     */
    private static boolean deleteDir(String outDirName) 
    {
        File outDirFile = new File(outDirName);
        return deleteDir(outDirFile);
    }


    private class Status
    {
        public int recordStatus = 0;
        public String marcStr = null;
        public String msg = null;
        public String pathName = null;
        public MarcRecord marc = null;
        public String id = null;
    }

    

}
