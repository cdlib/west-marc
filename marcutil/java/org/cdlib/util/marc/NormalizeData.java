/**
 * @author pdoshi
 */
package org.cdlib.util.marc;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.StringTokenizer;



public class NormalizeData {

	/*
	 * This method is removing Control characters and its leading whitespaces
	 * with only one whitespace.
	 */
	
	public static String removeControlCharacters(String str) {

		String val = "";
		if ((str == null) || (str.length() == 0)) {
			return val;
		}

		StringTokenizer stl = new StringTokenizer(str, " ", true);
		StringBuilder sb = new StringBuilder();

		while (stl.hasMoreTokens()) {
			char[] buff = stl.nextToken().toCharArray();

			for (char c : buff) {

				if (Character.isISOControl(c))
					continue;

				sb.append(c);

			}

		}

		val = sb.toString();
		val = normalizeWhiteSpace(val);
		return val;
	}


    /**
     * Normalize white space in a string
     *
     * Replace multiple Java whitespace characters with a single space,
     * trim leading and trailing spaces. This method will normalize a
     * single space to an empty string.
     *
     * @param str the original string
     * @return the normalized string (or null if String was null)
     */
    public static String normalizeWhiteSpace(String str) {
        
        if (str == null) {
            return str;
        }
        else {
            // Replace multiple white spaces with single space
            str = str.replaceAll("\\p{javaWhitespace}+", " ");
            // Remove leading and trailing whitespace
            return str.trim();
        }
    }
}
