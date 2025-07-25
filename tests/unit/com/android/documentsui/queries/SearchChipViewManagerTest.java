/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.documentsui.queries;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;

import static java.util.Objects.requireNonNull;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DocumentsContract;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.documentsui.IconUtils;
import com.android.documentsui.R;
import com.android.documentsui.base.MimeTypes;
import com.android.documentsui.base.Shared;
import com.android.documentsui.flags.Flags;
import com.android.documentsui.util.VersionUtils;

import com.google.android.material.chip.Chip;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class SearchChipViewManagerTest {

    private static final String LARGE_FILES_CHIP_MIME_TYPE = "";
    private static final String FROM_THIS_WEEK_CHIP_MIME_TYPE = "";
    private static final String[] TEST_MIME_TYPES_INCLUDING_DOCUMENT =
            new String[]{"image/*", "video/*", "text/*"};
    private static final String[] TEST_MIME_TYPES =
            new String[]{"image/*", "video/*"};
    private static final String[] TEST_OTHER_TYPES =
            new String[]{LARGE_FILES_CHIP_MIME_TYPE, FROM_THIS_WEEK_CHIP_MIME_TYPE};
    private static int CHIP_TYPE = 1000;

    private Context mContext;
    private SearchChipViewManager mSearchChipViewManager;
    private LinearLayout mChipGroup;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mContext.setTheme(com.android.documentsui.R.style.DocumentsTheme);
        mContext.getTheme().applyStyle(R.style.DocumentsDefaultTheme, false);
        mChipGroup = spy(new LinearLayout(mContext));

        mSearchChipViewManager = new SearchChipViewManager(mChipGroup);
        mSearchChipViewManager.initChipSets(new String[] {"*/*"});
    }

    @Test
    public void testInitChipSets_hasCorrectChipCount() throws Exception {
        mSearchChipViewManager.initChipSets(TEST_MIME_TYPES);
        mSearchChipViewManager.updateChips(new String[] {"*/*"});

        int totalChipLength = TEST_MIME_TYPES.length + TEST_OTHER_TYPES.length;
        assertThat(mChipGroup.getChildCount()).isEqualTo(totalChipLength);
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_USE_MATERIAL3})
    public void testChipIcon() {
        mSearchChipViewManager.initChipSets(
                new String[] {"image/*", "audio/*", "video/*", "text/*"});
        mSearchChipViewManager.updateChips(new String[] {"*/*"});

        Chip imageChip = (Chip) mChipGroup.getChildAt(0);
        assertDrawablesEqual(
                requireNonNull(imageChip.getChipIcon()),
                requireNonNull(mContext.getDrawable(R.drawable.ic_chip_image)));
        Chip audioChip = (Chip) mChipGroup.getChildAt(1);
        assertDrawablesEqual(
                requireNonNull(audioChip.getChipIcon()),
                requireNonNull(mContext.getDrawable(R.drawable.ic_chip_audio)));
        Chip videoChip = (Chip) mChipGroup.getChildAt(2);
        assertDrawablesEqual(
                requireNonNull(videoChip.getChipIcon()),
                requireNonNull(mContext.getDrawable(R.drawable.ic_chip_video)));
        Chip documentChip = (Chip) mChipGroup.getChildAt(3);
        assertDrawablesEqual(
                requireNonNull(documentChip.getChipIcon()),
                requireNonNull(mContext.getDrawable(R.drawable.ic_chip_document)));
    }

    @Test
    @RequiresFlagsDisabled({Flags.FLAG_USE_MATERIAL3})
    public void testChipIcon_M3Disabled() {
        mSearchChipViewManager.initChipSets(
                new String[] {"image/*", "audio/*", "video/*", "text/*"});
        mSearchChipViewManager.updateChips(new String[] {"*/*"});

        Chip imageChip = (Chip) mChipGroup.getChildAt(0);
        assertDrawablesEqual(
                requireNonNull(imageChip.getChipIcon()),
                requireNonNull(IconUtils.loadMimeIcon(mContext, "image/*")));
        Chip audioChip = (Chip) mChipGroup.getChildAt(1);
        assertDrawablesEqual(
                requireNonNull(audioChip.getChipIcon()),
                requireNonNull(IconUtils.loadMimeIcon(mContext, "audio/*")));
        Chip videoChip = (Chip) mChipGroup.getChildAt(2);
        assertDrawablesEqual(
                requireNonNull(videoChip.getChipIcon()),
                requireNonNull(IconUtils.loadMimeIcon(mContext, "video/*")));
        Chip documentChip = (Chip) mChipGroup.getChildAt(3);
        assertDrawablesEqual(
                requireNonNull(documentChip.getChipIcon()),
                requireNonNull(IconUtils.loadMimeIcon(mContext, MimeTypes.GENERIC_TYPE)));
    }

    @Test
    public void testUpdateChips_hasCorrectChipCount() throws Exception {
        mSearchChipViewManager.updateChips(TEST_MIME_TYPES);

        int totalChipLength = TEST_MIME_TYPES.length + TEST_OTHER_TYPES.length;
        assertThat(mChipGroup.getChildCount()).isEqualTo(totalChipLength);
    }

    @Test
    public void testUpdateChips_documentsFilterOnlyAvailableAboveR() throws Exception {
        mSearchChipViewManager.updateChips(TEST_MIME_TYPES_INCLUDING_DOCUMENT);

        int totalChipLength = TEST_MIME_TYPES_INCLUDING_DOCUMENT.length + TEST_OTHER_TYPES.length;
        if (VersionUtils.isAtLeastR()) {
            assertThat(mChipGroup.getChildCount()).isEqualTo(totalChipLength);
        } else {
            assertThat(mChipGroup.getChildCount()).isEqualTo(totalChipLength - 1);
        }
    }

    @Test
    public void testUpdateChips_withSingleMimeType_hasCorrectChipCount() throws Exception {
        mSearchChipViewManager.updateChips(new String[]{"image/*"});

        assertThat(mChipGroup.getChildCount()).isEqualTo(TEST_OTHER_TYPES.length);
    }

    @Test
    public void testGetCheckedChipMimeTypes_hasCorrectValue() throws Exception {
        mSearchChipViewManager.mCheckedChipItems = getFakeSearchChipDataList();

        String[] checkedMimeTypes =
                mSearchChipViewManager.getCheckedChipQueryArgs()
                        .getStringArray(DocumentsContract.QUERY_ARG_MIME_TYPES);

        assertThat(MimeTypes.mimeMatches(TEST_MIME_TYPES, checkedMimeTypes[0])).isTrue();
        assertThat(MimeTypes.mimeMatches(TEST_MIME_TYPES, checkedMimeTypes[1])).isTrue();
    }

    @Test
    public void testRestoreCheckedChipItems_hasCorrectValue() throws Exception {
        Bundle bundle = new Bundle();
        bundle.putIntArray(Shared.EXTRA_QUERY_CHIPS, new int[]{2});

        mSearchChipViewManager.restoreCheckedChipItems(bundle);

        assertThat(mSearchChipViewManager.mCheckedChipItems.size()).isEqualTo(1);
        Iterator<SearchChipData> iterator = mSearchChipViewManager.mCheckedChipItems.iterator();
        assertThat(iterator.next().getChipType()).isEqualTo(2);
    }

    @Test
    public void testSaveInstanceState_hasCorrectValue() throws Exception {
        mSearchChipViewManager.mCheckedChipItems = getFakeSearchChipDataList();
        Bundle bundle = new Bundle();

        mSearchChipViewManager.onSaveInstanceState(bundle);

        final int[] chipTypes = bundle.getIntArray(Shared.EXTRA_QUERY_CHIPS);
        assertThat(chipTypes.length).isEqualTo(1);
        assertThat(chipTypes[0]).isEqualTo(CHIP_TYPE);
    }

    @Test
    public void testBindMirrorGroup_sameValue() throws Exception {
        mSearchChipViewManager.updateChips(new String[] {"*/*"});

        ViewGroup mirror = spy(new LinearLayout(mContext));
        mSearchChipViewManager.bindMirrorGroup(mirror);

        assertThat(View.VISIBLE).isEqualTo(mirror.getVisibility());
        assertThat(mChipGroup.getChildCount()).isEqualTo(mirror.getChildCount());
        assertThat(mChipGroup.getChildAt(0).getTag()).isEqualTo(mirror.getChildAt(0).getTag());
    }

    @Test
    public void testBindMirrorGroup_showRow() throws Exception {
        mSearchChipViewManager.updateChips(new String[] {"image/*"});

        ViewGroup mirror = spy(new LinearLayout(mContext));
        mSearchChipViewManager.bindMirrorGroup(mirror);

        assertThat(View.VISIBLE).isEqualTo(mirror.getVisibility());
    }

    @Test
    public void testChipChecked_resetScroll() {
        // Mock chip group's parent chain according to search_chip_row.xml.
        FrameLayout parent = spy(new FrameLayout(mContext));
        HorizontalScrollView grandparent = spy(new HorizontalScrollView(mContext));
        parent.addView(mChipGroup);
        grandparent.addView(parent);
        // Verify that getParent().getParent() returns the HorizontalScrollView mock.
        ViewParent result = mChipGroup.getParent().getParent();
        assertEquals(grandparent, result);

        mSearchChipViewManager.initChipSets(
                new String[] {"image/*", "audio/*", "video/*", "text/*"});
        mSearchChipViewManager.updateChips(new String[] {"*/*"});

        // Manually set HorizontalScrollView's scrollX to something larger than 0.
        grandparent.scrollTo(100, 0);
        assertTrue(grandparent.getScaleX() > 0);

        assertEquals(6, mChipGroup.getChildCount());
        Chip lastChip = (Chip) mChipGroup.getChildAt(5);

        // chip.setChecked will trigger reorder animation, which needs to be run inside
        // the looper thread.
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            // Check last chip will move it to the first child and reset scroll view.
            lastChip.setChecked(true);
            assertEquals(0, grandparent.getScrollX());
        });
    }

    private static Set<SearchChipData> getFakeSearchChipDataList() {
        final Set<SearchChipData> chipDataList = new HashSet<>();
        chipDataList.add(new SearchChipData(CHIP_TYPE, 0 /* titleRes */, TEST_MIME_TYPES));
        return chipDataList;
    }

    private void assertDrawablesEqual(Drawable actual, Drawable expected) {
        Bitmap bitmap1 =
                Bitmap.createBitmap(
                        actual.getIntrinsicWidth(),
                        actual.getIntrinsicHeight(),
                        Bitmap.Config.ARGB_8888);
        Canvas canvas1 = new Canvas(bitmap1);
        actual.setBounds(0, 0, actual.getIntrinsicWidth(), actual.getIntrinsicHeight());
        actual.draw(canvas1);

        Bitmap bitmap2 =
                Bitmap.createBitmap(
                        expected.getIntrinsicWidth(),
                        expected.getIntrinsicHeight(),
                        Bitmap.Config.ARGB_8888);
        Canvas canvas2 = new Canvas(bitmap2);
        expected.setBounds(0, 0, expected.getIntrinsicWidth(), expected.getIntrinsicHeight());
        expected.draw(canvas2);

        assertTrue("Drawables are not equal", bitmap1.sameAs(bitmap2));
    }
}
