package org.cdlib.marcconvert;

import java.util.Enumeration;
import java.util.Vector;

import org.apache.log4j.Logger;

import org.cdlib.util.marc.MarcException;
import org.cdlib.util.marc.MarcFieldList;
import org.cdlib.util.marc.MarcRecord;
import org.cdlib.util.marc.MarcSubfield;
import org.cdlib.util.marc.MarcVblLengthField;
import org.cdlib.util.string.StringUtil;

/**
 * Convert the marc records from the RLIN sources. Those sources are:
 * CHS, LBL, and UCB Law. Unlike the III sources, which make use of
 * base class, that is extended by source specific converters, this
 * is an all in one converter. This class must be instantiated with
 * valid campus code. It is this campus code that is used to control
 * source specific behaviour.
 *
 * @author <a href="mailto:shawnm@splorkin.com">Shawn McGovern</a>
 * @version $Id: RL21Convert.java,v 1.1 2005/04/05 17:19:28 rkl Exp $
 */
public class RL21Convert extends MarcEmbedConvert
{
	/**
	 * log4j Logger for this class.
	 */
    private static Logger log = Logger.getLogger(RL21Convert.class);

	/**
	 * CVS header string.
	 */
    public static final String cvsHeader = "$Header: /cvs/root/melvyl/ei/marcconvert/java/org/cdlib/marcconvert/RL21Convert.java,v 1.1 2005/04/05 17:19:28 rkl Exp $";

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
     * Numerical code for California Historical Society records
     */
    public static final int CHS = 0;

    /**
     * Numerical code for Lawrence Berkeley Labs records
     */
    public static final int LBL = 1;

    /**
     * Numerical code for UCB Law records
     */
    public static final int BOALT = 2;

     /**
     * The minimun allowed value for campus code.
     */
	private static final int MINIMUM_CODE = 0;

    /**
     * The maximun allowed value for campus code.
     */
    private static final int MAXIMUM_CODE = 2;

    /**
     * Numerial code for source of records being converted. This value is set
     * by the contructor and must be within the range of MINIMUM_CODE to
     * MAXIMUM_CODE as defined within this class.
     */
    private int institution;

    /*
     * True iff a CHS AMC record
     */
    private boolean isAMC;

    /*
     * RLIN fill character
     */
    private static final char FILL_CHAR = '\u007C';

    /*
     * RLIN fill character as a one-character string
     */
    private static final String FILL_STRING = "\u007C";


    /**
     * Construct a new RL21Converter for the specified institution
     *
     * @param campus Numerical code for source of records. E.g.,
     * RL21Convert.BOALT, RL21Convert.LBL, etc.
     */
    public RL21Convert(int campus)
    {
        super();

        // Validate campus code
        if (campus < MINIMUM_CODE || campus > MAXIMUM_CODE)
        {
            throw new RuntimeException("Unknown campus code");
        }

        institution = campus;
    }


    /**
     * Convert the given marc record. Place the result into the given output
     * marc record parameter variable. The status of the conversion is returned.
     *
     * @param inRec the <code>MarcRecord</code> to convert
     * @param outRec the converted <code>MarcRecord</code>
     * @return the conversion status code
     * @throws MarcDropException whenever a record is rejected
     * @see org.cdlib.marcconvert.ConvertConstants
     */
    public int convert(MarcRecord inRec, MarcRecord outRec)
        throws MarcDropException
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
     * @throws MarcDropException whenever a record is rejected
     * @see org.cdlib.marcconvert.ConvertConstants
     */
    private int convert()
        throws MarcDropException
    {
        int rc = CONVERT_REC_SUCCESS;

        // Set default debug level
        setDebug(0);

        // Set status for this record
        setStatus(OK);

        // Verify record source
        if ( ! verifyCatalogingSource() )
        {
            throw new MarcDropException("Unknown or incorrect cataloging source");
        }

        // Determine if this is an Archive and Manuscript Control (AMC) record
        if (institution == RL21Convert.CHS)
        {
            String v852b = inMarc.getFirstValue("852", "b");
            isAMC = (exists(v852b) && v852b.trim().equals(libraryIdentifier()));
        }
        else
        {
            isAMC = false;
        }

        // Move the input leader to output
        moveLeader();

        // Move all fields within 000 to 899 to output
        moveOut(0, 899);

        // Drop unwanted fields
        dropFields();

        // Build 901 field based on input 001
        build901();

        // Build 852 fields from input 852 fields
        build852();

        debugOut(2,"process output leader:" + outMarc.getLeaderValue());

        return rc;
    }


    /**
     * Returns true if the cataloging source matches that for
     * the institution requested when the constructor was called.
     *
     * Returns false if the record lacks an 001 or if
     * the cataloging source doesn't match expectations.
     */
    private boolean verifyCatalogingSource()
    {
        // Get the library identifier from the record
        String v001 = inMarc.getFirstValue("001", "");

        // Note: exists() test is not strong enough here,
        // since the subtring will fail if the length is less than 4,
        // not just greater than 0.
        if (v001 == null || v001.length() < 4) return false;
        String li = v001.substring(0,4);

        // Get the expected library identifier
        String requiredLI = libraryIdentifier();
        if (!exists(requiredLI)) return false;

        // Make sure that the actual and the expected
        // identifiers match
        return li.equals(requiredLI);
    }


    /**
     * Return the library identifier for the institution
     * specified when the constructor was called to create
     * this instance.
     *
     * @return Library identifier for this institution.
     * E.g., "CHSV", "CUBL", etc., or null if institution
     * is unknown.
     */
     private String libraryIdentifier()
     {
        String li;
        switch (institution)
        {
        case CHS:
            li = "CHSV";
            break;

        case LBL:
            li = "CLBI";
            break;

        case BOALT:
            li = "CUBL";
            break;

        default:
            li = null;  // This should never happen
            break;

        }
        return li;
    }


    /**
     * Drop unwanted fields from the output marc object
     */
    private void dropFields()
    {
        // Drop 000
        outMarc.deleteFields("000");

        // Drop 541 for CHS
        if (institution == RL21Convert.CHS) outMarc.deleteFields("541");

        // Drop 583 for CHS
        if (institution == RL21Convert.CHS) outMarc.deleteFields("583");

        // Drop 590
        outMarc.deleteFields("590");

        //delete any 852 fields
        outMarc.deleteFields("852");

        // Drop 9XX (Shouldn't be necessary, but ... )
        outMarc.deleteFields("9XX");
    }


    /**
     * Build MARC 901 field for output record based on input
     * 901. Throw MarcDropException if input 001 is missing,
     * if the code for the specified institution is unknown,
     * or if the library identifier is incorrect.
     */
    protected void build901()
    {
        // get the first 901 field
        String v001 = inMarc.getFirstValue("001", "");
        if ( !exists(v001) )
        {
            throw new MarcDropException("Input lacks 001");
        }

        // Determine the library identifier, which will go in the $a
        String lid = v001.substring(0,4);

        // Make sure the identifier is OK.
        String requiredLI = libraryIdentifier();
        if ( !exists(requiredLI) || !exists(lid) || !lid.equals(requiredLI) )
        {
            throw new MarcDropException("Unknown or incorrect library identifier");
        }

        // Determine the record identifier, which will go in the $b
        String rid = v001.substring(4);

        // Add the output 901
        MarcVblLengthField out901 = outMarc.getNewVblField("901", "  ");
        out901.addSubfield("a", lid);
        if ( exists(rid) )
        {
            out901.addSubfield("b", rid);
        }
    }



    /*
     * Remove leading and trailing backslashes, and replace internal
     * backslashes with spaces.  Returns the result, or null if either
     * the input or result is null or empty.
     */
    private String replaceBackslashes(String s)
    {
        return StringUtil.stripBackSlash(s);
    }


    /**
     * For each incoming RLIN 852 field will generate a CDL 852 field unless the RLIN 852
     * lacks a subfield $b. In that case, the incoming 852 should be ignored and no 852
     * shuold be generated from it.
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
                                build8523(in852, out852);
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
     * Move the library id from 001 to the output 852 $a.
     * Use the value of the RLIN LI(library identifier) as the 852 $a.
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build852a(MarcVblLengthField in, MarcVblLengthField out)
    {
        //MARC: library identifier
        //Same value as output in the 901 $a.
        String f901a = outMarc.getFirstValue("901", "a");
	//Invalid 901 $a should have been checked in build901(); no checking here.
        out.addSubfield("a", f901a);
    }


    /**
     * Set the output 852 $b from the first input 852 $b.
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build852b(MarcVblLengthField in, MarcVblLengthField out)
    {
        //MARC: library location code
        //output first 852b subfield; if AMC record output "MAIN" to 852 $b.
        String f852b = in.firstSubfieldValue("b");
        if (f852b != null)
        {
	    String b = (isAMC ? "MAIN" : f852b);
            out.addSubfield("b", b);
        }
    }


    /**
     * Copy the all the input 852 $c subfields to the output 852.
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build852c(MarcVblLengthField in, MarcVblLengthField out)
    {
        //MARC: shelving location(repeatable)
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
                        out.addSubfield("h", replaceBackslashes(f852h));
                }

        Vector iSubs = in.subfields("i", true);
                if (iSubs != null)
                {
                        MarcSubfield[] sf = new MarcSubfield[iSubs.size()];
                        iSubs.copyInto((Object[])sf);
			int size = sf.length;
			for (int i = 0; i < size; i++)
			{
				if (MarcSubfield.exists(sf[i]))
				{
					out.addSubfield("i", replaceBackslashes((String)sf[i].value()));
				}
			}
                }

        String f852j = in.firstSubfieldValue("j");
                if (f852j != null)
                {
                        out.addSubfield("j", replaceBackslashes(f852j));
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
     * Set the output 852 $p from the first input 852 $p.
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build852p(MarcVblLengthField in, MarcVblLengthField out)
    {
        //MARC: piece designation
        //CDL: RLF numbers
        //output first 852p subfield
	//Check to see if subfield contains the values "SRLF:" or "NRLF:". If it does,
	//drop the "SRLF:" or "NRLF:" and output what remains as the $p. If the incoming $p
	//does not contain one of those values, do not output that $p to the CDL 852.

	Vector pSubs = in.subfields("p", true);
	if (pSubs != null)
	{
		for (int i = 0; i < pSubs.size(); i++)
		{
			MarcSubfield sf = (MarcSubfield) pSubs.elementAt(i);
			String f852p = sf.value();
		        if (f852p != null)
        		{
				int idxSrlf = f852p.indexOf("SRLF:");
				int idxNrlf = f852p.indexOf("NRLF:");
				String f852pRlf = null;
				if (idxSrlf >= 0) 
				{	
					f852pRlf = f852p.substring(idxSrlf + 5).trim();
				}
				if (idxNrlf >= 0)
				{
					f852pRlf = f852p.substring(idxNrlf + 5).trim();
				}
				if (f852pRlf != null)
				{
               				out.addSubfield("p", f852pRlf);
					break;
				}
        		}
        	}
	}
    }

    /**
     * Copy all the input 852 $x subfields to the output 852.
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build852x(MarcVblLengthField in, MarcVblLengthField out)
    {
        //MARC: nonpublic note
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
     * Copy the contents of each 590 $a to the output 852 $z.
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build852z(MarcVblLengthField in, MarcVblLengthField out)
    {
        //MARC: public note(R)
	//Move the contents of each 590 $a in the input record to a $z that is being output for
	//the record. There may be more than one 590 in a record, but each 590 should have only
	//one $a.

        Vector v590a = new Vector();
        MarcFieldList in590s = inMarc.allFields("590");

        if (in590s != null)
        {
                int nr590s = in590s.size();
                for (int i = 0; i < nr590s; i++)
                {
                        MarcVblLengthField in590 = (MarcVblLengthField) in590s.elementAt(i);
                        MarcSubfield f590a = in590.firstSubfield("a");
                        if (f590a != null)
                        {
				v590a.addElement(f590a);
                        }
                }
        }

        Vector zSubs = in.subfields("z", true);
	//Boalt's incoming $z will be used to create an 852 $3.
        if ((zSubs != null) && (institution != RL21Convert.BOALT))
        {
		for (int i = 0; i < zSubs.size(); i++)
		{
			MarcSubfield f852z = (MarcSubfield) zSubs.elementAt(i);
			String f852zStr = new String((String) f852z.value());

			//Unless the "Library has:" begins at the very beginning of the
			//subfield, the subfield will be output to 852 $z.
			if (f852zStr.indexOf("Library has:") != 0)
			{
				out.addSubfield("z", f852zStr);
			}
		}
        }

        if (v590a != null)
        {
		for (int i = 0; i < v590a.size(); i++)
		{
			out.addSubfield("z", ((MarcSubfield) v590a.elementAt(i)).value());
		}
        }
    }


    /**
     * Copy the input 852 $z subfield, if it begins with "Library has:", to the output 852 $3.
     * @param in the input 852 field
     * @param out the output 852 field
     */
    protected void build8523(MarcVblLengthField in, MarcVblLengthField out)
    {
        //MARC: materials specified(summary holdings)

        Vector zSubs = in.subfields("z", true);
        if (zSubs != null)
        {
		StringBuffer sb = new StringBuffer(100);
                for (int i = 0; i < zSubs.size(); i++)
                {
                        MarcSubfield f852z = (MarcSubfield) zSubs.elementAt(i);
                        String f852zStr = new String((String) f852z.value());

                        //If the $z begins with "Library has:", remove the "Library has:" string and 
                        //output the remainder to 852 $3. If there is more than one $z that begins
			//"Library has:", output each in the single $3 and separate them with a 
			//semicolon followed by a space.
                        if (f852zStr.indexOf("Library has:") == 0)
			{
                                if (sb.length() == 0)
                                {
                                        sb.append(f852zStr.substring(("Library has:").length()));
                                }
                                else
                                {
                                        sb.append("; " + f852zStr.substring(("Library has:").length()));
                                }
			}
			//For Boalt only: if the $z does not begin with "Library has:", move the 
			// $z to output 852 $3.
			if ((f852zStr.indexOf("Library has:") != 0) && (institution == RL21Convert.BOALT))
                        {
				if (sb.length() == 0)
				{
					sb.append(f852zStr);
				}
				else
				{
					sb.append("; " + f852zStr);
				}
                        }
                }
		if (sb.length() > 0)
		{
			out.addSubfield("3", sb.toString());
		}
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
     * This method is used by UCIR converter to determine if a holding is a monograph.
     *
     * @return true if the current record is a monograph record
     */
    protected boolean isMonograph()
    {
        return !isSerial();
    }

}
