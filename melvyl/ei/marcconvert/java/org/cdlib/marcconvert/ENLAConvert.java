package org.cdlib.marcconvert;

import java.util.Vector;

import org.apache.log4j.Logger;

import org.cdlib.util.marc.Field;
import org.cdlib.util.marc.MarcException;
import org.cdlib.util.marc.MarcFieldList;
import org.cdlib.util.marc.MarcFixedLengthField;
import org.cdlib.util.marc.MarcRecord;
import org.cdlib.util.marc.MarcSubfield;
import org.cdlib.util.marc.MarcVblLengthField;
import org.cdlib.util.string.StringUtil;

/**
 * Convert the marc records from the ENLA source.
 *
 * @author <a href="mailto:jgarey@library.berkeley.edu">Janet Garey</a>
 * @author <a href="mailto:shawnm@splorkin.com">Shawn McGovern</a>
 * @version $Id: ENLAConvert.java,v 1.2 2004/06/21 23:15:47 mreyes Exp $
 */
public class ENLAConvert extends MarcConvert
{
	/**
	 * log4j Logger for this class.
	 */
    private static Logger log = Logger.getLogger(ENLAConvert.class);

	/**
	 * CVS header string.
	 */
    public static final String cvsHeader = "$Header: /cvs/root/melvyl/ei/marcconvert/java/org/cdlib/marcconvert/ENLAConvert.java,v 1.2 2004/06/21 23:15:47 mreyes Exp $";

	/**
	 * CVS version string.
	 */
    public static final String version = "$Revision: 1.2 $";

     /**
      * Array of MarcRecord objects corresponding to holding records 
      */
    private Vector arrHold = new Vector(200);

     /**
      * Bib 
      */
    private MarcRecord bibMarc = null;

     /**
      * Array of MarcRecord objects corresponding to holding records 
      */
    private String f904a = null;

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
     * Instansiate a new UCBConvert object.
     */
    public ENLAConvert()
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
        reset();
        return convert();
    }


    /**
     * Convert the current marc record. Place the result into the output
     * marc record class variable. The status of the conversion is returned.
     * @return the status code
     * @see org.cdlib.marcconvert.ConvertConstants
     */
    public int convert()
    {
        int rc = CONVERT_REC_SUCCESS;
        arrHold.clear();
        bibMarc = null;

        int testStatus = 0;
        if (!hasBib()) {
            setStatusMsg("Bib segment not found");
            moveOut(inMarc, "001", "999");
            return CONVERT_REC_REJECT;
        }
        bibMarc = extGroup("001", "009", 0);
        if (bibMarc == null) {
            setStatusMsg("Bib segment not found");
            moveOut(inMarc, "001", "999");
            return CONVERT_REC_REJECT;
        }

        for (int i=0; i < 1000; i++) {
            MarcRecord holdMarc = extGroup("009", "009", i);
            if (holdMarc == null) break;
            arrHold.addElement(holdMarc);
        }

        if (arrHold.size() == 0) {
            setStatusMsg("No holding records found (009)");
            moveOut(inMarc, "001", "999");
            return CONVERT_JOB_FAILURE; // CONVERT_REC_REJECT;
        }

        //move the input leader to output
        moveLeader(inMarc);

        //move all fields within 000 to 899 to output
        bibMarc.deleteFields("852");
        moveOut(bibMarc, "000", "899");

        // drop fields
        outMarc.deleteFields("005");  // Drop 005 - use 902 to build 005
        outMarc.deleteFields("9XX");  // Drop 9XX

        if ((testStatus = build901()) != CONVERT_REC_SUCCESS) {
            return testStatus;
        }
        
        for (int i=0; i < arrHold.size(); i++) {
            MarcRecord holdMarc = (MarcRecord)arrHold.elementAt(i);
            moveOut(holdMarc, "007", "007", MarcRecord.TAG_ORDER);
            build852(holdMarc);
        }

        return rc;
    }

    /**
     * Build marc 901 field.
     */
    private int build901()
    {
        String v001 = bibMarc.getFirstValue("001", null);
        if (v001 == null) {
            setStatusMsg("Missing 001 field");
            return CONVERT_REC_REJECT;
        }
        String v003 = bibMarc.getFirstValue("003", null);
        if (v003 == null) {
            setStatusMsg("Missing 003 field");
            return CONVERT_REC_REJECT;
        }

        f904a = null;
        if (v003.equals("CLU")) f904a = "LAGE";
        else if (v003.equals("CLU-ETH")) f904a = "LAEM";
        else if (v003.equals("CLU-FT")) f904a = "LAFT";
        else {
            setStatusMsg("003 field not recognized");
            return CONVERT_REC_REJECT;
        }
        MarcVblLengthField f904 = (MarcVblLengthField)outMarc.setField("901",
            "  -$a" + f904a + "-$b" + v001, 
            "-$", MarcRecord.MARC_TAG_ORDER);

        return CONVERT_REC_SUCCESS;
    }


    /**
     * Build marc 852 field.
     */
    private void build852(
        MarcRecord holdMarc)
    {
        MarcVblLengthField outF852 = new MarcVblLengthField(
                "852",  "  -$a" + f904a, "-$");
        MarcVblLengthField inF852 = (MarcVblLengthField)holdMarc.getFirstField("852");
        if (inF852 == null) return;
        MarcSubfield s852b = inF852.firstSubfield("b");
        if (s852b == null) return;
        String v852b = s852b.value();
        if (isEmpty(v852b)) return;

        outMarc.setField(outF852, MarcRecord.TAG_ORDER);        
        copy852(inF852, outF852);
        //add852x(holdMarc, inF852, outF852); // removed because of record size issues
        add852z(holdMarc, inF852, outF852);
        add852s3(holdMarc, inF852, outF852);
    }

    /**
     * Copy specific subfields from input to output 852
     * This has to be order based with detection of repeatable requirements
     *
     * @param inF852 852 field from input
     * @param outF852 current 852 containing
     */
    private void copy852(
            MarcVblLengthField inF852,
            MarcVblLengthField outF852
        )
    {
         String subTag = "bcefghijklmnpqst";
         String subRep = "nrrrrnrnrnrnnnrn";
         String mTag = null;
         String mRep = null;
         int outMax = 0;
         Vector subArr = null;
         for (int i=0; i < subTag.length(); i++) {
             mTag = subTag.substring(i,i+1);
             mRep = subRep.substring(i,i+1);
             subArr = inF852.subfields(mTag, true);
             if ((subArr == null) || (subArr.size() == 0)) continue;
             if (mRep.equals("n")) outMax = 1;
             else outMax = subArr.size();
             for (int ia=0; ia < outMax; ia++) {
                 MarcSubfield sub = (MarcSubfield)subArr.elementAt(ia);
                 outF852.addSubfield(sub);
             }
         }
    }

    /**
     * Copy specific subfields from input to output 852
     *
     * @param inF852 852 field from serial
     * @param outF852 current 852 containing
     */
    private void copy852Old(
            MarcVblLengthField inF852,
            MarcVblLengthField outF852
        )
    {
         Vector subArr = inF852.subfields("bcefghijklmnpqst", true);
         for (int i=0; i < subArr.size(); i++) {
             MarcSubfield sub = (MarcSubfield)subArr.elementAt(i);
             outF852.addSubfield(sub);
         }
    }

    /**
     * Add $x fields to 852
     *
     * @param holdMarc holding marc record
     * @param outF852 variable field being constructed
     */
    private void add852x(
            MarcRecord holdMarc,
            MarcVblLengthField inF852,
            MarcVblLengthField outF852
        )
    {
        copyAllSub("x", inF852, "x", outF852);
        add(holdMarc, "866", "x", outF852, "x");
        add(holdMarc, "867", "x", outF852, "x");
        add(holdMarc, "868", "x", outF852, "x");
    }

    /**
     * Add $z fields to 852
     *
     * @param holdMarc holding marc record
     * @param outF852 variable field being constructed
     */
    private void add852z(
            MarcRecord holdMarc,
            MarcVblLengthField inF852,
            MarcVblLengthField outF852
        )
    {
        copyAllSub("z", inF852, "z", outF852);
        add(holdMarc, "866", "z", outF852, "z");
        add(holdMarc, "867", "z", outF852, "z");
        add(holdMarc, "868", "z", outF852, "z");
    }


    /**
     * Add $z fields to 852
     *
     * @param holdMarc holding marc record
     * @param outF852 variable field being constructed
     */
    private void add852s3(
            MarcRecord holdMarc,
            MarcVblLengthField inF852,
            MarcVblLengthField outF852
        )
    {
        StringBuffer buf = new StringBuffer(1000);
        //appendAllSub(buf, "3", inF852, "; ");
        String f852v3 = inF852.firstSubfieldValue("3");
        if (f852v3 != null) buf.append(f852v3);

        append(buf, holdMarc, "866", "a", null, "; ");
        append(buf, holdMarc, "867", "a", "Supplements:", "; ");
        append(buf, holdMarc, "868", "a", "Indexes:", "; ");
        if ( !isEmpty(buf.toString()) ) {
            outF852.addSubfield("3", buf.toString());
        }
    }

    /**
     * Process: 852 Location Segments - 852 $x Nonpublic Note
     *
     * @param inMarc marc record used for extraction
     * @param extTag tag value for extraction
     * @param extCode subfield code for subfield extraction
     * @param outField variable field used for adding extracted subfields
     * @param addCode sufield code for added subfield
     */
    private void add(
            MarcRecord inMarc,
            String extTag,
            String extCode,
            MarcVblLengthField outField,
            String addCode
        )
    {
        MarcFieldList list = inMarc.allFields(extTag);
        MarcVblLengthField field = null;
        for (int ifld=0; ifld<list.size(); ifld++) {
            field = (MarcVblLengthField)list.elementAt(ifld);
            Vector subArr = field.subfields(extCode, true);
            for (int isub=0; isub < subArr.size(); isub++) {
                MarcSubfield sub = (MarcSubfield)subArr.elementAt(isub);
                String subValue = sub.value();
                outField.addSubfield(addCode, subValue);
            }
        }
    }

    /**
     * Append subfield values into buffer
     *
     * @param buf allocated buffer used for appending subfield values
     * @param inMarc marc record used for extraction
     * @param extTag tag value for extraction
     * @param extCode subfield code for subfield extraction
     * @param delim delimiter
     */
    private void append(
            StringBuffer buf,
            MarcRecord inMarc,
            String extTag,
            String extCode,
            String prefix,
            String delim
        )
    {
        MarcFieldList list = inMarc.allFields(extTag);
        MarcVblLengthField field = null;
        for (int ifld=0; ifld<list.size(); ifld++) {
            field = (MarcVblLengthField)list.elementAt(ifld);
            Vector subArr = field.subfields(extCode, true);
            for (int isub=0; isub < subArr.size(); isub++) {
                MarcSubfield sub = (MarcSubfield)subArr.elementAt(isub);
                String subValue = sub.value();
                if (isEmpty(subValue)) continue;
                if (buf.length() > 0) buf.append(delim);
                if (prefix != null) {
                    buf.append(prefix);
                    prefix = null; // only once
                }
                buf.append(subValue);
            }
        }
    }


    /**
     * Append subfield values into buffer
     *
     * @param buf allocated buffer used for appending subfield values
     * @param extTag tag value for extraction
     * @param inFld field used for extraction
     * @param delim delimiter
     */
    private void appendAllSub(
            StringBuffer buf,
            String extCode,
            MarcVblLengthField inFld,
            String delim
        )
    {
        Vector vec = inFld.subfields(extCode, true);
        MarcSubfield sub = null;
        String subVal = null;
        if ((vec != null) && (vec.size() > 0)) {
            for (int i=0; i < vec.size(); i++) {
                sub = (MarcSubfield)vec.elementAt(i);
                subVal = sub.value();
                if ( !isEmpty(subVal) ) {
                    if (buf.length() > 0) buf.append(delim);
                    buf.append(subVal);
                }
            }
            return;
        }
        return;
    }

    private MarcRecord extGroup(String startTag, String nextTag, int occ)
    {

        if (isEmpty(startTag) || isEmpty(nextTag)) return null;
        int addcnt = 0;
        MarcFieldList list = inMarc.allFields();
        int cnt = -1;
        int inx = 0;
        Field field = null;
        for (inx=0; inx < list.size(); inx++) {
            field = list.elementAt(inx);
            if (field == null) break;
            if (field.tag().equals(startTag)) {
                cnt++;
                if (cnt == occ) break;
            }
        }

        // unable to match occurrence
        if (cnt < occ) return null;
        MarcFieldList tList = new MarcFieldList();

        // add first field allows startTag == nextTag
        //log.debug("extGroup - add field.tag:" + field.tag());
        tList.addElement(field);
        addcnt++;
       
        // add until local tag matches nextTag or end of list
        for (int binx = inx + 1; binx < list.size(); binx++) {
            field = list.elementAt(binx);
            if (field.tag().equals(nextTag)) break;
            //log.debug("extGroup - add field.tag:" + field.tag());
            tList.addElement(field);
            addcnt++;
        }
        if (tList.size() == 0) return null;

        MarcRecord groupMarc = new MarcRecord(tList, MarcRecord.END_LIST);
        log.debug("extGroup - start:" + startTag 
            + " - next:" + nextTag 
            + " - occ:" + occ 
            + " - size:" + list.size()
            + " - added:" + addcnt);
        return groupMarc;
    }

    private boolean hasBib()
    {
        String startTag = "001";
        String nextTag = "009";
        boolean ret = false;

        int addcnt = 0;
        MarcFieldList list = inMarc.allFields();
        int cnt = -1;
        int inx = 0;
        Field field = null;
        for (inx=0; inx < list.size(); inx++) {
            field = list.elementAt(inx);
            if (field == null) break;
            if (field.tag().equals(startTag)) return true;
            if (field.tag().equals(nextTag)) return false;
        }
        return false;
    }

}
