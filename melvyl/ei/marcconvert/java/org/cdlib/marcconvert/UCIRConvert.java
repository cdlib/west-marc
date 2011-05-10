package org.cdlib.marcconvert;

import org.apache.log4j.Logger;
import java.util.Vector;
import java.util.Enumeration;

import org.cdlib.util.marc.MarcException;
import org.cdlib.util.marc.MarcFieldList;
import org.cdlib.util.marc.MarcRecord;
import org.cdlib.util.marc.MarcSubfield;
import org.cdlib.util.marc.MarcVblLengthField;

/**
 * UC Irvine Aleph record conversion. This is a concrete subclass of the
 * MarcEmbedConvert abstract class.
 *
 *
 * @author Karen Coyle
 * @author Randy Lai
 * @version $id: UCIRConvert.java, v 1.0 5/01/2004
 */
public class UCIRConvert extends MarcEmbedConvert
{
	/**
	 * log4j Logger for this class.
	 */
	private static Logger log = Logger.getLogger(UCIRConvert.class);

	/**
	 * CVS header string.
	 */
    public static final String cvsHeader = "$Header: /cvs/root/melvyl/ei/marcconvert/java/org/cdlib/marcconvert/UCIRConvert.java,v 1.3 2006/07/31 19:22:16 rkl Exp $";

	/**
	 * CVS version string.
	 */
    public static final String version = "$Revision: 1.3 $";

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
     * Instanstiate a new UCIRConvert object.
     */
    public UCIRConvert()
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

        //set default debug level for NullConvert
        setDebug(0);

        //set status for this record
        setStatus(OK);

        //move the input leader to output
        moveLeader();

        //move all standard MARC fields to output

        moveOut(001, 899);

        //delete the original 852
        outMarc.deleteFields("852");

        //build record ID field from 001
        //...and check that this is the correct source
        //for this program
        build901();

        build852();

        debugOut(2, "process output leader:" + outMarc.getLeaderValue());

        return rc;
    }

    /**
     * For each input 852, build an output 852.
     */
    protected void build852()
    {
        //build 852 field(s) from input 852s
        //first get input 852s
        MarcFieldList in852s = inMarc.allFields("852");
        if (in852s != null)
        {
		int nr852s = in852s.size();

		//for each 852 field...
		for (int locNr = 0; locNr < nr852s; locNr++)
		{
			//get the current input 852 field
			MarcVblLengthField in852 = (MarcVblLengthField)in852s.elementAt (locNr);

			//if incoming 852 lacks a $b - do NOT output the 852
			if (MarcSubfield.exists(in852.firstSubfield("b")))
			{
				//852 - new variable field in output record
				MarcVblLengthField out852 = outMarc.getNewVblField("852", "  ");

				//build output 852 in desired order
				//see individual methods for more info
				//repeatable: $c, $e, $f, $g, $i, $k, $m, $s, $x, $z
				//not repeatable: $a, $b, $h, $j, $l, $n, $p, $q, $t, $3
				build852a(in852, out852);
				build852b(in852, out852);
				build852c(in852, out852);
				build852e(in852, out852);
				build852f(in852, out852);
				build852g(in852, out852);

				//Call numbers for serials will be sent in the 852 field.
				//Call numbers for monographs will be sent in the bibliographic field.
				if (isSerial())
				{
					build852hijSer(in852, out852);
				}
				else
				{
					build852jMono(out852);
				}
				build852k(in852, out852);
				build852l(in852, out852);
				build852m(in852, out852);
				build852n(in852, out852);
				build852p(in852, out852);
				build852q(in852, out852);
				build852s(in852, out852);
				build852t(in852, out852);
				build852x(in852, out852);
				build852z(in852, out852);
				build8523(in852, out852);
				build852DO(in852, out852);
			}
			else
			{
				if (log.isDebugEnabled())
				{
					log.debug("No 852 $b found");
				}
			}
	 	}
	}
    }


    /**
	 * Build the 901 field in the output marc object.
	 * Field length of the 001 field is 12 - not checking.
	 * Position 1-3 of the 001 field must be "UCI" in upper case - reject if not.
	 * Position 4 must be 'b'- not checking.
	 * Check the cataloging source in the first 3 bytes of the 001 field,
	 * this must be "UCI", but the value of "IRB" goes into the 901 $a subfield.
	 * Characters in position 5-12 represent III record ID. Move them to 901 $b. 
	 *
	 * @throws MarcDropException if input has no 001 field
	 *                           or the cataloging source is not UCI (901 $a)
	 *                           or the record id is missing (901 $b)
     */
    protected void build901()
    {
        //CDL: input record ID
        //
        // get the 001 field
		String in001 = inMarc.getFirstValue("001","");

		//Reject records with no incoming 001
		if (!exists(in001))
		{
			throw new MarcDropException("Input lacks 001");
		}

		// get the 901 $a field, cataloging source
		String f901a = in001.substring(0,3);

		// make sure the cataloging source is UCI in upper case
		if ("UCI".compareTo(f901a) != 0)
		{
			throw new MarcDropException("Not UCI Source");
		}

		// get the 901 $b field, record id
		// Pos 5-12 = III record id
		String f901b = in001.substring(4,12);

		// make sure the record id exists
		if (f901b == null || f901b.length() == 0)
		{
			throw new MarcDropException("No incoming record ID");
		}

		//set up output 901 field
		MarcVblLengthField out901 = outMarc.getNewVblField("901", "  ");
		out901.addSubfield("a", "IRB");
		out901.addSubfield("b", f901b);
    }


    /**
     * Force the output 852 $a to be "IRB".
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build852a(MarcVblLengthField in, MarcVblLengthField out)
    {
        //MARC: library identifier
        //UCI 852a will always be "IRB"
        out.addSubfield("a", "IRB");
    }

    /**
     * Copy the all the input 852 $c subfields to the output 852.
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build852c(MarcVblLengthField in, MarcVblLengthField out)
    {
        //MARC: library address
        //output all 852c subfields
        Vector cSubs = in.subfields("c", true);

        if (cSubs != null)
        {
            MarcSubfield[] sf = new MarcSubfield[cSubs.size()];
            cSubs.copyInto((Object[])sf);
            out.addSubfields(sf);
        }
    }

    /**
     * Set the output 852 $h from the first input 852 $h.
     * Set the output 852 $j from the first input 852 $j.
     * Copy the all the input 852 $i subfields to the output 852.
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build852hijSer(MarcVblLengthField in, MarcVblLengthField out)
    {
        //h and j are non-repeatable; output just first one
        //i is repeatable; output all

        String f852h = in.firstSubfieldValue("h");
                if (f852h != null)
                {
                        out.addSubfield("h", f852h);
                }

        Vector iSubs = in.subfields("i", true);
                if (iSubs != null)
                {
                        MarcSubfield[] sf = new MarcSubfield[iSubs.size()];
                        iSubs.copyInto((Object[])sf);
                        out.addSubfields(sf);
                }

        String f852j = in.firstSubfieldValue("j");
                if (f852j != null)
                {
                        out.addSubfield("j", f852j);
                }

    }


    /**
     * Set the output 852 $j from the first field found in the following list:
     * "099, 090, 050, 096, 060, 086".
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build852jMono(MarcVblLengthField out)
    {
        //MARC: call number
        //output first 852j subfield
	String fldList[] = {"099", "090", "050", "096", "060", "086"};
	StringBuffer sb852j = new StringBuffer();
	MarcSubfield sf = null;
	String sfValue = null;
	for (int i = 0; i < fldList.length; i++)
	{
		//MarcFieldList flds = inMarc.allFields(fldList[i]);
		//get the first instance of each field
		MarcVblLengthField fld = (MarcVblLengthField)inMarc.getFirstField(fldList[i]);
		if (fld != null)
		{
			Vector sfVector = fld.subfields("ab", true);
			for(Enumeration e = sfVector.elements(); e.hasMoreElements();)
			{
				sf = (MarcSubfield)e.nextElement();
				sfValue = (String)sf.value();
				sb852j = sb852j.append(" ").append(sfValue);
			}
			if (sb852j != null) 
			{
				String f852j = ((sb852j.toString().trim().length() > 0) ? sb852j.toString().trim() : null);
				if (f852j != null)
			 	{
					out.addSubfield("j", f852j);	
					break;
				}
			}
		}
	}
    }


    /**
     * Copy the all the input 852 $k subfields to the output 852.
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build852k(MarcVblLengthField in, MarcVblLengthField out)
    {
        //MARC: Call number prefix
        //output all 852k subfields
        Vector kSubs = in.subfields("k", true);

                if (kSubs != null)
                {
                        MarcSubfield[] sf = new MarcSubfield[kSubs.size()];
                        kSubs.copyInto((Object[])sf);
                        out.addSubfields(sf);
                }
    }


    /**
     * Determine if the input is a serials record.
     *
     * This is the canonical method for determining serial status of a holding.
     * This method is used by UCIR converter to determine if a holding is a serial.
     *
     * @return true if the current record is a serials record
     */
    protected boolean isSerial()
    {
        String type     = inMarc.getRecordType();
        String biblevel = inMarc.getBibLvl();

        if ( (type.equalsIgnoreCase("a") || type.equalsIgnoreCase("m"))
             && (biblevel.equalsIgnoreCase("b") || biblevel.equalsIgnoreCase("i")
		|| biblevel.equalsIgnoreCase("s")) )
        {
            return true;
        }
        else
        {
            return false;
        }

    }


    /**
     * Determine if the input is a monograph record.
     * The rule is: all that is not serial is monograph, so we just
     * return the inversion of <code>isSerial()</code>.
     *
     * This is the canonical method for determining monograph status of a holding.
     * This method is used by UCIR converter to determine if a holding is a monograph.
     *
     * @return true if the current record is a monograph record
     */
    protected boolean isMonograph()
    {
        return !isSerial();
    }

}
