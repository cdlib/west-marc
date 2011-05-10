package org.cdlib.marcconvert;

import java.util.Enumeration;
import java.util.Vector;

import org.apache.log4j.Logger;

import org.cdlib.util.marc.MarcException;
import org.cdlib.util.marc.MarcFieldList;
import org.cdlib.util.marc.MarcRecord;
import org.cdlib.util.marc.MarcSubfield;
import org.cdlib.util.marc.MarcVblLengthField;
import org.cdlib.util.marc.MarcFixedLengthField;
import org.cdlib.util.string.StringUtil;
import java.util.Hashtable;
import java.util.Properties;

/**
 * Create LHR records for UC campuses.
 *
 * author : Randy Lai
 * @version $Id: LHRConvert.java,v 1.4 2010/11/05 19:22:13 aleph16 Exp $
 */
public class LHRConvert extends MarcConvertLHR
{
	/**
	 * log4j Logger for this class.
	 */
    private static Logger log = Logger.getLogger(LHRConvert.class);

	/**
	 * CVS header string.
	 */
    public static final String cvsHeader = "$Header: /cvs/root/melvyl/ei/marcconvert/java/org/cdlib/marcconvert/LHRConvert.java,v 1.4 2010/11/05 19:22:13 aleph16 Exp $";

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


    private Hashtable hashOutMarc = new Hashtable();
    private Properties config = null;
    private MarcVblLengthField field852 = null;
    private MarcFixedLengthField field003 = null;

    /**
     * Instanstiate a new LHRConvert object.
     */
    public LHRConvert()
    {
        super();
    }


    /**
     * Process the given marc record. Place the result into the given output
     * marc record parameter variable. The status of the process is returned.
     *
     * @param inRec the <code>MarcRecord</code> to process
     * @param outRec the processed <code>MarcRecord</code>
     * @return the process status code
     * @throws MarcDropException whenever a record is rejected
     * @see org.cdlib.marcconvert.ConvertConstants
     */
 
/**
    public int convert(MarcRecord inRec, MarcRecord outRec)
        throws MarcDropException
    {
        if ( inRec == null )
        {
            throw new MarcException(this, "Parser received null input MarcRecord");
        }

        if ( outRec == null )
        {
            throw new MarcException(this, "Parser received null output MarcRecord");
        }

        inMarc = inRec;
        outMarc = outRec;
        reset();
        return convert();
    }

  */


    public int convertLHR(MarcRecord inRec, MarcRecord outRec, Hashtable hashTable, Properties conf )
        throws MarcDropException
    {
        if ( inRec == null )
        {
            throw new MarcException(this, "Parser received null input MarcRecord");
        }

        if ( outRec == null )
        {
            throw new MarcException(this, "Parser received null output MarcRecord");
        }

        inMarc = inRec;
        outMarc = outRec;
        reset();
        hashOutMarc = hashTable;
        config = conf;
        return convert();
    }


    /**
     * Parse the current marc record. Place the result into the output
     * marc record class variable. The status of the parse is returned.
     * @return the status code
     * @throws MarcDropException whenever a record is rejected
     * @see org.cdlib.marcconvert.ConvertConstants
     */
    private int convert()
        throws MarcDropException
    {
        int rc = CONVERT_REC_SUCCESS;

        // Set default debug level
        setDebug(0);

        // Set status for this record
        setStatus(OK);

        // Move all fields to output record
        moveOut(0,999);

        // Remove 920 fields from outMarc
        outMarc.deleteFields("920");
        // Remove 852 fields from outMarc
        outMarc.deleteFields("852");
        // Remove 856 fields from outMarc
        outMarc.deleteFields("856");
        // Remove 003 field from outMarc
        outMarc.deleteFields("003");

        // Create LHR records for campuses
        createLHR();

        debugOut(2,"process output leader:" + outMarc.getLeaderValue());

        return rc;
    }

    /**
     * 
     */
    private void createLHR()
    {
        // reject bad records - no field 003 or 852
        MarcFieldList fList003 = inMarc.allFields("003");
        MarcFieldList fList852 = inMarc.allFields("852");
        if ((fList003.size() == 0) || (fList852.size() == 0))
        {
            throw new MarcDropException(this, "Parser found no 003 or 852 fields in input MarcRecord");
        }
        // reject bad records - no field 856
        MarcFieldList fList856 = inMarc.allFields("856");
        if (fList856.size() == 0)
        {
            throw new MarcDropException(this, "Parser found no 856 fields in input MarcRecord");
        }

        // Find all 920 fields
        MarcFieldList field920s = inMarc.allFields("920");
        if (field920s.size() == 0)
        {
            throw new MarcDropException(this, "Parser found no 920 fields in input MarcRecord");
        }

        while (field920s.hasMoreElements()) 
        {
            // copy contents from outMarc to tempMarc, keep original leader values
            MarcRecord tempMarc = new MarcRecord();
            String leader = inMarc.getLeaderValue();
            tempMarc.setLeaderValue(leader);

            MarcFieldList fields = outMarc.allFields();
            tempMarc.setFields(fields, MarcRecord.END_LIST);

            MarcVblLengthField field920 = (MarcVblLengthField) field920s.nextElement();
            String sfv920a = field920.firstSubfieldValue("a");

            // create output 003 and 852 field with $a and $b
            field852 = tempMarc.getNewVblField("852", "  ");
            field003 = null;
 
            String[] table = getCampusTable(sfv920a);
            // reject if campus of 920 $a found no match with those on configuration table
            if (table.length == 0)
            {
                throw new MarcDropException(this, "Parser found invalid 920 $a in input MarcRecord:" + sfv920a);
            }
            createFields(table);
            tempMarc.setField(field003, MarcRecord.TAG_ORDER);

            // Create output 856-fields 
            // Add it to output:
            //     if it contains this phrase "freely available" - case insensitive
            //     if it contains this phrase "Restricted to UC campuses" - case insensitive
            //     if it contains "Restricted to " + UC-campus-code - case insensitive
            //     if it contains no restriction words as mentioned above(open resources)
 
            MarcFieldList field856s = inMarc.allFields("856");
            while (field856s.hasMoreElements())
            {
                MarcVblLengthField field856In = (MarcVblLengthField) field856s.nextElement();

                // Any 856 should contain subfield u, or drop it.
                if (field856In.firstSubfield("u") == null)
                {
                    throw new MarcDropException(this, "Parser found no 856 $u in input MarcRecord");
                }

                // Any 856 should contain subfield z, or drop it.
                String sz = field856In.firstSubfieldValue("z");
 
                // No subfield z - under no restriction, is considered an open resource.
                if ( sz != null)
                {
                    String szLowerCase = field856In.toString().toLowerCase();
                    // If 856 subfield z contains this phrase 'freely available', include this 856 field.
                    if ( szLowerCase.indexOf("freely available") == -1 ) 
                    {
                        if ( szLowerCase.indexOf("restricted to uc campuses") == -1 )  
                        {
                            int idx = szLowerCase.indexOf("restricted to ");
                            if ( idx >= 0 )
                            {
                                // The "Restricted to " must be followed with the value of subfield a of field 920 being used.
                                int idxCampus = szLowerCase.indexOf(sfv920a.toLowerCase(), idx);
                                if ( idxCampus == -1 )
                                {
                                   // This input 856 field won't be included
                                   continue;  
                                }
                            }
                        } 
                    }
                }
                tempMarc.addField(field856In);
            }    // while (field856s.hasMoreElements())

            hashOutMarc.put (sfv920a, tempMarc);
        }     //while (field920s.hasMoreElements())
    }
 

    private String[] getCampusTable(String campus)
    {
        String st = config.getProperty(campus.toLowerCase());
        if (st == null)
        {
            throw new MarcDropException(this, "Parser found invalid 920 $a in input MarcRecord:" + campus);
        }

        return st.split(",");
    }

    private void createFields(String[] value)
    {
        String symbol = value[0];
        String location = value[1];
        field003 = new MarcFixedLengthField("003", symbol);
        field852.addSubfield("a", symbol);
        field852.addSubfield("b", location);
    }
}
