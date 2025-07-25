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

package com.android.documentsui;

import static com.android.documentsui.flags.Flags.FLAG_HIDE_ROOTS_ON_DESKTOP_RO;
import static com.android.documentsui.flags.Flags.FLAG_USE_SEARCH_V2_READ_ONLY;
import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;

import android.app.Instrumentation;
import android.net.Uri;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.filters.HugeLongTest;
import com.android.documentsui.inspector.InspectorActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class FilesActivityUiTest extends ActivityTestJunit4<FilesActivity> {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        super.setUp();
        initTestFiles();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Override
    public void initTestFiles() throws RemoteException {
        Uri uri = mDocsHelper.createFolder(rootDir0, dirName1);
        mDocsHelper.createFolder(uri, childDir1);

        mDocsHelper.createDocument(rootDir0, "text/plain", "file0.log");
        mDocsHelper.createDocument(rootDir0, "image/png", "file1.png");
        mDocsHelper.createDocument(rootDir0, "text/csv", "file2.csv");

        mDocsHelper.createDocument(rootDir1, "text/plain", "anotherFile0.log");
        mDocsHelper.createDocument(rootDir1, "text/plain", "poodles.text");
    }

    // Recents is a strange meta root that gathers entries from other providers.
    // It is special cased in a variety of ways, which is why we just want
    // to be able to click on it.
    @Test
    public void testClickRecent() throws Exception {
        bots.roots.openRoot("Recent");

        boolean showSearchBar =
                context.getResources().getBoolean(R.bool.show_search_bar);
        if (showSearchBar) {
            bots.main.assertSearchBarShow();
        } else {
            bots.main.assertWindowTitle("Recent");
        }
    }

    @Test
    @RequiresFlagsDisabled(FLAG_HIDE_ROOTS_ON_DESKTOP_RO)
    public void testRootClick_SetsWindowTitle() throws Exception {
        bots.roots.openRoot("Images");
        bots.main.assertWindowTitle("Images");
    }

    private void filesListed() throws Exception {
        bots.directory.assertDocumentsPresent("file0.log", "file1.png", "file2.csv");
    }

    @Test
    @RequiresFlagsDisabled(FLAG_USE_SEARCH_V2_READ_ONLY)
    public void testFilesListed() throws Exception {
        filesListed();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testFilesListed_searchV2() throws Exception {
        filesListed();
    }

    private void filesListed_LiveUpdates() throws Exception {
        mDocsHelper.createDocument(rootDir0, "yummers/sandwich", "Ham & Cheese.sandwich");

        bots.directory.waitForDocument("Ham & Cheese.sandwich");
        bots.directory.assertDocumentsPresent(
                "file0.log", "file1.png", "file2.csv", "Ham & Cheese.sandwich");
    }

    @Test
    @RequiresFlagsDisabled(FLAG_USE_SEARCH_V2_READ_ONLY)
    public void testFilesList_LiveUpdate() throws Exception {
        filesListed_LiveUpdates();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testFilesList_LiveUpdate_searchV2() throws Exception {
        filesListed_LiveUpdates();
    }

    @Test
    public void testNavigate_byBreadcrumb() throws Exception {
        bots.directory.openDocument(dirName1);
        bots.directory.waitForDocument(childDir1);  // wait for known content
        bots.directory.assertDocumentsPresent(childDir1);

        device.waitForIdle();
        bots.breadcrumb.assertItemsPresent(dirName1, "TEST_ROOT_0");

        bots.breadcrumb.clickItem("TEST_ROOT_0");
        bots.directory.waitForDocument(dirName1);
    }

    @Test
    public void testNavigate_inFixedLayout_whileHasSelection() throws Exception {
        if (bots.main.inFixedLayout()) {
            bots.roots.openRoot(rootDir0.title);
            device.waitForIdle();
            bots.directory.selectDocument("file0.log", 1);

            // ensure no exception is thrown while navigating to a different root
            bots.roots.openRoot(rootDir1.title);
        }
    }

    @Test
    public void testNavigationToInspector() throws Exception {
        if(!features.isInspectorEnabled()) {
            return;
        }
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor(
                InspectorActivity.class.getName(), null, false);
        bots.directory.selectDocument("file0.log");
        bots.main.clickActionItem("Get info");
        monitor.waitForActivityWithTimeout(TIMEOUT);
    }

    @Test
    @HugeLongTest
    @RequiresFlagsDisabled(FLAG_HIDE_ROOTS_ON_DESKTOP_RO)
    public void testRootChange_UpdatesSortHeader() throws Exception {

        // switch to separate display modes for two separate roots. Each
        // mode has its own distinct sort header. This should be remembered
        // by files app.
        bots.roots.openRoot("Images");
        bots.main.switchToGridMode();
        bots.roots.openRoot("Videos");
        bots.main.switchToListMode();

        // Now switch back and assert the correct mode sort header mode
        // is restored when we load the root with that display mode.
        bots.roots.openRoot("Images");
        bots.sort.assertHeaderHide();
        if (bots.main.inFixedLayout()) {
            bots.roots.openRoot("Videos");
            bots.sort.assertHeaderShow();
        } else {
            bots.roots.openRoot("Videos");
            bots.sort.assertHeaderHide();
        }
    }
}
