package org.cdlib.marcconvert;

import org.apache.log4j.Logger;

import org.cdlib.util.marc.MarcException;
import org.cdlib.util.marc.MarcFieldList;
import org.cdlib.util.marc.MarcRecord;
import org.cdlib.util.marc.MarcSubfield;
import org.cdlib.util.marc.MarcVblLengthField;

/**
 * UC Davis Aleph record conversion. This is a concrete subclass of the
 * MarcEmbedConvert abstract class.
 *
 * @author <a href="mailto: karen.coyle@ucop.edu">Karen Coyle</a>
 * @version $Id: UCDConvert.java,v 1.7 2002/10/29 00:23:28 smcgovrn Exp $
 */
public class UCDConvert extends MarcEmbedConvert
{
	/**
	 * log4j Logger for this class.
	 */
    private static Logger log = Logger.getLogger(UCDConvert.class);

	/**
	 * CVS header string.
	 */
    public static final String cvsHeader = "$Header: /cvs/root/melvyl/ei/marcconvert/java/org/cdlib/marcconvert/UCDConvert.java,v 1.7 2002/10/29 00:23:28 smcgovrn Exp $";

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
     * Instanstiate a new UCDConvert object.
     */
    public UCDConvert()
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
				MarcVblLengthField in852 = (MarcVblLengthField)in852s.elementAt(locNr);

				if (MarcSubfield.exists(in852.firstSubfield("b")) &&
					MarcSubfield.exists(in852.firstSubfield("c")))
				{
                    //build output 852
                    //852 - new variable field in output record
                    MarcVblLengthField out852 = outMarc.getNewVblField("852", "  ");

                    //build output 852 in desired order
                    //see individual methods for more info
                    build852a(in852, out852);
                    build852b(in852, out852);
                    build852e(in852, out852);
                    build852f(in852, out852);
                    build852g(in852, out852);
                    build852hijk(in852, out852);
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
                        log.debug("Missing 852 $b or $c");
                    }
				}
			}
		}
	}


    /**
	 * Build the 901 field in the output marc object.
     * Check the cataloging source in the first 4 bytes of the 001 field,
	 * this must be "UCD-", and goes into the 901 $a subfield.
	 * The rest of the 001 field goes into the 901 $b subfield, which cannot
	 * be empty, so the incoming 001 field must have length at least 5.
	 *
	 * @throws MarcDropException if input has no 001 field
	 *                           or the 001 field has length less than 5
	 *                           or the cataloging source is not 'UCD-' (901 $a)
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

		// 001 field must be at least 5 bytes: 4 for the 901 $a, and 1 for the 901 $a
		if (in001.length() < 5)
        {
            throw new MarcDropException("Input 001 has length less than 5");
        }

		// get the 901 $a field, cataloging source
		String f901a = in001.substring(0,4);

		// make sure the cataloging source is UCD
		if (! "UCD-".equalsIgnoreCase(f901a))
        {
            throw new MarcDropException("Not UCD Source");
        }

		// get the 901 $b field, record id
		String f901b = in001.substring(4);

		// make sure the record id exists
		if (f901b == null || f901b.length() == 0)
		{
			throw new MarcDropException("No incoming record ID");
		}

		// set up output 901 field
		MarcVblLengthField out901 = outMarc.getNewVblField("901", "  ");
		out901.addSubfield("a", "DVXL");
		out901.addSubfield("b", f901b);
    }


    /**
     * Force the output 852 $a to be "DVXL".
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build852a(MarcVblLengthField in, MarcVblLengthField out)
    {
        //MARC: library identifier
        //UCD 852a will always be "DVXL"
        out.addSubfield("a", "DVXL");
    }


    /**
     * Set the output 852 $b by combining the first $b subfield value
	 * with the first $c subfield value from the input 852.
     * @param in the input 852 field
     * @param out the output 852 field
     */
	protected void build852b(MarcVblLengthField in, MarcVblLengthField out)
    {
        //MARC: library location code
		//combine incoming $b and $c

        String f852b = in.firstSubfieldValue("b");
        String f852c = in.firstSubfieldValue("c");
        if ((f852b != null) && (f852c != null))
        {
            String loc = (f852b + "-" + f852c);
            out.addSubfield("b", loc);
        }
    }

}
