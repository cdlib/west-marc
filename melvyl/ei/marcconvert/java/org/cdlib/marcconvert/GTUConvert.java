package org.cdlib.marcconvert;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.apache.regexp.RE;
import org.apache.regexp.RESyntaxException;

import org.cdlib.util.marc.MarcException;
import org.cdlib.util.marc.MarcFieldList;
import org.cdlib.util.marc.MarcRecord;
import org.cdlib.util.marc.MarcSubfield;
import org.cdlib.util.marc.MarcVblLengthField;
import org.cdlib.util.string.StringUtil;


/**
 * Convert the marc records from the GTU source. GTU is an III source,
 * so the functionality common to all III source convertison may be found
 * in the IIIConvert class, which is the super classs of this class.
 *
 * @author <a href="mailto:gmills@library.berkeley.edu">Garey Mills</a>
 * @author <a href="mailto:shawnm@splorkin.com">Shawn McGovern</a>
 * @author <a href="mailto:mark.reyes@ucop.edu">Mark Reyes</a>
 * @version $Id: GTUConvert.java,v 1.8 2003/03/04 19:43:52 mreyes Exp $
 */
public class GTUConvert extends IIIConvert
{
	/**
	 * log4j Logger for this class.
	 */
    private static Logger log = Logger.getLogger(GTUConvert.class);

	/**
	 * CVS header string.
	 */
    public static final String cvsHeader = "$Header: /cvs/root/melvyl/ei/marcconvert/java/org/cdlib/marcconvert/GTUConvert.java,v 1.8 2003/03/04 19:43:52 mreyes Exp $";

	/**
	 * CVS version string.
	 */
    public static final String version = "$Revision: 1.8 $";

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
	 * Regular expression to split out number of copies
	 */
    private String numberOfCopiesRegexpString
        = new String("\\s*\\(\\d+\\)\\s*(\\w+)");

	/**
	 * Default holdings literal
	 */
    private String defaultHoldingsInformation
        = new String("For holdings consult library");

	/**
	 * Regular expression to parse the call number suffix
	 */
    private String callNumberSuffixRegexpString
        = new String("SIZE (1|2|3|9|t|fff|ff|f)");

	/**
	 * Regular expression to parse the monograph summary holdings
	 */
    private String monographSummaryHoldingsRegexpString
        = new String("^\\s*v\\.|has (n|v)|have v|has: ");

	/**
	 * Regular expression to parse serial summary holdings in the 905 $b
	 */
    private String serialSummaryHoldingsRegexpString
        = new String("^\\s*v\\.|has (n|v|18|19|20)|have (v|18|19|20)|has: ");

	/**
	 * Regular expression to identify latest summary holdings in the 905 $b
	 */
    private String serialLatestIssueRegexpString
        = new String("current|latest");

    /**
     * Instantiate a new GTUConvert object. Instantiates the IIIConvert
     * super class as a GTU conversion.
     */
    public GTUConvert()
    {
        super(IIIConvert.GTU);
    }


    /**
     * Convert the given marc record. Place the result into the given output
     * marc record parameter variable. The status of the conversion is returned.
     *
     * @param inRec the <code>MarcRecord</code> to convert
     * @param outRec the converted <code>MarcRecord</code>
     * @return the conversion status code
     * @see org.cdlib.marcconvert.ConvertConstants
     */
    public int convert(MarcRecord inRec, MarcRecord outRec)
    {
        if ( inRec == null )
        {
            throw new MarcException(this, "Converter received null input MarcRecord");
        }

        if ( outRec == null )
        {
            throw new MarcException(this, "Converter received null output MarcRecord");
        }

        inMarc = inRec;
        outMarc = outRec;
        reset();
        return convert();
    }


    /**
     * Convert the current marc record. Place the result into the output
     * marc record class variable. The status of the conversion is returned.
     * @return the status code
     * @see org.cdlib.marcconvert.ConvertConstants
     */
    public int convert()
    {
        int rc = CONVERT_REC_SUCCESS;

        // Set default debug level for UCSDConvert
        setDebug(0);

        // Set status for this record
        setStatus(OK);

        // Verify record source
        if (!verifyCatalogingSource())
        {
            throw new MarcDropException("Cataloging source not GTU");
        }

        // Move the input leader to output
        moveLeader();

        // Move all fields within 000 to 899 to output
        moveOut(0, 899);

        //delete any 852 fields
        outMarc.deleteFields("852");

        // Normalize the leader
        normalizeLeader();

        // Normalize the bibliographic fields
        normalizeBibFields();

        // Normalize the holdings fields
        normalizeHoldingsFields();

        debugOut(2,"process output leader:" + outMarc.getLeaderValue());

        return rc;
    }


    /**
     * Normalizes the record leader in the output record.
     * Presumes that the caller has moved the input
     * leader to output.
     * Note: This method overrides the parent in IIIConvert.
     *       Reason being, for a slight change to the GTU
     *       source there is no reason to make a change that
     *       will affect all III sources.
     */
    protected void normalizeLeader()
    {
        RE deleteRE = null;
        // regexp for all delete cases for 901 $c
        //     Case sensitive: d,n,b,o,p,q,r,s,t,u,v,w,x,y
        String deleteCase = "[dnbopqrstuvwxy]";
        try {
           deleteRE = new RE(deleteCase);
        }
        catch (RESyntaxException rese) {
          throw new MarcDropException("901 $c - Regular Expression Error: " + rese);
        }

        // Determine whether this is a delete record

        String v901c = inMarc.getFirstValue("901", "c");
        boolean isDelete = false;
        if (deleteRE.match(v901c))
        {
            isDelete = true;
        }

        // Normalize the STATUS byte of the leader

        if ( isDelete )
        {
            outMarc.setRecordStatus("d");
        }

        // Normalize the TYPE byte in the leader

        if ( inMarc.getRecordType().equalsIgnoreCase("h"))
        {
            outMarc.setRecordType("a");
        }
    }


    /**
     * Create the 852 fields from the first 920 field. If no 920 fields are
     * found in the input, no 852 fields are created. After the 852 fields
     * have been created, handle non-public notes and summary holdings
     * conditions.
     */
    private void normalizeHoldingsFields()
    {
        Vector v852s = construct852sFromFirst920(); //5.2.2.1, 5.2.2.2, 5.2.2.3, 5.2.2.6, 5.2.2.8
        if (v852s != null)
        {
            handleNonPublicNotes(v852s); //5.2.2.10
            handleSummaryHoldings(v852s); //5.2.2.12
        }
    }


    /**
     * Create the 852 fields from the first 920 field. If no 920 fields are
     * found in the input, no 852 fields are created.
	 *
	 * @return a <code>Vector</code> of 852 field objects.
     */
    private Vector construct852sFromFirst920()
    {
        MarcFieldList v920s = inMarc.allFields("920");
        Vector v852s = new Vector();
        Vector locValues = new Vector();
        MarcVblLengthField f920 = null;

        if (v920s == null || v920s.size() == 0)
        {
            return null;
        }

        f920 = (MarcVblLengthField)v920s.elementAt(0);

        //5.2.2.3
        Vector locs = f920.subfield("l");
        Enumeration locsEnum = locs.elements();
        while (locsEnum.hasMoreElements())
        {
            MarcSubfield msf = (MarcSubfield)locsEnum.nextElement();
            String loc = cleanLoc(msf.value());
            if(loc.equalsIgnoreCase("gts"))
            { //5.2.2.1
                continue;
            }
            locValues.add(loc);
        }

        if (locValues.size() > 0)
        {
            Iterator it = locValues.iterator();
            while (it.hasNext())
            {
                String loc = (String)it.next();
                MarcVblLengthField target = outMarc.getNewVblField("852", "  ");
                target.addSubfield("a", "GTB"); //5.2.2.2
                target.addSubfield("b", loc);
                v852s.addElement(target);
            }
        }
        else
        {
            return null;
        }

        handleCallNumber(f920, v852s);
        return v852s;
    }


	/**
	 * Fill in the $h, $i, and $j, subfields for each of the 852 fields.
	 *
	 * See sections 5.2.2.6 and 5.2.2.8 of the spec for details.
	 *
	 * @param f920 the 920 field to use
	 * @param v852s the <code>Vector</code> of 852 fields to process
	 */
    private void handleCallNumber(MarcVblLengthField f920, Vector v852s)
    {
        Vector asfs = f920.subfield("a");
        Vector bsfs = f920.subfield("b");
        Vector aValues = new Vector();
        Vector bValues = new Vector();
        MarcSubfield asf = null;
        MarcSubfield bsf = null;
        String asfv = null;
        String bsfv = null;
        int i = 0;

        if (asfs.size() == 0 && bsfs.size() == 0)
        {
            return;
        }
        else
        {
            if (asfs.size() > 0)
            {
                Iterator itA = asfs.iterator();
                String asfValue = null;
                while(itA.hasNext())
                {
                    asf = (MarcSubfield)itA.next();
                    if(exists(asf) && !((asfValue = asf.value()).equals("")))
                    {
                        aValues.addElement(asfValue);
                    }
                }
            }

            if (bsfs.size() == 1)
            {
                String bsfValue = null;
                bsf = (MarcSubfield)bsfs.elementAt(0);
                if(exists(bsf) && !((bsfValue = bsf.value()).equals("")))
                {
                    bValues.addElement(bsfValue);
                }
            }

            if (aValues.size() > 1 || (aValues.size() == 0 || bValues.size() == 0))
            {
                StringBuffer sb = new StringBuffer();
                if (aValues.size() > 0) {
                    Iterator itAsfvs = aValues.iterator();
                    while (itAsfvs.hasNext())
                    {
                        asfv = (String)itAsfvs.next();
                        if (i == 0)
                        {
                            sb.append(asfv);
                            i++;
                        }
                        else
                        {
                            sb.append(" " + asfv);
                        }
                    }
                }

                if (bValues.size() > 0)
                { //Assume that there will be only one
                    bsfv = (String)bValues.elementAt(0);
                    if (sb.length() > 0)
                    {
                        sb.append(" " + bsfv);
                    }
                    else
                    {
                        sb.append(bsfv);
                    }
                }

                if (sb.length() > 0)
                {
                    Iterator it852 = v852s.iterator();
                    String jValue = handleCallNumberSuffix(sb.toString(), v852s);
                    while (it852.hasNext())
                    {
                        MarcVblLengthField f852 = (MarcVblLengthField)it852.next();
                        f852.addSubfield("j", jValue);
                    }
                }
            }
            else
            { //one a and one b
                asfv = handleCallNumberSuffix((String)aValues.elementAt(0), v852s);
                bsfv = handleCallNumberSuffix((String)bValues.elementAt(0), v852s);
                Iterator it852 = v852s.iterator();
                while (it852.hasNext())
                {
                    MarcVblLengthField f852 = (MarcVblLengthField)it852.next();
                    f852.addSubfield("h", asfv);
                    f852.addSubfield("i", bsfv);
                }
            }

        }
    }


    /**
     * Create the 852 $m subfield for each 852 using the supplied string.
     * The string is matched against a regular expression, and when a match
     * is found the $m subfield is set to the matching portion, then the
     * matching portion is removed and this method is invoked recursively
     * with the remainder, until no match is found.
     *
	 * See section 5.2.2.8 of the spec for details.
     *
     * @param cn the call number string
	 * @param v852s the <code>Vector</code> of 852 fields to process
     */
    private String handleCallNumberSuffix(String cn, Vector v852s)
    {
        RE callNumberSuffixRegexp = null;

        try
        {
            callNumberSuffixRegexp
                = new RE(callNumberSuffixRegexpString, RE.MATCH_CASEINDEPENDENT);
        }
        catch (RESyntaxException rese)
        {
            log.error("RESyntaxException: " + rese.getMessage(), rese);
        }

        if (callNumberSuffixRegexp.match(cn))
        {
            cn = cn.substring(0, callNumberSuffixRegexp.getParenStart(0))
                + cn.substring(callNumberSuffixRegexp.getParenEnd(0), cn.length());
            Iterator it852 = v852s.iterator();
            while (it852.hasNext())
            {
                MarcVblLengthField f852 = (MarcVblLengthField)it852.next();
                f852.addSubfield("m", callNumberSuffixRegexp.getParen(0));
            }

            //This recursive call should handle all suffixes found
            cn = handleCallNumberSuffix(cn, v852s);
        }
        return cn;
    }


    /**
     * Create 852 $x subfields from each 590 $a subfield.
     *
	 * See section 5.2.2.10 of the spec for details.
	 *
	 * @param v852s the <code>Vector</code> of 852 fields to process
     */
    private void handleNonPublicNotes(Vector v852s)
    {
        MarcFieldList v590s = inMarc.allFields("590");
        Vector asfs = null;
        Iterator it852s = null;
        Iterator itAsfs = null;
        MarcSubfield asf = null;
        MarcVblLengthField f852 = null;

        Enumeration e590 = v590s.elements();
        while (e590.hasMoreElements())
        {
            MarcVblLengthField f590 = (MarcVblLengthField)e590.nextElement();
            asfs = f590.subfield("a");
            if (asfs != null)
            {
                itAsfs = asfs.iterator();
                while (itAsfs.hasNext())
                {
                    asf = (MarcSubfield)itAsfs.next();
                    if (exists(asf))
                    {
                        it852s = v852s.iterator();
                        while (it852s.hasNext())
                        {
                            f852 = (MarcVblLengthField)it852s.next();
                            f852.addSubfield("x", asf.value());
                        }
                    }
                }
            }
        }
    }


	/**
	 * Set summary holding information in the 852 fields.
	 *
	 * @param v852s the <code>Vector</code> of 852 fields to process
	 */
    private void handleSummaryHoldings(Vector v852s)
    {
        if (inMarc.isMonograph())
        {
            handleMonographSummaryHoldings(v852s);
        }
        else if (inMarc.isSerial())
        {
            handleSerialSummaryHoldings(v852s);
        }
        else
        {
            //?
        }
    }


	/**
	 * Set monograph summary holding information in the 852 $3 subfields
	 * from the 934 $e subfields.
	 *
	 * @param v852s the <code>Vector</code> of 852 fields to process
	 */
    private void handleMonographSummaryHoldings(Vector v852s)
    {
        RE monographSummaryHoldingsRegexp = null;
        MarcFieldList v934s = inMarc.allFields("934");
        MarcVblLengthField f934 = null;

        if (v934s.size() == 0)
        {
            return;
        }

        try
        {
            monographSummaryHoldingsRegexp
                = new RE(monographSummaryHoldingsRegexpString, RE.MATCH_CASEINDEPENDENT);
        }
        catch(RESyntaxException rese)
        {
            log.error("RESyntaxException: " + rese.getMessage(), rese);
        }

        f934 = (MarcVblLengthField)v934s.elementAt(0);
        Vector asfs = f934.subfield("a");
        Iterator itAsfs = asfs.iterator();
        while (itAsfs.hasNext())
        {
            MarcSubfield asf = (MarcSubfield)itAsfs.next();
            if (exists(asf))
            {
                if (monographSummaryHoldingsRegexp.match(asf.value()))
                {
                    ((MarcVblLengthField)v852s.elementAt(0)).addSubfield("3", asf.value());
                    break;
                }
            }
        }
    }


	/**
	 * Set summary holding information in the 852 $3 from the 950 $b.
	 *
	 * @param v852s the <code>Vector</code> of 852 fields to process
	 */
    private void handleSerialSummaryHoldings(Vector v852s)
    {
        RE serialSummaryHoldingsRegexp = null;
        RE serialLatestIssueRegexp = null;
        MarcFieldList v950s = inMarc.allFields("950");
        Iterator it852 = null;
        MarcSubfield bsf = null;
        MarcVblLengthField f852 = null;
        String bValue = null;

        if (v950s.size() == 0)
        {
            for (int i = 0;i < v852s.size();i++)
            {
                f852 = (MarcVblLengthField)v852s.elementAt(i);
                f852.addSubfield("3", defaultHoldingsInformation);
            }
            return;
        }

        try
        {
            serialSummaryHoldingsRegexp
                = new RE(serialSummaryHoldingsRegexpString, RE.MATCH_CASEINDEPENDENT);
            serialLatestIssueRegexp
                = new RE(serialLatestIssueRegexpString, RE.MATCH_CASEINDEPENDENT);
        }
        catch (RESyntaxException rese)
        {
            log.error("RESyntaxException: " + rese.getMessage(), rese);
        }

        int i = 0;
        MarcVblLengthField f950 = (MarcVblLengthField)v950s.elementAt(0);
        Vector bsfs = f950.subfield("b");
        Iterator itBsf = bsfs.iterator();
        while (itBsf.hasNext())
        {
            bsf = (MarcSubfield)itBsf.next();
            log.debug("bsf value: " + bsf.value());

            if (exists(bsf) && serialLatestIssueRegexp.match(bsf.value()))
            {
                log.debug("bsf value matched serialLatest: " + bsf.value());
                if (serialSummaryHoldingsRegexp.match(bsf.value()))
                {
                    log.debug("bsf value matched serialLatest & serialSummary: " + bsf.value());
                    ((MarcVblLengthField)v852s.elementAt(0)).addSubfield("3", bsf.value());
                    for (int j = 1;j < v852s.size();j++)
                    {
                        f852 = (MarcVblLengthField)v852s.elementAt(j);
                        f852.addSubfield("3", defaultHoldingsInformation);
                    }
                    return;
                }
                else
                {
                    ((MarcVblLengthField)v852s.elementAt(0)).addSubfield("m", bsf.value());
                    i = 1;
                    break;
                }
            }
        }

        itBsf = bsfs.iterator();
        while (itBsf.hasNext())
        {
            bsf = (MarcSubfield)itBsf.next();
            if (exists(bsf) && serialSummaryHoldingsRegexp.match(bsf.value()))
            {
                log.debug("bsf value matched serialSummary: " + bsf.value());
                ((MarcVblLengthField)v852s.elementAt(0)).addSubfield("3", bsf.value());
                i = 1;
                break;
            }
        }

        for (;i < v852s.size();i++)
        {
            f852 = (MarcVblLengthField)v852s.elementAt(i);
            f852.addSubfield("3", defaultHoldingsInformation);
        }
        return;
    }

	/**
	 * Extract the text following a number of copies from a location string.
	 * Matches: (14)someloc and returns 'someloc'.
	 *
	 * @param preLoc the location string to examine
	 * @return the text following a number of copies, if number of copies exists,
	 *         otherwise the original string
	 */
    private String cleanLoc(String preLoc)
    {
        try
        {
            RE numCopies = new RE(numberOfCopiesRegexpString);

            if (numCopies.match(preLoc))
            {
                return numCopies.getParen(1);
            }
            return preLoc;
        }
        catch (RESyntaxException rese)
        {
            log.error("RESyntaxException: " + rese.getMessage(), rese);
        }
        return preLoc;
    }

}
