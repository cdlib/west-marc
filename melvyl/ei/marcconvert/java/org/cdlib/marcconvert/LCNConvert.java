package org.cdlib.marcconvert;

import org.apache.log4j.Logger;
import org.cdlib.util.marc.MarcException;
import org.cdlib.util.marc.Field;
import org.cdlib.util.marc.MarcFieldList;
import org.cdlib.util.marc.MarcRecord;

/**
 * LC Name Authorites Marc conversion.
 *
 *
 * @author <a href="mailto: karen.coyle@ucop.edu">Karen Coyle</a>
 * @version $Id: LCNConvert.java,v 1.5 2002/10/22 21:49:51 smcgovrn Exp $
 */
public class LCNConvert extends MarcConvert
{
	/**
	 * log4j Logger for this class.
	 */
    private static Logger log = Logger.getLogger(LCNConvert.class);

	/**
	 * CVS header string.
	 */
    public static final String cvsHeader = "$Header: /cvs/root/melvyl/ei/marcconvert/java/org/cdlib/marcconvert/LCNConvert.java,v 1.5 2002/10/22 21:49:51 smcgovrn Exp $";

	/**
	 * CVS version string.
	 */
    public static final String version = "$Revision: 1.5 $";

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
     * Instantiate a new LCNConvert object.
     */
    public LCNConvert()
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

    //====================================================
    //       PRIVATE
    //====================================================

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

        MarcFieldList mfl = null;
        Field         f   = null;

        if ( (((mfl = inMarc.allFields("4XX")) != null) && mfl.size() > 0)
             || (((mfl = inMarc.allFields("5XX")) != null) && mfl.size() > 0)
             || ((f = inMarc.getFirstField(663)) != null)
             || ((f = inMarc.getFirstField(664)) != null)
             || ((f = inMarc.getFirstField(665)) != null)
             || ((f = inMarc.getFirstField(666)) != null) )
        {
            //move the input leader to output
            moveLeader();

            //move all numeric fields under 900 to output
            moveOut(000, 899);
        }
        else
        {
            rc = CONVERT_REC_SKIP;
        }

        debugOut(2, "process output leader:" + outMarc.getLeaderValue());

        return rc;
    }

}
