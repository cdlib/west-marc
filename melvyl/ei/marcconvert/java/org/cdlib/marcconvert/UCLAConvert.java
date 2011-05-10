package org.cdlib.marcconvert;

import java.util.Vector;

import org.apache.log4j.Logger;

import org.cdlib.util.marc.MarcException;
import org.cdlib.util.marc.MarcFieldList;
import org.cdlib.util.marc.MarcFixedLengthField;
import org.cdlib.util.marc.MarcRecord;
import org.cdlib.util.marc.MarcSubfield;
import org.cdlib.util.marc.MarcVblLengthField;
import org.cdlib.util.string.StringUtil;

/**
 * Convert the marc records from the UCLA source.
 *
 * @author <a href="mailto:jgarey@library.berkeley.edu">Janet Garey</a>
 * @author <a href="mailto:shawnm@splorkin.com">Shawn McGovern</a>
 * @version $Id: UCLAConvert.java,v 1.7 2002/10/31 02:17:32 smcgovrn Exp $
 */
public class UCLAConvert extends MarcConvert
{
	/**
	 * log4j Logger for this class.
	 */
    private static Logger log = Logger.getLogger(UCLAConvert.class);

	/**
	 * CVS header string.
	 */
    public static final String cvsHeader = "$Header: /cvs/root/melvyl/ei/marcconvert/java/org/cdlib/marcconvert/UCLAConvert.java,v 1.7 2002/10/31 02:17:32 smcgovrn Exp $";

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
     * Instansiate a new UCBConvert object.
     */
    public UCLAConvert()
    {
        super();
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

        //set default debug level for UCLAConvert
        setDebug(0);

        //set status for this record
        setStatus(OK);

        //move the input leader to output
        moveLeader();

        //move all fields within 000 to 899 to output
        moveOut(000, 899);

        outMarc.deleteFields("005");  // Drop 005 - use 902 to build 005
        outMarc.deleteFields("59X");  // Drop 59X
        outMarc.deleteFields("9XX");  // Drop 9XX

        //delete any 852 fields
        outMarc.deleteFields("852");

        //build these fields for output
        build005();
        build007();
        build008();
        build901();
        build035();
        build852();

        debugOut(2, "process output leader:" + outMarc.getLeaderValue());

        return rc;
    }


    /**
     * Build marc 005 field.
     */
    private void build005()
    {
        //get value of first 902 $a
        String v902a = inMarc.getFirstValue("902", "a");

        // if 902 $a field found
        if (v902a != null)
        {
            // create new FIXED 005 field using 902 $a value
            outMarc.getNewFixField("005", v902a);
        }
        // if no 902 $a field found
        else
        {
            // output file using file conversion date
            outMarc.getNewFixField("005", fileConvertDate);
        }
    }


    /**
     * Build a marc 007 field from the $a subfield of any 958 fields with
     * a $T subfield whose value is "007".
     */
    private void build007()
    {
        // get all 958 fields
        MarcFieldList fields = inMarc.allFields("958");

        // for each 958 field
        int fmax = (fields == null) ? 0 : fields.size();
        for ( int i = 0; i < fmax; i++ )
        {
            // get the field from MarcFieldList
            MarcVblLengthField field = (MarcVblLengthField)fields.elementAt(i);

            //get the first subfield "T" in 958
            MarcSubfield subfield = field.firstSubfield("T");

            if ( subfield != null )
            {
                // get the String value of the subfield "T"
                String value = subfield.value();

                // if the value of "T" is 007
                if ( value != null && value.equals("007") )
                {
                    // then get the first "a" subfield
                    subfield = field.firstSubfield("a");

                    if ( subfield != null )
                    {
                        // create a new 007 field with this value
                        outMarc.getNewFixField("007", subfield.value());
                    }
                }
            }
        } // end foreach 902
    }


    /**
     * The 008 field will have already been copied from the input record,
     * but if the 901 $b subfield starts with '04' we want to set the 34th
     * character to '|'.
     */
    private void build008()
    {
        // get the first 901 $b field
        String v901b = inMarc.getFirstValue("901", "b");

        // if value of $b is "04"
        if ( (v901b != null)
             && (v901b.length() > 1)
             && (v901b.substring(0, 2).equals("04")))
        {
            // get the outMarc 008 field
            MarcFixedLengthField f008 = (MarcFixedLengthField)outMarc.getFirstField("008");

            if (f008 != null)
            {
                StringBuffer value = new StringBuffer(f008.value());
                value.setCharAt(33, '|');        // set offset 33 to bar
                f008.setData(value.toString());  // put back data into field
            }
        }
    }


    /**
     * Create the 035 fields from the 910 fields.
     */
    private void build035()
    {
        // copy all "910" field to "035" fields with blank indicators
        copyVFields("910", "035", "  ");
    }

    /**
     * Create the 852 fields from 920-969 field groups.
     * This not done for delete records.
     */
    private void build852()
    {
        // Do not create an 852 for deleted records
        if ( ! inMarc.isDeleteRecord() )
        {
            /* if the input has any 590 fields with a $a subfield that
             * begins with the pharse "shared purchase", case insensitive,
             * we want to create a $z with the value of that $a for each
             * 852. Since there may be many 852 fields created we will
             * do this once, outside the 852 creation loop, and pass the
             * string to a method to create the $z within the loop.
             */
            String sharedPurchase = getSharedPurchase();

            /* Loop through a set of repeating fields with the range 920 and 969
             * setGroup finds each group using an incrementer (here i).
             * The group of fields is then used to build a new Marc record groupMarc
             * that contains only the fields from the group. This allows all of the
             * MarcRecord functions to be available on the restricted group of fields
             */
            MarcRecord testFields = null;
            int i = 0;
            while ( (testFields = setGroup("920", "969", i++)) != null )
            {
                // Do not create an 852 if the 920 does not exist
                if ( testFields.getFirstField("920") != null )
                {
                    String v902a = testFields.getFirstValue("920", "a");

                    if ( "ARTSPCSR".equalsIgnoreCase(v902a)
                         || "URLRFSRL".equalsIgnoreCase(v902a) )
                    {
                        // Bypass 852 creation for either of these conditions
                        continue;
                    }

                    MarcVblLengthField f852 = outMarc.getNewVblField("852", "  ");

                    b852a(f852) ;
                    b852b(f852);
                    b852hij(f852);
                    b852k(f852);
                    b852m(f852);
                    b852p(f852);
                    b852xz(f852);

                    if ( sharedPurchase != null )
                    {
                        b852zsp(f852, sharedPurchase);
                    }

                    b852z(f852);
                    b852DO(f852);
                    b852s3(f852);
                }
            }
        }
    }


    /**
     * Search the 590 $a subfields for any that start with 'shared purchase'.
     * If one is found return that subfield value, otherwise return null.
     *
     * @return the shared purchase 590 $a subfield value, if one is found
     */
    private String getSharedPurchase()
    {
        String sp = null;

        // get all 590 $a subfield values for the input record
        Vector values = inMarc.getValues("590", "a");
        int vmax = (values == null) ? 0 : values.size();

        for ( int i = 0; i < vmax; i++ )
        {
            String temp = (String)values.elementAt(i);

            if ( StringUtil.startsWithIgnoreCase(temp ,"shared purchase") )
            {
                sp = temp;
                break;
            }
        }

        return sp;
    }


    /**
     * Set the 852 $a subfield to 'LAD'.
     *
     * @param f852 the 852 field to modify
     */
    private void b852a(MarcVblLengthField f852)
    {
        // add a $a subfield with LAD
        f852.addSubfield("a", "LAD");
    }


    /**
     * Create the 852 $b subfields from the 920 $a subfields in the the current
     * field group.
     *
     * @param f852 the 852 field to modify
     */
    private void b852b(MarcVblLengthField f852)
    {
        // get all 920 $a subfield values for this group
        Vector values = groupMarc.getValues("920", "a");
        if (values != null)
        {
            // add each $a as a $b subfield of the 852 field
            f852.addSubfields(values, "b");
        }
    }


    /**
     * Create the 852 $h, $i, and $j subfields from the $a, $b, and $c subfields
     * of first 930 in the the current field group.
     *
     * @param f852 the 852 field to modify
     */
    private void b852hij(MarcVblLengthField f852)
    {
        // get first f930 field in groupMarc
        MarcVblLengthField f930 = (MarcVblLengthField)groupMarc.getFirstField("930");
        if (f930 != null)
        {
            String v930a = f930.firstSubfieldValue("a"); //get first $a
            String v930b = f930.firstSubfieldValue("b"); //get first $b

            //if both fields are present
            if ((v930a != null) && (v930b != null))
            {
                // add the $h field to 852 containing $a data
                f852.addSubfield("h", v930a);
                // add the $i field to 852 containing $b data
                f852.addSubfield("i", v930b);
            }

            // get the values of all 930 $c subfields
            Vector values = groupMarc.getValues("930", "c");
            if (values == null)
            {
                // append all of the values together as a single
                // $j with a blank delimiter
                String append = f852.appendSubfields(values, " ");
                // create a new $j subfield - NOTE: if append is null nothing is created
                f852.addSubfield("j", append);
            }
        }
    }


    /**
     * Build the 852 $k subfields from the $a subfields of the 928 fields
     * in the current field group.
     *
     * @param f852 the 852 field to modify
     */
    private void b852k(MarcVblLengthField f852)
    {
        //get all 928 $a subfield values in groupMarc
        Vector values = groupMarc.getValues("928", "a");
        if (values == null)
        {
            // add a $k subfield for each 928 $a subfield found
            f852.addSubfields(values, "k");
        }
    }


    /**
     * Build the 852 $m subfields from the $a subfields of the 932 fields
     * in the current field group.
     *
     * @param f852 the 852 field to modify
     */
    private void b852m(MarcVblLengthField f852)
    {
        // get all 932 $a subfield values in groupMarc
        Vector values = groupMarc.getValues("932", "a");
        if (values != null)
        {
            // add a $m subfield for each 932 $a subfield found
            f852.addSubfields(values, "m");
        }
    }


    /**
     * Build the 852 $p subfield from the first $a subfield of the first 963
     * field in the current field group.
     *
     * @param f852 the 852 field to modify
     */
    private void b852p(MarcVblLengthField f852)
    {
        // get all 963 $a values for this group
        Vector values = groupMarc.getValues("963", "a");

        if (values != null)
        {
            // get the value of the first $a subfield
            String firstSubvalue = (String)values.elementAt(0);
            // add this first 963 $a as an 852 $p
            f852.addSubfield("p", firstSubvalue);
        }
    }


    /**
     * Build the 852 $x and $z subfields from the 935 fields in the current field group.
     *
     * @param f852 the 852 field to modify
     */
    private void b852xz(MarcVblLengthField f852)
    {
        // get all 935 fields for this group
        MarcFieldList fields = groupMarc.allFields("935");
        if (fields == null) return;

        MarcVblLengthField field = null;
        String v935a = null;
        String value = null;
        String indicators = null;

        // for each 935 field
        // Note that fields.size() is the number of fields found
        for (int i = 0; i < fields.size(); i++)
        {
            // extract field from MarcFieldList
            field = (MarcVblLengthField)fields.elementAt(i);

            // get value of first $a in this 935 field
            v935a = field.firstSubfieldValue("a");
            if (v935a == null) return;

            // get indicators of this field
            indicators = field.indicators();

            // if invalid indicators then skip
            if ((indicators == null) || (indicators.length() != 2)) return;

            // if second indicator is zero
            if (indicators.substring(1,2).equals("0"))
            {
                // add this 935 $a as an 852 $z
                f852.addSubfield("z", v935a);
            }
            // 2nd indicator is not zero
            else
            {
                // add the 935 $a as an 852 $x
                f852.addSubfield("x", v935a);
            }
        }
    }


    /**
     * Build the 852 $z from the supplied string.
     *
     * @param f852 the 852 field to modify
     * @param sp   the string to use
     */
    private void b852zsp(MarcVblLengthField f852, String sp)
    {
        if (sp != null)
        {
            // add this string as a $z subfield
            f852.addSubfield("z", sp);
        }
    }


    /**
     * Build the 852 $z subfields from the 958 fields in the current field group.
     *
     * @param f852 the 852 field to modify
     */
    private void b852z(MarcVblLengthField f852)
    {
        // get all 958 fields for this group
        MarcFieldList fields = groupMarc.allFields("958");

        // for each 958 field
        int fmax = (fields == null) ? 0 : fields.size();
        for ( int i = 0; i < fmax; i++ )
        {
            // get the field from MarcFieldList
            MarcVblLengthField field = (MarcVblLengthField)fields.elementAt(i);

            // get a $T subfield class
            MarcSubfield subfield = field.firstSubfield("T");

            if (subfield != null)
            {
                // get the value of the $T class
                String value = subfield.value();

                // if the $T contains "300"
                if (value.equals("300"))
                {
                    //append all NON-$t subfield values in this field
                    //with a blank delimiter
                    String data = field.appendSubfields(false, "T", " ");
                    // add this appended data as a $z subfield
                    f852.addSubfield("z", data);
                }
            }
        } // end foreach 958
    }

    /**
     * Build marc 852 $D and $O subfields from 951 fields in the current field group.
     *
     * @param f852 the 852 field to modify
     */
    private void b852DO(MarcVblLengthField f852)
    {
        MarcFieldList fields = groupMarc.allFields("951");

        // get all 951 fields for this group
        if (fields != null)
        {
            MarcVblLengthField field = null;
            MarcSubfield subfield;
            String value = null;
            String newValue = null;

            int max = fields.size();
            for (int i = 0; i < max; i++)
            {
                // for each 951 field
                // get the field at this increment
                field = (MarcVblLengthField)fields.elementAt(i);
                // get first $c subfield in this field
                value = field.firstSubfieldValue("c");

                // if not found then skip to next 951 field
                if (value == null) continue;
                // set new value based on value of 951 $c
                else if (value.equals("0")) {}
                else if (value.equals("3"))
                    newValue = "On order.";
                else if (value.equals("4"))
                    newValue = "Currently received";
                else if (value.equals("5"))
                    newValue = "Not currently received.";
                else if (value.equals("x"))
                    newValue = "No longer published.";
                else if (value.equals("y"))
                    newValue = "Subscription cancelled.";

                if ((newValue != null) && (newValue.length() > 0))
                {
                    // add new 852 $D field with new value
                    f852.addSubfield("D", newValue);
                    // add new 852 $O field with 951 $c value
                    f852.addSubfield("O", value);
                }
            }
        }
    }


    /**
     * Build the 852 $3 subfield from first 934 field in the current field group.
     *
     * @param f852 the 852 field to modify
     */
    private void b852s3(MarcVblLengthField f852)
    {
        MarcVblLengthField f934 = (MarcVblLengthField) groupMarc.getFirstField("934");

        // Use the $a subfield to create the $3 if the 934 exists
        if ( f934 != null )
        {
            MarcSubfield subfield;
            String value = f934.firstSubfieldValue("a");

            if (value != null)
            {
                f852.addSubfield("3", value);
            }
        }

        // extractMarcField is a powerful method for merging groups of fields
        // and subfields with field and subfield delimiting
        // In this case all 950 $a fields are merged as a single string with
        // "***" used as a delimiter at the field level.
        String extract = groupMarc.extractMarcFields("950", "a", true, " ***", " ");

        // if the field was successfully built
        if ((extract != null) && (extract.length() > 0))
        {
            // move it to 852 $3
            f852.addSubfield("3", extract);
            debugOut(2, "852s32:" + extract);
        }
    }


    /**
     * Build the 901 field from the input 901 field using the $b and $c subfields.
     */
    private void build901()
    {
        // get the first 901 field
        MarcVblLengthField f901 = (MarcVblLengthField)inMarc.getFirstField("901");

        if (f901 == null)
        {
            log.error("Input Record Error: Missing 901 field");
        }
        else
        {
            // get the first 901 $c field
            String v901c = f901.firstSubfieldValue("c");

            // get the first 901 $b field
            String v901b = f901.firstSubfieldValue("b");

            // build the 901 if both are found
            if ( v901b == null )
            {
                log.error("Input Record Error: 901 $b subfield missing");
            }
            else
            {
                if ( ! "LAD".equalsIgnoreCase(v901c) )
                {
                    log.error("Input Record Error: 901 $c subfield missing or invalid");
                }
                else
                {
                    // build a string that appends $$ to represent a subfield delimiter
                    String outMsg = "  $$a" + v901c + "$$b" + v901b;

                    // create this field in outMarc
                    setField("901", outMsg);
                }
            }
        }
    }

}
