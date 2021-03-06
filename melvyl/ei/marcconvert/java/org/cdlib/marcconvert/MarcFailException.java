package org.cdlib.marcconvert;

import org.apache.log4j.Logger;
import org.cdlib.util.marc.MarcException;


/**
 * Excption class for invalid MARC format.
 *
 * @author <a href="mailto:david.loy@ucop.edu">David Loy</a>
 * @version $Id: MarcFailException.java,v 1.1 2004/05/21 00:18:19 mreyes Exp $
 */
public class MarcFailException extends MarcException
{
	/**
	 * log4j Logger for this class.
	 */
    private static Logger log = Logger.getLogger(MarcFailException.class);

	/**
	 * CVS header string.
	 */
    public static final String cvsHeader = "$Header: /cvs/root/melvyl/ei/marcconvert/java/org/cdlib/marcconvert/MarcFailException.java,v 1.1 2004/05/21 00:18:19 mreyes Exp $";

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
     * Instantiate a new MarcFailException exception with the supplied
     * text.
     *
     * @param comment - programmer supplied comment describing error
     */
    public MarcFailException(String comment)
    {
        super(comment);
    }

    /**
     * Instantiate a new MarcFailException exception with the default
     * text.
     */
    public MarcFailException()
    {
        super("Marc object dropped");
    }

    /**
     * Instantiate a new MarcFailException exception with the supplied
     * text and a reference to the invoking object.
     *
     * @param obj - this value for calling object
     * @param comment - programmer supplied comment describing error
     */
    public MarcFailException(Object obj, String comment)
    {
        super(obj, comment);
    }
}
