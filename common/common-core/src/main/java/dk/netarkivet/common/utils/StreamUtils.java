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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;

import javax.servlet.jsp.JspWriter;

import org.dom4j.Document;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.netarkivet.common.Constants;
import dk.netarkivet.common.exceptions.ArgumentNotValid;
import dk.netarkivet.common.exceptions.IOFailure;

/**
 * Utilities for handling streams.
 */
public class StreamUtils {

    /** logger for this class. */
    private static final Logger log = LoggerFactory.getLogger(StreamUtils.class);

    /** Constant for UTF-8. */
    private static final String UTF8_CHARSET = "UTF-8";

    /**
     * Will copy everything from input stream to jsp writer, closing input stream afterwards. Charset UTF-8 is assumed.
     *
     * @param in InputStream to copy from
     * @param out JspWriter to copy to
     * @throws ArgumentNotValid if either parameter is null
     * @throws IOFailure if a read or write error happens during copy
     */
    public static void copyInputStreamToJspWriter(InputStream in, JspWriter out) {
        ArgumentNotValid.checkNotNull(in, "InputStream in");
        ArgumentNotValid.checkNotNull(out, "JspWriter out");

        byte[] buf = new byte[Constants.IO_BUFFER_SIZE];
        int read = 0;
        try {
            try {
                while ((read = in.read(buf)) != -1) {
                    String s = new String(buf, UTF8_CHARSET);
                    if (s.length() < read) {
                        log.debug("String length ({}) < bytes read({})", s.length(), read);
                        out.write(s, 0, s.length());
                    } else {
                        out.write(s, 0, read);
                    }
                    // Reinitializing the buffer to avoid garbage in buffer
                    buf = new byte[Constants.IO_BUFFER_SIZE];
                }
            } finally {
                in.close();
            }
        } catch (IOException e) {
            String errMsg = "Trouble copying inputstream " + in + " to JspWriter " + out;
            log.warn(errMsg, e);
            throw new IOFailure(errMsg, e);
        }
    }

    /**
     * Will copy everything from input stream to output stream, closing input stream afterwards.
     *
     * @param in Inputstream to copy from
     * @param out Outputstream to copy to
     * @throws ArgumentNotValid if either parameter is null
     * @throws IOFailure if a read or write error happens during copy
     */
    public static void copyInputStreamToOutputStream(InputStream in, OutputStream out) {
        ArgumentNotValid.checkNotNull(in, "InputStream in");
        ArgumentNotValid.checkNotNull(out, "OutputStream out");

        try {
            try {
                if (in instanceof FileInputStream && out instanceof FileOutputStream) {
                    FileChannel inChannel = ((FileInputStream) in).getChannel();
                    FileChannel outChannel = ((FileOutputStream) out).getChannel();
                    long transferred = 0;
                    final long fileLength = inChannel.size();
                    do {
                        transferred += inChannel.transferTo(transferred,
                                Math.min(Constants.IO_CHUNK_SIZE, fileLength - transferred), outChannel);
                    } while (transferred < fileLength);
                } else {
                    byte[] buf = new byte[Constants.IO_BUFFER_SIZE];
                    int bytesRead;
                    while ((bytesRead = in.read(buf)) != -1) {
                        out.write(buf, 0, bytesRead);
                    }
                }
                out.flush();
            } finally {
                in.close();
            }
        } catch (IOException e) {
            String errMsg = "Trouble copying inputstream " + in + " to outputstream " + out;
            log.warn(errMsg, e);
            throw new IOFailure(errMsg, e);
        }
    }

    /**
     * Write document tree to stream. Note, the stream is flushed, but not closed.
     *
     * @param doc the document tree to save.
     * @param os the stream to write xml to
     * @throws IOFailure On trouble writing XML to stream.
     */
    public static void writeXmlToStream(Document doc, OutputStream os) {
        ArgumentNotValid.checkNotNull(doc, "Document doc");
        ArgumentNotValid.checkNotNull(doc, "OutputStream os");
        XMLWriter xwriter = null;
        try {
            try {
                OutputFormat format = OutputFormat.createPrettyPrint();
                format.setEncoding(UTF8_CHARSET);
                xwriter = new XMLWriter(os, format);
                xwriter.write(doc);
            } finally {
                if (xwriter != null) {
                    xwriter.close();
                }
                os.flush();
            }
        } catch (IOException e) {
            String errMsg = "Unable to write XML to stream";
            log.warn(errMsg, e);
            throw new IOFailure(errMsg, e);
        }
    }

    /**
     * Reads an input stream and returns it as a string.
     *
     * @param in The input stream.
     * @return The string content of the input stream in the UTF8-charset.
     * @throws ArgumentNotValid If the input stream is null.
     * @throws IOFailure If an IOException is caught while reading the inputstream.
     */
    public static String getInputStreamAsString(InputStream in) throws ArgumentNotValid, IOFailure {
        ArgumentNotValid.checkNotNull(in, "InputStream in");

        StringBuilder res = new StringBuilder();
        byte[] buf = new byte[Constants.IO_BUFFER_SIZE];
        int read = 0;
        try {
            try {
                while ((read = in.read(buf)) != -1) {
                    String s = new String(buf, UTF8_CHARSET);
                    if (s.length() < read) {
                        log.debug("String length ({}) < bytes read({})", s.length(), read);
                        res.append(s, 0, s.length());
                    } else {
                        res.append(s, 0, read);
                    }
                    // Reinitializing the buffer to avoid garbage in buffer
                    buf = new byte[Constants.IO_BUFFER_SIZE];
                }
            } finally {
                in.close();
            }
        } catch (IOException e) {
            String errMsg = "Trouble reading inputstream '" + in + "'";
            log.warn(errMsg, e);
            throw new IOFailure(errMsg, e);
        }

        return res.toString();
    }
    
    /**
     * Get FileReader as String.
     * @param fr a given FileReader
     * @return the FileReader as a String.
     * @throws ArgumentNotValid If the FileReader is null.
     * @throws IOFailure If an IOException is caught while reading the FileReader.
     */
    public static String getFileReaderAsString(FileReader fr) throws ArgumentNotValid, IOFailure {
        ArgumentNotValid.checkNotNull(fr, "FileReader fr");

        StringBuilder res = new StringBuilder();
        String line = null;
        try {
            BufferedReader bufferreader = new BufferedReader(fr);
            line = bufferreader.readLine();
            while (line != null) {     
                res.append(line);
                res.append("\n");              
                line = bufferreader.readLine();
            }
        } catch (IOException ex) {
            String errMsg = "Trouble reading FileReader '" + fr + "'";
            log.warn(errMsg, ex);
            throw new IOFailure(errMsg, ex);
        }
        return res.toString();
    }
    
    /**
     * Convert inputStream to byte array.
     *
     * @param data a given InputStream
     * @param dataLength length of the InputStream (must be larger than 0)
     * @return byte[] containing the data in the given InputStream
     */
    public static byte[] inputStreamToBytes(InputStream data, int dataLength) {
        ArgumentNotValid.checkNotNull(data, "data");
        ArgumentNotValid.checkNotNegative(dataLength, "dataLength");
        byte[] contents = new byte[dataLength];
        try {
            int read = data.read(contents, 0, dataLength);
            if (dataLength != read) {
                log.debug("Only read {} bytes out of the {} bytes requested", read, dataLength);
            }
        } catch (IOException e) {
            throw new IOFailure("Unable to convert inputstream to byte array", e);
        }
        return contents;
    }

}
