package com.andruid.magic.discodruid.service

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.ContentUris
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.provider.MediaStore
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.os.bundleOf
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.andruid.magic.discodruid.R
import com.andruid.magic.discodruid.data.*
import com.andruid.magic.discodruid.ui.activity.MainActivity
import com.andruid.magic.discodruid.util.buildMediaDescription
import com.andruid.magic.discodruid.util.buildMediaMetaData
import com.andruid.magic.discodruid.util.getAlbumArtBitmap
import com.andruid.magic.discodruid.util.toMediaItem
import com.andruid.magic.medialoader.model.Track
import com.andruid.magic.medialoader.repository.AlbumRepository
import com.andruid.magic.medialoader.repository.ArtistRepository
import com.andruid.magic.medialoader.repository.TrackRepository
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.google.android.exoplayer2.upstream.ContentDataSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DataSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.*
import kotlin.coroutines.CoroutineContext

class MusicService : MediaBrowserServiceCompat(), CoroutineScope, Player.EventListener {
    companion object {
        private const val MEDIA_NOTI_ID = 1
        private const val MUSIC_CHANNEL_ID = "music_playback_channel"
        private const val MEDIA_SERVICE = "MusicService"

        private const val MSG_STOP_SERVICE = 0
    }

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private val mediaSessionCompat by lazy {
        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON).setClass(
            this,
            MediaButtonReceiver::class.java
        )
        val pendingIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, 0)
        val mediaButtonReceiver = ComponentName(applicationContext, MediaButtonReceiver::class.java)

        MediaSessionCompat(applicationContext, MEDIA_SERVICE, mediaButtonReceiver, pendingIntent)
    }
    private val exoPlayer by lazy {
        SimpleExoPlayer.Builder(this)
            .build()
    }
    private val mediaSessionConnector by lazy { MediaSessionConnector(mediaSessionCompat) }
    private val mediaSessionCallback = MediaSessionCallback()
    private val concatenatingMediaSource by lazy { ConcatenatingMediaSource() }
    private val mediaHandler by lazy { Handler(MediaHandlerCallback()) }
    private val playerNotificationManager by lazy {
        PlayerNotificationManager.createWithNotificationChannel(this,
            MUSIC_CHANNEL_ID,
            R.string.media_channel_name,
            0,
            MEDIA_NOTI_ID,
            DescriptionAdapter(),
            object : PlayerNotificationManager.NotificationListener {
                override fun onNotificationPosted(
                    notificationId: Int,
                    notification: Notification,
                    ongoing: Boolean
                ) {
                    super.onNotificationPosted(notificationId, notification, ongoing)
                    if (!ongoing) {
                        stopForeground(false)
                    } else {
                        startForeground(notificationId, notification)
                    }
                }

                override fun onNotificationCancelled(
                    notificationId: Int,
                    dismissedByUser: Boolean
                ) {
                    super.onNotificationCancelled(notificationId, dismissedByUser)
                    stopSelf()
                }
            })
    }

    override fun onCreate() {
        super.onCreate()

        job.start()
        initExoPlayer()
        initMediaSession()
        initPlayerNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PREPARE_QUEUE) {
            val mode = intent.getStringExtra(EXTRA_TRACK_MODE) ?: MODE_ALL_TRACKS
            prepareTracks(mode)
        } else
            MediaButtonReceiver.handleIntent(mediaSessionCompat, intent)

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        mediaHandler.sendEmptyMessage(MSG_STOP_SERVICE)
    }

    override fun onDestroy() {
        super.onDestroy()

        job.cancel()
        mediaSessionCompat.release()
        mediaSessionConnector.setPlayer(null)
        exoPlayer.release()
        playerNotificationManager.setPlayer(null)
    }

    override fun onPositionDiscontinuity(reason: Int) {
        super.onPositionDiscontinuity(reason)

        if (reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT ||
            reason == Player.DISCONTINUITY_REASON_PERIOD_TRANSITION || reason == Player.DISCONTINUITY_REASON_INTERNAL
        ) {
            val track = (exoPlayer.currentTag as Track?) ?: return
            setCurrentTrack(track)
        }
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        val extras = bundleOf(BrowserRoot.EXTRA_OFFLINE to true)
        return BrowserRoot(getString(R.string.app_name), extras)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaItem>>,
        options: Bundle
    ) {
        result.detach()

        when {
            parentId.contains(LOAD_ALBUM) -> {
                val page = options.getInt(MediaBrowserCompat.EXTRA_PAGE)
                val pageSize = options.getInt(MediaBrowserCompat.EXTRA_PAGE_SIZE)

                launch {
                    val albums = AlbumRepository.getAllContent(pageSize, page * pageSize)
                    val mediaItems = albums.map { album -> album.toMediaItem() }
                    result.sendResult(mediaItems.toMutableList())
                }
            }
            parentId.contains(LOAD_ARTIST) -> {
                val page = options.getInt(MediaBrowserCompat.EXTRA_PAGE)
                val pageSize = options.getInt(MediaBrowserCompat.EXTRA_PAGE_SIZE)

                launch {
                    val artists = ArtistRepository.getAllContent(pageSize, page * pageSize)
                    val mediaItems = artists.map { artist -> artist.toMediaItem() }
                    result.sendResult(mediaItems.toMutableList())
                }
            }
            parentId.contains(LOAD_TRACK) -> {
                val page = options.getInt(MediaBrowserCompat.EXTRA_PAGE)
                val pageSize = options.getInt(MediaBrowserCompat.EXTRA_PAGE_SIZE)

                when (options.getString(EXTRA_TRACK_MODE)) {
                    MODE_ALBUM_TRACKS -> {
                        val albumId = options.getString(EXTRA_ALBUM_ID)!!
                        launch {
                            val tracks =
                                TrackRepository.getTracksForAlbum(
                                    albumId,
                                    pageSize,
                                    page * pageSize
                                )
                            val mediaItems = tracks.map { track -> track.toMediaItem() }
                            result.sendResult(mediaItems.toMutableList())
                        }
                    }
                    else -> {
                        launch {
                            val tracks = TrackRepository.getAllContent(pageSize, page * pageSize)
                            val mediaItems = tracks.map { track -> track.toMediaItem() }
                            result.sendResult(mediaItems.toMutableList())
                        }
                    }
                }
            }
        }
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaItem>>) {
        result.sendResult(ArrayList())
    }

    private fun prepareTracks(mode: String) {
        if (mode == MODE_ALL_TRACKS) {
            launch {
                val tracks = TrackRepository.getAllContent(PAGE_SIZE, 0)
                addTracksToQueue(tracks)
            }
        }
    }

    private fun addTracksToQueue(tracks: List<Track>) {
        tracks.forEach { track ->
            val uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                track.audioId
            )
            try {
                val dataSpec = DataSpec(uri)
                val dataSource = ContentDataSource(this)
                dataSource.open(dataSpec)

                val factory = DataSource.Factory { dataSource }

                val mediaSource = ProgressiveMediaSource.Factory(factory)
                    .setTag(track)
                    .createMediaSource(uri)

                playerNotificationManager.setPlayer(exoPlayer)

                concatenatingMediaSource.addMediaSource(mediaSource)
            } catch (e: Exception) {
                Log.e("serviceLog", "not found ${uri.path}")
                e.printStackTrace()
            }
        }

        exoPlayer.prepare(concatenatingMediaSource, false, false)
        exoPlayer.playWhenReady = true
    }

    private fun setCurrentTrack(track: Track) {
        mediaSessionCompat.setMetadata(track.buildMediaMetaData(this))
    }

    private fun initExoPlayer() {
        exoPlayer.apply {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.CONTENT_TYPE_MUSIC)
                .build()
            setAudioAttributes(audioAttributes, true)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
            addListener(this@MusicService)

            prepare(concatenatingMediaSource)
            playWhenReady = true
        }
    }

    private fun initMediaSession() {
        mediaSessionCompat.apply {
            setCallback(mediaSessionCallback)
            isActive = true
        }

        sessionToken = mediaSessionCompat.sessionToken

        mediaSessionConnector.apply {
            setQueueNavigator(object : TimelineQueueNavigator(mediaSessionCompat) {
                override fun getMediaDescription(
                    player: Player,
                    windowIndex: Int
                ): MediaDescriptionCompat {
                    return (exoPlayer.currentTag as Track?)?.buildMediaDescription(this@MusicService)
                        ?: run {
                            MediaDescriptionCompat.Builder()
                                .build()
                        }
                }
            })
            setPlayer(exoPlayer)
        }
    }

    private fun initPlayerNotification() {
        playerNotificationManager.apply {
            setMediaSessionToken(mediaSessionCompat.sessionToken)
            setUseChronometer(true)
            setSmallIcon(R.drawable.logo_round)
        }
    }

    private fun setMediaPlaybackState(state: Int, pos: Int) {
        val playbackStateBuilder = PlaybackStateCompat.Builder()
            .setActiveQueueItemId(pos.toLong())

        if (state == PlaybackStateCompat.STATE_PLAYING || state == PlaybackStateCompat.STATE_BUFFERING ||
            state == PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS || state == PlaybackStateCompat.STATE_SKIPPING_TO_NEXT
        ) {
            playbackStateBuilder.setActions(
                PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_STOP
            )
        } else {
            playbackStateBuilder.setActions(
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_STOP
            )
        }

        if (state == PlaybackStateCompat.STATE_PAUSED || state == PlaybackStateCompat.STATE_PLAYING) {
            try {
                playbackStateBuilder.setState(state, exoPlayer.currentPosition, 0f)
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }
        } else
            playbackStateBuilder.setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0f)

        mediaSessionCompat.setPlaybackState(playbackStateBuilder.build())
    }

    private inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onSetShuffleMode(shuffleMode: Int) {
            super.onSetShuffleMode(shuffleMode)

            mediaSessionCompat.setShuffleMode(shuffleMode)
            exoPlayer.shuffleModeEnabled = shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL
        }

        override fun onSetRepeatMode(repeatMode: Int) {
            super.onSetRepeatMode(repeatMode)

            mediaSessionCompat.setRepeatMode(repeatMode)
            exoPlayer.repeatMode = when (repeatMode) {
                PlaybackStateCompat.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ONE
                PlaybackStateCompat.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_OFF
            }
        }

        override fun onPlay() {
            super.onPlay()
            exoPlayer.playWhenReady = true
            mediaSessionCompat.isActive = true

            (exoPlayer.currentTag as Track?)?.let { setCurrentTrack(it) }
            setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING, exoPlayer.currentWindowIndex)
        }

        override fun onPause() {
            super.onPause()
            exoPlayer.playWhenReady = false
            setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED, exoPlayer.currentWindowIndex)
        }

        override fun onSkipToNext() {
            super.onSkipToNext()
            if (exoPlayer.hasNext())
                exoPlayer.next()
        }

        override fun onSkipToPrevious() {
            super.onSkipToPrevious()
            if (exoPlayer.hasPrevious())
                exoPlayer.previous()
        }
    }

    private inner class MediaHandlerCallback : Handler.Callback {
        override fun handleMessage(msg: Message): Boolean {
            when (msg.what) {
                MSG_STOP_SERVICE -> {
                    mediaHandler.removeCallbacksAndMessages(null)
                    stopForeground(true)
                    stopSelf()
                }
            }

            return true
        }
    }

    private inner class DescriptionAdapter : PlayerNotificationManager.MediaDescriptionAdapter {
        override fun getCurrentContentTitle(player: Player) =
            (player.currentTag as Track?)?.title ?: "No title"

        override fun createCurrentContentIntent(player: Player): PendingIntent? {
            val intent = Intent(this@MusicService, MainActivity::class.java)
            return PendingIntent.getActivity(
                this@MusicService, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        override fun getCurrentContentText(player: Player) =
            (player.currentTag as Track?)?.album ?: "No album"

        override fun getCurrentLargeIcon(
            player: Player,
            callback: PlayerNotificationManager.BitmapCallback
        ): Bitmap? {
            val track = (player.currentTag as Track?) ?: return null
            callback.onBitmap(this@MusicService.getAlbumArtBitmap(track.albumId))
            return null
        }
    }
}