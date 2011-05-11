package org.cdlib.util.io;

import java.io.FilterInputStream ;
import java.io.InputStream ;
import java.io.IOException ;

import org.apache.log4j.Logger;

/**
 * An input stream for files with non-standard line ends,
 * e.g. Marc format files. Set the record delimiter prior
 * to reading through the file.
 *
 * A convenience method to get the number of records in the file
 * is provided.
 *
 * @author <a href="mailto:shawnm@splorkin.com">Shawn McGovern</a>
 * @version $Id: DelimitedInputStream.java,v 1.3 2002/07/16 21:51:06 smcgovrn Exp $
 */

public class DelimitedInputStream extends java.io.FilterInputStream
{
    private static Logger log = Logger.getLogger(DelimitedInputStream.class);

    protected byte    delim               = 0x0A;  // default delimiter is '\n'
    protected byte[]  buff                = null;
    protected int     bLen                = 0;

    protected boolean readComplete        = false; // internal

    protected boolean startOfRecordStatus = true;  // queryable
    protected boolean endOfRecordStatus   = true;  // queryable
    protected boolean endOfFileStatus     = false; // queryable

    protected int     recNbr              = 0;     // queryable
    protected int     recOffPrev          = 0;     // queryable
    protected int     recOffNext          = 0;     // queryable
    protected long    fileOffPrev         = 0;     // queryable
    protected long    fileOffNext         = 0;     // queryable

    public DelimitedInputStream(InputStream is)
    {
        super(is);
    }

    public DelimitedInputStream(InputStream is, byte delimiter)
    {
        this(is);
        delim = delimiter;
    }

    public DelimitedInputStream(InputStream is, char delimiter)
    {
        this(is);
        delim = (byte)delimiter;
    }

    /**
     * Get the current delimiter.
     * @return value of delim
     */
    public byte getDelim()
    {
        return delim;
    }

    /**
     * Set the delimiter byte.
     * @param b the new delimiter value
     */
    public void setDelim(byte b)
    {
        delim = b;
    }

    /**
     * Set the delimiter byte from a character.
     * @param c the new delimiter value - only the low byte is used
     */
    public void setDelim(char c)
    {
        delim = (byte)(c & 0x00FF);
    }

    /**
     * Get the start of record status.
     * @return value of startOfRecordStatus
     */
    public boolean getStartOfRecordStatus()
    {
        return startOfRecordStatus;
    }

    /**
     * Get the end of record status.
     * @return value of endOfRecordStatus
     */
    public boolean getEndOfRecordStatus()
    {
        return endOfRecordStatus;
    }

    /**
     * Get the end of file status.
     * @return value of endOfFileStatus
     */
    public boolean getEndOfFileStatus()
    {
        return endOfFileStatus;
    }

    /**
     * Get the current record number.
     * @return value of recNbr
     */
    public int getRecNbr()
    {
        return recNbr;
    }

    /**
     * Get the record offset of the previous read.
     * @return value of recOffPrev
     */
    public int getRecOffPrev()
    {
        return recOffPrev;
    }

    /**
     * Get the record offset of the next read.
     * @return value of recOffNext
     */
    public int getRecOffNext()
    {
        return recOffNext;
    }
    /**
     * Get the file offset of the previous read.
     * @return value of fileOffPrev
     */
    public long getFileOffPrev()
    {
        return fileOffPrev;
    }

    /**
     * Get the file offset of the next read.
     * @return value of fileOffNext
     */
    public long getFileOffNext()
    {
        return fileOffNext;
    }


    /**
     * Read bytes from the underlying stream into the supplied byte array.
     *
     * @param  ubuff the byte array to receive the bytes
     *
     * @return the number of byte returned in the byte array, or -1 if end
     *         of file is reached.
     *
     * @throws java.io.IOExeption if the underlying <code>InputStream</code>
     *         throws such an exception
     * @throws java.lang.NullPointerException if the user supplied buffer is null
     */
    public int read(byte[] ubuff)
        throws IOException
    {
        return this.read(ubuff, 0, ubuff.length);
    }


    /**
     * Read bytes from the underlying stream into the supplied byte array,
     * starting at the specifed offset, up to a maximum of the specified length.
     *
     * @param  ubuff the byte array to receive the bytes
     * @param  off   the offset in the array to begin storing data
     * @param  len   the maximum number of bytes to store
     *
     * @return the number of byte returned in the byte array, or -1 if end
     *         of file is reached.
     *
     * @throws java.io.IOExeption if the underlying <code>InputStream</code>
     *         throws such an exception
     * @throws java.lang.NullPointerException if the user supplied buffer is null
     * @throws java.lang.IndexOutOfBoundsException if and of the following are true:<br>
     *         1. offset is less than zero
     *         2. length is less than zero
     *         3. buffer length is less than the total of offset and length
     */
    public int read(byte[] ubuff, int off, int len)
        throws IOException
    {
        /*
         * Perform a sanity check on the user supplied parameters.
         * Immediately throw an exception if the parameter are bogus,
         * rather that wait for the side effects of said bogosity to
         * cause problems.
         */
        if ( ubuff == null )
        {
            throw new NullPointerException();
        }

        if ( off < 0 || len < 0 || off + len > ubuff.length )
        {
            throw new IndexOutOfBoundsException();
        }

        /*
         * Check if we are already at end of file and if so return immediately.
         */
        if ( endOfFileStatus )
        {
            return -1;
        }

        readComplete = false;

        if ( startOfRecordStatus )
        {
            startOfRecordStatus = false;
        }

        if ( endOfRecordStatus )
        {
            startOfRecordStatus = true;
            endOfRecordStatus   = false;
            recOffPrev          = 0;
            recOffNext          = 0;
            recNbr++;
        }

        if ( log.isDebugEnabled() )
        {
            log.debug("start of read: recNbr = " + recNbr
                      + " startOfRecordStatus = " + (startOfRecordStatus ? "true" : "false")
                      + " recOffPrev = " + recOffPrev + " recOffNext = " + recOffNext
                      + " fileOffPrev = " + fileOffPrev + " fileOffNext = " + fileOffNext
                      + " endOfRecordStatus = " + (endOfRecordStatus ? "true" : "false"));
        }

        int iRet = 0;

        if ( log.isDebugEnabled() )
        {
            log.debug("start of read: iRet = " + iRet + " ubuff.len = " + ubuff.length
                      + " off = " + off + " len = " + len);
        }

        recOffPrev  = recOffNext;
        fileOffPrev = fileOffNext;

        int sCnt = setUserBytes(ubuff, off, len);
        int rCnt = 0;

        if ( log.isDebugEnabled() )
        {
            log.debug("read part1: sCnt = " + sCnt + " readComplete = "
                      + (readComplete ? "true" : "false"));
        }

        if ( readComplete )
        {
            iRet = sCnt;
            if ( log.isDebugEnabled() )
            {
                log.debug("read completed from buffer - return byte count = " + iRet);
            }
        }
        else
        {
            iRet = sCnt;
            off += sCnt;
            len -= sCnt;
            buff = new byte[len];

            if ( log.isDebugEnabled() )
            {
                log.debug("pre super.read: iRet = " + iRet + " off = " + off + " len = " + len);
            }

            rCnt = super.read(buff, 0, len);
            bLen = rCnt;

            if ( log.isDebugEnabled() )
            {
                log.debug("post super.read: byte count = " + rCnt
                          + " iRet = " + iRet + " bLen = " + bLen);
            }

            switch ( rCnt )
            {
            case -1:
                if ( sCnt == 0 )
                {
                    iRet = rCnt;
                }
                break;

            case 0:
                break;

            default:
                sCnt = setUserBytes(ubuff, off, len);

                if ( log.isDebugEnabled() )
                {
                    log.debug("read part2: sCnt = " + sCnt + " readComplete = "
                              + (readComplete ? "true" : "false") + " iRet = " + iRet);
                }

                iRet += sCnt;
                break;
            }
        }

        if ( iRet > -1 )
        {
            recOffNext  += iRet;
            fileOffNext += iRet;
        }

        if ( log.isDebugEnabled() )
        {
            log.debug("end of read: recNbr = " + recNbr
                      + " startOfRecordStatus = " + (startOfRecordStatus ? "true" : "false")
                      + " recOffPrev = " + recOffPrev + " recOffNext = " + recOffNext
                      + " fileOffPrev = " + fileOffPrev + " fileOffNext = " + fileOffNext
                      + " endOfRecordStatus = " + (endOfRecordStatus ? "true" : "false"));
        }

        return iRet;
    }


    private int setUserBytes(byte[] ubuff)
    {
        return setUserBytes(ubuff, 0, ubuff.length);
    }


    private int setUserBytes(byte[] ubuff, int off, int len)
    {
        if ( ubuff == null )
        {
            throw new NullPointerException();
        }

        if ( off < 0 || len < 0 || off + len > ubuff.length )
        {
            throw new IndexOutOfBoundsException();
        }

        int iRet = 0;
        //int bLen = (buff == null ? 0 : buff.length);
        if ( log.isDebugEnabled() )
        {
            log.debug("bLen = " + bLen+ " ubuff.len = " + ubuff.length
                      + " off = " + off + " len = " + len);
        }

        if ( bLen > 0 )
        {
            int i = 0;
            int j = off;
            int umax = off + len;

            while ( i < bLen && j < umax )
            {
                iRet++;
                if ( (ubuff[j++] = buff[i++]) == delim )
                {
                    readComplete = true;
                    endOfRecordStatus = true;
                    break;
                }
            }

            int remain = bLen - i;
            if ( remain > 0 )
            {
                byte[] temp = new byte[remain];
                System.arraycopy(buff, i, temp, 0, remain);
                buff = temp;
                bLen = remain;
                readComplete = true;
            }
            else
            {
                buff = null;
                bLen = 0;
            }

            if ( j >= umax )
            {
                readComplete = true;
            }
        }

        return iRet;
    }

    /**
     * This is a convenience method to retrieve the count of records in
     * a file. It will quickly read through the file incrementing the
     * record count each time a delimeter is found. As a side effect the
     * the file will be at end of file when this method completes.
     * This method will work correctly when invoked after other reads
     * have taken place, but again, it will leave the input stream at
     * end of file.<br><br>
     * @return the number of records in the underlying input stream
     * @throws java.io.IOExeption if the underlying <code>InputStream</code>
     *         throws such an exception
     */
    public int getFileRecordCount()
        throws IOException
    {
        //log.debug("delim = 0x" + (delim >> 4) + (delim & (byte)0x0F));
        if ( log.isDebugEnabled() )
        {
            int lh = delim >> 4;
            int rh = delim & 0x0F;
            char lc = (char)(lh < 10 ? lh + 48 : lh + 55);
            char rc = (char)(rh < 10 ? rh + 48 : rh + 55);

            log.debug("delim = 0x" + lc + rc);
        }

        /*
         * if a buffer from a prior read is extant
         * exists, scan that first, before processing the input stream.
         */
        if ( buff != null && bLen > 0 )
        {
            if ( log.isDebugEnabled() )
            {
                log.debug("processing extant buffer first - bLen = " + bLen);
            }

            fileOffPrev = fileOffNext;
            fileOffNext += bLen;
            for ( int i = 0; i < bLen; i++ )
            {
                if ( buff[i] == delim )
                {
                    recNbr++;
                    if ( log.isDebugEnabled() )
                    {
                        log.debug("found end of record #" + recNbr);
                    }
                }
            }
        }

        buff = new byte[2000];
        bLen = 0;

        while ( (bLen = super.read(buff, 0, 2000)) > -1 )
        {
            if ( log.isDebugEnabled() )
            {
                log.debug("processing buffer - bLen = " + bLen);
            }

            fileOffPrev = fileOffNext;
            fileOffNext += bLen;
            for ( int i = 0; i < bLen; i++ )
            {
                if ( buff[i] == delim )
                {
                    recNbr++;
                    if ( log.isDebugEnabled() )
                    {
                        log.debug("found end of record #" + recNbr);
                    }
                }
            }
        }

        endOfFileStatus = true;
        log.debug("returning rec count = " + recNbr);

        return recNbr;
    }

}
// end of DelimitedInputStream class
