/* File:        $Id$
 * Revision:    $Revision$
 * Author:      $Author$
 * Date:        $Date$
 *
 * The Netarchive Suite - Software to harvest and preserve websites
 * Copyright 2004-2010 Det Kongelige Bibliotek and Statsbiblioteket, Denmark
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package dk.netarkivet.viewerproxy.reporting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;

import org.archive.io.arc.ARCRecord;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import dk.netarkivet.common.exceptions.ArgumentNotValid;
import dk.netarkivet.common.exceptions.IOFailure;
import dk.netarkivet.common.utils.arc.ARCBatchJob;
import dk.netarkivet.common.utils.batch.ARCBatchFilter;
import dk.netarkivet.common.Constants;

/**
 * Batchjob that extracts lines from a crawl log matching a regular expression 
 * The batch job should be restricted to run on metadata files for a specific
 * job only, using the {@link #processOnlyFilesMatching(String)} construct.
 */
public class CrawlLogLinesMatchingRegexp extends ARCBatchJob {

    /** The logger. */
    private final Log log = LogFactory.getLog(getClass().getName());
    
    /** Metadata URL for crawl logs. */
    private static final String SETUP_URL_FORMAT
            = "metadata://netarkivet.dk/crawl/logs/crawl.log";

    /** The regular expression to match in the crawl.log line. */
    private final String regexp;

    /**
     * Initialise the batch job.
     *
     * @param regexp The regexp to match in the crawl.log lines.
     */
    public CrawlLogLinesMatchingRegexp(String regexp) {
        ArgumentNotValid.checkNotNullOrEmpty(regexp, "regexp");
        this.regexp = regexp;

        /**
        * Two weeks in miliseconds.
        */
        batchJobTimeout = 7* Constants.ONE_DAY_IN_MILLIES;
    }

    /**
     * Does nothing, no initialisation is needed.
     * @param os Not used.
     */
    @Override
    public void initialize(OutputStream os) {
    }
    
    @Override
    public ARCBatchFilter getFilter() {
        return new ARCBatchFilter("OnlyCrawlLog") {
            public boolean accept(ARCRecord record) {
                return record.getHeader().getUrl().startsWith(SETUP_URL_FORMAT);
            }
        };
    }

    /**
     * Process a record on crawl log concerning the given domain to result.
     * @param record The record to process.
     * @param os The output stream for the result.
     *
     * @throws ArgumentNotValid on null parameters
     * @throws IOFailure on trouble processing the record.
     */
    @Override
    public void processRecord(ARCRecord record, OutputStream os) {
        ArgumentNotValid.checkNotNull(record, "ARCRecord record");
        ArgumentNotValid.checkNotNull(os, "OutputStream os");
        BufferedReader arcreader
                = new BufferedReader(new InputStreamReader(record));
        try {
            for(String line = arcreader.readLine(); line != null;
                line = arcreader.readLine()) {
                if (line.matches(regexp)) {
                    os.write(line.getBytes("UTF-8"));
                    os.write('\n');
                }

            }
        } catch (IOException e) {
            throw new IOFailure("Unable to process arc record", e);
        } finally {
            try {
                arcreader.close(); 
            } catch (IOException e) {
                log.warn("unable to close arcreader probably", e);
            }
        }
    }

    /**
     * Does nothing, no finishing is needed.
     * @param os Not used.
     */
    @Override
    public void finish(OutputStream os) {
    }

    @Override
    public String toString() {
        return getClass().getName() + ", with arguments: Regexp = " 
            + regexp + ", Filter = " + getFilter();
    }
}