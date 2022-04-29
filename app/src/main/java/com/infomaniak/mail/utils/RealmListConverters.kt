/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Recipient
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.message.Message
import io.realm.RealmList
import io.realm.realmListOf
import java.lang.reflect.Type

// TODO
// inline fun <reified T> typeAdapterOf(): Pair<Type, RealmListConverter<Any>> {
//     return Pair(object : TypeToken<RealmList<T>>() {}.type, RealmListConverter(T::class))
// }

// TODO
// class RealmListConverter<T : Any>(private val type: KClass<T>) : JsonSerializer<RealmList<T>>, JsonDeserializer<RealmList<T>> {
//     override fun serialize(src: RealmList<T>, typeOfSrc: Type?, context: JsonSerializationContext): JsonElement {
//         val jsonArray = JsonArray()
//         src.forEach { jsonArray.add(context.serialize(it)) }
//         return jsonArray
//     }
//
//     override fun deserialize(json: JsonElement, typeOfT: Type?, context: JsonDeserializationContext): RealmList<T> {
//         val folders = realmListOf<T>()
//         json.asJsonArray.forEach { folders.add(context.deserialize(it, type::class.java) as T) }
//         return folders
//     }
// }

inline fun <reified T> typeAdapterOfRealmListOf(converter: Any): Pair<Type, Any> {
    return Pair(object : TypeToken<RealmList<T>>() {}.type, converter)
}

class FolderRealmListConverter : JsonSerializer<RealmList<Folder>>, JsonDeserializer<RealmList<Folder>> {
    override fun serialize(src: RealmList<Folder>, typeOfSrc: Type?, context: JsonSerializationContext): JsonElement {
        val jsonArray = JsonArray()
        src.forEach { jsonArray.add(context.serialize(it)) }
        return jsonArray
    }

    override fun deserialize(json: JsonElement, typeOfT: Type?, context: JsonDeserializationContext): RealmList<Folder> {
        val folders = realmListOf<Folder>()
        json.asJsonArray.forEach { folders.add(context.deserialize(it, Folder::class.java) as Folder) }
        return folders
    }
}

class RecipientRealmListConverter : JsonSerializer<RealmList<Recipient>>, JsonDeserializer<RealmList<Recipient>> {
    override fun serialize(src: RealmList<Recipient>, typeOfSrc: Type?, context: JsonSerializationContext): JsonElement {
        val jsonArray = JsonArray()
        src.forEach { jsonArray.add(context.serialize(it)) }
        return jsonArray
    }

    override fun deserialize(json: JsonElement, typeOfT: Type?, context: JsonDeserializationContext): RealmList<Recipient> {
        val folders = realmListOf<Recipient>()
        json.asJsonArray.forEach { folders.add(context.deserialize(it, Recipient::class.java) as Recipient) }
        return folders
    }
}

class MessageRealmListConverter : JsonSerializer<RealmList<Message>>, JsonDeserializer<RealmList<Message>> {
    override fun serialize(src: RealmList<Message>, typeOfSrc: Type?, context: JsonSerializationContext): JsonElement {
        val jsonArray = JsonArray()
        src.forEach { jsonArray.add(context.serialize(it)) }
        return jsonArray
    }

    override fun deserialize(json: JsonElement, typeOfT: Type?, context: JsonDeserializationContext): RealmList<Message> {
        val folders = realmListOf<Message>()
        json.asJsonArray.forEach { folders.add(context.deserialize(it, Message::class.java) as Message) }
        return folders
    }
}

class AttachmentRealmListConverter : JsonSerializer<RealmList<Attachment>>, JsonDeserializer<RealmList<Attachment>> {
    override fun serialize(src: RealmList<Attachment>, typeOfSrc: Type?, context: JsonSerializationContext): JsonElement {
        val jsonArray = JsonArray()
        src.forEach { jsonArray.add(context.serialize(it)) }
        return jsonArray
    }

    override fun deserialize(json: JsonElement, typeOfT: Type?, context: JsonDeserializationContext): RealmList<Attachment> {
        val folders = realmListOf<Attachment>()
        json.asJsonArray.forEach { folders.add(context.deserialize(it, Attachment::class.java) as Attachment) }
        return folders
    }
}

class StringRealmListConverter : JsonSerializer<RealmList<String>>, JsonDeserializer<RealmList<String>> {
    override fun serialize(src: RealmList<String>, typeOfSrc: Type?, context: JsonSerializationContext): JsonElement {
        val jsonArray = JsonArray()
        src.forEach { jsonArray.add(context.serialize(it)) }
        return jsonArray
    }

    override fun deserialize(json: JsonElement, typeOfT: Type?, context: JsonDeserializationContext): RealmList<String> {
        val folders = realmListOf<String>()
        json.asJsonArray.forEach { folders.add(context.deserialize(it, String::class.java) as String) }
        return folders
    }
}
