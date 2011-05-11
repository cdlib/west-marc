package org.cdlib.util.io;

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
 * Get record counts and file size for large files. Perl chokes
 * on files over 2G, so that's why this class exists.
 *
 *
 * @author <a href="mailto:shawnm@splorkin.com">Shawn McGovern</a>
 * @version $Id: FileStat.java,v 1.3 2002/07/16 21:50:55 smcgovrn Exp $
 */

public class FileStat
{
    private static Logger log = Logger.getLogger(FileStat.class);

    private String               ifName        = null;
    private String               ofName        = null;
    private DelimitedInputStream in            = null;
    private PrintWriter          out           = null;

    private byte                 delim         = 0x0A;

    private boolean              useStdin      = false;
    private boolean              useStdout     = false;
    private boolean              inOpenStatus  = false;
    private boolean              outOpenStatus = false;


    public FileStat()
    {
        // Nothing to do but here so user may use
        // setters to setup the object.
    }

    public void setDelim(byte b)
    {
        if ( log.isDebugEnabled() )
        {
            int lh = delim >> 4;
            int rh = delim & 0x0F;
            char lc = (char)(lh < 10 ? lh + 48 : lh + 55);
            char rc = (char)(rh < 10 ? rh + 48 : rh + 55);

            log.debug("old delim = 0x" + lc + rc);
        }
        delim = b;
        if ( log.isDebugEnabled() )
        {
            int lh = delim >> 4;
            int rh = delim & 0x0F;
            char lc = (char)(lh < 10 ? lh + 48 : lh + 55);
            char rc = (char)(rh < 10 ? rh + 48 : rh + 55);

            log.debug("new delim = 0x" + lc + rc);
        }
    }

    public void printFileStats()
    {
        String statLine = getStatLine();

        log.info(statLine);
        out.println(statLine);
        out.flush();

        return;
    }

    public String getStatLine()
    {
        int  recCnt     = getRecCnt();
        long fileSize   = getFileSize();
        StringBuffer sb = new StringBuffer(500);

        sb.append("File: ");
        sb.append(ifName);
        sb.append(" records: ");
        sb.append(recCnt);
        sb.append(" size: ");
        sb.append(fileSize);

        String statLine =  sb.toString();
        return statLine;
    }


    public int getRecCnt()
    {
        int recCnt = 0;
        try
        {
            recCnt = in.getFileRecordCount();
        }
        catch (IOException e)
        {
            log.error("Trapped IOException: " + e.getMessage(), e);
        }

        return recCnt;
    }

    public long getFileSize()
    {
        return in.getFileOffNext();
    }


    public boolean open()
    {
        boolean bRet = false;

        /* Resist the urge to combine these in the return statement
         * like so: return(closein() && closeOut)
         * because if the first close fails the short circuit && operator
         * will prevent the second close from being executed.
         */
        bRet  = openIn();
        bRet &= openOut();

        return bRet;
    }


    public boolean openIn()
    {
        boolean bRet = true;
        try
        {
            if ( inOpenStatus )
            {
                closeIn();
            }

            if ( useStdin )
            {
                log.debug("Assigning in stream to STDIN");
                in = new DelimitedInputStream(new BufferedInputStream(System.in),
                                              delim);
            }
            else
            {
                log.debug("Assigning in stream to  file: " + ifName);
                in = new DelimitedInputStream(new BufferedInputStream
                                              (new FileInputStream(ifName)),
                                              delim);
            }

            inOpenStatus = true;
        }
        catch (IOException e)
        {
            log.error("Trapped IOException: " + e.getMessage(), e);
            bRet = false;
        }

        return bRet;
    }


    public boolean openOut()
    {
        boolean bRet = true;
        try
        {
            if ( outOpenStatus )
            {
                closeOut();
            }

            if ( useStdout )
            {
                log.debug("Assigning out stream to STDOUT");
                out = new PrintWriter(System.out);
            }
            else
            {
                log.debug("Assigning out stream to file: " + ofName);
                out = new PrintWriter(new FileOutputStream(ofName));
            }

            outOpenStatus = true;
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
        boolean bRet = false;

        /* Resist the urge to combine these in the return statement
         * like so: return(closein() && closeOut)
         * because if the first close fails the short circuit && operator
         * will prevent the second close from being executed.
         */
        bRet  = closeIn();
        bRet &= closeOut();

        return bRet;
    }

    public boolean closeIn()
    {
        boolean bRet = true;
        try
        {
            if ( inOpenStatus && !useStdin && in != null )
            {
                in.close();
            }
        }
        catch (IOException e)
        {
            log.error("Trapped IOException: " + e.getMessage(), e);
            bRet = false;
        }

        inOpenStatus = false;
        return bRet;
    }

    public boolean closeOut()
    {
        boolean bRet = true;

        if ( outOpenStatus && !useStdout && out != null )
        {
            out.close();
        }

        outOpenStatus = false;
        return bRet;
    }

    private boolean processArgs(String[] args)
    {
        boolean bRet = true;
        int     argc = args.length;

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
        log.info("Starting file stat");

        FileStat fs = new FileStat();

        if ( fs.processArgs(args) )
        {
            fs.setDelim((byte)0x1D);
            if ( fs.open() )
            {
                fs.printFileStats();
                fs.close();
            }
        }
    }

}
// end FileStat class
