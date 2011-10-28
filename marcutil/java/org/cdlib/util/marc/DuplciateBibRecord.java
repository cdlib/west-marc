/**
 * 
 */
package org.cdlib.util.marc;

import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;
import org.cdlib.util.marc.MarcRecord;

/**
 * @author pdoshi
 * 
 */
public class DuplciateBibRecord {
	
	static Set<String> duplicateBib = new HashSet<String>();

	private static Logger log = Logger.getLogger(DuplciateBibRecord.class);

	public static boolean isBib(MarcRecord marcIn) {
		String bibId;
		boolean isBib = false;
		bibId = marcIn.getLeaderValue();

		if (!(bibId.charAt(6) == 'y' || bibId.charAt(6) == 'u'
				|| bibId.charAt(6) == 'v' || bibId.charAt(6) == 'x')) {

			isBib = true;
		}

		return isBib;

	}

	public static boolean checkDuplicateBibRecord(String id) {
	
		boolean isDuplicate = false;
		if (!duplicateBib.add(id)) {
			isDuplicate = true;
			log.info("Record Rejected - Duplicate Bib record - " +"Bib record No -"+id);

		}
		return isDuplicate;

	}
}
