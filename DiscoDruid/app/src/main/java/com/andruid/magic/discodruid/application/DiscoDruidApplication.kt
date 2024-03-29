package com.andruid.magic.discodruid.application

import android.app.Application
import com.andruid.magic.medialoader.repository.AlbumRepository
import com.andruid.magic.medialoader.repository.ArtistRepository
import com.andruid.magic.medialoader.repository.PlaylistRepository
import com.andruid.magic.medialoader.repository.TrackRepository

class DiscoDruidApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        TrackRepository.init(this)
        AlbumRepository.init(this)
        ArtistRepository.init(this)
        PlaylistRepository.init(this)
    }
}