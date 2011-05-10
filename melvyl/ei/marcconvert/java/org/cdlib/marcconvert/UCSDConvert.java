package org.cdlib.marcconvert;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.apache.regexp.RE;
import org.apache.regexp.RESyntaxException;

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
 * @author <a href="mailto:gmills@library.berkeley.edu">Garey Mills</a>
 * @author <a href="mailto:shawnm@splorkin.com">Shawn McGovern</a>
 * @version $Id: UCSDConvert.java,v 1.7 2008/01/28 23:21:45 rkl Exp $
 */
public class UCSDConvert extends IIIConvert
{
	/**
	 * log4j Logger for this class.
	 */
    private static Logger log = Logger.getLogger(UCSDConvert.class);

	/**
	 * CVS header string.
	 */
    public static final String cvsHeader = "$Header: /cvs/root/melvyl/ei/marcconvert/java/org/cdlib/marcconvert/UCSDConvert.java,v 1.7 2008/01/28 23:21:45 rkl Exp $";

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
     * Instantiate a new UCSDConvert object. Instantiates the IIIConvert
     * super class as a UCSD conversion.
     */
    public UCSDConvert()
    {
        super(IIIConvert.UCSD);
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
        if ( !verifyCatalogingSource() )
        {
            throw new MarcDropException("Cataloging source not UCSD");
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

        MarcFieldList inFields       = null;
        MarcFieldList outFields      = new MarcFieldList();
        HashSet       locs           = new HashSet();  // the processed locations
        String        locSubTag      = null;
        String        nonPublicNote  = null;
        Vector        publicNotes    = null;

        //5.2.2.10
        // Save the notes once because they are added to each 852
        nonPublicNote = inMarc.getFirstValue("935", "a");
        publicNotes   = inMarc.getValues("590", "a");

        if ( isMonograph )
        {
            inFields = inMarc.allFields("920");
            locSubTag = new String("l");
        }
        else
        {
            inFields = inMarc.allFields("850");
            locSubTag = new String("c");
        }

        //Discover and discard fields with repeated locs, no $l or $c fields,
        //or $l or $c fields with blank or empty values
        //From 5.2.2.3 and 6.1
        Enumeration enu = inFields.elements();

        while ( enu.hasMoreElements() )
        {
            MarcVblLengthField f = (MarcVblLengthField)enu.nextElement();
            MarcSubfield lsf = f.firstSubfield(locSubTag);
            if ( exists(lsf) )
            {
                String loc = lsf.value();
                if( locs.add(loc) )
                {
                    outFields.addElement(f);
                }
            }
        }

        //We now have only those 920s or 850s that have unique locs in outFields
        if ( isMonograph )
        {
            build852sFrom920s(outFields, nonPublicNote, publicNotes);
        }
        else
        {
            build852sFrom850s(outFields, nonPublicNote, publicNotes);
        }
    }


    private void build852sFrom850s(MarcFieldList in850s, String nonPublicNote, Vector publicNotes)
    {
        Enumeration ins = in850s.elements();

        while ( ins.hasMoreElements() )
        {
            MarcVblLengthField target = outMarc.getNewVblField("852", "  ");
            MarcVblLengthField in = (MarcVblLengthField)ins.nextElement();

            //5.2.2.2
            target.addSubfield("a", "SDB");

            //5.2.2.3
            MarcSubfield csf = in.firstSubfield("c");
            if ( exists(csf) )
            {
                target.addSubfield("b", csf.value());
            }

            //5.2.2.6, 5.2.2.8
            processSerialCallNumber(in, target);

            //5.2.2.8
            moveEsandFs(in, target);

            //5.2.2.10, 5.2.2.11
            addNotes(target, nonPublicNote, publicNotes);

            //5.2.2.12
            moveG(in, target);

            //5.2.2.13
            createDandO(in, target);
        }
    }


    private void build852sFrom920s(MarcFieldList in920s, String nonPublicNote, Vector publicNotes)
    {
        Enumeration ins = in920s.elements();

        while ( ins.hasMoreElements() )
        {
            MarcVblLengthField target = outMarc.getNewVblField("852", "  ");
            MarcVblLengthField in = (MarcVblLengthField)ins.nextElement();

            //5.2.2.2
            target.addSubfield("a", "SDB");

            //5.2.2.3
            MarcSubfield lsf = in.firstSubfield("l");
            if ( exists(lsf) )
            {
                target.addSubfield("b", lsf.value());
            }

            //5.2.2.6, 5.2.2.8
            processMonographCallNumber(in, target);

            //5.2.2.10, 5.2.2.11
            addNotes(target, nonPublicNote, publicNotes);
        }
    }

    //5.2.2.6
    private void processMonographCallNumber(MarcVblLengthField in, MarcVblLengthField target)
    {
        Vector asandbs = in.subfields("ab", true);

        if ( asandbs.size() == 1
             && ((MarcSubfield)asandbs.elementAt(0)).value().trim() != "" )
        { //Only one, so it doesn't matter which it is
            target.addSubfield("j", ((MarcSubfield)asandbs.elementAt(0)).value());
        }
        else if ( asandbs.size() > 1 )
        {
            //Count the 'a's
            Enumeration enu = asandbs.elements();
            int i = 0;
            while ( enu.hasMoreElements() )
            {
                MarcSubfield aSuspect = (MarcSubfield)enu.nextElement();
                if ( aSuspect.tag().equals("a") && exists(aSuspect) )
                {
                    i++;
                }
            }
            if( i > 1 )
            {
                StringBuffer asb = new StringBuffer();
                StringBuffer bsb = new StringBuffer();
                enu = asandbs.elements();

                int j = 0;
                while ( enu.hasMoreElements() )
                {
                    MarcSubfield msf = (MarcSubfield)enu.nextElement();
                    if ( msf.tag().equals("a") && exists(msf) )
                    {
                        String msfv = processCallNumberSuffixNotes(msf.value(), target);
                        if ( j == 0 )
                        {
                            asb.append(msfv);
                            j++;
                        }
                        else
                        {
                            asb.append(" " + msfv);
                        }
                    }
                    else if ( msf.tag().equals("b") && exists(msf) )
                    {
                        bsb.append(" " + msf.value());
                    }
                }
                target.addSubfield("j", asb.toString() + bsb.toString());
            }
            else
            { //Note that this code assumes only one $b
                enu = asandbs.elements();
                while ( enu.hasMoreElements() )
                {
                    MarcSubfield msf = (MarcSubfield)enu.nextElement();
                    if ( msf.tag().equals("a") && exists(msf) )
                    {
                        target.addSubfield("h", processCallNumberSuffixNotes(msf.value(), target));
                    }
                    else if ( msf.tag().equals("b") && exists(msf) )
                    {
                        target.addSubfield("i", msf.value());
                    }
                }
            }
        }
        else
        { //What do we do now?
        }
    } //end processCallNumber


    private void processSerialCallNumber(MarcVblLengthField in, MarcVblLengthField target)
    {
        MarcSubfield sfd = in.firstSubfield("d");

        if ( exists(sfd) )
        {
            String cn = processCallNumberSuffixNotes(sfd.value(), target);
            target.addSubfield("j", cn);
        }
        else
        { //My interpretation of 5.2.2.6 is that only the $d would have suffix notes
            MarcSubfield hsf = in.firstSubfield("h");
            if ( exists(hsf) )
            {
                MarcSubfield isf = in.firstSubfield("i");
                if ( exists(isf) )
                {
                    target.addSubfield("h", hsf.value());
                    target.addSubfield("i", isf.value());
                }
                else
                {
                    target.addSubfield("j", hsf.value());
                }
            }
        }
    }


    //This code 5.2.2.8
    private String processCallNumberSuffixNotes(String cn, MarcVblLengthField target)
    {
        try
        {
            RE suffixNotes
                = new RE("^ *(Sp\\.Coll\\.Over|Sp\\.Coll|PHONO|TAPES|Rev\\.|Rare|EastAsia) *(.*)");

            if ( suffixNotes.match(cn) )
            {
                target.addSubfield("m", cn.substring(suffixNotes.getParenStart(1),
                                                     suffixNotes.getParenEnd(1)));
                return(cn.substring(suffixNotes.getParenStart(2), suffixNotes.getParenEnd(2)));
            }
            else
            {
                return cn;
            }
        }
        catch(RESyntaxException rese)
        {
            log.error("RESyntaxException: " + rese, rese);
        }
        return cn;
    }

    private void moveEsandFs(MarcVblLengthField in, MarcVblLengthField target)
    {
        Vector esandfs = in.subfields("ef", true);

        if ( esandfs == null || esandfs.size() == 0 )
        {
            return;
        }
        else
        {
            Enumeration enu = esandfs.elements();
            while ( enu.hasMoreElements() )
            {
                MarcSubfield muestra = (MarcSubfield)enu.nextElement();
                if ( exists(muestra) )
                {
                    target.addSubfield("m", muestra.value());
                }
            }
        }
    }

    private void addNotes(MarcVblLengthField target, String nonPublicNote, Vector publicNotes)
    {
        if ( exists(nonPublicNote) )
        {
            target.addSubfield("x", nonPublicNote);
        }

        if ( publicNotes != null && publicNotes.size() > 0 )
        {
            Enumeration enu = publicNotes.elements();

            while ( enu.hasMoreElements() )
            {
                target.addSubfield("z", (String)enu.nextElement());
            }
        }
    }


    private void moveG(MarcVblLengthField in, MarcVblLengthField target)
    {
	Vector gSubs = in.subfields("g", true);
		
	if ( gSubs != null)
	{
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < gSubs.size(); i++)
		{
			MarcSubfield sfg = (MarcSubfield) gSubs.elementAt(i);
			if (exists(sfg))
			{
				if (sb.length() == 0)
				{
					sb.append(sfg.value()) ;
				}
				else
				{
					sb.append(";  " + sfg.value());
				}
			}
		}

        	if (sb.length() != 0) 
		{
			target.addSubfield("3", sb.toString());
		}
	}

    }

    private void createDandO(MarcVblLengthField in, MarcVblLengthField target)
    {
        MarcSubfield afld = in.firstSubfield("a");

        if ( afld != null )
        {
            String val = in.firstSubfield("a").value();

            if ( val != null)
            {
                val = val.trim();
                if ( val.length() == 0 )
                {
                    target.addSubfield("O", "<blank>");
                }
                else
                {
                    if ( val.equals("a") )
                    {
                        target.addSubfield("D", "Currently received");
                    }
                    else if ( val.equals("i") || val.equals("t") )
                    {
                        target.addSubfield("D", "Not currently received");
                    }
                    target.addSubfield("O", val);
                }
            }
        }
    }

}
