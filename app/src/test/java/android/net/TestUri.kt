package android.net

import android.os.Parcel

/**
 * A Uri for JVM tests, e.g. to make a [com.sharkula.gusle.data.Song]: Android's own can't be made
 * there (Uri.EMPTY is null, and parse and Builder aren't implemented). In this package, as only it
 * may extend Uri. It stands for no file, and equals only itself.
 */
object TestUri : Uri() {
    override fun buildUpon(): Builder = throw UnsupportedOperationException()
    override fun getAuthority(): String? = null
    override fun getEncodedAuthority(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun getEncodedPath(): String? = null
    override fun getEncodedQuery(): String? = null
    override fun getEncodedSchemeSpecificPart(): String = ""
    override fun getEncodedUserInfo(): String? = null
    override fun getFragment(): String? = null
    override fun getHost(): String? = null
    override fun getLastPathSegment(): String? = null
    override fun getPath(): String? = null
    override fun getPathSegments(): List<String> = emptyList()
    override fun getPort(): Int = -1
    override fun getQuery(): String? = null
    override fun getScheme(): String? = null
    override fun getSchemeSpecificPart(): String = ""
    override fun getUserInfo(): String? = null
    override fun isHierarchical(): Boolean = false
    override fun isRelative(): Boolean = true
    override fun toString(): String = "TestUri"
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 0
    override fun compareTo(other: Uri): Int = throw UnsupportedOperationException()
    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: Parcel, flags: Int) = throw UnsupportedOperationException()
}
