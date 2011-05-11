package org.cdlib.util.cc;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.regexp.RE;
import org.apache.regexp.RESyntaxException;

/**
 * SimpleCommandLineParser.java
 *
 *
 *
 * @author <a href="mailto:shawnm@splorkin.com">Shawn McGovern</a>
 * @version $Id: SimpleCommandLineParser.java,v 1.2 2002/09/30 01:34:23 smcgovrn Exp $
 */

public class SimpleCommandLineParser
{
    private static Logger log = Logger.getLogger(SimpleCommandLineParser.class);

	private SimpleCommandLineParser()
	{
        // This class contains only static methods, so the contructor is private.
	}


    public static HashMap parseArgs(String[] args)
    {
        HashMap config   = new HashMap();
		int     argsSize = 0;
		int     start    = 0;
		int     optSize  = 0;
		int     valSize  = 0;

		if ( args != null
			 && (argsSize = args.length) > 0
			 && start >= 0 )
		{
			while ( start < argsSize )
			{
				String[] optEntry = getNextOptEntry(args, start);
				if ( optEntry == null || (optSize = optEntry.length) == 0 )
				{
					break;
				}
				else
				{
					String optName = optEntry[0];
					start += (optName == null ? optSize - 1 : optSize);
					String[] values = null;
					valSize = optSize - 1;
					if ( valSize > 0)
					{
						values = new String[valSize];
						int o = 1;
						int v = 0;
						while (  v < valSize )
						{
							values[v++] = optEntry[o++];
						}
					}

					if ( log.isDebugEnabled() )
					{
						StringBuffer sb = new StringBuffer(100);
						sb.append("adding option: Name = ");
						if ( optName == null)
						{
							sb.append(" null");
						}
						else
						{
							sb.append(optName);
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

					config.put(optName, values);
				}
			}
		}

        return config;
    }

    /**
     * Get the next option entry. An option entry is an option name
     * and the associate option value. An option name can have either
     * a short form, e.g -h, or a long form, e.g. --help. An option
     * may have a value associated with it, or may not, as in the
     * case of --help. An option value may consist of more than one
     * item, e.g. --infiles foo bar baz.
     *
     * This method looks for an option name at the top of the list
     * and assumes that only the next list item following it is the
     * value associated with that entry. If the the next entry is
	 * an option name then there is no value.
     *
     * If the first item in the args list is not an option name
     * it pops values off the list until it finds an option, or
     * reaches the end of the list, and returns those as values,
     * and places a null for the option name.
     *
     * The processed list entries are removed from the input list
     * and returned in a separate <code>String</code> array.
     *
     * @param args the array of arguments to search
	 *
     * @return the option entry as a <code>String</code> array,
	 *         of which the first entry is the option name, or
	 *         <code>null</code> if there are only values.
     */
    private static String[] getNextOptEntry(String[] args, int start)
    {
        String[] argEntry = null;
        String[] argsLeft = null;
        boolean  gotOptName = false;
        String   optName = null;
		int      optSize = 0;
		int      argsSize = 0;

        try
        {
            RE longOptRE = new RE("^--(.+)$"); // match a long option
            RE shortOptRE = new RE("^-(.)$");  // match a short option

            if ( args != null
				 && (argsSize = args.length) > 0
				 && start >= 0
				 && start < argsSize )
            {
				int ai = start; // initialize the args index

				if ( log.isDebugEnabled() )
				{
					log.debug("argsSize = " + argsSize + " start = " + start);
				}

                if ( longOptRE.match(args[ai]) )
                {
					ai++;
                    gotOptName = true;
                    optName = longOptRE.getParen(1);

					if ( log.isDebugEnabled() )
					{
						log.debug("got long match on element[" + ai + "] = " + args[ai]
								  + " opt = " + optName);
					}
                }
                else if ( shortOptRE.match(args[ai]) )
                {
					ai++;
                    gotOptName = true;
                    optName = shortOptRE.getParen(1);

					if ( log.isDebugEnabled() )
					{
						log.debug("got short match on element[" + ai + "] = " + args[ai]
								  + " opt = " + optName);
					}
                }
				optSize = 1;

				if ( gotOptName )
				{
					if ( ai < argsSize
						 && !longOptRE.match(args[ai])
						 && !shortOptRE.match(args[ai]) )
					{
						optSize++;
						ai++;
					}

				}
				else
				{
					while ( ai < argsSize
							&& !longOptRE.match(args[ai])
							&& !shortOptRE.match(args[ai]) )
					{
						optSize++;
						ai++;
					}

				}

				if ( optSize > 0)
				{
					argEntry = new String[optSize];
				}

				if ( gotOptName )
				{
					argEntry[0] = optName;
				}
				else
				{
					argEntry[0] = null;
				}

				int i = 1;
				int j = (gotOptName ? start + 1 : start);
				if ( log.isDebugEnabled() )
				{
					log.debug("adding values: optSize = " + optSize
							  + "  i = " + i + "  j = " + j);
				}

				while ( i < optSize )
				{
					if ( log.isDebugEnabled() )
					{
						log.debug("adding argEntry[" + i + "] from "
								  + "args[" + j + "] = " + args[j]);
					}
					argEntry[i++] = args[j++];
				}
            }

			if ( log.isDebugEnabled() )
			{
				StringBuffer sb = new StringBuffer(100);
				sb.append("Option Entry:");
				if ( argEntry == null)
				{
					sb.append(" null");
				}
				else
				{
					sb.append(" Name = ");
					sb.append(argEntry[0]);
					for ( int k = 1; k < optSize; k++ )
					{
						sb.append(" Value[");
						sb.append(k);
						sb.append("] = '");
						sb.append(argEntry[k]);
						sb.append("'");
					}
				}
				log.debug(sb.toString());
			}
        }
        catch (RESyntaxException e)
        {
            log.error("Regex error: " + e.getMessage(), e);
        }


        return argEntry;
    }



    public static void main(String[] args)
    {
        System.out.println("Started SimpleCommandLineParser: arg count = " + args.length);

		HashMap opts = parseArgs(args);

        if ( opts == null )
        {
            System.out.println("Option HashMap is null");
        }
        else
        {
            System.out.println("Dumping Option Hashmap:");
            Set optEntries = opts.entrySet();
            if ( optEntries.size() == 0)
            {
                System.out.println("  option map is empty");
            }
            else
            {
                System.out.println("  option map has " + optEntries.size() + " entries");
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
                    System.out.println(sb.toString());
                }
            }
        }
    }
	// end of main
}
// SimpleCommandLineParser class
