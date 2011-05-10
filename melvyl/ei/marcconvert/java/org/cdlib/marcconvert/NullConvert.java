package org.cdlib.marcconvert;

import org.apache.log4j.Logger;
import org.cdlib.util.marc.MarcException;
import org.cdlib.util.marc.MarcRecord;

/**
 * This class performs a null conversion of marc records. This is useful
 * in case where you just want to validate the marc records are valid,
 * or you want to examine the side effects of passing marc records through
 * parsing and dumping processes. It is important to note, in most cases
 * you will want to turn off post conversion validation to avoid having
 * all the input records rejected for lack of an 852. Of course, if a
 * file is supposed to be loadable, this converter could be run with
 * validation turned on to check that it meets CDL standards. The Null
 * Converter is truly the swiss army knife of converters, and awaits
 * only the application of the user's imagination to realize its full
 * potential.
 *
 * @author <a href="mailto: karen.coyle@ucop.edu">Karen Coyle</a>
 * @version $Id: NullConvert.java,v 1.4 2002/10/22 21:49:51 smcgovrn Exp $
 */
public class NullConvert extends MarcConvert
{
	/**
	 * log4j Logger for this class.
	 */
    private static Logger log = Logger.getLogger(NullConvert.class);

	/**
	 * CVS header string.
	 */
    public static final String cvsHeader = "$Header: /cvs/root/melvyl/ei/marcconvert/java/org/cdlib/marcconvert/NullConvert.java,v 1.4 2002/10/22 21:49:51 smcgovrn Exp $";

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
     * Instantiate a new NullConver object.
     */
    public NullConvert()
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
     * This method moves only numeric tags to from the input marc object to the
     * output marc object, it isn't really null, if the input conatins alpha
     * tags, as some do.
     *
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

        //move all fields to output

        //moveAll();
        moveOut(000, 999);

        debugOut(2, "process output leader:" + outMarc.getLeaderValue());

        return rc;
    }

}
// end of NullConvert class
