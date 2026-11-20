# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Navigation resolves custom app:argType values by class name at runtime.
-keepnames class com.infomaniak.core.ksuite.data.KSuite
-keepnames class com.infomaniak.core.ksuite.myksuite.ui.screens.MyKSuiteDashboardScreenData
-keepnames class com.infomaniak.mail.data.models.AttachmentType
-keepnames class com.infomaniak.mail.data.models.Bimi
-keepnames class com.infomaniak.mail.data.models.calendar.Attendee
-keepnames class com.infomaniak.mail.data.models.correspondent.Recipient
-keepnames class com.infomaniak.mail.data.models.draft.Draft$DraftMode
-keepnames class com.infomaniak.mail.ui.main.emojiPicker.EmojiPickerBottomSheetDialog$EmojiPickerObserverTarget
-keepnames class com.infomaniak.mail.ui.main.folderPicker.FolderPickerAction
-keepnames class com.infomaniak.mail.utils.extensions.AttachmentExt$AttachmentIntentType

# DynamicRealm migrations use model names to look up persisted tables.
-keepnames class com.infomaniak.mail.data.models.** implements io.realm.kotlin.types.RealmObject
-keepnames class com.infomaniak.mail.data.models.** implements io.realm.kotlin.types.EmbeddedRealmObject

# Core's Room converters use Gson, including fields without @SerializedName.
-keepclassmembers class com.infomaniak.core.auth.models.OrganizationAccount {
    !static !transient <fields>;
}
-keepclassmembers class com.infomaniak.core.auth.models.user.Card {
    !static !transient <fields>;
}
-keepclassmembers class com.infomaniak.core.auth.models.user.CardLink {
    !static !transient <fields>;
}
-keepclassmembers class com.infomaniak.core.auth.models.user.ContactMethod {
    !static !transient <fields>;
}
-keepclassmembers class com.infomaniak.core.auth.models.user.Phone {
    !static !transient <fields>;
}

# Core's token refresh also deserializes ApiToken with Gson.
-keepclassmembers class com.infomaniak.core.login.ApiToken {
    !static !transient <fields>;
}

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep names that are used from javascript
#-keepclasseswithmembers class com.infomaniak.mail.utils.WebViewUtils$JavascriptBridge {
#  public <methods>;
#}
