package com.andruid.magic.discodruid.model;

import android.os.Parcel;
import android.os.Parcelable;

public class Album implements Parcelable {
    private String albumId, album, artist, albumArtUri;
    private int songsCount;

    public Album() {
    }

    protected Album(Parcel in) {
        albumId = in.readString();
        album = in.readString();
        artist = in.readString();
        albumArtUri = in.readString();
        songsCount = in.readInt();
    }

    public static final Creator<Album> CREATOR = new Creator<Album>() {
        @Override
        public Album createFromParcel(Parcel in) {
            return new Album(in);
        }

        @Override
        public Album[] newArray(int size) {
            return new Album[size];
        }
    };

    public String getAlbumId() {
        return albumId;
    }

    public void setAlbumId(String albumId) {
        this.albumId = albumId;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getAlbumArtUri() {
        return albumArtUri;
    }

    public void setAlbumArtUri(String albumArtUri) {
        this.albumArtUri = albumArtUri;
    }

    public int getSongsCount() {
        return songsCount;
    }

    public void setSongsCount(int songsCount) {
        this.songsCount = songsCount;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(albumId);
        parcel.writeString(album);
        parcel.writeString(artist);
        parcel.writeString(albumArtUri);
        parcel.writeInt(songsCount);
    }
}