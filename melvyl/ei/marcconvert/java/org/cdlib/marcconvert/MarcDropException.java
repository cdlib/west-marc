package org.cdlib.marcconvert;

import org.apache.log4j.Logger;
import org.cdlib.util.marc.MarcException;


/**
 * Excption class for invalid MARC format.
 *
 * @author <a href="mailto:david.loy@ucop.edu">David Loy</a>
 * @version $Id: MarcDropException.java,v 1.5 2002/10/29 00:23:28 smcgovrn Exp $
 */
public class MarcDropException extends MarcException
{
	/**
	 * log4j Logger for this class.
	 */
    private static Logger log = Logger.getLogger(MarcDropException.class);

	/**
	 * CVS header string.
	 */
    public static final String cvsHeader = "$Header: /cvs/root/melvyl/ei/marcconvert/java/org/cdlib/marcconvert/MarcDropException.java,v 1.5 2002/10/29 00:23:28 smcgovrn Exp $";

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
     * Instantiate a new MarcDropException exception with the supplied
     * text.
     *
     * @param comment - programmer supplied comment describing error
     */
    public MarcDropException(String comment)
    {
        super(comment);
    }

    /**
     * Instantiate a new MarcDropException exception with the default
     * text.
     */
    public MarcDropException()
    {
        super("Marc object dropped");
    }

    /**
     * Instantiate a new MarcDropException exception with the supplied
     * text and a reference to the invoking object.
     *
     * @param obj - this value for calling object
     * @param comment - programmer supplied comment describing error
     */
    public MarcDropException(Object obj, String comment)
    {
        super(obj, comment);
    }
}
