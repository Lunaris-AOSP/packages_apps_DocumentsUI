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

package com.android.documentsui.dirlist;

import static com.android.documentsui.base.SharedMinimal.VERBOSE;
import static com.android.documentsui.base.State.MODE_GRID;
import static com.android.documentsui.base.State.MODE_LIST;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.documentsui.ConfigStore;
import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.IconUtils;
import com.android.documentsui.ProviderExecutor;
import com.android.documentsui.R;
import com.android.documentsui.ThumbnailCache;
import com.android.documentsui.ThumbnailCache.Result;
import com.android.documentsui.ThumbnailLoader;
import com.android.documentsui.UserManagerState;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.MimeTypes;
import com.android.documentsui.base.State;
import com.android.documentsui.base.State.ViewMode;
import com.android.documentsui.base.UserId;
import com.android.modules.utils.build.SdkLevel;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A class to assist with loading and managing the Images (i.e. thumbnails and icons) associated
 * with items in the directory listing.
 */
public class IconHelper {
    private static final String TAG = "IconHelper";

    private final Context mContext;
    private final ThumbnailCache mThumbnailCache;

    // The display mode (MODE_GRID, MODE_LIST, etc).
    private int mMode;
    private Point mCurrentSize;
    private boolean mThumbnailsEnabled = true;
    private final boolean mMaybeShowBadge;
    @Nullable
    private final UserId mManagedUser;
    private final UserManagerState mUserManagerState;
    private final ConfigStore mConfigStore;

    /**
     * @param mode MODE_GRID or MODE_LIST
     */
    public IconHelper(Context context, int mode, boolean maybeShowBadge, ConfigStore configStore) {
        this(context, mode, maybeShowBadge, DocumentsApplication.getThumbnailCache(context),
                configStore != null && configStore.isPrivateSpaceInDocsUIEnabled() ? null
                        : DocumentsApplication.getUserIdManager(context).getManagedUser(),
                configStore != null && configStore.isPrivateSpaceInDocsUIEnabled()
                        ? DocumentsApplication.getUserManagerState(context) : null,
                configStore);
    }

    @VisibleForTesting
    IconHelper(Context context, int mode, boolean maybeShowBadge, ThumbnailCache thumbnailCache,
            @Nullable UserId managedUser, @Nullable UserManagerState userManagerState,
            ConfigStore configStore) {
        mContext = context;
        setViewMode(mode);
        mThumbnailCache = thumbnailCache;
        mManagedUser = managedUser;
        mMaybeShowBadge = maybeShowBadge;
        mUserManagerState = userManagerState;
        if (configStore == null) {
            configStore = DocumentsApplication.getConfigStore();
        }
        mConfigStore = configStore;
    }

    /**
     * Enables or disables thumbnails. When thumbnails are disabled, mime icons (or custom icons, if
     * specified by the document) are used instead.
     */
    public void setThumbnailsEnabled(boolean enabled) {
        mThumbnailsEnabled = enabled;
    }

    /**
     * Sets the current display mode. This affects the thumbnail sizes that are loaded.
     *
     * @param mode See {@link State#MODE_LIST} and {@link State#MODE_GRID}.
     */
    public void setViewMode(@ViewMode int mode) {
        mMode = mode;
        int thumbSize = getThumbSize(mode);
        mCurrentSize = new Point(thumbSize, thumbSize);
    }

    private int getThumbSize(int mode) {
        int thumbSize;
        switch (mode) {
            case MODE_GRID:
                thumbSize = mContext.getResources().getDimensionPixelSize(R.dimen.grid_width);
                break;
            case MODE_LIST:
                thumbSize = mContext.getResources().getDimensionPixelSize(
                        R.dimen.list_item_thumbnail_size);
                break;
            default:
                throw new IllegalArgumentException("Unsupported layout mode: " + mode);
        }
        return thumbSize;
    }

    /**
     * Cancels any ongoing load operations associated with the given ImageView.
     */
    public void stopLoading(ImageView icon) {
        final ThumbnailLoader oldTask = (ThumbnailLoader) icon.getTag();
        if (oldTask != null) {
            oldTask.preempt();
            icon.setTag(null);
        }
    }

    /**
     * Load thumbnails for a directory list item.
     *
     * @param doc         The document
     * @param iconThumb   The itemview's thumbnail icon.
     * @param iconMime    The itemview's mime icon. Hidden when iconThumb is shown.
     * @param subIconMime The second itemview's mime icon. Always visible.
     * @param thumbnailLoadedCallback The callback function which will be invoked after the
     *                                thumbnail is loaded, with a boolean parameter to indicate
     *                                if it's loaded or not.
     */
    public void load(
            DocumentInfo doc,
            ImageView iconThumb,
            ImageView iconMime,
            @Nullable ImageView subIconMime,
            @Nullable Consumer<Boolean> thumbnailLoadedCallback) {
        load(doc.derivedUri, doc.userId, doc.mimeType, doc.flags, doc.icon, doc.lastModified,
                iconThumb, iconMime, subIconMime, thumbnailLoadedCallback);
    }

    /**
     * Load thumbnails for a directory list item.
     *
     * @param uri             The URI for the file being represented.
     * @param mimeType        The mime type of the file being represented.
     * @param docFlags        Flags for the file being represented.
     * @param docIcon         Custom icon (if any) for the file being requested.
     * @param docLastModified the last modified value of the file being requested.
     * @param iconThumb       The itemview's thumbnail icon.
     * @param iconMime        The itemview's mime icon. Hidden when iconThumb is shown.
     * @param subIconMime     The second itemview's mime icon. Always visible.
     * @param thumbnailLoadedCallback The callback function which will be invoked after the
     *                                thumbnail is loaded, with a boolean parameter to indicate
     *                                if it's loaded or not.
     */
    public void load(Uri uri, UserId userId, String mimeType, int docFlags, int docIcon,
            long docLastModified, ImageView iconThumb, ImageView iconMime,
            @Nullable ImageView subIconMime, @Nullable Consumer<Boolean> thumbnailLoadedCallback) {
        boolean loadedThumbnail = false;

        final String docAuthority = uri.getAuthority();

        final boolean supportsThumbnail = (docFlags & Document.FLAG_SUPPORTS_THUMBNAIL) != 0;
        final boolean allowThumbnail = (mMode == MODE_GRID)
                || MimeTypes.mimeMatches(MimeTypes.VISUAL_MIMES, mimeType);
        final boolean showThumbnail = supportsThumbnail && allowThumbnail && mThumbnailsEnabled;
        if (showThumbnail) {
            loadedThumbnail =
                    loadThumbnail(
                            uri,
                            userId,
                            docAuthority,
                            docLastModified,
                            iconThumb,
                            iconMime,
                            thumbnailLoadedCallback);
        }

        final Drawable mimeIcon = getDocumentIcon(mContext, userId, docAuthority,
                DocumentsContract.getDocumentId(uri), mimeType, docIcon);
        if (subIconMime != null) {
            setMimeIcon(subIconMime, mimeIcon);
        }

        if (loadedThumbnail) {
            hideImageView(iconMime);
        } else {
            // Add a mime icon if the thumbnail is not shown.
            setMimeIcon(iconMime, mimeIcon);
            hideImageView(iconThumb);
        }
        if (thumbnailLoadedCallback != null) {
            thumbnailLoadedCallback.accept(loadedThumbnail);
        }
    }

    private boolean loadThumbnail(Uri uri, UserId userId, String docAuthority, long docLastModified,
            ImageView iconThumb, ImageView iconMime,
            @Nullable Consumer<Boolean> thumbnailLoadedCallback) {
        final Result result = mThumbnailCache.getThumbnail(uri, userId, mCurrentSize);

        try {
            final Bitmap cachedThumbnail = result.getThumbnail();
            iconThumb.setImageBitmap(cachedThumbnail);
            if (thumbnailLoadedCallback != null) {
                thumbnailLoadedCallback.accept(cachedThumbnail != null);
            }

            boolean stale = (docLastModified > result.getLastModified());
            if (VERBOSE) {
                Log.v(TAG,
                        String.format("Load thumbnail for %s, got result %d and stale %b.",
                                uri.toString(), result.getStatus(), stale));
            }
            if (!result.isExactHit() || stale) {
                final BiConsumer<View, View> animator =
                        (cachedThumbnail == null ? ThumbnailLoader.ANIM_FADE_IN :
                                ThumbnailLoader.ANIM_NO_OP);

                final ThumbnailLoader task = new ThumbnailLoader(uri, userId, iconThumb,
                        mCurrentSize, docLastModified,
                        bitmap -> {
                            if (bitmap != null) {
                                iconThumb.setImageBitmap(bitmap);
                                animator.accept(iconMime, iconThumb);
                            }
                            if (thumbnailLoadedCallback != null) {
                                thumbnailLoadedCallback.accept(bitmap != null);
                            }
                        }, true /* addToCache */);

                ProviderExecutor.forAuthority(docAuthority).execute(task);
            }

            return result.isHit();
        } finally {
            result.recycle();
        }
    }

    private void setMimeIcon(ImageView view, Drawable icon) {
        view.setImageDrawable(icon);
        view.setAlpha(1f);
    }

    private void hideImageView(ImageView view) {
        view.setImageDrawable(null);
        view.setAlpha(0f);
    }

    private Drawable getDocumentIcon(Context context, UserId userId, String authority, String id,
            String mimeType, int icon) {
        if (icon != 0) {
            return IconUtils.loadPackageIcon(context, userId, authority, icon, mMaybeShowBadge);
        } else {
            return IconUtils.loadMimeIcon(context, mimeType, authority, id, mMode);
        }
    }

    /**
     * Returns a mime icon or package icon for a {@link DocumentInfo}.
     */
    public Drawable getDocumentIcon(Context context, DocumentInfo doc) {
        return getDocumentIcon(
                context, doc.userId, doc.authority, doc.documentId, doc.mimeType, doc.icon);
    }

    /**
     * Returns true if we should show a briefcase icon for the given user.
     */
    public boolean shouldShowBadge(int userIdIdentifier) {
        if (mConfigStore.isPrivateSpaceInDocsUIEnabled() && SdkLevel.isAtLeastS()) {
            return mMaybeShowBadge
                    && mUserManagerState.getUserIds().size() > 1
                    && ActivityManager.getCurrentUser() != userIdIdentifier;
        }
        return mMaybeShowBadge && mManagedUser != null
                && mManagedUser.getIdentifier() == userIdIdentifier;
    }

    /** Returns label of the profile the icon belongs to. */
    public String getProfileLabel(int userIdIdentifier) {
        if (SdkLevel.isAtLeastS()) {
            return mUserManagerState.getUserIdToLabelMap().get(UserId.of(userIdIdentifier));
        } else {
            return "";
        }
    }
}
