package org.cdlib.marcconvert;

import java.util.Enumeration;
import java.util.Vector;

import org.apache.log4j.Logger;

import org.cdlib.util.marc.MarcException;
import org.cdlib.util.marc.MarcFieldList;
import org.cdlib.util.marc.MarcRecord;
import org.cdlib.util.marc.MarcSubfield;
import org.cdlib.util.marc.MarcVblLengthField;
import org.cdlib.util.string.StringUtil;

/**
 * Convert the marc records from the RLIN sources. Those sources are:
 * CHS, LBL, and UCB Law. Unlike the III sources, which make use of
 * base class, that is extended by source specific converters, this
 * is an all in one converter. This class must be instantiated with
 * valid campus code. It is this campus code that is used to control
 * source specific behaviour.
 *
 * @author <a href="mailto:rmoon@library.berkeley.edu">Ralph Moon</a>
 * @author <a href="mailto:shawnm@splorkin.com">Shawn McGovern</a>
 * @version $Id: RLINConvert.java,v 1.12 2008/01/28 23:21:45 rkl Exp $
 */
public class RLINConvert extends MarcConvert
{
	/**
	 * log4j Logger for this class.
	 */
    private static Logger log = Logger.getLogger(RLINConvert.class);

	/**
	 * CVS header string.
	 */
    public static final String cvsHeader = "$Header: /cvs/root/melvyl/ei/marcconvert/java/org/cdlib/marcconvert/RLINConvert.java,v 1.12 2008/01/28 23:21:45 rkl Exp $";

	/**
	 * CVS version string.
	 */
    public static final String version = "$Revision: 1.12 $";

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
     * Numerical code for California Historical Society records
     */
    public static final int CHS = 0;

    /**
     * Numerical code for Lawrence Berkeley Labs records
     */
    public static final int LBL = 1;

    /**
     * Numerical code for UCB Law records
     */
    public static final int BOALT = 2;

     /**
     * The minimun allowed value for campus code.
     */
	private static final int MINIMUM_CODE = 0;

    /**
     * The maximun allowed value for campus code.
     */
    private static final int MAXIMUM_CODE = 2;

    /**
     * Numerial code for source of records being converted. This value is set
     * by the contructor and must be within the range of MINIMUM_CODE to
     * MAXIMUM_CODE as defined within this class.
     */
    private int institution;

    /*
     * True iff a CHS AMC record
     */
    private boolean isAMC;

    /*
     * RLIN fill character
     */
    private static final char FILL_CHAR = '\u007C';

    /*
     * RLIN fill character as a one-character string
     */
    private static final String FILL_STRING = "\u007C";


    /**
     * Construct a new RLINConverter for the specified institution
     *
     * @param campus Numerical code for source of records. E.g.,
     * RLINConvert.BOALT, RLINConvert.LBL, etc.
     */
    public RLINConvert(int campus)
    {
        super();

        // Validate campus code
        if (campus < MINIMUM_CODE || campus > MAXIMUM_CODE)
        {
            throw new RuntimeException("Unknown campus code");
        }

        institution = campus;
    }


    /**
     * Convert the given marc record. Place the result into the given output
     * marc record parameter variable. The status of the conversion is returned.
     *
     * @param inRec the <code>MarcRecord</code> to convert
     * @param outRec the converted <code>MarcRecord</code>
     * @return the conversion status code
     * @throws MarcDropException whenever a record is rejected
     * @see org.cdlib.marcconvert.ConvertConstants
     */
    public int convert(MarcRecord inRec, MarcRecord outRec)
        throws MarcDropException
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
     * @throws MarcDropException whenever a record is rejected
     * @see org.cdlib.marcconvert.ConvertConstants
     */
    private int convert()
        throws MarcDropException
    {
        int rc = CONVERT_REC_SUCCESS;

        // Set default debug level
        setDebug(0);

        // Set status for this record
        setStatus(OK);

        // Verify record source
        if ( ! verifyCatalogingSource() )
        {
            throw new MarcDropException("Unknown or incorrect cataloging source");
        }

        // Validate the leader
        validateLeader();

        // Check for acquisitions
        if ( isAcquisition() )
        {
            throw new MarcDropException("RLIN aquisitions record");
        }

        // Determine if this is an Archive and Manuscript Control (AMC) record
        // BUG: Test fails if the $l of the first 950 is missing or blank
        if (institution == RLINConvert.CHS)
        {
            String v950l = inMarc.getFirstValue("950", "l");
            isAMC = (exists(v950l) && v950l.trim().equals(libraryIdentifier()));
        }
        else
        {
            isAMC = false;
        }

        // Move the input leader to output
        moveLeader();

        // Move all fields within 000 to 899 to output
        moveOut(0, 899);

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
     * Returns true if the cataloging source matches that for
     * the institution requested when the constructor was called.
     *
     * Returns false if the record lacks an 001 or if
     * the cataloging source doesn't match expectations.
     */
    private boolean verifyCatalogingSource()
    {
        // Get the library identifier from the record
        String v001 = inMarc.getFirstValue("001", "");

        // Note: exists() test is not strong enough here,
        // since the subtring will fail if the length is less than 4,
        // not just greater than 0.
        if (v001 == null || v001.length() < 4) return false;
        String li = v001.substring(0,4);

        // Get the expected library identifier
        String requiredLI = libraryIdentifier();
        if (!exists(requiredLI)) return false;

        // Make sure that the actual and the expected
        // identifiers match
        return li.equals(requiredLI);
    }


    /**
     * Validate the leader. If the leader status is 'e' (RLIN error record)
     * or the leader status is 's' (RLIN save record) reject the record,
     * otherwise continue processing this input record.
     *
     * @return true whenever the record is not rejected
     * @throws MarcDropException to indicate a rejected record
     */
    private boolean validateLeader()
        throws MarcDropException
    {
        boolean bRet = true;
        char status = inMarc.getRecordStatusChar();
        if ( status == 'e' )
        {
            throw new MarcDropException("Leader status is save");
        }

        if ( status == 's' )
        {
            throw new MarcDropException("Leader status is error");
        }
        return bRet;
    }


    /**
     * Check if this is an RLIN acquisitions records.
     * RLIN aquisitions contain a 998 $t with a value of 'a'.
     *
     * @return true if this is an RLIN acquisitions record, otherwise false
     */
    private boolean isAcquisition()
    {
        boolean bRet = false; // assume this is not an acquisition
        MarcFieldList mfl = inMarc.getFields(998, 998);
        Enumeration enu = mfl.elements();
        while ( enu.hasMoreElements() && bRet == false )
        {
            MarcVblLengthField f = (MarcVblLengthField) enu.nextElement();
            Vector subfields = f.subfield("t");
            Enumeration esf = subfields.elements();
            while ( esf.hasMoreElements() && bRet == false )
            {
                MarcSubfield sf = (MarcSubfield)esf.nextElement();
                if ( MarcSubfield.exists(sf) )
                {
                    bRet = sf.value().equals("a");
                }
            }
        }

        return bRet;
    }


    /**
     * Normalizes the record leader in the output record.
     * Presumes that the caller has moved the input
     * leader to output.
     */
    private void normalizeLeader()
    {
        // Position 5 - Status
        if (inMarc.getRecordStatusChar() == FILL_CHAR)
            outMarc.setRecordStatus('c');

        // Position 6 - Type
        if (inMarc.getRecordType().equals("h"))
            outMarc.setRecordType("a");

        // Position 7 - Bibliographic Level
        if (inMarc.getBibLvl().equals("p"))
            outMarc.setBibLvl("a");

        // Position 17 - Encoding Level
        if (inMarc.getEncLvl().equals(FILL_STRING))
            outMarc.setEncLvl("7");

        // Position 18 - Descriptive Cataloging Form
        if (inMarc.getDescCatForm().equals(FILL_STRING))
            outMarc.setDescCatForm(" ");
    }


    /**
     * Normalizes the bibliographic fields in the output record.
     * Presumes that the caller has moved all non-local input
     * fields to output.
     *
     * @exception MarcDropException No 901 $a in input record. Will
     * already have been caught if verifyCatalogingSource() is called
     * before normalizeBibFields() as it should.
     */
     private void normalizeBibFields() {

        // Drop unwanted fields
        dropFields();

        // Build these fields for output
        build901();
    }


    /**
     * Normalizes the holdings fields in the output record.
     */
    private void normalizeHoldingsFields()
    {
        // Get the holdings fields
        MarcFieldList holdingsFields = inMarc.allFields("950");

        // Special processing for Boalt records that lack any 950 fields
        if ( holdingsFields.size() == 0 && institution == RLINConvert.BOALT )
        {
            if ( log.isDebugEnabled() )
            {
                log.debug("Processing Boalt with no 950");
            }

            // Add a new 852 to the output using STA as location
            MarcVblLengthField f852 = outMarc.getNewVblField("852", "  ");

            // Add 852 $a INSTITUTION CODE
            f852.addSubfield("a", libraryIdentifier());

            // Add 852 $b LOCATION CODE
            f852.addSubfield("b", "STA");

            // Add call number from 090 if there is one
            MarcVblLengthField f090 = (MarcVblLengthField)inMarc.getFirstField("090");
            if ( f090 != null )
            {
                if ( log.isDebugEnabled() )
                {
                    log.debug("Processing Boalt with no 950 - adding call number from 090");
                }
                addCallNr(f852, f090);
            }
        }
        // end of Boalt special processing

        /*
         * Main loop
         */
        for (int locNr = 0; locNr < holdingsFields.size(); locNr++)
        {
            MarcVblLengthField f950 = null;
            String locCode = null;

            // Get the location code from the 950 $l
            f950 = (MarcVblLengthField)holdingsFields.elementAt(locNr);
            if ( f950 != null )
            {
                locCode = f950.firstSubfieldValue("l");
            }

            /*
             * Creation Exceptions
             */

            // Move on if no usable loc was found
            // Note: this also means f950 is not null, so we can rely upon it later
            if ( !exists(locCode) )
            {
                continue;
            }

            // Move on if there is a holdings delete code in the 950 $i
            String v950i = f950.firstSubfieldValue("i");
            if ( exists(v950i) && v950i.length() >= 10 && v950i.charAt(9) == 'D' )
            {
                continue;
            }

            // New location, so add a new 852 to the output
            MarcVblLengthField f852 = outMarc.getNewVblField("852", "  ");

            // Add 852 $a INSTITUTION CODE
            f852.addSubfield("a", libraryIdentifier());


            // Add 852 $b LOCATION CODE
            String b = (isAMC ? "MAIN" : locCode);
            f852.addSubfield("b", b);


            // Add 852 $g NON-CODED LOCATION QUALIFIER

            // Location qualifiers come from the 950 and/or the 090
            String v950n = f950.firstSubfieldValue("n");
            String v090n = inMarc.getFirstValue("090", "n");

            String v852g = normalizeFor852g(v950n);
            if ( exists(v852g) )
            {
                f852.addSubfield("g", v852g);
            }

            v852g = normalizeFor852g(v090n);
            if ( exists(v852g) )
            {
                f852.addSubfield("g", v852g);
            }

            // Add 852 $j CALL NUMBER, if record is AMC and has an 090 $b
	    String v90b = inMarc.getFirstValue("090","b");
	    if (isAMC &&  exists(v90b))
	    {
		f852.addSubfield("j", v90b);
	    }
	
            // Add 852 $h, $i, $j CALL NUMBER, if record is not AMC 
            if (!isAMC && !addCallNr(f852, f950))
            {
                MarcVblLengthField f090 = (MarcVblLengthField)inMarc.getFirstField("090");
                addCallNr(f852, f090);
            }

            // Add 852 $k CALL NUMBER PREFIX
            Vector f950ds = f950.subfields("d", true);
            int maxd = 0;
            if ( f950ds != null && (maxd = f950ds.size()) > 0 )
            {
                for ( int i = 0; i < maxd; i++ )
                {
                    MarcSubfield f950d = (MarcSubfield)f950ds.elementAt(i);
                    if ( exists(f950d) )
                    {
                        String k = replaceBackslashes(f950d.value());
                        // Exceptions
                        if ( exists(k) && institution == RLINConvert.BOALT )
                        {
                            if ( !k.equals("Asia") && !k.equals("I") )
                            {
                                f852.addSubfield("m", k);
                                k = null;   // Force value to be ignored
                            }
                        }
                        if ( exists(k) )
                        {
                            f852.addSubfield("k", k);
                        }
                    }
                }
            }


            // Add 852 $m CALL NUMBER SUFFIX
            // Note: certain $m's are created above while creating $k's

            Vector f950es = f950.subfields("e", true);
            int maxe = 0;
            if ( f950es != null && (maxe = f950es.size()) > 0 && institution != RLINConvert.CHS)
            {
                for ( int i = 0; i < maxe; i++ )
                {
                    MarcSubfield f950e = (MarcSubfield)f950es.elementAt(i);
                    if ( exists(f950e) )
                    {
                        String m = replaceBackslashes(f950e.value());
                        // Exceptions
                        if ( institution == RLINConvert.LBL )
                        {
                            if ( m.equals("#34") || m.equals("#36") )
                            {
                                m = "Long Loan";
                            }
                            else if ( m.equals("#33") || m.equals("#35") )
                            {
                                m = null;   // Force value to be ignored
                            }
                        }
                        if ( exists(m) )
                        {
                            f852.addSubfield("m", m);
                        }
                    }
                }
            }


            // CHS Special Case
            // Note: Certain 852 $z's are create here in addition to $m's

            if ( institution == RLINConvert.CHS )
            {
                // Check for 'shelved', 'retained', and 'copies' notes.
                // First in the 950 $n's
                Vector f950ns = f950.subfields("n", true);
                int maxn = 0;

                if ( f950ns != null && (maxn = f950ns.size()) > 0 )
                {
                    for ( int i = 0; i < maxn; i++ )
                    {
                        MarcSubfield f950n = (MarcSubfield)f950ns.elementAt(i);
                        if ( exists(f950n)
                             && f950n.value().trim().startsWith("\\")
                             && f950n.value().trim().endsWith("\\"))
                        {
                            String m = replaceBackslashes(f950n.value());
                            if ( exists(m) )
                            {
                                String lcM = m.toLowerCase();
                                if ( lcM.startsWith("shelved")
                                     || lcM.startsWith("retain")
                                     || lcM.startsWith("copies") )
                                {
                                    f852.addSubfield("m", m);
                                }
                                else
                                {
                                    f852.addSubfield("z", m);
                                }
                            }
                        }
                    }
                }


                // Then in the 090 $n's
                MarcVblLengthField f090 = (MarcVblLengthField)inMarc.getFirstField("090");
                if ( f090 != null )
                {
                    Vector f090ns = f090.subfields("n", true);
                    if ( f090ns != null && (maxn = f090ns.size()) > 0 )
                    {
                        for ( int i = 0; i < maxn; i++ )
                        {
                            MarcSubfield f090n = (MarcSubfield)f090ns.elementAt(i);
                            if ( exists(f090n)
                                 && f090n.value().trim().startsWith("\\")
                                 && f090n.value().trim().endsWith("\\"))
                            {
                                String m = replaceBackslashes(f090n.value());
                                if ( exists(m) )
                                {
                                    String lcM = m.toLowerCase();
                                    if ( lcM.startsWith("shelved")
                                         || lcM.startsWith("retain")
                                         || lcM.startsWith("copies") )
                                    {
                                        f852.addSubfield("m", m);
                                    }
                                    else
                                    {
                                        f852.addSubfield("z", m);
                                    }
                                }
                            }
                        }
                    }
                }
            }


            // Add 852 $p PIECE DESIGNATION

            MarcFieldList f955s = inMarc.allFields("955");

            // Look through the 955's
            if ( f955s != null )
            {
                while ( f955s.hasMoreElements() )
                {
                    // Look for a 955 with a $s
                    MarcVblLengthField f955 = (MarcVblLengthField)f955s.nextElement();
                    String v955s = f955.firstSubfieldValue("s");
                    // If the $s contains 'NRLF'
                    int nrlfIndex;
                    if ( exists(v955s) && (nrlfIndex = v955s.indexOf("NRLF")) >= 0 )
                    {
                        int offset = nrlfIndex + "NRLF:".length();
                        if ( log.isDebugEnabled() )
                        {
                            log.debug("v955s = " + v955s + " length = " + v955s.length()
                                      + " offset = " + offset);
                        }
                        if ( offset < v955s.length() )
                        {

                            // Then extract the NRLF number from the $s
                            String p = v955s.substring(offset).trim();
                            // And put it in the 852 $p
                            if ( exists(p) )
                            {
                                f852.addSubfield("p", p);
                                break;  // Output only one $p
                            }
                        }
                    }
                }
            }


            // Add 852 $x NONPUBLIC NOTE

            // CHS AMC Special Case
            if ( isAMC )
            {
                // 541 $a's
                MarcFieldList f541s = inMarc.allFields("541");
                if ( f541s != null )
                {
                    while ( f541s.hasMoreElements() )
                    {
                        MarcVblLengthField f541 = (MarcVblLengthField)f541s.nextElement();
                        Vector sfs = f541.subfields();
                        int sfsSize = sfs.size();
                        StringBuffer sb = new StringBuffer();
                        for ( int i = 0; i < sfsSize; i++ )
                        {
                            MarcSubfield sf = (MarcSubfield)sfs.elementAt(i);
                            if ( exists(sf) )
                            {
                                sb.append(sf.value() + " ");
                            }
                        }
                        String x = sb.toString().trim();
                        if ( exists(x) )
                        {
                            f852.addSubfield("x", x);
                        }
                    }
                }

                // 583 $a's
                MarcFieldList f583s = inMarc.allFields("583");
                if ( f583s != null )
                {
                    while ( f583s.hasMoreElements() )
                    {
                        MarcVblLengthField f583 = (MarcVblLengthField)f583s.nextElement();
                        Vector sfs = f583.subfields();
                        int sfsSize = sfs.size();
                        StringBuffer sb = new StringBuffer();
                        for ( int i = 0; i < sfsSize; i++ )
                        {
                            MarcSubfield sf = (MarcSubfield)sfs.elementAt(i);
                            if (exists(sf))
                            {
                                sb.append(sf.value() + " ");
                            }
                        }
                        String x = sb.toString().trim();
                        if ( exists(x) )
                        {
                            f852.addSubfield("x", x);
                        }
                    }
                }
            }
            // CHS AMC Special Case


            // LBL Special Case
            if (institution == RLINConvert.LBL)
            {
                // Profiled notes
                String notes = "#16#17#18#19#21#23#24#25#26#28#29#30#31#37#38#40#41#42";

                MarcVblLengthField f090 = (MarcVblLengthField)inMarc.getFirstField("090");
                Vector f090ns = f090.subfields("n", true);
                Vector f950ns = f950.subfields("n", true);

                int f090nsSize = 0;
                int f950nsSize = 0;

                if ( f950ns != null && (f950nsSize = f950ns.size()) > 0 )
                {
                    // Add a non-public note for each profiled note in a 950 $n
                    for ( int i = 0; i < f950nsSize; i++ )
                    {
                        MarcSubfield f950n = (MarcSubfield)f950ns.elementAt(i);
                        if ( exists(f950n) )
                        {
                            /* Check if the value is in the list of profiled notes
                             * Find the note in the note list and make sure it coincides
                             * with a note boundary, and is of length 3.
                             * I.e., #19 is ok, but #1, or 19, or 9#2 are not okay, although
                             * all will have a positive index into the notes string.
                             */
                            String x = f950n.value();
                            if ( x.length() == 3 )
                            {
                                int noteIdx = notes.indexOf(x);
                                if ( noteIdx >= 0 && (noteIdx % 3) == 0 )
                                {
                                    f852.addSubfield("x", x);
                                }
                            }
                        }
                    }
                }

                if ( f090ns != null && (f090nsSize = f090ns.size()) > 0 )
                {
                    // Add a non-public note for each profiled note in a 090 $n
                    for ( int i = 0; i < f090nsSize; i++ )
                    {
                        MarcSubfield f090n = (MarcSubfield)f090ns.elementAt(i);
                        if ( exists(f090n) )
                        {
                            /* Check if the value is in the list of profiled notes
                             * Find the note in the note list and make sure it coincides
                             * with a note boundary, and is of length 3.
                             * I.e., #19 is ok, but #1, or 19, or 9#2 are not okay, although
                             * all will have a positive index into the notes string.
                             */
                            String x = f090n.value();
                            if ( x.length() == 3 )
                            {
                                int noteIdx = notes.indexOf(x);
                                if ( noteIdx >= 0 && (noteIdx % 3) == 0 )
                                {
                                    f852.addSubfield("x", x);
                                }
                            }
                        }
                    }
                }
            }
            // End of LBL special case

            // 852 $x's from 950 $f's, $u's, and $w's

            Vector f950fs = f950.subfields("f", true);
            Vector f950us = f950.subfields("u", true);
            Vector f950ws = f950.subfields("w", true);

            boolean has852xFrom950f = false;

            int f950fsSize = 0;
            if ( f950fs != null && (f950fsSize = f950fs.size()) > 0 )
            {
                // Add a non-public note for each profiled note in a 950 $f
                for ( int i = 0; i < f950fsSize; i++ )
                {
                    MarcSubfield f950f = (MarcSubfield)f950fs.elementAt(i);
                    if ( exists(f950f) )
                    {
                        f852.addSubfield("x", f950f.value());
                        has852xFrom950f = true;
                    }
                }
            }

            // Add a non-public note for each profiled note in a 950 $u
            int f950usSize = 0;
            if ( f950us != null && (f950usSize = f950us.size()) > 0 )
            {
                for ( int i = 0; i < f950usSize; i++ )
                {
                    MarcSubfield f950u = (MarcSubfield)f950us.elementAt(i);
                    if ( exists(f950u) )
                    {
                        f852.addSubfield("x", f950u.value());
                    }
                }
            }

            // Add a non-public note for each profiled note in a 950 $w
            int f950wsSize = 0;
            if ( f950ws != null && (f950wsSize = f950ws.size()) > 0 )
            {
                for ( int i = 0; i < f950wsSize; i++ )
                {
                    MarcSubfield f950w = (MarcSubfield)f950ws.elementAt(i);
                    if (exists(f950w))
                    {
                        f852.addSubfield("x", f950w.value());
                    }
                }
            }

            // If there are no contributions from the 950 $f, try the 090 $f's
            if ( !has852xFrom950f )
            {
                MarcVblLengthField f090 = (MarcVblLengthField)inMarc.getFirstField("090");
				if ( f090 != null )
				{
					Vector f090fs = f090.subfields("f", true);

					// Add a non-public note for each 090 $f
					int f090fsSize = 0;
                    if ( f090fs != null && (f090fsSize = f090fs.size()) > 0 )
                    {
                        for ( int i = 0; i < f090fsSize; i++ )
                        {
                            MarcSubfield f090f = (MarcSubfield)f090fs.elementAt(i);
                            if (exists(f090f))
                            {
                                f852.addSubfield("x", f090f.value());
                            }
                        }
					}
				}
            }

            // Add 852 $z PUBLIC NOTE
            // CHSV handled above during creation of 852 $m's

            if ( institution != RLINConvert.CHS )
            {
                MarcVblLengthField f090 = (MarcVblLengthField)inMarc.getFirstField("090");
				if ( f090 != null )
				{
					Vector f090ns = f090.subfields("n", true);
					Vector f950ns = f950.subfields("n", true);

					// Check for text between backslashes in 090 $n's
					int f090nsSize = 0;
                    if ( f090ns != null && (f090nsSize = f090ns.size()) > 0 )
                    {
                        for ( int i = 0; i < f090nsSize; i++ )
                        {
                            MarcSubfield f090n = (MarcSubfield)f090ns.elementAt(i);
                            if ( exists(f090n) )
                            {
                                String z = f090n.value();
                                if ( z.startsWith("\\") && z.endsWith("\\") && z.length() > 1 )
                                {
                                    z = z.substring(1, z.length()-1);
                                    if ( exists(z) )
                                    {
                                        f852.addSubfield("z", z);
                                    }
                                }
                            }
                        }
					}

					// Check for text between backslashes in 950 $n's
					int f950nsSize = f950ns.size();
                    if ( f950ns != null && (f950nsSize = f950ns.size()) > 0 )
                    {
                        for ( int i = 0; i < f950nsSize; i++ )
                        {
                            MarcSubfield f950n = (MarcSubfield)f950ns.elementAt(i);
                            if ( exists(f950n) )
                            {
                                String z = f950n.value().trim();
                                if ( z.startsWith("\\") && z.endsWith("\\") && z.length() > 1 )
                                {
                                    z = z.substring(1, z.length()-1);
                                    if ( exists(z) )
                                    {
                                        f852.addSubfield("z", z);
                                    }
                                }
                            }
                        }
					}
				}
			}
            // end not CHS


            // Check for 590 $a's
            MarcFieldList f590s = inMarc.allFields("590");
            int f590sSize = f590s.size();
            if ( f590s != null && (f590sSize = f590s.size()) > 0 )
            {
                for ( int i = 0; i < f590sSize; i++ )
                {
                    MarcVblLengthField f590 = (MarcVblLengthField)f590s.elementAt(i);
                    if ( f590 != null )
                    {
                        String z = f590.firstSubfieldValue("a");
                        if ( exists(z) )
                        {
                            f852.addSubfield("z", z);
                        }
                    }
                }
            }

            // Add 852 $3 MATERIALS SPECIFIED (SUMMARY HOLDINGS)

            boolean dollar3Found = false;

            // LBL Special Case

            if ( institution == RLINConvert.LBL )
            {
                // Check for note #13 or #32 in 950 $n
                Vector f950ns = f950.subfields("n", true);
                int f950nsSize = 0;
                if ( f950ns != null && (f950nsSize = f950ns.size()) > 0 )
                {
                    for ( int i = 0; !dollar3Found && i < f950nsSize; i++ )
                    {
                        MarcSubfield f950n = (MarcSubfield)f950ns.elementAt(i);
                        if ( exists(f950n) )
                        {
                            String trim950n = f950n.value().trim();
                            if ( trim950n.startsWith("#32") )
                            {
                                f852.addSubfield("3", "Latest edition only");
                                dollar3Found = true;
                                break;
                            }
                            if ( trim950n.startsWith("#13") )
                            {
                                String v8523 = trim950n.substring("#13".length());
                                int firstBS  = v8523.indexOf('\\');
                                int lastBS   = v8523.lastIndexOf('\\');
                                if ( firstBS >= 0 && lastBS > firstBS )
                                {
                                    String s = v8523.substring(firstBS + 1, lastBS);
                                    if ( exists(s) )
                                    {
                                        f852.addSubfield("3", "Library wants: " + s);
                                        dollar3Found = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }

                // If not found, check for note #13 or #32 in 090 $n

                if ( !dollar3Found )
                {
                    MarcVblLengthField f090 = (MarcVblLengthField)inMarc.getFirstField("090");
                    Vector f090ns = f090.subfields("n", true);
                    int f090nsSize = 0;
                    if ( f090ns != null && (f090nsSize = f090ns.size()) > 0 )
                    {
                        for ( int i = 0; !dollar3Found && i < f090nsSize; i++ )
                        {
                            MarcSubfield f090n = (MarcSubfield)f090ns.elementAt(i);
                            if ( exists(f090n) )
                            {
                                String trim090n = f090n.value();
                                if ( trim090n != null )
                                {
                                    if ( trim090n.startsWith("#32") )
                                    {
                                        f852.addSubfield("3", "Latest edition only");
                                        dollar3Found = true;
                                        break;
                                    }
                                    else if ( trim090n.startsWith("#13") )
                                    {
                                        String v8523 = trim090n.substring("#13".length());
                                        int firstBS = v8523.indexOf('\\');
                                        int lastBS = v8523.lastIndexOf('\\');
                                        if ( firstBS >= 0 && lastBS > firstBS )
                                        {
                                            String s = v8523.substring(firstBS + 1, lastBS);
                                            if ( exists(s) )
                                            {
                                                f852.addSubfield("3", "Library wants: " + s);
                                                dollar3Found = true;
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // end LBL Special Case

            // In general, concatenate 950 $v, $y, and $z (if present)

            if (!dollar3Found)
            {
                String v950v = f950.firstSubfieldValue("v");
                String v950y = f950.firstSubfieldValue("y");
                String v950z = f950.firstSubfieldValue("z");

                StringBuffer sb = new StringBuffer();
                if ( exists(v950v) )
                {
                    sb.append(v950v + " ");
                }

                if ( exists(v950y) )
                {
                    sb.append(v950y + " ");
                }

                if ( exists(v950z) )
                {
                    sb.append(v950z + " ");
                }

                String v8523 = sb.toString().trim();
                if ( exists(v8523) )
                {
                    f852.addSubfield("3", v8523);
                    dollar3Found = true;
                }
            }

            // As last resort, concatenate 090 $v, $y, and $z (if present)

            if (!dollar3Found)
            {
                MarcVblLengthField f090 = (MarcVblLengthField)inMarc.getFirstField("090");

				if ( f090 != null)
				{
					String v090v = f090.firstSubfieldValue("v");
					String v090y = f090.firstSubfieldValue("y");
					String v090z = f090.firstSubfieldValue("z");

					StringBuffer sb = new StringBuffer();
					if ( exists(v090v) )
					{
						sb.append(v090v + " ");
					}

					if ( exists(v090y) )
					{
						sb.append(v090y + " ");
					}

					if ( exists(v090z) )
					{
						sb.append(v090z + " ");
					}

					String v8523 = sb.toString().trim();
					if ( exists(v8523) )
					{
						f852.addSubfield("3", v8523);
						dollar3Found = true;
					}
				}
			}
        }
    }


    /**
     * Return the library identifier for the institution
     * specified when the constructor was called to create
     * this instance.
     *
     * @return Library identifier for this institution.
     * E.g., "CHSV", "CUBL", etc., or null if institution
     * is unknown.
     */
     private String libraryIdentifier()
     {
        String li;
        switch (institution)
        {
        case CHS:
            li = "CHSV";
            break;

        case LBL:
            li = "CLBI";
            break;

        case BOALT:
            li = "CUBL";
            break;

        default:
            li = null;  // This should never happen
            break;

        }
        return li;
    }


    /**
     * Drop unwanted fields from the output marc object
     */
    private void dropFields()
    {
        // Drop 000
        outMarc.deleteFields("000");

        // Drop 541
        if (isAMC) outMarc.deleteFields("541");

        // Drop 583
        if (isAMC) outMarc.deleteFields("583");

        // Drop 590
        outMarc.deleteFields("590");

        //delete any 852 fields
        outMarc.deleteFields("852");

        // Drop 9XX (Shouldn't be necessary, but ... )
        outMarc.deleteFields("9XX");
    }


    /**
     * Build MARC 901 field for output record based on input
     * 901. Throw MarcDropException if input 001 is missing,
     * if the code for the specified institution is unknown,
     * or if the library identifier is incorrect.
     */
    private void build901()
    {
        // get the first 901 field
        String v001 = inMarc.getFirstValue("001", "");
        if ( !exists(v001) )
        {
            throw new MarcDropException("Input lacks 001");
        }

        // Determine the library identifier, which will go in the $a
        String lid = v001.substring(0,4);

        // Make sure the identifier is OK.
        String requiredLI = libraryIdentifier();
        if ( !exists(requiredLI) || !exists(lid) || !lid.equals(requiredLI) )
        {
            throw new MarcDropException("Unknown or incorrect library identifier");
        }

        // Determine the record identifier, which will go in the $b
        String rid = v001.substring(4);

        // Add the output 901
        MarcVblLengthField out901 = outMarc.getNewVblField("901", "  ");
        out901.addSubfield("a", lid);
        if ( exists(rid) )
        {
            out901.addSubfield("b", rid);
        }
    }


    /**
     * Replaces valid profiled notes with appropriate text and returns
     * the result.  Returns null if the input string is null or empty or
     * doesn't contain a valid profiled note.
     */
    private String normalizeFor852g(String s)
    {
        // Profiled are only used by LBL
        if ( exists(s) && institution == RLINConvert.LBL )
        {
            // Note #12
            if ( s.indexOf("#12") >= 0 )
            {
                return "No Bound Volumes";
            }
            // Note #11
            else if ( s.indexOf("#11") >= 0 )
            {
                int openBackslash, closeBackslash = -1;
                String txt = null;

                // Completing text is between backslashes
                // So first find the backslashes
                openBackslash = s.indexOf('\\');
                if (openBackslash >= 0)
                {
                    closeBackslash = s.indexOf('\\', openBackslash+1);
                }

                // And then extract the text
                if ( openBackslash >= 0 )
                {
                    if ( closeBackslash > openBackslash )
                    {
                        txt = s.substring(openBackslash+1, closeBackslash);
                    }
                    else
                    {
                        txt = s.substring(openBackslash+1);
                    }
                }

                if ( exists(txt) )
                {
                    return ("Latest in " + txt);
                }
            }
        }
        return null;
    }


    /*
     * Add call number subfields to the specified 852 field from information
     * in the specified data field (which will be a 950 or 090). Returns true
     * if a call number was successfully added.
     */
    private boolean addCallNr(MarcVblLengthField f852, MarcVblLengthField data)
    {
        // Validate data
        if ( data == null )
        {
            return false;
        }

        // Get the a and b subfields (if any)
        String a = data.firstSubfieldValue("a");
        String b = data.firstSubfieldValue("b");

        // Replace flanking and embedded backslashes
        a = replaceBackslashes(a);
        b = replaceBackslashes(b);

        // One $a and one $b
        if ( exists(a) && exists(b) )
        {
            f852.addSubfield("h", a);
            f852.addSubfield("i", b);
            return true;
        }

        // One $a only
        if ( exists(a) && !exists(b) )
        {
            f852.addSubfield("j", a);
            return true;
        }

        // One $b only
        if (!exists(a) && exists(b)) {
            f852.addSubfield("j", b);
            return true;
        }

        // Neither exist
        return false;
    }


    /*
     * Remove leading and trailing backslashes, and replace internal
     * backslashes with spaces.  Returns the result, or null if either
     * the input or result is null or empty.
     */
    private String replaceBackslashes(String s)
    {
        return StringUtil.stripBackSlash(s);
    }

}
