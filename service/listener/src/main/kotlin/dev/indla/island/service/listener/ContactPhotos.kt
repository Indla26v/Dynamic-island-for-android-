package dev.indla.island.service.listener

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.indla.island.data.icons.IconStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Contact photos for callers and chat senders that arrive without one. Only with the optional
 * READ_CONTACTS permission; looked up by phone number or exact display name, read from the
 * phone's own contacts, cached in memory, never sent anywhere.
 */
@Singleton
class ContactPhotos @Inject constructor(
    @ApplicationContext private val context: Context,
    private val icons: IconStore,
) {
    /** name-or-number -> icon key, or "" when the contact has no photo (so we don't ask again). */
    private val cache = HashMap<String, String>()

    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Icon store key for this person's contact photo, or null. */
    fun photoKey(name: String?, phone: String? = null): String? {
        if (!granted()) return null
        val lookup = phone?.takeIf { it.count(Char::isDigit) >= 5 } ?: name?.takeIf { it.isNotBlank() } ?: return null
        cache[lookup]?.let { return it.ifEmpty { null } }
        val key = "contact|$lookup"
        val contactId = runCatching { if (phone != null && lookup == phone) byPhone(phone) else byName(lookup) }.getOrNull()
        val stream = contactId?.let {
            val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, it)
            runCatching { ContactsContract.Contacts.openContactPhotoInputStream(context.contentResolver, uri, false) }.getOrNull()
        }
        val bitmap = stream?.use { BitmapFactory.decodeStream(it) }
        if (bitmap == null) {
            cache[lookup] = ""
            return null
        }
        icons.put(key, if (bitmap.width > PX) bitmap.scale(PX, PX) else bitmap)
        cache[lookup] = key
        return key
    }

    private fun byPhone(phone: String): Long? {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phone))
        return context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use {
            if (it.moveToFirst()) it.getLong(0) else null
        }
    }

    private fun byName(name: String): Long? = context.contentResolver.query(
        ContactsContract.Contacts.CONTENT_URI,
        arrayOf(ContactsContract.Contacts._ID),
        "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} = ?",
        arrayOf(name),
        null,
    )?.use { if (it.moveToFirst()) it.getLong(0) else null }

    private companion object {
        const val PX = 160
    }
}
