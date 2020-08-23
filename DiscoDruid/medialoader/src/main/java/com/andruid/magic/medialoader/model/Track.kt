package com.andruid.magic.medialoader.model

import android.annotation.SuppressLint
import android.database.Cursor
import android.net.Uri
import android.os.Parcelable
import android.provider.MediaStore
import android.util.Log
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Track(
    val path: String,
    val title: String,
    val artist: String = "Unknown",
    val album: String = "Unknown",
    val albumId: String,
    val audioId: Long,
    val duration: Long = 0
) : Parcelable

@SuppressLint("InlinedApi")
fun Cursor.readTrack(): Track {
    val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val trackId = getLong(getColumnIndex(MediaStore.Audio.Media._ID))

    Log.d("trackLog", "trackId = $trackId")

    return Track(
        audioId = trackId,
        artist = getString(getColumnIndex(MediaStore.Audio.Media.ARTIST)),
        title = getString(getColumnIndex(MediaStore.Audio.Media.TITLE)),
        path = Uri.withAppendedPath(uri, "$trackId").path!!,
        duration = getLong(getColumnIndex(MediaStore.Audio.Media.DURATION)) / 1000,
        albumId = getString(getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)),
        album = getString(getColumnIndex(MediaStore.Audio.Media.ALBUM))
    )
}