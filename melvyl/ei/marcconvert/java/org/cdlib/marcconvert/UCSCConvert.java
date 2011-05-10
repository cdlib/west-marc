package org.cdlib.marcconvert;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Vector;

import org.apache.log4j.Logger;

import org.cdlib.util.marc.MarcException;
import org.cdlib.util.marc.MarcFieldList;
import org.cdlib.util.marc.MarcRecord;
import org.cdlib.util.marc.MarcSubfield;
import org.cdlib.util.marc.MarcVblLengthField;

/**
 * Convert the marc records from the UCSC source. UCSC is an III source,
 * so the functionality common to all III source convertison may be found
 * in the IIIConvert class, which is the super classs of this class.
 *
 * @author <a href="mailto:rmoon@library.berkeley.edu">Ralph Moon</a>
 * @author <a href="mailto:shawnm@splorkin.com">Shawn McGovern</a>
 * @version $Id: UCSCConvert.java,v 1.6 2008/01/28 23:21:45 rkl Exp $
 */
public class UCSCConvert extends IIIConvert
{
	/**
	 * log4j Logger for this class.
	 */
    private static Logger log = Logger.getLogger(UCSCConvert.class);

	/**
	 * CVS header string.
	 */
    public static final String cvsHeader = "$Header: /cvs/root/melvyl/ei/marcconvert/java/org/cdlib/marcconvert/UCSCConvert.java,v 1.6 2008/01/28 23:21:45 rkl Exp $";

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
     * Instantiate a new UCSCConvert object. Instantiates the IIIConvert
     * super class as a UCSC conversion.
     */
    public UCSCConvert()
    {
        super(IIIConvert.UCSC);
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
     *
     * @return the status code
     * @see org.cdlib.marcconvert.ConvertConstants
     */
    private int convert()
    {
        int rc = CONVERT_REC_SUCCESS;

        recNbr++;

        // Set default debug level for UCSCConvert
        setDebug(0);

        // Set status for this record
        setStatus(OK);

        // Verify record source
        if ( !verifyCatalogingSource() )
        {
            throw new MarcDropException("Cataloging source not UCSC");
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

        return rc;
    }


	/**
	 * Create the 852 fields from the holdings information.
	 */
    private void normalizeHoldingsFields()
    {
        boolean isSerial    = isSerial();  // Is this record a Serial?
        boolean isMonograph = !isSerial;   // Is this record a Monograph?
        boolean uses850s    = false;       // True if holdings in 850's
        boolean uses920s    = false;       // True if holdings in 920's

        /*
         * Serials use 850's if first 850 has a $b, otherwise use 920's.
         * Monographs always use 920's for holdings.
         */
        if ( isSerial && exists(inMarc.getFirstValue("850", "b")) )
        {
            uses850s = true;
        }
        else
        {
            uses920s = true;
        }

        /*
         * Get the list of holdings from either the 850 or the 920 fields.
         */
        MarcFieldList holdingsFields = (uses850s
                                        ? inMarc.allFields("850")
                                        : inMarc.allFields("920"));

        HashSet locs  = new HashSet();  // Set of locations already processed
        int     n852s = 0;              // Number of 852's have created

        /*
         * Create an 852 for each holdings field, with some exceptions.
         *
         * Do not create an 852 if any of the following are true:
         * 1. An 852 has already been created for this location
         * 2. The first character of the location is 'x', e.g. xmedia
         * 3. Item is a Serial using 850 fields and the 850 $f is '1' (one)
         * 4. Item is a Serial using 850 fields and the location is 'nrlf',
         *    but another valid location exists in this record
         */
        for (int locNr = 0; locNr < holdingsFields.size(); locNr++)
        {
            MarcVblLengthField f920 = null;
            MarcVblLengthField f850 = null;
            String locCode = null;

            if (isMonograph)
            {
                // Get the location code from the 920 $l
                f920 = (MarcVblLengthField)holdingsFields.elementAt(locNr);
                locCode = f920.firstSubfieldValue("l");
            }
            else if (isSerial && uses850s)
            {
                // Take the location from the 850 $b unless the 850 $f is '1' (one),
                // in which case the location should be ignored
                f850 = (MarcVblLengthField)holdingsFields.elementAt(locNr);
                locCode = f850.firstSubfieldValue("b");
                if (exists(locCode))
                {
                    String v850f = f850.firstSubfieldValue("f");
                    if (exists(v850f) && !v850f.equals("1"))
                    {
                        locCode = null; // Force program to ignore this 850
                    }
                }
            }
            else if (isSerial && uses920s)
            {
                // Get the location code from the 920 $l
                f920 = (MarcVblLengthField)holdingsFields.elementAt(locNr);
                locCode = f920.firstSubfieldValue("l");
                if (exists(locCode) && locCode.equals("nrlf"))
                {
                    // Ignore nrlf location if there are other non-nrlf locations
                    for (int i = 0; i < holdingsFields.size(); i++)
                    {
                        MarcVblLengthField f = (MarcVblLengthField)holdingsFields.elementAt(i);
                        String l = f.firstSubfieldValue("l");
                        if ( exists(l) && !l.equals("nrlf") && (l.charAt(0) != 'x') )
                        {
                            if ( log.isDebugEnabled() )
                            {
                                log.debug("ignoring nrlf - found location '" + l + "'");
                            }

                            locCode = null; // Force program to ignore this 850
                            break;
                        }
                    }
                }
            }

            /*
             * Creation Exceptions
             */

            // Move on if no usable loc was found
            if (!exists(locCode)) continue;

            if ( log.isDebugEnabled() )
            {
                log.debug("location = '" + locCode + "'");
            }

            // Move on if location code begins with 'x'
            if (locCode.startsWith("x")) continue;

            // Check if this loc has already been processed
            // and move on if it has
            if (!locs.add(locCode)) continue;

            // New loc, so add a new 852 to the output

            MarcVblLengthField f852 = outMarc.getNewVblField("852", "  ");
            n852s++;


            // Add 852 $a INSTITUTION CODE

            f852.addSubfield("a", "SCB");


            // Add 852 $b LOCATION CODE

            f852.addSubfield("b", locCode);


            // Add 852 $g NON-CODED LOCATION QUALIFIER

            if (isSerial) {
                String g = uses850s ?
                        f850.firstSubfieldValue("g") : inMarc.getFirstValue("857", "a");
                if (exists(g)) f852.addSubfield("g", "NRLF: " + g);
            }


            // Add 852 $h, $i, $j CALL NUMBER

            boolean callNrFound = false;

            // Serials should use the 850 $c if available

            if (isSerial && uses850s) {
                String v850c = f850.firstSubfieldValue("c");
                if (exists(v850c)) {
                    f852.addSubfield("j", v850c);
                    callNrFound = true;
                }
            }

            // Monographs should use 920 $a and/or $b if present

            if (isMonograph)
            {
                Vector sfAs = f920.subfields("a", true);
                MarcSubfield sfB = f920.firstSubfield("b");

                // Remove any bad data

                for (int i = 0; i < sfAs.size(); i++) {
                    if (!exists((MarcSubfield)sfAs.elementAt(i)))
                        sfAs.remove(i);
                }

                if (!exists(sfB)) sfB = null;

                if ( log.isDebugEnabled() )
                {
                    log.debug("mono: sfAs.size = " + sfAs.size() + " sfB = " + sfB);
                }

                //  Call number in 920

                // One $a and one $b

                if (sfAs.size() == 1 && sfB != null) {
                    f852.addSubfield("h", ((MarcSubfield)sfAs.firstElement()).value());
                    f852.addSubfield("i", sfB.value());
                    callNrFound = true;
                }

                // One $a only

                if (sfAs.size() == 1 && sfB == null) {
                    f852.addSubfield("j", ((MarcSubfield)sfAs.firstElement()).value());
                    callNrFound = true;
                }

                // One $b only

                if (sfAs.size() == 0 && sfB != null) {
                    f852.addSubfield("j", sfB.value());
                    callNrFound = true;
                }

                // Multiple $a's

                if (sfAs.size() > 1) {
                    StringBuffer sb = new StringBuffer();
                    for (int i = 0; i < sfAs.size(); i++)
                        sb.append(((MarcSubfield)sfAs.elementAt(i)).value() + "  ");
                    if (sfB != null)
                        sb.append(sfB.value());
                    f852.addSubfield("j", sb.toString().trim());
                    callNrFound = true;
                }
            }

            // Use 099, etc., as last resort

            if (!callNrFound)
            {
                // Identify source of call number

                MarcVblLengthField source;
                if (locCode.equals("mgus")
                    || locCode.equals("mmaps")
                    || locCode.equals("smaps"))
                {
                    String[] prefs = {"099", "090", "086", "050"};
                    source = callNrSource(prefs);
                }
                else
                {
                    String[] prefs = {"099", "090", "050", "086"};
                    source = callNrSource(prefs);
                }

                // Source is 050 or 090

                if ( log.isDebugEnabled() )
                {
                    if ( source == null )
                    {
                        log.debug("cnbr: source is null");
                    }
                    else
                    {
                        log.debug("cnbr: source.tag = " + source.tag());
                    }
                }

                if (source != null
                    && (source.tag().equals("050")
                        || source.tag().equals("090")))
                {
                    String a = source.firstSubfieldValue("a");
                    String b = source.firstSubfieldValue("b");

                    // Both $a and $b present
                    if (exists(a) && exists(b)) {
                        f852.addSubfield("h", a);
                        f852.addSubfield("i", b);
                    }

                    // $a only
                    if (exists(a) && !exists(b))
                        f852.addSubfield("j", a);

                    // $b only
                    if (!exists(a) && exists(b))
                        f852.addSubfield("j", b);
                }

                // Call number source is 086

                if (source != null && source.tag().equals("086"))
                {
                    if ( log.isDebugEnabled() )
                    {
                        Vector sf86as = source.subfields("a", true);
                        Enumeration enum86a = sf86as.elements();
                        while ( enum86a.hasMoreElements() )
                        {
                            log.debug("cnbr: 086 $a = "
                                      + ((MarcSubfield)enum86a.nextElement()).value());
                        }
                    }

                    String a = source.firstSubfieldValue("a");
                    if (exists(a))
                    {
                        if ( log.isDebugEnabled() )
                        {
                            log.debug("cnbr: setting $j = " + a);
                        }
                        f852.addSubfield("j", a);
                    }
                }

                // Call number source is 099

                if (source != null && source.tag().equals("099"))
                {
                    // Gather the subfields

                    Vector sourceAs = source.subfields("a", true);
                    MarcSubfield sfB = source.firstSubfield("b");

                    // Remove any bad data

                    for (int i = 0; i < sourceAs.size(); i++) {
                        if (!exists((MarcSubfield)sourceAs.elementAt(i)))
                            sourceAs.remove(i);
                    }

                    if (!exists(sfB)) sfB = null;

                    // Serials use first $a and possibly a $b

                    if (isSerial && sourceAs.size() > 0) {
                        String v099a = ((MarcSubfield)sourceAs.elementAt(0)).value();
                        if (sfB == null)
                            f852.addSubfield("j", v099a);
                        else {
                            f852.addSubfield("h", v099a);
                            f852.addSubfield("i", sfB.value());
                        }
                    }
                    else if (isMonograph && sourceAs.size() > 0) {
                        StringBuffer sb = new StringBuffer();
                        MarcSubfield sf = null;

                        for (int i = 0; i < sourceAs.size(); i++) {
                            sf = (MarcSubfield)sourceAs.elementAt(i);
                            sb.append(sf.value() + " ");
                        }

                        // Add the concatenated value to the 852 $j

                        f852.addSubfield("j", sb.toString().trim());
                    }
                }
            }


            // Add 852 $m

            if (isSerial)
            {
                if (uses850s)
                {
                    Vector esubs = f850.subfield("e");
                    if ( esubs != null )
                    {
                        Enumeration enu = esubs.elements();
                        while ( enu.hasMoreElements() )
                        {
                            String v850e = ((MarcSubfield)enu.nextElement()).value();
                            if (exists(v850e))
                            {
                                f852.addSubfield("m", v850e);
                            }
                        }
                    }
                }
                else
                {
                    MarcFieldList mfl = inMarc.allFields("858");
                    if ( mfl != null )
                    {
                        Enumeration enu = mfl.elements();
                        while ( enu.hasMoreElements() )
                        {
                            String v858a =
                                ((MarcVblLengthField)enu.nextElement()).firstSubfieldValue("a");
                            if (exists(v858a))
                            {
                                f852.addSubfield("m", v858a);
                            }
                        }
                    }
                }
            }


            // Add 852 $x's and $z's

            MarcFieldList localNotes = inMarc.allFields("590");
            while (localNotes.hasMoreElements()) {
                MarcVblLengthField f590 = (MarcVblLengthField)localNotes.nextElement();
                String v590a = f590.firstSubfieldValue("a");
                if (!exists(v590a)) continue;
                if (v590a.toLowerCase().startsWith("shared purchase"))
                    f852.addSubfield("z", v590a);
                else if (v590a.toLowerCase().indexOf("has ") < 0 &&
                        v590a.toLowerCase().indexOf("has: ") < 0)
                    f852.addSubfield("x", v590a);
            }

            if (isMonograph) {
                MarcFieldList f934s = inMarc.allFields("934");
                while (f934s.hasMoreElements()) {
                    MarcVblLengthField f934 = (MarcVblLengthField)f934s.nextElement();
                    String v934a = f934.firstSubfieldValue("a");
                    if (exists(v934a) && v934a.toLowerCase().indexOf("has ") < 0 &&
                            v934a.toLowerCase().indexOf("has: ") < 0)
                        f852.addSubfield("x", v934a);
                }
            }


            // Add 852 $3 MATERIALS SPECIFIED (SUMMARY HOLDINGS)

            // Monograph summary holdings

            if (n852s < 2 && isMonograph) {

                boolean sumFound = false;

                MarcFieldList f934s = inMarc.allFields("934");
                while (f934s.hasMoreElements()) {
                    MarcVblLengthField f934 = (MarcVblLengthField)f934s.nextElement();
                    String v934a = f934.firstSubfieldValue("a");
                    if (exists(v934a) && (v934a.toLowerCase().indexOf("has ") >= 0 ||
                            v934a.toLowerCase().indexOf("has: ") >= 0)) {
                        f852.addSubfield("3", v934a);
                        sumFound = true;
                        break;
                    }
                }

                if (!sumFound) {
                    MarcFieldList f590s = inMarc.allFields("590");
                    while (f590s.hasMoreElements()) {
                        MarcVblLengthField f590 = (MarcVblLengthField)f590s.nextElement();
                        String v590a = f590.firstSubfieldValue("a");
                        if (exists(v590a) && (v590a.toLowerCase().indexOf("has ") >= 0 ||
                                v590a.toLowerCase().indexOf("has: ") >= 0)) {
                            f852.addSubfield("3", v590a);
                            break;
                        }
                    }
                }
            }

            // Serial summary holdings

            if (isSerial && uses850s) {
                String v850h = f850.firstSubfieldValue("h");
                if (exists(v850h)) {
                    StringBuffer sb = new StringBuffer(v850h);

                    // Check for index information
                    String v850j = f850.firstSubfieldValue("j");
                    if (exists(v850j)) {
                        if (v850j.toLowerCase().startsWith("index: "))
                            sb.append("; ");
                        else
                            sb.append("; Index: ");
                        sb.append(v850j);
                    }
                    f852.addSubfield("3", sb.toString());
                }
            }

            if (isSerial && uses920s) {

                MarcFieldList f850s = inMarc.allFields("850");
                while (f850s.hasMoreElements()) {

                    MarcVblLengthField f = (MarcVblLengthField)f850s.nextElement();
                    String v850a = f.firstSubfieldValue("a");

                    if (exists(v850a)) {
                        StringBuffer sb = new StringBuffer(v850a);

                        // Check for index information
                        MarcFieldList f855s = inMarc.allFields("855");
                        while (f855s.hasMoreElements()) {

                            MarcVblLengthField f855 = (MarcVblLengthField)f855s.nextElement();
                            String v855a = f855.firstSubfieldValue("a");
                            if (exists(v855a)) {
                                if (v855a.toLowerCase().startsWith("index: "))
                                    sb.append("; ");
                                else
                                    sb.append("; Index: ");
                                sb.append(v855a);
                                break;
                            }
                        }
                        f852.addSubfield("3", sb.toString());
                        break;
                    }
                }
            }


            // Add 852 $D and $O

            if (isSerial)
            {
                if (uses850s && exists(f850.firstSubfield("3")))
                {
                    String v8503 = f850.firstSubfieldValue("3");
                    if ( log.isDebugEnabled() )
                    {
                        log.debug("locCode " + locCode + " $3 = '" + v8503 + "'");
                    }

                    // $D
                    if (v8503.equals("3"))
                        f852.addSubfield("D", "On order");
                    else if (v8503.equals("1") || v8503.equals("a"))
                        f852.addSubfield("D", "Currently received");
                    else if (v8503.equals("2") || v8503.equals("z"))
                        f852.addSubfield("D", "Not currently received");
                    else if (v8503.equals("4"))
                        f852.addSubfield("D", "No longer published");
                    else if (v8503.equals("5"))
                        f852.addSubfield("D", "Subscription cancelled");

                    // $O
                    f852.addSubfield("O", v8503);
                }
                else if (uses920s || (uses850s && n852s <= 1))
                {
                    String v901c = inMarc.getFirstValue("901", "c");
                    if (exists(v901c))
                    {
                        // $D
                        if (v901c.equals("3"))
                            f852.addSubfield("D", "On order");
                        else if (v901c.equals("1") || v901c.equals("a"))
                            f852.addSubfield("D", "Currently received");
                        else if (v901c.equals("2") || v901c.equals("z"))
                            f852.addSubfield("D", "Not currently received");
                        else if (v901c.equals("4"))
                            f852.addSubfield("D", "No longer published");
                        else if (v901c.equals("5"))
                            f852.addSubfield("D", "Subscription cancelled");

                        // $O
                        f852.addSubfield("O", v901c);
                    }
                }
            }
        }
    }


    /*
     * Determine the source of the call number from the supplied
     * list of preferred fields. Returns null if none of the
     * supplied fields exists in the input record.
     */
    private MarcVblLengthField callNrSource(String[] fldTags)
    {
        for (int i = 0; i < fldTags.length; i++)
        {
            MarcVblLengthField f = (MarcVblLengthField)inMarc.getFirstField(fldTags[i]);
            if (f != null) return f;
        }
        return null;
    }

}
