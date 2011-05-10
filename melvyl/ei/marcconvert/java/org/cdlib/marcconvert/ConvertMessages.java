package org.cdlib.marcconvert;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeMap;
import org.apache.log4j.Logger;
import org.cdlib.util.marc.MarcConstants;

/**
 * A collection of messages keyed by number.
 * Used by the cdl marc processing class library for error and reject messages.
 *
 *
 * @author <a href="mailto:shawnm@splorkin.com">Shawn McGovern</a>
 * @version $Id: ConvertMessages.java,v 1.5 2010/04/30 22:33:36 aleph16 Exp $
 *
 */

public class ConvertMessages
    implements ConvertConstants, MarcConstants
{
	/**
	 * log4j Logger for this class.
	 */
    private static Logger log = Logger.getLogger(ConvertMessages.class);

	/**
	 * CVS header string.
	 */
    public static final String cvsHeader = "$Header: /cvs/root/melvyl/ei/marcconvert/java/org/cdlib/marcconvert/ConvertMessages.java,v 1.5 2010/04/30 22:33:36 aleph16 Exp $";

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
	 * Code for the 'no 852 was generated' messages.
	 */
    public static String ERR_CODE_NO_LOCATION_CODE = "1001";

	/**
	 * Short message for the 'no 852 was generated' error.
	 */
    public static String ERR_MSGS_NO_LOCATION_CODE = "no 852 was generated";

	/**
	 * Long message for the 'no 852 was generated' error.
	 */
    public static String ERR_MSGL_NO_LOCATION_CODE = "Record did not contain a location code in the expected place; no 852 was generated";

 	/**
	 * Code for the 'no 245 field' messages.
	 */
	public static String ERR_CODE_NO_245_FIELD = "1002";

	/**
	 * Short message for the 'no 245 field' error.
	 */
    public static String ERR_MSGS_NO_245_FIELD = "no 245 field";

	/**
	 * Long message for the 'no 245 field' error.
	 */
    public static String ERR_MSGL_NO_245_FIELD = "Record contains no 245 field";

 	/**
	 * Code for the 'unknown location code' messages.
	 */
    public static String ERR_CODE_UNKNOWN_LOCATION = "1003";

	/**
	 * Short message for the 'unknown location code' error.
	 */
    public static String ERR_MSGS_UNKNOWN_LOCATION = "unknown location code";

	/**
	 * Long message for the 'unknown location code' error.
	 */
    public static String ERR_MSGL_UNKNOWN_LOCATION = "Record contains the unknown location code";

 	/**
	 * Code for the 'invalid leader type' messages.
	 */
    public static String ERR_CODE_INVALID_LEADER_TYPE = "1004";

	/**
	 * Short message for the 'invalid leader type' error.
	 */
    public static String ERR_MSGS_INVALID_LEADER_TYPE = "invalid leader type";

	/**
	 * Long message for the 'invalid leader type' error.
	 */
    public static String ERR_MSGL_INVALID_LEADER_TYPE = "Record contains the invalid leader type";

 	/**
	 * Code for the 'invalid bibliographic level' messages.
	 */
    public static String ERR_CODE_INVALID_BIB_LEVEL = "1005";

	/**
	 * Short message for the 'invalid bibliographic level' error.
	 */
    public static String ERR_MSGS_INVALID_BIB_LEVEL = "invalid bibliographic level";

	/**
	 * Long message for the 'invalid bibliographic level' error.
	 */
    public static String ERR_MSGL_INVALID_BIB_LEVEL = "Record contains the invalid bibliographic level";

        /**
         * Code for the 'no field 856' messages.
         */
    public static String ERR_CODE_NO_856_FIELD = "1006";

        /**
         * Short message for the 'no 856 field' error.
         */
    public static String ERR_MSGS_NO_856_FIELD = "no 856 field";

        /**
         * Long message for the 'Record contains no 856 field' error.
         */
    public static String ERR_MSGL_NO_856_FIELD = "Record contains no 856 field";



	/**
	 * Message number by short message map.
	 */
	private static TreeMap shortMsgTbl = null;

	/**
	 * Message number by long message map.
	 */
	private static TreeMap longMsgTbl = null;

	/**
	 * A convenience structure containing all of the above defined codes and
	 * associated short and long messages. Used by the methods that build the
	 * short and long message maps. If new errors are added to this class they
	 * should be add to this structure as well.
	 */
	private static String[][] msgTbl=
	{
		{
			ERR_CODE_NO_LOCATION_CODE, ERR_MSGS_NO_LOCATION_CODE, ERR_MSGL_NO_LOCATION_CODE
		},
		{
			ERR_CODE_NO_245_FIELD, ERR_MSGS_NO_245_FIELD, ERR_MSGL_NO_245_FIELD
		},
		{
			ERR_CODE_UNKNOWN_LOCATION, ERR_MSGS_UNKNOWN_LOCATION, ERR_MSGL_UNKNOWN_LOCATION
		},
		{
			ERR_CODE_INVALID_LEADER_TYPE, ERR_MSGS_INVALID_LEADER_TYPE, ERR_MSGL_INVALID_LEADER_TYPE
		},
		{
			ERR_CODE_INVALID_LEADER_TYPE, ERR_MSGS_INVALID_LEADER_TYPE, ERR_MSGL_INVALID_LEADER_TYPE
		},
		{
			ERR_CODE_INVALID_BIB_LEVEL, ERR_MSGS_INVALID_BIB_LEVEL, ERR_MSGL_INVALID_BIB_LEVEL
		},
		{
			ERR_CODE_NO_856_FIELD, ERR_MSGS_NO_856_FIELD, ERR_MSGL_NO_856_FIELD
		}
	};

	/**
	 * Return the short message for the given code.
	 */
	public static String lookupShortMessage(String key)
	{
		if ( shortMsgTbl == null )
		{
			buildShortMsgTbl();
		}
		return (String) shortMsgTbl.get(key);
	}

	/**
	 * Return the long message for the given code.
	 */
	public static String lookupLongMessage(String key)
	{
		if ( longMsgTbl == null )
		{
			buildLongMsgTbl();
		}
		return (String) longMsgTbl.get(key);
	}

	/**
	 * Build the code by short message map.
	 */
	private static void buildShortMsgTbl()
	{
		shortMsgTbl = new TreeMap();
		int max = msgTbl.length;
		for ( int i = 0; i < max; i++ )
		{
			shortMsgTbl.put(msgTbl[i][0], msgTbl[i][1]);
			if ( log.isDebugEnabled() )
			{
				log.debug("short msg tbl: added key '" + msgTbl[i][0]);
				printShortKeys();
			}
		}
	}

	/**
	 * Build the code by long message map.
	 */
	private static void buildLongMsgTbl()
	{
		longMsgTbl = new TreeMap();
		int max = msgTbl.length;
		for ( int i = 0; i < max; i++ )
		{
			shortMsgTbl.put(msgTbl[i][0], msgTbl[i][2]);
		}
	}

	private static void printShortKeys()
	{
		log.debug("short msg tbl: first key = " + (String)shortMsgTbl.firstKey());
		log.debug("short msg tbl: last key = " + (String)shortMsgTbl.lastKey());
		Set keys = shortMsgTbl.keySet();
		Iterator iter = keys.iterator();
		int i = 0;
		while ( iter.hasNext() )
		{
			i++;
			log.debug("short msg tbl: key[" + i + "] = " + (String)iter.next());
		}
	}

}
