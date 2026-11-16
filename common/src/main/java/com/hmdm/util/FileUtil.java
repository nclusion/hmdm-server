/*
 *
 * Headwind MDM: Open Source Android MDM Software
 * https://h-mdm.com
 *
 * Copyright (C) 2019 Headwind Solutions LLC (http://h-sms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hmdm.util;

import com.hmdm.persistence.domain.Application;
import com.hmdm.persistence.domain.Customer;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;

/**
 * <p>An utility class for managing the files on local file system.</p>
 *
 * @author isv
 */
public final class FileUtil {
    private static final String TEMP_FILE_DELIMITER = "1111111";

    /**
     * <p>Directory, next to {@code files.directory}, that holds upload temp files.</p>
     */
    static final String UPLOAD_TMP_DIR_NAME = ".hmdm-upload-tmp";

    private static final Set<PosixFilePermission> OWNER_ONLY_DIR_PERMISSIONS =
            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);

    private static final Set<PosixFilePermission> OWNER_ONLY_FILE_PERMISSIONS =
            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    /**
     * <p>Constructs new <code>FileUtil</code> instance. This implementation does nothing.</p>
     */
    private FileUtil() {
    }

    public static String adjustFileName(String fileName) {
        return fileName
                .replace(' ', '_')
                .replace('+', '_')          // Not valid in URL
                .replace('%', '_')          // Not valid in URL
                .replace("(", "")           // These characters are used by Windows when a file is downloaded twice
                .replace(")", "");
    }

    /**
     * <p>Creates an upload temp file in an application-owned directory beside
     * {@code filesDirectory}, not in the shared system temp directory. The directory is
     * {@code 0700} and the file is {@code 0600} where POSIX permissions are supported.
     * The recoverable name is the sanitized prefix before {@link #TEMP_FILE_DELIMITER}.</p>
     *
     * @param filesDirectory configured {@code files.directory} root.
     * @param fileName client-supplied name used as the recoverable prefix.
     * @return the created temporary file.
     * @throws IOException if the directory or the file cannot be created.
     */
    public static File createTempFile(String filesDirectory, String fileName) throws IOException {
        Path uploadTmpDir = ensureUploadTempDirectory(filesDirectory);
        String prefix = sanitizeTempFilePrefix(fileName) + TEMP_FILE_DELIMITER;
        Path tempFile;
        if (supportsPosixPermissions(uploadTmpDir)) {
            tempFile = Files.createTempFile(
                    uploadTmpDir,
                    prefix,
                    ".temp",
                    PosixFilePermissions.asFileAttribute(OWNER_ONLY_FILE_PERMISSIONS));
        } else {
            tempFile = Files.createTempFile(uploadTmpDir, prefix, ".temp");
        }
        return tempFile.toFile();
    }

    /**
     * <p>Absolute path of the upload temp directory for {@code filesDirectory}.
     * Does not create the directory.</p>
     */
    public static String uploadTempDirectoryPath(String filesDirectory) {
        if (filesDirectory == null || filesDirectory.trim().isEmpty()) {
            return "";
        }
        Path base = Paths.get(filesDirectory).toAbsolutePath().normalize();
        Path parent = base.getParent();
        if (parent == null) {
            return "";
        }
        return parent.resolve(UPLOAD_TMP_DIR_NAME).toString();
    }

    /**
     * <p>Creates the upload temp directory with owner-only permissions where POSIX is supported.</p>
     */
    static Path ensureUploadTempDirectory(String filesDirectory) throws IOException {
        String directoryPath = uploadTempDirectoryPath(filesDirectory);
        if (directoryPath.isEmpty()) {
            throw new IOException("files.directory is not configured");
        }
        Path tmpDir = Paths.get(directoryPath);
        Files.createDirectories(tmpDir);
        if (supportsPosixPermissions(tmpDir)) {
            Files.setPosixFilePermissions(tmpDir, OWNER_ONLY_DIR_PERMISSIONS);
        }
        return tmpDir;
    }

    /**
     * <p>Removes path separators and parent-directory segments so the prefix cannot escape
     * the upload temp directory. Other characters are kept so {@link #getNameFromTmpPath(String)}
     * still returns the name that was stored.</p>
     */
    static String sanitizeTempFilePrefix(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "upload";
        }
        return fileName
                .replace("..", "_")
                .replace("/", "_")
                .replace("\\", "_")
                .replace("\0", "_");
    }

    private static boolean supportsPosixPermissions(Path path) {
        try {
            FileStore store = Files.getFileStore(path);
            return store.supportsFileAttributeView("posix");
        } catch (IOException e) {
            return false;
        }
    }

    public static void writeToFile(InputStream uploadedInputStream, String uploadedFileLocation) {
        try (FileOutputStream out = new FileOutputStream(new File(uploadedFileLocation))) {
            byte[] bytes = new byte[1024];

            int read;
            while((read = uploadedInputStream.read(bytes)) != -1) {
                out.write(bytes, 0, read);
            }

            out.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getNameFromTmpPath(String tmpFilePath) {
        File localFile = new File(tmpFilePath);
        if (!localFile.getName().contains(TEMP_FILE_DELIMITER)) {
            throw new RuntimeException("Temp file should contain the delimiter: " + tmpFilePath);
        }
        return localFile.getName().split(TEMP_FILE_DELIMITER)[0];
    }

    /**
     * <p>Moves the specified uploaded file to desired location related to specified customer account.</p>
     *
     * @param customer a customer account which the file belongs to.
     * @param filesDirectory a directory which holds all the files maintained by the application.
     * @param localPath an optional local path to move file to.
     * @param tmpFilePath a path to a temorary file to be moved.
     * @return a file referencing the moved file if operation was successful; <code>null</code> otherwise.
     * @throws FileExistsException if file already exists in
     */
    public static File moveFile(Customer customer, String filesDirectory, String localPath, String tmpFilePath) {
        File localFile = new File(tmpFilePath);
        String fileName = getNameFromTmpPath(tmpFilePath);

        String filePath;
        if (localPath == null || localPath.isEmpty()) {
            filePath = String.format("%s/%s/%s", filesDirectory, customer.getFilesDir(), fileName);
        } else {
            filePath = String.format("%s/%s/%s/%s", filesDirectory, customer.getFilesDir(), localPath, fileName);
        }
        File file = new File(filePath.replace("/", File.separator));
        file.getParentFile().mkdirs();

        if (file.exists()) {
            throw new FileExistsException(customer, file.getName());
        }

        final boolean success = localFile.renameTo(file);
        if (success) {
            return file;
        } else {
            // Try to copy and delete because rename can fail due to different file systems
            // For example, on Tomcat 9 renaming from /tmp to /var/lib/tomcat9/work will fail due to sandbox restrictions
            try {
                FileInputStream inputStream = new FileInputStream(localFile);
                writeToFile(inputStream, file.getAbsolutePath());
                inputStream.close();
                localFile.delete();
                return file;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

    }

    public static String translateURLToLocalFilePath(Customer customer, String url, String baseUrl) {
        final String prefixWithoutCustomer = baseUrl + "/files/";
        String prefix = prefixWithoutCustomer;
        if (customer.getFilesDir() != null && !customer.getFilesDir().isEmpty()) {
            prefix += customer.getFilesDir() + "/";
        }
        if (url.startsWith(prefix)) {
            final String path = url.substring(prefix.length());
            return path.replace("/", File.separator);
        }

        return null;
    }

    /**
     * <p>Deletes the specified file on local file system.</p>
     *
     * @param baseDirectory a path to a base directory where all application files are stored..
     * @param path a path to a file to delete.
     * @return <code>true</code> if file was deleted successfully; <code>false</code> otherwise.
     */
    public static boolean deleteFile(Customer customer, String baseDirectory, String path) {
        String filePath = String.format("%s/%s", baseDirectory, customer.getFilesDir()).replace("/", File.separator);
        final File fileToDelete = new File(filePath, path);
        return fileToDelete.delete();
    }

    public static String createFileUrl(String baseUrl, String customerDir, String fileName) {
        String url = baseUrl + "/files/";
        if (customerDir != null && !customerDir.equals("")) {
            url += customerDir + "/";
        }
        url += fileName;
        return url;
    }

    public static String downloadTextFile(URL url) throws IOException {
        BufferedInputStream bis = new BufferedInputStream(url.openStream());
        StringBuffer stringBuffer = new StringBuffer();
        byte[] buffer = new byte[1024];
        int count=0;
        while((count = bis.read(buffer,0,1024)) != -1) {
            stringBuffer.append(new String(buffer, StandardCharsets.UTF_8));
        }
        bis.close();
        return stringBuffer.toString();
    }

    public static void downloadFile(URL url, String directory, String name) throws IOException {
        BufferedInputStream bis = new BufferedInputStream(url.openStream());
        File file = new File(directory, name);
        if (file.exists()) {
            file.delete();
        }
        FileOutputStream fos = new FileOutputStream(file);
        byte[] buffer = new byte[1024];
        int count=0;
        while((count = bis.read(buffer,0,1024)) != -1) {
            fos.write(buffer, 0, count);
        }
        bis.close();
        fos.close();
    }

    public static boolean isSafePath(String path) {
        return !path.contains("..");
    }
}
