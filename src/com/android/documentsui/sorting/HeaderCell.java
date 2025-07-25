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

package com.android.documentsui.sorting;

import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;

import android.animation.AnimatorInflater;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.AnimatorRes;
import androidx.annotation.StringRes;

import com.android.documentsui.R;

/**
 * A clickable, sortable table header cell layout. It updates its display when it binds to {@link
 * SortDimension} and changes the status of sorting when it's clicked.
 */
public class HeaderCell extends LinearLayout {

    private static final long ANIMATION_DURATION = 100;

    private @SortDimension.SortDirection int mCurDirection = SortDimension.SORT_DIRECTION_NONE;

    public HeaderCell(Context context) {
        this(context, null);
    }

    public HeaderCell(Context context, AttributeSet attrs) {
        super(context, attrs);

        LayoutTransition transition = getLayoutTransition();
        transition.setDuration(ANIMATION_DURATION);
        transition.setStartDelay(LayoutTransition.CHANGE_APPEARING, 0);
        transition.setStartDelay(LayoutTransition.CHANGE_DISAPPEARING, 0);
        transition.setStartDelay(LayoutTransition.CHANGING, 0);
    }

    void onBind(SortDimension dimension) {
        setVisibility(dimension.getVisibility());

        if (dimension.getVisibility() == View.VISIBLE) {
            TextView label = findViewById(R.id.label);
            label.setText(dimension.getLabelId());
            switch (dimension.getDataType()) {
                case SortDimension.DATA_TYPE_NUMBER:
                    setDataTypeNumber(label);
                    break;
                case SortDimension.DATA_TYPE_STRING:
                    setDataTypeString(label);
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Unknown column data type: " + dimension.getDataType() + ".");
            }

            if (mCurDirection != dimension.getSortDirection()) {
                ImageView arrow = findViewById(R.id.sort_arrow);
                switch (dimension.getSortDirection()) {
                    case SortDimension.SORT_DIRECTION_NONE:
                        arrow.setVisibility(View.GONE);
                        break;
                    case SortDimension.SORT_DIRECTION_ASCENDING:
                        showArrow(
                                arrow,
                                R.animator.arrow_rotate_up,
                                R.string.sort_direction_ascending);
                        break;
                    case SortDimension.SORT_DIRECTION_DESCENDING:
                        showArrow(
                                arrow,
                                R.animator.arrow_rotate_down,
                                R.string.sort_direction_descending);
                        break;
                    default:
                        throw new IllegalArgumentException(
                                "Unknown sort direction: " + dimension.getSortDirection() + ".");
                }

                mCurDirection = dimension.getSortDirection();
            }
        }
    }

    /**
     * Sets a listener on the sort arrow image. When Material 3 is enabled, the Sort Arrow has
     * "android:clickable" set to true (to enable a hover state). This stops the click listener from
     * falling through to the cell click listener and thus the sort arrow will need to handle clicks
     * itself.
     */
    public void setSortArrowListeners(
            View.OnClickListener clickListener,
            View.OnKeyListener keyListener,
            SortDimension dimension) {
        ImageView arrow = findViewById(R.id.sort_arrow);
        arrow.setTag(dimension);
        arrow.setOnKeyListener(keyListener);
        arrow.setOnClickListener(clickListener);
    }

    private void showArrow(
            ImageView arrow, @AnimatorRes int anim, @StringRes int contentDescriptionId) {
        arrow.setVisibility(View.VISIBLE);

        CharSequence description = getContext().getString(contentDescriptionId);
        arrow.setContentDescription(description);

        ObjectAnimator animator =
                (ObjectAnimator) AnimatorInflater.loadAnimator(getContext(), anim);
        animator.setTarget(arrow.getDrawable().mutate());
        animator.start();
    }

    private void setDataTypeNumber(View label) {
        if (isUseMaterial3FlagEnabled()) {
            label.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        } else {
            label.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
            setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        }
    }

    private void setDataTypeString(View label) {
        label.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
    }
}
