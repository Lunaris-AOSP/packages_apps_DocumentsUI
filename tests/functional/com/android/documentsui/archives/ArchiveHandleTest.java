/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.documentsui.archives;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static java.util.Objects.requireNonNull;

import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.runner.AndroidJUnit4;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.compressors.CompressorException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

@RunWith(AndroidJUnit4.class)
public class ArchiveHandleTest {
    @Rule
    public ArchiveFileTestRule mArchiveFileTestRule = new ArchiveFileTestRule();

    private ArchiveHandle prepareArchiveHandle(String archivePath, String suffix, String mimeType)
            throws IOException, CompressorException, ArchiveException {
        ParcelFileDescriptor parcelFileDescriptor = mArchiveFileTestRule.openAssetFile(archivePath,
                suffix);

        return ArchiveHandle.create(parcelFileDescriptor, mimeType);
    }

    private static ArchiveEntry getFileInArchive(Enumeration<ArchiveEntry> enumeration) {
        while (enumeration.hasMoreElements()) {
            ArchiveEntry entry = enumeration.nextElement();
            if (entry.getName().equals("hello/inside_folder/hello_insside.txt")) {
                return entry;
            }
        }
        return null;
    }

    private static class ArchiveEntryRecord implements ArchiveEntry {
        private final String mName;
        private final long mSize;
        private final boolean mIsDirectory;

        private ArchiveEntryRecord(ArchiveEntry archiveEntry) {
            this(archiveEntry.getName(), archiveEntry.getSize(), archiveEntry.isDirectory());
        }

        private ArchiveEntryRecord(String name, long size, boolean isDirectory) {
            mName = name;
            mSize = size;
            mIsDirectory = isDirectory;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (obj == null) {
                return false;
            }

            if (obj instanceof ArchiveEntryRecord record) {
                return mName.equals(record.mName) && mSize == record.mSize
                        && mIsDirectory == record.mIsDirectory;
            }

            return false;
        }

        @Override
        public String getName() {
            return mName;
        }

        @Override
        public long getSize() {
            return mSize;
        }

        @Override
        public boolean isDirectory() {
            return mIsDirectory;
        }

        @Override
        public Date getLastModifiedDate() {
            return null;
        }

        @NonNull
        @Override
        public String toString() {
            return String.format(Locale.ENGLISH, "name: %s, size: %d, isDirectory: %b", mName,
                    mSize, mIsDirectory);
        }
    }

    private static List<ArchiveEntry> transformToIterable(Enumeration<ArchiveEntry> enumeration) {
        List<ArchiveEntry> list = new ArrayList<>();
        while (enumeration.hasMoreElements()) {
            list.add(new ArchiveEntryRecord(enumeration.nextElement()));
        }
        return list;
    }

    private static final List<ArchiveEntryRecord> sExpectEntries = List.of(
            new ArchiveEntryRecord("hello/hello.txt", 48, false),
            new ArchiveEntryRecord("hello/inside_folder/hello_insside.txt", 14, false),
            new ArchiveEntryRecord("hello/hello2.txt", 48, false));

    private static String getNormalizedPath(String in, boolean isDir) {
        return Archive.getEntryPath(new ArchiveEntryRecord(in, -1, isDir));
    }

    @Test
    public void normalizePath() {
        assertThat(getNormalizedPath("", true)).isEqualTo("/");
        assertThat(getNormalizedPath("", false)).isEqualTo("/?");
        assertThat(getNormalizedPath("/", true)).isEqualTo("/");
        assertThat(getNormalizedPath("/", false)).isEqualTo("/?");
        assertThat(getNormalizedPath("///", true)).isEqualTo("/");
        assertThat(getNormalizedPath("///", false)).isEqualTo("/?");
        assertThat(getNormalizedPath(".", true)).isEqualTo("/");
        assertThat(getNormalizedPath(".", false)).isEqualTo("/?");
        assertThat(getNormalizedPath("./", true)).isEqualTo("/");
        assertThat(getNormalizedPath("./", false)).isEqualTo("/?");
        assertThat(getNormalizedPath("./foo", true)).isEqualTo("/foo/");
        assertThat(getNormalizedPath("./foo", false)).isEqualTo("/foo");
        assertThat(getNormalizedPath("./foo/", true)).isEqualTo("/foo/");
        assertThat(getNormalizedPath("./foo/", false)).isEqualTo("/foo/?");
        assertThat(getNormalizedPath("..", true)).isEqualTo("/");
        assertThat(getNormalizedPath("..", false)).isEqualTo("/?");
        assertThat(getNormalizedPath("../", true)).isEqualTo("/");
        assertThat(getNormalizedPath("../", false)).isEqualTo("/?");
        assertThat(getNormalizedPath("foo", true)).isEqualTo("/foo/");
        assertThat(getNormalizedPath("foo", false)).isEqualTo("/foo");
        assertThat(getNormalizedPath("foo/", true)).isEqualTo("/foo/");
        assertThat(getNormalizedPath("foo/", false)).isEqualTo("/foo/?");
        assertThat(getNormalizedPath("foo/.", true)).isEqualTo("/foo/");
        assertThat(getNormalizedPath("foo/.", false)).isEqualTo("/foo/?");
        assertThat(getNormalizedPath("foo/..", true)).isEqualTo("/");
        assertThat(getNormalizedPath("foo/..", false)).isEqualTo("/?");
        assertThat(getNormalizedPath("/foo", true)).isEqualTo("/foo/");
        assertThat(getNormalizedPath("/foo", false)).isEqualTo("/foo");
        assertThat(getNormalizedPath("//./../a//b///../c.ext", true)).isEqualTo("/a/c.ext/");
        assertThat(getNormalizedPath("//./../a//b///../c.ext", false)).isEqualTo("/a/c.ext");
        assertThat(getNormalizedPath("//./../a//b///../c.ext/", true)).isEqualTo("/a/c.ext/");
        assertThat(getNormalizedPath("//./../a//b///../c.ext/", false)).isEqualTo("/a/c.ext/?");
    }

    @Test
    public void buildArchiveHandle_sevenZFile_shouldNotNull() throws Exception {
        try (ArchiveHandle archiveHandle = prepareArchiveHandle("archives/7z/hello.7z", ".7z",
                "application/x-7z-compressed")) {
            assertThat(archiveHandle).isNotNull();
        }
    }

    @Test
    public void buildArchiveHandle_zipFile_shouldNotNull() throws Exception {
        try (ArchiveHandle archiveHandle = prepareArchiveHandle("archives/zip/hello.zip", ".zip",
                "application/zip")) {
            assertThat(archiveHandle).isNotNull();
        }
    }

    @Test
    public void buildArchiveHandle_tarFile_shouldNotNull() throws Exception {
        try (ArchiveHandle archiveHandle = prepareArchiveHandle("archives/tar/hello.tar", ".tar",
                "application/x-gtar")) {
            assertThat(archiveHandle).isNotNull();
        }
    }

    @Test
    public void buildArchiveHandle_tgzFile_shouldNotNull() throws Exception {
        try (ArchiveHandle archiveHandle = prepareArchiveHandle("archives/tar_gz/hello.tgz", ".tgz",
                "application/x-compressed-tar")) {
            assertThat(archiveHandle).isNotNull();
        }
    }

    @Test
    public void buildArchiveHandle_tarGzFile_shouldNotNull() throws Exception {
        try (ArchiveHandle archiveHandle = prepareArchiveHandle("archives/tar_gz/hello_tar_gz",
                ".tar.gz", "application/x-compressed-tar")) {
            assertThat(archiveHandle).isNotNull();
        }
    }

    @Test
    public void buildArchiveHandle_tarBzipFile_shouldNotNull() throws Exception {
        try (ArchiveHandle archiveHandle = prepareArchiveHandle("archives/tar_bz2/hello.tar.bz2",
                ".tar.bz2", "application/x-bzip-compressed-tar")) {
            assertThat(archiveHandle).isNotNull();
        }
    }

    @Test
    public void buildArchiveHandle_tarXzFile_shouldNotNull() throws Exception {
        try (ArchiveHandle archiveHandle = prepareArchiveHandle("archives/xz/hello.tar.xz",
                ".tar.xz", "application/x-xz-compressed-tar")) {
            assertThat(archiveHandle).isNotNull();
        }
    }

    @Test
    public void buildArchiveHandle_tarBrFile_shouldNotNull() throws Exception {
        try (ArchiveHandle archiveHandle = prepareArchiveHandle("archives/brotli/hello.tar.br",
                ".tar.br", "application/x-brotli-compressed-tar")) {
            assertThat(archiveHandle).isNotNull();
        }
    }

    @Test
    public void getMimeType_sevenZFile_shouldBeSevenZ()
            throws CompressorException, ArchiveException, IOException {
        try (ArchiveHandle archiveHandle = prepareArchiveHandle("archives/7z/hello.7z", ".7z",
                "application/x-7z-compressed")) {
            assertThat(archiveHandle.getMimeType()).isEqualTo("application/x-7z-compressed");
        }
    }

    @Test
    public void getMimeType_tarBrotli_shouldBeBrotliCompressedTar()
            throws CompressorException, ArchiveException, IOException {
        try (ArchiveHandle archiveHandle = prepareArchiveHandle("archives/brotli/hello.tar.br",
                ".tar.br", "application/x-brotli-compressed-tar")) {
            assertThat(archiveHandle.getMimeType()).isEqualTo(
                    "application/x-brotli-compressed-tar");
        }
    }

    @Test
    public void getMimeType_tarXz_shouldBeXzCompressedTar()
            throws CompressorException, ArchiveException, IOException {
        try (ArchiveHandle archiveHandle = prepareArchiveHandle("archives/xz/hello.tar.xz",
                ".tar.xz", "application/x-xz-compressed-tar")) {
            assertThat(archiveHandle.getMimeType()).isEqualTo("application/x-xz-compressed-tar");
        }
    }

    @Test
    public void getMimeType_tarGz_shouldBeCompressedTar()
            throws CompressorException, ArchiveException, IOException {
        try (ArchiveHandle archiveHandle = prepareArchiveHandle("archives/tar_gz/hello_tar_gz",
                ".tar.gz", "application/x-compressed-tar")) {
            assertThat(archiveHandle.getMimeType()).isEqualTo("application/x-compressed-tar");
        }
    }

    @Test
    public void getCommonArchive_tarBrFile_shouldBeCommonArchiveInputHandle() throws Exception {
        try (ArchiveHandle archiveHandle = prepareArchiveHandle("archives/brotli/hello.tar.br",
                ".tar.br", "application/x-brotli-compressed-tar")) {
            assertThat(archiveHandle.toString()).contains("CommonArchiveInputHandle");
        }
    }

    @Test
    public void getCommonArchive_sevenZFile_shouldBeSevenZFileHandle() throws Exception {
        try (ArchiveHandle archiveHandle = prepareArchiveHandle("archives/7z/hello.7z", ".7z",
                "application/x-7z-compressed")) {
            assertThat(archiveHandle.toString()).contains("SevenZFileHandle");
        }
    }

    @Test
    public void getCommonArchive_zipFile_shouldBeZipFileHandle() throws Exception {
        try (ArchiveHandle archiveHandle = prepareArchiveHandle("archives/zip/hello.zip", ".zip",
                "application/zip")) {
            assertThat(archiveHandle.toString()).contains("ZipFileHandle");
        }
    }

    @Test
    public void close_zipFile_shouldBeSuccess() throws Exception {
        try (ArchiveHandle archiveHandle = prepareArchiveHandle("archives/zip/hello.zip", ".zip",
                "application/zip")) {
            assertThat(archiveHandle).isNotNull();
        }
    }

    @Test
    public void close_sevenZFile_shouldBeSuccess() throws Exception {
        try (ArchiveHandle archiveHandle = prepareArchiveHandle("archives/7z/hello.7z", ".7z",
                "application/x-7z-compressed")) {
            assertThat(archiveHandle).isNotNull();
        }
    }

    @Test
    public void closeInputStream_zipFile_shouldBeSuccess() throws Exception {
        try (ArchiveHandle archiveHandle = prepareArchiveHandle("archives/zip/hello.zip", ".zip",
                "application/zip")) {
            try (InputStream inputStream = archiveHandle.getInputStream(
                    requireNonNull(getFileInArchive(archiveHandle.getEntries())))) {
                assertThat(inputStream).isNotNull();
            }
        }
    }

    @Test
    public void close_zipFile_shouldNotOpen() throws Exception {
        ParcelFileDescriptor parcelFileDescriptor = mArchiveFileTestRule.openAssetFile(
                "archives/zip/hello.zip", ".zip");

        ArchiveHandle archiveHandle = ArchiveHandle.create(parcelFileDescriptor, "application/zip");

        archiveHandle.close();

        FileInputStream fileInputStream = new FileInputStream(
                parcelFileDescriptor.getFileDescriptor());
        assertThat(fileInputStream).isNotNull();
    }

    @Test
    public void getInputStream_zipFile_shouldHaveTheSameContent() throws Exception {
        ParcelFileDescriptor parcelFileDescriptor = mArchiveFileTestRule.openAssetFile(
                "archives/zip/hello.zip", ".zip");

        String expectedContent = mArchiveFileTestRule.getAssetText(
                "archives/original/hello/inside_folder/hello_insside.txt");

        ArchiveHandle archiveHandle = ArchiveHandle.create(parcelFileDescriptor, "application/zip");

        InputStream inputStream = archiveHandle.getInputStream(
                requireNonNull(getFileInArchive(archiveHandle.getEntries())));

        assertThat(ArchiveFileTestRule.getStringFromInputStream(inputStream)).isEqualTo(
                expectedContent);
    }

    @Test
    public void getInputStream_zipFileNotExistEntry_shouldFail() throws Exception {
        try (ArchiveHandle archiveHandle = prepareArchiveHandle("archives/zip/hello.zip", ".zip",
                "application/zip")) {
            ArchiveEntry archiveEntry = mock(ArchiveEntry.class);
            when(archiveEntry.getName()).thenReturn("/not_exist_entry");

            try {
                archiveHandle.getInputStream(archiveEntry);
                fail("It should not be here.");
            } catch (ClassCastException e) {
                /* do nothing */
            }
        }
    }

    @Test
    public void getInputStream_directoryEntry_shouldFail() throws Exception {
        try (ArchiveHandle archiveHandle = prepareArchiveHandle("archives/zip/hello.zip", ".zip",
                "application/zip")) {
            ArchiveEntry archiveEntry = mock(ArchiveEntry.class);
            when(archiveEntry.isDirectory()).thenReturn(true);

            try {
                archiveHandle.getInputStream(archiveEntry);
                fail("It should not be here.");
            } catch (IllegalArgumentException e) {
                /* expected, do nothing */
            }
        }
    }

    @Test
    public void getInputStream_negativeSizeEntry_shouldFail() throws Exception {
        try (ArchiveHandle archiveHandle = prepareArchiveHandle("archives/zip/hello.zip", ".zip",
                "application/zip")) {
            ArchiveEntry archiveEntry = mock(ArchiveEntry.class);
            when(archiveEntry.isDirectory()).thenReturn(false);
            when(archiveEntry.getSize()).thenReturn(-1L);

            try {
                archiveHandle.getInputStream(archiveEntry);
                fail("It should not be here.");
            } catch (IllegalArgumentException e) {
                /* expected, do nothing */
            }
        }
    }

    @Test
    public void getInputStream_emptyStringEntry_shouldFail() throws Exception {
        try (ArchiveHandle archiveHandle = prepareArchiveHandle("archives/zip/hello.zip", ".zip",
                "application/zip")) {
            ArchiveEntry archiveEntry = mock(ArchiveEntry.class);
            when(archiveEntry.isDirectory()).thenReturn(false);
            when(archiveEntry.getSize()).thenReturn(14L);
            when(archiveEntry.getName()).thenReturn("");

            try {
                archiveHandle.getInputStream(archiveEntry);
                fail("It should not be here.");
            } catch (IllegalArgumentException e) {
                /* expected, do nothing */
            }
        }
    }

    @Test
    public void getInputStream_sevenZFile_shouldHaveTheSameContent() throws Exception {
        ParcelFileDescriptor parcelFileDescriptor = mArchiveFileTestRule.openAssetFile(
                "archives/7z/hello.7z", ".7z");

        String expectedContent = mArchiveFileTestRule.getAssetText(
                "archives/original/hello/inside_folder/hello_insside.txt");

        ArchiveHandle archiveHandle = ArchiveHandle.create(parcelFileDescriptor,
                "application/x-7z-compressed");

        InputStream inputStream = archiveHandle.getInputStream(
                requireNonNull(getFileInArchive(archiveHandle.getEntries())));

        assertThat(ArchiveFileTestRule.getStringFromInputStream(inputStream)).isEqualTo(
                expectedContent);
    }

    @Test
    public void getInputStream_tarGzFile_shouldHaveTheSameContent() throws Exception {
        ParcelFileDescriptor parcelFileDescriptor = mArchiveFileTestRule.openAssetFile(
                "archives/tar_gz/hello.tgz", ".tar.gz");

        String expectedContent = mArchiveFileTestRule.getAssetText(
                "archives/original/hello/inside_folder/hello_insside.txt");

        ArchiveHandle archiveHandle = ArchiveHandle.create(parcelFileDescriptor,
                "application/x-compressed-tar");

        InputStream inputStream = archiveHandle.getInputStream(
                requireNonNull(getFileInArchive(archiveHandle.getEntries())));

        assertThat(ArchiveFileTestRule.getStringFromInputStream(inputStream)).isEqualTo(
                expectedContent);
    }

    @Test
    public void getInputStream_tarGzFileInvalidEntry_getNullInputStream() throws Exception {
        ParcelFileDescriptor parcelFileDescriptor = mArchiveFileTestRule.openAssetFile(
                "archives/tar_gz/hello.tgz", ".tar.gz");

        ArchiveHandle archiveHandle = ArchiveHandle.create(parcelFileDescriptor,
                "application/x-compressed-tar");

        ArchiveEntry archiveEntry = mock(ArchiveEntry.class);
        when(archiveEntry.getName()).thenReturn("");
        try {
            archiveHandle.getInputStream(archiveEntry);
            fail("It should not here");
        } catch (IllegalArgumentException | ArchiveException | CompressorException e) {
            /* expected, do nothing */
        }
    }

    @Test
    public void getInputStream_tarBrotliFile_shouldHaveTheSameContent() throws Exception {
        ParcelFileDescriptor parcelFileDescriptor = mArchiveFileTestRule.openAssetFile(
                "archives/brotli/hello.tar.br", ".tar.br");

        String expectedContent = mArchiveFileTestRule.getAssetText(
                "archives/original/hello/inside_folder/hello_insside.txt");

        ArchiveHandle archiveHandle = ArchiveHandle.create(parcelFileDescriptor,
                "application/x-brotli-compressed-tar");

        InputStream inputStream = archiveHandle.getInputStream(
                requireNonNull(getFileInArchive(archiveHandle.getEntries())));

        assertThat(ArchiveFileTestRule.getStringFromInputStream(inputStream)).isEqualTo(
                expectedContent);
    }

    @Test
    public void getEntries_zipFile_shouldTheSameWithList() throws Exception {
        try (ArchiveHandle archiveHandle = prepareArchiveHandle("archives/zip/hello.zip", ".zip",
                "application/zip")) {
            assertThat(transformToIterable(archiveHandle.getEntries())).containsAtLeastElementsIn(
                    sExpectEntries);
        }
    }

    @Test
    public void getEntries_tarFile_shouldTheSameWithList() throws Exception {
        try (ArchiveHandle archiveHandle = prepareArchiveHandle("archives/tar/hello.tar", ".tar",
                "application/x-gtar")) {
            assertThat(transformToIterable(archiveHandle.getEntries())).containsAtLeastElementsIn(
                    sExpectEntries);
        }
    }

    @Test
    public void getEntries_tgzFile_shouldTheSameWithList() throws Exception {
        try (ArchiveHandle archiveHandle = prepareArchiveHandle("archives/tar_gz/hello.tgz", ".tgz",
                "application/x-compressed-tar")) {
            assertThat(transformToIterable(archiveHandle.getEntries())).containsAtLeastElementsIn(
                    sExpectEntries);
        }
    }

    @Test
    public void getEntries_tarBzFile_shouldTheSameWithList() throws Exception {
        try (ArchiveHandle archiveHandle = prepareArchiveHandle("archives/tar_bz2/hello.tar.bz2",
                ".tar.bz2", "application/x-bzip-compressed-tar")) {
            assertThat(transformToIterable(archiveHandle.getEntries())).containsAtLeastElementsIn(
                    sExpectEntries);
        }
    }

    @Test
    public void getEntries_tarBrotliFile_shouldTheSameWithList() throws Exception {
        try (ArchiveHandle archiveHandle = prepareArchiveHandle("archives/brotli/hello.tar.br",
                ".tar.br", "application/x-brotli-compressed-tar")) {
            assertThat(transformToIterable(archiveHandle.getEntries())).containsAtLeastElementsIn(
                    sExpectEntries);
        }
    }

    @Test
    public void getEntries_tarXzFile_shouldTheSameWithList() throws Exception {
        try (ArchiveHandle archiveHandle = prepareArchiveHandle("archives/xz/hello.tar.xz",
                ".tar.xz", "application/x-xz-compressed-tar")) {
            assertThat(transformToIterable(archiveHandle.getEntries())).containsAtLeastElementsIn(
                    sExpectEntries);
        }
    }
}
