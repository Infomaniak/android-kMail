/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.infomaniak.mail.utils

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.facebook.stetho.okhttp3.StethoInterceptor
import com.infomaniak.lib.core.BuildConfig
import com.infomaniak.lib.core.networking.HttpUtils
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.nio.charset.StandardCharsets

object Utils {

    val UTF_8: String = StandardCharsets.UTF_8.name()

    private const val COIL_CACHE_DIR = "coil_cache"

    fun newImageLoader(context: Context, withAuthentication: Boolean = false): ImageLoader {
        return ImageLoader.Builder(context)
            .crossfade(true)
            .okHttpClient {
                OkHttpClient.Builder().apply {
                    addInterceptor(Interceptor { chain ->
                        chain.request().newBuilder()
                            .apply {
                                headers(HttpUtils.getHeaders())
                                removeHeader("Cache-Control")
                                if (!withAuthentication) removeHeader("Authorization")
                            }
                            .build()
                            .let(chain::proceed)
                    })
                    if (BuildConfig.DEBUG) {
                        addNetworkInterceptor(StethoInterceptor())
                    }
                }.build()
            }
            .memoryCache {
                MemoryCache.Builder(context).build()
            }
            .diskCache {
                DiskCache.Builder().directory(context.cacheDir.resolve(COIL_CACHE_DIR)).build()
            }
            .build()
    }
}
