package org.cdlib.marcconvert;

/**
 * A collection of constants used by the marc converter classes to indicate
 * the status of a particular record conversion, or the status of a converter
 * run. Use of these symbolic names over their numeric values is stongly
 * encouraged for reasons of ease of maintenance and code readability.
 *
 * @author <a href="mailto:shawnm@splorkin.com">Shawn McGovern</a>
 * @version $Id: ConvertConstants.java,v 1.4 2002/10/22 21:49:50 smcgovrn Exp $
 */

public interface ConvertConstants
{
	//--------------------------------------------------------------------------
	// Job completion codes.
	//--------------------------------------------------------------------------

    /**
     * Marc conversion job completion status: success.
     */
    int CONVERT_JOB_SUCCESS = 0;

    /**
     * Marc conversion job completion status: warning.
     */
    int CONVERT_JOB_WARNING = 1;

    /**
     * Marc conversion job completion status: job failed.
     */
    int CONVERT_JOB_FAILURE = 2;


	//--------------------------------------------------------------------------
	// Record conversion status codes
	//--------------------------------------------------------------------------

    /**
     * Marc record conversion status: success.
     */
    int CONVERT_REC_SUCCESS = 0;

    /**
     * Marc record conversion status: record has errors.
     */
    int CONVERT_REC_ERROR   = 1;

    /**
     * Marc record conversion status: reject record.
     */
    int CONVERT_REC_REJECT  = 2;

    /**
     * Marc record conversion status: skip record.
     */
    int CONVERT_REC_SKIP    = 3;

    /**
     * Marc record conversion status: failure.
     */
    int CONVERT_REC_FAILURE = 4;

}
