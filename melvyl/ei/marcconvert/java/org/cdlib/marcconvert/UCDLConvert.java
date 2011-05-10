package org.cdlib.marcconvert;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.apache.regexp.RE;
import org.apache.regexp.RESyntaxException;

import org.cdlib.util.marc.MarcException;
import org.cdlib.util.marc.MarcFieldList;
import org.cdlib.util.marc.MarcRecord;
import org.cdlib.util.marc.MarcRecord;
import org.cdlib.util.marc.MarcSubfield;
import org.cdlib.util.marc.MarcVblLengthField;


/**
 * Convert the marc records from the UCD Law source. UCD Law is an III source,
 * so the functionality common to all III source convertison may be found
 * in the IIIConvert class, which is the super classs of this class.
 *
 * @author <a href="mailto:gmills@library.berkeley.edu">Garey Mills</a>
 * @author <a href="mailto:shawnm@splorkin.com">Shawn McGovern</a>
 * @version $Id: UCDLConvert.java,v 1.7 2002/10/31 02:22:34 smcgovrn Exp $
 */
public class UCDLConvert extends IIIConvert
{
	/**
	 * log4j Logger for this class.
	 */
    private static Logger log = Logger.getLogger(UCDLConvert.class);

	/**
	 * CVS header string.
	 */
    public static final String cvsHeader = "$Header: /cvs/root/melvyl/ei/marcconvert/java/org/cdlib/marcconvert/UCDLConvert.java,v 1.7 2002/10/31 02:22:34 smcgovrn Exp $";

	/**
	 * CVS version string.
	 */
    public static final String version = "$Revision: 1.7 $";

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
	 * Default holdings literal
	 */
    private String defaultHoldingsInfo           = new String("For holdings consult library");

	/**
	 * Regular expression to split out number of copies from the text.
	 * Matches: (14)someloc and isolates 'someloc'.
	 */
    private String numberOfCopiesRegexpString    = new String("\\s*\\(\\d+\\)\\s*(\\w+)");

	/**
	 * Regular expression to identify public notes
	 */
    private String publicNotesRegexpString       = new String("^\\s*shared purchase");

	/**
	 * Regular expression to identify a substring of '*' characters
	 */
    private String asteriskRegexpString          = new String("^(\\s|\\*)*(.*)");

	/**
	 * Regular expression to split out the content in a 'NRLF' string
	 */
    private String locationQualifierRegexpString = new String("^\\s*UCDL:NRLF:\\s*(.*)");

	/**
	 * Regular expression to identify an 'AT NRLF' string
	 */
    private String atNRLFString                  = new String("AT NRLF: ");

	/**
	 * Regular expression to split out the content in summary holdings string
	 * that begins with 'UCDL:'
	 */
    private String summaryHoldingsRegexpString   = new String("^UCDL:\\s*(.*)");

	/**
	 * Regular expression to identify a 'NRLF' string
	 */
    private String NRLFRegexpString              = new String("NRLF");


    /**
     * Instantiate a new UCDLConvert object. Instantiates the IIIConvert
     * super class as a UCDL conversion.
     */
    public UCDLConvert()
    {
        super(IIIConvert.UCDL);
    }


    /**
     * Convert the given marc record. Place the result into the given output
     * marc record parameter variable. The status of the conversion is returned.
     *
     * @param inRec  the <code>MarcRecord</code> to convert
     * @param outRec the converted <code>MarcRecord</code>
     *
     * @return       the conversion status code
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
     *
     * @return the conversion status code
     * @see org.cdlib.marcconvert.ConvertConstants
     */
    private int convert()
    {
        int rc = CONVERT_REC_SUCCESS;
        // Set default debug level for UCSDConvert
        setDebug(0);

        // Set status for this record
        setStatus(OK);

        // Verify record source
        if ( !verifyCatalogingSource() )
        {
            throw new MarcDropException("Cataloging source not UCDL");
        }

        // Move the input leader to output
        moveLeader();

        // Move all fields within 000 to 899 to output
        moveOut("000", "899");

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
	 * Prepare the 852 fields from the holdings information.
	 */
    private void normalizeHoldingsFields()
    {
        Vector v852s = construct852sFromFirst920(); //5.2.2.2, 5.2.2.3, 5.2.2.6

        if ( v852s != null && v852s.size() > 0 )
        {
            handleLocationQualifier(v852s); //5.2.2.5
            handleNotes(v852s); //5.2.2.10, 5.2.2.11
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
        HashSet locValues = new HashSet();

        if ( v920s != null && v920s.size() > 0 )
        {
            MarcVblLengthField f920 = (MarcVblLengthField)v920s.elementAt(0);
            Vector locs = f920.subfield("l");
            Enumeration locsEnum = locs.elements();

            while ( locsEnum.hasMoreElements() )
            {
                MarcSubfield msf = (MarcSubfield)locsEnum.nextElement();
                String loc = cleanLoc(msf.value());
                if ( locValues.add(loc) )
                {
                    MarcVblLengthField target = outMarc.getNewVblField("852", "  ");
                    target.addSubfield("a", "DLB"); //5.2.2.2
                    target.addSubfield("b", loc);
                    v852s.addElement(target);
                }
            }

            if ( v852s.size() > 0 )
            {
                handleCallNumber(f920, v852s);
            }
        }

        return v852s;
    }


	/**
	 * Fill in the $h, $i, and $j, subfields for each of the 852 fields.
	 *
	 * See section 5.2.2.6 of the spec for details.
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

        if ( asfs.size() != 0 || bsfs.size() != 0 )
        {
            if ( asfs.size() > 0 )
            {
                Iterator itA = asfs.iterator();
                while ( itA.hasNext() )
                {
                    asf = (MarcSubfield)itA.next();
                    if ( exists(asf) )
                    {
                        String asfValue = asteriskStrip(asf.value());
                        if ( asfValue.length() > 0 )
                        {
                            aValues.addElement(asfValue);
                        }
                    }
                }
            }

            if ( bsfs.size() == 1 )
            {
                bsf = (MarcSubfield)bsfs.elementAt(0);
                if ( exists(bsf) )
                {
                    String bsfValue = asteriskStrip(bsf.value());
                    if ( bsfValue.length() > 0 )
                    {
                        bValues.addElement(bsfValue);
                    }
                }
            }

            if ( aValues.size() > 1 || (aValues.size() == 0 || bValues.size() == 0) )
            {
                StringBuffer sb = new StringBuffer();
                if ( aValues.size() > 0 )
                {
                    Iterator itAsfvs = aValues.iterator();
                    while ( itAsfvs.hasNext() )
                    {
                        asfv = (String)itAsfvs.next();
                        if ( i == 0 )
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

                if ( bValues.size() > 0 )
                {   //Assume that there will be only one
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

                if ( sb.length() > 0 )
                {
                    Iterator it852 = v852s.iterator();
                    while (it852.hasNext())
                    {
                        MarcVblLengthField f852 = (MarcVblLengthField)it852.next();
                        f852.addSubfield("j", sb.toString());
                    }
                }
            }
            else
            {   //one a and one b
                asfv = (String)aValues.elementAt(0);
                bsfv = (String)bValues.elementAt(0);
                Iterator it852 = v852s.iterator();
                while ( it852.hasNext() )
                {
                    MarcVblLengthField f852 = (MarcVblLengthField)it852.next();
                    f852.addSubfield("h", asfv);
                    f852.addSubfield("i", bsfv);
                }
            }
        }
    }


	/**
	 * Get location qualifier information from the 934 fields in the input
	 * <code>MarcRecord</code> object to fill in the $g subfield for each
	 * of the supplied 852 field objects.
	 *
	 * See section 5.2.2.5 of the spec for details.
	 *
	 * @param v852s the <code>Vector</code> of 852 fields to process
	 */
    private void handleLocationQualifier(Vector v852s)
    {
        MarcFieldList v934s = inMarc.allFields("934");
        Enumeration e934 = v934s.elements();
        RE lqRegexp = null;
        String remainder = null;

        try
        {
            lqRegexp = new RE(locationQualifierRegexpString);
        }
        catch(RESyntaxException rese)
        {
            log.error("RESyntaxException: " + rese.getMessage(), rese);
        }

        while (e934.hasMoreElements())
        {
            Vector asfs = ((MarcVblLengthField)e934.nextElement()).subfield("a");
            if ( asfs == null || asfs.size() == 0 )
            {
                continue;
            }
            Iterator itAsf = asfs.iterator();
            while ( itAsf.hasNext() )
            {
                MarcSubfield asf = (MarcSubfield)itAsf.next();
                if ( exists(asf)
                     && lqRegexp.match(asf.value())
                     && (remainder = lqRegexp.getParen(1).trim()).length() > 0 )
                {
                    Iterator it852s = v852s.iterator();
                    while (it852s.hasNext())
                    {
                        MarcVblLengthField f852 = (MarcVblLengthField)it852s.next();
                        f852.addSubfield("g", atNRLFString + remainder);
                    }
                }
            }
        }
    }


	/**
	 * Get the notes from the 590 and 910 fields in the <code>MarcRecord</code> object.
	 * The note from the 590 fields go into with the $x or $z subfield of the 852,
	 * depending on whether the is public ($z), or not public ($x). The notes from
	 * the 910 fields always go into a $x subfield of the 852.
	 *
	 * See sections 5.2.2.10 and 5.2.2.11 of the spec for details.
	 *
	 * @param v852s the <code>Vector</code> of 852 fields to process
	 */
    private void handleNotes(Vector v852s)
    {
        RE publicNotesRegexp = null;
        MarcFieldList v590s = inMarc.allFields("590");
        MarcFieldList v910s = inMarc.allFields("910");
        Vector asfs = null;
        Iterator it852s = null;
        Iterator itAsfs = null;
        MarcSubfield asf = null;
        MarcVblLengthField f852 = null;

        try
        {
            publicNotesRegexp = new RE(publicNotesRegexpString, RE.MATCH_CASEINDEPENDENT);
        }
        catch (RESyntaxException rese)
        {
            System.out.println("RESyntaxException in UCDLConvert::handleNotes: " + rese);
        }

        Enumeration e590 = v590s.elements();
        while ( e590.hasMoreElements() )
        {
            MarcVblLengthField f590 = (MarcVblLengthField)e590.nextElement();
            asfs = f590.subfield("a");
            if ( asfs != null )
            {
                itAsfs = asfs.iterator();
                while ( itAsfs.hasNext() )
                {
                    asf = (MarcSubfield)itAsfs.next();
                    if ( exists(asf) )
                    {
                        if ( publicNotesRegexp.match(asf.value()) )
                        {
                            it852s = v852s.iterator();
                            while ( it852s.hasNext() )
                            {
                                f852 = (MarcVblLengthField)it852s.next();
                                f852.addSubfield("z", asf.value());
                            }
                        }
                        else
                        {
                            it852s = v852s.iterator();
                            while ( it852s.hasNext() )
                            {
                                f852 = (MarcVblLengthField)it852s.next();
                                f852.addSubfield("x", asf.value());
                            }
                        }
                    }
                }
            }
        }

        Enumeration e910 = v910s.elements();
        while ( e910.hasMoreElements() )
        {
            MarcVblLengthField f910 = (MarcVblLengthField)e910.nextElement();
            asfs = f910.subfield("a");
            if ( asfs != null )
            {
                itAsfs = asfs.iterator();
                while ( itAsfs.hasNext() )
                {
                    asf = (MarcSubfield)itAsfs.next();
                    if ( exists(asf) )
                    {
                        it852s = v852s.iterator();
                        while ( it852s.hasNext() )
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
        if ( isMonograph() )
        {
            handleMonographSummaryHoldings(v852s);
        }
        else
        {
            handleSerialSummaryHoldings(v852s);
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
        RE lqRegexp = null;
        RE summaryHoldingsRegexp = null;
        MarcFieldList v934s = inMarc.allFields("934");

        try
        {
            summaryHoldingsRegexp = new RE(summaryHoldingsRegexpString);
            lqRegexp = new RE(locationQualifierRegexpString);
        }
        catch(RESyntaxException rese)
        {
            log.error("RESyntaxException: " + rese.getMessage(), rese);
        }

        Enumeration e934 = v934s.elements();
        while ( e934.hasMoreElements() )
        {
            MarcVblLengthField f934 = (MarcVblLengthField)e934.nextElement();
            Vector asfs = f934.subfield("a");
            MarcSubfield asf = (MarcSubfield)asfs.elementAt(0);
            if ( exists(asf) )
            {
                if ( lqRegexp.match(asf.value()) )
                {
                    continue;
                }
                else
                {
                    String aValue = asf.value().trim();
                    if ( aValue.equalsIgnoreCase("UCDL:") )
                    {
                        if ( asfs.size() > 1
                             && exists((asf = (MarcSubfield)asfs.elementAt(1))) )
                        {
                            ((MarcVblLengthField)v852s.elementAt(0)).addSubfield("3", asf.value());
                            break;
                        }
                        else
                        {
                            continue;
                        }
                    }
                    else if ( summaryHoldingsRegexp.match(aValue))
                    {
                        ((MarcVblLengthField)v852s.elementAt(0)).addSubfield("3", summaryHoldingsRegexp.getParen(1));
                        break;
                    }
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
        RE lqRegexp = null;
        RE summaryHoldingsRegexp = null;
        RE nrlfRegexp = null;
        MarcFieldList v950s = inMarc.allFields("950");
        Iterator it852 = null;
        MarcSubfield bsf = null;
        MarcVblLengthField f852 = null;
        String bValue = null;

        try
        {
            summaryHoldingsRegexp = new RE(summaryHoldingsRegexpString);
            lqRegexp = new RE(locationQualifierRegexpString);
            nrlfRegexp = new RE(NRLFRegexpString, RE.MATCH_CASEINDEPENDENT);
        }
        catch (RESyntaxException rese)
        {
            log.error("RESyntaxException: " + rese.getMessage(), rese);
        }

        Enumeration e950 = v950s.elements();
        while ( e950.hasMoreElements() )
        {
            MarcVblLengthField f950 = (MarcVblLengthField)e950.nextElement();
            Vector bsfs = f950.subfield("b");
            Iterator itBsf = bsfs.iterator();
            while ( itBsf.hasNext() )
            {
                MarcSubfield bsf_temp = (MarcSubfield)itBsf.next();
                if ( exists(bsf_temp) )
                {
                    bsf = bsf_temp;
                    break;
                }
            }
            if ( bsf != null )
            {
                break;
            }
        }

        if ( log.isDebugEnabled() )
        {
            log.debug("950 $b = '" + (bsf == null ? "null" : bsf.value()) + "'");
        }

        if ( bsf == null )
        {
            it852 = v852s.iterator();
            while ( it852.hasNext() )
            {
                f852 = (MarcVblLengthField)it852.next();
                f852.addSubfield("3", defaultHoldingsInfo);
            }
        }
        else
        {
            if ( nrlfRegexp.match(bsf.value()))
            {
                if ( lqRegexp.match(bsf.value()) )
                {
                    bValue = lqRegexp.getParen(1);
                }
                else if ( summaryHoldingsRegexp.match(bsf.value()) )
                {
                    bValue = summaryHoldingsRegexp.getParen(1);
                }
                else
                {
                    bValue = bsf.value().trim();
                }

                String firstLoc = ((MarcVblLengthField)v852s.elementAt(0)).firstSubfieldValue("b");

                if ( log.isDebugEnabled() )
                {
                    log.debug("$b is nrlf - bValue = '" + bValue
                              + "' firstLoc = '" + firstLoc + "'");
                }

                if ( firstLoc.equalsIgnoreCase("nrlf") )
                {
                    ((MarcVblLengthField)v852s.elementAt(0)).addSubfield("3", bValue);
                }
                else
                {
                    ((MarcVblLengthField)v852s.elementAt(0)).addSubfield("3", defaultHoldingsInfo);
                }

            }
            else
            {
                if ( summaryHoldingsRegexp.match(bsf.value()) )
                {
                    bValue = summaryHoldingsRegexp.getParen(1);
                }
                else
                {
                    bValue = bsf.value().trim();
                }
                ((MarcVblLengthField)v852s.elementAt(0)).addSubfield("3", bValue);
            }
        }

        // All but the first 852 get the default holdings string
        int max = v852s.size();
        for ( int i = 1; i < max; i++ )
        {
            ((MarcVblLengthField)v852s.elementAt(i)).addSubfield("3", defaultHoldingsInfo);
        }
    }

	/**
	 * Strip leading asterisks and white space from a string.
	 * E.g. ' *** *.thingy' -> '.thingy'
	 *
	 * @param pre the string to strip
	 * @return the stripped string
	 */
    private String asteriskStrip(String pre)
    {
        RE asteriskRegexp = null;
        String post = null;
        String sRet = null;

        try
        {
            asteriskRegexp = new RE(asteriskRegexpString);
            if ( asteriskRegexp.match(pre) )
            {
                post = asteriskRegexp.getParen(2);
            }
            else
            {
                post = pre;
            }

            int i = post.length() - 1;
            for ( ; i >= 0; i-- )
            {
                if ( post.charAt(i) != ' ' && post.charAt(i) != '*')
                {
                    break;
                }
            }
            sRet = post.substring(0, i + 1);
        }
        catch(RESyntaxException rese)
        {
            log.error("RESyntaxException: " + rese.getMessage(), rese);
        }

        return sRet;
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
        String sRet = preLoc;

        try
        {
            RE numCopies = new RE(numberOfCopiesRegexpString);

            if ( numCopies.match(preLoc) )
            {
                sRet = numCopies.getParen(1);
            }
        }
        catch(RESyntaxException rese)
        {
            log.error("RESyntaxException: " + rese.getMessage(), rese);
        }
        return sRet;
    }

	/**
	 * main method provided to unit test the utility methods.
	 */
	public static void main(String[] args)
	{
		UCDLConvert c = new UCDLConvert();

		// test cleanLoc
		String s1 = "(14) copies";
		String s2 = c.cleanLoc(s1);
		System.out.println("cleanLoc: in = " + s1 + " out = " + s2);

		// test asteriskStrip
		String s3 = " ***.thingy";
		String s4 = c.asteriskStrip(s3);
		System.out.println("asteriskStrip: in = '" + s3 + "' out = '" + s4 + "'");

		String s5 = " *** *.thingy";
		String s6 = c.asteriskStrip(s5);
		System.out.println("asteriskStrip: in = '" + s5 + "' out = '" + s6 + "'");

	}

}
