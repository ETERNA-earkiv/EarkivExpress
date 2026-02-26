package com.example.restservice.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipValidator {

    private static final String PGIP_XML = "pgip.xml";
    private static final String PGIP_VERSIONED = "pgip_1.3.xml";

    public static boolean hasPgipMetadata(byte[] zipBytes) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (isPgipFile(entry.getName())) {
                    return true;
                }
            }
            return false;
        }
    }

    private static boolean isPgipFile(String name) {
        String lowerName = name.toLowerCase();
        return lowerName.endsWith("/" + PGIP_XML) || 
               lowerName.endsWith("/" + PGIP_VERSIONED) ||
               lowerName.equals(PGIP_XML) ||
               lowerName.equals(PGIP_VERSIONED);
    }

    public static List<String> getZipEntries(byte[] zipBytes) throws IOException {
        List<String> entries = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
        }
        return entries;
    }
}
