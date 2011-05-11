package org.cdlib.util.rpt;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintWriter;
import org.apache.log4j.Logger;
import org.cdlib.util.HexUtils;

/**
 * Hex print a file, or standard input, in a three line character
 * over /under hex format.
 *
 *
 * @author <a href="mailto:shawnm@splorkin.com">Shawn McGovern</a>
 * @version $Id: HexPrintFile.java,v 1.4 2002/10/22 21:28:08 smcgovrn Exp $
 */
public class HexPrintFile
{
    private static Logger log = Logger.getLogger(HexPrintFile.class);

    private static final char formFeed = 0x000C;

    private String ifName = null;
    private String ofName = null;
    private BufferedInputStream in = null;
    private PrintWriter out = null;

    private int pageLen = 40;
    private int blockLen = 10;
    private int lineLen = 120;
    private int buffSize = 100;

    private int pageCnt = 0;
    private int lineCnt = Integer.MAX_VALUE;

    private boolean readyToPrint = false;
    private boolean useStdin = false;
    private boolean useStdout = false;

    /**
     * Instatiate a new HexPrintFile object. Nothing is done here,
     * the caller just gets a reference the makes the public methods
     * available.
     */
    public HexPrintFile()
    {
        // Nothing to do but here so user may use
        // setters to setup the object.
    }


    /**
     * Read the input file, format the data for hex output and print
     * it to the output stream. Hex formatting is done by calling the
     * hexPrintChar2 method of the HexUtils class.
     *
     * This method expects that input and output files have already
     * been opened by a call to the open() method.
     *
     * @see org.cdlib.util.HexUtils#hexPrintChar2
     */
    public void printFile()
    {
        int      bytesRead = 0;
        byte[]   fbuff = new byte[buffSize];
        char[][] xbuff = null;
        lineCnt = pageLen + 1;

        try
        {
            while ( (bytesRead = in.read(fbuff, 0, buffSize)) > 0 )
            {
                log.debug("Read " + bytesRead + " bytes");

                xbuff = HexUtils.hexPrintChar2(fbuff);
                printLines(formatLines(fbuff, xbuff, bytesRead));
            }
        }
        catch (IOException e)
        {
            log.error("Trapped IOException: " + e.getMessage(), e);
        }

        return;
    }

    /**
     * Format the lines for printing. The results
     */
    public String[] formatLines(byte[] fbuff, char[][] xbuff, int limit)
    {
        log.debug("limit = " + limit);

        String[] lines = new String[3];
        int sbSize = 2 * limit;
        StringBuffer sb = new StringBuffer(sbSize);
        int start = 0;
        int inc   = 10;
        int end   = start + 10;

        for ( int i = 0; i < limit; i++)
        {
            if ( i >= end )
            {
                sb.append(' ');
                sb.append(' ');
                end += inc;
            }
            sb.append((char)(fbuff[i]));
            log.debug("byte = " + (char)fbuff[i]);
        }
        lines[0] = sb.toString();

        sb.delete(0, sbSize);
        start = 0;
        end = start + 10;
        for ( int i = 0; i < limit; i++)
        {
            if ( i >= end )
            {
                sb.append(' ');
                sb.append(' ');
                end += inc;
            }
            sb.append((char)(xbuff[0][i]));
            log.debug("char = " + xbuff[0][i]);
        }
        lines[1] = sb.toString();

        sb.delete(0, sbSize);
        start = 0;
        end = start + 10;
        for ( int i = 0; i < limit; i++)
        {
            if ( i >= end )
            {
                sb.append(' ');
                sb.append(' ');
                end += inc;
            }
            sb.append((char)(xbuff[1][i]));
            log.debug("char = " + xbuff[0][i]);
        }
        lines[2] = sb.toString();

        return lines;
    }


    public void printLines(String[] lines)
    {
        int max = lines.length;

        if ( lineCnt + max > pageLen )
        {
            out.println(formFeed);
            lineCnt = 0;
        }

        for ( int i = 0; i < max; i++)
        {
            out.println(lines[i]);
            lineCnt++;
        }

        if ( lineCnt < pageLen)
        {
            out.println();
            lineCnt++;
        }
        out.flush();
        log.debug("lineCnt = " + lineCnt);

        return;
    }

    public boolean open()
    {
        boolean bRet = true;
        try
        {
            if ( useStdin )
            {
                in = new BufferedInputStream(System.in);
            }
            else
            {
                in = new BufferedInputStream(new FileInputStream(ifName));
            }

            if ( useStdout )
            {
                out = new PrintWriter(System.out);
            }
            else
            {
                out = new PrintWriter(new FileOutputStream(ofName));
            }
        }
        catch (IOException e)
        {
            log.error("Trapped IOException: " + e.getMessage(), e);
            bRet = false;
        }

        return bRet;
    }

    public boolean close()
    {
        boolean bRet = true;
        return bRet;
    }

    private boolean processArgs(String[] args)
    {
        boolean bRet = true;
        int argc = args.length;
        switch ( argc )
        {
        case 0:
            log.info("No arguments - using STDIN and STDOUT");
            useStdin = true;
            useStdout = true;
            break;

        case 1:
            log.info("One arguments - using arg #1 as input file and STDOUT");
            useStdin = true;
            useStdout = false;
            ifName = args[0];
            break;

        default:
            log.info("Two arguments - using arg #1 as input file and arg #2 as output file");
            useStdin = false;
            useStdout = false;
            ifName = args[0];
            ofName = args[1];
            break;
        }

        return bRet;
    }

    public static void main(String[] args)
    {
        log.info("Starting hex print");

        HexPrintFile hp = new HexPrintFile();

        if (hp.processArgs(args))
        {
            if (hp.open())
            {
                hp.printFile();
                hp.close();
            }
        }
    }

}
// end HexPrintFile class
