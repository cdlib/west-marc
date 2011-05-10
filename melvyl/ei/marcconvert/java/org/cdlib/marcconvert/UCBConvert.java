package org.cdlib.marcconvert;

import java.util.Vector;

import org.apache.log4j.Logger;

import org.cdlib.util.marc.MarcException;
import org.cdlib.util.marc.MarcFieldList;
import org.cdlib.util.marc.MarcFixedLengthField;
import org.cdlib.util.marc.MarcRecord;
import org.cdlib.util.marc.MarcSubfield;
import org.cdlib.util.marc.MarcVblLengthField;
import org.cdlib.util.marc.TranslateTable;
import org.cdlib.util.string.StringUtil;

/**
 * Convert the marc records from the UCB source.
 *
 * @author <a href="mailto:lhaynes@library.berkeley.edu">Leigh Haynes</a>
 * @author <a href="mailto:shawnm@splorkin.com">Shawn McGovern</a>
 * @version $Id: UCBConvert.java,v 1.7 2002/10/29 00:23:28 smcgovrn Exp $
 */
public class UCBConvert extends MarcConvert
{
	/**
	 * log4j Logger for this class.
	 */
    private static Logger log = Logger.getLogger(UCBConvert.class);

	/**
	 * CVS header string.
	 */
    public static final String cvsHeader = "$Header: /cvs/root/melvyl/ei/marcconvert/java/org/cdlib/marcconvert/UCBConvert.java,v 1.7 2002/10/29 00:23:28 smcgovrn Exp $";

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

    /*
     * Translate table for RLIN to Marc 21 escape sequences.
     */
    private static final String[][] xlatTbl1 =
    {
        {"\\xAF", "\u001B\u0067\u0061\u001B\u0073"},  // Greek alpha
        {"\\xBE", "\u001B\u0067\u0062\u001B\u0073"},  // Greek beta
        {"\\xBF", "\u001B\u0067\u0063\u001B\u0073"},  // Greek gamma

        {"\\xD0", "\u001B\u0062\u0030\u001B\u0073"},  // Subscript 0
        {"\\xD1", "\u001B\u0062\u0031\u001B\u0073"},  // Subscript 1
        {"\\xD2", "\u001B\u0062\u0032\u001B\u0073"},  // Subscript 2
        {"\\xD3", "\u001B\u0062\u0033\u001B\u0073"},  // Subscript 3
        {"\\xD4", "\u001B\u0062\u0034\u001B\u0073"},  // Subscript 4
        {"\\xD5", "\u001B\u0062\u0035\u001B\u0073"},  // Subscript 5
        {"\\xD6", "\u001B\u0062\u0036\u001B\u0073"},  // Subscript 6
        {"\\xD7", "\u001B\u0062\u0037\u001B\u0073"},  // Subscript 7
        {"\\xD8", "\u001B\u0062\u0038\u001B\u0073"},  // Subscript 8
        {"\\xD9", "\u001B\u0062\u0039\u001B\u0073"},  // Subscript 9
        {"\\xDA", "\u001B\u0062\u002B\u001B\u0073"},  // Subscript +
        {"\\xDB", "\u001B\u0062\u002D\u001B\u0073"},  // Subscript -
        {"\\xDC", "\u001B\u0062\u0028\u001B\u0073"},  // Subscript (
        {"\\xDD", "\u001B\u0062\u0029\u001B\u0073"},  // Subscript )

        {"\\xC0", "\u001B\u0070\u0030\u001B\u0073"},  // Superscript 0
        {"\\xC1", "\u001B\u0070\u0031\u001B\u0073"},  // Superscript 1
        {"\\xC2", "\u001B\u0070\u0032\u001B\u0073"},  // Superscript 2
        {"\\xC3", "\u001B\u0070\u0033\u001B\u0073"},  // Superscript 3
        {"\\xC4", "\u001B\u0070\u0034\u001B\u0073"},  // Superscript 4
        {"\\xC5", "\u001B\u0070\u0035\u001B\u0073"},  // Superscript 5
        {"\\xC6", "\u001B\u0070\u0036\u001B\u0073"},  // Superscript 6
        {"\\xC7", "\u001B\u0070\u0037\u001B\u0073"},  // Superscript 7
        {"\\xC8", "\u001B\u0070\u0038\u001B\u0073"},  // Superscript 8
        {"\\xC9", "\u001B\u0070\u0039\u001B\u0073"},  // Superscript 9
        {"\\xCA", "\u001B\u0070\u002B\u001B\u0073"},  // Superscript +
        {"\\xCB", "\u001B\u0070\u002D\u001B\u0073"},  // Superscript -
        {"\\xCC", "\u001B\u0070\u0028\u001B\u0073"},  // Superscript (
        {"\\xCD", "\u001B\u0070\u0029\u001B\u0073"},  // Superscript )
    };

    /*
     * Note: these translations have been dropped per UCB request,
     * but the table remains for now, in case their winds of desire
     * shift once again.
     */
    private static final String[][] xlatTbl2 =
    {
        {"\\xA0", "\u00C1"},  // Script small L
        {"\\xCE", "\u00C0"},  // Degree sign
        {"\\xCF", "\u00C2"},  // Sound rec copy
        {"\\xDE", "\u00C3"},  // Copyright sign
        {"\\xDF", "\u00C4"},  // Musical sharp
        {"\\xFC", "\u00C6"},  // Inverted exclamation
        {"\\xFD", "\u00C5"}   // Inverted question
    };


    /**
     * Instansiate a new UCBConvert object.
     */
    public UCBConvert()
    {
        super();
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


    //====================================================
    //       PRIVATE
    //====================================================

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

        // Set default debug level for UCBConvert
        setDebug(0);

        // Set status for this record
        setStatus(OK);

        // Verify record source
        String v001 = inMarc.getFirstValue("001", "");
        if (!exists(v001))
            throw new MarcDropException("No 001 field");

        if (v001.length() < 9)
            throw new MarcDropException("Malformed 001 - length less than 9");

        if (!verifyCatalogingSource(v001))
            throw new MarcDropException("Cataloging source not UCB");

        // Move the input leader to output
        moveLeader();

        // Move all fields within 000 to 899 to output
        // Non-numeric tags are excluded by moveOut and will cause
        // an exception to be thrown
        moveOut(0, 899);

        // Normalize the leader
        normalizeLeader();

        // Normalize the bibliographic fields
        normalizeBibFields();

        // Normalize the holdings fields
        normalizeHoldingsFields();

        debugOut(2, "process output leader:" + outMarc.getLeaderValue());

        // Create the TranslateTable object and use it to do the escape translation
        // on all of the subfields in the output record.

        TranslateTable tt1 = new TranslateTable();

        if ( tt1.makeMap(xlatTbl1) )
        {
            outMarc.translateVblFields(tt1);
        }
        else
        {
            log.error("Failed to instantiate translate tables - escape translations bypassed");
        }
        return rc;
    }


    private boolean hasStorageNote (Vector values_950n)
    {
        String v950n = null;
        int vsize = 0;
        boolean hasSto = false;

        if (values_950n != null)
        {
            vsize = values_950n.size();

            for (int j=0; j < vsize && !hasSto; j++)
            {
                v950n = (String)values_950n.elementAt(j);
                hasSto = (v950n.indexOf("#507") % 4 == 0
                          || v950n.indexOf("#096") % 4 == 0);
            }
        }

        return (hasSto);
    }


    private boolean hasSumInfo (Vector values_950n)
    {
        String v950n = null;
        int vsize = 0;
        boolean hasSum = false;

        if (values_950n != null)
        {
            vsize = values_950n.size();

            for (int i=0; i < vsize && !hasSum; i++)
            {
                v950n = (String)values_950n.elementAt(i);
                hasSum = v950n.indexOf("#8") % 2 == 0;
            }
        }

        return (hasSum);
    }


    private boolean hasNRLFnum (Vector values_955s)
    {
        String v955s = null;
        int vsize = 0;
        boolean hasNum = false;

        if (values_955s != null)
        {
            vsize = values_955s.size();

            for (int i=0; i < vsize && !hasNum; i++)
            {
                v955s = (String)values_955s.elementAt(i);

                if (exists(v955s))
                {
                    v955s = StringUtil.stripBackSlash(v955s);
                    hasNum = v955s.substring(0,5).equals("NRLF:");
                }
            }
        }

        return (hasNum);
    }


    /**
     * Returns true if the cataloging source matches that for
     * the campus requested when the constructor was called.
     * Returns false if the record lacks a 901 $a or if
     * the cataloging source doesn't match expectations.
     */
    private boolean verifyCatalogingSource(String v001)
    {
        //check to make sure we actually have a GLADIS record here
        return (v001 != null && v001.length() >= 8 && v001.substring(4,8).equals("GLAD"));
    }


    /**
     * Translate the record type and bibliographic level fields from
     * RLIN values to standard USMARC values.
     */
    private void normalizeLeader()
    {
        if (inMarc.getRecordType().equals("h"))
        {
            outMarc.setRecordType("a");
        }

        if (inMarc.getBibLvl().equals("p"))
        {
            outMarc.setBibLvl("a");
        }
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
    private void normalizeBibFields()
    {
        // Drop unwanted fields
        outMarc.deleteFields("000");
        outMarc.deleteFields("003");
        outMarc.deleteFields("590");
        outMarc.deleteFields("780");  // Rebuilt in build780()
        outMarc.deleteFields("785");  // Rebuild in build785()
        outMarc.deleteFields("852");
        outMarc.deleteFields("9XX");  // Drop 9XX (Shouldn't be necessary, but ... )

        build005();
        build780();
        build785();
        build901();
        return;
    }


    private void normalizeHoldingsFields()
    {
        // record status is d=delete
        if ( inMarc.isDeleteRecord() )
        {
            return;
        }

        build852();
        return;
    }


    /**
     * build marc 852 field
     *   This is the main work that this program does.
     *   The 852 is the holdings field.
     */
    private void build852()
    {
        // standard 852 handling, for deletes and non-deletes

        MarcRecord holdFields = null;

        MarcVblLengthField f950 = null;
        MarcVblLengthField f852 = null;
        MarcVblLengthField f852mvm = null;

        Vector values_950d = null;
        Vector values_950e = null;
        Vector values_950n = null;
        Vector values_950v = null;
        Vector values_955s = null;

        int i = 0;
        int vsize = 0;

        // Loop through a set of repeating fields with the range 950 and 955
        // setGroup finds each group using an incrementer (here i)
        // The group of fields is then used to build a new Marc record groupMarc
        // that contains only the fields from the group. This allows all of the
        // MarcRecord functions to be available on the restricted group of fields

        while ((holdFields = setGroup("950", "955", i++)) != null)
        {
            // Reset all our indicators. Having these outside the loop
            // lead to wretched side effect errors. Whoops!
            boolean isNRLF = false;
            boolean isSRLF = false;
            boolean isMVM = false;
            boolean isBancroft = false;
            boolean hasNum = false;
            boolean hasSto = false;
            boolean hasSum = false;

            // Get first f950 field in groupMarc, if we don't find one
            // leave the the loop, we are done.
            f950 = (MarcVblLengthField)groupMarc.getFirstField("950");
            if ( f950 == null )
            {
                break;
            }

            // Check for a deleted holding first, because we do not want
            // to create an 852 for deleted holdings.
            String s950i = f950.firstSubfieldValue("i");
            if ( s950i != null && s950i.length() > 9 && s950i.charAt(9) == 'D' )
            {
                if ( log.isDebugEnabled() )
                {
                    log.debug("Found delete: s950i = '" + s950i + "'");
                }
                continue; // This is a delete, so process the next entry
            }

            String locCode = f950.firstSubfieldValue("l");
            if ( log.isDebugEnabled() )
            {
                log.debug("f950 locCode = '" + locCode + "'");
            }

            // Without a location code we cannot really make rational
            // decisions about how to create the 852, so punt.
            if ( locCode == null || locCode.length() == 0 )
            {
                log.error("No location code specified in 950 $l");
                continue;
            }

            // Initialize the 852 for this holding
            f852 = outMarc.getNewVblField("852", "  ");

            /* Set up vectors of values for repeatable subfields  */

            // 950 $d subfields
            values_950d = groupMarc.getValues("950", "d");
            if (values_950d != null)
            {
                values_950d = StringUtil.stripBackSlash(values_950d);
            }

            // 950 $e subfields
            values_950e = groupMarc.getValues("950", "e");
            if (values_950e != null)
            {
                values_950e = StringUtil.stripBackSlash(values_950e);
            }

            // 950 $n subfields
            values_950n = groupMarc.getValues("950", "n");
            if (values_950n != null)
            {
                values_950n = StringUtil.stripBackSlash(values_950n);
            }

            // 950 $v subfields
            values_950v = groupMarc.getValues("950", "v");

            // 955 $s subfields
            values_955s = groupMarc.getValues("955", "s");

            /* Set up boolean flags for storage number, storage note, summary note */

            if (values_955s != null) hasNum = hasNRLFnum(values_955s);
            if (values_950n != null) hasSto = hasStorageNote(values_950n);
            if (values_950n != null) hasSum = hasSumInfo(values_950n);
            if (values_950v != null && !hasSum) hasSum = true;

            isSRLF = locCode.equals("ANEG");

            isNRLF = (hasNum || hasSto) && !isSRLF;

            isMVM =  hasSto && hasSum && isNRLF;

            isBancroft = (locCode.equals("BANC") || locCode.equals("BANC2")
                          || locCode.equals("BANC3") || locCode.equals("BANC4")
                          || locCode.equals("BMAP") || locCode.equals("BNEG")
                          || locCode.equals("MARK") || locCode.equals("RARE")
                          || locCode.equals("UARC"));

            if (isNRLF)
            {
                if (!isMVM) // only single volume stored
                {
                    f852.addSubfield("a", "GLAD");

                    if (isBancroft) f852.addSubfield("b", "BNRLF");
                    else f852.addSubfield("b", "NRLF");

                    b852g(f852, values_950n, false);
                    b852hij(f852, f950);
                    b852k(f852, values_950d);
                    b852m(f852, values_950d, values_950e, values_950n);
                    b852p(f852, values_955s);
                    b852xFrom950n(f852, values_950n);
                    b852xFrom950fuw(f852, f950);
                    b852z(f852, values_950n);
                    b852zFrom590a(f852);
                    b852s3(f852, values_950v, f950);
                }
                else // multiple volumes stored
                {
                    //create 1st 852 record for the mvm
                    f852.addSubfield("a", "GLAD");
                    f852.addSubfield("b", locCode);
                    b852g(f852, values_950n, true);
                    b852hij(f852, f950);
                    b852k(f852, values_950d);
                    b852m(f852, values_950d, values_950e, values_950n);
                    b852xFrom950n(f852, values_950n);
                    b852xFrom950fuw(f852, f950);
                    b852z(f852, values_950n);
                    b852zFrom590a(f852);
                    b852s3(f852, values_950v, f950);

                    //create 2nd 852 record for the mvm
                    f852mvm = outMarc.getNewVblField("852", "  ");
                    f852mvm.addSubfield("a", "GLAD");

                    if (isBancroft) f852mvm.addSubfield("b", "BNRLF");
                    else f852mvm.addSubfield("b", "NRLF");

                    b852g(f852mvm, values_950n, false);
                    b852hij(f852mvm, f950);
                    b852k(f852mvm, values_950d);
                    b852m(f852mvm, values_950d, values_950e, values_950n);
                    b852p(f852mvm, values_955s);
                    b852xFrom950n(f852mvm, values_950n);
                    b852xFrom950fuw(f852mvm, f950);
                    b852z(f852mvm, values_950n);
                    b852zFrom590a(f852mvm);
                    // b852s3(f852mvm, values_950v, f950); // Bad behavior!
                }
            }
            else  // not NRLF
            {
                f852.addSubfield("a", "GLAD");
                f852.addSubfield("b", locCode);
                b852g(f852, values_950n, false);
                b852hij(f852, f950);
                b852k(f852, values_950d);
                b852m(f852, values_950d, values_950e, values_950n);

                if (isSRLF) b852p(f852, values_955s);

                b852xFrom950n(f852, values_950n);
                b852xFrom950fuw(f852, f950);
                b852z(f852, values_950n);
                b852zFrom590a(f852);
                b852s3(f852, values_950v, f950);
            }
        }


        // This test is now done for all records prior to output
//      // no 852s created - error
//      if (i == 0)
//      {
//          setError("Insufficient local data to create an 852 field");
//      }

        return;
    }


    private void b852g(MarcVblLengthField f852, Vector values_950n, boolean addText)
    {
        String v950n = null;
        int startNote = 0;
        int vsize = 0;

        if (values_950n != null)
        {
            vsize = values_950n.size();

            for (int i=0; i < vsize; i++)
            {
                startNote = -1;

                v950n = (String)values_950n.elementAt(i);

                if (v950n.indexOf("#096") % 4 == 0)
                {
                    startNote = v950n.indexOf("#096");
                }

                if (startNote < 0 && v950n.indexOf("#507") % 4 == 0)
                {
                    startNote = v950n.indexOf("#507");
                }

                if (startNote >= 0)
                {
                    v950n = ((addText) ? "At NRLF: " : "") + v950n.substring(startNote + 4).trim();
                    f852.addSubfield("g", v950n);
                }
            }
        }
    }

    private void b852hij(MarcVblLengthField f852, MarcVblLengthField f950)
    {
        String v950a = f950.firstSubfieldValue("a");
        String v950b = f950.firstSubfieldValue("b");

        if (!exists(v950a) && !exists(v950b))  return;

        if (exists(v950a)) v950a = StringUtil.stripBackSlash(v950a);
        if (exists(v950b)) v950b = StringUtil.stripBackSlash(v950b);

        if (exists(v950a) && exists(v950b))
        {
            // add the $h field to 852 containing $a data
            f852.addSubfield("h", v950a);
            // add the $i field to 852 containing $b data
            f852.addSubfield("i", v950b);
        }
        else if (exists(v950a))
        {
            f852.addSubfield("j", v950a);  // only $a
        }
        else
        {
            f852.addSubfield("j", v950b);  // only $b
        }
    }


    // Call Number prefix
    private void b852k(MarcVblLengthField f852, Vector values_950d)
    {
        String v950d = null;
        int vsize = 0;

        if (values_950d != null)
        {
            vsize = values_950d.size();

            for (int i=0; i < vsize; i++)
            {
                v950d = (String)values_950d.elementAt(i);

                if (exists(v950d) && (StringUtil.indexOfIgnoreCase(v950d, "Ref") < 0))
                {
                    // add an 852 $k for each 950 $d that does not contain Ref
                    f852.addSubfield("k", v950d);
                }
            }
        }
    }


    // Call number suffix
    private void b852m (MarcVblLengthField f852, Vector values_950d,
                        Vector values_950e, Vector values_950n)
    {
        String v950d = null;
        String v950e = null;
        String v950n = null;

        int startNote = 0;
        int vsize     = 0;

        if (values_950d != null)
        {
            vsize = values_950d.size();

            for (int i=0; i < vsize; i++)
            {
                v950d = (String)values_950d.elementAt(i);

                if (exists(v950d) && (StringUtil.indexOfIgnoreCase(v950d, "Ref") >= 0))
                {
                    // add an 852 $m subfield for each 950 $d that contains Ref
                    f852.addSubfield("m", v950d.trim());
                }
            }
        }

        if (values_950e != null)
        {
            vsize = values_950e.size();

            for (int i=0; i < vsize; i++)
            {
                v950e = (String)values_950e.elementAt(i);

                if (exists(v950e))
                {
                    // add an 852 $m for each 950 $e
                    f852.addSubfield("m", v950e.trim());
                }
            }
        }

        if (values_950n != null)
        {
            vsize = values_950n.size();

            for (int i=0; i < vsize; i++)
            {
                startNote = -1;

                v950n = (String)values_950n.elementAt(i);

                if (exists(v950n))
                {
                    startNote = v950n.indexOf("#506");
                    if (startNote < 0) startNote = v950n.indexOf("#817");

                    if (startNote >= 0)
                    {
                        if ( log.isDebugEnabled() )
                        {
                            log.debug("adding $m: startNote = " + startNote
                                      + "; $n = '" + v950n  + "'; $n(note) = '"
                                      + v950n.substring(startNote + 4).trim() + "'");
                        }
                        // add an 852 $m subfield for each 950 $n note #506 and/or #817
                        f852.addSubfield("m", v950n.substring(startNote + 4).trim());
                    }
                }
            }
        }
    }


    private void b852p(MarcVblLengthField f852, Vector values_955s)
    {
        String v955s = null;

        if (values_955s != null)
        {
            v955s = (String)values_955s.elementAt(0);

            if (exists(v955s) && (v955s.length() > 5))
            {
                f852.addSubfield("p", v955s.substring(5).trim());
            }
        }
    }


    private void b852xFrom950n(MarcVblLengthField f852, Vector values_950n)
    {
        if ( values_950n != null )
        {
            int vsize = values_950n.size();

            for ( int i = 0; i < vsize; i++ )
            {
                String v950n = (String)values_950n.elementAt(i);
                if ( v950n == null)
                {
                    continue;
                }

                int v950nLen = v950n.length();
                if ( v950nLen == 0 )
                {
                    continue;
                }


                int startNote = v950n.indexOf("#");
                int noteNbr = 0;

                // Skip this $n if it does not contain a profiled note
                if ( startNote == -1)
                {
                    continue;
                }

                // look for a sequnece of ascii digits
                // and convert them to a note number
                for ( int j = startNote + 1; j < v950nLen; j++ )
                {
                    char c = v950n.charAt(j);
                    if ( c >= 0x0030 && c <= 0x0039 )
                    {
                        noteNbr = (10 * noteNbr) + (int)(c & 0x000F);
                    }
                    else
                    {
                        break;
                    }
                }

                if ( log.isDebugEnabled() )
                {
                    log.debug("$n = '" + v950n + "'; noteNbr = " + noteNbr);
                }

                // We want this note if it is in the range #43 - #51
                if ( noteNbr >= 43 && noteNbr <= 51)
                {
                    f852.addSubfield("x", v950n);
                }
            }
        }
    }

    private void b852xFrom950fuw(MarcVblLengthField f852, MarcVblLengthField f950) {

        String v950f = f950.firstSubfieldValue("f");
        if (exists(v950f))  f852.addSubfield("x", v950f);

        String v950u = f950.firstSubfieldValue("u");
        if (exists(v950u)) f852.addSubfield("x", v950u);

        String v950w = f950.firstSubfieldValue("w");
        if (exists(v950w)) f852.addSubfield("x", v950w);
    }

    private void b852z(MarcVblLengthField f852, Vector values_950n) {

        String v950n = null;
        int startNote = 0, vsize = 0;

        if (values_950n != null) {

            vsize = values_950n.size();

            for (int i=0; i < vsize; i++) {

                startNote = -1;

                v950n = (String)values_950n.elementAt(i);

                if (exists(v950n))
                {
                    // add the 950 $n as an 852 $z
                    startNote = v950n.indexOf("#");
                    int endNote = startNote + 4;

                    if (startNote >= 0 && v950n.length() >= endNote)
                    {
                        if (v950n.substring(startNote, endNote).equals("#508") ||
                            v950n.substring(startNote, endNote).equals("#513") ||
                            v950n.substring(startNote, endNote).equals("#541"))
                        {
                            v950n = v950n.substring(endNote, v950n.length());
                            f852.addSubfield("z", v950n);
                        }
                    }
                }
            }
        }
    }

    private void b852zFrom590a(MarcVblLengthField f852) {

        MarcVblLengthField f590 = null;
        MarcSubfield f590a = null;
        String v590a = null;

        // get all 590 fields
        MarcFieldList fields = inMarc.allFields("590");
        int fsize = fields.size();

        if (fsize == 0) return;

        // for each 590 field
        // Note that fsize is the number of fields found
        for (int i=0; i < fsize; i++) {

            f590 = (MarcVblLengthField)fields.elementAt(i);

            f590a = f590.firstSubfield("a");

            v590a = f590a.value();

            f852.addSubfield("z", v590a);
        }
    }

    /**
     * build marc 852s3 from 950v, 950y, 950z field
     */
    private void b852s3(MarcVblLengthField f852, Vector values_950v, MarcVblLengthField f950)
    {
        String v950v = null;
        String v950y = null;
        String v950z = null;
        int    vsize = 0;
        StringBuffer sb = new StringBuffer(200);

        if ( values_950v != null )
        {
            vsize = values_950v.size();

            for ( int i = 0; i < vsize; i++ )
            {
                v950v = (String)values_950v.elementAt(i);

                if ( exists(v950v) )
                {
                    if ( sb.length() > 0 )
                    {
                        sb.append("; ");
                    }
                    sb.append(v950v.trim());
                }
            }
        }

        v950y = f950.firstSubfieldValue("y");
        if ( exists(v950y) )
        {
            if ( sb.length() > 0 )
            {
                sb.append("; Library Lacks: ");
            }
            else
            {
                sb.append("Library Lacks: ");
            }
            sb.append(v950y.trim());
        }

        v950z = f950.firstSubfieldValue("z");
        if ( exists(v950z) )
        {
            if ( sb.length() > 0 )
            {
                sb.append("; ");
            }
            sb.append(v950z);
        }

        if ( sb.length() > 0 )
        {
            // create an 852 $3 subfield using 950 $v, 950 $y, 950 $z
            f852.addSubfield("3", sb.toString());
        }
    }

    // private bib field build methods and utilities

    private void build005()
    {
        MarcFixedLengthField f005 = (MarcFixedLengthField)inMarc.getFirstField("005");

        if (f005 == null) outMarc.getNewFixField("005", fileConvertDate);
    }

    private void build780()
    {
        MarcVblLengthField inField = null, outField = null;

        // get all 780 fields
        MarcFieldList fields = inMarc.allFields("780");
        int fsize = fields.size();

        // for each 780 field
        // Note that fields.size() is the number of fields found
        for (int i = 0; i < fsize; i++)
        {
            // get the field from MarcFieldList
            inField = (MarcVblLengthField)fields.elementAt(i);

            // Remove any $n subfields and add what's left (if anything)
            outField = inField.deleteSubfields("n");
            if (outField == null) break;

            // setfield creates and populates the field
            setField("780", outField.value());

            // output this message to report if 2 <= debug level
            debugOut(2, "***>780n:" + inField);
        }
    }

    private void build785()
    {
        MarcVblLengthField inField = null, outField = null;

        // get all 785 fields
        MarcFieldList fields = inMarc.allFields("785");
        int fsize = fields.size();
        if (fsize == 0) return;

        // for each 785 field
        // Note that fields.size() is the number of fields found
        for (int i=0; i < fsize; i++) {
            // get the field from MarcFieldList
            inField = (MarcVblLengthField)fields.elementAt(i);

            // Remove all $e, $h, $n subfields and add what's left (if anything)
            outField = inField.deleteSubfields("ehn");
            if (outField == null) return;

            //setfield creates and populates the field
            setField("785", outField.value());

            // output this message to report if 2 <= debug level
            debugOut(2, "***>785:" + inField); //!!!
        }
    }

    private void build901()
    {
        // get the 001 field
        String v001 = inMarc.getFirstValue("001","");
        if ( log.isDebugEnabled() )
        {
            log.debug(new StringBuffer(50)
                      .append("v001 = '").append(v001)
                      .append("'").toString());
        }

        if (!exists(v001))
            throw new MarcDropException("Input lacks 001");

        if (v001.length() < 9)
            throw new MarcDropException("Malformed 001 - length less than 9");

        MarcVblLengthField f901 = (MarcVblLengthField)inMarc.getFirstField("901");

        // get the 901 $a field
        String v901a = v001.substring(4,8);

        // get the 901 $b field
        String v901b = null;
        int fmtCodeStart = v001.indexOf('-', 8);
        if (fmtCodeStart > -1)
        {
            v901b = v001.substring(8, fmtCodeStart);
        }
        else
        {
            v901b = v001.substring(8);
        }

        if ( log.isDebugEnabled() )
        {
            log.debug(new StringBuffer(50)
                      .append("v901a = '").append(v901a)
                      .append("' v901b = '").append(v901b)
                      .append("'").toString());
        }

        // if both are not found then return
        if ((v901a == null) || (v901b == null))
        {
            throw new MarcDropException("Bad source or record number");
        }

        // build a string that appends $$ to represent a subfield delimiter
        String outMsg = new StringBuffer(50)
            .append("  $$a").append(v901a)
            .append("$$b").append(v901b).toString();

        if ( log.isDebugEnabled() )
        {
            log.debug("901 outMsg = " + outMsg);
        }

        // create this field in outMarc
        setField("901", outMsg);

        // if the debug level is 2 or more then output this message
        debugOut(3,"901:" + outMsg);
    }

    /*
     * Main method to test the RLIN -> Marc 21 Escape sequences
     * Code esentially lifted from org.cdlib.util.marc.TranslateTable
     * with the data tables replaced.
     */


    public static void main(String[] args)
    {
        String[][] xlatTblRaw =
            {
                {"\u00AF", "\u001B\u0067\u0061\u001B\u0073"},  // Greek alpha
                {"\u00BE", "\u001B\u0067\u0062\u001B\u0073"},  // Greek beta
                {"\u00BF", "\u001B\u0067\u0063\u001B\u0073"},  // Greek gamma

                {"\u00D0", "\u001B\u0062\u0030\u001B\u0073"},  // Subscript 0
                {"\u00D1", "\u001B\u0062\u0031\u001B\u0073"},  // Subscript 1
                {"\u00D2", "\u001B\u0062\u0032\u001B\u0073"},  // Subscript 2
                {"\u00D3", "\u001B\u0062\u0033\u001B\u0073"},  // Subscript 3
                {"\u00D4", "\u001B\u0062\u0034\u001B\u0073"},  // Subscript 4
                {"\u00D5", "\u001B\u0062\u0035\u001B\u0073"},  // Subscript 5
                {"\u00D6", "\u001B\u0062\u0036\u001B\u0073"},  // Subscript 6
                {"\u00D7", "\u001B\u0062\u0037\u001B\u0073"},  // Subscript 7
                {"\u00D8", "\u001B\u0062\u0038\u001B\u0073"},  // Subscript 8
                {"\u00D9", "\u001B\u0062\u0039\u001B\u0073"},  // Subscript 9
                {"\u00DA", "\u001B\u0062\u002B\u001B\u0073"},  // Subscript +
                {"\u00DB", "\u001B\u0062\u002D\u001B\u0073"},  // Subscript -
                {"\u00DC", "\u001B\u0062\u0028\u001B\u0073"},  // Subscript (
                {"\u00DD", "\u001B\u0062\u0029\u001B\u0073"},  // Subscript )

                {"\u00C0", "\u001B\u0070\u0030\u001B\u0073"},  // Superscript 0
                {"\u00C1", "\u001B\u0070\u0031\u001B\u0073"},  // Superscript 1
                {"\u00C2", "\u001B\u0070\u0032\u001B\u0073"},  // Superscript 2
                {"\u00C3", "\u001B\u0070\u0033\u001B\u0073"},  // Superscript 3
                {"\u00C4", "\u001B\u0070\u0034\u001B\u0073"},  // Superscript 4
                {"\u00C5", "\u001B\u0070\u0035\u001B\u0073"},  // Superscript 5
                {"\u00C6", "\u001B\u0070\u0036\u001B\u0073"},  // Superscript 6
                {"\u00C7", "\u001B\u0070\u0037\u001B\u0073"},  // Superscript 7
                {"\u00C8", "\u001B\u0070\u0038\u001B\u0073"},  // Superscript 8
                {"\u00C9", "\u001B\u0070\u0039\u001B\u0073"},  // Superscript 9
                {"\u00CA", "\u001B\u0070\u002B\u001B\u0073"},  // Superscript +
                {"\u00CB", "\u001B\u0070\u002D\u001B\u0073"},  // Superscript -
                {"\u00CC", "\u001B\u0070\u0028\u001B\u0073"},  // Superscript (
                {"\u00CD", "\u001B\u0070\u0029\u001B\u0073"},  // Superscript )

                {"\u00A0", "\u00C1"},  // Script small L
                {"\u00CE", "\u00C0"},  // Degree sign
                {"\u00CF", "\u00C2"},  // Sound rec copy
                {"\u00DE", "\u00C3"},  // Copyright sign
                {"\u00DF", "\u00C4"},  // Musical sharp
                {"\u00FC", "\u00C6"},  // Inverted exclamation
                {"\u00FD", "\u00C5"}   // Inverted question
            };

        String[][] xlatTbl1 =
            {
                {"\\xAF", "\u001B\u0067\u0061\u001B\u0073"},  // Greek alpha
                {"\\xBE", "\u001B\u0067\u0062\u001B\u0073"},  // Greek beta
                {"\\xBF", "\u001B\u0067\u0063\u001B\u0073"},  // Greek gamma

                {"\\xD0", "\u001B\u0062\u0030\u001B\u0073"},  // Subscript 0
                {"\\xD1", "\u001B\u0062\u0031\u001B\u0073"},  // Subscript 1
                {"\\xD2", "\u001B\u0062\u0032\u001B\u0073"},  // Subscript 2
                {"\\xD3", "\u001B\u0062\u0033\u001B\u0073"},  // Subscript 3
                {"\\xD4", "\u001B\u0062\u0034\u001B\u0073"},  // Subscript 4
                {"\\xD5", "\u001B\u0062\u0035\u001B\u0073"},  // Subscript 5
                {"\\xD6", "\u001B\u0062\u0036\u001B\u0073"},  // Subscript 6
                {"\\xD7", "\u001B\u0062\u0037\u001B\u0073"},  // Subscript 7
                {"\\xD8", "\u001B\u0062\u0038\u001B\u0073"},  // Subscript 8
                {"\\xD9", "\u001B\u0062\u0039\u001B\u0073"},  // Subscript 9
                {"\\xDA", "\u001B\u0062\u002B\u001B\u0073"},  // Subscript +
                {"\\xDB", "\u001B\u0062\u002D\u001B\u0073"},  // Subscript -
                {"\\xDC", "\u001B\u0062\u0028\u001B\u0073"},  // Subscript (
                {"\\xDD", "\u001B\u0062\u0029\u001B\u0073"},  // Subscript )

                {"\\xC0", "\u001B\u0070\u0030\u001B\u0073"},  // Superscript 0
                {"\\xC1", "\u001B\u0070\u0031\u001B\u0073"},  // Superscript 1
                {"\\xC2", "\u001B\u0070\u0032\u001B\u0073"},  // Superscript 2
                {"\\xC3", "\u001B\u0070\u0033\u001B\u0073"},  // Superscript 3
                {"\\xC4", "\u001B\u0070\u0034\u001B\u0073"},  // Superscript 4
                {"\\xC5", "\u001B\u0070\u0035\u001B\u0073"},  // Superscript 5
                {"\\xC6", "\u001B\u0070\u0036\u001B\u0073"},  // Superscript 6
                {"\\xC7", "\u001B\u0070\u0037\u001B\u0073"},  // Superscript 7
                {"\\xC8", "\u001B\u0070\u0038\u001B\u0073"},  // Superscript 8
                {"\\xC9", "\u001B\u0070\u0039\u001B\u0073"},  // Superscript 9
                {"\\xCA", "\u001B\u0070\u002B\u001B\u0073"},  // Superscript +
                {"\\xCB", "\u001B\u0070\u002D\u001B\u0073"},  // Superscript -
                {"\\xCC", "\u001B\u0070\u0028\u001B\u0073"},  // Superscript (
                {"\\xCD", "\u001B\u0070\u0029\u001B\u0073"},  // Superscript )
            };

        String[][] xlatTbl2 =
            {
                {"\\xA0", "\u00C1"},  // Script small L
                {"\\xCE", "\u00C0"},  // Degree sign
                {"\\xCF", "\u00C2"},  // Sound rec copy
                {"\\xDE", "\u00C3"},  // Copyright sign
                {"\\xDF", "\u00C4"},  // Musical sharp
                {"\\xFC", "\u00C6"},  // Inverted exclamation
                {"\\xFD", "\u00C5"}   // Inverted question
            };

        TranslateTable tt1 = new TranslateTable();
        if ( tt1.makeMap(xlatTbl1) )
        {
            System.out.println("build translate table1 - okay");
        }
        else
        {
            System.out.println("build translate table1 - falied");
            System.exit(1);
        }

        TranslateTable tt2 = new TranslateTable();
        if ( tt2.makeMap(xlatTbl2) )
        {
            System.out.println("build translate table2 - okay");
        }
        else
        {
            System.out.println("build translate table2 - falied");
            System.exit(1);
        }

        int rawlen = xlatTblRaw.length;
        int xt1len = xlatTbl1.length;
        int xt2len = xlatTbl2.length;
        StringBuffer sb1 = new StringBuffer(rawlen);
        StringBuffer sb2 = new StringBuffer(rawlen);
        StringBuffer sb3 = new StringBuffer(rawlen);

        for ( int i = 0; i < rawlen; i++ )
        {
            sb1.append(xlatTblRaw[i][0]);
            sb2.append(xlatTblRaw[i][1]);
        }

        for ( int i = 0; i < xt1len; i++ )
        {
            sb3.append(xlatTbl1[i][0]);
        }

        for ( int i = 0; i < xt2len; i++ )
        {
            sb3.append(xlatTbl2[i][0]);
        }

        String s1 = sb1.toString();
        String s2 = sb2.toString();
        String s3 = sb3.toString();

        String sout = tt2.applyAll(tt1.applyAll(s1));

        System.out.println("  s1 = '" + s1 + "'");
        System.out.println("  s2 = '" + s2 + "'");
        System.out.println("  s3 = '" + s3 + "'");
        System.out.println("sout = '" + sout + "'");
        System.out.println("================================");

        for ( int i = 0; i < rawlen; i++)
        {
            System.out.println(xlatTblRaw[i][0] + "=="
                               + tt2.applyAll(tt1.applyAll(xlatTblRaw[i][0])));
        }

    }
    // end of main method
}
// end of UCBConvert class
