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

package com.android.documentsui.base;

import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;

public final class Menus {

    private Menus() {}

    /**
     * Disables hidden menu items so that they are not invokable via command shortcuts
     */
    public static void disableHiddenItems(Menu menu, MenuItem... exclusions) {
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item.isVisible()) {
                item.setEnabled(true);
                continue;
            }
            if (contains(exclusions, item)) {
                continue;
            }
            item.setEnabled(false);
        }
    }

    /** Set enabled/disabled state of a menuItem, and updates its visibility. */
    public static void setEnabledAndVisible(@NonNull MenuItem item, boolean enabled) {
        item.setEnabled(enabled);
        item.setVisible(enabled);
    }

    private static boolean contains(MenuItem[] exclusions, MenuItem item) {
        for (int x = 0; x < exclusions.length; x++) {
            if (exclusions[x] == item) {
                return true;
            }
        }
        return false;
    }
}
