package org.cdlib.marcconvert;

import java.util.HashSet;
import java.util.Vector;

import org.apache.log4j.Logger;

import org.cdlib.util.marc.MarcException;
import org.cdlib.util.marc.MarcFieldList;
import org.cdlib.util.marc.MarcRecord;
import org.cdlib.util.marc.MarcSubfield;
import org.cdlib.util.marc.MarcVblLengthField;

/**
 * Convert the marc records from the UCR source. UCR is an III source,
 * so the functionality common to all III source convertison may be found
 * in the IIIConvert class, which is the super classs of this class.
 *
 * @author <a href="mailto:rmoon@library.berkeley.edu">Ralph Moon</a>
 * @author <a href="mailto:shawnm@splorkin.com">Shawn McGovern</a>
 * @version $Id: UCRConvert.java,v 1.5 2002/10/31 02:17:32 smcgovrn Exp $
 */
public class UCRConvert extends IIIConvert
{
	/**
	 * log4j Logger for this class.
	 */
    private static Logger log = Logger.getLogger(UCRConvert.class);

	/**
	 * CVS header string.
	 */
    public static final String cvsHeader = "$Header: /cvs/root/melvyl/ei/marcconvert/java/org/cdlib/marcconvert/UCRConvert.java,v 1.5 2002/10/31 02:17:32 smcgovrn Exp $";

	/**
	 * CVS version string.
	 */
    public static final String version = "$Revision: 1.5 $";

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
     * Instantiate a new UCRConvert object. Instantiates the IIIConvert
     * super class as a UCR conversion.
     */
    public UCRConvert()
    {
        super(IIIConvert.UCR);
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
     * @return the conversion status code
     * @see org.cdlib.marcconvert.ConvertConstants
     */
    private int convert()
    {
        int rc = CONVERT_REC_SUCCESS;

        // Set default debug level for UCRConvert
        setDebug(0);

        // Set status for this record
        setStatus(OK);

        // Verify record source
        if ( !verifyCatalogingSource() )
        {
            throw new MarcDropException("Cataloging source not UCR");
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
	 * Create the 852 fields from the holdings information.
	 */
    private void normalizeHoldingsFields()
    {
        boolean       isSerial       = isSerial();  // Is this entry a serial?
        boolean       isMonograph    = !isSerial;   //    or a monograph?
        MarcFieldList holdingsFields = null;        // Field(s) containing holdings information
        int           nLocs          = 0;           // Number of holdings (possibly incl. repeats)

        HashSet       locs   = new HashSet();       // the processed locations
        int           n852s  = 0;                   // number of 852 fields created

        boolean uses920 = false;                    // Flags indicating which fields contain
        boolean uses850 = false;                    //    holdings information
        boolean uses901 = false;                    // Only one of these should be true for
        boolean uses998 = false;                    //    any particular record

        // Determine the holdings fields
        // First check for availability of preferred fields
        if ( isMonograph )
        {
            // Check if 920's contain locations
            holdingsFields = inMarc.allFields("920");
            if (holdingsFields.size() > 0)
            {
                uses920 = true;
                nLocs = holdingsFields.size();
            }
        }
        else
        {   // isSerial
            // Check if 850's contain locations
            holdingsFields = inMarc.allFields("850");
            if (holdingsFields.size() > 0)
            {
                uses850 = true;
                nLocs = holdingsFields.size();
            }
        }

        // If not using 920s or 850s, try the 901
        if ( !uses920 && !uses850 )
        {
            String v901b = inMarc.getFirstValue("901", "b");
            if ( exists(v901b) )
            {
                if ( v901b.equalsIgnoreCase("multi") )
                {
                    Vector v998as = inMarc.getValues("998", "a");
                    if ( v998as != null )
                    {
                        // use 998 field
                        uses998 = true;
                        holdingsFields = inMarc.allFields("998");
                        nLocs = v998as.size();
                    }
                }
                else
                {
                    // use 901 field
                    uses901 = true;
                    holdingsFields = inMarc.allFields("901");
                    nLocs = 1;
                }
            }
        }

        // Iterate over the source fields
        for (int locNr = 0; locNr < nLocs; locNr++)
        {
            MarcVblLengthField source  = null;  // Field containing holdings info
            String             locCode = null;

            if (uses920)
            {
                // Get the location code from the 920 $l
                source = (MarcVblLengthField)holdingsFields.elementAt(locNr);
                locCode = source.firstSubfieldValue("l");
            }
            else if (uses850)
            {
                // Get the location code from the 850 $b
                source = (MarcVblLengthField)holdingsFields.elementAt(locNr);
                locCode = source.firstSubfieldValue("b");
            }
            else if (uses901)
            {
                // Get the location code from the 901 $b
                source = (MarcVblLengthField)holdingsFields.elementAt(0);
                locCode = source.firstSubfieldValue("b");
            }
            else if (uses998)
            {
                // Get the location code from the next 998 $a
                source = (MarcVblLengthField)holdingsFields.elementAt(0);
                Vector v998as = source.subfields("a", true);
                locCode = ((MarcSubfield)v998as.elementAt(locNr)).value();
            }
            else
            {
                continue;
            }

            /*
             * Skip this entry if any of the following are true:
             * 1. there is no location code
             * 2. the locations code begins with "rd"
             * 3. an 852 has already been created for this location
             */

            // Move on if there's no location code
            if ( !exists(locCode)
                 || locCode.startsWith("rd")
                 ||!locs.add(locCode) )
            {
                continue;
            }

            /*
             * Create new 852 for this location
             */

            MarcVblLengthField f852 = outMarc.getNewVblField("852", "  ");
            n852s++;

            // Add 852 $a INSTITUTION CODE
            f852.addSubfield("a", "RVB");

            // Add 852 $b LOCATION CODE
            f852.addSubfield("b", locCode);

            // Add 852 $h, $i, $j CALL NUMBER
            boolean callNrFound = false;

            // First check the preferred sources
            if (uses920)
            {
                // Check 920 $a
                String v920a = source.firstSubfieldValue("a");
                if (exists(v920a)) {
                    f852.addSubfield("j", v920a);
                    callNrFound = true;
                }
            }

            if (!callNrFound && uses850)
            {
                // Check 850 $c
                String v850c = source.firstSubfieldValue("c");
                if (exists(v850c)) {
                    f852.addSubfield("j", v850c);
                    callNrFound = true;
                }
            }

            // If not found in preferred sources, check 099
            if (!callNrFound)
            {
                MarcVblLengthField f099 = (MarcVblLengthField)inMarc.getFirstField("099");
                if (f099 != null)
                {
                    Vector f099As = f099.subfields("a", true);
                    if (f099As != null)
                    {
                        StringBuffer sb = new StringBuffer();
                        for (int i = 0; i < f099As.size(); i++)
                        {
                            String v099a = ((MarcSubfield)f099As.elementAt(i)).value();
                            if (exists(v099a))
                            {
                                sb.append(v099a + " ");
                            }
                        }
                        f852.addSubfield("j", sb.toString().trim());
                        callNrFound = true;
                    }
                }
            }

            // Finally, check the 090 and 050
            if (!callNrFound)
            {
                // Check the 090
                String sfa = inMarc.getFirstValue("090", "a");
                String sfb = inMarc.getFirstValue("090", "b");

                // And as a last resort the 050
                if (!exists(sfa) && !exists(sfb))
                {
                    sfa = inMarc.getFirstValue("050", "a");
                    sfb = inMarc.getFirstValue("050", "b");
                }

                // If $a and a $b
                if (exists(sfa) && exists(sfb))
                {
                    f852.addSubfield("h", sfa);
                    f852.addSubfield("i", sfb);
                    callNrFound = true;
                }

                // If $a only
                if (exists(sfa) && !exists(sfb))
                {
                    f852.addSubfield("j", sfa);
                    callNrFound = true;
                }

                // If $b only
                if (!exists(sfa) && exists(sfb))
                {
                    f852.addSubfield("j", sfb);
                    callNrFound = true;
                }
            }

            // Add 852 $p
            String v963a = inMarc.getFirstValue("963", "a");
            if (exists(v963a) && n852s <= 1)
            {
                f852.addSubfield("p", v963a);
            }

            // Add 852 $z's
            Vector v590as = inMarc.getValues("590", "a");
            if (v590as != null)
            {
                for (int i = 0; i < v590as.size(); i++)
                {
                    String v590a = (String)v590as.elementAt(i);
                    if (exists(v590a))
                    {
                        f852.addSubfield("z", v590a);
                    }
                }
            }

            // Add 852 $3
            if (isMonograph)
            {
                set852s3Mono(n852s, f852);
            }

            if (isSerial)
            {
                set852s3Ser(uses850, n852s, source, f852);
            }

            // Add 852 $D and $O
            if (isSerial)
            {
                set852DOSer(uses850, n852s, source, f852);
            }
        } // end main for-loop
    }// end normalizeHoldingsFields method


    /**
     * Set the 852 $3 subfield for a monograph.
     * @param n852s   count of 852 fields created
     * @param f852 852 target field
     */
    private void set852s3Mono(int n852s,MarcVblLengthField f852)
    {
        Vector v599as = inMarc.getValues("599", "a");
        if (v599as != null && v599as.size() > 0)
        {
            // Output default value for all but first 852
            if (n852s > 1)
            {
                f852.addSubfield("3", "Consult UCR Library Catalog for holdings information.");
            }
            else
            {
                // Concatenate 599 $a's and use as $3 of first 852
                StringBuffer sb = new StringBuffer();
                for (int i = 0; i < v599as.size(); i++)
                {
                    String v599a = (String)v599as.elementAt(i);
                    if (exists(v599a) && v599a.endsWith("."))
                    {
                        v599a = v599a.substring(0, v599a.length() - 1);
                    }

                    if (exists(v599a))
                    {
                        if (i <= 0)
                        {
                            sb.append(v599a);
                        }
                        else
                        {
                            sb.append("; " + v599a);
                        }
                    }
                }

                if (sb.length() > 0)
                {
                    sb.append(".");
                    f852.addSubfield("3", sb.toString());
                }
            }
        }
    }


    /**
     * Set the 852 $3 subfield for a serial.
     * @param uses850 indicate if source is an 850 field
     * @param n852s   count of 852 fields created
     * @param source  source field
     * @param f852 852 target field
     */
    private void set852s3Ser(boolean uses850, int n852s,
                             MarcVblLengthField source,
                             MarcVblLengthField f852)
    {
        boolean sf3Created = false;

        // Check 850 $h's
        if (uses850)
        {
            Vector f850hs = source.subfields("h", true);
            if (f850hs != null && f850hs.size() > 0)
            {
                StringBuffer sb = new StringBuffer();
                for (int i = 0; i < f850hs.size(); i++)
                {
                    String v850h = ((MarcSubfield)f850hs.elementAt(i)).value();
                    if (exists(v850h))
                    {
                        if (i <= 0)
                        {
                            sb.append(v850h);
                        }
                        else
                        {
                            sb.append("; " + v850h);
                        }
                    }
                }

                if (sb.length() > 0)
                {
                    f852.addSubfield("3", sb.toString());
                    sf3Created = true;
                }
            }
        }

        if (!sf3Created)
        {  // Check 599's
            MarcFieldList f599s = inMarc.allFields("599");
            Vector v599as = new Vector();
            for (int i = 0; i < f599s.size(); i++)
            {
                MarcVblLengthField f599 = (MarcVblLengthField)f599s.elementAt(i);
                if (!f599.indicators().startsWith("1"))
                {
                    String v599a = f599.firstSubfield("a").value();
                    if (exists(v599a))
                    {
                        v599as.addElement(v599a);
                    }
                }
            }

            if (v599as.size() > 0)
            {
                // Output default value for all but first 852
                if (n852s > 1)
                {
                    f852.addSubfield("3", "Consult UCR Library Catalog for holdings information.");
                }
                else
                {
                    // Concatenate 599 $a's and use as $3 of first 852
                    StringBuffer sb = new StringBuffer();
                    for (int i = 0; i < v599as.size(); i++)
                    {
                        String v599a = (String)v599as.elementAt(i);
                        if (exists(v599a) && v599a.endsWith("."))
                        {
                            v599a = v599a.substring(0, v599a.length() - 1);
                        }
                        if (exists(v599a))
                        {
                            if (i <= 0)
                            {
                                sb.append(v599a);
                            }
                            else
                            {
                                sb.append("; " + v599a);
                            }
                        }
                    }

                    if (sb.length() > 0)
                    {
                        sb.append(".");
                        f852.addSubfield("3", sb.toString());
                    }
                }
            }
        }
    }


    /**
     * Set the 852 $D and $O subfields. This is done for serials only.
     * @param uses850 indicate if source is an 850 field
     * @param n852s   count of 852 fields created
     * @param source  source field
     * @param f852    852 target field
     */
    private void set852DOSer(boolean uses850, int n852s,
                             MarcVblLengthField source,
                             MarcVblLengthField f852)
    {
        String code = null;
        boolean uses999 = false;

        // Use 850 $f if possible
        if ( uses850 )
        {
            String v850f = source.firstSubfieldValue("f");
            if ( exists(v850f) && !v850f.equals("-") )
            {
                code = v850f;
            }
        }

        // If code not in 850 $f try 999 $d
        if ( !exists(code) )
        {
            code = inMarc.getFirstValue("999", "d");
            if ( exists(code) )
            {
                uses999 = true;
            }
        }

        if ( exists(code) )
        {
            //  Output only once if source is 999
            if ( !uses999 || n852s <= 1 )
            {
                // $D
                if ( "gmopstxy15".indexOf(code) >= 0 )
                {
                    f852.addSubfield("D", "Currently received");
                }
                else if ("irz26".indexOf(code) >= 0)
                {
                    f852.addSubfield("D", "Not currently received");
                }
                else if ("q37".indexOf(code) >= 0)
                {
                    f852.addSubfield("D", "No longer published");
                }
                else if ("l4".indexOf(code) >= 0)
                {
                    f852.addSubfield("D", "Subscription cancelled");
                }

                // $O
                f852.addSubfield("O", code);
            }
        }
    } // end of set852DOSer method

}
