/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.content.pm.ResolveInfo;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.documentsui.ActionHandler;
import com.android.documentsui.R;


/**
 * Similar to {@link ProfileItem} but only used in the navigation rail.
 */
public class NavRailProfileItem extends ProfileItem {

    public NavRailProfileItem(ResolveInfo info, String title, ActionHandler actionHandler) {
        super(R.layout.nav_rail_item_root, info, title, actionHandler);
    }

    @Override
    public void bindView(View convertView) {
        final ImageView icon = convertView.findViewById(android.R.id.icon);
        final TextView titleView = convertView.findViewById(android.R.id.title);

        titleView.setText(title);
        titleView.setContentDescription(userId.getUserBadgedLabel(convertView.getContext(), title));

        bindIcon(icon);
    }
}
