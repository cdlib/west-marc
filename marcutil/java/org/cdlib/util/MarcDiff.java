package org.cdlib.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.GregorianCalendar;

import org.apache.log4j.Logger;
import org.cdlib.util.io.DelimitedInputStream;
import org.cdlib.util.string.F;

/**
 * MarcDiff.java
 *
 *
 *
 * @author <a href="mailto: shawnm@splorkin.com">Shawn McGovern</a>
 * @version $Id: MarcDiff.java,v 1.3 2002/07/18 00:45:52 smcgovrn Exp $
 */

public class MarcDiff
{
    private static Logger log = Logger.getLogger(MarcDiff.class);

    private static final char formFeed = 0x000C;

    private String               ifName = null;
    private String               ofName = null;
    private DelimitedInputStream in     = null;
    private PrintWriter          out    = null;

    private int pageLen   = 50;
    private int lineLen   = 129;
    private int chunkSize = 10;
    private int buffSize  = 100;

    private int pageCnt = 0;
    private int lineCnt = Integer.MAX_VALUE;

    private boolean readyToPrint = false;
    private boolean useStdin     = false;
    private boolean useStdout    = false;

    private String positionHeader = null;
    private String fileNameHeader = null;
    private String dateHeader     = null;
    private String pageHeader     = null;
    private String headerLine1    = null;

    public MarcDiff()
    {
        // Nothing to do but here so user may use
        // setters to setup the object.
    }

    public void printFile()
    {
        int      bytesRead = 0;
        byte[]   fbuff = new byte[buffSize];
        char[][] xbuff = null;
        lineCnt = pageLen + 1;

        setHeaderLines();

        log.debug("buffSize = " + buffSize + " fbuff.len = " + fbuff.length);

        try
        {
            while ( (bytesRead = in.read(fbuff, 0, buffSize)) > 0 )
            {
                log.debug("Read " + bytesRead + " bytes");

                xbuff = HexUtils.hexPrintChar2(fbuff);
                printLines(formatLines(fbuff, xbuff, bytesRead));
            }
            log.debug("Final Read: read " + bytesRead + " bytes");
        }
        catch (IOException e)
        {
            log.error("Trapped IOException: " + e.getMessage(), e);
        }

        return;
    }


    public void setHeaderLines()
    {
        positionHeader = getPositionHeader(buffSize, chunkSize);
        fileNameHeader = "File: " + ifName;
        dateHeader     = "Date: " + getPrintDateTime();
        pageHeader     = "Page: ";
        headerLine1    = buildHeaderLine(fileNameHeader, dateHeader, pageHeader, lineLen - 5);
    }


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

        sb.append(left);
        for ( int i = 0; i < lGap; i++)
        {
            sb.append(' ');
        }

        sb.append(center);
        for ( int i = 0; i < rGap; i++)
        {
            sb.append(' ');
        }

        sb.append(right);

        return sb.toString();
    }


    public String getPositionHeader(int buffSize, int chunkSize)
    {
        StringBuffer sb = new StringBuffer(Math.max(lineLen, buffSize + 12));

        sb.append("Position:  ");
        for ( int pos = 1; pos < buffSize; pos += chunkSize )
        {
            sb.append(F.f(pos, chunkSize, F.LJ));
            sb.append("  ");
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


    public void printHeaders()
    {
        pageCnt++;
        out.println(headerLine1 + F.f(pageCnt, 5, F.RJ));
        out.println();
        out.println(positionHeader);
        out.println();
        lineCnt += 4;
    }


    public void printLines(String[] lines)
    {
        int max = lines.length;
        boolean newRec = in.getStartOfRecordStatus();

        if ( in.getStartOfRecordStatus() )
        {
            if ( lineCnt + max + 2 > pageLen )
            {
                out.println(formFeed);
                lineCnt = 0;
                printHeaders();
            }
            out.println("Record: " + F.f(in.getRecNbr(), 8));
            out.println();
            lineCnt += 2;
        }
        else
        {
            if ( lineCnt + max > pageLen )
            {
                out.println(formFeed);
                lineCnt = 0;
                printHeaders();
            }
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


    public String[] formatLines(byte[] fbuff, char[][] xbuff, int limit)
    {
        log.debug("limit = " + limit);

        String[] lines = new String[3];
        int sbSize = Math.max(lineLen, 2 * limit);
        StringBuffer sb = new StringBuffer(sbSize);
        int endChunk  = chunkSize;

        sb.append(getCLinePrefix());
        for ( int i = 0; i < limit; i++)
        {
            if ( i >= endChunk )
            {
                sb.append(' ');
                sb.append(' ');
                endChunk += chunkSize;
            }
            char c = (char)fbuff[i];
            sb.append((c < 0x0020 ? '.' : c));
            //log.debug("byte = " + (char)fbuff[i]);
        }
        lines[0] = sb.toString();

        sb.delete(0, sbSize);
        endChunk = chunkSize;
        sb.append(getX1LinePrefix());
        for ( int i = 0; i < limit; i++)
        {
            if ( i >= endChunk )
            {
                sb.append(' ');
                sb.append(' ');
                endChunk += chunkSize;
            }
            sb.append((char)(xbuff[0][i]));
            //log.debug("char = " + xbuff[0][i]);
        }
        lines[1] = sb.toString();

        sb.delete(0, sbSize);
        endChunk = chunkSize;
        sb.append(getX2LinePrefix());
        for ( int i = 0; i < limit; i++)
        {
            if ( i >= endChunk )
            {
                sb.append(' ');
                sb.append(' ');
                endChunk += chunkSize;
            }
            sb.append((char)(xbuff[1][i]));
            //log.debug("char = " + xbuff[0][i]);
        }
        lines[2] = sb.toString();

        return lines;
    }


    public String getCLinePrefix()
    {
        StringBuffer sb = new StringBuffer(20);

        sb.append("C  ");
        sb.append((in.getStartOfRecordStatus() ? '*' : ' '));
        sb.append(F.f((in.getRecOffPrev() + 1), 5, F.RJ));
        sb.append("  ");

        return sb.toString();
    }


    public String getX1LinePrefix()
    {
        StringBuffer sb = new StringBuffer(20);

        sb.append("X1 ");
        sb.append((in.getStartOfRecordStatus() ? '*' : ' '));
        sb.append(F.f((in.getRecOffPrev() + 1), 5, F.RJ));
        sb.append("  ");

        return sb.toString();
    }

    public String getX2LinePrefix()
    {
        StringBuffer sb = new StringBuffer(20);

        sb.append("X2 ");
        sb.append((in.getStartOfRecordStatus() ? '*' : ' '));
        sb.append(F.f((in.getRecOffPrev() + 1), 5, F.RJ));
        sb.append("  ");

        return sb.toString();
    }

    public boolean open()
    {
        boolean bRet = true;
        try
        {
            if ( useStdin )
            {
                in = new DelimitedInputStream(new BufferedInputStream(System.in),
                                              (byte)0x1D);
            }
            else
            {
                in = new DelimitedInputStream(new BufferedInputStream
                                              (new FileInputStream(ifName)),
                                              (byte)0x1D);
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
            ifName = "STDIN";
            ofName = "STDOUT";
            break;

        case 1:
            log.info("One arguments - using arg #1 as input file and STDOUT");
            useStdin = false;
            useStdout = true;
            ifName = args[0];
            ofName = "STDOUT";
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

        MarcDiff hp = new MarcDiff();

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
// end MarcDiff class
