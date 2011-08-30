package org.cdlib.marcconvert;

import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.Character;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Properties;
import java.util.TreeMap;
import java.util.Vector;
import java.text.DateFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;

import org.apache.log4j.Logger;

import org.cdlib.util.marc.MarcException;
import org.cdlib.util.marc.LocationTable;
import org.cdlib.util.marc.MarcConstants;
import org.cdlib.util.marc.MarcEndOfFileException;
import org.cdlib.util.marc.MarcFormatException;
import org.cdlib.util.marc.MarcParmException;
import org.cdlib.util.marc.MarcRecord;
import org.cdlib.util.marc.MarcStream;
import org.cdlib.util.string.F;
import org.cdlib.util.string.StringUtil;
import org.cdlib.util.marc.MarcFieldList;
import org.cdlib.util.marc.Field;
import org.cdlib.util.marc.MarcFixedLengthField;
import org.cdlib.util.marc.MarcVblLengthField;
import org.cdlib.util.marc.SharedPrintTable;


/**
 * This class runs the Marc conversion using a subclass
 * of MarcConvert
 *
 *
 * @author <a href="mailto:david.loy@ucop.edu">David Loy</a>
 * @author <a href="mailto:shawnm@splorkin.com">Shawn McGovern</a>
 * @version $Id: RunConvert.java,v 1.18 2008/01/28 23:21:45 rkl Exp $
 */
public class RunConvert
    implements ConvertConstants, MarcConstants
{
	/**
	 * log4j Logger for this class.
	 */
    private static Logger log = Logger.getLogger(RunConvert.class);

	/**
	 * CVS header string.
	 */
    public static final String cvsHeader = "$Header: /cvs/root/melvyl/ei/marcconvert/java/org/cdlib/marcconvert/RunConvert.java,v 1.18 2008/01/28 23:21:45 rkl Exp $";

	/**
	 * CVS version string.
	 */
    public static final String version = "$Revision: 1.18 $";

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
    private static TreeMap errorStats  = new TreeMap();
    private static TreeMap rejectStats = new TreeMap();

    //====================================================
    //       VARIABLES
    //====================================================

    // Class variables set in the config file
    private String  inFileName      = null;
    private String  outFileName     = null;
    private String  errorFileName   = null;
    private String  rejectFileName  = null;
    private String  reportFileName  = null;
    private String  loctabFileName  = null;
    private String  sharedPrintFileName  = null;
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
    private SharedPrintTable   sptab     = null;
    private MarcConvert        converter = null;

    private Properties config      = new Properties();
    private String     startTime   = new String();
    private String     endTime     = new String();
    private int        inputCnt    = 0;
    private int        outputCnt   = 0;
    private int        errorCnt    = 0;
    private int        rejectCnt   = 0;
    private int        skipCnt     = 0;
    private int        okCnt       = 0;
    private int        runStatus   = -1;


    //====================================================
    //       CONSTRUCTORS
    //====================================================
    /**
     * RunConvert - contructor - set subclass for marc conversion
     * @param inMarcConvert - Marc conversion subclass of MarcConvert
     */
    public RunConvert(MarcConvert inMarcConvert)
        throws MarcParmException
    {
        if (inMarcConvert == null)
        {
            throw new MarcParmException(this, "RunConvert: invalid parameters");
        }
        converter = inMarcConvert;
    }


    //====================================================
    //       PUBLIC METHODS
    //====================================================

    /**
     * Handle the conversion process. This method replaces the obsolete method process().
     */
    public int convert()
    {
        runStatus = CONVERT_JOB_SUCCESS;

        MarcRecord marcRecIn  = null;
        MarcRecord marcRecOut = null;
        MarcStream marcReader = null;

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

            marcReader = new MarcStream(inFileName);

            // initialize the location table
            if ( loctabFileName != null && loctabFileName.length() > 0 )
            {
                try
                {
                    loctab = new LocationTable(loctabFileName);
                }
                catch (RuntimeException re)
                {
                    log.error("Processing halted: failed to load location table");
                    runStatus = Math.max(runStatus, CONVERT_JOB_FAILURE);
                    return runStatus;
                }
            }
            else
            {
                 loctab = null;
                 log.warn("Location table name not specified - location validation will be bypassed");
                 runStatus = Math.max(runStatus, CONVERT_JOB_WARNING);
            }

            // initialize the shared print table
            if ( sharedPrintFileName != null && sharedPrintFileName.length() > 0 )
            {
                try
                {
                    sptab = new SharedPrintTable(sharedPrintFileName);
                }
                catch (RuntimeException re)
                {
                    log.error("Processing halted: failed to load shared print table");
                    runStatus = Math.max(runStatus, CONVERT_JOB_FAILURE);
                    return runStatus;
                }
            }
            else
            {
                 sptab = null;
                 log.warn("Shared print table name not specified - shared print processing will be bypassed");
                 runStatus = Math.max(runStatus, CONVERT_JOB_WARNING);
            }
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

        debugOut(2, "DEBUG" + EOL);
        marcReader.start();
        converter.setMarcConvert(config, fileDate, report);

        if ( log.isDebugEnabled() )
        {
            log.debug("skip = " + skip);
            log.debug("maxIn = " + maxIn);
            log.debug("maxOut = " + maxOut);
        }

        int readStatus = -1;
        int convertStatus = -1;

        int max = skip + maxIn;
        convertloop:
        for ( int i = skip; i < maxIn; i++ )
        {
            try
            {
                if ( outputCnt >= maxOut ) break;

                marcRecIn = new MarcRecord();
                inputCnt++; // increment now so it is correct for exception handling
                readStatus = marcReader.getRecord(i, marcRecIn);

                if (marcRecIn == null)
                {
                    log.error("Record # " + inputCnt + " input routine returned null MarcRecord");
                    runStatus = CONVERT_JOB_FAILURE;
                    break;
                }

		// Reject record if it contains a line feed(hex 0a) or carriage return(hex 0d).
		checkLFCR(marcRecIn);

		// Check for and replace any hex 7f values
		check7f(marcRecIn);

                dumpMarc(2, marcRecIn, "Input:" + i);
                marcRecOut = new MarcRecord();
                convertStatus = converter.convert(marcRecIn, marcRecOut);

                if ( log.isDebugEnabled() )
                {
                    log.debug("rec # " + inputCnt + " convert status = " + convertStatus);
                }

                switch ( convertStatus )
                {
                case CONVERT_REC_SKIP:
                    skipCnt = addRunStat(MarcConvert.SKIPPED);
                    break;

                case CONVERT_REC_REJECT:
                case CONVERT_REC_FAILURE:
                    dumpMarc(3, marcRecOut, "Output RF(" + convertStatus + "):" + i);
                    writeReject(rejects, marcRecIn.marcDump());
                    rejectCnt = addRunStat(MarcConvert.REJECTED);
					addRejectStat(converter.getStatusMsg());
                    log.error("Record Rejected: I-" + inputCnt + " R-" + rejectCnt + " "
                              + converter.getStatusMsg());
                    break;

                case CONVERT_REC_SUCCESS:
                case CONVERT_REC_ERROR:

                    // validate the output record
                    int tempStatus = (validateOut
                                      ? converter.validateOutRec(inputCnt, loctab)
                                      : CONVERT_REC_SUCCESS);

                    if ( log.isDebugEnabled() )
                    {
                        log.debug("rec # " + inputCnt + " validate status = " + tempStatus);
                    }
                    convertStatus = Math.max(convertStatus, tempStatus);

		    // create a shared print field (793), if needed
		    tempStatus = converter.make793(inputCnt, sptab);

                    if ( log.isDebugEnabled() )
                    {
                        log.debug("rec # " + inputCnt + " make 793 status = " + tempStatus);
                    }
                    convertStatus = Math.max(convertStatus, tempStatus);

                    dumpMarc(3, marcRecOut, "Output SE(" + convertStatus + "):" + i);

                    // convert the marc record to a String here because all the output
                    // routines will need it, and it might fail if the record is too
                    // long, and if so we want to catch it in one place.
                    String marcStr = null;
                    try
                    {
                        marcStr = marcRecOut.marcDump();
                    }
                    catch (Exception e)
                    {
                        log.error("Failed to dump converted record - Exception: " + e.getMessage(), e);
                        writeReject(rejects, marcRecIn.marcDump());
                        rejectCnt = addRunStat(MarcConvert.REJECTED);
                        addRejectStat("converted record could not be dumped to marc record");
                        log.error("Record Rejected: I-" + inputCnt + " R-" + rejectCnt + " "
                                  + "converted record could not be dumped to marc record");
                        continue;
                    }

                    // check the conversion status after validation
                    switch ( convertStatus )
                    {
                    case CONVERT_REC_REJECT:
                        writeReject(rejects, marcStr);
                        rejectCnt = addRunStat(MarcConvert.REJECTED);
						addRejectStat(converter.getStatusMsg());
                        log.error("Record Rejected: I-" + inputCnt + " R-" + rejectCnt + " "
                                  + converter.getStatusMsg());
                        break;

                    case CONVERT_REC_SUCCESS:
                        writeMarc(marcOut, marcStr);
                        okCnt = addRunStat(MarcConvert.OK);
                        break;

                    case CONVERT_REC_ERROR:
                        writeMarc(marcOut, marcStr);
                        writeError(errors, marcStr);
                        errorCnt = addRunStat(MarcConvert.ERROR);
                        break;

                    default:
                        log.error("Process halted: Invalid validation return code = "
								  + convertStatus);
                        runStatus = Math.max(runStatus, CONVERT_JOB_FAILURE);
                        break convertloop;
                    } // end of post validation switch()

                    break;

                default:
                    log.error("Process halted: Invalid conversion return code = " + convertStatus);
                    runStatus = Math.max(runStatus, CONVERT_JOB_FAILURE);
                    break convertloop;
                } // end of post conversion switch()
            }
            catch (MarcEndOfFileException e)
            {
				inputCnt--; // adjust for adding prior to read, this is easier than
                            // accounting for all the extra adds required to get the
                            // count right for all the other exceptions.
                log.debug("Trapped MarcEndOfFileException - rec count = " + inputCnt);
                break;
            }
            catch (MarcFormatException e)
            {
                try
                {
                    writeReject(rejects, marcReader.getReadBuffer());
                    rejectCnt = addRunStat(MarcConvert.REJECTED);
					addRejectStat(e.getMessage());
                    log.error("Record Rejected: I-" + inputCnt + " R-" + rejectCnt + " "
                              + e.getMessage());

					if ( log.isDebugEnabled() )
					{
						log.error("MarcFormatException: printing stack trace", e);
					}
                }
                catch (IOException ioe)
                {
                    log.error("Exception: " + ioe.getMessage(), ioe);
                    runStatus = CONVERT_JOB_FAILURE;
                    break;
                }
            }
            catch (MarcDropException e)
            {
                try
                {
                    writeReject(rejects, marcReader.getReadBuffer());
                    rejectCnt = addRunStat(MarcConvert.REJECTED);
					addRejectStat(e.getMessage());
                    log.error("Record Rejected: I-" + inputCnt + " R-" + rejectCnt + " "
                              + e.getMessage());
                }
                catch (IOException ioe)
                {
                    log.error("Exception: " + ioe.getMessage(), ioe);
                    runStatus = CONVERT_JOB_FAILURE;
                    break;
                }
            }
            catch (MarcException e)
            {
                log.error("MarcException for record (I-" + inputCnt + "): " + e.getMessage(), e);
                runStatus = CONVERT_JOB_FAILURE;
                break;
            }
            catch (Exception e)
            {
                log.error("Exception: for record (I-" + inputCnt + "): " + e.getMessage(), e);
                runStatus = CONVERT_JOB_FAILURE;
                break;
            }
        } // end convertloop

        endTime = getPrintDateTime();
        doReport();
        close();
        return runStatus;
    }


    /**
     * Get convert status and update table
     */
    public void addRunStat()
    {
        if (converter != null)
        {
            addRunStat(converter.getStatus());
        }
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
     * checkLFCR - reject record if it contains line feed(hex 0a) or carriage return(hex 0d)
     */
    private void checkLFCR(MarcRecord marcRec)
    {
	String LFStr = "\n";
	String CRStr = "\r";

        // check leader
        String leader = marcRec.getLeader().value();
        if (leader.indexOf(LFStr) > 0) 
        {
            throw new MarcFormatException(this, "Line feed(hex 0a) found on leader");
        }
        if (leader.indexOf(CRStr) > 0)
	{
            throw new MarcFormatException(this, "Carriage return(hex 0d) found on leader");
	}

        // check all tag fields
	MarcFieldList mfl = marcRec.getFields();
	Enumeration enu = mfl.elements();
	while (enu.hasMoreElements())
	{
	    Field f = (Field) enu.nextElement();
	    if (f.getTag().compareTo("010") < 0)
	    {
	        MarcFixedLengthField fixedFld = (MarcFixedLengthField) f;
		String fdata = fixedFld.value();
		if (fdata.indexOf(LFStr) > 0) 
		{
		    throw new MarcFormatException(this, "Line feed (hex 0a) found on tag " + f.getTag());
		}
		if (fdata.indexOf(CRStr) > 0)
		{
		    throw new MarcFormatException(this, "Carriage return (hex 0d) found on tag " + f.getTag());
		}
	    }
	    else
	    {
		MarcVblLengthField vblFld = (MarcVblLengthField) f;
		String vdata = vblFld.value();
		if (vdata.indexOf(LFStr) > 0) 
		{
		    throw new MarcFormatException(this, "Line feed (hex 0a) found on tag " + f.getTag());
		}
		if (vdata.indexOf(CRStr) > 0)
		{
		    throw new MarcFormatException(this, "Carriage return (hex 0d) found on tag " + f.getTag());
		}
    	    }
	}
    }

    /**
     * check7f - replace any hex value 7f.  This char will corrupt the record 
     */
    private void check7f(MarcRecord marcRec)
    {
        Character char7f = new Character('\u007F');
	String Str7f = char7f.toString();

        // check leader
        String leader = marcRec.getLeader().value();
        if (leader.indexOf(Str7f) > 0)
        {
	    throw new MarcFormatException(this, "Delete character (hex 7f) found in leader");
        }

        // check all tag fields
        MarcFieldList mfl = marcRec.getFields();
        Enumeration enu = mfl.elements();
        while (enu.hasMoreElements())
        {
            Field f = (Field) enu.nextElement();
            if (f.getTag().compareTo("010") < 0)
            {
                MarcFixedLengthField fixedFld = (MarcFixedLengthField) f;
                String fdata = fixedFld.value();
		if (fdata.indexOf(Str7f) > 0)
                {
	    	    throw new MarcFormatException(this, "Delete character (hex 7f) found on tag " + f.getTag());
                }
            }
            else
            {
                MarcVblLengthField vblFld = (MarcVblLengthField) f;
                String vdata = vblFld.value();
                if (vdata.indexOf(Str7f) > 0)
                {
	    	    throw new MarcFormatException(this, "Delete character (hex 7f) found on tag " + f.getTag());
                }
            }
        }
    }


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
        report.write(EOL + EOL + "MARC CONVERT REPORT" + EOL + EOL);
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
    {   String url = "";
    
        Properties pin = new Properties();
        Properties pout = new Properties();

        String test = null;
        System.setProperty("config", "C:/Dev/conf/config/config.txt" );
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
         * Get shared print table file name
         */
        sharedPrintFileName = pin.getProperty("sharedprintfile", "");
        if ( sharedPrintFileName.length() == 0 )
        {
            log.warn("sharedPrintFileName - shared print table file name not supplied");
        }
        pout.setProperty("sharedprintfile", sharedPrintFileName);

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
                 + " outfile:" + outFileName
                 + " reportfile:" + reportFileName
                 + " errorfile:" + errorFileName
                 + " rejectfile:" + rejectFileName
                 + " loctabfile:" + loctabFileName
                 + " sharedPrintfile:" + sharedPrintFileName
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
        report.write("  sharedPrintfile:  " + sharedPrintFileName + EOL);
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
        reportRunStat(outputCnt, "Records Output");
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
}
