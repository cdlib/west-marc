package org.cdlib.marcconvert;

import java.util.Vector;

import org.apache.log4j.Logger;
import org.cdlib.marcconvert.RunConvert;
import org.cdlib.util.marc.MarcException;
import org.cdlib.util.marc.MarcFieldList;
import org.cdlib.util.marc.MarcRecord;
import org.cdlib.util.marc.MarcSubfield;
import org.cdlib.util.marc.MarcVblLengthField;

/**
 * MARC21 record conversion for records with embedded holdings fields.
 * This is the abstract base class for those sources that produce marc
 * records with the holdings information already in 852 fields. Every
 * concrete subclass of this class must implement the build901() method.
 *
 * @author <a href="mailto: karen.coyle@ucop.edu">Karen Coyle</a>
 * @version $Id: MarcEmbedConvert.java,v 1.4 2002/11/12 21:40:12 smcgovrn Exp $
 */
public abstract class MarcEmbedConvert extends MarcConvert
{
	/**
	 * log4j Logger for this class.
	 */
    private static Logger log = Logger.getLogger(MarcEmbedConvert.class);

	/**
	 * CVS header string.
	 */
    public static final String cvsHeader = "$Header: /cvs/root/melvyl/ei/marcconvert/java/org/cdlib/marcconvert/MarcEmbedConvert.java,v 1.4 2002/11/12 21:40:12 smcgovrn Exp $";

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

    /**
     * Instanstiate a new MarcEmbedConvert object.
     */
    public MarcEmbedConvert()
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
	 * Build the 901 field in the output marc object.
     * Every concrete subclass of this class must implement this method.
     */
    abstract protected void build901();

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

				if (MarcSubfield.exists(in852.firstSubfield("b")))
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
						log.debug("No 852 $b found");
					}
				}
			}
		}
	}

    /**
     * Set the output 852 $a from the first input 852 $a.
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build852a(MarcVblLengthField in, MarcVblLengthField out)
    {
        //MARC: library identifier
        //output first 852a subfield

        String f852a = in.firstSubfieldValue("a");
        if (f852a != null)
        {
            out.addSubfield("a", f852a);
        }
    }

    /**
     * Set the output 852 $b from the first input 852 $b.
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build852b(MarcVblLengthField in, MarcVblLengthField out)
    {
        //MARC: library location code
        //output first 852b subfield
        String f852b = in.firstSubfieldValue("b");
        if (f852b != null)
        {
            out.addSubfield("b", f852b);
        }
    }

    /**
     * Copy the all the input 852 $e subfields to the output 852.
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build852e(MarcVblLengthField in, MarcVblLengthField out)
    {
        //MARC: library address
        //output all 852e subfields
        Vector eSubs = in.subfields("e", true);

        if (eSubs != null)
        {
            MarcSubfield[] sf = new MarcSubfield[eSubs.size()];
            eSubs.copyInto((Object[])sf);
            out.addSubfields(sf);
        }
    }

    /**
     * Copy the all the input 852 $f subfields to the output 852.
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build852f(MarcVblLengthField in, MarcVblLengthField out)
    {
        //MARC: location qualifier
        //output all 852f subfields
        Vector fSubs = in.subfields("f", true);

		if (fSubs != null)
		{
			MarcSubfield[] sf = new MarcSubfield[fSubs.size()];
			fSubs.copyInto((Object[])sf);
			out.addSubfields(sf);
		}
    }

    /**
     * Copy the all the input 852 $g subfields to the output 852.
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build852g(MarcVblLengthField in, MarcVblLengthField out)
    {
        //MARC: location qualifier
        //output all 852g subfields
        Vector gSubs = in.subfields("g", true);

		if (gSubs != null)
		{
			MarcSubfield[] sf = new MarcSubfield[gSubs.size()];
			gSubs.copyInto((Object[])sf);
			out.addSubfields(sf);
		}
    }

    /**
     * Set the output 852 $h from the first input 852 $h.
     * Set the output 852 $j from the first input 852 $j.
     * Copy the all the input 852 $i subfields to the output 852.
     * Copy the all the input 852 $k subfields to the output 852.
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build852hijk(MarcVblLengthField in, MarcVblLengthField out)
    {
        //MARC: call number, call number prefix
        //h and j are non-repeatable; output just first one
        //i and k are repeatable; output all

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

		Vector kSubs = in.subfields("k", true);
		if (kSubs != null)
		{
			MarcSubfield[] sf = new MarcSubfield[kSubs.size()];
			kSubs.copyInto((Object[])sf);
			out.addSubfields(sf);
		}
    }

    /**
     * Set the output 852 $l from the first input 852 $l.
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build852l(MarcVblLengthField in, MarcVblLengthField out)
    {
        //MARC: shelving form of title
        //output first 852l (el) subfield
        String f852l = in.firstSubfieldValue("l");
        if (f852l != null)
        {
            out.addSubfield("l", f852l);
        }
    }

    /**
     * Copy the all the input 852 $m subfields to the output 852.
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build852m(MarcVblLengthField in, MarcVblLengthField out)
    {
        //MARC: call number suffix
        //CDL: post call number notes
        //output all 852m subfields
		Vector mSubs = in.subfields("m", true);
		if (mSubs != null)
		{
			MarcSubfield[] sf = new MarcSubfield[mSubs.size()];
			mSubs.copyInto((Object[])sf);
			out.addSubfields(sf);
		}
    }

    /**
     * Set the output 852 $n from the first input 852 $n.
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build852n(MarcVblLengthField in, MarcVblLengthField out)
    {
        //MARC: physical piece condition
        //output first 852n subfield
        String f852n = in.firstSubfieldValue("n");
        if (f852n != null)
        {
            out.addSubfield("n", f852n);
        }
    }

    /**
     * Set the output 852 $p from the first input 852 $p.
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build852p(MarcVblLengthField in, MarcVblLengthField out)
    {
        //MARC: piece designation
        //CDL: RLF numbers
        //output first 852p subfield
        String f852p = in.firstSubfieldValue("p");
		if (f852p != null)
		{
		    out.addSubfield("p", f852p);
        }
    }

    /**
     * Set the output 852 $q from the first input 852 $q.
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build852q(MarcVblLengthField in, MarcVblLengthField out)
    {
        //MARC: physical piece condition
        //output first 852q subfield
        String f852q = in.firstSubfieldValue("q");
        if (f852q != null)
        {
            out.addSubfield("q", f852q);
        }
    }

    /**
     * Copy the all the input 852 $s subfields to the output 852.
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build852s(MarcVblLengthField in, MarcVblLengthField out)
    {
        //MARC: location qualifier
        //output all 852s subfields
        Vector sSubs = in.subfields("s", true);

		if (sSubs != null)
		{
			MarcSubfield[] sf = new MarcSubfield[sSubs.size()];
			sSubs.copyInto((Object[])sf);
			out.addSubfields(sf);
		}
    }

    /**
     * Set the output 852 $a from the first input 852 $t.
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build852t(MarcVblLengthField in, MarcVblLengthField out)
    {
        //MARC: copy number
        //output first 852t subfield
        String f852t = in.firstSubfieldValue("t");
        if (f852t != null)
        {
            out.addSubfield("t", f852t);
        }
    }

    /**
     * Copy the all the input 852 $x subfields to the output 852.
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build852x(MarcVblLengthField in, MarcVblLengthField out)
    {
        //MARC: non-public note
        //output all 852x subfields
		Vector xSubs = in.subfields("x", true);
		if (xSubs != null)
		{
			MarcSubfield[] sf = new MarcSubfield[xSubs.size()];
			xSubs.copyInto((Object[])sf);
			out.addSubfields(sf);
		}
    }

    /**
     * Copy the all the input 852 $z subfields to the output 852.
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build852z(MarcVblLengthField in, MarcVblLengthField out)
    {
        //MARC: public note
        //output all 852z subfields
        Vector zSubs = in.subfields("z", true);
		if (zSubs != null)
		{
			MarcSubfield[] sf = new MarcSubfield[zSubs.size()];
			zSubs.copyInto((Object[])sf);
			out.addSubfields(sf);
		}
    }

    /**
     * Set the output 852 $3 from the first input 852 $3.
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build8523(MarcVblLengthField in, MarcVblLengthField out)
    {
        //MARC: materials specified
        //CDL: summary holdings statement
        //output first 8523 subfield
        String f8523 = in.firstSubfieldValue("3");
        if (f8523 != null)
        {
            out.addSubfield("3", f8523);
        }
    }

    /**
     * Set the output 852 $D from the first input 852 $D,
     * and set the output 852 $O from the first input 852 $O.
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build852DO(MarcVblLengthField in, MarcVblLengthField out)
    {
        //CDL: Collection development statement
        //output first D and first O (letter O) subfields
        String f852D = in.firstSubfieldValue("D");
        if (f852D != null)
        {
            out.addSubfield("D", f852D);
        }

        String f852O = in.firstSubfieldValue("O");
        if (f852O != null)
        {
            out.addSubfield("O", f852O);
        }
    }

}
