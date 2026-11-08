package dev.gearturner.sparklauncher.feature.search

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

data class ContactHit(val lookupUri: Uri, val name: String)

/**
 * reads contact names through the system's own filter query, which already matches nicknames and
 * initials. only names and lookup keys are read; nothing is stored or cached between searches.
 */
internal class ContactSearch(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun search(query: String, limit: Int = MAX_RESULTS): List<ContactHit> {
        if (query.isBlank() || !hasPermission()) return emptyList()
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query.trim()))
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
        )
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val hits = ArrayList<ContactHit>()
                while (hits.size < limit && cursor.moveToNext()) {
                    val name = cursor.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                    val lookup = cursor.getString(1) ?: continue
                    hits += ContactHit(ContactsContract.Contacts.getLookupUri(cursor.getLong(0), lookup), name)
                }
                hits
            }.orEmpty()
        } catch (e: SecurityException) {
            // access can be revoked between the check and the query
            emptyList()
        } catch (e: SQLiteException) {
            Log.w(TAG, "contact search failed", e)
            emptyList()
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "contact search failed", e)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "ContactSearch"
        const val MAX_RESULTS = 5
    }
}
