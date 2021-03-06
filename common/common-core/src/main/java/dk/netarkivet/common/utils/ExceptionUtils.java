/*
 * #%L
 * Netarchivesuite - common
 * %%
 * Copyright (C) 2005 - 2018 The Royal Danish Library, 
 *             the National Library of France and the Austrian National Library.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */
package dk.netarkivet.common.utils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.sql.SQLException;

import dk.netarkivet.common.exceptions.ArgumentNotValid;

/**
 * Utilities for reading a stacktrace.
 */
public class ExceptionUtils {

    /**
     * Utility class, do not instantiate.
     */
    private ExceptionUtils() {
    }

    /**
     * Prints the stacktrace of an exception to a String. Why this functionality is not included in the standard java
     * libraries is anybody's guess.
     *
     * @param aThrowable An exception
     * @return String containing a stacktrace of exception aThrowable. Will return the string "null" and a linebreak if
     * aThrowable is null.
     */
    public static String getStackTrace(Throwable aThrowable) {
        final Writer result = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(result);
        if (aThrowable != null) {
            aThrowable.printStackTrace(printWriter);
        } else {
            printWriter.println("null");
        }
        return result.toString();
    }

    /**
     * SQLExceptions have their own stack of causes accessed via the getNextException() method. This utility provides a
     * string representation of those causes for use in logging or rethrowing
     *
     * @param e the original top-level exception
     * @return a String describing the exception
     */
    public static String getSQLExceptionCause(SQLException e) {
        ArgumentNotValid.checkNotNull(e, "SQLException");
        StringBuffer message = new StringBuffer("SQLException trace:\n");
        do {
            message.append(getSingleSQLExceptionCause(e));
            e = e.getNextException();
            if (e != null) {
                message.append("NextException:\n");
            }
        } while (e != null);
        message.append("End of SQLException trace");
        return message.toString();
    }

    private static StringBuffer getSingleSQLExceptionCause(SQLException e) {
        StringBuffer message = new StringBuffer();
        message.append("SQL State:").append(e.getSQLState()).append("\n");
        message.append("Error Code:").append(e.getErrorCode()).append("\n");
        StringWriter string_writer = new StringWriter();
        PrintWriter writer = new PrintWriter(string_writer);
        e.printStackTrace(writer);
        message.append(string_writer.getBuffer());
        return message;
    }

    public static Throwable getRootCause(Throwable crawlException) {
        return org.apache.commons.lang.exception.ExceptionUtils.getRootCause(crawlException);
    }

}
