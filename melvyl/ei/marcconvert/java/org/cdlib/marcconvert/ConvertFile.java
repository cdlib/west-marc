package org.cdlib.marcconvert;

import java.io.File;
import java.io.FileOutputStream;
import org.apache.log4j.Logger;
import org.cdlib.util.marc.MarcParmException;

/**
 * A wrapper class for a <code>java.io.FileOutputStream</code>.
 *
 * @author <a href="mailto:david.loy@ucop.edu">David Loy</a>
 * @author <a href="mailto:shawnm@splorkin.com">Shawn McGovern</a>
 * @version $Id: ConvertFile.java,v 1.4 2002/10/22 21:49:50 smcgovrn Exp $
 */
public class ConvertFile
{
	/**
	 * log4j Logger for this class.
	 */
    private static Logger log = Logger.getLogger(ConvertFile.class);

	/**
	 * CVS header string.
	 */
    public static final String cvsHeader = "$Header: /cvs/root/melvyl/ei/marcconvert/java/org/cdlib/marcconvert/ConvertFile.java,v 1.4 2002/10/22 21:49:50 smcgovrn Exp $";

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
	 * The <code>FileOutputStream</code> this object wraps.
	 */
    private FileOutputStream fFile = null;

    /**
     * Indicator used to track this object's open or closed status.
     */
    private boolean cfOpen = false;

	/**
	 * Instantiate a new <code>ConvertFile</code> object using the specified
	 * file name.
	 */
    public ConvertFile(String inType, String fileName)
    {
        if ((fileName == null) || (fileName.length() == 0))
        {
            throw new MarcParmException(this, "ConvertFile() - no file name specified");
        }

        try
        {
            fFile = new FileOutputStream(new File(fileName));
        }
        catch (Exception e)
        {
            throw new MarcParmException(this, "ConvertFile() - "  + e.getMessage());
        }

        cfOpen = true;
    }

    /**
     * Write a string to a ConvertFile. All end of line handling must be done by caller.
     *
     * @param str string to be written
     */
    public void write(String str)
    {
        if (cfOpen && str != null && str.length() > 0)
        {
            try
            {
                byte[] bytes = str.getBytes();
                fFile.write(bytes, 0, str.length());
            }
            catch (Exception e)
            {
                throw new MarcParmException(this, "ConvertFile.write() - " + e.getMessage());
            }
        }
    }

    /**
     * Close this object and the underlying output stream.
     */
    public void close()
    {
        if (cfOpen)
        {
            try
            {
                cfOpen = false;
                fFile.close();
            }
            catch (Exception e)
            {
            }
        }
    }

}
