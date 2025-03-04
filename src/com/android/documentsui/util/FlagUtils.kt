/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.documentsui.util

import com.android.documentsui.flags.Flags

/**
 * Wraps the static flags classes to enable a single place to refactor flag usage
 * (or combine usage when required).
 */
class FlagUtils {
    companion object {
        @JvmStatic
        fun isUseMaterial3FlagEnabled(): Boolean {
            return Flags.useMaterial3()
        }

        @JvmStatic
        fun isZipNgFlagEnabled(): Boolean {
            return Flags.zipNg()
        }

        @JvmStatic
        fun isUseSearchV2RwFlagEnabled(): Boolean {
            return Flags.useSearchV2Rw()
        }

        @JvmStatic
        fun isDesktopFileHandlingFlagEnabled(): Boolean {
            return Flags.desktopFileHandling()
        }

        @JvmStatic
        fun isHideRootsOnDesktopFlagEnabled(): Boolean {
            return Flags.hideRootsOnDesktop()
        }

        @JvmStatic
        fun isUsePeekPreviewFlagEnabled(): Boolean {
            return Flags.usePeekPreview()
        }
    }
}
