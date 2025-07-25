/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.documentsui.services;

import static com.android.documentsui.services.FileOperationService.OPERATION_COPY;

import static com.google.common.collect.Lists.newArrayList;

import android.net.Uri;
import android.provider.DocumentsContract.Document;

import androidx.test.filters.MediumTest;

@MediumTest
public class CopyJobTest extends AbstractCopyJobTest<CopyJob> {

    public CopyJobTest() {
        super(OPERATION_COPY);
    }

    public void testCopyFiles() throws Exception {
        runCopyFilesTest();
    }

    public void testCopyVirtualTypedFile() throws Exception {
        runCopyVirtualTypedFileTest();
    }

    public void testCopyVirtualNonTypedFile() throws Exception {
        runCopyVirtualNonTypedFileTest();
    }

    public void testCopy_BackendSideVirtualTypedFile_Fallback() throws Exception {
        mDocs.assertChildCount(mDestRoot, 0);

        Uri testFile = mDocs.createDocumentWithFlags(
                mSrcRoot.documentId, "virtual/mime-type", "tokyo.sth",
                Document.FLAG_VIRTUAL_DOCUMENT | Document.FLAG_SUPPORTS_COPY
                        | Document.FLAG_SUPPORTS_MOVE, "application/pdf");

        CopyJob job = createJob(newArrayList(testFile));
        job.run();

        waitForJobFinished();
        mDocs.assertChildCount(mDestRoot, 1);
        mDocs.assertHasFile(mDestRoot, "tokyo.sth.pdf");  // Copy should convert file to PDF.

        JobProgress progress = job.getJobProgress();
        assertEquals(Job.STATE_COMPLETED, progress.state);
        assertFalse(progress.hasFailures);
        assertEquals("Copying tokyo.sth to " + mDestRoot.title, progress.msg);
        assertEquals(-1, progress.currentBytes);
        assertEquals(-1, progress.requiredBytes);
    }

    public void testCopyEmptyDir() throws Exception {
        runCopyEmptyDirTest();
    }

    public void testCopyDirRecursively() throws Exception {
        runCopyDirRecursivelyTest();
    }

    public void testCopyDirRecursively_loadingInFirstCursor() throws Exception {
        mDocs.setLoadingDuration(500);
        testCopyDirRecursively();
    }

    public void testNoCopyDirToSelf() throws Exception {
        runNoCopyDirToSelfTest();
    }

    public void testNoCopyDirToDescendent() throws Exception {
        runNoCopyDirToDescendentTest();
    }

    public void testCopyFileWithReadErrors() throws Exception {
        runCopyFileWithReadErrorsTest();
    }

    public void testCopyProgressWithFileCount() throws Exception {
        runCopyProgressForFileCountTest();
    }

    public void testCopyProgressWithByteCount() throws Exception {
        runCopyProgressForByteCountTest();
    }
}
