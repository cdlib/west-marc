package org.cdlib.marcconvert;

import java.util.Enumeration;
import java.util.Vector;
import java.util.HashSet;

import org.apache.log4j.Logger;
import org.apache.regexp.RE;
import org.apache.regexp.RESyntaxException;

import org.cdlib.util.marc.MarcException;
import org.cdlib.util.marc.MarcFieldList;
import org.cdlib.util.marc.MarcRecord;
import org.cdlib.util.marc.MarcSubfield;
import org.cdlib.util.marc.MarcVblLengthField;


/**
 * Convert the marc records from the UCSF source. UCSF is an III source,
 * so the functionality common to all III source convertison may be found
 * in the IIIConvert class, which is the super classs of this class.
 *
 * @author <a href="mailto:gmills@library.berkeley.edu">Garey Mills</a>
 * @author <a href="mailto:shawnm@splorkin.com">Shawn McGovern</a>
 * @version $Id: UCSFConvert.java,v 1.8 2008/03/06 18:01:00 aleph16 Exp $
 */
public class UCSFConvert extends IIIConvert
{
	/**
	 * log4j Logger for this class.
	 */
    private static Logger log = Logger.getLogger(UCSFConvert.class);

	/**
	 * CVS header string.
	 */
    public static final String cvsHeader = "$Header: /cvs/root/melvyl/ei/marcconvert/java/org/cdlib/marcconvert/UCSFConvert.java,v 1.8 2008/03/06 18:01:00 aleph16 Exp $";

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
     * Private Inner Class - UCSFPseudoSubField
     */
    private class UCSFPseudoSubField
    {
        private String tag;
        private String value;

        UCSFPseudoSubField()
        {
            tag = null;
            value = null;
        }

        UCSFPseudoSubField(String t, String v)
        {
            tag = t;
            value = v;
        }

        public String getTag()
        {
            return tag;
        }

        public void setTag(String t)
        {
            tag = t;
        }

        public String getValue()
        {
            return value;
        }

        public void setValue(String v)
        {
            value = v;
        }
    } // end of UCSFPseudoSubField inner class


    private boolean hasRedsg = false;

    //5.2.2.7
    private String prefixRegexpString
        = new String("^\\s*(ff|f|t)(.*)");
    //5.2.2.8
    private String suffixRegexpString
        = new String("(anesth\\.|berne|calif\\.|case 38|history office|homeop\\.|open shelves|osler|ref\\.)");
    //5.2.2.10
    private String nonPublicNotesRegexpString
        = new String("(^\\s*copies|^\\s*shared purchase|^\\s*\\**\\s*library has|library keeps)");
    //5.2.2.11
    private String publicNotesRegexpString
        = new String("^\\s*shared purchase");
    //5.2.2.12
    private String monographSummaryHoldingsRegexpString
        = new String("^\\s*(\\**library has|library keeps)");
    //5.2.2.12
    private String serialSummaryHoldingsRegexpString
        = new String("\\s*\\**library has:\\s*(.*)");
    //5.2.2.8
    private String serial590No850RegexpString
        = new String("library keeps");
    //5.2.2.3
    private String numberOfCopiesRegexpString
        = new String("\\s*\\(\\d+\\)\\s*(\\w+)");

    //5.2.2.8
    private String[][] suffixes
        = {{"anesth.", "ANESTH."},
           {"berne", "Berne"},
           {"calif.", "CALIF."},
           {"case 38", "Case 38"},
           {"history office", "History Office"},
           {"homeop.", "HOMEOP."},
           {"open shelves", "Open Shelves"},
           {"osler", "OSLER"},
           {"ref.", "REF."}
        };

    //5.2.2.13
    private String[][] serialOrderReceiptTrans
        = {{"3", "On order"},
           {"4", "Currently received"},
           {"5", "Not currently received"},
           {"x", "Ceased"},
           {"y", "Cancelled"}
        };

    //5.2.2.12
    private String localHoldings = "For holdings, consult UCSF local catalog.";


    /**
     * Instantiate a new UCSCConvert object. Instantiates the IIIConvert
     * super class as a UCSC conversion.
     */
    public UCSFConvert()
    {
        super(IIIConvert.UCSF);
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
    private int convert()
    {
        int rc = CONVERT_REC_SUCCESS;
        // Set default debug level for UCSDConvert
        setDebug(0);

        // Set status for this record
        setStatus(OK);

        // Verify record source
        if (!verifyCatalogingSource())
        {
            throw new MarcDropException("Cataloging source not UCSF");
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

    private void normalizeHoldingsFields()
    {
        if ( isSerial() )
        {
            MarcFieldList fv852 = inMarc.allFields("852");
            MarcFieldList fv850 = inMarc.allFields("850");
            if (fv852.size() > 0)
	    {
		processSerial852();
	    }
            else if (fv850.size() > 0)
            	{
               		 processSerial850();
            	}
            	else
            	{
               		 processSerial920();
            	}
        }
        else
        {
            processMonograph();
        }
    }

    private void processMonograph()
    {
        Vector v852s = start852sFrom920s();
        if (v852s != null)
        {
            addFirstM(v852s); //5.2.2.8
            handleCallNumbersNo850(v852s); //5.2.2.6
            handle595s(v852s); //5.2.2.1, 5.2.2.8
            handleNonPublicNotes(v852s); //5.2.2.10
            handlePublicNotes(v852s); //5.2.2.11
            handleMonographSummaryHoldings(v852s); //5.2.2.12
        }
    }

    private void processSerial852()
    {
	Vector v852s = start852sFrom852s();
    }

    private void processSerial850()
    {
        Vector v852s = start852sFrom850s(); //5.2.2.2, 5.2.2.3, 5.2.2.6, 5.2.2.7, 5.2.2.12, 5.2.2.8, 5.2.2.13
        if (v852s != null)
        {
            handle595s(v852s); //5.2.2.1, 5.2.2.8
            handleNonPublicNotes(v852s); //5.2.2.10
            handlePublicNotes(v852s); //5.2.2.11
        }
    }

    private void processSerial920()
    {
        Vector v852s = start852sFrom920s();
        if (v852s != null)
        {
            addFirstM(v852s); //5.2.2.8
            handleCallNumbersNo850(v852s); //5.2.2.6
            handle595s(v852s); //5.2.2.1, 5.2.2.8
            handleLibraryKeeps(v852s); //5.2.2.8
            handleNonPublicNotes(v852s); //5.2.2.10
            handlePublicNotes(v852s); //5.2.2.11
            handleSerialSummaryHoldingsNo850(v852s); //5.2.2.12
        }
    }

    //5.2.2.3
    private Vector start852sFrom920s()
    {
        Vector new852s = new Vector();
        MarcVblLengthField target;

	//If more than one 920, use the first and ignore the rest.
	MarcVblLengthField first920 = (MarcVblLengthField)inMarc.getFirstField("920");

	if (first920 == null)
	{
		return null;
	}
	//Find out how many subfield "l" in this 920 field.
	Vector subfields_l = first920.subfields("l", true);
	if (subfields_l.isEmpty())
	{
		return null;
	}

	//Take unique locations only; get rid of duplicate locations.
	HashSet locs_hashSet = new HashSet();
	Enumeration locsEnum = subfields_l.elements();
	while (locsEnum.hasMoreElements())
	{
		//A location can be prefixed with copy information;
		//Call cleanLoc() to get rid of it.
		MarcSubfield subfild_l = (MarcSubfield)locsEnum.nextElement();
		String loc = cleanLoc(subfild_l.value());
		if (locs_hashSet.add(loc))
		{
			//For each unique location, create an 852 field.
			target = outMarc.getNewVblField("852", "  ");
			target.addSubfield("a", "SFB"); //5.2.2.2
			target.addSubfield("b", loc); //5.2.2.3
			new852s.addElement(target);	
		}
	}

        if (new852s.size() > 0)
        {
        	return new852s;
        }
        return null;
    }


    private Vector start852sFrom852s()
    {
	// The location codes in these records will be taken from non-repeatable $b subfields
	// in repeatable 852 fields. Each UNIQUE 852 $b will generate a new 852.
    	Vector v852s = new Vector();
	MarcVblLengthField target = null;

	MarcFieldList fv852 = inMarc.allFields("852");
        Enumeration enu_852 = fv852.elements();

	// Search for unique locations, ignore duplicate locations.
        HashSet locs_hashSet = new HashSet();
        MarcFieldList fv852_unique = new MarcFieldList();
        while (enu_852.hasMoreElements())
        {
           MarcVblLengthField fld852 = (MarcVblLengthField)enu_852.nextElement();
           String first_sf_b = fld852.firstSubfieldValue("b");
           if (first_sf_b != null)
           {
                //Ignore it if it is a duplicate. Otherwise, save it for further processing.
                if (locs_hashSet.add(first_sf_b))
                {
                        fv852_unique.addElement(fld852);
                }
           }
        }
         
	Enumeration enu_unique = fv852_unique.elements();
        MarcSubfield sf_b = null;
        MarcSubfield sf_j = null;
        MarcSubfield sf_z = null;
        MarcSubfield sf_3 = null;

	while (enu_unique.hasMoreElements())
	{
	    MarcVblLengthField f852 = (MarcVblLengthField)enu_unique.nextElement();
            sf_b = f852.firstSubfield("b");

            //5.2.2.3 Create output 852 $a, $b.
            if (exists(sf_b))
            {
                target = outMarc.getNewVblField("852", "  ");
                target.addSubfield("a", "SFB"); //5.2.2.2
                target.addSubfield("b", sf_b.value()); //5.2.2.3
            }
            else
            {
                continue;
            }

	    // Let's handle the output 852 $j, $z and $3 here for each UNIQUE location.
	    // Create output 852 $j from input 852 $j.
	    sf_j = f852.firstSubfield("j");
            if (exists(sf_j))
            {
		// Remove any prefix and suffix from the value of $j.
                strip852(sf_j.value(), target);
            }

	    //Create output 852 $z.
	    sf_z = f852.firstSubfield("z");
            if (exists(sf_z))
            {
                target.addSubfield("z", sf_z.value());
            }

            //Create output 852 $3. 
            sf_3 = f852.firstSubfield("3"); 
            if (exists(sf_3)) 
	    {
                target.addSubfield("3", sf_3.value()); 
            }
            else
            {
                target.addSubfield("3", localHoldings);
            }
	}
	
        if (v852s.size() > 0)
        {
                return v852s;
        }
        return null;

    }


    private Vector start852sFrom850s()
    {
        Vector v852s = new Vector();
        MarcVblLengthField target = null;

	// Input 850 is repeatable, but subfield $g (location code) is non-repeatable.
        MarcFieldList fv850 = inMarc.allFields("850");
        Enumeration enu = fv850.elements();

	//Get unique locations, ignore duplicate.
	HashSet locs_hashSet = new HashSet();
	MarcFieldList fv850_unique = new MarcFieldList();
	while (enu.hasMoreElements())
	{
	   MarcVblLengthField fld850 = (MarcVblLengthField)enu.nextElement();
	   String first_sf_g = fld850.firstSubfieldValue("g");
	   if (first_sf_g != null)
	   {
		//Ignore it if it is a duplicate. Otherwise, save it for further processing.
		if (locs_hashSet.add(first_sf_g))
		{
			fv850_unique.addElement(fld850);
		}
	   }
	}
	   
	Enumeration enu_unique = fv850_unique.elements();
        MarcSubfield gsf = null;
        MarcSubfield fsf = null;
        MarcSubfield hsf = null;
        MarcSubfield jsf = null;
        MarcSubfield zsf = null;
        MarcSubfield ksf = null;
        MarcSubfield csf = null;

        while (enu_unique.hasMoreElements())
        {
            MarcVblLengthField f850 = (MarcVblLengthField)enu_unique.nextElement();
            gsf = f850.firstSubfield("g");

            //5.2.2.3
            if (exists(gsf))
            {
                target = outMarc.getNewVblField("852", "  ");
                target.addSubfield("a", "SFB"); //5.2.2.2
                target.addSubfield("b", gsf.value()); //5.2.2.3
            }
            else
            {
                continue;
            }
            //5.2.2.6, 5.2.2.7
            fsf = f850.firstSubfield("f");
            if (exists(fsf))
            {
                strip850(fsf.value(), target);
            }

            //5.2.2.12
            boolean j_used = false;
            hsf = f850.firstSubfield("h");
            if (exists(hsf))
            {
                target.addSubfield("3", hsf.value());
            }
            else
            {
                jsf = f850.firstSubfield("j");
                if (exists(jsf))
                {
                    j_used = true;
                    target.addSubfield("3", jsf.value());
                }
                else
                {
                    target.addSubfield("3", localHoldings);
                }
            }

            //5.2.2.8 Note that this code creates separate $ms for each input subfield
            jsf = f850.firstSubfield("j");
            if (exists(jsf) && !j_used)
            {
                target.addSubfield("m", jsf.value());
            }

            zsf = f850.firstSubfield("z");
            if (exists(zsf))
            {
                target.addSubfield("m", zsf.value());
            }

            ksf = f850.firstSubfield("k");
            if (exists(ksf))
            {
                target.addSubfield("m", ksf.value());
            }
            //End 5.2.2.8 see also handle595s

            v852s.addElement(target);
        }

        if (v852s.size() > 0)
        {
            return v852s;
        }

        return null;
    }

    private void addFirstM(Vector v852s)
    {
        String zval = inMarc.getFirstValue("950", "z");

        if (exists(zval))
        {
            ((MarcVblLengthField)v852s.elementAt(0)).addSubfield("m", zval);
        }
    }


    private void handleCallNumbersNo850(Vector v852s)
    { //5.2.2.6
        MarcFieldList mfl;
        Vector sfs;

        if ((mfl = inMarc.allFields("099")) != null
            && mfl.size() > 0
            && (sfs = ((MarcVblLengthField)mfl.elementAt(0)).subfields("abc", true)).size() > 0)
        {
            sfs = stripNo850(sfs, v852s);
            process9996(sfs, v852s);
        }
        else if ((mfl = inMarc.allFields("096")) != null
                 && mfl.size() > 0
                 && (sfs = ((MarcVblLengthField)mfl.elementAt(0)).subfields("abc", true)).size() > 0)
        {
            sfs = stripNo850(sfs, v852s);
            process9996(sfs, v852s);
        }
        else if ((mfl = inMarc.allFields("090")) != null
                 && mfl.size() > 0
                 && (sfs = ((MarcVblLengthField)mfl.elementAt(0)).subfields("abc", true)).size() > 0)
        {
            sfs = stripNo850(sfs, v852s);
            process90(sfs, v852s);
        }
        else if ((mfl = inMarc.allFields("050")) != null
                 && mfl.size() > 0
                 && (sfs = ((MarcVblLengthField)mfl.elementAt(0)).subfields("ab", true)).size() > 0)
        {
            sfs = stripNo850(sfs, v852s);
            process50(sfs, v852s);
        }
        else
        {
            //No call numbers found. Return
        }
    }


    private Vector stripNo850(Vector sfs, Vector v852s)
    {
        Vector stripped = new Vector();
        RE prefix1 = null;
        RE prefix2 = null;
        RE suffix = null;

        try
        {
            //prefix1 = new RE(prefixRegexpString, RE.MATCH_CASEINDEPENDENT);
            prefix1 = new RE(prefixRegexpString);
            prefix2 = new RE("browsing", RE.MATCH_CASEINDEPENDENT);
            suffix = new RE(suffixRegexpString, RE.MATCH_CASEINDEPENDENT);
        }
        catch (RESyntaxException rese)
        {
            System.out.println("RESyntaxException in UCSFConvert::stripNo850: " + rese);
        }

        Enumeration sfEnum = sfs.elements();
        while (sfEnum.hasMoreElements())
        {
            MarcSubfield cnsf = (MarcSubfield)sfEnum.nextElement();
            if (!exists(cnsf))
            {
                continue;
            }

            String cn = cnsf.value();
            if (prefix1.match(cn))
            {
                String pfx = prefix1.getParen(1);
                cn = prefix1.getParen(2);
                Enumeration fEnum = v852s.elements();
                while (fEnum.hasMoreElements())
                {
                    MarcVblLengthField f852 = (MarcVblLengthField)fEnum.nextElement();
                    f852.addSubfield("k", pfx);
                }
            }

            if (prefix2.match(cn))
            {
                cn = cn.substring(0, prefix2.getParenStart(0))
                    + cn.substring(prefix2.getParenEnd(0), cn.length());
            }

            if (suffix.match(cn))
            {
                String sfx = suffix.getParen(1);
                cn = cn.substring(0, suffix.getParenStart(0))
                    + cn.substring(suffix.getParenEnd(0), cn.length());
                for (int i = 0;i < suffixes.length;i++)
                {
                    if (sfx.equalsIgnoreCase(suffixes[i][0]))
                    {
                        sfx = suffixes[i][1];
                        break;
                    }
                }
                Enumeration fEnum = v852s.elements();
                while (fEnum.hasMoreElements())
                {
                    MarcVblLengthField f852 = (MarcVblLengthField)fEnum.nextElement();
                    f852.addSubfield("m", sfx);
                }
            }
            stripped.addElement(new UCSFPseudoSubField(cnsf.tag(), cn.trim()));
        }
        return stripped;
    }

    private void strip850(String cn, MarcVblLengthField target)
    {
        RE prefix = null;

        try
        {
            //prefix = new RE(prefixRegexpString, RE.MATCH_CASEINDEPENDENT);
            prefix = new RE(prefixRegexpString);
        }
        catch (RESyntaxException rese)
        {
            System.out.println("RESyntaxException in UCSFConvert::stripNo850: " + rese);
        }

        if (prefix.match(cn))
        {
            target.addSubfield("k", prefix.getParen(1));
            target.addSubfield("j", prefix.getParen(2).trim());
        }
        else
        {
            target.addSubfield("j", cn);
        }
    }


    private void strip852(String cn, MarcVblLengthField target)
    {
	// The UCSF input 852, only $j may contain prefix or suffix.
        RE prefix1 = null;
        RE prefix2 = null;
        RE suffix = null;

        try
        {
            //prefix = new RE(prefixRegexpString, RE.MATCH_CASEINDEPENDENT);
            prefix1 = new RE(prefixRegexpString);
            prefix2 = new RE("browsing", RE.MATCH_CASEINDEPENDENT);
	    suffix = new RE(suffixRegexpString, RE.MATCH_CASEINDEPENDENT);
        }
        catch (RESyntaxException rese)
        {
            System.out.println("RESyntaxException in UCSFConvert::strip852: " + rese);
        }

        if (prefix1.match(cn))
        {
	    cn = prefix1.getParen(2).trim();
        }

	if (prefix2.match(cn))
	{
	    cn = cn.substring(0, prefix2.getParenStart(0))
                    + cn.substring(prefix2.getParenEnd(0), cn.length());
	}

        if (suffix.match(cn))
        {
            String sfx = suffix.getParen(1);
            cn = cn.substring(0, suffix.getParenStart(0))
                + cn.substring(suffix.getParenEnd(0), cn.length());
	}

        target.addSubfield("j", cn);
    }


    private void process9996(Vector sfs, Vector v852s)
    {
        UCSFPseudoSubField muestra = null;
        UCSFPseudoSubField muestra2 = null;
        UCSFPseudoSubField temp = null;
        MarcVblLengthField target = null;
        Enumeration sfEnum = null;
        Enumeration fEnum = null;

        if (sfs.size() == 2
            && (muestra = (UCSFPseudoSubField)sfs.elementAt(0)) != null
            && (muestra2 = (UCSFPseudoSubField)sfs.elementAt(1)) != null
            && ((muestra.getTag().equals("a") && muestra2.getTag().equals("b"))
                || (muestra.getTag().equals("b") && muestra2.getTag().equals("a"))))
        {
            if (muestra2.getTag().equals("a"))
            {
                temp = muestra;
                muestra = muestra2;
                muestra2 = temp;
            }

            fEnum = v852s.elements();
            while (fEnum.hasMoreElements())
            {
                target = (MarcVblLengthField)fEnum.nextElement();
                target.addSubfield("h", muestra.getValue());
                target.addSubfield("i", muestra2.getValue());
            }
        }
        else if (sfs.size() == 1)
        {
            fEnum = v852s.elements();
            muestra = (UCSFPseudoSubField)sfs.elementAt(0);
            while (fEnum.hasMoreElements())
            {
                target = (MarcVblLengthField)fEnum.nextElement();
                target.addSubfield("j", muestra.getValue());
            }
        }
        else
        {
            Vector ordered = new Vector();

            sfEnum = sfs.elements();
            while (sfEnum.hasMoreElements())
            {
                UCSFPseudoSubField aSuspect = (UCSFPseudoSubField)sfEnum.nextElement();
                if (aSuspect.getTag().equals("a"))
                {
                    ordered.addElement(aSuspect);
                }
            }

            sfEnum = sfs.elements();
            while (sfEnum.hasMoreElements())
            {
                UCSFPseudoSubField bSuspect = (UCSFPseudoSubField)sfEnum.nextElement();
                if (bSuspect.getTag().equals("b"))
                {
                    ordered.addElement(bSuspect);
                    break;
                }
            }

            sfEnum = sfs.elements();
            while (sfEnum.hasMoreElements())
            {
                UCSFPseudoSubField cSuspect = (UCSFPseudoSubField)sfEnum.nextElement();
                if (cSuspect.getTag().equals("c"))
                {
                    ordered.addElement(cSuspect);
                }
            }

            StringBuffer jval = new StringBuffer();
            int i = 0;
            sfEnum = ordered.elements();
            while (sfEnum.hasMoreElements())
            {
                UCSFPseudoSubField suspect = (UCSFPseudoSubField)sfEnum.nextElement();
                if (i == 0)
                {
                    jval.append(suspect.getValue());
                    i = 1;
                }
                else
                {
                    jval.append(" " + suspect.getValue());
                }
            }

            fEnum = v852s.elements();
            while (fEnum.hasMoreElements())
            {
                target = (MarcVblLengthField)fEnum.nextElement();
                target.addSubfield("j", jval.toString());
            }
        }
    }

    private void process90(Vector sfs, Vector v852s)
    {
        Enumeration sfEnum = sfs.elements();
        while (sfEnum.hasMoreElements())
        {
            UCSFPseudoSubField muestra = (UCSFPseudoSubField)sfEnum.nextElement();
            if (muestra.getValue().equalsIgnoreCase("x"))
            {
                return;
            }
        }
        process9996(sfs, v852s);
    }

    private void process50(Vector sfs, Vector v852s)
    {
        UCSFPseudoSubField muestra = null;
        UCSFPseudoSubField muestra2 = null;
        UCSFPseudoSubField temp = null;
        MarcVblLengthField target = null;
        Enumeration fEnum = null;
        boolean seenA = false;
        boolean seenB = false;

        for (int i = 0; i < sfs.size(); )
        {
            muestra = (UCSFPseudoSubField)sfs.elementAt(i);
            if (muestra.getTag().equals("a"))
            {
                if (seenA)
                {
                    sfs.removeElementAt(i);
                    continue;
                }
                seenA = true;
            }
            else if (muestra.getTag().equals("b"))
            {
                if (seenB)
                {
                    sfs.removeElementAt(i);
                    continue;
                }
                seenB = true;
            }
            i++;
        }

        if (sfs.size() == 2
            && (muestra = (UCSFPseudoSubField)sfs.elementAt(0)) != null
            && (muestra2 = (UCSFPseudoSubField)sfs.elementAt(1)) != null
            && ((muestra.getTag().equals("a") && muestra2.getTag().equals("b"))
                || (muestra.getTag().equals("b") && muestra2.getTag().equals("a"))))
        {
            if (muestra2.getTag().equals("a"))
            {
                temp = muestra;
                muestra = muestra2;
                muestra2 = temp;
            }

            fEnum = v852s.elements();
            while (fEnum.hasMoreElements())
            {
                target = (MarcVblLengthField)fEnum.nextElement();
                target.addSubfield("h", muestra.getValue());
                target.addSubfield("i", muestra2.getValue());
            }
        }
        else if (sfs.size() == 1)
        {
            fEnum = v852s.elements();
            muestra = (UCSFPseudoSubField)sfs.elementAt(0);
            while (fEnum.hasMoreElements())
            {
                target = (MarcVblLengthField)fEnum.nextElement();
                target.addSubfield("j", muestra.getValue());
            }
        }
        else
        {
            // do nothing
        }
    }

    private void handle595s(Vector v852s)
    { //5.2.2.1
        MarcFieldList fv595 = inMarc.allFields("595");
        MarcVblLengthField target = null;

	// Do it only if the record contains one of more 595 field(s).
        if (fv595.size() > 0)
        {
	    Enumeration e852 = v852s.elements();
	    while (e852.hasMoreElements())
            {
                target = (MarcVblLengthField)e852.nextElement();

                Enumeration e595 = fv595.elements();
                while (e595.hasMoreElements())
                {
               		MarcVblLengthField f595 = (MarcVblLengthField)e595.nextElement();
                	MarcSubfield asf = f595.firstSubfield("a");
                	if (exists(asf))
                	{
                    		target.addSubfield("m", asf.value().trim());
                	}
                }
	    }
        }
    }


    //Assumes one $a field per 590
    //Note that this recognizes subfields that possibly begin with some number
    //of blanks, and in the case of checking for 'library has' it recognizes 0
    //or more blanks followed by 0 or more asterisks followed by 'library has'.
    private void handleNonPublicNotes(Vector v852s)
    { //5.2.2.10
        conditionalMoveToAll("590", v852s, nonPublicNotesRegexpString, false, "a", "x");
    }

    //Assumes one $a field per 590
    private void handlePublicNotes(Vector v852s)
    { //5.2.2.11
        Vector newOutput = new Vector();
        newOutput.addElement(v852s.elementAt(0));
        conditionalMoveToAll("590", newOutput, publicNotesRegexpString, true, "a", "z");
    }

    private void handleMonographSummaryHoldings(Vector v852s)
    { //5.2.2.12
        conditionalMoveToAll("590", v852s, monographSummaryHoldingsRegexpString, true, "a", "3");
    }

    private void handleLibraryKeeps(Vector v852s)
    { //5.2.2.8
        conditionalMoveToAll("590", v852s, serial590No850RegexpString, true, "a", "m");
    }

    private void conditionalMoveToAll(String inputFieldTag, //get all these fields
                                      Vector outputFields,  //move a value to these fields
                                      String re, //only if value does(n't) include this pattern
                                      boolean include, //does(n't)
                                      String inputSubTag, //from this sub tag
                                      String outputSubTag //to this sub tag
                                      )
    { //5.2.2.8
        RE accepts = null;

        try
        {
            accepts = new RE(re, RE.MATCH_CASEINDEPENDENT);
        }
        catch (RESyntaxException rese)
        {
            System.out.println("RESyntaxException in UCSFConvert::conditionalMoveToAll: " + rese);
        }

        MarcFieldList mfl = inMarc.allFields(inputFieldTag);
        if (mfl.size() > 0)
        {
            Enumeration inputsEnum = mfl.elements();
            while (inputsEnum.hasMoreElements())
            {
                MarcVblLengthField f = (MarcVblLengthField)inputsEnum.nextElement();
                MarcSubfield asf = f.firstSubfield(inputSubTag);
                if (exists(asf)
                    && (include ? accepts.match(asf.value()) : !accepts.match(asf.value())))
                {
                    Enumeration outputsEnum = outputFields.elements();
                    while (outputsEnum.hasMoreElements())
                    {
                        MarcVblLengthField fOutput = (MarcVblLengthField)outputsEnum.nextElement();
                        fOutput.addSubfield(outputSubTag, asf.value());
                    }
                }
            }
        }
    }

    private void handleSerialSummaryHoldingsNo850(Vector v852s)
    { //5.2.2.12
        RE libhas = null;
        boolean found = false;
        String summaryHoldingsNote = null;

        try
        {
            libhas = new RE(serialSummaryHoldingsRegexpString, RE.MATCH_CASEINDEPENDENT);
        }
        catch (RESyntaxException rese)
        {
            System.out.println("RESyntaxException in UCSFConvert::conditionalMoveToAll: " + rese);
        }

        MarcFieldList mfl = inMarc.allFields("590");

        if (mfl.size() > 0)
        {
            Enumeration inputsEnum = mfl.elements();
            while (inputsEnum.hasMoreElements())
            {
                MarcVblLengthField f = (MarcVblLengthField)inputsEnum.nextElement();
                MarcSubfield asf = f.firstSubfield("a");
                if (exists(asf) && libhas.match(asf.value()))
                {
                    summaryHoldingsNote = libhas.getParen(1);
                    int i = summaryHoldingsNote.length() - 1;
                    for ( ; i >= 0; i-- )
                    {
                        if (summaryHoldingsNote.charAt(i) != ' '
                            && summaryHoldingsNote.charAt(i) != '*')
                        {
                            break;
                        }
                    }
                    summaryHoldingsNote = summaryHoldingsNote.substring(0, i + 1);
                    break;
                }
            }
        }

        if (summaryHoldingsNote == null)
        { //No 590 with 'library has' found. Look in 950s
            mfl = inMarc.allFields("950");
            if (mfl.size() > 0)
            {
                MarcVblLengthField f950 = (MarcVblLengthField)mfl.elementAt(0);
                MarcSubfield candidate = f950.firstSubfield("b");
                if (exists(candidate))
                {
                    summaryHoldingsNote = candidate.value();
                }
                else
                {
                    candidate = f950.firstSubfield("d");
                    if (exists(candidate))
                    {
                        summaryHoldingsNote = candidate.value();
                    }
                }
            }
        }

        if (summaryHoldingsNote == null)
        {
            summaryHoldingsNote = localHoldings;
        }

        Enumeration outputsEnum = v852s.elements();
        while (outputsEnum.hasMoreElements())
        {
            MarcVblLengthField fOutput = (MarcVblLengthField)outputsEnum.nextElement();
            fOutput.addSubfield("3", summaryHoldingsNote);
        }
    }

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
            System.out.println("RESyntaxException in UCSFConvert::cleanLoc: " + rese);
        }
        return preLoc;
    }

}
