package org.cdlib.marcconvert.run;

import java.util.Date;
import org.apache.log4j.Logger;
import org.cdlib.marcconvert.OCLCConvert;
import org.cdlib.marcconvert.RunConvert;
import org.cdlib.util.marc.MarcParmException;


/**
 * This class performs conversion of marc records from the OCLC sources
 * by passing a OCLCConvert converter object to RunConvert and invoking
 * the RunConvert.convert() method.
 *
 * @author <a href="mailto:jgarey@library.berkeley.edu">Janet Garey</a>
 * @author <a href="mailto:shawnm@splorkin.com">Shawn McGovern</a>
 * @version $Id: RunOCLC.java,v 1.6 2002/11/12 21:41:09 smcgovrn Exp $
 */
public class RunOCLC
{
	/**
	 * log4j Logger for this class.
	 */
    private static Logger log = Logger.getLogger(RunOCLC.class);

	/**
	 * CVS header string.
	 */
    public static final String cvsHeader = "$Header: /cvs/root/melvyl/ei/marcconvert/java/org/cdlib/marcconvert/run/RunOCLC.java,v 1.6 2002/11/12 21:41:09 smcgovrn Exp $";

	/**
	 * CVS version string.
	 */
    public static final String version = "$Revision: 1.6 $";

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
     * Run the conversion. Log the start and end times. This method does
     * not process any command line parameters, however it does require
     * that a propertes file name be specified as an environment variable
     * using the -D switch, like so: -Dconfig=<configfile>. Logging must
     * also be setup by specifying a log4j properties file with -D switch,
     * like so: -Dlog4j.configuration=<log4j.props>, this file must be in
     * the classpath.
     *
     * @param args the command line parameters
     */
    public static void main(String args[])
    {
        try
        {
            Date startDate = new Date();
            System.out.println("Job started " + startDate);

            // Change the thread name so we can easily identify messages
            // from this run in the log.
            Thread t = Thread.currentThread();
            t.setName("RunOCLC_" + startDate.getTime());

            log.info("OCLC conversion started");

            OCLCConvert oclc = new OCLCConvert();

            // Get a new RunConvert using our conversion class.
            RunConvert run = new RunConvert(oclc);

            //RunConvert.process handles all I/O and the calling
            //of the MarcConvert routines to perform the conversion
            int rc = run.convert();
            log.info("OCLC conversion completed - return code = " + rc);
            System.out.println("Job completed " + new java.util.Date() + " rc = " + rc);
            System.exit(rc);
        }
        catch (MarcParmException e)
        {
            log.error("MarcParmException: " + e.getMessage(), e);
        }
        catch (Exception e)
        {
            log.error("Exception: " + e.getMessage(), e);
        }
    }

}
