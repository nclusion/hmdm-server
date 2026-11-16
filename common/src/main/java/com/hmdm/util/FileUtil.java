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

import com.hmdm.persistence.domain.Customer;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * <p>An utility class for managing the files on local file system.</p>
 *
 * @author isv
 */
public final class FileUtil {
    private static final String TEMP_FILE_DELIMITER = "1111111";

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

    public static File createTempFile(String fileName) throws IOException {
        return File.createTempFile(fileName + TEMP_FILE_DELIMITER, ".temp");
    }

    /**
     * <p>Writes the stream to a destination that has already been validated as contained under an intended base
     * directory (via {@link #resolveContainedChild(File, String)} or equivalent).</p>
     *
     * @param uploadedInputStream content to write.
     * @param destinationFile destination file (must already be resolved/validated by the caller; must not be a directory).
     * @return {@code true} if the bytes were written; {@code false} if the destination was rejected or the write failed.
     */
    public static boolean writeToFile(InputStream uploadedInputStream, File destinationFile) {
        if (destinationFile == null || (destinationFile.exists() && destinationFile.isDirectory())) {
            return false;
        }
        try (FileOutputStream out = new FileOutputStream(destinationFile)) {
            byte[] bytes = new byte[1024];

            int read;
            while((read = uploadedInputStream.read(bytes)) != -1) {
                out.write(bytes, 0, read);
            }

            out.flush();
            return true;

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * <p>Writes the stream to {@code uploadedFileLocation} only when that absolute path stays under
     * {@code baseDirectory}.</p>
     *
     * @param uploadedInputStream content to write.
     * @param baseDirectory intended base directory that must contain the destination.
     * @param uploadedFileLocation absolute or relative destination path.
     * @return {@code true} if the write succeeded; {@code false} if the path was rejected or the write failed.
     */
    public static boolean writeToFile(InputStream uploadedInputStream, File baseDirectory, String uploadedFileLocation) {
        File destination = confirmContained(baseDirectory, uploadedFileLocation);
        if (destination == null) {
            return false;
        }
        return writeToFile(uploadedInputStream, destination);
    }

    /**
     * @deprecated Prefer {@link #writeToFile(InputStream, File)} after resolving with
     * {@link #resolveContainedFile(File, String)}. This overload accepts an absolute path only when it stays under
     * the JVM temporary directory.
     */
    @Deprecated
    public static void writeToFile(InputStream uploadedInputStream, String uploadedFileLocation) {
        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        writeToFile(uploadedInputStream, tmpDir, uploadedFileLocation);
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
        // A textual startsWith(tmpdir) check accepts "<tmpdir>/../outside" and "<tmpdir>-sibling".
        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        File localFile = confirmContained(tmpDir, tmpFilePath);
        if (localFile == null || !localFile.isFile()) {
            return null;
        }
        String fileName = getNameFromTmpPath(localFile.getPath());

        File filesBase = new File(filesDirectory);
        File customerBase = resolveContainedFile(filesBase, nullToEmpty(customer.getFilesDir()));
        if (customerBase == null) {
            return null;
        }

        File file;
        String relativeLocal = normalizeRelativeChild(localPath);
        if (relativeLocal.isEmpty()) {
            file = resolveContainedChild(customerBase, fileName);
        } else {
            File localDir = resolveContainedFile(customerBase, relativeLocal);
            file = localDir == null ? null : resolveContainedChild(localDir, fileName);
        }
        if (file == null) {
            return null;
        }

        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

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
                boolean written = writeToFile(inputStream, file);
                inputStream.close();
                if (!written) {
                    return null;
                }
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
        File filesBase = new File(baseDirectory);
        File customerBase = resolveContainedFile(filesBase, nullToEmpty(customer.getFilesDir()));
        if (customerBase == null) {
            return false;
        }
        final File fileToDelete = resolveContainedChild(customerBase, path);
        if (fileToDelete == null) {
            return false;
        }
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
        File base = new File(directory);
        File file = resolveContainedChild(base, name);
        if (file == null) {
            bis.close();
            throw new IOException("Refusing to download outside base directory: " + name);
        }
        if (file.exists()) {
            file.delete();
        }
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
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

    /**
     * <p>Static safety check for a relative path segment or relative path. Rejects {@code null}/empty values,
     * absolute paths, and {@code ..} segments. Call sites that open files must still use
     * {@link #resolveContainedFile(File, String)} so the resolved path is proven to stay under the base.</p>
     */
    public static boolean isSafePath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        // Reject control characters (e.g. embedded NUL) that would make Path.resolve throw.
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) < 0x20) {
                return false;
            }
        }
        String normalized = path.replace('\\', '/');
        // "/" or "./" alone means "base directory" — treated as a safe relative root marker.
        if ("/".equals(normalized) || "./".equals(normalized) || ".".equals(normalized)) {
            return true;
        }
        // UNC must be rejected before stripping leading slashes.
        if (normalized.startsWith("//")) {
            return false;
        }
        // Strip leading "./" and "/" root-of-base markers before absolute checks so values like
        // "/subdir/file" (UI-relative) stay accepted while drive-letter forms are rejected.
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            return true;
        }
        if (new File(normalized).isAbsolute()) {
            return false;
        }
        // Reject Windows drive-letter forms only. Colon is otherwise a legal filename
        // byte on Linux/Android (e.g. "backup:1.apk"); do not ban it per-segment —
        // containment is proven by normalize + isUnderBase. Drive letters are specifically
        // "X:" or "X:/..." — not "name:tag".
        if (normalized.length() >= 2
                && Character.isLetter(normalized.charAt(0))
                && normalized.charAt(1) == ':'
                && (normalized.length() == 2
                    || normalized.charAt(2) == '/'
                    || normalized.charAt(2) == '\\')) {
            return false;
        }
        String[] parts = normalized.split("/");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                return false;
            }
        }
        return true;
    }

    /**
     * <p>Resolves {@code childPath} against {@code baseDir} and returns the canonical file only when the result
     * stays inside the base directory. Returns {@code null} for unsafe children, traversal ({@code ..}),
     * symlink escapes (where the platform resolves them), or I/O failures.</p>
     *
     * <p>An empty, {@code null}, or {@code /} child path resolves to the canonical base directory itself
     * (directory lookup). Prefer {@link #resolveContainedChild(File, String)} when the caller expects a
     * real file under the base rather than the base directory.</p>
     *
     * <p>A leading slash is treated as a root-of-base marker ({@code /subdir} → {@code subdir} under the base),
     * matching historical {@code new File(base, "/subdir")} behaviour. Values that remain absolute after
     * that normalization (Windows drive letters / UNC) are rejected.</p>
     *
     * @param baseDir intended containing directory (created if missing).
     * @param childPath relative child path (leading {@code /} allowed as base-root marker).
     * @return canonical contained {@link File}, or {@code null} if the path is unsafe.
     */
    public static File resolveContainedFile(File baseDir, String childPath) {
        if (baseDir == null) {
            return null;
        }
        try {
            File base = canonicalBase(baseDir);
            String relative = normalizeRelativeChild(childPath);
            if (relative.isEmpty()) {
                return base;
            }
            // After stripping leading-slash base markers, reject values that are still absolute
            // (Windows drive letters / UNC) or otherwise unsafe.
            if (new File(relative).isAbsolute()) {
                return null;
            }
            if (!isSafePath(relative)) {
                return null;
            }
            // Paths.get + normalize collapses ./ and redundant separators without following symlinks yet
            Path basePath = base.toPath().toAbsolutePath().normalize();
            Path resolved = basePath.resolve(relative).normalize();
            if (!resolved.startsWith(basePath)) {
                return null;
            }
            // getCanonicalFile follows symlinks where the platform supports it
            File canonical = resolved.toFile().getCanonicalFile();
            if (!isUnderBase(base, canonical)) {
                return null;
            }
            return canonical;
        } catch (IOException e) {
            return null;
        } catch (RuntimeException e) {
            // InvalidPathException (NUL / illegal chars) extends IllegalArgumentException;
            // return null so callers keep their 403/404 instead of leaking a 500.
            return null;
        }
    }

    /**
     * <p>Like {@link #resolveContainedFile(File, String)} but refuses to return the base directory itself.
     * Use this when the caller expects a file (or nested path) under the base — empty, {@code /}, {@code .},
     * and {@code ./} children yield {@code null}.</p>
     */
    public static File resolveContainedChild(File baseDir, String childPath) {
        File resolved = resolveContainedFile(baseDir, childPath);
        if (resolved == null || baseDir == null) {
            return null;
        }
        try {
            File base = canonicalBase(baseDir);
            if (resolved.equals(base)) {
                return null;
            }
            return resolved;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * <p>Resolves successive relative segments under {@code baseDir}. Equivalent to nesting
     * {@link #resolveContainedFile(File, String)} calls.</p>
     */
    public static File resolveContainedFile(File baseDir, String firstChild, String secondChild) {
        File mid = resolveContainedFile(baseDir, firstChild);
        if (mid == null) {
            return null;
        }
        return resolveContainedFile(mid, secondChild);
    }

    /**
     * <p>Resolves {@code firstChild} as a directory under {@code baseDir}, then {@code secondChild} as a
     * real child that must not collapse back to that directory.</p>
     */
    public static File resolveContainedChild(File baseDir, String firstChild, String secondChild) {
        File mid = resolveContainedFile(baseDir, firstChild);
        if (mid == null) {
            return null;
        }
        return resolveContainedChild(mid, secondChild);
    }

    /**
     * <p>Returns the canonical form of {@code candidate} when it already exists under {@code baseDir};
     * otherwise {@code null}. Useful when the caller already holds an absolute path (for example a temp file)
     * and needs to prove containment before opening it.</p>
     */
    public static File confirmContained(File baseDir, String candidatePath) {
        if (baseDir == null || candidatePath == null || candidatePath.isEmpty()) {
            return null;
        }
        try {
            File base = canonicalBase(baseDir);
            File candidate = new File(candidatePath).getCanonicalFile();
            if (!isUnderBase(base, candidate)) {
                return null;
            }
            return candidate;
        } catch (IOException e) {
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * <p>Returns the canonical form of {@code candidate} when it stays under any of the provided base
     * directories; otherwise {@code null}.</p>
     */
    public static File confirmContainedInAny(File candidate, File... baseDirs) {
        if (candidate == null || baseDirs == null || baseDirs.length == 0) {
            return null;
        }
        try {
            File canonical = candidate.getCanonicalFile();
            for (int i = 0; i < baseDirs.length; i++) {
                File baseDir = baseDirs[i];
                if (baseDir == null) {
                    continue;
                }
                File base = canonicalBase(baseDir);
                if (isUnderBase(base, canonical)) {
                    return canonical;
                }
            }
            return null;
        } catch (IOException e) {
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    static boolean isUnderBase(File base, File candidate) {
        if (base == null || candidate == null) {
            return false;
        }
        String basePath = base.getPath();
        String candidatePath = candidate.getPath();
        if (candidatePath.equals(basePath)) {
            return true;
        }
        String prefix = basePath.endsWith(File.separator) ? basePath : basePath + File.separator;
        return candidatePath.startsWith(prefix);
    }

    private static File canonicalBase(File baseDir) throws IOException {
        // Resolve only. Callers that write create directories after the path is proven contained.
        return baseDir.getAbsoluteFile().getCanonicalFile();
    }

    private static String normalizeRelativeChild(String childPath) {
        if (childPath == null) {
            return "";
        }
        String normalized = childPath.replace('\\', '/').trim();
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
