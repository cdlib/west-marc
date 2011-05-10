package org.cdlib.marcconvert;

import java.util.HashSet;
import java.util.Vector;

import org.apache.log4j.Logger;

import org.cdlib.util.marc.MarcException;
import org.cdlib.util.marc.MarcFieldList;
import org.cdlib.util.marc.MarcRecord;
import org.cdlib.util.marc.MarcSubfield;
import org.cdlib.util.marc.MarcVblLengthField;
import org.cdlib.util.string.StringUtil;

/**
 * Convert the marc records from the UCI source. UCI is an III source,
 * so the functionality common to all III source convertison may be found
 * in the IIIConvert class, which is the super classs of this class.
 *
 * @author <a href="mailto:rmoon@library.berkeley.edu">Ralph Moon</a>
 * @author <a href="mailto:shawnm@splorkin.com">Shawn McGovern</a>
 * @version $Id: UCIConvert.java,v 1.6 2002/10/31 02:17:32 smcgovrn Exp $
 */
public class UCIConvert extends IIIConvert
{
	/**
	 * log4j Logger for this class.
	 */
    private static Logger log = Logger.getLogger(UCIConvert.class);

	/**
	 * CVS header string.
	 */
    public static final String cvsHeader = "$Header: /cvs/root/melvyl/ei/marcconvert/java/org/cdlib/marcconvert/UCIConvert.java,v 1.6 2002/10/31 02:17:32 smcgovrn Exp $";

	/**
	 * CVS version string.
	 */
    public static final String version = "$Revision: 1.6 $";

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
     * Instantiate a new UCIConvert object. Instantiates the IIIConvert
     * super class as a UCI conversion.
     */
    public UCIConvert()
    {
        super(IIIConvert.UCI);
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

        // Set default debug level for UCIConvert
        setDebug(0);

        // Set status for this record
        setStatus(OK);

        // Verify record source
        if ( ! verifyCatalogingSource() )
        {
            throw new MarcDropException("Cataloging source not UCI");
        }

        // Move the input leader to output
        moveLeader();

        // Move all fields within 000 to 899 to output
        moveOut(000, 899);

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
     * Create the 852 fields from either the 920 field, for monographs,
     * or the 850 field, for serials. Create only one 852 for each location.
     */
    private void normalizeHoldingsFields()
    {
        boolean isSerial    = isSerial();  // Is this entry a serial?
        boolean isMonograph = !isSerial;   //    or a monograph?

        // Get the holdings fields
        MarcFieldList holdingsFields = (isSerial
                                        ? inMarc.allFields("850")
                                        : inMarc.allFields("920"));

        HashSet locs = new HashSet();  // the locations already processed

        // Iterate over the source fields
        while ( holdingsFields.hasMoreElements() )
        {
            MarcVblLengthField f920 = null;
            MarcVblLengthField f850 = null;
            String locCode = null;

            if ( isMonograph )
            {
                // Get the location code from the 920 $l
                f920 = (MarcVblLengthField)holdingsFields.nextElement();
                locCode = f920.firstSubfieldValue("l");
            }
            else
            {
                // Get the location code from the 850 $c
                f850 = (MarcVblLengthField)holdingsFields.nextElement();
                locCode = f850.firstSubfieldValue("c");
            }

            /*
             * Creation Exceptions
             */

            // Move on if no usable location was found
            if ( ! exists(locCode) )
            {
                continue;
            }

            // Move on if 850 $i has value 'n'

            if ( isSerial )
            {
                String v850i = f850.firstSubfieldValue("i");
                if ( exists(v850i) && v850i.startsWith("n") )
                {
                    continue;
                }
            }

            // Check if this location has already been processed
            // and move on if it has
            if ( ! locs.add(locCode) )
            {
                continue;
            }

            // New loc, so add a new 852 to the output
            MarcVblLengthField f852 = outMarc.getNewVblField("852", "  ");

            // Add 852 $a INSTITUTION CODE
            f852.addSubfield("a", "IRB");

            // Add 852 $b LOCATION CODE
            f852.addSubfield("b", locCode);

            // Add 852 $h, $i, $j CALL NUMBER
            if ( isSerial )
            {
                set852jSer(f850, f852);
            }
            else
            {
                set852hijMono(locCode, f920, f852);
            }

            // Add 852 $m
            if ( isMonograph )
            {
                set852mMono(f920, f852);
            }

            // Add 852 $p
            set852p(locCode, f852);

            // Add 852 $x's and $z's
            set852xz(f852);

            // Add 852 $3
            if ( isMonograph )
            {
                set852s3Mono(f852);
            }
            else
            {
                set852s3Ser(f850, f852);
            }

            // Add 852 $D and $O
            if ( isSerial )
            {
                set852DOSer(f850, f852);
            }
        }
    }


    /**
     * Set the 852 $h, $i, and $j subfields for a monograph.
     * @param locCode the location code
     * @param f920 920 source field
     * @param f852 852 target field
     */
    private void set852hijMono(String locCode,
                               MarcVblLengthField f920,
                               MarcVblLengthField f852)
    {
        Vector       sfAs = f920.subfields("a", true);
        MarcSubfield sfB  = f920.firstSubfield("b");

        // Remove empty subfields from the Vector
        for ( int i = 0; i < sfAs.size(); i++)
        {
            if ( ! exists((MarcSubfield)sfAs.elementAt(i)) )
            {
                sfAs.remove(i);
            }
        }

        if ( ! exists(sfB) )
        {
            sfB = null;
        }

        /*
         * Set the call number depending on the number of $a subfields and the
         * existence of the $b subfield. There are five combinations we process.
         * 1. No $a subfield and $b does not exist
         *      - Call number not in 920, set from alternate source.
         * 2. No $a subfield and $b exists
         *      - set $j only, from $b value
         * 3. One $a subfield and $b does not exist
         *      - set $j only, from $a value
         * 4. One $a subfield and $b exists
         *      - set $h from $a value
         *      - set $i from $b value
         *      - do not set $j
         * 5. More than one $a subfield
         *      - set $j only by concatenating all $a subfield values,
         *        space separated, followed by the $b value
         */
        MarcSubfield[] sfAsList = (MarcSubfield[])sfAs.toArray(new MarcSubfield[sfAs.size()]);
        int            sfAsCnt  = sfAsList.length;

        switch ( sfAsCnt )
        {
        case 0:
            if ( sfB == null )
            {
                set852hijMonoNot920Source(locCode, f852);
            }
            else
            {
                f852.addSubfield("j", sfB.value());
            }
            break;

        case 1:
            if ( sfB == null )
            {
                f852.addSubfield("j", ((MarcSubfield)sfAs.firstElement()).value());
            }
            else
            {
                f852.addSubfield("h", ((MarcSubfield)sfAs.firstElement()).value());
                f852.addSubfield("i", sfB.value());
            }
            break;

        default:
            set852hijMonoMultAsf(sfAsList, sfB, f852);
            break;
        }
    }


    /**
     * Set the 852 $j subfields for a monograph whose source contains multiple
     * $a subfields. Set $j by concatenating all $a subfield values, space separated,
     * followed by the $b value.
     *
     * @param sfAs the $a subfields
     * @param sfB  the $b subfield
     * @param f852 852 target field
     */
    private void set852hijMonoMultAsf(MarcSubfield[] sfAsList,
                                      MarcSubfield sfB,
                                      MarcVblLengthField f852)
    {
        int          sfAsCnt = sfAsList.length;
        StringBuffer sb      = new StringBuffer();

        for ( int i = 0; i < sfAsCnt; i++ )
        {
            sb.append(sfAsList[i].value());
            sb.append(' ');
        }

        if ( sfB != null )
        {
            sb.append(sfB.value());
        }

        f852.addSubfield("j", sb.toString());
    }


    /**
     * Set the 852 $h, $i, and $j subfields for a monograph whose source
     * is not the 920 field.
     *
     * @param locCode the location code
     * @param f852    852 target field
     */
    private void set852hijMonoNot920Source(String locCode, MarcVblLengthField f852)
    {
        MarcVblLengthField source = null;

        /*
         * Identify and locate the call number source.
         */
        if ( locCode.startsWith("bm") || locCode.startsWith("mc") )
        {
            String[] prefs = {"099", "096", "060"};
            source = callNrSource(prefs);
        }
        else if ( locCode.startsWith("gp") )
        {
            String[] prefs = {"099", "086", "090", "050"};
            source = callNrSource(prefs);
        }
        else if ( locCode.startsWith("sl") )
        {
            String[] prefs = {"099", "096", "090", "060", "050"};
            source = callNrSource(prefs);
        }
        else
        {
            String[] prefs = {"099", "090", "050"};
            source = callNrSource(prefs);
        }

        /*
         * Set the call number depending on the source tag.
         */
        if ( source != null )
        {
            int srcTagNbr = source.getTagint();

            switch ( srcTagNbr )
            {
            case 50:
            case 60:
                set852hijMono5060(source, f852);
                break;

            case 86:
                set852jMono086(locCode, source, f852);
                break;

            case 90:
            case 96:
            case 99:
                set852jMono09X(source, f852);
                break;

            default:
                // There is no default action to take.
                break;
            }
        }
    }


    /**
     * Set the 852 $h, $i, and $j subfields for a monograph whose source
     * is either the 050, or 060, field. Use the $a and $b subfields from
     * the source field to set the taget fields as follows:<br>
     * 1. Both $a and $b are present<br>
     * <pre>- set $h from the $a value<br>
     * <pre>- set $i from the $b value<br>
     * 2. Only $a is present<br>
     * <pre>- set $j from the $a value<br>
     * 3. Only $b is present<br>
     * <pre>- set $j from the $b value<br>
     *
     * @param locCode the location code
     * @param f852    852 target field
     */
    private void set852hijMono5060(MarcVblLengthField source, MarcVblLengthField f852)
    {
        String a = source.firstSubfieldValue("a");
        String b = source.firstSubfieldValue("b");

        boolean haveA = exists(a);
        boolean haveB = exists(b);

        if ( haveA )
        {
            if ( haveB )
            {
                f852.addSubfield("h", a);
                f852.addSubfield("i", b);
            }
            else
            {
                f852.addSubfield("j", a);
            }
        }
        else
        {
            if ( haveB )
            {
                f852.addSubfield("j", b);
            }
        }
    }


    /**
     * Set the 852 $j subfield for a monograph whose source is the 086 field.
     * The $h and $i subfields are not set for this source.
     * Set the $j subfield using the location code and the $a and $b subfields
     * from the source as follows:<br>
     * 1. if the $a is present set $j to the $a value.<br>
     * 2. if the location code begins with "gp" and the $b is present
     * set the $j to the $b value.<br>
     *
     * @param locCode the location code
     * @param f852    852 target field
     */
    private void set852jMono086(String locCode, MarcVblLengthField source,
                                  MarcVblLengthField f852)
    {
        String a = source.firstSubfieldValue("a");

        if ( exists(a) )
        {
            f852.addSubfield("j", a);
        }
        else if ( locCode.startsWith("gp") )
        {
            String b = source.firstSubfieldValue("b");
            if ( exists(b) )
            {
                f852.addSubfield("j", b);
            }
        }
    }


    /**
     * Set the 852 $j subfield for a monograph whose source is either
     * the 090, 096, or 099, field. Set the $j to the space separated
     * concatenation of the $a and $b subfield in the source field.
     *
     * @param source the location code
     * @param f852    852 target field
     */
    private void set852jMono09X(MarcVblLengthField source, MarcVblLengthField f852)
    {
        // Gather the subfields

        Vector sourceAs = source.subfields("a", true);
        Vector sourceBs = source.subfields("b", true);

        StringBuffer sb = new StringBuffer();
        MarcSubfield sf = null;

        // Concatenate the $a subfield values
        int max = sourceAs.size();
        for ( int i = 0; i < sourceAs.size(); i++ )
        {
            sf = (MarcSubfield)sourceAs.elementAt(i);
            if ( exists(sf) )
            {
                sb.append(sf.value());
                sb.append(' ');
            }
        }

        // Concatenate the $b subfield values
        max = sourceBs.size();
        for ( int i = 0; i < max; i++ )
        {
            sf = (MarcSubfield)sourceBs.elementAt(i);
            if ( exists(sf) )
            {
                sb.append(sf.value());
                sb.append(' ');
            }
        }

        // Add the concatenated value to the 852 $j
        f852.addSubfield("j", sb.toString().trim());
    }


    /**
     * Set the 852 $j subfield for a serial.
     * @param f850 850 source field
     * @param f852 852 target field
     */
    private void set852jSer(MarcVblLengthField f850, MarcVblLengthField f852)
    {
        String v850d = f850.firstSubfieldValue("d");

        if ( exists(v850d) )
        {
            f852.addSubfield("j", v850d);
        }
    }


    /**
     * Set the 852 $m subfield for a monograph.
     * @param f920 920 source field
     * @param f852 852 target field
     */
    private void set852mMono(MarcVblLengthField f920, MarcVblLengthField f852)
    {
        String v920u = f920.firstSubfieldValue("u");

        if ( exists(v920u)
             && StringUtil.indexOfIgnoreCase(v920u, "oversize") >= 0 )
        {
            f852.addSubfield("m", v920u);
        }
    }


    /**
     * Set the 852 $p subfield for both monographs and serials.
     * @param locCode the location code
     * @param f852 852 target field
     */
    private void set852p(String locCode, MarcVblLengthField f852)
    {
        if ( locCode.equalsIgnoreCase("srlf") )
        {
            String v963a = inMarc.getFirstValue("963", "a");

            if ( exists(v963a) )
            {
                f852.addSubfield("p", v963a);
            }
        }
    }


    /**
     * Set the 852 $x and $z subfields for both monographs and serials.
     * @param f852 852 target field
     */
    private void set852xz(MarcVblLengthField f852)
    {
        MarcFieldList localNotes = inMarc.allFields("590");

        while ( localNotes.hasMoreElements() )
        {
            MarcVblLengthField f590 = (MarcVblLengthField)localNotes.nextElement();
            String v590a = f590.firstSubfieldValue("a");

            if ( exists(v590a) )
            {
                if ( StringUtil.startsWithIgnoreCase(v590a, "shared purchase") )
                {
                    f852.addSubfield("z", v590a);
                }
                else
                {
                    f852.addSubfield("x", v590a);
                }
            }
        }
    }


    /**
     * Set the 852 $3 subfield for a monograph.
     * @param f852 852 target field
     */
    private void set852s3Mono(MarcVblLengthField f852)
    {
        String v934a = inMarc.getFirstValue("934", "a");

        if ( exists(v934a) )
        {
            f852.addSubfield("3", v934a);
        }
    }


    /**
     * Set the 852 $3 subfield for a serial.
     * @param f850 850 source field
     * @param f852 852 target field
     */
    private void set852s3Ser(MarcVblLengthField f850, MarcVblLengthField f852)
    {
        String v850g = f850.firstSubfieldValue("g");

        if ( exists(v850g) )
        {
            String v850f = f850.firstSubfieldValue("f");
            String v850h = f850.firstSubfieldValue("h");
            StringBuffer sb = new StringBuffer(200);

            if ( exists(v850f) )
            {
                sb.append(v850f);
                sb.append("; ");
            }

            sb.append(v850g);

            if ( exists(v850h) )
            {
                sb.append("; ");
                sb.append(v850h);
            }

            f852.addSubfield("3", sb.toString());
        }
    }


    /**
     * Set the 852 $D and $O subfields. This is done for serials only.
     * @param f850 850 source field
     * @param f852 852 target field
     */
    private void set852DOSer(MarcVblLengthField f850, MarcVblLengthField f852)
    {
        String v850a = f850.firstSubfieldValue("a");

        if ( v850a != null )
        {
            // $D
            if ( v850a.equals("3") )
            {
                f852.addSubfield("D", "On order");
            }
            else if ( v850a.equals("4") )
            {
                f852.addSubfield("D", "Currently received");
            }
            else if ( v850a.equals("5") )
            {
                f852.addSubfield("D", "Not currently received");
            }
            else if ( v850a.equals("x") )
            {
                f852.addSubfield("D", "No longer published");
            }
            else if ( v850a.equals("y") )
            {
                f852.addSubfield("D", "Subscription cancelled");
            }

            // $O
            f852.addSubfield("O", v850a);
        }
    }


    /*
     * Determine the source of the call number from the supplied
     * list of preferred fields. Returns null if none of the
     * supplied fields exists in the input record.
     */
    private MarcVblLengthField callNrSource(String[] fldTags)
    {
        MarcVblLengthField fRet = null;

        for ( int i = 0; i < fldTags.length; i++ )
        {
            if ( (fRet = (MarcVblLengthField)inMarc.getFirstField(fldTags[i])) != null )
            {
                break;
            }
        }

        return fRet;
    }

}
