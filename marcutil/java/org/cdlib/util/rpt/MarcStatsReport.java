package org.cdlib.util.rpt;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.Vector;

import org.apache.log4j.Logger;

import org.cdlib.util.cc.SimpleCommandLineParser;
import org.cdlib.util.marc.MarcEndOfFileException;
import org.cdlib.util.marc.MarcFormatException;
import org.cdlib.util.marc.MarcParmException;
import org.cdlib.util.marc.MarcRecord;
import org.cdlib.util.marc.MarcStream;
import org.cdlib.util.string.F;
import org.cdlib.util.string.StringUtil;

/**
 * MarcStatsReport.java
 *
 *
 *
 * @author <a href="mailto:shawnm@splorkin.com">Shawn McGovern</a>
 * @version $Id: MarcStatsReport.java,v 1.3 2002/09/30 01:36:38 smcgovrn Exp $
 */

public class MarcStatsReport
{
    private static Logger log = Logger.getLogger(MarcStatsReport.class);

    private static final char formFeed = 0x000C;

	private HashMap options = null;
	private HashMap config  = null;

    private MarcStream  in  = null;
    private PrintWriter out = null;

    private int pageLen = 50;
    private int lineLen = 129;
    private int pageCnt = 0;
    private int lineCnt = Integer.MAX_VALUE;

    private String tagHeaderLine = null;
	private String sfHeaderLine  = null;
	private String medHeaderLine = null;

	public static final String tagSection  = "Tag Table";
	public static final String[] tagSubheads = {"Tag  Freq by  Percent  Freq by",
                                                "     Records  Records  Occurence"};

	public static final String sfSection   = "Subfield Detail";
	public static final String[] sfSubheads  = {"Tag  Subfield  Freq by  Percent  Freq by",
                                                "     Code      Records  Records  Occurence"};

	public static final String medSection  = "Media Type Summary";
	public static final String[] medSubheads = {"Media Type               Total"};


	/**
	 * Create a new <code>MarcStatsReport</code> object. Reporting is controlled
	 * by invoking the public methods of this class.
	 */
    public MarcStatsReport ()
    {
		if ( log.isDebugEnabled() )
		{
			log.debug("Created new MarcStatsReport");
		}
    }


	/**
	 * Set the options <code>HashMap</code> for this object.
	 * @param hm the hashmap of options to use
	 */
	public void setOptions(HashMap hm)
	{
		options = hm;

		// Big debugging statement to print the options map
		if ( log.isDebugEnabled() )
		{
			if ( options == null )
			{
				log.debug("Option HashMap is null");
			}
			else
			{
				log.debug("Dumping Option Hashmap:");
				Set optEntries = options.entrySet();
				if ( optEntries.size() == 0)
				{
					log.debug("  option map is empty");
				}
				else
				{
					log.debug("  option map has " + optEntries.size() + " entries");
					Iterator iter = optEntries.iterator();
					while ( iter.hasNext() )
					{
						StringBuffer sb = new StringBuffer(100);
						Map.Entry entry = (Map.Entry)iter.next();
						String optName = (String) entry.getKey();
						String[] values = (String[]) entry.getValue();
						int valSize = (values == null ? 0 : values.length);

						sb.append("  Name =");
						if ( optName == null)
						{
							sb.append(" null ");
						}
						else
						{
							sb.append(" '");
							sb.append(optName);
							sb.append("' ");
						}

						for ( int k = 0; k < valSize; k++ )
						{
							sb.append(" Value[");
							sb.append(k);
							sb.append("] = '");
							sb.append(values[k]);
							sb.append("'");
						}
						log.debug(sb.toString());
					}
				}
			}
		} // end isDebugEnabled
	}


	/**
	 * Set the config <code>HashMap</code> for this object.
	 * @param hm the config hashmap to use
	 */
	public void setConfig(HashMap hm)
	{
		config = hm;
	}


	/**
	 * Open the input and output file specified in the config map.
	 * @return true if sucessful, false if there is an error
	 */
	public boolean openFiles()
	{
		if ( log.isDebugEnabled() )
		{
			log.debug("Attempting to open files");
		}

        boolean bRet = true;
        try
        {
			String inFileName  = (String)config.get("infile");
			String outFileName = (String)config.get("outfile");

			if ( log.isDebugEnabled() )
			{
				log.debug("opening input file: " + inFileName);
				log.debug("opening output file: " + outFileName);
			}

			in = new MarcStream(inFileName);
			out = new PrintWriter(new FileOutputStream(outFileName));
        }
        catch (IOException e)
        {
            log.error("Trapped IOException: " + e.getMessage(), e);
            bRet = false;
        }

        return bRet;
	}


	/**
	 * Close our files. Any exceptions are trapped and logged.
	 */
	public void closeFiles()
	{

		if ( log.isDebugEnabled() )
		{
			log.debug("Attempting to close files");
		}

        try
        {
			if ( in != null )
			{
				in.close();
			}

			if ( out != null )
			{
				out.close();
			}
        }
        catch (Exception e)
        {
            log.error("Trapped Exception: " + e.getMessage(), e);
        }
	}


	/**
	 * Produce the stats report. Statistics are gathered from the input file,
	 * accumulated into map strucutres, and finally printed to the output file
	 * when a MarcEndOfFile Exception is caught.
	 */
	public void run()
	{
        /**
         * record count
         */
		int recCnt = 0;

        /**
         * field count for all records
         */
		int fldCnt = 0;

        /**
         * Media counts map for all records.
         */
        TreeMap mediaMap  = new TreeMap();

        /**
         * Field occurences for a record, keyed by field tag.
         */
		TreeMap fieldMap  = new TreeMap();

        /**
         * Count the number records that have each field, keyed by field tag.
         */
		TreeMap entryMap  = new TreeMap();

        /**
         * Total field occurrences for all records, keyed by field tag.
         */
		TreeMap totalMap  = new TreeMap(); //

        /**
         * Subfield counts map for each record, keyed by subfield code.
         */
		TreeMap sfcMap    = new TreeMap(); //

        /**
         * Count the number records that have each subfield, keyed by subfield code.
         */
		TreeMap sfcEntMap = new TreeMap(); //

        /**
         * Subfield total occurrences for all records, keyed by subfield code.
         */
		TreeMap sfcTotMap = new TreeMap(); //

        /**
         * Summary map for subfields within a set of field tags.
         * Used to hold the results for a single record.
         */
		TreeMap sfMutliTagMap = new TreeMap();

        /**
         * Entry map for subfields within a set of field tags
         * Used to count the number record in which a subfield occurs.
         */
		TreeMap sfMutliTagEntMap = new TreeMap(); // tag x countMap

        /**
         * Total map for subfields within a set of field tags
         */
		TreeMap sfMutliTagTotMap = new TreeMap(); // tag x countMap

        /**
         * Summary map for subfields for all field tags.
         * Used to hold the results for a single record.
         */
		TreeMap sfSummaryMap = new TreeMap(); // tag x countMap

        /**
         * Entry map for subfields for all field tags
         * Used to count the number record in which a subfield occurs.
         */
		TreeMap sfSummaryEntMap = new TreeMap(); // tag x countMap

        /**
         * Total map for subfields for all field tags.
         */
		TreeMap sfSummaryTotMap = new TreeMap(); // tag x countMap

        /**
         * The list of field tags for which to gather subfield statistics
         */
		String[] tagList = null;

		try
		{
// 			if ( log.isDebugEnabled() )
// 			{
// 				log.debug("Dumping Maps at Start");
// 				dumpMap(fieldMap, "Field");
// 				dumpMap(entryMap, "Entry");
// 				dumpMap(totalMap, "Total");
// 				dumpMap(sfcMap, "SubField");
// 				dumpMap(sfcEntMap, "SFEntry");
// 				dumpMap(sfcTotMap, "SFTotal");
// 				dumpSummaryMap(sfMutliTagMap, "SF_Tags");
// 				dumpSummaryMap(sfMutliTagEntMap, "SF_Tags_Entry");
// 				dumpSummaryMap(sfMutliTagTotMap, "SF_Tags_Total");
// 				dumpSummaryMap(sfSummaryMap, "SF_by_Field");
// 				dumpSummaryMap(sfSummaryEntMap, "SF_by_Field_Entry");
// 				dumpSummaryMap(sfSummaryTotMap, "SF_by_Field_Total");
// 			} // end isDebugEnabled

            /*
             * Read through the file and allow the end of file exception to terminate
             * the loop.
             */
			while ( true )
			{
				try
				{
					MarcRecord rec = in.next();
					if ( rec == null )
					{
						continue; // continue with the next record if we had parse error
					}

					recCnt++;

					// get the number of fields in this record
					fldCnt += rec.getFieldCount();

					// get the media type for this record and add it to the media map
                    accumMap(mediaMap, rec.getMediaDesc());

					// get the field occurence counts for this record
					fieldMap = rec.getFieldCountMap();
					sumMaps(fieldMap, entryMap, totalMap);

					tagList = (String[])config.get("taglist");
					if ( tagList != null && tagList.length > 0)
					{
						if ( "all".equalsIgnoreCase(tagList[0]) )
						{
							sfSummaryMap = rec.getSubFieldCountMapByField();
							sumSummaryMaps(sfSummaryMap, sfSummaryEntMap, sfSummaryTotMap);
						}
						else
						{
							sfMutliTagMap = rec.getSubFieldCountMap(tagList);
							sumSummaryMaps(sfMutliTagMap, sfMutliTagEntMap, sfMutliTagTotMap);
						}
					}

// 					if ( log.isDebugEnabled() )
// 					{
// 						log.debug("Dumping Maps after read");
// 						dumpMap(mediaMap, "Media");
// 						dumpMap(fieldMap, "Field");
// 						dumpMap(entryMap, "Entry");
// 						dumpMap(totalMap, "Total");
// 						dumpMap(sfcMap, "SubField");
// 						dumpMap(sfcEntMap, "SFEntry");
// 						dumpMap(sfcTotMap, "SFTotal");
// 						dumpSummaryMap(sfMutliTagMap, "SF_Tags");
// 						dumpSummaryMap(sfMutliTagEntMap, "SF_Tags_Entry");
// 						dumpSummaryMap(sfMutliTagTotMap, "SF_Tags_Total");
// 						dumpSummaryMap(sfSummaryMap, "SF_by_Field");
// 						dumpSummaryMap(sfSummaryEntMap, "SF_by_Field_Entry");
// 						dumpSummaryMap(sfSummaryTotMap, "SF_by_Field_Total");
// 					} // end isDebugEnabled
				}
				catch (MarcFormatException e)
				{
					log.error("Invalid record format - record #" + (recCnt + 1));
				}
			}
		}
		catch (MarcEndOfFileException eof)
		{
			if ( log.isDebugEnabled() )
			{
				log.debug("Dumping Maps at EOF");
				dumpMap(mediaMap, "Media");
				dumpMap(fieldMap, "Field");
				dumpMap(entryMap, "Entry");
				dumpMap(totalMap, "Total");
				dumpMap(sfcMap, "SubField");
				dumpMap(sfcEntMap, "SFEntry");
				dumpMap(sfcTotMap, "SFTotal");
				dumpSummaryMap(sfMutliTagMap, "SF_Tags");
				dumpSummaryMap(sfMutliTagEntMap, "SF_Tags_Entry");
				dumpSummaryMap(sfMutliTagTotMap, "SF_Tags_Total");
				dumpSummaryMap(sfSummaryMap, "SF_by_Field");
				dumpSummaryMap(sfSummaryEntMap, "SF_by_Field_Entry");
				dumpSummaryMap(sfSummaryTotMap, "SF_by_Field_Total");
			} // end isDebugEnabled

            /*
             * Print the report
             */
			lineCnt = Integer.MAX_VALUE;
			printFieldStats(recCnt, totalMap, entryMap);

            printReportLine(null, tagHeaderLine, tagSubheads);
            printReportLine(null, tagHeaderLine, tagSubheads);
            printReportLine("Record count = " + recCnt, tagHeaderLine, tagSubheads);
            printReportLine("Field  count = " + fldCnt, tagHeaderLine, tagSubheads);

			lineCnt = Integer.MAX_VALUE;
			if ( tagList != null && tagList.length > 0 )
			{
				if ( "all".equalsIgnoreCase(tagList[0]) )
				{
					printSubfieldStats(recCnt, sfSummaryTotMap, sfSummaryEntMap);
				}
				else
				{
					printSubfieldStats(recCnt, sfMutliTagTotMap, sfMutliTagEntMap);
				}
			}

			lineCnt = Integer.MAX_VALUE;
			printMediaStats(mediaMap);
		} // end MarcEndOfFileException processing
		catch (Exception e)
		{
			log.error("Exception: " + e.getMessage(), e);
		}
	}


    /**
     * Increment an Integer value in a map using the suppied key.
     *
     * @param dataMap the map to accumulate
     * @param key the key to use
     *
     * @return true if sudessful, false if the map is null
     */
    public boolean accumMap(Map dataMap, Object key)
    {
		boolean bRet = true;

        if ( dataMap == null )
        {
			bRet = false;
            log.error("Data map is null - cannot use to accumulate");
        }
        else
        {
            Integer iVal = (Integer)dataMap.get(key);
            int accum = (iVal == null ? 1 : iVal.intValue() + 1);
            dataMap.put(key, new Integer(accum));
        }

		return bRet;
    }


    /**
     * Add the contents of a source map (String x Integer) to a total map
     * and increment entries in an entry map for each of the keys in the
     * source map.
     *
     * @param dataMap the source map
     * @param entryMap the entry map
     * @param totalMap the total map
     *
     * @return true if successful, false if any of the maps are null
     */
	public boolean sumMaps(Map dataMap, Map entryMap, Map totalMap)
	{
		boolean bRet = true;

		if ( dataMap == null )
		{
			bRet = false;
			log.error("One of the maps is null: dataMap");
		}

		if ( entryMap == null )
		{
			bRet = false;
			log.error("One of the maps is null: entryMap");
		}

		if ( totalMap == null )
		{
			bRet = false;
			log.error("One of the maps is null: totalMap");
		}

		if ( bRet )
		{
			Set dataEntries = dataMap.entrySet();
			Iterator iter = dataEntries.iterator();
			while ( iter.hasNext() )
			{
				Map.Entry dataEntry = (Map.Entry)iter.next();

				String dataKey = (String)(dataEntry.getKey());
				Integer dataCount = (Integer)(dataEntry.getValue());
				Integer entryCount = (Integer)(entryMap.get(dataKey));
				Integer totalCount = (Integer)(totalMap.get(dataKey));

				int dataCnt = (dataCount == null ? 0 : dataCount.intValue());
				int entryCnt = (entryCount == null ? 0 : entryCount.intValue());
				int totalCnt = (totalCount == null ? 0 : totalCount.intValue());

				entryCnt++;
				totalCnt += dataCnt;

				entryMap.put(dataKey, new Integer(entryCnt));
				totalMap.put(dataKey, new Integer(totalCnt));
			}
		}

		return bRet;
	}


    /**
     * Add the contents of a summary map (a map of maps) to a total map
     * and increment entries in an entry map for each of the keys in the
     * source map.
     *
     * @param dataSummaryMap the source map
     * @param entrySummaryMap the entry map
     * @param totalSummaryMap the total map
     *
     * @return true if successful, false if any of the maps are null
     */
	public boolean sumSummaryMaps(Map dataSummaryMap,
								  Map entrySummaryMap,
								  Map totalSummaryMap)
	{
		boolean bRet = true;

		if ( dataSummaryMap == null )
		{
			bRet = false;
			log.error("One of the maps is null: dataSummaryMap");
		}

		if ( entrySummaryMap == null )
		{
			bRet = false;
			log.error("One of the maps is null: entrySummaryMap");
		}

		if ( totalSummaryMap == null )
		{
			bRet = false;
			log.error("One of the maps is null: totalSummaryMap");
		}

		if ( bRet )
		{
			Set dataEntries = dataSummaryMap.entrySet();
			Iterator iter = dataEntries.iterator();
			while ( iter.hasNext() )
			{
				Map.Entry dataEntry = (Map.Entry)iter.next();
				if ( log.isDebugEnabled() )
				{
					log.debug("Summary Entry: key = " + dataEntry.getKey()
							  + " value = " + dataEntry.getValue());
				}

				String dataKey = (String)(dataEntry.getKey());
				TreeMap dataMap = (TreeMap)(dataEntry.getValue());
				if ( dataMap == null )
				{
					continue;
				}

				TreeMap entryMap = (TreeMap)(entrySummaryMap.get(dataKey));
				if ( entryMap == null )
				{
					entryMap = new TreeMap();
				}

				TreeMap totalMap = (TreeMap)(totalSummaryMap.get(dataKey));
				if ( totalMap == null )
				{
					totalMap = new TreeMap();
				}

				sumMaps(dataMap, entryMap, totalMap);

				entrySummaryMap.put(dataKey, entryMap);
				totalSummaryMap.put(dataKey, totalMap);
			}
		}

		return bRet;
	}


    /**
     * A private method used during debugging to dump a summary map
     * to the output stream.
     * The type strucure of a summary map is String x Map.
     *
     * @param map the map to print
     * @param name a name for the map
     */
	private void printSummaryMap(Map map, String name)
	{
		if ( log.isDebugEnabled() )
		{
			log.debug("Printing '" + name + "' Summary Map");
		}

		if ( map == null )
		{
			log.error("Summary Map '" + name + "' is null");
		}
		else
		{
			out.println("Printing " + name + " Map:");
			Set mapEntries = map.entrySet();
			if ( mapEntries.size() == 0)
			{
				out.println("Printing " + name + " Map:");
			}
			else
			{
				out.println("  " + name + " map has " + mapEntries.size() + " entries");
				Iterator iter = mapEntries.iterator();
				while ( iter.hasNext() )
				{
					Map.Entry entry = (Map.Entry)iter.next();
					String key = (String) entry.getKey();
					Map value = (Map) entry.getValue();
					printMap(value, name + " - " + key);
				}
			}
		}
	}


    /**
     * A private method used during debugging to dump a regular map
     * to the output stream.
     * The type strucure of a regular map is String x Integer.
     *
     * @param map the map to print
     * @param name a name for the map
     */
	private void printMap(Map map, String name)
	{
		if ( log.isDebugEnabled() )
		{
			log.debug("Printing '" + name + "' Map");
		}

		if ( map == null )
		{
			log.error("Map " + name + " is null");
		}
		else
		{
			out.println("Printing " + name + " Map:");

			Set mapEntries = map.entrySet();
			if ( mapEntries.size() == 0)
			{
				out.println("  " + name + " map has " + mapEntries.size() + " entries");
			}
			else
			{
				out.println("  " + name + " map has " + mapEntries.size() + " entries");
				Iterator iter = mapEntries.iterator();
				while ( iter.hasNext() )
				{
					StringBuffer sb = new StringBuffer(100);
					Map.Entry entry = (Map.Entry)iter.next();
					String key = (String) entry.getKey();
					Integer value = (Integer) entry.getValue();

					sb.append("  Tag =");
					if ( key == null)
					{
						sb.append(" null ");
					}
					else
					{
						sb.append(" '");
						sb.append(key);
						sb.append("' ");
						sb.append(" Count = ");
						sb.append(value);
					}
					out.println(sb.toString());
				}
			}
		}
	}


    /**
     * A private method used during debugging to dump the a summary map
     * to the log.
     * The type strucure of a summary map is String x Map.
     *
     * @param map the map to print
     * @param name a name for the map
     */
	private void dumpSummaryMap(Map map, String name)
	{
		if ( log.isDebugEnabled() )
		{
			if ( map == null )
			{
				log.debug("Map " + name + " is null");
			}
			else
			{
				log.debug("Dumping " + name + " Map:");
				Set mapEntries = map.entrySet();
				if ( mapEntries.size() == 0)
				{
					log.debug("  " + name + " map is empty");
				}
				else
				{
					log.debug("  " + name + " map has " + mapEntries.size() + " entries");
					Iterator iter = mapEntries.iterator();
					while ( iter.hasNext() )
					{
						Map.Entry entry = (Map.Entry)iter.next();
						String key = (String) entry.getKey();
						Map value = (Map) entry.getValue();
						dumpMap(value, name + " - " + key);
					}
				}
			}
		} // end isDebugEnabled
	}


    /**
     * A private method used during debugging to dump a regular map
     * to the log.
     * The type strucure of a regular map is String x Integer.
     *
     * @param map the map to print
     * @param name a name for the map
     */
	private void dumpMap(Map map, String name)
	{
		if ( log.isDebugEnabled() )
		{
			if ( map == null )
			{
				log.debug("Map " + name + " is null");
			}
			else
			{
				log.debug("Dumping " + name + " Map:");
				Set mapEntries = map.entrySet();
				if ( mapEntries.size() == 0)
				{
					log.debug("  " + name + " map is empty");
				}
				else
				{
					log.debug("  " + name + " map has " + mapEntries.size() + " entries");
					Iterator iter = mapEntries.iterator();
					while ( iter.hasNext() )
					{
						StringBuffer sb = new StringBuffer(100);
						Map.Entry entry = (Map.Entry)iter.next();
						String key = (String) entry.getKey();
						Integer value = (Integer) entry.getValue();

						sb.append("  Tag =");
						if ( key == null)
						{
							sb.append(" null ");
						}
						else
						{
							sb.append(" '");
							sb.append(key);
							sb.append("' ");
							sb.append(" Count = ");
							sb.append(value);
						}
						log.debug(sb.toString());
					}
				}
			}
		} // end isDebugEnabled
	}


	/**
	 * Print the field statistics section of the report.
	 */
	public void printFieldStats(int recCnt, Map totalMap, Map entryMap )
	{
		if ( log.isDebugEnabled() )
		{
			log.debug("Printing Tag Data Section");
		}

		if ( totalMap == null )
		{
			log.error("Map totalMap is null");
		}
		else if ( entryMap == null )
		{
			log.error("Map entryMap is null");
		}
		else
		{
			tagHeaderLine = setHeaderLine(tagSection);

			Set mapEntries = totalMap.entrySet();
			if ( mapEntries.size() == 0)
			{
                printReportLine("No fields found to print", tagHeaderLine, tagSubheads);
			}
			else
			{
				if ( log.isDebugEnabled() )
				{
					log.debug("  Tag Data Section has " + mapEntries.size() + " entries");
				}
				Iterator iter = mapEntries.iterator();
				while ( iter.hasNext() )
				{
					StringBuffer sb = new StringBuffer(100);
					Map.Entry entry = (Map.Entry)iter.next();
					String tag = (String) entry.getKey();
					Integer tagTotInt = (Integer) entry.getValue();
					Integer tagRecInt = (Integer) entryMap.get(tag);
					int totOccur = (tagTotInt == null ? 0 : tagTotInt.intValue());
					int recOccur = (tagRecInt == null ? 0 : tagRecInt.intValue());
					printFieldLine(tag, recCnt, totOccur, recOccur);
				}
			}
		}

	}


	/**
	 * Print a line from the field detail section of the report.
	 *
	 * @param sfCode the subfeild code
	 * @param recCnt the number of records in the file
	 * @param totOccur the total number of occurrences of this field
	 * @param recOccur the number of records containing this field
	 */
	public void printFieldLine(String tag, int recCnt, int totOccur, int recOccur)
	{
		if ( recCnt == 0 )
		{
			log.error("Record Count is zero");
		}
		else
		{
			int recPercent = 100 * recOccur / recCnt;
			StringBuffer sb = new StringBuffer(100);
			sb.append(tag);
			sb.append("  ");
			sb.append(F.f(recOccur, 8));
			sb.append("    ");
			sb.append(F.f(recPercent, 3));
			sb.append("%   ");
			sb.append(F.f(totOccur, 8));
            printReportLine(sb.toString(), tagHeaderLine, tagSubheads);
		}
	}


	/**
	 * Print the subfield detail section of the report.
	 *
	 * @param recCnt the number of records in the file
	 * @param totalMap the total number of occurrences of this subfield
	 * @param recMap the number of records containing this subfield
	 */
	public void printSubfieldStats(int recCnt, Map totalMap, Map recMap )
	{
		if ( log.isDebugEnabled() )
		{
			log.debug("Printing Tag Data Section");
		}

		if ( totalMap == null )
		{
			log.error("Map totalMap is null");
		}
		else if ( recMap == null )
		{
			log.error("Map recMap is null");
		}
		else
		{
			sfHeaderLine = setHeaderLine(sfSection);

			Set sfmapEntries = totalMap.entrySet();
			if ( sfmapEntries.size() == 0)
			{
                printReportLine("No subfield detail found to print", sfHeaderLine, sfSubheads);
			}
			else
			{
				if ( log.isDebugEnabled() )
				{
					log.debug("  Subfield Data Section has " + sfmapEntries.size() + " entries");
				}
				Iterator sfIter = sfmapEntries.iterator();
				while ( sfIter.hasNext() )
				{
					Map.Entry sfEntry = (Map.Entry)sfIter.next();
					String tag = (String) sfEntry.getKey();
					Map value = (Map) sfEntry.getValue();
					Map entryMap = (Map) recMap.get(tag);

                    printReportLine(tag, sfHeaderLine, sfSubheads);

					Set dtlEntries = value.entrySet();

					if ( dtlEntries.size() == 0)
					{
                        printReportLine("No subfields found to print", sfHeaderLine, sfSubheads);
					}
					else
					{
						if ( log.isDebugEnabled() )
						{
							log.debug("  Subfield Detail Section has " + dtlEntries.size()
									  + " entries");
						}
						Iterator dtlIter = dtlEntries.iterator();
						while ( dtlIter.hasNext() )
						{
							StringBuffer sb = new StringBuffer(100);
							Map.Entry entry = (Map.Entry)dtlIter.next();
							String sfCode = (String) entry.getKey();
							Integer tagTotInt = (Integer) entry.getValue();
							Integer tagRecInt = (Integer) entryMap.get(sfCode);
							int totOccur = (tagTotInt == null ? 0 : tagTotInt.intValue());
							int recOccur = (tagRecInt == null ? 0 : tagRecInt.intValue());
							printSubfieldLine(sfCode, recCnt, totOccur, recOccur);
						}
					}
				}
			}
		}
	}


	/**
	 * Print a line from the subfield detail section of the report.
	 *
	 * @param sfCode the subfeild code
	 * @param recCnt the number of records in the file
	 * @param totOccur the total number of occurrences of this subfield
	 * @param recOccur the number of records containing this subfield
	 */
 	public void printSubfieldLine(String sfCode, int recCnt, int totOccur, int recOccur)
	{
		if ( recCnt == 0 )
		{
			log.error("Record Count is zero");
		}
		else
		{
			int recPercent = 100 * recOccur / recCnt;
			StringBuffer sb = new StringBuffer(100);
			sb.append("        ");
			sb.append(sfCode);
			sb.append("     ");
			sb.append(F.f(recOccur, 8));
			sb.append("     ");
			sb.append(F.f(recPercent, 3));
			sb.append("%   ");
			sb.append(F.f(totOccur, 8));
            printReportLine(sb.toString(), sfHeaderLine, sfSubheads);
		}
	}


	/**
	 * Print the media statistics section of the report.
	 *
	 * @param mediaMap the map containing the media type statistics
	 */
	public void printMediaStats(Map mediaMap)
	{
		if ( log.isDebugEnabled() )
		{
			log.debug("Printing Media Summary Section");
		}

		if ( mediaMap == null )
		{
			log.error("Map mediaMap is null");
		}
		else
		{
			medHeaderLine = setHeaderLine(medSection);

			Set mapEntries = mediaMap.entrySet();
			if ( mapEntries.size() == 0)
			{
				printHeaders(medHeaderLine, medSubheads);
                out.println();
				out.println("No media summary data found to print");
			}
			else
			{
				if ( log.isDebugEnabled() )
				{
					log.debug("  Media Summary Section has " + mapEntries.size() + " entries");
				}
				Iterator iter = mapEntries.iterator();
				while ( iter.hasNext() )
				{
					StringBuffer sb = new StringBuffer(100);
					Map.Entry entry = (Map.Entry)iter.next();
					String mediaDesc = (String) entry.getKey();
					Integer recOccInt = (Integer) entry.getValue();
					int recOccur = (recOccInt == null ? 0 : recOccInt.intValue());
					printMediaLine(mediaDesc, recOccur);
				}
			}
		}
	}


	/**
	 * Print a line from the media statictics section of the report.
	 *
	 * @param mediaDesc the media description tag
	 * @param  recOccur the number records repesenting this media
	 */
	public void printMediaLine(String mediaDesc, int recOccur)
	{
        StringBuffer sb = new StringBuffer(100);
        sb.append(F.f(mediaDesc, 20, F.LJ));
        sb.append("  ");
        sb.append(F.f(recOccur, 8));
        printReportLine(sb.toString(), medHeaderLine, medSubheads);
	}


	/**
	 * Pring the supplied report line, and any header lines reqired if we are at
	 * a page break.
	 *
	 * @param line the report line to print
	 * @param headerLine the heading line
	 * @param subHeaderLines the subheading lines
	 */
	public void printReportLine(String line, String headerLine, String[] subHeaderLines)
	{
        if ( lineCnt > pageLen )
        {
            printHeaders(headerLine, subHeaderLines);
        }

        if ( line != null )
        {
            out.println(line);
        }
        else
        {
            out.println();
        }

        lineCnt++;
	}


	/**
	 * Print the supplied heading and subheading lines.
	 *
	 * @param headerLine the heading line
	 * @param subHeaderLines the subheading lines
	 */
    public void printHeaders(String headerLine, String[] subHeaderLines)
    {
        pageCnt++;
		lineCnt = 0;
		out.println(formFeed);

        if ( headerLine != null)
        {
            out.println(headerLine + F.f(pageCnt, 5, F.RJ));
            out.println();
            lineCnt += 2;
        }

        if ( subHeaderLines != null )
        {
            int shmax = subHeaderLines.length;
            if ( shmax > 0 )
            {
                for ( int i = 0; i < shmax; i++ )
                {
                    out.println(subHeaderLines[i]);
                    lineCnt++;
                }
                out.println();
                lineCnt++;
            }
        }
    }


	/**
	 * Create a header line consisting of the file name, on the left, the supllied
	 * section name, in the middle, and the the date and time, and page, on the right.
	 *
	 * @param section the report section name
	 * @return the header line
	 */
    public String setHeaderLine(String section)
    {
        String fileNameHeader = "File: " + (String)config.get("infile");
        String dateHeader     = "Date: " + getPrintDateTime();
        String pageHeader     = "  Page: ";

        return(buildHeaderLine(fileNameHeader, section, dateHeader + pageHeader, lineLen - 5));
    }


	/**
	 * Builds a header line by aligning three <code>Strings</code> within a given
	 * line legnth. There is a left aligned segment, a center aligned segment,
	 * and a right aligned segment, any of which mat be <code>null</code>.
	 * if the length of the supplied segments exceed the given line lengh a line
	 * larger than the given lengh is returned. The segment are always separated
	 * by at least one space. This means that in the pathological case where all
	 * supplied segments are empty a line of two spaces is returned.
	 *
	 * @param left the left aligned segment
	 * @param center the center aligned segment
	 * @param right the right aligned segment
	 * @return the header line
	 */
    public String buildHeaderLine(String left, String center, String right, int len)
    {
        int lLen = (left == null ? 0 : left.length());
        int cLen = (center == null ? 0 : center.length());
        int rLen = (right == null ? 0 : right.length());
        int totLen = lLen + cLen + rLen;
        int minLen = totLen + 2;
        int sbSize = Math.max(len, minLen);

        if ( minLen > len )
        {
            log.warn("Minimum heading size (" + minLen + ") exceeds line length (" + len + ")");
        }

        int diff = sbSize - totLen;
        int lGap = (diff >>> 1);
        int rGap = diff - lGap;

        StringBuffer sb = new StringBuffer(len);

		if ( left != null )
		{
			sb.append(left);
		}

        for ( int i = 0; i < lGap; i++ )
        {
            sb.append(' ');
        }

		if ( left != null )
		{
			sb.append(center);
		}

        for ( int i = 0; i < rGap; i++ )
        {
            sb.append(' ');
        }

		if ( left != null )
		{
			sb.append(right);
		}

        return sb.toString();
    }


    /**
     * Get current date and time string formatted for printing.
     *
     * @return formatted date time string
     */
    private String getPrintDateTime()
    {
        StringBuffer sb       = new StringBuffer(30);
        Calendar     calendar = new GregorianCalendar();

        int year   = calendar.get(Calendar.YEAR);
        int month  = calendar.get(Calendar.MONTH) + 1;
        int day    = calendar.get(Calendar.DAY_OF_MONTH);
        int hour   = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        int second = calendar.get(Calendar.SECOND);

        sb.append(year).append("-");
        sb.append(month).append("-");
        sb.append(day).append(" ");
        sb.append(hour).append(":");
        sb.append(minute).append(":");
        sb.append(second);

        return sb.toString();
    }


    /**
     * Get configuration properties from configuration file given on the command
     * line option as --config <filename>
     *
     * Configuration file is a set of name value pairs.
     *
     * Required entries:
     *
     *   infile      - The marc data file to convert
     *   outfile     - The converted marc data file
     *
     * Optional entries:
     *
     *   skip        - The number of input records to skip before conversion starts.
     *                 Default's to 0, if the entry is not specified or is not numeric.
     *                 Keyword values 'none' and 'all' may be specified.
     *
     *   maxin       - The maximum number of records to read from the file. This includes
     *                 the number of records to skip, so if skip is greater than or equal
     *                 maxin no records will be processed.
     *                 Default's to 'all', if the entry is not specified or is not numeric.
     *                 Keyword values 'none' and 'all' may be specified.
     *
     *   maxout      - The maximum number of records to convert. Rejected records are not
     *                 included in this count.
     *                 Default's to 'all', if the entry is not specified or is not numeric.
     *                 Keyword values 'none' and 'all' may be specified.
     *
     */
    private HashMap processConfiguration()
		throws MarcParmException
    {
        Properties pin         = new Properties();
        HashMap    config      = new HashMap();
        String     test        = null;
        String[]   confparams  = null;
        String     configfile  = null;
        String     inFileName  = null;
        String     outFileName = null;
		String     tagListStr  = null;
		String[]   tagList     = null;
        int        skip        = 0;
        int        maxIn       = 0;
        int        maxOut      = 0;

        /*
         * Get the config file
         */
        if ( (confparams = (String[])options.get("config")) != null)
        {
            configfile = confparams[0];
        }

        if ( configfile == null || configfile.length() == 0 )
        {
            throw new MarcParmException(this, "--config <filename> required on java command");
        }

        try
        {
            pin.load(new FileInputStream(configfile));
        }
        catch (Exception exception)
        {
            throw new MarcParmException(this, "configuration error: " + exception);
        }
        config.put("configfile", configfile);

        /*
         * Get input file name
         */
        inFileName = pin.getProperty("infile", "").trim();
        if ( inFileName.length() == 0 )
        {
            throw new MarcParmException(this, "infile - input file name not supplied");
        }
        config.put("infile", inFileName);

        /*
         * Get output file name
         */
        outFileName = pin.getProperty("outfile", "").trim();
        if ( outFileName.length() == 0 )
        {
            throw new MarcParmException(this, "outfile - output file name not supplied");
        }
        config.put("outfile", outFileName);

        /*
         * Get list of fields to detail
         */
        tagListStr = pin.getProperty("taglist", "").trim();
        if ( tagListStr.length() == 0 )
        {
            tagList = new String[0];
        }
		else
		{
			Vector vtmp = new Vector(20, 20);
			int idx = 0;

			StringTokenizer stokens = new StringTokenizer(tagListStr);
			while ( stokens.hasMoreTokens())
			{
				vtmp.add(stokens.nextToken());
			}

			tagList = (String[])vtmp.toArray(new String[vtmp.size()]);
		}

        config.put("taglist", tagList);
        config.put("tagliststr", tagListStr);

        /*
         * Get the number of records to skip over
         */
        test = pin.getProperty("skip", "none").trim();
        if (test.equalsIgnoreCase("none"))
        {
            skip = 0;
        }
        else if (test.equalsIgnoreCase("all"))
        {
            skip = Integer.MAX_VALUE;
        }
        else
        {
            try
            {
                skip = StringUtil.parseInt(test);
            }
            catch (NumberFormatException e)
            {
                skip = 0;
            }
        }

        if ( skip < 0 )
        {
            skip = 0;
        }

        config.put("skip", new Integer(skip));

        /*
         * Get maximum number of input records to process.
         */
        test = pin.getProperty("maxin", "all").trim();
        if (test.equalsIgnoreCase("none"))
        {
            maxIn = 0;
        }
        else if (test.equalsIgnoreCase("all"))
        {
            maxIn = Integer.MAX_VALUE;
        }
        else
        {
            try
            {
                maxIn = StringUtil.parseInt(test);
            }
            catch (NumberFormatException e)
            {
                maxIn = 0;
            }
        }

        if ( maxIn < 0 )
        {
            maxIn = 0;
        }

        config.put("maxin", new Integer(maxIn));

        /*
         * Get maximum number of records to create
         */
        test = pin.getProperty("maxout", "all").trim();
        if (test.equalsIgnoreCase("none"))
        {
            maxOut = 0;
        }
        else if (test.equalsIgnoreCase("all"))
        {
            maxOut = Integer.MAX_VALUE;
        }
        else
        {
            try
            {
                maxOut = StringUtil.parseInt(test);
            }
            catch (NumberFormatException e)
            {
                maxOut = 0;
            }
        }

        if ( maxOut < 0 )
        {
            maxOut = 0;
        }

        config.put("maxout", new Integer(maxOut));

        if ( log.isDebugEnabled() )
        {
			log.debug(" configfile:" + configfile);
			log.debug("  infile:" + inFileName);
			log.debug("  outfile:" + outFileName);
			log.debug("  skip:" + skip);
			log.debug("  maxin:" + maxIn);
			log.debug("  maxout:" + maxOut);
			log.debug("  tagliststr:" + tagListStr);

			String tags = null;
			if ( tagList == null )
			{
				tags = "null";
			}
			else
			{
				StringBuffer sb = new StringBuffer(20);
				int tmax = tagList.length;
				for ( int ti = 0; ti < tmax; ti++)
				{
					sb.append("[");
					sb.append(ti);
					sb.append("]=");
					if ( tagList[ti] == null )
					{
						sb.append("null ");
					}
					else
					{
						sb.append("'");
						sb.append(tagList[ti]);
						sb.append("' ");
					}
				}
				tags = sb.toString();
			}
			log.debug("  taglist:" + tags);
		}

        return config;
    }


    /**
     * Report configuration settings
     */
    private void reportConfiguration()
    {
		if ( log.isDebugEnabled() )
		{
			log.debug("Reporting configuration");
		}

        out.println(formFeed);
        out.println("Configuration:");
        out.println("  infile:      " + config.get("infile"));
        out.println("  outfile:     " + config.get("outfile"));
        out.println("  skip:        " + config.get("skip"));
        out.println("  maxin:       " + config.get("maxin"));
        out.println("  maxout:      " + config.get("maxout"));
		out.println("  tagliststr:  " + config.get("tagliststr"));

		String[] tagList = (String[])config.get("taglist");
		String tags = null;
		if ( tagList == null )
		{
			tags = "null";
		}
		else
		{
			StringBuffer sb = new StringBuffer(20);
			int tmax = tagList.length;
			for ( int ti = 0; ti < tmax; ti++)
			{
				sb.append("[");
				sb.append(ti);
				sb.append("]=");
				if ( tagList[ti] == null )
				{
					sb.append("null ");
				}
				else
				{
					sb.append("'");
					sb.append(tagList[ti]);
					sb.append("' ");
				}
			}
			tags = sb.toString();
		}
		out.println("  taglist:     " + tags);
    }


    /*
     * This main() method instantiates a new object, calls the setupt methods,
	 * and the produces the report with the run method.
	 * Extensive debug logic exists to unit test the class, but is conditionalized
	 * out when debugging is not turned on.
     */
    public static void main(String[] args)
    {
		if ( log.isDebugEnabled() )
		{
			log.debug("Started MarcStatsReport: arg count = " + args.length);
		}

		MarcStatsReport rpt = new MarcStatsReport();
		HashMap opts = SimpleCommandLineParser.parseArgs(args);

		if ( log.isDebugEnabled() )
		{
			if ( opts == null )
			{
				log.debug("Option HashMap is null");
			}
			else
			{
				log.debug("Dumping Option Hashmap:");
				Set optEntries = opts.entrySet();
				if ( optEntries.size() == 0)
				{
					log.debug("  option map is empty");
				}
				else
				{
					log.debug("  option map has " + optEntries.size() + " entries");
					Iterator iter = optEntries.iterator();
					while ( iter.hasNext() )
					{
						StringBuffer sb = new StringBuffer(100);
						Map.Entry entry = (Map.Entry)iter.next();
						String optName = (String) entry.getKey();
						String[] values = (String[]) entry.getValue();
						int valSize = (values == null ? 0 : values.length);

						sb.append("  Name =");
						if ( optName == null)
						{
							sb.append(" null ");
						}
						else
						{
							sb.append(" '");
							sb.append(optName);
							sb.append("' ");
						}

						for ( int k = 0; k < valSize; k++ )
						{
							sb.append(" Value[");
							sb.append(k);
							sb.append("] = '");
							sb.append(values[k]);
							sb.append("'");
						}
						log.debug(sb.toString());
					}
				}
			}
		} // end isDebugEnabled

		rpt.setOptions(opts);
		HashMap config = rpt.processConfiguration();
		rpt.setConfig(config);
		boolean filesOpen = rpt.openFiles();

		if ( log.isDebugEnabled() )
		{
			log.debug((filesOpen ? "Files succesfully opened" : "Failed to open files"));
		}

		rpt.run();
		rpt.reportConfiguration();
		rpt.closeFiles();
    }
	// end of main
}
// MarcStatsReport class
