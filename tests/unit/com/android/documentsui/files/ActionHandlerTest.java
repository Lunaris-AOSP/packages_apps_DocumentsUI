/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.documentsui.files;

import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;
import static com.android.documentsui.testing.IntentAsserts.assertHasAction;
import static com.android.documentsui.testing.IntentAsserts.assertHasData;
import static com.android.documentsui.testing.IntentAsserts.assertHasExtra;
import static com.android.documentsui.testing.IntentAsserts.assertHasExtraIntent;
import static com.android.documentsui.testing.IntentAsserts.assertHasExtraList;
import static com.android.documentsui.testing.IntentAsserts.assertHasExtraUri;
import static com.android.documentsui.testing.IntentAsserts.assertTargetsComponent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Parcelable;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Path;
import android.util.Pair;
import android.view.DragEvent;

import androidx.core.util.Preconditions;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;

import com.android.documentsui.AbstractActionHandler;
import com.android.documentsui.ModelId;
import com.android.documentsui.R;
import com.android.documentsui.TestActionModeAddons;
import com.android.documentsui.TestConfigStore;
import com.android.documentsui.archives.ArchivesProvider;
import com.android.documentsui.base.DebugFlags;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.flags.Flags;
import com.android.documentsui.inspector.InspectorActivity;
import com.android.documentsui.testing.ClipDatas;
import com.android.documentsui.testing.DocumentStackAsserts;
import com.android.documentsui.testing.Roots;
import com.android.documentsui.testing.TestActivityConfig;
import com.android.documentsui.testing.TestDocumentClipper;
import com.android.documentsui.testing.TestDragAndDropManager;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestFeatures;
import com.android.documentsui.testing.TestPeekViewManager;
import com.android.documentsui.testing.TestProvidersAccess;
import com.android.documentsui.testing.UserManagers;
import com.android.documentsui.ui.TestDialogController;
import com.android.documentsui.util.VersionUtils;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(Parameterized.class)
@MediumTest
public class ActionHandlerTest {

    private TestEnv mEnv;
    private TestActivity mActivity;
    private TestActionModeAddons mActionModeAddons;
    private TestDialogController mDialogs;
    private ActionHandler<TestActivity> mHandler;
    private TestDocumentClipper mClipper;
    private TestDragAndDropManager mDragAndDropManager;
    private TestPeekViewManager mPeekViewManager;
    private TestFeatures mFeatures;
    private TestConfigStore mTestConfigStore;
    private boolean refreshAnswer = false;
    @Mock private Runnable mMockCloseSelectionBar;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Parameter(0)
    public boolean isPrivateSpaceEnabled;

    /**
     * Parametrize values for {@code isPrivateSpaceEnabled} to run all the tests twice once with
     * private space flag enabled and once with it disabled.
     */
    @Parameters(name = "privateSpaceEnabled={0}")
    public static Iterable<?> data() {
        return Lists.newArrayList(true, false);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mFeatures = new TestFeatures();
        mEnv = TestEnv.create(mFeatures);
        mActivity = TestActivity.create(mEnv);
        mActivity.userManager = UserManagers.create();
        mActionModeAddons = new TestActionModeAddons();
        mDialogs = new TestDialogController();
        mClipper = new TestDocumentClipper();
        mDragAndDropManager = new TestDragAndDropManager();
        mPeekViewManager = new TestPeekViewManager(mActivity);
        mTestConfigStore = new TestConfigStore();
        mEnv.state.configStore = mTestConfigStore;

        isPrivateSpaceEnabled &= SdkLevel.isAtLeastS();
        if (isPrivateSpaceEnabled) {
            mTestConfigStore.enablePrivateSpaceInPhotoPicker();
            mEnv.state.canForwardToProfileIdMap.put(TestProvidersAccess.USER_ID, true);
        }

        mEnv.providers.configurePm(mActivity.packageMgr);
        ((TestActivityConfig) mEnv.injector.config).nextDocumentEnabled = true;
        mEnv.injector.dialogs = mDialogs;

        mHandler = createHandler();

        mEnv.selectDocument(TestEnv.FILE_GIF);
    }

    private void assertSelectionContainerClosed() {
        if (isUseMaterial3FlagEnabled()) {
            verify(mMockCloseSelectionBar, times(1)).run();
        } else {
            assertTrue(mActionModeAddons.finishActionModeCalled);
        }
    }

    @Test
    public void testOpenSelectedInNewWindow() {
        mHandler.openSelectedInNewWindow();

        DocumentStack path = new DocumentStack(Roots.create("123"), mEnv.model.getDocument("1"));

        Intent expected = LauncherActivity.createLaunchIntent(mActivity);
        expected.putExtra(Shared.EXTRA_STACK, (Parcelable) path);

        Intent actual = mActivity.startActivity.getLastValue();
        assertEquals(expected.toString(), actual.toString());
    }

    @Test
    @RequiresFlagsDisabled({Flags.FLAG_DESKTOP_FILE_HANDLING_RO})
    public void testOpenFileFlags() {
        mHandler.onDocumentOpened(TestEnv.FILE_GIF,
                com.android.documentsui.files.ActionHandler.VIEW_TYPE_PREVIEW,
                com.android.documentsui.files.ActionHandler.VIEW_TYPE_REGULAR, false);

        int expectedFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        Intent actual = mActivity.startActivity.getLastValue();
        assertEquals(expectedFlags, actual.getFlags());
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_DESKTOP_FILE_HANDLING_RO})
    public void testOpenFileFlagsDesktop() {
        mHandler.onDocumentOpened(TestEnv.FILE_GIF,
                com.android.documentsui.files.ActionHandler.VIEW_TYPE_PREVIEW,
                com.android.documentsui.files.ActionHandler.VIEW_TYPE_REGULAR, false);

        int expectedFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                | Intent.FLAG_ACTIVITY_MULTIPLE_TASK | Intent.FLAG_ACTIVITY_NEW_TASK;
        Intent actual = mActivity.startActivity.getLastValue();
        assertEquals(expectedFlags, actual.getFlags());
    }

    @Test
    public void testSpringOpenDirectory() {
        mHandler.springOpenDirectory(TestEnv.FOLDER_0);
        assertSelectionContainerClosed();
        assertEquals(TestEnv.FOLDER_0, mEnv.state.stack.peek());
    }

    @Test
    public void testCutSelectedDocuments_NoGivenSelection() {
        mEnv.populateStack();

        mEnv.selectionMgr.clearSelection();
        mHandler.cutToClipboard();
        mDialogs.assertDocumentsClippedNotShown();
    }

    @Test
    public void testCutSelectedDocuments_ContainsNonMovableItem() {
        mEnv.populateStack();
        mEnv.selectDocument(TestEnv.FILE_READ_ONLY);

        mHandler.cutToClipboard();
        mDialogs.assertDocumentsClippedNotShown();
        mDialogs.assertShowOperationUnsupported();
        mClipper.clipForCut.assertNotCalled();
    }

    @Test
    public void testCopySelectedDocuments_NoGivenSelection() {
        mEnv.populateStack();

        mEnv.selectionMgr.clearSelection();
        mHandler.copyToClipboard();
        mDialogs.assertDocumentsClippedNotShown();
    }

    @Test
    public void testShowDeleteDialog_NoSelection() {
        mEnv.populateStack();

        mEnv.selectionMgr.clearSelection();
        mHandler.showDeleteDialog();
        mActivity.startService.assertNotCalled();
        assertFalse(mActionModeAddons.finishActionModeCalled);
    }

    @Test
    public void testDeleteSelectedDocuments() {
        mEnv.populateStack();

        mEnv.selectionMgr.clearSelection();
        mEnv.selectDocument(TestEnv.FILE_PNG);

        List<DocumentInfo> docs = new ArrayList<>();
        docs.add(TestEnv.FILE_PNG);
        mHandler.deleteSelectedDocuments(docs, mEnv.state.stack.peek());

        mActivity.startService.assertCalled();
        assertSelectionContainerClosed();
    }

    @Test
    public void testShareSelectedDocuments_ShowsChooser() {
        mActivity.resources.strings.put(R.string.share_via, "Sharezilla!");
        mHandler.shareSelectedDocuments();

        mActivity.assertActivityStarted(Intent.ACTION_CHOOSER);
    }

    @Test
    public void testShareSelectedDocuments_Single() {
        mActivity.resources.strings.put(R.string.share_via, "Sharezilla!");
        mHandler.shareSelectedDocuments();

        Intent intent = assertHasExtraIntent(mActivity.startActivity.getLastValue());
        assertHasAction(intent, Intent.ACTION_SEND);
        assertFalse(intent.hasCategory(Intent.CATEGORY_TYPED_OPENABLE));
        assertFalse(intent.hasCategory(Intent.CATEGORY_OPENABLE));
        assertHasExtraUri(intent, Intent.EXTRA_STREAM);
    }

    @Test
    public void testShareSelectedDocuments_ArchivedFile() {
        mEnv = TestEnv.create(ArchivesProvider.AUTHORITY);
        mHandler = createHandler();

        mActivity.resources.strings.put(R.string.share_via, "Sharezilla!");
        mEnv.selectionMgr.clearSelection();
        mEnv.selectDocument(TestEnv.FILE_PDF);
        mHandler.shareSelectedDocuments();

        Intent intent = mActivity.startActivity.getLastValue();
        assertNull(intent);
    }

    @Test
    public void testShareSelectedDocuments_Multiple() {
        mActivity.resources.strings.put(R.string.share_via, "Sharezilla!");
        mEnv.selectDocument(TestEnv.FILE_PDF);
        mHandler.shareSelectedDocuments();

        Intent intent = assertHasExtraIntent(mActivity.startActivity.getLastValue());
        assertHasAction(intent, Intent.ACTION_SEND_MULTIPLE);
        assertFalse(intent.hasCategory(Intent.CATEGORY_TYPED_OPENABLE));
        assertFalse(intent.hasCategory(Intent.CATEGORY_OPENABLE));
        assertHasExtraList(intent, Intent.EXTRA_STREAM, 2);
    }

    @Test
    public void testShareSelectedDocuments_overShareLimit() {
        mActivity.resources.strings.put(R.string.share_via, "Sharezilla!");
        mEnv.selectMultipleFiles(500);
        mHandler.shareSelectedDocuments();

        Intent intent = mActivity.startActivity.getLastValue();
        assertNull(intent);
        mDialogs.assertShareOverLimitShown();
    }

    @Test
    public void testShareSelectedDocuments_VirtualFiles() {
        if (!mEnv.features.isVirtualFilesSharingEnabled()) {
            return;
        }

        mActivity.resources.strings.put(R.string.share_via, "Sharezilla!");
        mEnv.selectionMgr.clearSelection();
        mEnv.selectDocument(TestEnv.FILE_VIRTUAL);
        mHandler.shareSelectedDocuments();

        Intent intent = assertHasExtraIntent(mActivity.startActivity.getLastValue());
        assertHasAction(intent, Intent.ACTION_SEND);
        assertTrue(intent.hasCategory(Intent.CATEGORY_TYPED_OPENABLE));
        assertFalse(intent.hasCategory(Intent.CATEGORY_OPENABLE));
        assertHasExtraUri(intent, Intent.EXTRA_STREAM);
    }

    @Test
    public void testShareSelectedDocuments_RegularAndVirtualFiles() {
        mActivity.resources.strings.put(R.string.share_via, "Sharezilla!");
        mEnv.selectDocument(TestEnv.FILE_PNG);
        mEnv.selectDocument(TestEnv.FILE_VIRTUAL);
        mHandler.shareSelectedDocuments();

        Intent intent = assertHasExtraIntent(mActivity.startActivity.getLastValue());
        assertHasAction(intent, Intent.ACTION_SEND_MULTIPLE);

        assertFalse(intent.hasCategory(Intent.CATEGORY_OPENABLE));
        if (mEnv.features.isVirtualFilesSharingEnabled()) {
            assertTrue(intent.hasCategory(Intent.CATEGORY_TYPED_OPENABLE));
            assertHasExtraList(intent, Intent.EXTRA_STREAM, 3);
        }else {
            assertHasExtraList(intent, Intent.EXTRA_STREAM, 2);
        }
    }

    @Test
    public void testShareSelectedDocuments_OmitsPartialFiles() {
        mActivity.resources.strings.put(R.string.share_via, "Sharezilla!");
        mEnv.selectDocument(TestEnv.FILE_PARTIAL);
        mEnv.selectDocument(TestEnv.FILE_PNG);
        mHandler.shareSelectedDocuments();

        Intent intent = assertHasExtraIntent(mActivity.startActivity.getLastValue());
        assertHasAction(intent, Intent.ACTION_SEND_MULTIPLE);
        assertFalse(intent.hasCategory(Intent.CATEGORY_TYPED_OPENABLE));
        assertFalse(intent.hasCategory(Intent.CATEGORY_OPENABLE));
        assertHasExtraList(intent, Intent.EXTRA_STREAM, 2);
    }

    @Test
    public void testDocumentPicked_DefaultsToView() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.HOME;

        mHandler.openDocument(TestEnv.FILE_GIF, ActionHandler.VIEW_TYPE_PREVIEW,
                ActionHandler.VIEW_TYPE_REGULAR);
        mActivity.assertActivityStarted(Intent.ACTION_VIEW);
    }

    @Test
    public void testDocumentPicked_InArchive_QuickViewable() throws Exception {
        mActivity.resources.setQuickViewerPackage("corptropolis.viewer");
        mActivity.currentRoot = TestProvidersAccess.HOME;

        mHandler.openDocument(TestEnv.FILE_IN_ARCHIVE, ActionHandler.VIEW_TYPE_PREVIEW,
                ActionHandler.VIEW_TYPE_REGULAR);
        mActivity.assertActivityStarted(Intent.ACTION_QUICK_VIEW);
    }

    @Test
    public void testDocumentPicked_InArchive_Unopenable() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.HOME;

        mHandler.openDocument(TestEnv.FILE_IN_ARCHIVE, ActionHandler.VIEW_TYPE_PREVIEW,
                ActionHandler.VIEW_TYPE_REGULAR);
        mDialogs.assertViewInArchivesShownUnsupported();
    }

    @Test
    public void testDocumentPicked_PreviewsWhenResourceSet() throws Exception {
        mActivity.resources.setQuickViewerPackage("corptropolis.viewer");
        mActivity.currentRoot = TestProvidersAccess.HOME;

        mHandler.openDocument(TestEnv.FILE_GIF, ActionHandler.VIEW_TYPE_PREVIEW,
                ActionHandler.VIEW_TYPE_REGULAR);
        mActivity.assertActivityStarted(Intent.ACTION_QUICK_VIEW);
    }

    @Test
    public void testDocumentPicked_Downloads_ManagesApks() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.DOWNLOADS;
        TestEnv.FILE_APK.authority = TestProvidersAccess.DOWNLOADS.authority;

        mHandler.openDocument(TestEnv.FILE_APK, ActionHandler.VIEW_TYPE_PREVIEW,
                ActionHandler.VIEW_TYPE_REGULAR);
        mActivity.assertActivityStarted(DocumentsContract.ACTION_MANAGE_DOCUMENT);
    }

    @Test
    public void testDocumentPicked_Downloads_ManagesPartialFiles() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.DOWNLOADS;
        TestEnv.FILE_PARTIAL.authority = TestProvidersAccess.DOWNLOADS.authority;

        mHandler.openDocument(TestEnv.FILE_PARTIAL, ActionHandler.VIEW_TYPE_PREVIEW,
                ActionHandler.VIEW_TYPE_REGULAR);
        mActivity.assertActivityStarted(DocumentsContract.ACTION_MANAGE_DOCUMENT);
    }

    @Test
    public void testDocumentPicked_Recent_ManagesApks() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.RECENTS;
        TestEnv.FILE_APK.authority = TestProvidersAccess.DOWNLOADS.authority;

        mHandler.openDocument(TestEnv.FILE_APK, ActionHandler.VIEW_TYPE_PREVIEW,
                ActionHandler.VIEW_TYPE_REGULAR);
        mActivity.assertActivityStarted(DocumentsContract.ACTION_MANAGE_DOCUMENT);
    }

    @Test
    public void testDocumentPicked_Home_SendsActionViewForApks() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.HOME;

        mHandler.openDocument(TestEnv.FILE_APK, ActionHandler.VIEW_TYPE_PREVIEW,
                ActionHandler.VIEW_TYPE_REGULAR);
        mActivity.assertActivityStarted(Intent.ACTION_VIEW);
    }

    @Test
    public void testDocumentPicked_OpensArchives() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.HOME;
        mEnv.docs.nextDocument = TestEnv.FILE_ARCHIVE;

        final boolean result = mHandler.openDocument(TestEnv.FILE_ARCHIVE,
                ActionHandler.VIEW_TYPE_PREVIEW, ActionHandler.VIEW_TYPE_REGULAR);
        assertEquals(TestEnv.FILE_ARCHIVE, mEnv.state.stack.peek());
        assertEquals(false, result);
    }

    @Test
    public void testDocumentPicked_OpensDirectories() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.HOME;

        final boolean result = mHandler.openDocument(TestEnv.FOLDER_1,
                ActionHandler.VIEW_TYPE_PREVIEW, ActionHandler.VIEW_TYPE_REGULAR);
        assertEquals(TestEnv.FOLDER_1, mEnv.state.stack.peek());
        assertEquals(false, result);
    }

    // Require desktop file handling flag because when it's disabled proguard strips the
    // openDocumentViewOnly function because it's not used anywhere reachable by production code.
    @Test
    @RequiresFlagsEnabled({Flags.FLAG_DESKTOP_FILE_HANDLING_RO})
    public void testDocumentContextMenuOpen() throws Exception {
        mActivity.resources.setQuickViewerPackage("corptropolis.viewer");
        mActivity.currentRoot = TestProvidersAccess.HOME;

        // Test normal picking (i.e. double click) behaviour will quick view
        mHandler.openDocument(TestEnv.FILE_GIF, ActionHandler.VIEW_TYPE_PREVIEW,
                ActionHandler.VIEW_TYPE_REGULAR);
        mActivity.assertActivityStarted(Intent.ACTION_QUICK_VIEW);

        // And verify open via context menu will view instead
        mHandler.openDocumentViewOnly(TestEnv.FILE_GIF);
        mActivity.assertActivityStarted(Intent.ACTION_VIEW);
    }

    @Test
    @RequiresFlagsDisabled({Flags.FLAG_DESKTOP_FILE_HANDLING_RO})
    public void testShowChooser() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.DOWNLOADS;

        mHandler.showChooserForDoc(TestEnv.FILE_PDF);
        mActivity.assertActivityStarted(Intent.ACTION_CHOOSER);
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_DESKTOP_FILE_HANDLING_RO})
    public void testShowChooserDesktop() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.DOWNLOADS;

        mHandler.showChooserForDoc(TestEnv.FILE_PDF);
        Intent actual = mActivity.startActivity.getLastValue();
        assertEquals(Intent.ACTION_VIEW, actual.getAction());
        assertEquals("ComponentInfo{android/com.android.internal.app.ResolverActivity}",
                actual.getComponent().toString());
    }

    @Test
    public void testInitLocation_LaunchToStackLocation() {
        DocumentStack path = new DocumentStack(Roots.create("123"), mEnv.model.getDocument("1"));

        Intent intent = LauncherActivity.createLaunchIntent(mActivity);
        intent.putExtra(Shared.EXTRA_STACK, (Parcelable) path);

        mHandler.initLocation(intent);
        mActivity.refreshCurrentRootAndDirectory.assertCalled();
    }

    @Test
    public void testInitLocation_RestoresIfStackIsLoaded() throws Exception {
        mEnv.state.stack.changeRoot(TestProvidersAccess.DOWNLOADS);
        mEnv.state.stack.push(TestEnv.FOLDER_0);

        mHandler.initLocation(mActivity.getIntent());
        mActivity.restoreRootAndDirectory.assertCalled();
    }

    @Test
    public void testInitLocation_LoadsRootDocIfStackOnlyHasRoot() throws Exception {
        mEnv.state.stack.changeRoot(TestProvidersAccess.HAMMY);

        mHandler.initLocation(mActivity.getIntent());
        assertRootPicked(TestProvidersAccess.HAMMY.getUri());
    }

    @Test
    public void testInitLocation_forceDefaultsToRoot() throws Exception {
        mActivity.resources.strings.put(R.string.default_root_uri,
                TestProvidersAccess.DOWNLOADS.getUri().toString());

        mHandler.initLocation(mActivity.getIntent());
        assertRootPicked(TestProvidersAccess.DOWNLOADS.getUri());
    }

    @Test
    public void testInitLocation_BrowseRootWithoutRootId() throws Exception {
        Intent intent = mActivity.getIntent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(DocumentsContract.buildRootsUri(TestProvidersAccess.HAMMY.authority));

        mHandler.initLocation(intent);
        assertRootPicked(TestProvidersAccess.HAMMY.getUri());
    }

    @Test
    public void testInitLocation_BrowseRootWrongAuthority_ShowDefault() throws Exception {
        Intent intent = mActivity.getIntent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(DocumentsContract.buildRootsUri("com.test.wrongauthority"));
        mActivity.resources.strings.put(R.string.default_root_uri,
                TestProvidersAccess.HOME.getUri().toString());

        mHandler.initLocation(intent);
        assertRootPicked(TestProvidersAccess.HOME.getUri());
    }

    @Test
    public void testInitLocation_BrowseRoot() throws Exception {
        Intent intent = mActivity.getIntent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(TestProvidersAccess.PICKLES.getUri());

        mHandler.initLocation(intent);
        assertRootPicked(TestProvidersAccess.PICKLES.getUri());
    }

    @Test
    public void testInitLocation_LaunchToDocuments() throws Exception {
        if (!mEnv.features.isLaunchToDocumentEnabled()) {
            return;
        }

        mEnv.docs.nextIsDocumentsUri = true;
        mEnv.docs.nextPath = new Path(
                TestProvidersAccess.HOME.rootId,
                Arrays.asList(
                        TestEnv.FOLDER_0.documentId,
                        TestEnv.FOLDER_1.documentId));
        mEnv.docs.nextDocuments =
                Arrays.asList(TestEnv.FOLDER_0, TestEnv.FOLDER_1);

        mActivity.refreshCurrentRootAndDirectory.assertNotCalled();
        Intent intent = mActivity.getIntent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(TestEnv.FOLDER_1.derivedUri);
        mHandler.initLocation(intent);

        mEnv.beforeAsserts();

        DocumentStackAsserts.assertEqualsTo(mEnv.state.stack, TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0, TestEnv.FOLDER_1));
        mActivity.refreshCurrentRootAndDirectory.assertCalled();
    }

    @Test
    public void testInitLocation_LaunchToDownloads() throws Exception {
        Intent intent = mActivity.getIntent();
        intent.setAction(DownloadManager.ACTION_VIEW_DOWNLOADS);

        mHandler.initLocation(intent);
        assertRootPicked(TestProvidersAccess.DOWNLOADS.getUri());
    }

    // Ignoring the test because it uses hidden api DragEvent#obtain() and changes to the api is
    // causing failure on older base builds
    // TODO: b/343206763 remove dependence on hidden api
    @Ignore
    @Test
    public void testDragAndDrop_OnReadOnlyRoot() throws Exception {
        assumeTrue(VersionUtils.isAtLeastS());
        RootInfo root = new RootInfo(); // root by default has no SUPPORT_CREATE flag
        DragEvent event = DragEvent.obtain(DragEvent.ACTION_DROP, 1, 1, 0, 0, 0, 0, null, null,
                null, null, null, true);
        assertFalse(mHandler.dropOn(event, root));
    }

    // Ignoring the test because it uses hidden api DragEvent#obtain() and changes to the api is
    // causing failure on older base builds
    // TODO: b/343206763 remove dependence on hidden api
    @Ignore
    @Test
    public void testDragAndDrop_OnLibraryRoot() throws Exception {
        assumeTrue(VersionUtils.isAtLeastS());
        DragEvent event = DragEvent.obtain(DragEvent.ACTION_DROP, 1, 1, 0, 0, 0, 0, null, null,
                null, null, null, true);
        assertFalse(mHandler.dropOn(event, TestProvidersAccess.RECENTS));
    }

    // Ignoring the test because it uses hidden api DragEvent#obtain() and changes to the api is
    // causing failure on older base builds
    // TODO: b/343206763 remove dependence on hidden api
    @Ignore
    @Test
    public void testDragAndDrop_DropsOnWritableRoot() throws Exception {
        assumeTrue(VersionUtils.isAtLeastS());
        // DragEvent gets recycled in Android, so it is possible that by the time the callback is
        // called, event.getLocalState() and event.getClipData() returns null. This tests to ensure
        // our Clipper is getting the original CipData passed in.
        Object localState = new Object();
        ClipData clipData = ClipDatas.createTestClipData();
        DragEvent event = DragEvent.obtain(DragEvent.ACTION_DROP, 1, 1, 0, 0, 0, 0, localState,
                null, clipData, null, null, true);

        mHandler.dropOn(event, TestProvidersAccess.DOWNLOADS);
        event.recycle();

        Pair<ClipData, RootInfo> actual = mDragAndDropManager.dropOnRootHandler.getLastValue();
        assertSame(clipData, actual.first);
        assertSame(TestProvidersAccess.DOWNLOADS, actual.second);
    }

    @Test
    public void testRefresh_nullUri() throws Exception {
        refreshAnswer = true;
        mHandler.refreshDocument(null, (boolean answer) -> {
            refreshAnswer = answer;
        });

        mEnv.beforeAsserts();
        assertFalse(refreshAnswer);
    }

    @Test
    public void testRefresh_emptyStack() throws Exception {
        refreshAnswer = true;
        assertTrue(mEnv.state.stack.isEmpty());
        mHandler.refreshDocument(new DocumentInfo(), (boolean answer) -> {
            refreshAnswer = answer;
        });

        mEnv.beforeAsserts();
        assertFalse(refreshAnswer);
    }

    @Test
    public void testRefresh() throws Exception {
        refreshAnswer = false;
        mEnv.populateStack();
        mHandler.refreshDocument(mEnv.model.getDocument(
                ModelId.build(mEnv.model.mUserId, TestProvidersAccess.HOME.authority, "1")),
                (boolean answer) -> {
                    refreshAnswer = answer;
                });

        mEnv.beforeAsserts();
        if (mEnv.features.isContentRefreshEnabled()) {
            assertTrue(refreshAnswer);
        } else {
            assertFalse(refreshAnswer);
        }
    }

    @Test
    public void testAuthentication() throws Exception {
        PendingIntent intent = PendingIntent.getActivity(
                InstrumentationRegistry.getInstrumentation().getTargetContext(), 0, new Intent(),
                PendingIntent.FLAG_IMMUTABLE);

        mHandler.startAuthentication(intent);
        assertEquals(intent.getIntentSender(), mActivity.startIntentSender.getLastValue().first);
        assertEquals(AbstractActionHandler.CODE_AUTHENTICATION,
                mActivity.startIntentSender.getLastValue().second.intValue());
    }

    @Test
    public void testOnActivityResult_onOK() throws Exception {
        mHandler.onActivityResult(AbstractActionHandler.CODE_AUTHENTICATION, Activity.RESULT_OK,
                null);
        mActivity.refreshCurrentRootAndDirectory.assertCalled();
    }

    @Test
    public void testOnActivityResult_onNotOK() throws Exception {
        mHandler.onActivityResult(0, Activity.RESULT_OK, null);
        mActivity.refreshCurrentRootAndDirectory.assertNotCalled();

        mHandler.onActivityResult(AbstractActionHandler.CODE_AUTHENTICATION,
                Activity.RESULT_CANCELED, null);
        mActivity.refreshCurrentRootAndDirectory.assertNotCalled();
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_PEEK_PREVIEW_RO})
    public void testShowPeek() throws Exception {
        mHandler.showPreview(TestEnv.FILE_GIF);
        // The inspector activity is not called.
        mActivity.startActivity.assertNotCalled();
        mPeekViewManager.getPeekDocument().assertCalled();
        mPeekViewManager.getPeekDocument().assertLastArgument(TestEnv.FILE_GIF);
    }

    @Test
    @RequiresFlagsDisabled({Flags.FLAG_USE_PEEK_PREVIEW_RO})
    public void testShowInspector() throws Exception {
        mHandler.showPreview(TestEnv.FILE_GIF);

        mPeekViewManager.getPeekDocument().assertNotCalled();
        mActivity.startActivity.assertCalled();
        Intent intent = mActivity.startActivity.getLastValue();
        assertTargetsComponent(intent, InspectorActivity.class);
        assertHasData(intent, TestEnv.FILE_GIF.derivedUri);

        // should only send this under especial circumstances. See test below.
        assertFalse(intent.getExtras().containsKey(Intent.EXTRA_TITLE));
    }

    @Test
    @RequiresFlagsDisabled({Flags.FLAG_USE_PEEK_PREVIEW_RO})
    public void testShowInspector_DebugDisabled() throws Exception {
        mFeatures.debugSupport = false;

        mHandler.showPreview(TestEnv.FILE_GIF);
        Intent intent = mActivity.startActivity.getLastValue();

        assertHasExtra(intent, Shared.EXTRA_SHOW_DEBUG);
        assertFalse(intent.getExtras().getBoolean(Shared.EXTRA_SHOW_DEBUG));
    }

    @Test
    @RequiresFlagsDisabled({Flags.FLAG_USE_PEEK_PREVIEW_RO})
    public void testShowInspector_DebugEnabled() throws Exception {
        mFeatures.debugSupport = true;
        DebugFlags.setDocumentDetailsEnabled(true);

        mHandler.showPreview(TestEnv.FILE_GIF);
        Intent intent = mActivity.startActivity.getLastValue();

        assertHasExtra(intent, Shared.EXTRA_SHOW_DEBUG);
        assertTrue(intent.getExtras().getBoolean(Shared.EXTRA_SHOW_DEBUG));
        DebugFlags.setDocumentDetailsEnabled(false);
    }

    @Test
    @RequiresFlagsDisabled({Flags.FLAG_USE_PEEK_PREVIEW_RO})
    public void testShowInspector_OverridesRootDocumentName() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.PICKLES;
        mEnv.populateStack();

        // Verify test setup is correct, but not an assert related to the logic of our test.
        Preconditions.checkState(mEnv.state.stack.size() == 1);
        Preconditions.checkNotNull(mEnv.state.stack.peek());

        DocumentInfo rootDoc = mEnv.state.stack.peek();
        rootDoc.displayName = "poodles";

        mHandler.showPreview(rootDoc);
        Intent intent = mActivity.startActivity.getLastValue();
        assertEquals(
                TestProvidersAccess.PICKLES.title,
                intent.getExtras().getString(Intent.EXTRA_TITLE));
    }

    @Test
    @RequiresFlagsDisabled({Flags.FLAG_USE_PEEK_PREVIEW_RO})
    public void testShowInspector_OverridesRootDocumentNameX() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.PICKLES;
        mEnv.populateStack();
        mEnv.state.stack.push(TestEnv.FOLDER_2);

        // Verify test setup is correct, but not an assert related to the logic of our test.
        Preconditions.checkState(mEnv.state.stack.size() == 2);
        Preconditions.checkNotNull(mEnv.state.stack.peek());

        DocumentInfo rootDoc = mEnv.state.stack.peek();
        rootDoc.displayName = "poodles";

        mHandler.showPreview(rootDoc);
        Intent intent = mActivity.startActivity.getLastValue();
        assertFalse(intent.getExtras().containsKey(Intent.EXTRA_TITLE));
    }

    @Test
    public void testViewInOwner() {
        mEnv.populateStack();

        mEnv.selectionMgr.clearSelection();
        mEnv.selectDocument(TestEnv.FILE_PNG);

        mHandler.viewInOwner();
        mActivity.assertActivityStarted(DocumentsContract.ACTION_DOCUMENT_SETTINGS);
    }

    @Test
    public void testOpenSettings() {
        mHandler.openSettings(TestProvidersAccess.HAMMY);
        mActivity.assertActivityStarted(DocumentsContract.ACTION_DOCUMENT_ROOT_SETTINGS);
    }

    private void assertRootPicked(Uri expectedUri) throws Exception {
        mEnv.beforeAsserts();

        mActivity.rootPicked.assertCalled();
        RootInfo root = mActivity.rootPicked.getLastValue();
        assertNotNull(root);
        assertEquals(expectedUri, root.getUri());
    }

    private ActionHandler<TestActivity> createHandler() {
        return new ActionHandler<>(
                mActivity,
                mEnv.state,
                mEnv.providers,
                mEnv.docs,
                mEnv.searchViewManager,
                mEnv::lookupExecutor,
                mActionModeAddons,
                mMockCloseSelectionBar,
                mClipper,
                null, // clip storage, not utilized unless we venture into *jumbo* clip territory.
                mDragAndDropManager,
                mPeekViewManager,
                mEnv.injector);
    }
}
