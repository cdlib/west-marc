package org.cdlib.marcconvert;

import org.apache.log4j.Logger;
import java.util.Vector;
import java.util.Enumeration;

import org.cdlib.util.marc.MarcException;
import org.cdlib.util.marc.MarcFieldList;
import org.cdlib.util.marc.MarcRecord;
import org.cdlib.util.marc.MarcSubfield;
import org.cdlib.util.marc.Field;
import org.cdlib.util.marc.MarcVblLengthField;

/**
 * UC Merced Aleph record conversion. This is a concrete subclass of the
 * MarcEmbedConvert abstract class.
 *
 *
 * @author Karen Coyle
 * @author Randy Lai
 * @version $id: UCMConvert.java, v 1.0 5/01/2004
 */
public class UCMConvert extends MarcEmbedConvert
{
	/**
	 * log4j Logger for this class.
	 */
	private static Logger log = Logger.getLogger(UCMConvert.class);

	/**
	 * CVS header string.
	 */
    public static final String cvsHeader = "$Header: /cvs/root/melvyl/ei/marcconvert/java/org/cdlib/marcconvert/UCMConvert.java,v 1.1 2005/04/27 16:34:06 rkl Exp $";

	/**
	 * CVS version string.
	 */
    public static final String version = "$Revision: 1.1 $";

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
     * Instanstiate a new UCMConvert object.
     */
    public UCMConvert()
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

        //delete the original 852 and 866
        outMarc.deleteFields("852");
        outMarc.deleteFields("866");

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
	//get a fields list for 852 and 866, 866 fields are needed for creating CDL 852 $3
        MarcFieldList in852866s = inMarc.allFields("852","866");
	
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

				build852hij(in852, out852);

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
				build8523(locNr, in852866s, out852);
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
	 * Position 1-3 of the 001 field must be "UCM" in upper case - reject if not.
	 * Position 4 must be 'b'- not checking.
	 * Check the cataloging source in the first 3 bytes of the 001 field,
	 * this must be "UCM", but the value of "MER" goes into the 901 $a subfield.
	 * Characters in position 5-12 represent III record ID. Move them to 901 $b. 
	 *
	 * @throws MarcDropException if input has no 001 field
	 *                           or the cataloging source is not UCM (901 $a)
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

		// make sure the cataloging source is UCM in upper case
		if ("UCM".compareTo(f901a) != 0)
		{
			throw new MarcDropException("Not UCM Source");
		}
		// reject the record if the 4th character of 001 field is not a lower case b
		if ("b".compareTo(in001.substring(3,4)) != 0)
                {
                        throw new MarcDropException("The 4th character of 001 field not a 'b'");
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
		out901.addSubfield("a", "MER");
		out901.addSubfield("b", f901b);
    }


    /**
     * Force the output 852 $a to be "MER".
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build852a(MarcVblLengthField in, MarcVblLengthField out)
    {
        //MARC: library identifier
        //UCM 852a will always be "MER"
        out.addSubfield("a", "MER");
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
    protected void build852hij(MarcVblLengthField in, MarcVblLengthField out)
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
     * Create output 852 $3 from input 866 $a.
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build8523(int locNr, MarcFieldList in, MarcVblLengthField out)
    {
        //MARC: Materials Specified (Summary Holdings)
	//Each incoming 852 in a UCM record may be followed by one or more 866 fields.
	//The $3 in the CDL 852 will be created from the 866s that follow it - up to next 852
	//in the incoming record. Take the holdings data from subfield $a of each 866 field.
	//Each 866 is expected to have only one $a.
	
	int size = in.size();
	boolean found = false;
	StringBuffer sb = new StringBuffer();
	int cnt852 = 0;
	
	for (int i = 0; i < size; i++)
	{
		Field f = (Field) in.elementAt (i);
		if ((f.tag().compareToIgnoreCase("852") == 0) && (cnt852 == locNr))
		{
			found = true;
			cnt852++;
			continue;
		}
		else if ((f.tag().compareToIgnoreCase("852") == 0) && (cnt852 > locNr))
		{
			break;
		}
		else if (f.tag().compareToIgnoreCase("852") == 0)
		{
			cnt852++;
			continue;
		}	
		if ( found && (f.tag().compareToIgnoreCase("866") == 0))
		{
			String f866a = ((MarcVblLengthField)f).firstSubfieldValue("a");
			if (f866a.trim() != null)
			{
				sb = ((sb.length() == 0) ? sb.append(f866a) : sb.append(";  ").append(f866a));
			}
		}

	}
	if (sb != null && sb.length() != 0)
	{
		String str = sb.toString().trim();
		out.addSubfield("3", str);
	}
    }


    /**
     * Determine if the input is a serials record.
     *
     * This is the canonical method for determining serial status of a holding.
     * This method is used by UCM converter to determine if a holding is a serial.
     *
     * @return true if the current record is a serials record
     */
    protected boolean isSerial()
    {
        String type     = inMarc.getRecordType();
        String biblevel = inMarc.getBibLvl();

        if ( (type.equalsIgnoreCase("a") || type.equalsIgnoreCase("m"))
             && (biblevel.equalsIgnoreCase("b") || biblevel.equalsIgnoreCase("s")) )
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
     * This method is used by UCM converter to determine if a holding is a monograph.
     *
     * @return true if the current record is a monograph record
     */
    protected boolean isMonograph()
    {
        return !isSerial();
    }

}
