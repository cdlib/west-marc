package org.cdlib.marcconvert;

import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.apache.regexp.RE;
import org.apache.regexp.RESyntaxException;

import org.cdlib.util.marc.Field;
import org.cdlib.util.marc.MarcException;
import org.cdlib.util.marc.FieldList;
import org.cdlib.util.marc.MarcFixedLengthField;
import org.cdlib.util.marc.MarcRecord;
import org.cdlib.util.marc.MarcSubfield;
import org.cdlib.util.marc.MarcVblLengthField;
import org.cdlib.util.HexUtils;

/**
 * Convert the marc records from the CSL sources.
 *
 *
 * @author <a href="mailto:dloy@ucop.edu">David Loy</a>
  * @version $Id: CSLConvert.java,v 1.8 2005/02/04 23:44:35 rkl Exp $
 */
public class CSLConvert extends MarcConvert
{
	/**
	 * log4j Logger for this class.
	 */
    private static Logger log = Logger.getLogger(CSLConvert.class);

	/**
	 * CVS header string.
	 */
    public static final String cvsHeader = "$Header: /cvs/root/melvyl/ei/marcconvert/java/org/cdlib/marcconvert/CSLConvert.java,v 1.8 2005/02/04 23:44:35 rkl Exp $";

	/**
	 * CVS version string.
	 */
    public static final String version = "$Revision: 1.8 $";

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

    // record characteristic flags
        private boolean flagNoRLINCCS = true;
        private boolean flagCCSP = false;
        private boolean flag074 = false;
        private boolean flagAMC = false;

        // drop list
        private String dropList[] = {"005", "039", "069", "590", "852", "899"};

        // fields to drop if they contain an empty $a
        private String emptyFieldsList[] = {
                     "020", "022", "040", 
                     "100", 
                     "260", 
                     "351", 
                     "500", "506", "541", "545", "555",
                     "600", "651",
                     "700"
                    };

        private MarcSubfield f010_o = null;
        private MarcVblLengthField f090_last = null;
        private Vector libraryHas = null;

    /**
     * Instanstiate a new CSLConvert object.
     */
    public CSLConvert()
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


    //====================================================
    //       PRIVATE
    //====================================================
    /**
     * Convert the current marc record. Place the result into the output
     * marc record class variable. The status of the conversion is returned.
     * @return the status code
     * @see org.cdlib.marcconvert.ConvertConstants
     */
    private int debugConvert()
    {
        try {
            return convert();
        }
        catch (Exception except) { 
                System.out.println(except); 
                except.printStackTrace();
                return 2;
        }             
    }

    /**
     * Convert the current marc record. Place the result into the output
     * marc record class variable. The status of the conversion is returned.
     * @return the status code
     * @see org.cdlib.marcconvert.ConvertConstants
     */
    private int convert()
    {
        int rc = CONVERT_REC_SUCCESS;

        //set status for this record
        setStatus(OK);

        //start processing
        start();

        //process leader (3.0)
        processLeader();
       
        // process Bibliographic Fields
        processBibliographicFields();

        // process Location info building the 852 field
        processLocationFields();

        return rc;
    }


    /**
     * Process 010 - drop $o from 010 
     * Process 035 - create new 035 from 010 $o
     *
     */
    private void start() 
    {
        flagNoRLINCCS = true;
        flagCCSP = false;
        flag074 = false;
        flagAMC = false;

        Field field = null;
        MarcVblLengthField buildField = null;

        f010_o = inMarc.getFirstSubfield("010", "o");
        if (f010_o != null) {
            String subStr = f010_o.value();
            if (!isEmpty(subStr)) {
                if (subStr.indexOf("RLINCCS") >= 0) flagNoRLINCCS = false;
                if (subStr.indexOf("RLINCCSP") >= 0) flagCCSP = true;
            }
            else f010_o = null;
        }
        FieldList list090 = inMarc.allFields("090");
        f090_last = null;
        if ((list090 != null) && (list090.size() > 0)) {
             f090_last = (MarcVblLengthField)list090.elementAt(list090.size() - 1);
        }

        libraryHas = null;

        field = inMarc.getFirstField("074");
        if (field != null) flag074 = true;
 
        if (inMarc.getRecordType().equals("b")) flagAMC = true;
        else if (inMarc.getControlType().equals("a")) flagAMC = true;
    }

    /**
     * process Leader for output - 3.x
     */
    private void processLeader()
    {
        String status = inMarc.getRecordStatus();

        org.cdlib.util.marc.MarcLeader inLeader = inMarc.getLeader();
        outMarc.setLeader(inLeader);
      
        status = status.replace('\u007C', 'c');
        outMarc.setRecordStatus(status);

        String recType = inMarc.getRecordType();
        recType = recType.replace('h', 'a');
        outMarc.setRecordType(recType);


        String encLvl = inMarc.getEncLvl();
        encLvl = encLvl.replace('\u007C', '7');
        outMarc.setEncLvl(encLvl);

        String isbd = inMarc.getDescCatForm();
        isbd = isbd.replace('\u007C', ' ');
        outMarc.setDescCatForm(isbd);
    }


    /**
     * Process Bibliographic Fields - 4.0
     *
     */
    private void processBibliographicFields()
    {
        // Records to drop
        String s245a = inMarc.getFirstValue("245", "a");
        Field field = null;
        if ((s245a != null) 
              && (s245a.length() >= 6)
              && s245a.substring(0,6).equals("DELETE") ) {
            throw new MarcDropException("245 $a indicates delete");
        }

        FieldList mfl = inMarc.allFields();
        for (int i=0; i<mfl.size(); i++) {
            field = mfl.elementAt(i);
            if (dropField(field)) continue;
            if (emptyField(field)) continue;
            if (field.tag().equals("010")) {
                add010((MarcVblLengthField)field);
                continue;
            }
            outMarc.setField(field, MarcRecord.END_LIST);
        }
        add901();
    }


    /**
     * Process the Location fields of the record:
     * AMC, non-950, and 950 types
     *
     */
    private void processLocationFields()
    {
        if (flagAMC) {
            processAMCLocation();
        } else {
            Field field = inMarc.getFirstField("950");
            if (field == null) {
                processSingleLocation();
            } else {
                process950Locations();
            }
        }
    }

    /**
     * Process AMC Location
     *
     */
    private void processAMCLocation()
    {
        String fld = "  -$aCSLD-$bCAL*";
        MarcVblLengthField f852 = (MarcVblLengthField)outMarc.setField("852", 
                fld, "-$", MarcRecord.TAG_ORDER);
        MarcVblLengthField f950 = (MarcVblLengthField)inMarc.getFirstField("950");
        addCallNumber(f950, f852);
        add852k(f950, f852);
        add852m_950de(f950, f852);
        add852m_086(f852);
    }

    /**
     * Process location for Record not having any 950
     *
     */
    private void processSingleLocation()
    {
        Field field = inMarc.getFirstField("074");
        String subb = null;
        if (field != null) subb = "US";
        else subb = "X";
        String fld = "  -$aCSLD-$b" + subb;
        MarcVblLengthField f852 = (MarcVblLengthField)outMarc.setField(
                "852", fld, "-$", MarcRecord.TAG_ORDER);
        addCallNumber(null, f852);        
        add852m_086(f852);
        add852z(null, f852);
        add852s3(null, f852);
    }

    /**
     * Process Record containing one or more 950
     *
     */
    private void process950Locations()
    {
        String subb = null;
        FieldList list950 = inMarc.allFields("950");
        MarcVblLengthField f852 = null;
        MarcVblLengthField f950 = null;
        for (int i=0; i<list950.size(); i++) {
            f950 = (MarcVblLengthField)list950.elementAt(i);
            f852 = new MarcVblLengthField("852", "  -$aCSLD", "-$");
            subb = add852b(f950, f852);
            if (subb == null) continue;
            addCallNumber(f950, f852);
            add852k(f950, f852);
            add852m_950de(f950, f852);
            add852m_086(f852);
            add852x(f950, f852);
            add852z(f950, f852);
            add852s3(f950, f852);
            outMarc.setField(f852, MarcRecord.TAG_ORDER);
        }
    }

    /**
     * Process: 852 Location Segments - 852 $h, $i, $j Call Numbers
     *
     * @param f950 current 950 field to be processed into 852
     *
     * @param f852 current 852 containing location info for output
     */
    public void addCallNumber(
        MarcVblLengthField f950,
        MarcVblLengthField f852)
    {
       
        String callNo = null;

        // call number from 541
        String s541e = inMarc.getFirstValue("541", "e");
        if (!isEmpty(s541e)) {
            f852.addSubfield("j", trim(s541e));
            return;
        }

        // call number from 950
        if (f950 != null) {
            if (  add852hij(f950, f852) ) return;
        }

        // call number from 090
        if (f090_last == null) return;
        if (  add852hij(f090_last, f852) ) return;
    }

    /**
     * Process: Bibliographic fields - 010 LCCN - drop $o from 010 
     * Process: Bibliographic fields -  035 Field - create new 035 from 010 $o
     *
     */
    private void add010(MarcVblLengthField f010) 
    {
        MarcVblLengthField buildField = null;
        if (f010_o == null) {
            outMarc.setField(f010, MarcRecord.MARC_TAG_ORDER);
            return;
        }

        Vector subFields = f010.allButSubfields("o");
        if ((subFields != null) && (subFields.size() > 0)) {
            buildField = new MarcVblLengthField("010", "  ", subFields);
            outMarc.setField(buildField, MarcRecord.MARC_TAG_ORDER);
        }
        outMarc.setField("035", "  -$a" + f010_o.value(), "-$", MarcRecord.MARC_TAG_ORDER);
    }


    /**
     * Process: 852 Location Segments - Records with 950 fields
     *
     * @param f950 current 950 field to be processed into 852
     *
     * @param f852 current 852 containing location info for output
     */
    private String add852b(
        MarcVblLengthField f950,
        MarcVblLengthField f852)
    {
        String subb = null;
        String subi = f950.firstSubfieldValue("i");
        String subL = f950.firstSubfieldValue("l");
        String s010o = null;
        if (f010_o != null) s010o = f010_o.value();


        if ( subL == null ) subb = "X";
        else if ( isEmpty(subL) ) return null;
        else subb = subL;
        if ((subi != null) && (subi.length() >= 10)) {
            if (subi.charAt(9) == 'D') return null;
        }

        if (subb.equals("R")) {
            if (s010o == null) {
                subb = "X";
            } else if (s010o.indexOf("RLINCCSL") >= 0) {
                subb = "RLAW";
            } else if (s010o.indexOf("RLINCCSG") >= 0) {
                subb = "RREF";
            } else {
                subb = "X";
            }

        } else if (subb.equals("S")) {
            if (s010o == null) {
                subb = "X";
            } else if (s010o.indexOf("RLINCCSL") >= 0) {
                subb = "SLAW";
            } else if (s010o.indexOf("RLINCCSG") >= 0) {
                subb = "SUTRO";
            } else if (flagCCSP) {
                subb = "SUTRO";
            } else {
                subb = "X";
            }
        }
        f852.addSubfield("b", subb); 
        return subb;
    }

    /**
     * Process: 852 Location Segments - (Generic handling)
     *     Taking the call number from the 950 field
     *     Taking the call number from the 090 field
     *
     * @param inField field containing $a and $b for creation of 852 $hij 
     *
     * @param f852 current 852 containing location info for output
     */
    private boolean  add852hij(
        MarcVblLengthField inField,
        MarcVblLengthField f852)
    {
        if (inField == null) return false;

        String suba = inField.firstSubfieldValue("a");
        String subb = inField.firstSubfieldValue("b");

        suba = fix(suba, '\\');
        subb = fix(subb, '\\');
        if ((suba != null) || (subb != null)) {
            if ((suba != null) && (subb != null)) {
                f852.addSubfield("h", suba);
                f852.addSubfield("i", subb);
            } else {
                if (suba != null) f852.addSubfield("j", suba);
                if (subb != null) f852.addSubfield("j", subb);
            }
            return true;
        }
        return false;
    }

    /**
     * Process: 852 Location Segments - 852 $k Call Number Prefix
     *
     * @param f950 current 950 field to be processed into 852
     *
     * @param f852 current 852 containing location info for output
     */
    private void  add852k(
        MarcVblLengthField f950,
        MarcVblLengthField f852)
    {
        String subd = null;
        StringBuffer buf = new StringBuffer(4);
        if (f950 == null) return;

        Vector vecd = f950.subfields("d",true);
        if ((vecd == null) || (vecd.size() == 0)) return;
        for (int i=0; i<vecd.size(); i++) {
            subd = ((MarcSubfield)vecd.elementAt(i)).value();
            if ((subd.indexOf('*') < 0)
                && (subd.length() <= 4)) {
                subd = fix(subd, '\\');
                if (!isEmpty(subd)) f852.addSubfield("k", subd);
            }
        }
        return;
    }

    /**
     * Process: 852 Location Segments - $m From 086 fields in Marcive Records
     *
     * @param f852 current 852 containing location info for output
     */
    private void  add852m_086(
        MarcVblLengthField f852)
    {
        String f086a = null;
        String f090a = null;
        int addcnt = 0;

        if (!flagNoRLINCCS) return;
        if (!flag074) return;
        boolean match_f086_f090 = false;
        MarcVblLengthField f086 = null;
        FieldList fields = inMarc.allFields("086");
        if ((fields == null) || (fields.size() == 0)) return;
        MarcVblLengthField f086_last = (MarcVblLengthField)fields.elementAt(fields.size()-1);
        if (f090_last == null) match_f086_f090 = false;
        else {
            f086a = f086_last.firstSubfieldValue("a");
            f090a = f090_last.firstSubfieldValue("a");
 //System.out.println("086a="+ f086a + " - 090a=" + f090a);

            if ( isEmpty(f086a) ) match_f086_f090 = false;
            else if ( isEmpty(f090a) ) match_f086_f090 = false;
            else if (f086a.equals(f090a)) match_f086_f090 = true;
        }
        int outCnt = fields.size();
//System.out.println("outCnt=" + outCnt + " - match=" + match_f086_f090);
        if (match_f086_f090) outCnt--;
        //if (outCnt > 5) outCnt = 5;

        if (outCnt > 0) {
            StringBuffer buf = new StringBuffer(200);
            buf.append("Other Govt Docs numbers: ");
            for (int i=0; i<outCnt; i++) {
                f086 = (MarcVblLengthField)fields.elementAt(i);
                f086a = f086.firstSubfieldValue("a");
                if ( !isEmpty(f086a) ) {
                    if (addcnt > 0) buf.append(',');
                    buf.append(f086a);
                    addcnt++;
                }
            }
            if (addcnt > 0) f852.addSubfield("m", buf.toString());
        }
    }

    /**
     * Process: 852 Location Segments - $m's From 950 Fields
     *
     * @param f950 current 950 field to be processed into 852
     *
     * @param f852 current 852 containing location info for output
     */
    private void  add852m_950de(
            MarcVblLengthField f950,
            MarcVblLengthField f852)
    {
        String subd = null;
        String sube = null;
        String subm = null;
        StringBuffer buf = new StringBuffer(4);
        MarcSubfield subfield = null;

        if (f950 == null) return;

        Vector vecde = f950.subfields("de", true);
        if ((vecde == null) || (vecde.size() == 0)) return;
        for (int i=0; i<vecde.size(); i++) {
            subfield = (MarcSubfield)vecde.elementAt(i);
            subm = null;
            if (subfield.tag().equals("d")) {
                subd = trim(subfield.value());
                if (subd.indexOf("\\*\\") >= 0) {
                    subm = "Non-circ";
                } else if (subd.indexOf("\\**\\") >= 0) {
                    subm = "Rare Book";
                } else if (subd.length() > 4) {
                    subm = fix(subd, '\\');
                }
                if (!isEmpty(subm)) f852.addSubfield("m", subm);
            }

           if (subfield.tag().equals("e"))  {
                sube = subfield.value();
                if (sube.indexOf("#30") >= 0) {
                    subm = "also in Calif";
                } else {
                    if ( isEmpty(sube) ) continue;
                    if (sube.charAt(0) == '\\') {
                        subm = fix(sube,'\\');
                    }
                }
                if (!isEmpty(subm)) f852.addSubfield("m", subm);
           }
        }
        return;
    }

    /**
     * Process: 852 Location Segments - 852 $x Nonpublic Note
     *
     * @param f950 current 950 field to be processed into 852
     *
     * @param f852 current 852 containing location info for output
     */
    private void add852x(
            MarcVblLengthField f950,
            MarcVblLengthField f852
        )
    {
        if (flagAMC) return;
        if (f950 == null) return;
        MarcSubfield subf = copyFirstSub("f", f950, "x", f852);
        if ((subf == null) && (f090_last != null)) {
            subf = copyFirstSub("f", f090_last, "x", f852);
        }
        copyAllSub("u", f950, "x", f852);
        MarcSubfield subw = copyFirstSub("w", f950, "x", f852);
    }

    /**
     * Process: 852 Location Segments - 852 $z Public Note
     *
     * @param f950 current 950 field to be processed into 852
     *
     * @param f852 current 852 containing location info for output
     */
    private void add852z(
            MarcVblLengthField f950,
            MarcVblLengthField f852
        )
    {
        libraryHas = new Vector(10);
        String subs = null;

        if (flagAMC) return;
        FieldList list590 = inMarc.allFields("590");
        if ((list590 != null) && (list590.size() > 0)) {
            MarcVblLengthField f590 = null;
            String s590a = null;
            String s590aLower = null;

            for (int i=0; i<list590.size(); i++) {
                f590 = (MarcVblLengthField)list590.elementAt(i);
                s590a = f590.firstSubfieldValue("a");
                if (isEmpty(s590a)) continue;
                s590aLower = s590a.toLowerCase();
                if (f010_o != null) {
                    String s010 = f010_o.value();
                    int lpos = s590aLower.indexOf("library has:");
                    if (flagCCSP && (lpos >= 0)) {
                        s590a = s590a.substring(lpos + 12, s590a.length() );
                        if ( !isEmpty(s590a) ) libraryHas.addElement( trim(s590a) );
                        continue;
                    }
                }
                f852.addSubfield("z", s590a);
            }
        }

        if (f950 != null) {
            Vector vec = f950.subfields("n",true);
            if ((vec != null) && (vec.size() > 0)) {
                for (int i=0; i<vec.size(); i++) {
                    subs = ((MarcSubfield)vec.elementAt(i)).value();
                    subs = fix(subs, '\\');
                    if (subs != null) f852.addSubfield("z", subs);
                }
            }
        }

        if (f090_last != null) {
            Vector vec = f090_last.subfields("n",true);
            if ((vec != null) && (vec.size() > 0)) {
                for (int i=0; i<vec.size(); i++) {
                    subs = ((MarcSubfield)vec.elementAt(i)).value();
                    if ( isEmpty(subs) ) continue;
                    if (subs.charAt(0) == '\\') {
                        subs = fix(subs, '\\');
                        if (subs != null) f852.addSubfield("z", subs);
                    }
                }
            }
        }


    }

    /**
     * Process: 852 Location Segments - 852 $3 Materials Specified (Summary Holdings)
     *
     * @param f950 current 950 field to be processed into 852
     *
     * @param f852 current 852 containing location info for output
     */
    private void add852s3(
            MarcVblLengthField f950,
            MarcVblLengthField f852
        )
    {
        if (flagAMC) return;

        // see add852z for setting of this as an optimization
        if (libraryHas != null) {
            if (flagCCSP) {
                String s590a = null;
                int hasCnt = libraryHas.size();
                for (int i=0; i<hasCnt; i++) {
                    s590a = (String)libraryHas.elementAt(i);
                    f852.addSubfield("3", s590a);
                    break; // added to output only one $3
                }
                return;
            }
         
        }
        else {
            throw new MarcException(this, "call to add852z required before call to add852s3");
        }

        if (f950 != null) {
            Vector vec = f950.subfields("vyz", true);
            if ((vec != null) && (vec.size() > 0)) {
                StringBuffer buf = new StringBuffer(200);
                String sub = null;
                int addcnt = 0;
                for (int i=0; i < vec.size(); i++) {
                    sub = ((MarcSubfield)vec.elementAt(i)).value();
                    if ( isEmpty(sub) ) continue;
                    if (addcnt > 0) buf.append(' ');
                    buf.append(sub);
                    addcnt++;
                }
                if (addcnt > 0) f852.addSubfield("3", buf.toString());
            }
        }
    } 

    /**
     * Process: 852 Location Segments - Records with 950 fields
     */
    private void add901()
    {
        String f001 = inMarc.getFirstValue("001", "");
        if (isEmpty(f001)) {
            throw new MarcDropException("Missing 001 field");
        }
        String errmsg = null;
        try {
            f001 = trim(f001);
            RE tagExp = new RE("[A-Z][A-Z][A-Z]-\\d\\d\\d");
            if (!tagExp.match(f001)) 
                errmsg = new String("001 field has invalid format:" + tagExp);
            }
        catch (Exception ex) {
            errmsg = "RE setup fails";
        }
        if (errmsg != null) {
            throw new MarcDropException(errmsg);
        }
        String fld = "  -$aCSLD-$b" + f001;
        outMarc.setField("901", fld, "-$", MarcRecord.MARC_TAG_ORDER);
    }

    /**
     * Process Bibliographic Fields - 4.3 Fields to Drop
     *
     */
    private boolean dropField(Field field)
    {
        String mTag = null;
        String tag = field.tag();
        if (tag.substring(0,1).equals("9")) return true;
        for (int i=0; i<dropList.length; i++) {
            mTag = dropList[i];
            if (mTag.equals(tag)) return true;
        }
        return false;
    }

    /**
     * Drop empty $a fields - 4.4
     *
     */
    private boolean emptyField(Field inField)
    {
        String mTag = null;
        String tag = inField.tag();
        String suba = null;
 
        if (tag.compareTo( "010" ) < 0) return false;
        MarcVblLengthField field = (MarcVblLengthField)inField;
        for (int i=0; i < emptyFieldsList.length; i++) {
            mTag = emptyFieldsList[i];
            if (mTag.equals(tag)) {
                suba = field.firstSubfieldValue("a");
                if (suba == null) return false;
                if (trim(suba) == null) return true;
                return false;
            }
        }
        return false;
    }

    /* ------------- Generic routines ------------- */

    private static final boolean emptyChar(char c)
    {
            if ( c <= ' ' ) {
                if (c == '\u001B') return false;
                return true;
            } 
            else return false;
     }

    private String fix(String in, char val)
    {
        if ((in == null) || (in.length() == 0)) return null;
//System.out.println(" in:" + in);
        int slen = in.length();
        StringBuffer bufin = new StringBuffer(in);
        StringBuffer bufout = new StringBuffer(slen);
        int iBeg = 0;
        int iEnd = slen - 1;
        boolean blank = false;
        char c1 = '\0';
        char cp = '\0';
        for (; iBeg < slen; iBeg++) {
            c1 = bufin.charAt(iBeg);
            if (c1 == val) {
                bufin.setCharAt(iBeg, ' ');
                blank = true;
            }
            else if ( emptyChar(c1) ) blank = true;
        }
        if (!blank) return in;
//System.out.println("rep:" + bufin.toString());

        for (iBeg=0; iBeg < slen; iBeg++) {
            if ( !emptyChar( bufin.charAt(iBeg) ) ) break;
        }
        if (iBeg == slen) return null;
        
        for (; iEnd > iBeg; iEnd--) {
            if ( !emptyChar( bufin.charAt(iEnd) ) ) break;
        }
//System.out.println("beg:" + iBeg + " - end:" + iEnd + " out:" + bufout.toString() + '+');

        cp = bufin.charAt(iBeg);
        bufout.append(cp);
        for (iBeg++; iBeg <= iEnd; iBeg++) {
            c1 = bufin.charAt(iBeg);
            if ((c1 == cp) && (c1 == ' ')) continue;
            cp = c1;
            bufout.append(c1);
        }

//System.out.println("beg:" + iBeg + " - end:" + iEnd + " out:" + bufout.toString() + '+');
        if (bufout.length() == 0) return null;
        return bufout.toString();
    }

    private String trim(String in)
    {
        if ((in == null) || (in.length() == 0)) return null;
        char [] sarr = in.toCharArray();
        int len = sarr.length;
        
        int iBeg = 0;
        int iEnd = 0;
        char c = '\0';
        for (iBeg=0; iBeg < len; iBeg++) {
            if ( !emptyChar(sarr[iBeg]) ) break;
        }
        if (iBeg > len) return null;

        for (iEnd=len-1; iEnd > iBeg; iEnd--)  {
            if ( !emptyChar(sarr[iEnd]) ) break;
        }

        if ((iBeg == 0) && (iEnd == (len-1))) return in;
        return in.substring(iBeg, iEnd+1);
    }

}
