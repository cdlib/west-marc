package org.cdlib.marcconvert;

import java.util.Vector;

import org.apache.log4j.Logger;

import org.cdlib.util.marc.MarcException;
import org.cdlib.util.marc.MarcFieldList;
import org.cdlib.util.marc.MarcRecord;
import org.cdlib.util.marc.MarcSubfield;
import org.cdlib.util.marc.MarcVblLengthField;
import org.cdlib.util.marc.TranslateTable;
import org.cdlib.util.string.StringUtil;

/**
 * Convert records extracted from old Melvyl. Ansel escape translation
 * is perform on all subfields.
 *
 * @author <a href="mailto:mark.reyes@ucop.edu">Mark Reyes</a>
 * @author <a href="mailto:shawnm@splorkin.com">Shawn McGovern</a>
 * @version $Id: UnloadConvert.java,v 1.4 2002/10/31 02:17:32 smcgovrn Exp $
 */
public class UnloadConvert extends MarcConvert
{
	/**
	 * log4j Logger for this class.
	 */
    private static Logger log = Logger.getLogger(UnloadConvert.class);

	/**
	 * CVS header string.
	 */
    public static final String cvsHeader = "$Header: /cvs/root/melvyl/ei/marcconvert/java/org/cdlib/marcconvert/UnloadConvert.java,v 1.4 2002/10/31 02:17:32 smcgovrn Exp $";

	/**
	 * CVS version string.
	 */
    public static final String version = "$Revision: 1.4 $";

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
     * Translate table for ANSEL escape sequences.
     */
    private static final String[][] xlatTbl =
    {
        {"\\xAF", "\u001B\u0062\u0030\u001B\u0073"},
        {"\\x91", "\u001B\u0062\u0031\u001B\u0073"},
        {"\\x92", "\u001B\u0062\u0032\u001B\u0073"},
        {"\\x93", "\u001B\u0062\u0033\u001B\u0073"},
        {"\\x94", "\u001B\u0062\u0034\u001B\u0073"},
        {"\\x95", "\u001B\u0062\u0035\u001B\u0073"},
        {"\\x96", "\u001B\u0062\u0036\u001B\u0073"},
        {"\\x97", "\u001B\u0062\u0037\u001B\u0073"},
        {"\\x98", "\u001B\u0062\u0038\u001B\u0073"},
        {"\\x99", "\u001B\u0062\u0039\u001B\u0073"},
        {"\\x9A", "\u001B\u0062\u002B\u001B\u0073"},
        {"\\x9B", "\u001B\u0062\u002D\u001B\u0073"},
        {"\\x9C", "\u001B\u0062\u0028\u001B\u0073"},
        {"\\x9D", "\u001B\u0062\u0029\u001B\u0073"},
        {"\\xC0", "\u001B\u0070\u0030\u001B\u0073"},
        {"\\xC1", "\u001B\u0070\u0031\u001B\u0073"},
        {"\\xC2", "\u001B\u0070\u0032\u001B\u0073"},
        {"\\xC3", "\u001B\u0070\u0033\u001B\u0073"},
        {"\\xC4", "\u001B\u0070\u0034\u001B\u0073"},
        {"\\xC5", "\u001B\u0070\u0035\u001B\u0073"},
        {"\\xC6", "\u001B\u0070\u0036\u001B\u0073"},
        {"\\xC7", "\u001B\u0070\u0037\u001B\u0073"},
        {"\\xC8", "\u001B\u0070\u0038\u001B\u0073"},
        {"\\xC9", "\u001B\u0070\u0039\u001B\u0073"},
        {"\\xFC", "\u001B\u0070\u002B\u001B\u0073"},
        {"\\xD0", "\u001B\u0070\u002D\u001B\u0073"},
        {"\\xD1", "\u001B\u0070\u0028\u001B\u0073"},
        {"\\x7F", "\u001B\u0070\u0029\u001B\u0073"},
        {"\\xFD", "\u001B\u0067\u0061\u001B\u0073"},
        {"\\xCE", "\u001B\u0067\u0062\u001B\u0073"},
        {"\\xFF", "\u001B\u0067\u0063\u001B\u0073"}
    };


    /**
     * Instantiate a new UnloadConvert object.
     */
    public UnloadConvert()
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


    /**
     * Convert the current marc record. Place the result into the output
     * marc record class variable. The status of the conversion is returned.
     * @return the status code
     * @see org.cdlib.marcconvert.ConvertConstants
     */
    private int convert()
    {
        int rc = CONVERT_REC_SUCCESS;

        //set default debug level for MELVYLConvert
        setDebug(0);

        //set status for this record
        setStatus(OK);

        //move the input leader to output
        moveLeader();

        //move all fields within 000 to 899 to output
        // Specification: 4.1
        moveOut("000", "899");

        // Specification: 4.2
        outMarc.deleteFields("000");  // Drop 000
        outMarc.deleteFields("005");  // Drop 005 - use 902 to build 005
        outMarc.deleteFields("852");  // Drop 852 - use 920 to build 852
        outMarc.deleteFields("9XX");  // Drop 9XX - belt and suspenders, no recs expected.

        // Specification: 4.3
        // build these fields for output.
        build005();     // from 902
        build035();     // from 910

        // Specification: 5.2
        // build location segments.
        build852();     // from 920 and 901

        // Specification: 5.1
        // build maintenance key.
        build901();     // from 901

        // Create the TranslateTable object and use it to do the escape translation
        // on all of the subfields in the output record.
        TranslateTable tt = new TranslateTable();

        if ( tt.makeMap(xlatTbl) )
        {
            outMarc.translateVblFields(tt);
        }
        else
        {
            log.error("Failed to instantiate translate table - escape translations bypassed");
        }

        return rc;
    }


    /**
     * Build marc 005 field from the first 902 field.
     * Specification: 4.3
     *
     * @throws MarcDropException when no 902 $a subfield is found
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
        else
        {
            // if no 902 $a subfield found - reject the record.
            throw new MarcDropException("902 $a subfield not found");
        }
    }


    /**
     * Build the 035 field from the 910 fields with blank indicators.
     */
    private void build035()
    {
        // copy all "910" field to "035" fields with blank indicators
        copyVFields("910", "035", "  ");
    }

    /**
     * Build the 852 fields. The 852 $a subfield is always built from
     * the 901 $a and $c subfield. The other subfields are built from
     * the groups of 920-969 fields.
     *
     * @throws MarcDropException when no 920 field is found
     */
    private void build852()
    {
        MarcFieldList mfl;
        // Do not create an 852 for deleted records
        if ( ! inMarc.isDeleteRecord() )    // ????? DO WE NEED THIS ??????
        {
            // if no 920 field found - reject record. Specification 5.2
            if ( (mfl = inMarc.allFields("920")) == null || mfl.size() == 0)
            {
                throw new MarcDropException("920 field not found.");
            }

            MarcRecord testFields = null;
            int i = 0;
            while ( (testFields = setGroup("920", "969", i++)) != null )
            {
                // Do not create an 852 if the 920 does not exist
                if ( testFields.getFirstField("920") != null )
                {
                    MarcVblLengthField f852 = outMarc.getNewVblField("852", "  ");

                    // Institutional code. Specification 5.2.2.2
                    b852a(f852);        // from 901 $a $c (NR)

                    // Location code. Specification 5.2.2.3
                    b852b(f852);        // from 920 $a (NR)

                    // Non-coded location qualifier. Specification 5.2.2.5
                    b852g(f852);        // from 933 $a (NR)

                    // Call number. Specification 5.2.2.6
                    b852hij(f852);      // from 930 $a (NR)

                    // Call number prefix. Specification 5.2.2.7
                    b852k(f852);        // from 928 $a (R)

                    // Call number suffix. Specification 5.2.2.8
                    b852m(f852);        // from 932 $m (R)

                    // Piece designation. Specification 5.2.2.9
                    b852p(f852);        // from 963 $p (NR)

                    // Nonpublic note. Specification 5.2.2.10/11
                    b852x(f852);        // from 935 $a (NR)

                    // Materials specified. Specification 5.2.2.12
                    b8523(f852);        // from 934 $a and 950 $a (NR)

                    // Serial order/receipt data field. Specification 5.2.2.13
                    b852do(f852);       // from 951 $a and 950 $a (NR)
                }
            }
        }
    }


    /**
     * Build the 852 $a subfield from the 901 $a and $c subfields.
     *
     * @param f852 the 852 field to modify
     * @throws MarcDropException when no 901 field is found
     */
    private void b852a(MarcVblLengthField f852)
    {
        MarcFieldList mfl;

        // if no 901 field - reject the record.
        if ( (mfl = inMarc.allFields("901")) == null || mfl.size() == 0)
        {
            throw new MarcDropException(this, "901 field not found");
        }

        // Extract 901 $a $c
        String v901a = inMarc.getFirstValue("901", "a");
        String v901c = inMarc.getFirstValue("901", "c");

		if ( exists(v901a) && exists(v901c) )
		{
			// Create 852 $a
			if (v901a.equals("Z") && v901c.equals("???"))   // Giannini source
			{
				f852.addSubfield("a", "GIA");
			}
			else
			{
				f852.addSubfield("a", v901c);
			}
		}
    }


    /**
     * Build the 852 $b subfield from the first 920 in the current field group.
     * If the group does not contain a 920 field the $b subfield is not created.
     * Otherwise it gets the value of the first 920 $a subfield.
     *
     * @param f852 the 852 field to modify
     */
    private void b852b(MarcVblLengthField f852)
    {
        // get first (and only) 920 $a subfield values for this group.
        MarcVblLengthField f920 = (MarcVblLengthField)groupMarc.getFirstField("920");
        if (f920 == null)
		{
			return;
		}

        //get first $a
        String v920a = f920.firstSubfieldValue("a");

		if ( exists(v920a) )
		{
			// create the 852 $a from the 920 $b
			f852.addSubfield("b", v920a);
		}
    }


    /**
     * Build the 852 $g subfield from the first 933 in the current field group.
     * If the group does not contain a 933 field the $g subfield is not created.
     * Otherwise it gets the value of the first 933 $a subfield.
     *
     * @param f852 the 852 field to modify
     */
    private void b852g(MarcVblLengthField f852)
    {
        // get first (and only) 933 $a subfield values for this group.
        MarcVblLengthField f933 = (MarcVblLengthField)groupMarc.getFirstField("933");
        if (f933 == null)
		{
			return;
		}

        //get first $a
        String v933a = f933.firstSubfieldValue("a");

		if ( exists(v933a) )
		{
			// create the 852 $g from the 933 $a
			f852.addSubfield("g", v933a);
		}
    }


    /**
     * Build the 852 $h, $i, and $j, subfields from the first 930 in the current
     * field group. If the group does not contain a 930 field the subfields are
     * not created. Also if the 930 $c subfield starts with either 'no call number'
     * or 'call number not reported' the $h, $i, and $j, subfields are not created.
     *
     * @param f852 the 852 field to modify
     */
    private void b852hij(MarcVblLengthField f852)
    {
        // get first (and only) 930 $a subfield values for this group.
        MarcVblLengthField f930 = (MarcVblLengthField)groupMarc.getFirstField("930");
        if (f930 == null)
		{
			return;
		}

        //get $a $b $c
        String v930a = f930.firstSubfieldValue("a");
        String v930b = f930.firstSubfieldValue("b");
        String v930c = f930.firstSubfieldValue("c");

        // preempt call number if $c specifies none
        if ( exists(v930c) )
		{
            if ( StringUtil.startsWithIgnoreCase(v930c, "no call number" ))
			{
				return;
			}

            if ( StringUtil.startsWithIgnoreCase(v930c, "call number not reported" ))
			{
				return;
			}
        }

        // Create 852 $h $i $j
        if ( exists(v930a) && exists(v930b) )
        {
            f852.addSubfield("h", v930a);
            f852.addSubfield("i", v930b);
        }
		else if ( exists(v930a) || exists(v930b) || exists(v930c) )
        {
            // change per RAD 7/25 spec.
            if ( exists(v930a) )
            {
                if ( StringUtil.startsWithIgnoreCase(v930a , "no call number" ))
				{
					return;
				}

                if ( StringUtil.startsWithIgnoreCase(v930a , "call number not reported" ))
				{
					return;
				}

                f852.addSubfield("j", v930a);
            }
			else if ( exists(v930b) )
            {
                if ( StringUtil.startsWithIgnoreCase(v930b , "no call number" ))
				{
					return;
				}

                if ( StringUtil.startsWithIgnoreCase(v930b , "call number not reported" ))
				{
					return;
				}

                f852.addSubfield("j", v930b);
            }
			else
            {
                // combine all $c's to single subfield
                Vector f930s = f930.subfields();
				if ( f930s != null && f930s.size() >0 )
				{
					StringBuffer buf = new StringBuffer();
					for (int j = 0; j < f930s.size(); j++)
					{
						MarcSubfield sf = (MarcSubfield)f930s.elementAt(j);
						if (sf.tag().equals("c"))
						{
							buf.append(sf.value()).append(" ");
						}
					}
					if ( StringUtil.startsWithIgnoreCase(buf.toString(), "no call number" ) )
					{
						return;
					}

					if ( StringUtil.startsWithIgnoreCase(buf.toString(), "call number not reported" ))
					{
						return;
					}

					if (buf.length() > 0)
					{
						f852.addSubfield("j", buf.toString());
					}
				}
            }
        }
		else
        {
            debugOut(2, "Can not create 852 $h $i $j:");
        }
    }


    /**
     * Create the 852 $k subfields from the first $a subfield in each
     * 928 field in the current group. If there are no 928 fields then
     * no $k subfields are created.
     *
     * @param f852 the 852 field to modify
     */
    private void b852k(MarcVblLengthField f852)
    {
        // get all 928 $a subfield values for this group
        // REPLACE following: Will give multiple $a's in one field
        // Vector values = groupMarc.getValues("928", "a");
        // if (values == null) return;

        // get all 928 fields
        MarcFieldList fields = groupMarc.allFields("928");
		if ( fields != null )
		{
			Vector values = new Vector();
			for ( int i = 0; i < fields.size(); i++ )
			{
				// get only first $a subfield for each 928 field
				MarcVblLengthField field = (MarcVblLengthField)fields.elementAt(i);
				String v928a = field.firstSubfield("a").value();
				if (exists(v928a)) values.addElement(v928a);
			}

			if ( values.size() > 0 )
			{
				// add each $a subfield of the 852 field
				f852.addSubfields(values, "k");
			}
		}
    }


    /**
     * Create the 852 $m and $z subfield from each of the 932 fields in
     * the current field group. If there are no 932 fields the no $m or
     * $z subfields are created. When there are 932 fields then the 932
     * $a subfield's value is use the create the $z subfield when the 932
     * $a subfield starts with 'shared purchase', otherwise that value
     * goes into the 852 $m subfield.
     *
     * @param f852 the 852 field to modify
     */
    private void b852m(MarcVblLengthField f852)
    {
        // REPLACE following: Will give multiple $a's in one field
        // get all 932 $a subfield values for this group
        // Vector values = groupMarc.getValues("932", "a");
        // if (values == null) return;

        // get all 932 fields
        MarcFieldList fields = groupMarc.allFields("932");
		if ( fields != null )
		{
			for ( int i = 0; i < fields.size(); i++ )
			{
				// get only first $a subfield for each 932 field
				MarcVblLengthField field = (MarcVblLengthField)fields.elementAt(i);
				String v932a = field.firstSubfield("a").value();
				if (exists(v932a))
				{
					if ( StringUtil.startsWithIgnoreCase(v932a, "shared purchase") )
					{
						// Shared purchase exception. Specification 5.2.2.11
						f852.addSubfield("z", v932a);
					}
					else
					{
						// Call number suffix. Specification 5.2.2.8
						f852.addSubfield("m", v932a);
					}
				}
			}
		}
    }


    /**
     * Create the 852 $p subfield from the first $a subfield in the first
     * 963 field in the current field group. If the group contains no 963
     * field, or the $a subfield is empty, no $p subfield is created.
     *
     * @param f852 the 852 field to modify
     */
    private void b852p(MarcVblLengthField f852)
    {
        // get first (and only) 963 $a subfield values for this group.
        MarcVblLengthField f963 = (MarcVblLengthField)groupMarc.getFirstField("963");
        if (f963 == null)
		{
			return;
		}

        //get first $a
        String v963a = f963.firstSubfieldValue("a");

		if ( exists(v963a) )
		{
			// add $a as $p subfield of the 852 field.
			f852.addSubfield("p", v963a);
		}
    }


    /**
     * Create the 852 $x subfields from the each of the 935 fields found
     * in the current field group. For each 935 all the subfields are
     * (space separated) concatenated into a single string, which value
     * is placed in the 852 $x subfield, unless the second indicator of
     * the 935 is 0, when the value is put into an 852 $z subfield.
     *
     * @param f852 the 852 field to modify
     */
    private void b852x(MarcVblLengthField f852)
    {
        // get all 935 fields for this group
        MarcFieldList fields = groupMarc.allFields("935");
        if (fields == null || fields.size() == 0 )
		{
			return;
		}

        MarcVblLengthField field = null;
        String v935a = null;
        String value = null;
        String indicators = null;

        // for each 935 field
        // Note that fields.size() is the number of fields found
        for (int i=0; i < fields.size(); i++)
        {
            // extract field from MarcFieldList
            field = (MarcVblLengthField)fields.elementAt(i);

            // get value of first $a in this 935 field
            v935a = field.firstSubfieldValue("a");

            // get indicators of this field
            indicators = field.indicators();

            // if invalid indicators then skip
            if ((indicators == null) || (indicators.length() != 2))
			{
				return;
			}

            // Extract all 935 subfields into a single string
            Vector f935 = field.subfields();
			if ( f935 != null && f935.size() > 0 )
			{
				StringBuffer buf = new StringBuffer();
				for (int j = 0; j < f935.size(); j++)
				{
					MarcSubfield sf = (MarcSubfield)f935.elementAt(j);
					if (exists(sf))
					{
						buf.append(sf.value()).append(" ");
					}
				}

				// if second indicator is zero
				if (indicators.substring(1,2).equals("0"))
				{
					// add all 935 subfields as an 852 $z
					f852.addSubfield("z", buf.toString());
				}
				// 2nd indicator is not zero
				else
				{
					// add all 935 subfields as an 852 $x
					f852.addSubfield("x", buf.toString());
				}
			}
        }
    }


    /**
     * Create the 853 $3 subfield from either the first 934 field, or the first
     * 950 field, in the current group. In each case the the $a subfield value
     * of the source field is used to create the 852 $3 subfield.
     * If both a 934 and a 950 are found the record is dropped.
     *
     * @param f852 the 852 field to modify
     * @throws MarcDropException when a 934 and a 950 field both exist
     */
    private void b8523(MarcVblLengthField f852)
    {
        // get first 934/950 $a subfield values for this group.
        MarcVblLengthField f934 = (MarcVblLengthField)groupMarc.getFirstField("934");
        MarcVblLengthField f950 = (MarcVblLengthField)groupMarc.getFirstField("950");
        if (f934 != null && f950 != null)
        {
            // Throw exception.  Specification 5.2.2.12
            throw new MarcDropException(this, "934 $a and 950 $a both exist.  See spec. 5.2.2.12");
        }
        else if (f934 != null)
        {
            //get first $a
            String v934a = f934.firstSubfieldValue("a");

            // add $a as $3 subfield of the 852 field.
            if ( exists(v934a) )
            {
                f852.addSubfield("3", v934a);
            }
        }
        else if (f950 != null)
        {
            //get first $a
            String v950a = f950.firstSubfieldValue("a");

            // add $a as $3 subfield of the 852 field.
            // Exception is when $a contains "holdings not reported".
            // if (! v950a.equalsIgnoreCase("holdings not reported"))
            if ( exists(v950a)
				 &&! StringUtil.startsWithIgnoreCase(v950a , "holdings not reported") )
            {
                f852.addSubfield("3", v950a);
            }
        }
    }


    /**
     * Create 852 subfields $D and $O from the first 951 field in the current
     * field group.
     *
     * @param f852 the 852 field to modify
     */
    private void b852do(MarcVblLengthField f852)
    {
        MarcVblLengthField f951 = (MarcVblLengthField)groupMarc.getFirstField("951");
        if (f951 == null) return;

        String v951b = f951.firstSubfieldValue("b");
        String v951c = f951.firstSubfieldValue("c");

        if ( exists(v951b) )
        {
			String newValue = null;

			// Create $D if necessary.
			if (v951b.equals("3"))
			{
				newValue = "On order.";
			}
			else if (v951b.equals("4"))
			{
				newValue = "Currently received";
			}
			else if (v951b.equals("5"))
			{
				newValue = "Not currently received.";
			}
			else if (v951b.equals("x"))
			{
				newValue = "No longer published.";
			}
			else if (v951b.equals("y"))
			{
				newValue = "Subscription cancelled.";
			}
			else
			{
				// contains "0" or other value.
			}

			if (newValue != null)
			{
				// add new 852 $D field with new value
				f852.addSubfield("D", newValue);
			}
		}

        if ( exists(v951c) )
        {
            // add new 852 $O field with 951 $c value
            f852.addSubfield("O", v951c);
        }
    }


    /**
     * Build the output 901 from the input 901.
     *
     * @throws MarcDropException when there is no input 901 field
     */
    private void build901()
    {
        // get the first 901 field
        MarcVblLengthField f901 = (MarcVblLengthField)inMarc.getFirstField("901");

        if (f901 == null)
        {
            // Reject the record.
            throw new MarcDropException(this, "901 field not found");
        }
        else
        {
            // Create 901
            // Specification: 5.1.1
            MarcVblLengthField out901 = outMarc.getNewVblField("901", "  ");

            // Get the 901 subfields.
            String v901a = f901.firstSubfieldValue("a");
            String v901b = f901.firstSubfieldValue("b");
            String v901c = f901.firstSubfieldValue("c");
            String v901d = f901.firstSubfieldValue("d");

            // Handle OCLC and Giannini exceptions.
            // Specification: 5.1.2
            if (v901a.equals("O"))          // OCLC source
            {
                if (v901b.length() < 8)
                {
                    // Pad with 0's until 8 chars.
                    v901b = "00000000".substring(v901b.length()).concat(v901b);
                }
            }
            else if (v901a.equals("Z") && v901c.equals("???"))  // Giannini source
            {
                // Replace v901c.
                v901c = "GIA";
            }

            // Create 901 $a with $c.
            out901.addSubfield("a", v901c);

            // Create 901 $b with $b.
            out901.addSubfield("b", v901b);

            // Create 901 $d for RLIN source only.
            if (v901d != null)
            {
                out901.addSubfield("d", v901d);
            }
        }
    }

}
