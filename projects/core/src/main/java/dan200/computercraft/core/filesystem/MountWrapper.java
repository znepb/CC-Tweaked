/*
 * This file is part of ComputerCraft - http://www.computercraft.info
 * Copyright Daniel Ratcliffe, 2011-2022. Do not distribute without permission.
 * Send enquiries to dratcliffe@gmail.com
 */
package dan200.computercraft.core.filesystem;

import dan200.computercraft.api.filesystem.FileOperationException;
import dan200.computercraft.api.filesystem.Mount;
import dan200.computercraft.api.filesystem.WritableMount;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.OptionalLong;

class MountWrapper {
    private final String label;
    private final String location;

    private final Mount mount;
    private final @Nullable WritableMount writableMount;

    MountWrapper(String label, String location, Mount mount) {
        this.label = label;
        this.location = location;
        this.mount = mount;
        writableMount = null;
    }

    MountWrapper(String label, String location, WritableMount mount) {
        this.label = label;
        this.location = location;
        this.mount = mount;
        writableMount = mount;
    }

    public String getLabel() {
        return label;
    }

    public String getLocation() {
        return location;
    }

    public long getFreeSpace() {
        if (writableMount == null) return 0;

        try {
            return writableMount.getRemainingSpace();
        } catch (IOException e) {
            return 0;
        }
    }

    public OptionalLong getCapacity() {
        return writableMount == null ? OptionalLong.empty() : OptionalLong.of(writableMount.getCapacity());
    }

    public boolean isReadOnly(String path) throws FileSystemException {
        try {
            return writableMount == null || writableMount.isReadOnly(path);
        } catch (IOException e) {
            throw localExceptionOf(path, e);
        }
    }

    public boolean exists(String path) throws FileSystemException {
        path = toLocal(path);
        try {
            return mount.exists(path);
        } catch (IOException e) {
            throw localExceptionOf(path, e);
        }
    }

    public boolean isDirectory(String path) throws FileSystemException {
        path = toLocal(path);
        try {
            return mount.exists(path) && mount.isDirectory(path);
        } catch (IOException e) {
            throw localExceptionOf(path, e);
        }
    }

    public void list(String path, List<String> contents) throws FileSystemException {
        path = toLocal(path);
        try {
            if (!mount.exists(path) || !mount.isDirectory(path)) {
                throw localExceptionOf(path, "Not a directory");
            }

            mount.list(path, contents);
        } catch (IOException e) {
            throw localExceptionOf(path, e);
        }
    }

    public long getSize(String path) throws FileSystemException {
        path = toLocal(path);
        try {
            if (!mount.exists(path)) throw localExceptionOf(path, "No such file");
            return mount.isDirectory(path) ? 0 : mount.getSize(path);
        } catch (IOException e) {
            throw localExceptionOf(path, e);
        }
    }

    public BasicFileAttributes getAttributes(String path) throws FileSystemException {
        path = toLocal(path);
        try {
            if (!mount.exists(path)) throw localExceptionOf(path, "No such file");
            return mount.getAttributes(path);
        } catch (IOException e) {
            throw localExceptionOf(path, e);
        }
    }

    public SeekableByteChannel openForRead(String path) throws FileSystemException {
        path = toLocal(path);
        try {
            if (mount.exists(path) && !mount.isDirectory(path)) {
                return mount.openForRead(path);
            } else {
                throw localExceptionOf(path, "No such file");
            }
        } catch (IOException e) {
            throw localExceptionOf(path, e);
        }
    }

    public void makeDirectory(String path) throws FileSystemException {
        if (writableMount == null) throw exceptionOf(path, "Access denied");

        path = toLocal(path);
        try {
            if (mount.exists(path)) {
                if (!mount.isDirectory(path)) throw localExceptionOf(path, "File exists");
            } else {
                writableMount.makeDirectory(path);
            }
        } catch (IOException e) {
            throw localExceptionOf(path, e);
        }
    }

    public void delete(String path) throws FileSystemException {
        if (writableMount == null) throw exceptionOf(path, "Access denied");

        path = toLocal(path);
        try {
            if (mount.exists(path)) {
                writableMount.delete(path);
            }
        } catch (AccessDeniedException e) {
            throw new FileSystemException("Access denied");
        } catch (IOException e) {
            throw localExceptionOf(path, e);
        }
    }

    public void rename(String source, String dest) throws FileSystemException {
        if (writableMount == null) throw exceptionOf(source, "Access denied");

        source = toLocal(source);
        dest = toLocal(dest);
        try {
            if (!dest.isEmpty()) {
                var destParent = FileSystem.getDirectory(dest);
                if (!destParent.isEmpty() && !mount.exists(destParent)) writableMount.makeDirectory(destParent);
            }

            writableMount.rename(source, dest);
        } catch (AccessDeniedException e) {
            throw new FileSystemException("Access denied");
        } catch (IOException e) {
            throw localExceptionOf(source, e);
        }
    }

    public WritableByteChannel openForWrite(String path) throws FileSystemException {
        if (writableMount == null) throw exceptionOf(path, "Access denied");

        path = toLocal(path);
        try {
            if (mount.exists(path) && mount.isDirectory(path)) {
                throw localExceptionOf(path, "Cannot write to directory");
            } else {
                if (!path.isEmpty()) {
                    var dir = FileSystem.getDirectory(path);
                    if (!dir.isEmpty() && !mount.exists(path)) {
                        writableMount.makeDirectory(dir);
                    }
                }
                return writableMount.openForWrite(path);
            }
        } catch (AccessDeniedException e) {
            throw new FileSystemException("Access denied");
        } catch (IOException e) {
            throw localExceptionOf(path, e);
        }
    }

    public WritableByteChannel openForAppend(String path) throws FileSystemException {
        if (writableMount == null) throw exceptionOf(path, "Access denied");

        path = toLocal(path);
        try {
            if (!mount.exists(path)) {
                if (!path.isEmpty()) {
                    var dir = FileSystem.getDirectory(path);
                    if (!dir.isEmpty() && !mount.exists(path)) {
                        writableMount.makeDirectory(dir);
                    }
                }
                return writableMount.openForWrite(path);
            } else if (mount.isDirectory(path)) {
                throw localExceptionOf(path, "Cannot write to directory");
            } else {
                return writableMount.openForAppend(path);
            }
        } catch (AccessDeniedException e) {
            throw new FileSystemException("Access denied");
        } catch (IOException e) {
            throw localExceptionOf(path, e);
        }
    }

    private String toLocal(String path) {
        return FileSystem.toLocal(path, location);
    }

    private FileSystemException localExceptionOf(@Nullable String localPath, IOException e) {
        if (!location.isEmpty() && e instanceof FileOperationException ex) {
            if (ex.getFilename() != null) return localExceptionOf(ex.getFilename(), FileSystemException.getMessage(ex));
        }

        if (e instanceof java.nio.file.FileSystemException ex) {
            // This error will contain the absolute path, leaking information about where MC is installed. We drop that,
            // just taking the reason. We assume that the error refers to the input path.
            var message = getReason(ex);
            return localPath == null ? new FileSystemException(message) : localExceptionOf(localPath, message);
        }

        return FileSystemException.of(e);
    }

    private FileSystemException localExceptionOf(String path, String message) {
        if (!location.isEmpty()) path = path.isEmpty() ? location : location + "/" + path;
        return exceptionOf(path, message);
    }

    private static FileSystemException exceptionOf(String path, String message) {
        return new FileSystemException("/" + path + ": " + message);
    }

    private static String getReason(java.nio.file.FileSystemException e) {
        var reason = e.getReason();
        if (reason != null) return reason.trim();

        if (e instanceof FileAlreadyExistsException) return "File exists";
        if (e instanceof NoSuchFileException) return "No such file";
        if (e instanceof AccessDeniedException) return "Access denied";

        return "Operation failed";
    }
}
