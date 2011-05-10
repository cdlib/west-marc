package org.cdlib.marcconvert;

import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.Hashtable; // Hash of location codes
import java.util.Enumeration;

import org.apache.log4j.Logger;
import org.apache.regexp.RE; // http://jakarta.apache.org/regexp/apidocs
import org.apache.regexp.RESyntaxException;


import org.cdlib.util.marc.Field;
import org.cdlib.util.marc.MarcException;
import org.cdlib.util.marc.MarcFieldList;
import org.cdlib.util.marc.MarcFixedLengthField;
import org.cdlib.util.marc.MarcRecord;
import org.cdlib.util.marc.MarcSubfield;
import org.cdlib.util.marc.MarcVblLengthField;


/**
 * Convert the marc records from the OCLC sources.
 *
 *
 * @author <a href="mailto:jgarey@library.berkeley.edu">Janet Garey</a>
 * @author <a href="mailto:shawnm@splorkin.com">Shawn McGovern</a>
 * @author <a href="mailto:mark.reyes@ucop.edu">Mark Reyes</a>
 * @version $Id: OCLCConvert.java,v 1.11 2006/11/07 23:22:11 rkl Exp $
 */
public class OCLCConvert
    extends MarcConvert {
  /**
   * log4j Logger for this class.
   */
  private static Logger log = Logger.getLogger(OCLCConvert.class);

  /**
   * CVS header string.
   */
  public static final String cvsHeader = "$Header: /cvs/root/melvyl/ei/marcconvert/java/org/cdlib/marcconvert/OCLCConvert.java,v 1.11 2006/11/07 23:22:11 rkl Exp $";

  /**
   * CVS version string.
   */
  public static final String version = "$Revision: 1.11 $";

  /**
   * Private indicator used to assure CVS information is logged
   * only once.
   */
  private static boolean versionLogged = false;

  /*
   * Static initializer used to log cvs header info.
   */
  {
    if (!versionLogged) {
      log.info(cvsHeader);
      versionLogged = true;
    }
  }

  /**
   * Instanstiate a new OCLCConvert object.
   */
  public OCLCConvert() {
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
  public int convert(MarcRecord inRec, MarcRecord outRec) {
    if (inRec == null) {
      throw new MarcException(this, "Converter received null input MarcRecord");
    }

    if (outRec == null) {
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
  private int convert() {
    int rc = CONVERT_REC_SUCCESS;

    // set status for this record
    setStatus(OK);

    // reject record if key fields, subfields missing
    // Returns 994 $b - input source
    // Spec. 2.0
    String inst = checkMissingFields();

    // reject record if improper cataloging source, type, or format
    // return 994 $a - update transaction type
    // Spec. 2.1/3.1/3.2
    String updateTransactionType = checkSourceTypeFormat(inst);

    // move the input leader to output
    moveLeader();

    // check for delete status and set leader value
    // Input 994 $a - update transaction type
    // Spec 4.1
    setDeleteStatus(updateTransactionType);

    // move all fields within 000 to 899 to output record
    // Spec 5.1
    moveOut(0, 899);

    // build these fields for output
    // Spec. 5.4.1
    if (inst.equals("CBT")) {
      outMarc.deleteFields("690"); // Drop 690, we will build new one.
      build690();
    }

    // Create 901 field (Maintenance Key) from existing data
    // Input 994 $b - input source
    // Spec. 6.1
    outMarc.deleteFields("901"); // Drop 901, even though it shouldn't exist.
    build901(inst);

    // Create 852 field from existing data
    // Input 994 $b - input source
    // Spec. 6.2
    outMarc.deleteFields("852"); // Drop 852, we will build new one.
    build852(inst);

    // delete all 590's from output record
    // Spec. 5.2
    outMarc.deleteFields("590");

    return rc;
  }

  /**
   * check for missing fields
   *     record is rejected if
   *        record lacks 001, 994, or 994 $b
   * OCLC spec 3.1/2
   */
  private String checkMissingFields() {

    Field v001 = inMarc.getFirstField("001");
    // if no 001 field found
    if (v001 == null) {
      throw new MarcDropException("001 field not found");
    }

    Field v994 = inMarc.getFirstField("994");
    // if no 994 field found
    if (v994 == null) {
      throw new MarcDropException("994 field not found");
    }

    String v994a = inMarc.getFirstValue("994", "a");
    // if no 994 $a subfield found
    if (v994a == null) {
      throw new MarcDropException("994 $a subfield not found");
    }

    String v994b = inMarc.getFirstValue("994", "b");
    // if no 994 $b subfield found
    if (v994b == null) {
      throw new MarcDropException("994 $b subfield not found");
    }

    // return inst variable now for use later
    return v994b.toUpperCase();
  }

  /**
   * check cataloging source, transaction type, and format
   *     if 994 $b not a valid OCLC code or not present
   *         record is rejected.
   *     if 994 $a is "11", indicating "replace" the record is rejected.
   *     if 994 $b is "CUH" (Hastings) and format is serial
   *         record is rejected.
   *     Input: 994 $b - location.
   *     Return: 994 $a - type.
   */
  private String checkSourceTypeFormat(String inst) {

    //get value of 994 and $a
    String v994a = inMarc.getFirstValue("994", "a");

    // Check source. Spec. 2.1
    if (!inst.equals("CAW") && !inst.equals("CRL") &&
        !inst.equals("CBG") && !inst.equals("CBT") &&
        !inst.equals("CUH") && !inst.equals("HMF") &&
        !inst.equals("NPW") && !inst.equals("PRO") &&
        !inst.equals("QCE") && !inst.equals("WCA")) {
      throw new MarcDropException("994 $b - Source not recognized");
    }
    // Spec. 3.1
    else if (v994a.equals("11")) {
      throw new MarcDropException(
          "994 $a - Online replacement rec. contains no local data field");
    }
    return v994a;
  }

  /**
   * set delete status in leader
   *      from OCLC 994 $a
   */
  private void setDeleteStatus(String v994a) {
    if (v994a.equals("03") || v994a.equals("93")) {
      outMarc.setRecordStatus("d"); // sets pos 5 of leader to "d"
    }
  }

  /**
   * build marc 690 fields from 653 fields.  Called for CBT records.
   * Spec. 5.4.1
   */
  private void build690() {
    MarcVblLengthField field = null;
    MarcSubfield subfield = null;
    int fieldCount = 0;

    // get all 653 fields
    MarcFieldList fields = inMarc.allFields("653");

    // for each 653 field
    while (fields.hasMoreElements()) { 

      // get the field from MarcFieldList
      field = (MarcVblLengthField) fields.nextElement();

      //put all sub a's in a vector
      Vector subfieldA = field.subfield("a");
      Enumeration subfieldAEnum = subfieldA.elements();

      // loop to get all $a's in field
      while (subfieldAEnum.hasMoreElements()) {
        subfield = (MarcSubfield) subfieldAEnum.nextElement();
        // if no subfield "a" then process next 653 field
        if (!exists(subfield)) {
          continue;
        }

        // Uppercase the first letter of the subfield and put in buffer
        StringBuffer value = new StringBuffer(subfield.value().substring(0, 1).
                                              toUpperCase());
        // add the rest of the subfield to the buffer
        value.append(subfield.value().substring(1));

        // Create a new 690 with 7 in the 2nd indicator
        MarcVblLengthField f690 = outMarc.getNewVblField("690", " 7");
        // add the uppercased text as a $a
        f690.addSubfield("a", value.toString());

        // add $2 TRIS to the 690
        f690.addSubfield("2", "TRIS");
      } // end loop to get all $a's
    } // end loop to get all 653's

    //after all 690's are built, delete all 653's from output record
    outMarc.deleteFields("653");
  }

  private void build901(String inst) {
    // the 994 $b has been stored in the inst variable

    // get the 001 field
    MarcFixedLengthField f001 = (MarcFixedLengthField) outMarc.getFirstField(
        "001");
    if (f001 == null) {
      throw new MarcDropException("Field 001 - Output field does not exist.");
    }

    // set new values of inst for NPW and PRO
    if (inst.equals("NPW")) {
      inst = "CRL";
    }
    if (inst.equals("PRO")) {
      inst = "CBG";

      // create a new 901 field with blank indicators
    }
    MarcVblLengthField f901 = outMarc.getNewVblField("901", "  ");
    f901.addSubfield("a", inst);
    if (f001.value().substring(0, 3).compareTo("ocm") == 0) {
    	f901.addSubfield("b", f001.value().substring(3, 11));
    }
    else if (f001.value().substring(0, 3).compareTo("ocn") == 0) {
    	f901.addSubfield("b", f001.value().substring(3, 12));
    }
    else {
	throw new MarcDropException("OCLC 001 field not as expected");
    }

  }

  /**
   * build marc 852 field
   *   The 852 is the holdings field.
   */
  private void build852(String inst) {
    // set new values of inst for NPW and PRO
    if (inst.equals("NPW")) { inst = "CRL"; }
    if (inst.equals("PRO")) { inst = "CBG"; }

    /**
     * the full location string from the 049 $a
     */
    String loc = null;

    // if no 049 or no $a in the 049, no 852 will be created
    // Spec. 6.2.1.2
    Field f049 = inMarc.getFirstField("049");
    if (f049 == null) {
      return;
    }
    String v049a = inMarc.getFirstValue("049", "a");
    if (v049a == null) {
      return;
    }

    // There will only be one 049 field, but it may contain multiple
    // $a's, with or without comma delimited data.
    // Spec. 6.2.2.3
    Enumeration allAs = ((Vector) ((MarcVblLengthField) f049).subfield("a")).elements();  // Promote field to variable field
    Vector uniqueLocs = new Vector();

    // Remove all comma delimited input locations and append to allAs
    while (allAs.hasMoreElements()) {

      // Break up comma delimited data first.
      // e.g.  $a 1,2,3 -> $a 1 $a 2 $a 3
      MarcSubfield allA = (MarcSubfield) allAs.nextElement();
      // Commas located in input stamps should not be interpreted as
      // delimiters.
      String allAValue = allA.value();

      // Create vector of locations.  Do not use StringTokenizer,
      // as it will fail if an Input Stamp contains a comma.
      // Must index through string. Uggh!
      boolean leftB = false;
      StringBuffer allAValueBuff = new StringBuffer();
      for (int i = 0; i < allAValue.length(); i++) {
        char charI = allAValue.charAt(i);
        if (charI == '[') { leftB = true; }
        if (charI == ']') { leftB = false; }
        if (charI == ',' && ! leftB) {
          // Reset for next location
          uniqueLocs.addElement(allAValueBuff.toString());
          allAValueBuff.delete(0, allAValueBuff.length()); 
        } else {
          allAValueBuff.append(charI);
        }
      } 
      uniqueLocs.addElement(allAValueBuff.toString());
    }

    // Create hash table to contain location codes as keys.
    // Using a hash will eliminate the work of determining
    // if the location is unique.  We will only process the
    // first location of a multiple specified location code.
    Hashtable locationHash = new Hashtable();

    // Vector allAs now contains all singular location codes
    // Iterate through allAs and process each source, creating an 852
    // field for every unique input location.
    Enumeration uniqueLocsEnum = uniqueLocs.elements();

    // logical for creating 852 $p.  Only create for the first 852 record
    boolean first852 = true;

    while (uniqueLocsEnum.hasMoreElements()) { // all locations in 049 $a

      String allA = (String) uniqueLocsEnum.nextElement();

      // Extract location from 049 $a
      String location = parse049a(allA, "loc");
      if (!locationHash.containsKey(location.toUpperCase())) {
        locationHash.put(location.toUpperCase(), "");
      }
      else {
        continue; // Already processed this location.
      }

      // Extract input stamp(s) before location code
      String inputStampPre = parse049a(allA, "pre");

      // Extract input stamp(s) after location code
      String inputStampPost = parse049a(allA, "post");

      // creation exception. If WCA and WCAL, do not create 852.
      // Spec. 6.2.2.1
      if (inst.equals("WCA") && (location.toUpperCase().indexOf("WCAL") >= 0)) {
        continue;
      }

      if (log.isDebugEnabled()) {
        log.debug("Creating new 852 field with location: " + location);
      }
      // Create blank 852 field and load subfields
      MarcVblLengthField f852 = outMarc.getNewVblField("852", "  ");

      // creation of 852 $a - institutional code
      // Spec. 6.2.2.2
      b852a(f852, inst);

      // creation of 852 $b - location code
      // Spec. 6.2.2.3
      if (location.equals("")) {
      	b852b(f852, "INVALID-LOC-DATA");		// exception that handles case 049 $a CRLL,
							// record will be marked as ERROR - "unknown location" 
      } else {
      	b852b(f852, location.toUpperCase());
      }

      // creation of 852 $c - shelving location
      // Spec. 6.2.2.4
      // null method - Not currently implemented
      b852c();

      // creation of 852 $h, $i, $j - call number
      // Spec. 6.2.2.6
      b852hij(f852, inst, location);

      // creation $k $m - call number prefix and suffix
      // Spec. 6.2.2.7 - 6.2.2.8
      b852km(f852, inst, inputStampPre, inputStampPost);

      // creation of 852 $p
      // Spec. 6.2.2.9
      if (first852) {      //build $p for 1st 852 only
        b852p(f852);
      }

      // creation of 852 $x
      // Spec. 6.2.2.10
      // null method - Not currently implemented
      b852x();

      // creation of 852 $z and $3
      // Spec. 6.2.2.11 - 6.2.2.12
      b852zs3(f852, inst, allA.toString(), first852);    // first852 needed for 852 $3 CRL check

      first852 = false;    // used for 852 $p

    }
  }

  /*
   * parse049a
   *      Extract input stamps and location from singular (no delimited commas) 049 $a. 
   *         e.g.  ("[loc][tab]CDLL [end]  [module]", "pre")  returns "loc tab"
   */
  private String parse049a(String allA, String area) {
    String returnString = "";
    RE inputStampRE = null;
    try {
       // Regular expression to handle all cases of whitespace/bracket combination.
       // Note: Brackets must be "escaped". 
       inputStampRE = new RE("^\\s*((\\[\\s*.*\\s*\\]\\s*)*)\\s*(\\w{3}.)\\s*((\\[\\s*.*\\s*\\]\\s*)*)\\s*$");    // \1:pre \4:post \3:loc
    }
    catch (RESyntaxException rese) {
      throw new MarcDropException(
          "049 $a - Regular Expression Error - input stamp" + rese);
    }
    if (inputStampRE.match(allA)) {
      if (area.equals("pre")) {
        returnString = inputStampRE.getParen(1);
      }
      else if (area.equals("post")) {
        returnString = inputStampRE.getParen(4);
      }
      else if (area.equals("loc")) {
        returnString = inputStampRE.getParen(3);
        if (returnString == "") {
          throw new MarcDropException("049 $a - Can not determine location code");
        }
      }
    } else {
        // Regular expression is not matched, output entire $a so that it will be
	// flagged as ERROR: unknowm location code.
        returnString = allA;

    }

    if (returnString != null) {
      // Remove any extra whitespace as defined in Spec. 6.2.2.8
      return compress(returnString);
    } else {
      return "";
    }
  }

  private void b852a(MarcVblLengthField f852, String inst) {
    f852.addSubfield("a", inst);
  }

  private void b852b(MarcVblLengthField f852, String location) {
    // add this loc as a $b subfield of the 852 field
    f852.addSubfield("b", location);
  }

  private void b852c() {
    // Not output for OCLC at this time.
    return;
  }

  /**
   * Call Number fields
   *      Selection of call number field and scheme depends on the institution
   *      and in some cases the location within the institution.
   */
  private void b852hij(MarcVblLengthField f852, String inst, String location) {
    // Spec 6.2.2.6.1
    if (inst.equals("CAW")) {
      if (location.equalsIgnoreCase("CAWB")) {
        // CAWB uses either the 092 or a created callno of "Unclassified"
        MarcVblLengthField f092 = (MarcVblLengthField) inMarc.getFirstField(
            "092");
        if (f092 == null) {
          f852.addSubfield("j", "Unclassified");
        }
        else {
          handle09XCallNum(f852, f092, inst, "");
        }
      }
      else {
        LCCallNumScheme(f852, inst);
      }
    }
    //if (inst.equals("CRL") || inst.equals("NPW")) {
    if (inst.equals("CRL")) {
      // CRL uses only the 099, and only for non-serials.
      if (!isSerial()) {
        MarcFieldList fields = inMarc.allFields("099");

        if (fields.size() > 0) {
          //if more than one 099, use the last one
          handle09XCallNum(f852,
                           (MarcVblLengthField) fields.elementAt(fields.size() -
              1), inst, "");
        }
      }
    }
    if (inst.equals("CUH")) {
      if (location.equalsIgnoreCase("CUHH") || location.equalsIgnoreCase("CUHS")) {
        SUDOCScheme(f852, inst);
      }
      else {
        LCCallNumScheme(f852, inst);
      }
    }
    if (inst.equals("HMF")) {
      // HMF may use $o for the $a immediately preceding it in the 049.
      // If no $o follows the $a, the NLMScheme is used.

      MarcVblLengthField f049 = (MarcVblLengthField) inMarc.getFirstField("049");
      Vector v049subs = f049.subfields();

      // Find $o preceded by $a from 049 field.
      // return empty string if none exist.
      String dollarO = sourceHMF(v049subs, location);

      if (dollarO.length() > 0) // found $a $o combination for specific location
        f852.addSubfield("j", dollarO);
      else         // extract 852's using NML scheme with the HMF exception built in.
        NLMScheme(f852, inst);
    }
    if (inst.equals("QCE")) {
      // QCE uses only the 099. If none present, no call number info output.
      MarcFieldList fields = inMarc.allFields("099");

      if (fields.size() > 0) {
        //if more than one 099, use the last one
        handle09XCallNum(f852,
                         (MarcVblLengthField) fields.elementAt(fields.size() -
            1), inst, "");
      }
    }
    else if (inst.equals("CBG") || inst.equals("CBT") || inst.equals("WCA")) {
      LCCallNumScheme(f852, inst);
    }

  }

  /**
   *  LCCallNumber Scheme
   *  prefer the 099 field if present, then the 090 field, then the 050.
   *  Use the last occurrence of the field.
   */
  private void LCCallNumScheme(MarcVblLengthField f852, String inst) {
    MarcFieldList fields;
    Vector selectionOrder = new Vector();
    selectionOrder.addElement("099");
    selectionOrder.addElement("090");
    selectionOrder.addElement("050");
    Enumeration selectionOrderEnum = selectionOrder.elements();

    while (selectionOrderEnum.hasMoreElements()) {
      String field = (String) selectionOrderEnum.nextElement();
      fields = inMarc.allFields(field);
      if (fields.size() > 0) {
        // generic handler which will determine how to correctly handle based on field
        callHandler(f852,
                    (MarcVblLengthField) fields.elementAt(fields.size() - 1),
                    inst, field);
        return;
      }
    }
  }

  /**
   *  NLM CallNumber Scheme
   *  Fields in order of selection are: 099, 096, 060, 090, 050.
   *  Use the last occurrence of the field.
   */
  private void NLMScheme(MarcVblLengthField f852, String inst) {
    MarcFieldList fields;
    Vector selectionOrder = new Vector();
    selectionOrder.addElement("099");
    selectionOrder.addElement("096");
    selectionOrder.addElement("060");
    selectionOrder.addElement("090");
    selectionOrder.addElement("050");
    Enumeration selectionOrderEnum = selectionOrder.elements();

    while (selectionOrderEnum.hasMoreElements()) {
      String field = (String) selectionOrderEnum.nextElement();
      fields = inMarc.allFields(field);
      if (fields.size() > 0) {
        // generic handler which will determine how to correctly handle based on field
        callHandler(f852,
                    (MarcVblLengthField) fields.elementAt(fields.size() - 1),
                    inst, field);
        return;
      }
    }
  }

  /**
   *  SUDOC CallNumber Scheme
   *  Fields in order of selection are: 099, 086.
   *  Use the last occurrence of the field.
   */
  private void SUDOCScheme(MarcVblLengthField f852, String inst) {
    MarcFieldList fields;
    Vector selectionOrder = new Vector();
    selectionOrder.addElement("099");
    selectionOrder.addElement("086");
    Enumeration selectionOrderEnum = selectionOrder.elements();

    while (selectionOrderEnum.hasMoreElements()) {
      String field = (String) selectionOrderEnum.nextElement();
      fields = inMarc.allFields(field);
      if (fields.size() > 0) {
        // generic handler which will determine how to correctly handle based on field
        callHandler(f852,
                    (MarcVblLengthField) fields.elementAt(fields.size() - 1),
                    inst, field);
        return;
      }
    }
  }

  // Generic handler to determine how to process call number based on the call number
  // selection scheme.
  // Spec. 6.2.2.6
  private void callHandler(MarcVblLengthField f852, MarcVblLengthField XXX,
                           String inst, String field) {

    if (field.startsWith("09")) {
      handle09XCallNum(f852, XXX, inst, field);
    }
    else if (field.startsWith("086")) {
      handle086CallNum(f852, XXX);
    }
    else {
      handle050060CallNum(f852, XXX);
    }
    return;
  }

  // define processing 090.092,096,099 fields
  // Spec. 6.2.2.6.2
  private void handle09XCallNum(MarcVblLengthField f852,
                                MarcVblLengthField f09X, String inst, String field) {
    String subfldA, subfldB, subfldX = null;

    Vector v09Xsubs = f09X.subfields();
    int size = v09Xsubs.size();
    Enumeration v09XsubsEnum = v09Xsubs.elements();
    subfldA = f09X.firstSubfieldValue("a");
    subfldB = f09X.firstSubfieldValue("b");

    // if HMF and 099 $a is available, output 852 $j
    // Spec. 6.2.2.6.1 HMF source
    if (inst.equals("HMF") && field.equals("099")) {
      if (subfldA != null) {
        f852.addSubfield("j", subfldA);
        return;
      }
      else {
        // Reject record!!!!!
      }
    }

    if (size == 1) { //only 1 sbfld; test for X or blank or null in the sbfld
      subfldX = ( (MarcSubfield) v09XsubsEnum.nextElement()).value();
      if (subfldX == null || subfldX.equals(" ") ||
          subfldX.equalsIgnoreCase("x")) {
        if (inst.equals("CAW")) {
          f852.addSubfield("j", "Unclassified");
        }
        return; //no callno info output except for CAW.
      }
    }

    if (exists(subfldA) && exists(subfldB) && size == 2) { // 1 $a and 1 $b
      f852.addSubfield("h", subfldA);
      f852.addSubfield("i", subfldB);
    }
    else if (exists(subfldA) && size == 1) { // only 1 $a
      f852.addSubfield("j", subfldA);
    }
    else if (exists(subfldB) && size == 1) { // only 1 $b
      f852.addSubfield("j", subfldB);
    }
    else {
      StringBuffer callnobuf = new StringBuffer();
      while (v09XsubsEnum.hasMoreElements()) { //loop through all subs in 09X
        // to strip out subfld delim & codes
        String subval = ((MarcSubfield) v09XsubsEnum.nextElement()).value();
        if (exists(subval)) {
          callnobuf.append(subval + " ");
        }
      }
      f852.addSubfield("j", callnobuf.toString().trim());
    }

  }

  private void handle050060CallNum(MarcVblLengthField f852,
                                   MarcVblLengthField f0X0) {
    String subfldA, subfldB = null;

    subfldA = f0X0.firstSubfieldValue("a"); // ignore all but 1st $a
    subfldB = f0X0.firstSubfieldValue("b"); // $b is not repeatable

    if (exists(subfldA) && exists(subfldB)) { // $a and $b
      f852.addSubfield("h", subfldA);
      f852.addSubfield("i", subfldB);
    }
    else if (exists(subfldA)) { // only $a
      f852.addSubfield("j", subfldA);
    }
    else if (exists(subfldB)) { // only $b
      f852.addSubfield("j", subfldB);
    }
  }

  private void handle086CallNum(MarcVblLengthField f852,
                                MarcVblLengthField f086) {
    // from the 086, use the first $a and ignore all other subflds.

    String subfldA = null;

    subfldA = f086.firstSubfieldValue("a");

    if (exists(subfldA)) { // 1st $a
      f852.addSubfield("j", subfldA);
    }

  }

  // Call Number prefix anf suffix
  // Spec. 6.2.2.7 and 6.2.2.8
  private void b852km(MarcVblLengthField f852, String inst, String inputStampPre, String inputStampPost) {

    // logical to determine if we created 852 $k
    boolean hasPre = false, hasPost = false;

    // Define check string for 852 $k
    String[] checkString = {"[f]", "[ff]", "[*]", "[**]", "[***]", "[****]", "[t]"};

    // Check prefix for 852 $k
    for (int j=0; j < checkString.length; j++) {
      if (inputStampPre.indexOf(checkString[j]) >= 0){
        f852.addSubfield("k", stripBrackets(checkString[j]));
        hasPre = true;
        break;
      }
    }

    // Check suffix for 852 $k
    if (! hasPre) {
      for (int j=0; j < checkString.length; j++) {
        if (inputStampPost.indexOf(checkString[j]) >= 0){
          f852.addSubfield("k", stripBrackets(checkString[j]));
          hasPost = true;
          break;
        }
      }
   }

   // If no 852 $k for specific Pre/Post input stamp was created,
   // create 852 $m - Spec. 6.2.2.8
   // Can create multiple $m's, one for Pre., another for Post
   String inputStampPreTmp = stripBrackets(inputStampPre);
   String inputStampPostTmp = stripBrackets(inputStampPost);

   if (inst.equals("CAW")) {     // Requires special processing
       if (inputStampPreTmp.equals("M.P.")) {
         f852.addSubfield("m", "Morrison Planetarium");
       } else if (inputStampPreTmp.equals("S.C.")) {
         f852.addSubfield("m", "Special Collections");
       } else if (inputStampPreTmp.equals("BOp")) {
         f852.addSubfield("m", "Building Operations");
       }  else if (! inputStampPreTmp.equals("")) {
         f852.addSubfield("m", inputStampPreTmp);
       }
   } else {
     if (! inputStampPreTmp.equals("") && ! hasPre)
       f852.addSubfield("m", inputStampPreTmp);
     if (! inputStampPostTmp.equals("") && ! hasPost)
       f852.addSubfield("m", inputStampPostTmp);
   }
}

  // Piece Designation
  // Only called for the first 852 in a record.
  // Spec. 6.2.2.9
  private void b852p(MarcVblLengthField f852) {
    // get the 949 field in this record
    Field v949 = inMarc.getFirstField("949");
    // if no 049 field found
    if (v949 == null) {
      return;
    }
    // get the value of the first $r subfield
    String v949r = inMarc.getFirstValue("949", "r");
    if (v949r == null) {
      return;
    }
    // add this first 949 $r as an 852 $p
    f852.addSubfield("p", v949r);
  }


  private void b852x() {
    // Not output for OCLC at this time.
    return;
  }

  /**
   * build marc 852 $z and 852 $3
   * from 590 $a and 049
   *   only $a's that will not be used for 852 $3's become 852 $z's
   * Spec. 6.2.2.11 - 6.2.2.12
   */
  private void b852zs3(MarcVblLengthField f852, String inst, String location, boolean first852) {

    boolean builds3from049 = false;
    // CAW $3 - use 049 $v, $y, $m
    // Do not use any 590
    // Note: Do not add subfield here, wait until any $z's are built
    if (inst.equals("CAW")) {
      builds3from049 = true;
    }

    // Rest of $z and $3 processing involves the 590 field
    // get all 590 fields
    MarcFieldList fields = inMarc.allFields("590");
    if (fields == null) {
      return;
    }
    MarcVblLengthField field = null;
    MarcSubfield subfield = null;
    String value = null;
    StringBuffer CRL$3 = new StringBuffer();    // used to create CRL $3 which may span many 590's
    boolean has$3 = false;                      // used for only adding to first 852 for $3 from CBG,...
    boolean has$3QCE = false;                   // used for only adding to first 852 for $3 from QCE
    boolean hasCenter = false;                  // used for CRL $3 creation
    boolean a = false, b = false, c = false, d = false;    // used again for 852 $3 CBG to keep state info.
    String trimmedValue = "";
    Vector all$3 = new Vector();


    // for each 590 field
    while (fields.hasMoreElements()) {
      // get the field from MarcFieldList
      field = (MarcVblLengthField) fields.nextElement();
      // get a $a subfield class
      subfield = field.firstSubfield("a");
      if (subfield == null) {
        continue;
      }

      // get the value of the $a class
      value = subfield.value();
      if (inst.equals("CAW")) {
        // output any 590 $a as $z; never used for 852 $3 for CAW
        f852.addSubfield("z", value);
      } else if (inst.equals("CRL")) {
        // if $a begins with "Center has", add $3 else add $z
        if (value.toLowerCase().startsWith("center has:") || hasCenter) {
          if (! hasCenter) {
            hasCenter = true;
          } else {
            // create string that will be used to add $3
            CRL$3.append(value + "+++");
          }
        } else {
          f852.addSubfield("z", value);
        }
      } else if (inst.equals("CBG") || inst.equals("CBT") || inst.equals("CUH") ||
               inst.equals("HMF") || inst.equals("WCA")) {
        //if $a begins with Library has, etc., add $3, else add $z
        if (((a = value.toLowerCase().startsWith("library has")) ||
            (b = value.toLowerCase().startsWith("lib. has")) ||
            (c = value.toLowerCase().startsWith("lib has")) ||
            (d = value.toLowerCase().startsWith("library lacks")))) {	// Process all 852 for $3. See Spec. 6.2.2.12
          if (a) {
            trimmedValue = value.substring(11).trim();
          } else if (b) {
            trimmedValue = value.substring(8).trim();
          } else if (c) {
            trimmedValue = value.substring(7).trim();
          } else {
            trimmedValue = value.trim();
          }
          if (trimmedValue.toLowerCase().startsWith(":")) {
            trimmedValue = trimmedValue.substring(1).trim();
          }
          has$3 = true;  // do not create any more $3's
         // Build $3 for CBG,CBT,CUH,HMF,WCA for EVERY 590 $a
         if (has$3 && trimmedValue != "" && first852) {
           all$3.addElement(trimmedValue);
         }
        } else {
          f852.addSubfield("z", value);
        }
      } else if (inst.equals("QCE") && ! has$3QCE) {
        //if $a begins with Lib has, continue, else add $z
        if (value.toLowerCase().startsWith("lib has")) {
          value = value.substring(8).trim();
          has$3QCE = true;  // do not create any more $3's for QCE
        } else {
          f852.addSubfield("z", value);
        }
        // Build $3 for QCE
        if (has$3QCE && value != "" && first852) {
          f852.addSubfield("3", value);
        }
      }
    }  // while (590's)
    // write CRL $3 if necessary
    if (inst.equals("CRL") && ! CRL$3.toString().equals("") && first852) {
      if (CRL$3.length() > 3) {
        CRL$3.setLength(CRL$3.length() - 3); // trim the last +++
      }
      f852.addSubfield("3", CRL$3.toString());
    }

    // Build CAW $3
    if ( builds3from049) {
      b852s3from049(f852, location);
    }

    // Build CBG, CBT, CUH, HMF, WCA $3's
    Iterator iterator = all$3.iterator();
    while (iterator.hasNext()) {
        f852.addSubfield("3", iterator.next().toString());
	iterator.remove();
    }
  }

  /**
   * build marc 852s3 from 049 field
   *      Serials holdings statement, goes in 852 $3
   *     CAW uses the $v, $y, and $m, immediately following the proper $a,
   *     in the 049 to build $3
   * [mjr] Never rewrote this method. TODO Eliminate the indexing.
   */
  private void b852s3from049(MarcVblLengthField f852, String loc) {
    Vector v049subs = new Vector();

    String subfldv = null;
    String subfldy = null;
    String subfldm = null;
    boolean foundThisLoc = false;
    MarcVblLengthField v049 = (MarcVblLengthField) inMarc.getFirstField("049");

    v049subs = v049.subfields();
    int subCount = v049subs.size();

    int x = 0, i = 0;

    for (i = 0; i <= (subCount - 1); i++) {
      if (foundThisLoc) {
        if ( ( (MarcSubfield) v049subs.elementAt(i)).tag().equals("a")) {
          break;
        }
        if ( ( (MarcSubfield) v049subs.elementAt(i)).tag().equals("v")) {
          subfldv = ( (MarcSubfield) v049subs.elementAt(i)).value();
        }
        if ( ( (MarcSubfield) v049subs.elementAt(i)).tag().equals("y")) {
          subfldy = ( (MarcSubfield) v049subs.elementAt(i)).value();
        }
        if ( ( (MarcSubfield) v049subs.elementAt(i)).tag().equals("m")) {
          subfldm = ( (MarcSubfield) v049subs.elementAt(i)).value();
        }
      }
      else if ( ( (MarcSubfield) v049subs.elementAt(i)).value().equals(loc)) {
        foundThisLoc = true;
      }
    }

    if (exists(subfldv) && exists(subfldy) && exists(subfldm)) {
      f852.addSubfield("3",
                       subfldv + ", " + subfldy + "; Library lacks: " + subfldm);

    }
    else if (exists(subfldv) && exists(subfldy)) {
      f852.addSubfield("3", subfldv + ", " + subfldy);

    }
    else if (exists(subfldv) && exists(subfldm)) {
      f852.addSubfield("3", subfldv + "; Library lacks: " + subfldm);

    }
    else if (exists(subfldy) && exists(subfldm)) {
      f852.addSubfield("3", subfldy + "; Library lacks: " + subfldm);

    }
    else if (exists(subfldv)) {
      f852.addSubfield("3", subfldv);

    }
    else if (exists(subfldy)) {
      f852.addSubfield("3", subfldy);

    }
    else if (exists(subfldm)) {
      f852.addSubfield("3", "Library lacks: " + subfldm);

    }

  }

  /**
   * Determine if the input is a serials record
   *
   * @return True if inMarc is a serials record
   */
  protected boolean isSerial() {
    String type = inMarc.getRecordType();
    String biblevel = inMarc.getBibLvl();

    if (type.equalsIgnoreCase("a") &&
        (biblevel.equalsIgnoreCase("b") || biblevel.equalsIgnoreCase("i")
		|| biblevel.equalsIgnoreCase("s"))) {
      return true;
    }
    else {
      return false;
    }
  }

  /**
   * Determine call numbers using 049 $o preceded immediately by $a subfield
   * Spec. 6.2.2.6.1 HMF source
   */
  private String sourceHMF(Vector v049subs, String location) {
    boolean hasA = false, hasO = false, found = false;
    String loc = "";
    Enumeration v049subsEnum = v049subs.elements();

    // continue until we can bind a location to a $o. it is possible
    // in a multiple location 049, the $o is not in the first occurrance.
    // This method is complicated by the fact that we will have to determine
    // if our source is the one we are testing.
    while (v049subsEnum.hasMoreElements()) {
      MarcSubfield subfld = (MarcSubfield) v049subsEnum.nextElement();
      String tag = subfld.tag();

      hasA = tag.equals("a");
      hasO = tag.equals("o");

      if (hasA) {
        if (found) {  // first $a for source is not followed by $o
          return "";
        } else {
          loc = subfld.value();
          found = (loc.indexOf(location) >= 0);  // This is our source
        }
      }
      else if (hasO && found){     // found $o after $a
        return subfld.value();
      }
      else {
       loc = "";
      }
    }
    return "";     // never found $a $o combination
  }

  /**
   * Strip off brackets for 852 $m.
   * Can not use replace() String method because an empty char literal is not legal.
   * Spec. 6.2.2.8
   */
  private String stripBrackets (String input) {
    // Strip off brackets.
    // Can not use replace() String method because an empty char literal is not legal.
    boolean outsideBracket = false;
    StringBuffer tmpPre = new StringBuffer();
    for (int i=0; i < input.length(); i++) {
      char tmpChar = input.charAt(i);
      // Logic to eliminate whitspace that in NOT contained in the input brackets, 
      // while retaining whitspace that is.
      // e.g.  "[folio olio]"     ->    852 $m folio olio 
      //       "[folio]   [olio]" ->    852 $m folio olio 
      //       "[folio][olio]"    ->    852 $m folio olio 
      if (tmpChar != '[' && tmpChar != ']') {
        if (tmpChar != ' ' || ! outsideBracket)  { tmpPre.append(tmpChar); }
      } else if (tmpChar == ']') {
        tmpPre.append(' ');
	outsideBracket = true;
      } else if (tmpChar == '[') {
	outsideBracket = false;
      }
    }
    return tmpPre.toString().trim();
  }

  /**
   * Compress multiple whitespace
   */
  // public static String compress (String a) {
    // StringBuffer b = new StringBuffer(a);
    // int loc;
    // while( (int) (loc = b.indexOf("  ")) > 0) {
      // System.out.println(b.toString());
        // b.delete(loc, loc+1);
    // }
    // return b.toString();
  // }
    /**
     * Squeeze the whitespace from a string
     */
    public static final String compress(String s)
    {
        StringTokenizer st = new StringTokenizer(s);
        StringBuffer    sb = new StringBuffer(s.length());

        while (st.hasMoreTokens())
                {
                        sb.append(st.nextToken() + " ");
                }

        return (sb.toString().trim());
    }




}
