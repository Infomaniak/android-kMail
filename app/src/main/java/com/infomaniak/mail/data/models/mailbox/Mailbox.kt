/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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
@file:UseSerializers(RealmListKSerializer::class)

package com.infomaniak.mail.data.models.mailbox

import androidx.core.app.NotificationManagerCompat
import com.infomaniak.core.ksuite.data.KSuite
import com.infomaniak.mail.data.api.ZeroAsNullLongSerializer
import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.data.models.FeatureFlag
import com.infomaniak.mail.data.models.Quotas
import com.infomaniak.mail.data.models.signature.Signature
import com.infomaniak.mail.utils.UnreadDisplay
import com.infomaniak.mail.utils.extensions.getDefault
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.serializers.RealmListKSerializer
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import kotlin.reflect.KMutableProperty0

@Serializable
class Mailbox : RealmObject {

    //region Remote data
    var uuid: String = ""
    var email: String = ""
    @SerialName("mailbox")
    var mailboxName: String = ""
    @SerialName("mailbox_id")
    var mailboxId: Int = -3 // AppSettings.DEFAULT_ID
    @SerialName("hosting_id")
    var hostingId: Int = 0
    @SerialName("link_id")
    var linkId: Int = 0
    @SerialName("is_primary")
    var isPrimary: Boolean = false
    @SerialName("is_locked")
    var isLocked: Boolean = false
    @SerialName("unseen_messages")
    var unreadCountRemote: Int = 0
    var aliases = realmListOf<String>()
    @SerialName("is_spam_filter")
    var isSpamFiltered: Boolean = false
    @SerialName("is_free")
    var isKSuitePerso: Boolean = false // Means it's any [KSuite.Perso.*] tier
    @SerialName("is_limited")
    var isLimited: Boolean = false
    @SerialName("is_part_of_ksuite")
    var isKSuitePro: Boolean = false // Means it's any [KSuite.Pro.*] tier
    @SerialName("is_ksuite_essential")
    var isKSuiteProFree: Boolean = false // Means it's a [KSuite.Pro.Free]
    @SerialName("is_part_of_starter_pack")
    var isPartOfStarterPack: Boolean = false // Means it's a legacy offer, bound to KSuite.Pro.Free's functionalities
    @SerialName("owner_or_admin")
    var isAdmin: Boolean = false
    @Serializable(with = ZeroAsNullLongSerializer::class)
    @SerialName("max_storage")
    var maxStorage: Long? = null
    //endregion

    //region Local data (Transient)

    // ------------- !IMPORTANT! -------------
    // Every field that is added in this Transient region should be declared in
    // `initLocalValue()` too to avoid loosing data when updating from the API.

    @Transient
    @PrimaryKey
    var objectId: String = ""
    @Transient
    var userId: Int = AppSettings.DEFAULT_ID
    @Transient
    var quotas: Quotas? = null
    @Transient
    var unreadCountLocal: Int = 0
    @Transient
    var permissions: MailboxPermissions? = null
    @Transient
    var signatures = realmListOf<Signature>()
    @Transient
    var haveSignatureNeverBeenFetched = true
    @Transient
    var _featureFlags = realmSetOf<String>()
        private set
    @Transient
    var externalMailFlagEnabled: Boolean = false
    @Transient
    var trustedDomains = realmListOf<String>()
    @Transient
    var sendersRestrictions: SendersRestrictions? = null
    //endregion

    //region UI data (Transient & Ignore)
    @Transient
    @Ignore
    val featureFlags = FeatureFlagSet(::_featureFlags)
    //endregion

    inline val kSuite: KSuite?
        get() = when {
            // For KSuite Pro tiers, only Free & Standard are relevant in kMail, all Pro paid tiers got the same functionalities
            isKSuitePro && isKSuiteProFree -> KSuite.Pro.Free
            isKSuitePro && !isKSuiteProFree -> KSuite.Pro.Standard
            isKSuitePerso && isLimited -> KSuite.Perso.Free
            isKSuitePerso && !isLimited -> KSuite.Perso.Plus
            isPartOfStarterPack -> KSuite.StarterPack
            else -> null // It's an older offer, but it checks out.
        }

    inline val channelGroupId get() = "$mailboxId"
    inline val channelId get() = "${mailboxId}_channel_id"
    inline val notificationGroupId get() = uuid.hashCode()
    inline val notificationGroupKey get() = uuid

    val unreadCountDisplay: UnreadDisplay
        get() = UnreadDisplay(
            count = unreadCountLocal,
            shouldDisplayPastille = unreadCountLocal == 0 && unreadCountRemote > 0,
        )

    private fun createObjectId(userId: Int): String = "${userId}_${this.mailboxId}"

    fun initLocalValues(
        userId: Int,
        quotas: Quotas?,
        inboxUnreadCount: Int?,
        permissions: MailboxPermissions?,
        signatures: List<Signature>?,
        featureFlags: Set<String>?,
        externalMailFlagEnabled: Boolean?,
        trustedDomains: List<String>?,
        sendersRestrictions: SendersRestrictions?,
    ) {
        this.objectId = createObjectId(userId)
        this.userId = userId
        this.quotas = quotas
        inboxUnreadCount?.let { this.unreadCountLocal = it }
        this.permissions = permissions
        signatures?.let(this.signatures::addAll)
        featureFlags?.let(this._featureFlags::addAll)
        externalMailFlagEnabled?.let { this.externalMailFlagEnabled = it }
        trustedDomains?.let(this.trustedDomains::addAll)
        sendersRestrictions?.let { this.sendersRestrictions = it }
    }

    fun getDefaultSignatureWithFallback(): Signature {
        return signatures.getDefault() ?: signatures.first()
    }

    fun notificationsIsDisabled(notificationManagerCompat: NotificationManagerCompat): Boolean = with(notificationManagerCompat) {
        val isGroupBlocked = getNotificationChannelGroupCompat(channelGroupId)?.isBlocked == true
        val isChannelBlocked = getNotificationChannelCompat(channelId)?.importance == NotificationManagerCompat.IMPORTANCE_NONE
        return@with !areNotificationsEnabled() || isGroupBlocked || isChannelBlocked
    }

    class FeatureFlagSet(val featureFlagsBacking: KMutableProperty0<RealmSet<String>>) {
        fun contains(featureFlag: FeatureFlag): Boolean {
            return featureFlagsBacking.get().contains(featureFlag.apiName)
        }

        fun setFeatureFlags(featureFlags: List<String>) = with(featureFlagsBacking.get()) {
            clear()
            addAll(featureFlags)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as FeatureFlagSet

            return featureFlagsBacking.get() == other.featureFlagsBacking.get()
        }

        override fun hashCode(): Int = featureFlagsBacking.get().hashCode()
    }

    companion object {
        val isLockedPropertyName get() = Mailbox::isLocked.name
    }
}
