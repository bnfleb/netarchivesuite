/*
 * #%L
 * Netarchivesuite - common
 * %%
 * Copyright (C) 2005 - 2014 The Royal Danish Library, the Danish State and University Library,
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

package dk.netarkivet.common.distribute.arcrepository.bitrepository;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.netarkivet.common.distribute.FileRemoteFile;
import dk.netarkivet.common.distribute.arcrepository.ArcRepositoryClient;
import dk.netarkivet.common.distribute.arcrepository.BatchStatus;
import dk.netarkivet.common.distribute.arcrepository.BitarchiveRecord;
import dk.netarkivet.common.distribute.arcrepository.Replica;
import dk.netarkivet.common.distribute.arcrepository.ReplicaStoreState;
import dk.netarkivet.common.exceptions.ArgumentNotValid;
import dk.netarkivet.common.exceptions.IOFailure;
import dk.netarkivet.common.exceptions.NotImplementedException;
import dk.netarkivet.common.exceptions.PermissionDenied;
import dk.netarkivet.common.utils.FileUtils;
import dk.netarkivet.common.utils.Settings;
import dk.netarkivet.common.utils.batch.BatchLocalFiles;
import dk.netarkivet.common.utils.batch.FileBatchJob;

/**
 * A implementation of ArcRepositoryClient that gets its data from a bitrepository system.
 * The batchjobs are executed locally, after fetching the data from the bitrepository.
 * This is only meant for internal NAS-usage
 *
 * Only the store, get(?), and getFile, and batch will be implemented
 *
 */
public class BitmagArcRepositoryClient implements ArcRepositoryClient {

    /** The logger for this class. */
    private static final Logger log = LoggerFactory.getLogger(BitmagArcRepositoryClient.class);

    /*
     * The static initialiser is called when the class is loaded. It will add default values for all settings defined in
     * this class, by loading them from a settings.xml file in classpath.
     */
    static {
        Settings.addDefaultClasspathSettings(
                "dk/netarkivet/common/distribute/arcrepository/bitrepository/BitmagArcRepositoryClientSettings.xml");
    }

    public static final String BITREPOSITORY_TEMPDIR = "settings.common.arcrepositoryClient.bitrepository.tempdir";

    public static final String BITREPOSITORY_SETTINGS_DIR = "settings.common.arcrepositoryClient.bitrepository.settingsDir";

    // optional so we don't force the user to use credentials.
    public static final String BITREPOSITORY_KEYFILENAME = "settings.common.arcrepositoryClient.bitrepository.keyfilename";

    public static final String BITREPOSITORY_STORE_MAX_PILLAR_FAILURES = "settings.common.arcrepositoryClient.bitrepository.storeMaxPillarFailures";

    public static final String BITREPOSITORY_COLLECTIONID = "settings.common.arcrepositoryClient.bitrepository.collectionID";

    public static final String BITREPOSITORY_USEPILLAR = "settings.common.arcrepositoryClient.bitrepository.usepillar";


    private final String collectionId;

    private final File tempdir;

    private final Bitrepository bitrep;

    /** Create a new BitmagArcRepositoryClient based on current settings. */
    public BitmagArcRepositoryClient() {
        this.collectionId = Settings.get(BITREPOSITORY_COLLECTIONID);
        this.tempdir = Settings.getFile(BITREPOSITORY_TEMPDIR);
        tempdir.mkdirs();
        log.info("Storing tempfiles in folder {}", tempdir);

        File configDir = Settings.getFile(BITREPOSITORY_SETTINGS_DIR);
        String keyfilename = Settings.get(BITREPOSITORY_KEYFILENAME);
        int maxStoreFailures = Settings.getInt(BITREPOSITORY_STORE_MAX_PILLAR_FAILURES);
        String usepillar = Settings.get(BITREPOSITORY_USEPILLAR);

        // Initialize connection to the bitrepository
        this.bitrep = new Bitrepository(configDir, keyfilename, maxStoreFailures, usepillar);
    }

    @Override
    public void close() {
        this.bitrep.shutdown();
    }

    /**
     * Store the given file in the repository. After storing, the file is deleted.
     *
     * @param file A file to be stored. Must exist.
     * @throws IOFailure thrown if store is unsuccessful, or failed to clean up files after the store operation.
     * @throws ArgumentNotValid if file parameter is null or file is not an existing file.
     */
    @Override
    public void store(File file) throws IOFailure, ArgumentNotValid {
        ArgumentNotValid.checkExistsNormalFile(file, "File '" + file.getAbsolutePath() + "' does not exist");
        // Check if file already exists
        if (bitrep.existsInCollection(file.getName(), collectionId)) {
            log.warn("The file '{}' is already in collection '{}'", file.getName(), collectionId);
            return;
        } else {
            // upload file
            boolean uploadSuccessful = bitrep.uploadFile(file, file.getName(), collectionId);
            if (!uploadSuccessful) {
                throw new IOFailure(
                        "Upload to collection '" + collectionId + "' of file '" + file.getName() + "' failed.");
            } else {
                log.info("Upload to collection '{}' of file '{}' was successful", collectionId, file.getName());
            }
        }
    }

    /**
     * Gets a single (W)ARC record out of the repository.
     *
     * @param arcfile The name of a file containing the desired record.
     * @param index The offset of the desired record in the file
     * @return a BitarchiveRecord-object, or null if request times out or object is not found.
     * @throws ArgumentNotValid on null or empty filenames, or if index is negative.
     * @throws IOFailure If the get operation failed.
     */
    @Override
    public BitarchiveRecord get(String arcfile, long index) throws ArgumentNotValid {
        ArgumentNotValid.checkNotNullOrEmpty(arcfile, "String arcfile");
        ArgumentNotValid.checkNotNegative(index, "long index");

        if (!bitrep.existsInCollection(arcfile, collectionId)) {
            log.warn("The file '{}' is not in collection '{}'. Returning null BitarchiveRecord", arcfile, collectionId);
            return null;
        } else {
            File f = bitrep.getFile(arcfile, collectionId, null);
            return BitarchiveRecord.getBitarchiveRecord(arcfile, f, index);
        }
    }

    /**
     * Retrieves a file from a repository and places it in a local file.
     *
     * @param arcfilename Name of the arcfile to retrieve.
     * @param replica The bitarchive to retrieve the data from. (Note argument is ignored)
     * @param toFile Filename of a place where the file fetched can be put.
     * @throws ArgumentNotValid if arcfilename is null or empty, or if toFile is null
     * @throws IOFailure if there are problems reading or writing file, or the file with the given arcfilename could not
     * be found.
     */
    @Override
    public void getFile(String arcfilename, Replica replica, File toFile) {
        ArgumentNotValid.checkNotNullOrEmpty(arcfilename, "String arcfilename");
        ArgumentNotValid.checkNotNull(toFile, "File toFile");

        if (!bitrep.existsInCollection(arcfilename, collectionId)) {
            log.warn("The file '{}' is not in collection '{}'.", arcfilename, collectionId);
            throw new IOFailure("File '" + arcfilename + "' does not exist");
        } else {
            File f = bitrep.getFile(arcfilename, collectionId, null);
            FileUtils.copyFile(f, toFile);
        }
    }

    /**
     * Runs a batch job on each file in the ArcRepository.
     *
     * @param job An object that implements the FileBatchJob interface. The initialize() method will be called before
     * processing and the finish() method will be called afterwards. The process() method will be called with each File
     * entry. An optional function postProcess() allows handling the combined results of the batchjob, e.g. summing the
     * results, sorting, etc.
     * @param replicaId The archive to execute the job on. Argument Ignored: replaced by usepillar.
     * @param args The arguments for the batchjob. This can be null.
     * @return The status of the batch job after it ended.
     * @throws ArgumentNotValid If the job is null
     * @throws IOFailure If a problem occurs during processing the batchjob.
     */
    @Override
    public BatchStatus batch(final FileBatchJob job, String replicaId, String... args)
            throws ArgumentNotValid, IOFailure {
        ArgumentNotValid.checkNotNull(job, "FileBatchJob job");

        // Deduce the remote file to run the batchjob on from the job.getFilenamePattern()
        // e.g. "22-metadata-[0-9]+.(w)?arc" => 22-metadata-1.warc
        log.info("Trying to deducing requested file to run batch on from pattern {}",
                job.getFilenamePattern().pattern());

        String patternAsString = job.getFilenamePattern().pattern();
        if (!patternAsString.contains("metadata-")) {
            log.warn("deducing requested file to run batch on from pattern {} failed. Is not a metadata file",
                    job.getFilenamePattern().pattern());
            return null;
        } else {
            // With 22-metadata-[0-9]+.(w)?arc
            // nameparts will be ["22", "metadata", "[0", "9]+.(w)?arc"]
            String nameParts[] = patternAsString.split("-");
            String nameToFetch = nameParts[0] + "-metadata-1.warc";
            List<File> files = new ArrayList<File>();
            if (!bitrep.existsInCollection(nameToFetch, collectionId)) {
                log.warn("The file '{}' is not in collection '{}'.", nameToFetch, collectionId);
            } else {
                File tmpFile = bitrep.getFile(nameToFetch, this.collectionId, null);
                File workFile = new File(tempdir, nameToFetch);
                FileUtils.moveFile(tmpFile, workFile);
                files.add(workFile);
            }

            OutputStream os = null;
            File resultFile;
            try {
                resultFile = File.createTempFile("batch", replicaId, FileUtils.getTempDir());
                os = new FileOutputStream(resultFile);

                BatchLocalFiles batcher = new BatchLocalFiles(files.toArray(new File[files.size()]));
                batcher.run(job, os);
            } catch (IOException e) {
                throw new IOFailure("Cannot perform batch '" + job + "'", e);
            } finally {
                if (os != null) {
                    try {
                        os.close();
                    } catch (IOException e) {
                        log.warn("Error closing batch output stream '{}'", os, e);
                    }
                }
            }
            return new BatchStatus(replicaId, job.getFilesFailed(), job.getNoOfFilesProcessed(), new FileRemoteFile(
                    resultFile), job.getExceptions());
        }
    }

    /////////////////// The rest of the API is not implemented for the bitrepository system ///////////////////////////

    /**
     * Updates the administrative data in the ArcRepository for a given file and replica. This implementation does
     * nothing.
     *
     * @param fileName The name of a file stored in the ArcRepository.
     * @param bitarchiveId The id of the replica that the administrative data for fileName is wrong for.
     * @param newval What the administrative data will be updated to.
     */
    @Override
    public void updateAdminData(String fileName, String bitarchiveId, ReplicaStoreState newval) {
        throw new NotImplementedException("UpdateAdminData is not implemented here");
    }

    /**
     * Updates the checksum kept in the ArcRepository for a given file. It is the responsibility of the ArcRepository
     * implementation to ensure that this checksum matches that of the underlying files. This implementation does
     * nothing.
     *
     * @param filename The name of a file stored in the ArcRepository.
     * @param checksum The new checksum.
     */
    @Override
    public void updateAdminChecksum(String filename, String checksum) {
        throw new NotImplementedException("UpdateAdminChecksum is not implemented here");
    }

    /**
     * Remove a file from one part of the ArcRepository, retrieving a copy for security purposes. This is typically used
     * when repairing a file that has been corrupted.
     *
     * @param fileName The name of the file to remove.
     * @param bitarchiveId The id of the replica from which to remove the file. Not used in this implementation, may be
     * null.
     * @param checksum The checksum of the file to be removed.
     * @param credentials A string that shows that the user is allowed to perform this operation.
     * @return A local copy of the file removed.
     * @throws ArgumentNotValid On null or empty parameters for fileName, checksum or credentials.
     * @throws IOFailure On IO trouble.
     * @throws PermissionDenied On wrong MD5 sum or wrong credentials.
     */
    @Override
    public File removeAndGetFile(String fileName, String bitarchiveId, String checksum, String credentials) {
        throw new NotImplementedException("removeAndGetFile is not implemented here");
    }

    /**
     * Method for retrieving the checksums of all the files of the replica.
     *
     * @param replicaId Inherited dummy argument.
     * @return A file containing the names and checksum of all the files in the system.
     * @throws ArgumentNotValid If the replicaId is either null or the empty string.
     * @throws IOFailure If an unexpected IOException is caught.
     */
    @Override
    public File getAllChecksums(String replicaId) throws IOFailure, ArgumentNotValid {
        throw new NotImplementedException("getAllChecksums is not implemented here");
    }

    /**
     * Method for retrieving all the filenames of the replica.
     *
     * @param replicaId Inherited dummy argument.
     * @return A file containing the names of all the files.
     * @throws ArgumentNotValid If the replicaId is either null or empty.
     * @throws IOFailure If an IOException is caught.
     */
    @Override
    public File getAllFilenames(String replicaId) throws IOFailure, ArgumentNotValid {
        throw new NotImplementedException("getAllFilenames is not implemented here");
    }

    /**
     * Method for correcting a bad entry. Calls 'removeAndGetFile' followed by 'store'.
     *
     * @param replicaId Inherited dummy argument.
     * @param checksum The checksum of the bad entry.
     * @param file The new file to replace the bad entry.
     * @param credentials The 'password' to allow changing the archive.
     * @return The bad entry file.
     * @throws ArgumentNotValid If one of the arguments are null, or if a string is empty.
     * @throws PermissionDenied If the credentials or checksum are invalid.
     */
    @Override
    public File correct(String replicaId, String checksum, File file, String credentials) throws ArgumentNotValid,
            PermissionDenied {
        throw new NotImplementedException("correct is not implemented here");
    }

    /**
     * Method for finding the checksum of a file.
     *
     * @param replicaId Inherited dummy variable.
     * @param filename The name of the file to calculate the checksum.
     * @return The checksum of the file, or the empty string if the file was not found or an error occurred.
     * @throws ArgumentNotValid If the replicaId or the filename is either null or the empty string.
     */
    @Override
    public String getChecksum(String replicaId, String filename) throws ArgumentNotValid {
        throw new NotImplementedException("getChecksum is not implemented here");
    }
}