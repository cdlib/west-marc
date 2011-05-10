package org.cdlib.marcconvert;

import java.util.Properties;
import java.util.Vector;

import org.apache.log4j.Logger;

import org.cdlib.util.marc.LocationTable;
import org.cdlib.util.marc.MarcConstants;
import org.cdlib.util.marc.MarcException;
import org.cdlib.util.marc.MarcFieldList;
import org.cdlib.util.marc.MarcFixedLengthField;
import org.cdlib.util.marc.MarcParmException;
import org.cdlib.util.marc.MarcRecord;
import org.cdlib.util.marc.MarcSubfield;
import org.cdlib.util.marc.MarcVblLengthField;
import org.cdlib.util.marc.SharedPrintTable;
import org.cdlib.util.marc.SharedPrint;
import org.cdlib.util.string.StringUtil;

/**
 * Base class that handles Marc conversion.
 *
 *
 * @author <a href="mailto:david.loy@ucop.edu">David Loy</a>
 * @author <a href="mailto:shawnm@splorkin.com">Shawn McGovern</a>
 * @version $Id: MarcConvert.java,v 1.16 2010/04/02 19:15:53 aleph16 Exp $
 */
public abstract class MarcConvert
    implements ConvertConstants, MarcConstants
{
	/**
	 * log4j Logger for this class.
	 */
    private static Logger log = Logger.getLogger(MarcConvert.class);

	/**
	 * CVS header string.
	 */
    public static final String cvsHeader = "$Header: /cvs/root/melvyl/ei/marcconvert/java/org/cdlib/marcconvert/MarcConvert.java,v 1.16 2010/04/02 19:15:53 aleph16 Exp $";

	/**
	 * CVS version string.
	 */
    public static final String version = "$Revision: 1.16 $";

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

    /**
     * Current record number
     */
    protected int recNbr = 0;

    /**
     * Input Marc record
     */
    protected MarcRecord inMarc = null;

    /**
     * Output Marc record
     */
    protected MarcRecord outMarc = null;

    /**
     * Convert date to use when building a default 005 field
     */
    protected String fileConvertDate = null;

    /**
     * Statistics report file
     */
    protected ConvertFile report = null;

    /**
     * The runtime configuration options
     */
    protected Properties config = null;

    /**
     * Indicator used to identify a benchmark test run. A benchmark test
     * will use the value supplied in the benchmark date option when
     * creating 998 error fields, rather than the run date. This allows
     * for stable compares across runs.
     */
	protected boolean benchRun = false;

    /**
     * The date string to use when creating 998 error fields during a
     * benchmark run. This can be any text, it need not be a date.
     */
	protected String benchRunDateStr = null;

    /**
     * The run date string, this should be formatted accorinding the 005 standard.
     */
	protected String runDateStr = null;

    /**
     * The error date string, this should be formatted accorinding the 005 standard.
     * This string will go into 998 error fields, subfield $d.
     */
	protected String errDateStr = null;


    /**
     * A <code>MarcRecord</code> containing a slice of a repeating group of fields.
     *
     * @see #setGroup(String startTag, String endTag, String startGroupTag, int occ)
     * @see #setGroup(String startTag, String endTag, int occ)
     */
    protected MarcRecord groupMarc = null;

    /**
     * A work field used to hold groups of fields during processing of repeating
     * field groups.
     *
     * @see #groupMarc
     * @see #groupTag
     */
    private MarcFieldList mfl = null;

    /**
     * The starting tag of a recurring group of tags.
     *
     * @see #groupMarc
     * @see #mfl
     */
    private String groupTag = null;

    /**
     * The text status of a particular record conversion.
     * This may be accessed by the <code>getStatus()</code> method.
     *
     * @see #getStatus()
     */
    private String status = "none";

    /**
     * The status message for a particular record convertsion.
     * This may be accessed by the <code>getStatusMsg()</code> method.
     *
     * @see #getStatusMsg()
     */
    private String statusMsg = null;

    /**
     * The debug level used by the debugOut() method. That method is obsolete, having
     * been replaced by log4j as the preferred method for issuing debug content.
     */
    private int verbose = 0;

    /**
     * Marc record converted successfully
     */
    public static String OK = "OK";


    /**
     * Marc record converted with errors
     */
    public static String ERROR = "Error";

    /**
     * Marc record rejected
     */
    public static String REJECTED = "Rejected";

    /**
     * Marc record skipped
     */
    public static String SKIPPED = "Skipped";

    /**
     * System specific text line separator
     */
    public static String EOL = System.getProperty("line.separator");


    /**
     * Instantiate an uninitialized MarcConvert object.
     * Before this object may be used the
     * {@link #setMarcConvert(Properties, String, ConvertFile) setMarcConvert}
     * method should be called to complete the required setup of this object.
     */
    protected MarcConvert(){}


    /**
     * Instantiate an initialized MarcConvert object. This constructor will
     * set the confuguration properties, the default 008 date, and the
     * report file. It requires a <code>String</code> containing a raw
     * marc record that can be used to set the input <code>MarcRecord</code>,
     * but since the input marc record must be passed to the convert method
     * this is pointless.
     *
     * @deprecated This contructor should no longer be used. Instead use
     * {@link #MarcConvert()} followed by
     * {@link #setMarcConvert(Properties, String, ConvertFile) setMarcConvert}
     */
    protected MarcConvert(String inMarc,
                          Properties inConfig,
                          String inDate,
                          ConvertFile inReport)
    {
        MarcRecord in = new MarcRecord(inMarc);
        setMarcConvert(inConfig, inDate, inReport);
    }


    /**
     * Method called to perform the conversion. An implementation for this method
     * must be supplied by any concrete class which extends this abstract class.
     *
     * @return the conversion status code
     */
    public abstract int convert(MarcRecord inRec, MarcRecord outRec);


    /**
     * Return converted Marc record.
     *
     * @return converted record
     */
    public MarcRecord getMarcOut()
    {
        return outMarc;
    }

    /**
     * Return the value of the conversion status code.
     *
     * @return the value of the conversion status code
     */
    public String getStatus()
    {
        return status;
    }

    /**
     * Return the value of the conversion status message.
     *
     * @return the value of the conversion status message
     */
    public String getStatusMsg()
    {
        return statusMsg;
    }

    /**
     * Sets debug level for debugOut processing using the suppied parameter.
     *
     * @param in debug level
     *
     * @deprecated This method of debugging has been superceded by the use of log4j.
     */
    public void setDebug(int in)
    {
        verbose = in;
    }

    /**
     * Sets debug level for debugOut processing using the debug level from
     * the config properties.
     *
     * @deprecated This method of debugging has been superceded by the use of log4j.
     */
    public void setDebug()
    {
        String test = config.getProperty("debug", "none");
        verbose = (test.equals("none") ? 0 : StringUtil.parseInt(test));
    }

    /**
     * setStatus - sets status for the record conversion
     * @param in status for conversion
     */
    public void setStatus(String in)
    {
        status = in;
    }

    /**
     * setStatusMsg - set message for this error
     * @param in status message for conversion
     */
    public void setStatusMsg(String in)
    {
        statusMsg = in;
    }

    /**
     * Set the confuguration properties, the default 008 date, and the
     * report file.
     *
     * @param inConfig  configuration properties
     * @param inDate    the default 005 date
     * @param inReport  the report file
     */
    public void setMarcConvert(Properties inConfig,
                               String inDate,
                               ConvertFile inReport)
    {
        if ((inConfig == null)
            || (inDate == null)
            || (inReport == null))
        {
            throw new MarcParmException(this, "setMarcConvert: invalid parameters");
        }

        config = inConfig;
        report = inReport;
        outMarc = new MarcRecord();
        fileConvertDate = inDate;

		runDateStr      = config.getProperty("rundate", "no run date");
        benchRun        = config.getProperty("benchrun", "N").equalsIgnoreCase("Y");
		benchRunDateStr = config.getProperty("benchdate", "BenchMarkDate");
		errDateStr      = (benchRun ? benchRunDateStr : runDateStr);

        reset();
    }

    /**
     * Set the output marc record using the replied reference.
     *
     * @param outMarcRec the marc record to use as a conversion target
     *
     * @deprecated The {@link #convert(MarcRecord, MarcRecord)} syntax eliminates
     * the value of this method.
     *
     * @throws MarcParmException if the parameter is null
     */
    public void setMarcOut(MarcRecord outMarcRec)
    {
        if ( outMarcRec == null)
        {
            throw new MarcParmException(this, "Output marc record cannot be null");
        }

        outMarc = outMarcRec;
    }

    /**
     * Set the input marc record using the supplied reference. A side effect
     * of this method is the output marc record is set to a new marc record.
     * This almost certainly bad, and exists here as an historical artifact
     * required to allow backward compatibility as the converters were all
     * modified to use the new <code>convert(MarcRecord, MarcRecord)</code>
     * syntax. It should be removed, but since this entails regression tesing
     * the entire set of converters the cost may outweigh the benefits.
     *
     * @param in the marc record to use as a conversion source
     *
     * @deprecated The {@link #convert(MarcRecord, MarcRecord)} syntax eliminates
     * the value of this method.
     *
     * @throws MarcParmException if the parameter is null
     */
    public void setMarcRecord(MarcRecord in)
    {
        if (in == null)
        {
            throw new MarcParmException(this, "Input marc record cannot be null");
        }

        inMarc = in;
        outMarc = new MarcRecord();
        reset();
    }

    /**
     * Set input record using the raw marc record in the supplied <code>String</code>.
     * This method calls <code>setMarcRecord(MarcRecord)</code>, and so has all of its
     * attendant evils.
     *
     * @param strMarc - Marc record to convert
     *
     * @deprecated The {@link #convert(MarcRecord, MarcRecord)} syntax eliminates
     * the value of this method.
     *
     * @throws MarcParmException if the parameter is null or has zero length
     * @throws MarcDropException if the raw marc record cannot be parsing into
     * a <code>MarcRecord</code> object.
     *
     * @see #setMarcRecord(MarcRecord in)
     */
    public void setMarcRecord(String strMarc)
    {
        if ((strMarc == null) || (strMarc.length() == 0))
        {
            throw new MarcParmException(this, "Input marc record cannot be null or empty");
        }

        try
        {
            setMarcRecord(new MarcRecord(strMarc));
        }
        catch (Exception e)
        {
            throw new MarcDropException(this, e.getMessage());
        }
    }

    /**
     * Reset the wretched global variables. This method must be invoked between
     * record conversions or erroneous results will occur.
     */
    public void reset()
    {
        groupMarc = null;
        groupTag  = null;
        mfl       = null;
        status    = "none";
        statusMsg = null;
    }


    //====================================================
    //       PROTECTED
    //====================================================

    /**
     * Copy all ocurences of a variable length fields with the given tag
     * from the input marc record to the output marc record, preserving the
     * subfields and their values, but overriding the indicators for each field
     * with the supplied value. It the value supplied for the indicators is
     * either null or not of length 2, then the indicators from the input
     * field are used.
     *
     * @param inTag      input tag to select
     * @param outTag     output tag to target
     * @param indicators indicators to use in output fields
     */
    protected void copyVFields(String inTag, String outTag, String indicators)
    {
        MarcFieldList fields = inMarc.allFields(inTag);

        if (fields != null)
        {
            boolean overrideIndicators = ((indicators != null) && (indicators.length() == 2));
            int max = fields.size();
            for (int i = 0; i < max; i++)
            {
                MarcVblLengthField field = (MarcVblLengthField)fields.elementAt(i);
                if (overrideIndicators)
                {
                    field.setIndicators(indicators);
                }

                field.setTag(outTag);
                outMarc.setField(field, outMarc.TAG_ORDER);
            }
        }
    }

    /**
     * Copy all subs from one field to another
     */
    protected int copyAllSub(
            String inTag,
            MarcVblLengthField inFld,
            String outTag,
            MarcVblLengthField outFld
        )
    {
        Vector vec = inFld.subfields(inTag, true);
        MarcSubfield sub = null;
        String subVal = null;
        if ((vec != null) && (vec.size() > 0)) {
            for (int i=0; i < vec.size(); i++) {
                sub = (MarcSubfield)vec.elementAt(i);
                subVal = sub.value();
                if ( !isEmpty(subVal) ) {
                    outFld.addSubfield(outTag, subVal);
                }
            }
            return vec.size();
        }
        return 0;
    }

    /**
     * Copy first subfield from one field to another
     */
    protected MarcSubfield copyFirstSub(
                String inTag,
                MarcVblLengthField inFld,
                String outTag,
                MarcVblLengthField outFld
            )
    {
        MarcSubfield sub = inFld.firstSubfield(inTag);
        if (sub != null) {
            String subVal = sub.value();
            if (!isEmpty(subVal)) {
                outFld.addSubfield(outTag, subVal);
            }
        }
        return sub;
    }

    /**
     * see if non-blank or control character is present
     * @param in string to be tested
     * @return true=empty, false=non blank found
     */
    protected static final boolean isEmpty(String in)
    {
        if ((in == null) || (in.length() == 0)) return true;
        int slen = in.length();
        char c = '\0';
        for (int i=0; i<slen; i++) {
            c = in.charAt(i);
            if (c > ' ') return false;
        }
        return true;
    }

    /**
     * Copy all field in the input marc record to output marc record.
     */
    protected void moveAll()
    {
        MarcFieldList fields = inMarc.allFields();
        outMarc.setFields(fields, MarcRecord.END_LIST);
    }

    /**
     * Copy the input marc record's leader to output marc record.
     */
    protected void moveLeader()
    {
        String leader = inMarc.getLeaderValue();
        outMarc.setLeaderValue(leader);
    }

    /**
     * Copy the input marc record's leader to output marc record.
     * @param marc marc record used for extraction
     */
    protected void moveLeader(MarcRecord marc)
    {
        String leader = marc.getLeaderValue();
        outMarc.setLeaderValue(leader);
    }

    /**
     * Copy a range of fields from the input marc record to the output marc record.
     *
     * @param from low range tag for move (inclusive)
     * @param to   high range tag for move (inclusive)
     */
    protected void moveOut(String from, String to)
    {
        MarcFieldList fields = inMarc.allFields(from, to);
        outMarc.setFields(fields, MarcRecord.END_LIST);
    }

    /**
     * Copy a range of fields from the input marc record to the output marc record.
     *
     * @param marc marc record used for extraction
     * @param from low range tag for move (inclusive)
     * @param to   high range tag for move (inclusive)
     */
    protected void moveOut(MarcRecord marc, String from, String to)
    {
        MarcFieldList fields = marc.allFields(from, to);
        outMarc.setFields(fields, MarcRecord.END_LIST);
    }

    /**
     * Copy a range of fields from the input marc record to the output marc record.
     *
     * @param marc marc record used for extraction
     * @param from low range tag for move (inclusive)
     * @param to   high range tag for move (inclusive)
     * @param listLoc location in to save in MarcRecord
     */
    protected void moveOut(MarcRecord marc, String from, String to, int listLoc)
    {
        MarcFieldList fields = marc.allFields(from, to);
        outMarc.setFields(fields, listLoc);
    }

    /**
     * Copy a range of fields with numeric tags from the input marc record
     * to the output marc record. Note: fields with non-numeric tags are
     * not copied.
     *
     * @param from low range tag for move (inclusive)
     * @param to   high range tag for move (inclusive)
     */
    protected void moveOut(int from, int to)
    {
        MarcFieldList fields = inMarc.allFields(from, to);
        outMarc.setFields(fields, MarcRecord.END_LIST);
    }


    /**
     * Create a special Marc record containing a group of fields for processing.
     * This is used to handle repeating Marc groups.
     * <br><br>
     * A field list containing all the tags in the specified range is first selected
     * from the input marc record. This list is only built when the start tag changes,
     * when the list has not been previously built. Otherwise the existing list is used.
     * Then the requested occurence of the repeating group, using the tag specified in
     * the startGroupTag parameter, is selected from the field list, and that set
     * of fields, if non-empty, is used to build a <code>MarcRecord</code>.
     * That <code>MarcRecord</code> is returned.
     *
     * @param startTag      starting tag of the range of tags in field list backing this group
     * @param endTag        ending tag of the range of tags in this group
     * @param startGroupTag starting tag of the range of tags in this group
     * @param occ           the occurence of the group to select, releative to zero
     *
     * @return a <code>MarcRecord</code> containing the requested group of fields,
     *         of null if none are found
     *
     * @see #groupMarc
     */
    protected MarcRecord setGroup(String startTag, String endTag, String startGroupTag, int occ)
    {
        if ((groupTag == null) || (!groupTag.equals(startGroupTag)))
        {
            mfl = inMarc.allFields(startTag, endTag);
            if (mfl == null) return null;
            groupTag = startGroupTag;
        }
        MarcFieldList localmfl = mfl.getIncrement(startGroupTag, occ);
        if (localmfl == null) return null;

        groupMarc = new MarcRecord(localmfl, groupMarc.END_LIST);
        return groupMarc;
    }

    /**
     * Create a special Marc record containing a group of fields for processing.
     * This is used to handle repeating Marc groups.
     * <br><br>
     * A field list containing all the tags in the specified range is first selected
     * from the input marc record. This list is only built when the start tag changes,
     * when the list has not been previously built. Otherwise the existing list is used.
     * The the requested occurence of the repeating group is selected from the field list,
     * and that set of fields, if non-empty, is used to build a <code>MarcRecord</code>.
     * That <code>MarcRecord</code> is returned.
     *
     * @param startTag starting tag of the range of tags in this group
     * @param endTag   ending tag of the range of tags in this group
     * @param occ      the occurence of the group to select, releative to zero
     *
     * @return a <code>MarcRecord</code> containing the requested group of fields,
     *         of null if none are found
     *
     * @see #groupMarc
     */
    protected MarcRecord setGroup(String startTag, String endTag, int occ)
    {
        if ((groupTag == null) || (!groupTag.equals(startTag)))
        {
            mfl = inMarc.allFields(startTag, endTag);
            if (mfl == null) return null;
            groupTag = startTag;
        }
        MarcFieldList localmfl = mfl.getIncrement(startTag, occ);
        if (localmfl == null) return null;

        groupMarc = new MarcRecord(localmfl, groupMarc.END_LIST);
        return groupMarc;
    }

    /**
     * Create a field in the output marc record using the specified tag and data.
     *
     * @param tag  the new field's tag
     * @param data the new field's data
     *
     * @see org.cdlib.util.marc.MarcBaseRecord#setField(String tag, String data, String delim, int order)
     */
    protected void setField(String tag, String data)
    {
        outMarc.setField(tag, data, "$$", MarcRecord.MARC_TAG_ORDER);
    }


    /**
     * Write the supplied <code>String</code> to the report file, if the supplied
     * debug level is greater than the level specified in the verbose variable.
     *
     * @param inDebug  level for this output
     * @param str      string to output
     *
     * @deprecated this method of debugging has been superceded by the log4j api
     */
    protected void debugOut(int inDebug, String str)
    {
        if (verbose < inDebug) return;
        StringBuffer buff = new StringBuffer("***>" + str + EOL);
        report.write(buff.toString());
    }


    /**
     * Create Marc 793 field to hold shared print information.  
     * This method should be invoked after the validateOutRec method of MarcConvert has
     * completed. It assumes that all fields have been checked and are valid.
     * If lookup into table fials to retrieve entry, just ignore and do not create.
     *
     * @param recNbr         The number of curent record
     * @param sptab          Shared Print table
     *
     */
    protected int make793(int recNbr, SharedPrintTable sptab)
        throws MarcException
    {
        int rc = CONVERT_REC_SUCCESS;
        MarcFieldList mfl = outMarc.getFields(852, 852);

        if ( exists(mfl) )
        {
            int mflSize = mfl.size();
            for ( int i = 0; i < mflSize; i++ )
            {
                MarcVblLengthField f852 = (MarcVblLengthField)mfl.elementAt(i);
		String s852a = f852.firstSubfieldValue("a");
		String s852b = f852.firstSubfieldValue("b");
        	if ( sptab != null )
        	{
            		StringBuffer sb = new StringBuffer(30);

            		if ( s852a != null)
            		{
                		sb.append(s852a.trim());
            		}

            		if ( s852b != null)
            		{
                		sb.append(s852b.trim());
            		}

            		String key = sb.toString();

            		if ( log.isDebugEnabled() )
            		{
                		log.debug("Shared print search key = '" + key + "'");
            		}

			// If key not found, do not create 793.
            		if ( sptab.exists(key) )
            		{
			        MarcVblLengthField field = new MarcVblLengthField("793");
				SharedPrint sp = sptab.lookup(key);
       		 		field.setIndicators("  ");
        			field.addSubfield("a", sp.f793a);
        			field.addSubfield("p", sp.f793p);
        			outMarc.setField(field, outMarc.TAG_ORDER);
			        if ( log.isDebugEnabled() )
        			{
            				log.debug("rec# " + recNbr + "field 793 created: " + sp.toString()); 
        			}
			}
        	}
	    }
        }
        return rc;
    }


    /**
     * Create Marc 998 field to hold error information for those records
     * accecpted with errors.
     *
     * @param s901ab       the 901 $a and $b subfields in a <code>String</code> array
     * @param errorCode    the error code prompting this 998
     * @param errorMessage the error message for this 998
     *
     * @throws MarcExpention if any of the parameters are missing or empty
     */
    protected void make998(String[] s901ab, String errorCode, String errorMessage)
        throws MarcException
    {
        if ( log.isDebugEnabled() )
        {
            log.debug("998 params: $a = " + s901ab[0] + " $b " + errorCode
                      + " $c = " + errorMessage + " $d = " + s901ab[1]);
        }

        if ( s901ab == null || s901ab.length < 2
             || s901ab[0] == null || s901ab[0].length() == 0
             || s901ab[1] == null || s901ab[1].length() == 0)
        {
            log.error("998 input 901 subfields are null or have length zero");
            throw new MarcException(this, "Coding error detected: "
                                    + "998 input 901 subfields are null or have length zero");
        }

        if ( errorCode == null || errorCode.length() == 0 )
        {
            log.error("998 error code is null or has length zero");
            throw new MarcException(this, "Coding error detected: "
                                    + "998 error code is null or has length zero");
        }

        if ( errorMessage == null || errorMessage.length() == 0 )
        {
            log.error("998 error message is null or has length zero");
            throw new MarcException(this, "Coding error detected: "
                                    + "998 error message is null or has length zero");
        }

        MarcVblLengthField field = new MarcVblLengthField("998");
        field.setIndicators("  ");
        field.addSubfield("a", s901ab[0]);
        field.addSubfield("b", errorCode);
        field.addSubfield("c", errorMessage);
        field.addSubfield("d", errDateStr);
        field.addSubfield("e", s901ab[1]);
        outMarc.setField(field, outMarc.END_LIST);
        setStatus(ERROR);
        statusMsg = errorMessage;
		RunConvert.addErrorStat(errorCode);
    }


    /**
     * Perform post conversion validation. This method should be invoked after the
     * other conversion activity has completed and before the output record is
     * written.
     * <br><br>
     * The order of the validations is important to a degree.
     * <br><br>
     * The 901 validation must come before any validations that can create a
     * 998 field because the $a subfield it returns is used in the creation of
     * the 998 field.
     * <br><br>
     * The validations that can result in a rejected record should come before
     * the validations that cannot reject the record, because if the record is
     * rejected there is no point in further processing the ouptut. However,
     * the exception is the 852 checking. A missing 852 is a acceptable, but
     * cause a 998 to be created. An 852 with invalid location subfields causes
     * the record to be rejected. Clearly the existance check must come first.
     * <br><br>
     * The 005 validation is really a type of cleanup operation, in that it always
     * returns success, and can happen any time. I think it makes sense to do it
     * only to records we know will not be rejected. That way a rejected record
     * will have the original 005, if any. An alternate viewpoint is the reject
     * record should have the correct date field for easier reprocessing, and
     * to make it easier to align it with its non-rejected brethren.
     * <br><br>
     *
     * @param recNbr the current input record number
     * @param loctab the location table
     *
     * @return Status code indicating either success or error
     *
     * @see org.cdlib.marcconvert.ConvertConstants#CONVERT_REC_SUCCESS
     * @see org.cdlib.marcconvert.ConvertConstants#CONVERT_REC_ERROR
     * @see org.cdlib.marcconvert.ConvertConstants#CONVERT_REC_REJECT
     */
    public int validateOutRec(int recNbr, LocationTable loctab)
    {
        int      rc         = CONVERT_REC_SUCCESS;
        int      tempStatus = -1;
        String[] s901ab     = new String[2];
        boolean  deleteRec  = outMarc.isDeleteRecord();

        if ( log.isDebugEnabled() )
        {
            log.debug("rec# " + recNbr + " entering validation - delete is "
                      + (deleteRec ? "true" : "false"));
        }

        rc = Math.max(rc, tempStatus);
        rc = Math.max(rc, validate901(recNbr, s901ab)); // 901 $a and $b are set in here

        if ( rc < CONVERT_REC_REJECT )
        {
            rc = Math.max(rc, validate005(recNbr, s901ab));
        }

        if ( rc < CONVERT_REC_REJECT && ! deleteRec )
        {
            rc = Math.max(rc, validate852(recNbr, s901ab, loctab));
            if ( rc < CONVERT_REC_REJECT )
            {
                rc = Math.max(rc, validateLeaderType(recNbr, s901ab));
                rc = Math.max(rc, validateBibLevel(recNbr, s901ab));
                rc = Math.max(rc, validate245(recNbr, s901ab));
            }
        }

        return rc;
    }


    /**
     * Check the record has a valid leader type, if not report it in a 998.
     *
     * @param recNbr the current input record number
     * @param s901ab the 901 $a and $b subfields in a <code>String</code> array
     *
     * @return Status code indicating either success or error
     *
     * @see org.cdlib.marcconvert.ConvertConstants#CONVERT_REC_SUCCESS
     * @see org.cdlib.marcconvert.ConvertConstants#CONVERT_REC_ERROR
     */
    protected int validateLeaderType(int recNbr, String[] s901ab)
    {
        int rc = CONVERT_REC_SUCCESS;
        if ( ! outMarc.hasValidType() )
        {
            rc = CONVERT_REC_ERROR;
            make998(s901ab, ConvertMessages.ERR_CODE_INVALID_LEADER_TYPE,
					ConvertMessages.ERR_MSGL_INVALID_LEADER_TYPE + ' ' + outMarc.getRecordType());
        }

        return rc;
    }


    /**
     * Check the record has a valid bibliographic level, if not report it in a 998.
     *
     * @param recNbr the current input record number
     * @param s901ab the 901 $a and $b subfields in a <code>String</code> array
     *
     * @return Status indicating either success or error
     *
     * @see org.cdlib.marcconvert.ConvertConstants#CONVERT_REC_SUCCESS
     * @see org.cdlib.marcconvert.ConvertConstants#CONVERT_REC_ERROR
     */
    protected int validateBibLevel(int recNbr, String[] s901ab)
    {
        int rc = CONVERT_REC_SUCCESS;
        if ( ! outMarc.hasValidBibLevel() )
        {
            rc = CONVERT_REC_ERROR;
            make998(s901ab, ConvertMessages.ERR_CODE_INVALID_BIB_LEVEL,
					ConvertMessages.ERR_MSGL_INVALID_BIB_LEVEL + ' ' + outMarc.getBibLevel());
        }

        return rc;
    }


    /**
     * Check the record has a 005, if not create one using the filedate runtime
     * option. Unless the forcedate runtime option has been specified, then delete any
     * existing 005, and create a new one using the filedate runtime option.
     *
     * @param recNbr the current input record number
     * @param s901ab the 901 $a and $b subfields in a <code>String</code> array
     *
     * @return Status code which always indicates success
     *
     * @see org.cdlib.marcconvert.ConvertConstants#CONVERT_REC_SUCCESS
     */
    protected int validate005(int recNbr, String[] s901ab)
    {
        int rc = CONVERT_REC_SUCCESS;
        boolean forcedate = config.getProperty("forcedate", "N").equalsIgnoreCase("Y");
        MarcFixedLengthField of = null;
        MarcFixedLengthField nf = null;

        if ( forcedate )
        {
            int dcnt = outMarc.deleteFields("005");
            nf = outMarc.getNewFixField("005", fileConvertDate);
            if ( log.isDebugEnabled() )
            {
                log.debug("forcedate = true fileConvertDate = " + fileConvertDate);
                log.debug("dcnt = " + dcnt + " new field = t(" + nf.tag() + ") v(" + nf.value() + ")");
            }
        }
        else
        {
            of = (MarcFixedLengthField)outMarc.getFirstField("005");
            if ( of == null )
            {
                nf = outMarc.getNewFixField("005", fileConvertDate);
            }
            if ( log.isDebugEnabled() )
            {
                log.debug("forcedate = false fileConvertDate = " + fileConvertDate);
                if ( of == null )
                {
                    log.debug("new field = t(" + nf.tag() + ") v(" + nf.value() + ")");
                }
                else
                {
                    log.debug("old field = t(" + of.tag() + ") v(" + of.value() + ")");
                }
            }
        }

        if ( log.isDebugEnabled() )
        {
            of = (MarcFixedLengthField)outMarc.getFirstField("005");
            log.debug("final field = t(" + of.tag() + ") v(" + of.value() + ")");
        }

        return rc;
    }


    /**
     * Check the record has a 245, if not report it in a 998.
     *
     * @param recNbr the current input record number
     * @param s901ab the 901 $a and $b subfields in a <code>String</code> array
     *
     * @return Status code indicating either success or error
     *
     * @see org.cdlib.marcconvert.ConvertConstants#CONVERT_REC_SUCCESS
     * @see org.cdlib.marcconvert.ConvertConstants#CONVERT_REC_ERROR
     */
    protected int validate245(int recNbr, String[] s901ab)
    {
        int rc = CONVERT_REC_SUCCESS;

        if ( outMarc.getFirstField("245") == null )
        {
            rc = CONVERT_REC_ERROR;
            make998(s901ab, ConvertMessages.ERR_CODE_NO_245_FIELD,
					ConvertMessages.ERR_MSGL_NO_245_FIELD);
        }

        return rc;
    }


    /**
     * Check the record has a 852, if not report it in a 998.
     *
     * @param recNbr the current input record number
     * @param s901ab the 901 $a and $b subfields in a <code>String</code> array
     *
     * @return Status code indicating either success, error, or reject
     *
     * @see org.cdlib.marcconvert.ConvertConstants#CONVERT_REC_SUCCESS
     * @see org.cdlib.marcconvert.ConvertConstants#CONVERT_REC_ERROR
     * @see org.cdlib.marcconvert.ConvertConstants#CONVERT_REC_REJECT
     */
    protected int validate852(int recNbr, String[] s901ab, LocationTable loctab)
    {
        int           rc  = CONVERT_REC_SUCCESS;
        MarcFieldList mfl = outMarc.getFields(852, 852);

        if ( ! exists(mfl) )
        {
            rc = CONVERT_REC_REJECT;
            statusMsg = ConvertMessages.ERR_MSGS_NO_LOCATION_CODE;
        }
        else
        {
            int mflSize = mfl.size();
            for ( int i = 0; i < mflSize; i++ )
            {
                MarcVblLengthField f852 = (MarcVblLengthField)mfl.elementAt(i);
                Vector aSubFields = f852.subfields("a", true);
                Vector bSubFields = f852.subfields("b", true);
                int    aSubCount  = 0;
                int    bSubCount  = 0;
                String aSubValue  = null;
                String bSubValue  = null;

                /*
                 * Make sure we have exactly one $a subfield, and it is non-empty.
                 */
                if ( aSubFields == null || (aSubCount = aSubFields.size()) == 0 )
                {
                    rc = Math.max(rc, CONVERT_REC_REJECT);
                    statusMsg = "852 field has no $a subfield";
                    break;
                }
                else if ( aSubCount > 1 )
                {
                    rc = Math.max(rc, CONVERT_REC_REJECT);
                    statusMsg = "852 field has more than one $a subfield";
                    break;
                }
                else if ( (aSubValue = ((MarcSubfield)aSubFields.elementAt(0)).value()) == null
                          || aSubValue.length() == 0 )
                {
                    rc = Math.max(rc, CONVERT_REC_REJECT);
                    statusMsg = "852 subfield $a is empty";
                    break;
                }
                /*
                 * Make sure we have at leaset one $b subfield, and it is non-empty.
                 */
                else if ( bSubFields == null || (bSubCount = bSubFields.size()) == 0 )
                {
                    rc = Math.max(rc, CONVERT_REC_REJECT);
                    statusMsg = "852 field has no $b subfield";
                    break;
                }
                else if ( (bSubValue = ((MarcSubfield)bSubFields.elementAt(0)).value()) == null
                          || bSubValue.length() == 0 )
                {
                    rc = Math.max(rc, CONVERT_REC_REJECT);
                    statusMsg = "852 subfield $b is empty";
                    break;
                }
                /*
                 * We have a good 852, so check the location using the $a and $b subfields.
                 */
                else
                {
                    rc = Math.max(rc, validate852Location(recNbr, s901ab, loctab, aSubValue, bSubValue));
                }
            }
        }

        return rc;
    }


    /**
     * Check the record has a valid 901, if not report it in a 998.
     * Accept the field if the location table is null, since we take
     * that to mean location validation is to be bypassed.
     *
     * @param recNbr the current input record number
     * @param s901ab the 901 $a and $b subfields in a <code>String</code> array
     * @param loctab the location table to use for validation
     * @param s852a  the location from the 852 $a subfield
     * @param s852b  the sublocation from the 852 $b subfield
     *
     * @return Status code indicating success or error.
     *
     * @see org.cdlib.marcconvert.ConvertConstants#CONVERT_REC_SUCCESS
     * @see org.cdlib.marcconvert.ConvertConstants#CONVERT_REC_ERROR
     */
    protected int validate852Location(int recNbr,
                                      String[] s901ab,
                                      LocationTable loctab,
                                      String s852a,
                                      String s852b)
    {
        int rc = CONVERT_REC_SUCCESS;

        if ( log.isDebugEnabled() )
        {
            log.debug("rec# " + recNbr + " - s901a " + s901ab[0] + " - s901b = '" + s901ab[1]
                      + "'; s852a = '" + s852a + "'; s852b = '" + s852b + "'");
        }

        if ( loctab != null )
        {
            StringBuffer sb = new StringBuffer(30);

            if ( s852a != null)
            {
                sb.append(s852a.trim());
            }

            if ( s852b != null)
            {
                sb.append(s852b.trim());
            }

            String key = sb.toString();

            if ( log.isDebugEnabled() )
            {
                log.debug("location key = '" + key + "'");
            }

            if ( ! loctab.exists(key) )
            {
                 rc = CONVERT_REC_ERROR;
                 make998(s901ab, ConvertMessages.ERR_CODE_UNKNOWN_LOCATION,
						 ConvertMessages.ERR_MSGL_UNKNOWN_LOCATION + ' ' + s852b);
            }
            else
            {
                if ( log.isDebugEnabled() )
                {
                    log.debug("location = '" + loctab.lookup(key).toString() + "'");
                }
            }
        }

        return rc;
    }


    /**
     * Check the record has a valid 901, if not report it in a 998.
     * If the 901 is valid, put the $a and $b subfields in the the array
     * specifed in the second parmaeter.
     *
     * @param recNbr the current input record number
     * @param s901ab an empty <code>String</code> array to hold the 901 $a and $b subfields
     *
     * @return Status code indicating success or reject.
     *
     * @see org.cdlib.marcconvert.ConvertConstants#CONVERT_REC_SUCCESS
     * @see org.cdlib.marcconvert.ConvertConstants#CONVERT_REC_REJECT
     */
    protected int validate901(int recNbr, String[] s901ab)
    {
        int rc = CONVERT_REC_SUCCESS;
        MarcFieldList mfl = outMarc.getFields(901, 901);

        if ( ! exists(mfl) )
        {
            rc = CONVERT_REC_REJECT;
            statusMsg = "901 field is missing";
        }
        else
        {
            if ( mfl.size() > 1 )
            {
                rc = CONVERT_REC_REJECT;
                statusMsg = "More than one 901 field in output record";
            }
            else
            {
                MarcVblLengthField f901 = (MarcVblLengthField)mfl.elementAt(0);
                Vector aSubFields = f901.subfields("a", true);
                Vector bSubFields = f901.subfields("b", true);
                int    aSubCount  = 0;
                int    bSubCount  = 0;
                String aSubValue  = null;
                String bSubValue  = null;

                /*
                 * Make sure we have exactly one $a subfield, and it is non-empty.
                 */
                if ( aSubFields == null || (aSubCount = aSubFields.size()) == 0 )
                {
                    rc = CONVERT_REC_REJECT;
                    statusMsg = "901 field has no $a subfield";
                }
                else if ( aSubCount > 1 )
                {
                    rc = CONVERT_REC_REJECT;
                    statusMsg = "901 field has more than one $a subfield";
                }
                else if ( (aSubValue = ((MarcSubfield)aSubFields.elementAt(0)).value()) == null
                          || aSubValue.length() == 0 )
                {
                    rc = CONVERT_REC_REJECT;
                    statusMsg = "901 subfield $a is empty";
                }
                /*
                 * Make sure we have exactly one $b subfield, and it is non-empty.
                 */
                else if ( bSubFields == null || (bSubCount = bSubFields.size()) == 0)
                {
                    rc = CONVERT_REC_REJECT;
                    statusMsg = "901 field has no $b subfield";
                }
                else if ( bSubCount > 1 )
                {
                    rc = CONVERT_REC_REJECT;
                    statusMsg = "901 field has more than one $b subfield";
                }
                else if ( (bSubValue = ((MarcSubfield)bSubFields.elementAt(0)).value()) == null
                          || bSubValue.length() == 0 )
                {
                    rc = CONVERT_REC_REJECT;
                    statusMsg = "901 subfield $b is empty";
                }
                /*
                 * We have a good 901, so set the $a and $b in the caller's array
                 * and exit with a good return code.
                 */
                else
                {
                    rc = CONVERT_REC_SUCCESS;
                    s901ab[0] = aSubValue;
                    s901ab[1] = bSubValue;
                }
            }
        }

        return rc;
    }


    /**
     * Returns true if specified <code>String</code> is not null and not empty.
     */
    protected boolean exists(String sfval)
    {
        return ( sfval != null && sfval.trim().length() > 0 );
    }


    /**
     * Returns true if the specified <code>MarcSubfield</code> is not null and
     * the value of the specified <code>MarcSubfield</code> is not null and not empty.
     */
    protected boolean exists(MarcSubfield sf)
    {
        return ( sf != null && exists(sf.value()) );
    }


    /**
     * Returns true if the value of the specified <code>MarcFieldList</code>
     * is not null and not empty.
     */
    protected boolean exists(MarcFieldList mfl)
    {
        return ( mfl != null && mfl.size() > 0 );
    }

}
