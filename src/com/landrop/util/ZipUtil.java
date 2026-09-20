package com.landrop.util;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipUtil {

    public static File zipFolder(File folderToZip) throws IOException {
        if (!folderToZip.exists() || !folderToZip.isDirectory()) {
            throw new IllegalArgumentException("Path must be an existing directory: " + folderToZip.getAbsolutePath());
        }

        File tempZip = File.createTempFile("landrop_", "_" + folderToZip.getName() + ".zip");
        tempZip.deleteOnExit();

        Path sourcePath = folderToZip.toPath();

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempZip))) {
            Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String relativePath = sourcePath.relativize(file).toString().replace('\\', '/');
                    zos.putNextEntry(new ZipEntry(folderToZip.getName() + "/" + relativePath));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    String relativePath = sourcePath.relativize(dir).toString().replace('\\', '/');
                    if (!relativePath.isEmpty()) {
                        zos.putNextEntry(new ZipEntry(folderToZip.getName() + "/" + relativePath + "/"));
                        zos.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        return tempZip;
    }
}