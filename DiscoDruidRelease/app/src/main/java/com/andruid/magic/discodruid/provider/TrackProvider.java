package com.andruid.magic.discodruid.provider;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.content.ContentResolverCompat;

import com.andruid.magic.discodruid.data.Constants;
import com.andruid.magic.discodruid.model.Track;

import java.util.ArrayList;
import java.util.List;

public class TrackProvider {
    private final Cursor cursor;
    private final ContentResolver contentResolver;
    private String selection;
    private Uri uri;

    public TrackProvider(Context context) {
        contentResolver = context.getContentResolver();
        initSelection();
        uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        cursor = ContentResolverCompat.query(
                contentResolver,
                uri,
                getProjection(),
                selection,
                null,
                getSortOrder(),
                null
        );
    }

    public TrackProvider(Context context, Bundle options){
        contentResolver = context.getContentResolver();
        uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        initSelection();
        String[] projection = getProjection();
        String[] selectionArgs = null;
        String sortOrder = getSortOrder();
        if(options.containsKey(Constants.ALBUM_ID)) {
            selection = selection + " AND " + MediaStore.Audio.Media.ALBUM_ID + "=?";
            selectionArgs = new String[]{options.getString(Constants.ALBUM_ID)};
        }
        else if(options.containsKey(Constants.ARTIST_ID)){
            selection = selection + " AND " + MediaStore.Audio.Media.ARTIST_ID + "=?";
            selectionArgs = new String[]{options.getString(Constants.ARTIST_ID)};
        }
        else if(options.containsKey(Constants.AUDIO_ID_ARRAYLIST)){
            List<String> audioIdList = options.getStringArrayList(Constants.AUDIO_ID_ARRAYLIST);
            StringBuilder builder = new StringBuilder(selection);
            builder.append(" AND ");
            builder.append(MediaStore.Audio.Media._ID);
            builder.append(" IN (");
            if (audioIdList != null) {
                for(int i=0;i<audioIdList.size()-1;i++){
                    builder.append("?,");
                }
            }
            builder.append("?)");
            selection = builder.toString();
            if (audioIdList != null) {
                selectionArgs = audioIdList.toArray(new String[0]);
            }
        }
        else if(options.containsKey(Constants.PLAYLIST_ID)){
            uri = MediaStore.Audio.Playlists.Members.getContentUri("external", options.getLong(Constants.PLAYLIST_ID));
            projection = new String[]{
                    MediaStore.Audio.Playlists.Members.AUDIO_ID,
                    MediaStore.Audio.Playlists.Members.ARTIST,
                    MediaStore.Audio.Playlists.Members.TITLE,
                    MediaStore.Audio.Playlists.Members.DATA,
                    MediaStore.Audio.Playlists.Members.DURATION,
                    MediaStore.Audio.Playlists.Members.ALBUM_ID,
                    MediaStore.Audio.Playlists.Members.ALBUM
            };
            sortOrder = MediaStore.Audio.Playlists.Members.PLAY_ORDER;
        }
        cursor = ContentResolverCompat.query(
                contentResolver,
                uri,
                projection,
                selection,
                selectionArgs,
                sortOrder,
                null
        );
    }

    public int getListSize(){
        return cursor.getCount();
    }

    public List<Track> getTracksAtRange(int start, int end){
        List<Track> trackList = new ArrayList<>();
        for(int i=start;i<end;i++){
            Track track = getTrackAtPosition(i);
            if(track!=null)
                trackList.add(track);
        }
        return trackList;
    }

    private Track getTrackAtPosition(int position){
        if(!cursor.moveToPosition(position))
            return null;
        return getTrackFromCursor(cursor);
    }

    private Track getTrackFromCursor(Cursor cursor){
        Track track = new Track();
        track.setAudioId(cursor.getLong(0));
        track.setArtist(cursor.getString(1));
        track.setTitle(cursor.getString(2));
        track.setPath(cursor.getString(3));
        track.setDuration(cursor.getLong(4) / 1000);
        track.setAlbumId(cursor.getString(5));
        track.setAlbum(cursor.getString(6));
        Cursor c = contentResolver.query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Albums.ALBUM_ART},
                MediaStore.Audio.Albums._ID + "=?",
                new String[]{track.getAlbumId()},
                null);
        if (c != null && c.moveToFirst()) {
            String path = c.getString(0);
            track.setAlbumArtUri(path);
        }
        if (c != null) {
            c.close();
        }
        return track;
    }

    private String[] getProjection() {
        return new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.ALBUM
        };
    }

    private void initSelection() {
        selection = "(" + MediaStore.Audio.Media.IS_MUSIC + " !=0 )"
                    + "AND (" + MediaStore.Audio.Media.IS_ALARM + " ==0 )"
                    + "AND (" + MediaStore.Audio.Media.IS_NOTIFICATION + " ==0 )"
                    + "AND (" + MediaStore.Audio.Media.IS_PODCAST + " ==0 )"
                    + "AND (" + MediaStore.Audio.Media.IS_RINGTONE + " ==0 )";
    }

    private String getSortOrder() {
        return MediaStore.Audio.Media.TITLE + " ASC";
    }

    public List<Track> getAllTracks() {
        List<Track> trackList = new ArrayList<>();
        for(int i=0;i<cursor.getCount();i++)
            trackList.add(getTrackAtPosition(i));
        return trackList;
    }
}