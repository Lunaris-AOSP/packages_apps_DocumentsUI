/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.documentsui.sidebar;

import static com.android.documentsui.base.Shared.compareToIgnoreCaseNullable;
import static com.android.documentsui.base.SharedMinimal.DEBUG;
import static com.android.documentsui.base.SharedMinimal.VERBOSE;
import static com.android.documentsui.util.FlagUtils.isHideRootsOnDesktopFlagEnabled;
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.ext.SdkExtensions;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnDragListener;
import android.view.View.OnGenericMotionListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.loader.app.LoaderManager;
import androidx.loader.app.LoaderManager.LoaderCallbacks;
import androidx.loader.content.Loader;

import com.android.documentsui.ActionHandler;
import com.android.documentsui.BaseActivity;
import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.DragHoverListener;
import com.android.documentsui.Injector;
import com.android.documentsui.Injector.Injected;
import com.android.documentsui.ItemDragListener;
import com.android.documentsui.R;
import com.android.documentsui.UserManagerState;
import com.android.documentsui.UserPackage;
import com.android.documentsui.base.BooleanConsumer;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.Events;
import com.android.documentsui.base.Features;
import com.android.documentsui.base.Providers;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;
import com.android.documentsui.roots.ProvidersAccess;
import com.android.documentsui.roots.ProvidersCache;
import com.android.documentsui.roots.RootsLoader;
import com.android.documentsui.util.CrossProfileUtils;
import com.android.modules.utils.build.SdkLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Display list of known storage backend roots.
 * This fragment will be used in:
 * * fixed_layout: as navigation tree (sidebar)
 * * drawer_layout: as navigation drawer
 * * nav_rail_layout: as navigation drawer and navigation rail.
 */
public class RootsFragment extends Fragment {

    private static final String TAG = "RootsFragment";
    private static final String EXTRA_INCLUDE_APPS = "includeApps";
    private static final String EXTRA_INCLUDE_APPS_INTENT = "includeAppsIntent";
    /**
     * A key used to store the container id in the RootFragment.
     * RootFragment is used in both navigation drawer and navigation rail, there are 2 instances
     * of the fragment rendered on the page, we need to know which one is which to render different
     * nav items inside.
     */
    private static final String EXTRA_CONTAINER_ID = "containerId";
    private static final int CONTEXT_MENU_ITEM_TIMEOUT = 500;

    private final OnItemClickListener mItemListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            final Item item = mAdapter.getItem(position);
            item.open();

            getBaseActivity().setRootsDrawerOpen(false);
        }
    };

    private final OnItemLongClickListener mItemLongClickListener = new OnItemLongClickListener() {
        @Override
        public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
            final Item item = mAdapter.getItem(position);
            return item.showAppDetails();
        }
    };

    private ListView mList;
    private RootsAdapter mAdapter;
    private LoaderCallbacks<Collection<RootInfo>> mCallbacks;
    private @Nullable OnDragListener mDragListener;

    @Injected
    private Injector<?> mInjector;

    @Injected
    private ActionHandler mActionHandler;

    private List<Item> mApplicationItemList;

    // Weather the fragment is using nav_rail_container_roots as its container (in nav_rail_layout).
    // This will always be false if isUseMaterial3FlagEnabled() flag is off.
    private boolean mUseRailAsContainer = false;

    /**
     * Show the RootsFragment inside the navigation drawer container.
     */
    public static RootsFragment show(FragmentManager fm, boolean includeApps, Intent intent) {
        return showWithLayout(R.id.container_roots, fm, includeApps, intent);
    }

    /**
     * Show the RootsFragment inside the navigation rail container.
     */
    public static RootsFragment showNavRail(FragmentManager fm, boolean includeApps,
            Intent intent) {
        return showWithLayout(R.id.nav_rail_container_roots, fm, includeApps, intent);
    }

    /**
     * Shows the {@link RootsFragment}.
     *
     * @param containerId the container id where the {@link RootsFragment} will be rendered into
     * @param fm          the FragmentManager for interacting with fragments associated with this
     *                    fragment's activity
     * @param includeApps if {@code true}, query the intent from the system and include apps in
     *                    the {@RootsFragment}.
     * @param intent      the intent to query for package manager
     */
    private static RootsFragment showWithLayout(
            @IdRes int containerId, FragmentManager fm, boolean includeApps, Intent intent) {
        final Bundle args = new Bundle();
        args.putBoolean(EXTRA_INCLUDE_APPS, includeApps);
        args.putParcelable(EXTRA_INCLUDE_APPS_INTENT, intent);
        if (isUseMaterial3FlagEnabled()) {
            args.putInt(EXTRA_CONTAINER_ID, containerId);
        }

        final RootsFragment fragment = new RootsFragment();
        fragment.setArguments(args);

        final FragmentTransaction ft = fm.beginTransaction();
        ft.replace(containerId, fragment);
        ft.commitAllowingStateLoss();

        return fragment;
    }

    /**
     * Get the RootsFragment instance for the navigation drawer.
     */
    public static RootsFragment get(FragmentManager fm) {
        return (RootsFragment) fm.findFragmentById(R.id.container_roots);
    }

    /**
     * Get the RootsFragment instance for the navigation drawer.
     */
    public static RootsFragment getNavRail(FragmentManager fm) {
        return (RootsFragment) fm.findFragmentById(R.id.nav_rail_container_roots);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        if (isUseMaterial3FlagEnabled()) {
            mUseRailAsContainer =
                    getArguments() != null
                            && getArguments().getInt(EXTRA_CONTAINER_ID)
                                    == R.id.nav_rail_container_roots;
        }

        mInjector = getBaseActivity().getInjector();

        final View view =
                inflater.inflate(
                        mUseRailAsContainer
                                ? R.layout.fragment_nav_rail_roots
                                : R.layout.fragment_roots,
                        container,
                        false);
        mList = (ListView) view.findViewById(R.id.roots_list);
        mList.setOnItemClickListener(mItemListener);
        // ListView does not have right-click specific listeners, so we will have a
        // GenericMotionListener to listen for it.
        // Currently, right click is viewed the same as long press, so we will have to quickly
        // register for context menu when we receive a right click event, and quickly unregister
        // it afterwards to prevent context menus popping up upon long presses.
        // All other motion events will then get passed to OnItemClickListener.
        mList.setOnGenericMotionListener(
                new OnGenericMotionListener() {
                    @Override
                    public boolean onGenericMotion(View v, MotionEvent event) {
                        if (Events.isMouseEvent(event)
                                && event.getButtonState() == MotionEvent.BUTTON_SECONDARY) {
                            int x = (int) event.getX();
                            int y = (int) event.getY();
                            return onRightClick(v, x, y, () -> {
                                mInjector.menuManager.showContextMenu(
                                        RootsFragment.this, v, x, y);
                            });
                        }
                        return false;
                    }
                });
        mList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        mList.setSelector(new ColorDrawable(Color.TRANSPARENT));
        return view;
    }

    private boolean onRightClick(View v, int x, int y, Runnable callback) {
        final int pos = mList.pointToPosition(x, y);
        final Item item = mAdapter.getItem(pos);

        // If a read-only root, no need to see if top level is writable (it's not)
        if (!(item instanceof RootItem) || !((RootItem) item).root.supportsCreate()) {
            return false;
        }

        final RootItem rootItem = (RootItem) item;
        getRootDocument(rootItem, (DocumentInfo doc) -> {
            rootItem.docInfo = doc;
            callback.run();
        });
        return true;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final BaseActivity activity = getBaseActivity();
        final ProvidersCache providers = DocumentsApplication.getProvidersCache(activity);
        final State state = activity.getDisplayState();

        mActionHandler = mInjector.actions;

        if (mInjector.config.dragAndDropEnabled()) {
            final DragHost host = new DragHost(
                    activity,
                    DocumentsApplication.getDragAndDropManager(activity),
                    this::getItem,
                    mActionHandler);
            final ItemDragListener<DragHost> listener = new ItemDragListener<DragHost>(host) {
                @Override
                public boolean handleDropEventChecked(View v, DragEvent event) {
                    final Item item = getItem(v);

                    assert (item.isRoot());

                    return item.dropOn(event);
                }
            };
            mDragListener = DragHoverListener.create(listener, mList);
            mList.setOnDragListener(mDragListener);
        }

        mCallbacks = new LoaderCallbacks<Collection<RootInfo>>() {
            @Override
            public Loader<Collection<RootInfo>> onCreateLoader(int id, Bundle args) {
                return new RootsLoader(activity, providers, state);
            }

            @Override
            public void onLoadFinished(
                    Loader<Collection<RootInfo>> loader, Collection<RootInfo> roots) {
                if (!isAdded()) {
                    return;
                }

                boolean shouldIncludeHandlerApp = getArguments().getBoolean(EXTRA_INCLUDE_APPS,
                        /* defaultValue= */ false);
                Intent handlerAppIntent = getArguments().getParcelable(EXTRA_INCLUDE_APPS_INTENT);

                final Intent intent = activity.getIntent();
                final boolean excludeSelf =
                        intent.getBooleanExtra(DocumentsContract.EXTRA_EXCLUDE_SELF, false);
                final String excludePackage = excludeSelf ? activity.getCallingPackage() : null;
                final boolean maybeShowBadge =
                        getBaseActivity().getDisplayState().supportsCrossProfile();

                // For action which supports cross profile, update the policy value in state if
                // necessary.
                ResolveInfo crossProfileResolveInfo = null;
                UserManagerState userManagerState = null;
                if (state.supportsCrossProfile() && handlerAppIntent != null) {
                    if (state.configStore.isPrivateSpaceInDocsUIEnabled()
                            && SdkLevel.isAtLeastS()) {
                        userManagerState = DocumentsApplication.getUserManagerState(getContext());
                        Map<UserId, Boolean> canForwardToProfileIdMap =
                                userManagerState.getCanForwardToProfileIdMap(handlerAppIntent);
                        updateCrossProfileMapStateAndMaybeRefresh(canForwardToProfileIdMap);
                    } else {
                        crossProfileResolveInfo = CrossProfileUtils.getCrossProfileResolveInfo(
                                UserId.CURRENT_USER, getContext().getPackageManager(),
                                handlerAppIntent, getContext(),
                                state.configStore.isPrivateSpaceInDocsUIEnabled());
                        updateCrossProfileStateAndMaybeRefresh(
                                /* canShareAcrossProfile= */ crossProfileResolveInfo != null);
                    }
                }

                if (state.configStore.isPrivateSpaceInDocsUIEnabled()
                        && userManagerState == null) {
                    userManagerState = DocumentsApplication.getUserManagerState(getContext());
                }

                List<Item> sortedItems = sortLoadResult(
                        getContext(),
                        state,
                        roots,
                        excludePackage,
                        shouldIncludeHandlerApp ? handlerAppIntent : null,
                        DocumentsApplication.getProvidersCache(getContext()),
                        getBaseActivity().getSelectedUser(),
                        getUserIds(),
                        maybeShowBadge,
                        userManagerState);

                // This will be removed when feature flag is removed.
                if (crossProfileResolveInfo != null && !Features.CROSS_PROFILE_TABS) {
                    // Add profile item if we don't support cross-profile tab.
                    sortedItems.add(new SpacerItem());
                    if (mUseRailAsContainer) {
                        sortedItems.add(new NavRailProfileItem(crossProfileResolveInfo,
                                crossProfileResolveInfo.loadLabel(
                                        getContext().getPackageManager()).toString(),
                                mActionHandler));
                    } else {
                        sortedItems.add(new ProfileItem(crossProfileResolveInfo,
                                crossProfileResolveInfo.loadLabel(
                                        getContext().getPackageManager()).toString(),
                                mActionHandler));
                    }
                }

                // Disable drawer if only one root
                activity.setRootsDrawerLocked(sortedItems.size() <= 1);

                // Get the first visible position and offset
                final int firstPosition = mList.getFirstVisiblePosition();
                View firstChild = mList.getChildAt(0);
                final int offset =
                        firstChild != null ? firstChild.getTop() - mList.getPaddingTop() : 0;
                final int oriItemCount = mAdapter != null ? mAdapter.getCount() : 0;
                mAdapter = new RootsAdapter(activity, sortedItems, mDragListener);
                mList.setAdapter(mAdapter);

                // recover the position.
                if (oriItemCount == mAdapter.getCount()) {
                    mList.setSelectionFromTop(firstPosition, offset);
                }

                mInjector.shortcutsUpdater.accept(roots);
                mInjector.appsRowManager.updateList(mApplicationItemList);
                mInjector.appsRowManager.updateView(activity);
                onCurrentRootChanged();
            }

            private List<UserId> getUserIds() {
                if (state.configStore.isPrivateSpaceInDocsUIEnabled() && SdkLevel.isAtLeastS()) {
                    return DocumentsApplication.getUserManagerState(getContext()).getUserIds();
                }
                return DocumentsApplication.getUserIdManager(getContext()).getUserIds();
            }

            @Override
            public void onLoaderReset(Loader<Collection<RootInfo>> loader) {
                mAdapter = null;
                mList.setAdapter(null);
            }
        };
    }

    /**
     * Updates the state values of whether we can share across profiles, if necessary. Also reload
     * documents stack if the selected user is not the current user.
     */
    private void updateCrossProfileStateAndMaybeRefresh(boolean canShareAcrossProfile) {
        final State state = getBaseActivity().getDisplayState();
        if (state.canShareAcrossProfile != canShareAcrossProfile) {
            state.canShareAcrossProfile = canShareAcrossProfile;
            if (!UserId.CURRENT_USER.equals(getBaseActivity().getSelectedUser())) {
                mActionHandler.loadDocumentsForCurrentStack();
            }
        }
    }

    private void updateCrossProfileMapStateAndMaybeRefresh(
            Map<UserId, Boolean> canForwardToProfileIdMap) {
        final State state = getBaseActivity().getDisplayState();
        if (!state.canForwardToProfileIdMap.equals(canForwardToProfileIdMap)) {
            state.canForwardToProfileIdMap = canForwardToProfileIdMap;
            if (!UserId.CURRENT_USER.equals(getBaseActivity().getSelectedUser())) {
                mActionHandler.loadDocumentsForCurrentStack();
            }
        }
    }

    /**
     * If the package name of other providers or apps capable of handling the original intent
     * include the preferred root source, it will have higher order than others.
     *
     * @param excludePackage   Exclude activities from this given package
     * @param handlerAppIntent When not null, apps capable of handling the original intent will
     *                         be included in list of roots (in special section at bottom).
     */
    @VisibleForTesting
    List<Item> sortLoadResult(
            Context context,
            State state,
            Collection<RootInfo> roots,
            @Nullable String excludePackage,
            @Nullable Intent handlerAppIntent,
            ProvidersAccess providersAccess,
            UserId selectedUser,
            List<UserId> userIds,
            boolean maybeShowBadge,
            UserManagerState userManagerState) {
        final List<Item> result = new ArrayList<>();

        final RootItemListBuilder librariesBuilder = new RootItemListBuilder(selectedUser, userIds);
        final RootItemListBuilder storageProvidersBuilder = new RootItemListBuilder(selectedUser,
                userIds);
        final List<RootItem> otherProviders = new ArrayList<>();

        for (final RootInfo root : roots) {
            final RootItem item;

            if (root.isExternalStorageHome()) {
                continue;
            } else if (isHideRootsOnDesktopFlagEnabled()
                    && context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_PC)
                    && (root.isImages() || root.isVideos()
                    || root.isDocuments()
                    || root.isAudio())) {
                // Hide Images/Videos/Documents/Audio roots on desktop.
                Log.d(TAG, "Hiding " + root);
                continue;
            } else if (root.isLibrary() || root.isDownloads()) {
                item =
                        mUseRailAsContainer
                                ? new NavRailRootItem(root, mActionHandler, maybeShowBadge)
                                : new RootItem(root, mActionHandler, maybeShowBadge);
                librariesBuilder.add(item);
            } else if (root.isStorage()) {
                item =
                        mUseRailAsContainer
                                ? new NavRailRootItem(root, mActionHandler, maybeShowBadge)
                                : new RootItem(root, mActionHandler, maybeShowBadge);
                storageProvidersBuilder.add(item);
            } else {
                item =
                        mUseRailAsContainer
                                ? new NavRailRootItem(
                                        root,
                                        mActionHandler,
                                        providersAccess.getPackageName(root.userId, root.authority),
                                        maybeShowBadge)
                                : new RootItem(
                                        root,
                                        mActionHandler,
                                        providersAccess.getPackageName(root.userId, root.authority),
                                        maybeShowBadge);
                otherProviders.add(item);
            }
        }

        final List<RootItem> libraries = librariesBuilder.getList();
        final List<RootItem> storageProviders = storageProvidersBuilder.getList();

        final RootComparator comp = new RootComparator();
        Collections.sort(libraries, comp);
        Collections.sort(storageProviders, comp);

        if (VERBOSE) Log.v(TAG, "Adding library roots: " + libraries);
        result.addAll(libraries);

        // Only add the spacer if it is actually separating something.
        if (!result.isEmpty() && !storageProviders.isEmpty()) {
            result.add(new SpacerItem());
        }
        if (VERBOSE) Log.v(TAG, "Adding storage roots: " + storageProviders);
        result.addAll(storageProviders);

        final List<Item> rootList = new ArrayList<>();
        final List<Item> rootListOtherUser = new ArrayList<>();
        final List<List<Item>> rootListAllUsers = new ArrayList<>();
        for (int i = 0; i < userIds.size(); ++i) {
            rootListAllUsers.add(new ArrayList<>());
        }

        mApplicationItemList = new ArrayList<>();
        if (handlerAppIntent != null) {
            includeHandlerApps(state, handlerAppIntent, excludePackage, rootList, rootListOtherUser,
                    rootListAllUsers, otherProviders, userIds, maybeShowBadge);
        } else {
            // Only add providers
            otherProviders.sort(comp);
            for (RootItem item : otherProviders) {
                if (state.configStore.isPrivateSpaceInDocsUIEnabled()) {
                    createRootListsPrivateSpaceEnabled(item, userIds, rootListAllUsers);
                } else {
                    createRootListsPrivateSpaceDisabled(item, rootList, rootListOtherUser);
                }
                mApplicationItemList.add(item);
            }
        }

        List<Item> presentableList =
                state.configStore.isPrivateSpaceInDocsUIEnabled() && SdkLevel.isAtLeastS()
                        ? getPresentableListPrivateSpaceEnabled(
                        context, state, rootListAllUsers, userIds, userManagerState) :
                        getPresentableListPrivateSpaceDisabled(context, state, rootList,
                                rootListOtherUser);
        addListToResult(result, presentableList);
        return result;
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private List<Item> getPresentableListPrivateSpaceEnabled(Context context, State state,
            List<List<Item>> rootListAllUsers, List<UserId> userIds,
            UserManagerState userManagerState) {
        return new UserItemsCombiner(context.getResources(),
                context.getSystemService(DevicePolicyManager.class), state)
                .setRootListForAllUsers(rootListAllUsers)
                .createPresentableListForAllUsers(userIds, userManagerState.getUserIdToLabelMap());
    }

    private List<Item> getPresentableListPrivateSpaceDisabled(Context context, State state,
            List<Item> rootList, List<Item> rootListOtherUser) {
        return new UserItemsCombiner(context.getResources(),
                context.getSystemService(DevicePolicyManager.class), state)
                .setRootListForCurrentUser(rootList)
                .setRootListForOtherUser(rootListOtherUser)
                .createPresentableList();
    }

    private void addListToResult(List<Item> result, List<Item> rootList) {
        if (!result.isEmpty() && !rootList.isEmpty()) {
            result.add(new SpacerItem());
        }
        result.addAll(rootList);
    }

    /**
     * Adds apps capable of handling the original intent will be included in list of roots. If
     * the providers and apps are the same package name, combine them as RootAndAppItems.
     */
    private void includeHandlerApps(State state,
            Intent handlerAppIntent, @Nullable String excludePackage, List<Item> rootList,
            List<Item> rootListOtherUser, List<List<Item>> rootListAllUsers,
            List<RootItem> otherProviders, List<UserId> userIds, boolean maybeShowBadge) {
        if (VERBOSE) Log.v(TAG, "Adding handler apps for intent: " + handlerAppIntent);

        Context context = getContext();
        final Map<UserPackage, ResolveInfo> appsMapping = new HashMap<>();
        final Map<UserPackage, Item> appItems = new HashMap<>();

        final String myPackageName = context.getPackageName();
        for (UserId userId : userIds) {
            final PackageManager pm = userId.getPackageManager(context);
            final List<ResolveInfo> infos = pm.queryIntentActivities(
                    handlerAppIntent, PackageManager.MATCH_DEFAULT_ONLY);

            // In addition to hiding DocumentsUI from possible handler apps, the Android
            // Photopicker should also be hidden. ACTION_PICK_IMAGES is used to identify
            // the Photopicker package since that is the primary API.
            List<ResolveInfo> photopickerActivities;
            List<String> photopickerPackages;

            if (SdkLevel.isAtLeastR()
                    && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 2) {
                photopickerActivities = pm.queryIntentActivities(
                        new Intent(MediaStore.ACTION_PICK_IMAGES),
                        PackageManager.MATCH_DEFAULT_ONLY);
                photopickerPackages = photopickerActivities.stream()
                        .map(info -> info.activityInfo.packageName)
                .collect(Collectors.toList());
            } else {
                photopickerActivities = Collections.emptyList();
                photopickerPackages = Collections.emptyList();
            }

            // Omit ourselves and maybe calling package from the list
            for (ResolveInfo info : infos) {
                if (!info.activityInfo.exported) {
                    if (VERBOSE) {
                        Log.v(TAG, "Non exported activity: " + info.activityInfo);
                    }
                    continue;
                }

                final String packageName = info.activityInfo.packageName;

                // If the package name for the activity is in the list of Photopicker
                // activities, exclude it.
                if (photopickerPackages.contains(packageName)) {
                    continue;
                }

                if (!myPackageName.equals(packageName)
                        && !TextUtils.equals(excludePackage, packageName)) {
                    UserPackage userPackage = new UserPackage(userId, packageName);
                    appsMapping.put(userPackage, info);

                    if (!CrossProfileUtils.isCrossProfileIntentForwarderActivity(info)) {
                        final Item item =
                                mUseRailAsContainer
                                        ? new NavRailAppItem(
                                                info,
                                                info.loadLabel(pm).toString(),
                                                userId,
                                                mActionHandler)
                                        : new AppItem(
                                                info,
                                                info.loadLabel(pm).toString(),
                                                userId,
                                                mActionHandler);
                        appItems.put(userPackage, item);
                        if (VERBOSE) Log.v(TAG, "Adding handler app: " + item);
                    }
                }
            }
        }

        // If there are some providers and apps has the same package name, combine them as one item.
        for (RootItem rootItem : otherProviders) {
            final UserPackage userPackage = new UserPackage(rootItem.userId,
                    rootItem.getPackageName());
            final ResolveInfo resolveInfo = appsMapping.get(userPackage);

            final Item item;
            if (resolveInfo != null) {
                item =
                        mUseRailAsContainer
                                ? new NavRailRootAndAppItem(
                                        rootItem.root, resolveInfo, mActionHandler, maybeShowBadge)
                                : new RootAndAppItem(
                                        rootItem.root, resolveInfo, mActionHandler, maybeShowBadge);
                appItems.remove(userPackage);
            } else {
                item = rootItem;
            }

            if (state.configStore.isPrivateSpaceInDocsUIEnabled()) {
                createRootListsPrivateSpaceEnabled(item, userIds, rootListAllUsers);
            } else {
                createRootListsPrivateSpaceDisabled(item, rootList, rootListOtherUser);
            }
        }

        for (Item item : appItems.values()) {
            if (state.configStore.isPrivateSpaceInDocsUIEnabled()) {
                createRootListsPrivateSpaceEnabled(item, userIds, rootListAllUsers);
            } else {
                createRootListsPrivateSpaceDisabled(item, rootList, rootListOtherUser);
            }
        }

        final String preferredRootPackage = getResources().getString(
                R.string.preferred_root_package, "");
        final ItemComparator comp = new ItemComparator(preferredRootPackage);

        if (state.configStore.isPrivateSpaceInDocsUIEnabled()) {
            addToApplicationItemListPrivateSpaceEnabled(userIds, rootListAllUsers, comp, state);
        } else {
            addToApplicationItemListPrivateSpaceDisabled(rootList, rootListOtherUser, comp, state);
        }

    }

    private void addToApplicationItemListPrivateSpaceEnabled(List<UserId> userIds,
            List<List<Item>> rootListAllUsers, ItemComparator comp, State state) {
        for (int i = 0; i < userIds.size(); ++i) {
            rootListAllUsers.get(i).sort(comp);
            if (UserId.CURRENT_USER.equals(userIds.get(i))) {
                mApplicationItemList.addAll(rootListAllUsers.get(i));
            } else if (state.supportsCrossProfile && state.canInteractWith(userIds.get(i))) {
                mApplicationItemList.addAll(rootListAllUsers.get(i));
            }
        }
    }

    private void addToApplicationItemListPrivateSpaceDisabled(List<Item> rootList,
            List<Item> rootListOtherUser, ItemComparator comp, State state) {
        rootList.sort(comp);
        rootListOtherUser.sort(comp);
        if (state.supportsCrossProfile() && state.canShareAcrossProfile) {
            mApplicationItemList.addAll(rootList);
            mApplicationItemList.addAll(rootListOtherUser);
        } else {
            mApplicationItemList.addAll(rootList);
        }
    }

    private void createRootListsPrivateSpaceEnabled(Item item, List<UserId> userIds,
            List<List<Item>> rootListAllUsers) {
        for (int i = 0; i < userIds.size(); ++i) {
            if (userIds.get(i).equals(item.userId)) {
                rootListAllUsers.get(i).add(item);
                break;
            }
        }
    }

    private void createRootListsPrivateSpaceDisabled(Item item, List<Item> rootList,
            List<Item> rootListOtherUser) {
        if (UserId.CURRENT_USER.equals(item.userId)) {
            rootList.add(item);
        } else {
            rootListOtherUser.add(item);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        final Context context = getActivity();
        // Update the information for Storage's root
        if (context != null) {
            DocumentsApplication.getProvidersCache(context).updateAuthorityAsync(
                    ((BaseActivity) context).getSelectedUser(), Providers.AUTHORITY_STORAGE);
        }
        onDisplayStateChanged();
    }

    public void onDisplayStateChanged() {
        final Context context = getActivity();
        final State state = ((BaseActivity) context).getDisplayState();

        if (state.action == State.ACTION_GET_CONTENT) {
            mList.setOnItemLongClickListener(mItemLongClickListener);
        } else {
            mList.setOnItemLongClickListener(null);
            mList.setLongClickable(false);
        }

        LoaderManager.getInstance(this).restartLoader(2, null, mCallbacks);
    }

    public void onCurrentRootChanged() {
        if (mAdapter == null) {
            return;
        }

        final RootInfo root = ((BaseActivity) getActivity()).getCurrentRoot();
        for (int i = 0; i < mAdapter.getCount(); i++) {
            final Object item = mAdapter.getItem(i);
            if (item instanceof RootItem) {
                final RootInfo testRoot = ((RootItem) item).root;
                if (Objects.equals(testRoot, root)) {
                    // b/37358441 should reload all root title after configuration changed
                    root.title = testRoot.title;
                    mList.setItemChecked(i, true);
                    return;
                }
            }
        }
    }

    /**
     * Called when the selected user is changed. It reloads roots with the current user.
     */
    public void onSelectedUserChanged() {
        LoaderManager.getInstance(this).restartLoader(/* id= */ 2, /* args= */ null, mCallbacks);
    }

    /**
     * Attempts to shift focus back to the navigation drawer.
     */
    public boolean requestFocus() {
        return mList.requestFocus();
    }

    private BaseActivity getBaseActivity() {
        return (BaseActivity) getActivity();
    }

    @Override
    public void onCreateContextMenu(
            ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo adapterMenuInfo = (AdapterContextMenuInfo) menuInfo;
        final Item item = mAdapter.getItem(adapterMenuInfo.position);

        BaseActivity activity = getBaseActivity();
        item.createContextMenu(menu, activity.getMenuInflater(), mInjector.menuManager);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo adapterMenuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
        // There is a possibility that this is called from DirectoryFragment since
        // all fragments' onContextItemSelected gets called when any menu item is selected
        // This is to guard against it since DirectoryFragment's RecylerView does not have a
        // menuInfo
        if (adapterMenuInfo == null) {
            return false;
        }
        final RootItem rootItem = (RootItem) mAdapter.getItem(adapterMenuInfo.position);
        final int id = item.getItemId();
        if (id == R.id.root_menu_eject_root) {
            final View ejectIcon = adapterMenuInfo.targetView.findViewById(R.id.action_icon);
            ejectClicked(ejectIcon, rootItem.root, mActionHandler);
            return true;
        } else if (id == R.id.root_menu_open_in_new_window) {
            mActionHandler.openInNewWindow(new DocumentStack(rootItem.root));
            return true;
        } else if (id == R.id.root_menu_paste_into_folder) {
            mActionHandler.pasteIntoFolder(rootItem.root);
            return true;
        } else if (id == R.id.root_menu_settings) {
            mActionHandler.openSettings(rootItem.root);
            return true;
        }
        if (DEBUG) {
            Log.d(TAG, "Unhandled menu item selected: " + item);
        }
        return false;
    }

    private void getRootDocument(RootItem rootItem, RootUpdater updater) {
        // We need to start a GetRootDocumentTask so we can know whether items can be directly
        // pasted into root
        mActionHandler.getRootDocument(
                rootItem.root,
                CONTEXT_MENU_ITEM_TIMEOUT,
                (DocumentInfo doc) -> {
                    updater.updateDocInfoForRoot(doc);
                });
    }

    private Item getItem(View v) {
        final int pos = (Integer) v.getTag(R.id.item_position_tag);
        return mAdapter.getItem(pos);
    }

    static void ejectClicked(View ejectIcon, RootInfo root, ActionHandler actionHandler) {
        assert (ejectIcon != null);
        assert (!root.ejecting);
        ejectIcon.setEnabled(false);
        root.ejecting = true;
        actionHandler.ejectRoot(
                root,
                new BooleanConsumer() {
                    @Override
                    public void accept(boolean ejected) {
                        // Event if ejected is false, we should reset, since the op failed.
                        // Either way, we are no longer attempting to eject the device.
                        root.ejecting = false;

                        // If the view is still visible, we update its state.
                        if (ejectIcon.getVisibility() == View.VISIBLE) {
                            ejectIcon.setEnabled(!ejected);
                        }
                    }
                });
    }

    private static class RootComparator implements Comparator<RootItem> {
        @Override
        public int compare(RootItem lhs, RootItem rhs) {
            return lhs.root.compareTo(rhs.root);
        }
    }

    /**
     * The comparator of {@link AppItem}, {@link RootItem} and {@link RootAndAppItem}.
     * Sort by if the item's package name starts with the preferred package name,
     * then title, then summary. Because the {@link AppItem} doesn't have summary,
     * it will have lower order than other same title items.
     */
    @VisibleForTesting
    static class ItemComparator implements Comparator<Item> {
        private final String mPreferredPackageName;

        ItemComparator(String preferredPackageName) {
            mPreferredPackageName = preferredPackageName;
        }

        @Override
        public int compare(Item lhs, Item rhs) {
            // Sort by whether the item starts with preferred package name
            if (!mPreferredPackageName.isEmpty()) {
                if (lhs.getPackageName().startsWith(mPreferredPackageName)) {
                    if (!rhs.getPackageName().startsWith(mPreferredPackageName)) {
                        // lhs starts with it, but rhs doesn't start with it
                        return -1;
                    }
                } else {
                    if (rhs.getPackageName().startsWith(mPreferredPackageName)) {
                        // lhs doesn't start with it, but rhs starts with it
                        return 1;
                    }
                }
            }

            // Sort by title
            int score = compareToIgnoreCaseNullable(lhs.title, rhs.title);
            if (score != 0) {
                return score;
            }

            // Sort by summary. If the item is AppItem, it doesn't have summary.
            // So, the RootItem or RootAndAppItem will have higher order than AppItem.
            if (lhs instanceof RootItem) {
                return rhs instanceof RootItem ? compareToIgnoreCaseNullable(
                        ((RootItem) lhs).root.summary, ((RootItem) rhs).root.summary) : 1;
            }
            return rhs instanceof RootItem ? -1 : 0;
        }
    }

    @FunctionalInterface
    interface RootUpdater {
        void updateDocInfoForRoot(DocumentInfo doc);
    }
}
