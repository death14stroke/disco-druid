package com.andruid.magic.discodruid;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Toast;

import com.andruid.magic.discodruid.adapter.CustomPagerAdapter;
import com.andruid.magic.discodruid.adapter.TrackDetailAdapter;
import com.andruid.magic.discodruid.data.Constants;
import com.andruid.magic.discodruid.databinding.ActivityMainBinding;
import com.andruid.magic.discodruid.model.Track;
import com.andruid.magic.discodruid.util.MediaUtils;
import com.github.florent37.materialviewpager.MaterialViewPager;
import com.github.florent37.materialviewpager.header.HeaderDesign;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SnapHelper;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private BottomSheetBehavior<LinearLayout> bottomSheetBehavior;
    private TrackDetailAdapter trackDetailAdapter;
    private String mode = "";
    private boolean userScroll = false;
    private int arrowImage = android.R.drawable.arrow_up_float, seekbarProgress = 0;
    private Track currentTrack;
    private MediaBrowserCompat mediaBrowserCompat;
    private MediaControllerCompat mediaControllerCompat;
    private AlertDialog alertDialog, inputDialog;
    private Handler mSeekBarUpdateHandler = new Handler();
    private Runnable mUpdateSeekBar = new Runnable() {
        @Override
        public void run() {
            binding.bottomSheet.trackDetailSeekBar.setProgress(
                    binding.bottomSheet.trackDetailSeekBar.getProgress()+1);
            mSeekBarUpdateHandler.postDelayed(this, 1000);
        }
    };

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setBinding();
        alertDialog = MediaUtils.getPermissionsDialogBuilder(this).create();
        inputDialog = MediaUtils.getPlaylistDialogBuilder(this).create();
        setViewPager();
        setRecyclerView();
        setBottomSheet();
        setSeekBar();
        mediaBrowserCompat = new MediaBrowserCompat(MainActivity.this, new ComponentName(MainActivity.this, BackgroundAudioService.class),
                new MediaBrowserCompat.ConnectionCallback(){
                    @Override
                    public void onConnected() {
                        super.onConnected();
                        try {
                            mediaControllerCompat = new MediaControllerCompat(MainActivity.this, mediaBrowserCompat.getSessionToken());
                            mediaControllerCompat.registerCallback(callback);
                            MediaControllerCompat.setMediaController(MainActivity.this,mediaControllerCompat);
                            if(Intent.ACTION_VIEW.equalsIgnoreCase(getIntent().getAction())){
                                mediaControllerCompat.getTransportControls().playFromUri(getIntent().getData(),null);
                                return;
                            }
                            mediaBrowserCompat.subscribe(Constants.PLAY_QUEUE,subscriptionCallback);
                            setShuffleButton(mediaControllerCompat.getShuffleMode());
                            setRepeatButton(mediaControllerCompat.getRepeatMode());
                            PlaybackStateCompat ps = mediaControllerCompat.getPlaybackState();
                            if(ps!=null){
                                int state = ps.getState();
                                Bundle extras = ps.getExtras();
                                if(state == PlaybackStateCompat.STATE_PLAYING && extras!=null){
                                    int pos = Integer.parseInt(String.valueOf(extras.getLong(Constants.SEEK_POSITION)));
                                    binding.bottomSheet.trackDetailSeekBar.setProgress(pos/1000);
                                    mSeekBarUpdateHandler.postDelayed(mUpdateSeekBar,1000);
                                }
                                setButtonStates(state);
                            }
                        } catch( RemoteException e ) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onConnectionFailed() {
                        super.onConnectionFailed();
                        Toast.makeText(getApplicationContext(),"Connection failed",Toast.LENGTH_SHORT).show();
                    }
                }, null);
        Dexter.withActivity(this)
                .withPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                .withListener(new PermissionListener() {
                    @Override
                    public void onPermissionGranted(PermissionGrantedResponse response) {
                        if(!mediaBrowserCompat.isConnected())
                            mediaBrowserCompat.connect();
                    }
                    @Override
                    public void onPermissionDenied(PermissionDeniedResponse response) {
                        if(!alertDialog.isShowing())
                            alertDialog.show();
                    }
                    @Override
                    public void onPermissionRationaleShouldBeShown(PermissionRequest permission, PermissionToken token) {
                        token.continuePermissionRequest();
                    }
                }).check();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main,menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.menu_create:
                if(!inputDialog.isShowing())
                    inputDialog.show();
                break;
            case R.id.menu_queue:
//                QueueDialogFragment dialog = new QueueDialogFragment();
//                FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
//                dialog.show(fragmentTransaction,Constants.QUEUE_DIALOG);
                break;
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if(bottomSheetBehavior.getState()==BottomSheetBehavior.STATE_EXPANDED)
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        else
            super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mediaControllerCompat!=null)
            mediaControllerCompat.unregisterCallback(callback);
        if(mediaBrowserCompat!=null)
            mediaBrowserCompat.disconnect();
    }

    private MediaBrowserCompat.SubscriptionCallback subscriptionCallback = new MediaBrowserCompat.SubscriptionCallback() {
        @Override
        public void onChildrenLoaded(@NonNull String parentId, @NonNull List<MediaBrowserCompat.MediaItem> children) {
            super.onChildrenLoaded(parentId, children);
            switch (parentId){
                case Constants.PLAY_QUEUE:
                    List<Track> trackList = MediaUtils.getTracksFromMediaItems(children);
                    trackDetailAdapter.setTrackList(trackList);
                    trackDetailAdapter.notifyDataSetChanged();
                    if(trackList.isEmpty()){
                        binding.bottomSheet.trackDetailSeekBar.setProgress(0);
                        mSeekBarUpdateHandler.removeCallbacks(mUpdateSeekBar);
                    }
                    MediaMetadataCompat metadataCompat = mediaControllerCompat.getMetadata();
                    if(metadataCompat!=null) {
                        Track track = MediaUtils.getTrackFromMetaData(metadataCompat);
                        setActivityUI(track);
                    }
                    break;
            }
        }
    };

    private MediaControllerCompat.Callback callback = new MediaControllerCompat.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            super.onMetadataChanged(metadata);
            Track track = MediaUtils.getTrackFromMetaData(metadata);
            setActivityUI(track);
            binding.bottomSheet.trackDetailSeekBar.setProgress(0);
        }

        @Override
        public void onShuffleModeChanged(int shuffleMode) {
            super.onShuffleModeChanged(shuffleMode);
            setShuffleButton(shuffleMode);
        }

        @Override
        public void onRepeatModeChanged(int repeatMode) {
            super.onRepeatModeChanged(repeatMode);
            setRepeatButton(repeatMode);
        }

        @SuppressLint("SwitchIntDef")
        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            super.onPlaybackStateChanged(state);
            if(state==null){
                return;
            }
            switch (state.getState()) {
                case PlaybackStateCompat.STATE_STOPPED:
                case PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM:
                    binding.bottomSheet.trackDetailSeekBar.setProgress(0);
                    mSeekBarUpdateHandler.removeCallbacks(mUpdateSeekBar);
                    break;
                case PlaybackStateCompat.STATE_BUFFERING:
                    binding.bottomSheet.trackDetailSeekBar.setProgress(0);
                    break;
                case PlaybackStateCompat.STATE_PLAYING:
                    setButtonStates(PlaybackStateCompat.STATE_PLAYING);
                    int pos = Integer.parseInt(String.valueOf(state.getPosition()));
                    binding.bottomSheet.trackDetailSeekBar.setProgress(pos/1000);
                    mSeekBarUpdateHandler.postDelayed(mUpdateSeekBar,0);
                    break;
                case PlaybackStateCompat.STATE_PAUSED:
                    setButtonStates(PlaybackStateCompat.STATE_PAUSED);
                    pos = Integer.parseInt(String.valueOf(state.getPosition()));
                    binding.bottomSheet.trackDetailSeekBar.setProgress(pos/1000);
                    mSeekBarUpdateHandler.removeCallbacks(mUpdateSeekBar);
                    break;
            }
        }
    };

    private void changePlayList(List<Track> trackList) {
        Bundle params = new Bundle();
        params.putParcelableArrayList(Constants.PLAY_QUEUE, new ArrayList<>(trackList));
        mediaControllerCompat.sendCommand(Constants.SET_QUEUE, params, null);
    }

    private void changeMode(String modeNew, List<Track> trackList, int pos){
        binding.bottomSheet.trackDetailSeekBar.setProgress(0);
        mSeekBarUpdateHandler.removeCallbacks(mUpdateSeekBar);
        if(!mode.equalsIgnoreCase(modeNew)) {
            changePlayList(trackList);
            mode = modeNew;
        }
        mediaControllerCompat.getTransportControls().skipToQueueItem(pos);
    }

    private void setButtonStates(int state) {
        if(state==PlaybackStateCompat.STATE_PLAYING){
            binding.bottomSheet.trackDetailPlayBtn.setImageResource(android.R.drawable.ic_media_pause);
            binding.bottomSheet.bottomCollapsedView.trackPlayBtn.setImageResource(android.R.drawable.ic_media_pause);
        }
        else{
            binding.bottomSheet.trackDetailPlayBtn.setImageResource(android.R.drawable.ic_media_play);
            binding.bottomSheet.bottomCollapsedView.trackPlayBtn.setImageResource(android.R.drawable.ic_media_play);
        }
    }

    private void setActivityUI(Track track) {
        currentTrack = track;
        List<Track> trackList = trackDetailAdapter.getTrackList();
        int pos;
        for(pos=0;pos<trackList.size();pos++){
            if(track.getAudioId()==trackList.get(pos).getAudioId())
                break;
        }
        binding.bottomSheet.trackDetailRecyclerView.scrollToPosition(pos);
    }

    private void setRepeatButton(int rMode) {
        if(rMode==PlaybackStateCompat.REPEAT_MODE_ALL)
            binding.bottomSheet.trackDetailRepeatBtn.setImageResource(R.drawable.ic_repeat_on);
        else if(rMode==PlaybackStateCompat.REPEAT_MODE_NONE)
            binding.bottomSheet.trackDetailRepeatBtn.setImageResource(R.drawable.ic_repeat_off);
        else if(rMode==PlaybackStateCompat.REPEAT_MODE_ONE)
            binding.bottomSheet.trackDetailRepeatBtn.setImageResource(R.drawable.ic_repeat_one);
    }

    private void setShuffleButton(int sMode) {
        if(sMode==PlaybackStateCompat.SHUFFLE_MODE_ALL)
            binding.bottomSheet.trackDetailShuffleBtn.setImageResource(R.drawable.ic_shuffle_on);
        else if(sMode==PlaybackStateCompat.REPEAT_MODE_NONE)
            binding.bottomSheet.trackDetailShuffleBtn.setImageResource(R.drawable.ic_shuffle_off);
    }


    private void setBinding() {
        binding = DataBindingUtil.setContentView(this,R.layout.activity_main);
        binding.setTrack(currentTrack);
        binding.setVariable(BR.arrowImage,arrowImage);
        binding.setVariable(BR.progress,seekbarProgress);
    }

    private void setSeekBar() {
        binding.bottomSheet.trackDetailSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                seekbarProgress = seekBar.getProgress();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                seekbarProgress = seekBar.getProgress();
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mediaControllerCompat.getTransportControls().seekTo(seekBar.getProgress()*1000);
            }
        });
    }

    private void setBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet.bottomSheetLl);
        bottomSheetBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @SuppressLint("SwitchIntDef")
            @Override
            public void onStateChanged(@NonNull View view, int state) {
                switch (state) {
                    case BottomSheetBehavior.STATE_COLLAPSED:
                        arrowImage = android.R.drawable.arrow_up_float;
                        binding.bottomSheet.bottomCollapsedView.getRoot().setVisibility(View.VISIBLE);
                        break;
                    case BottomSheetBehavior.STATE_EXPANDED:
                        arrowImage = android.R.drawable.arrow_down_float;
                        binding.bottomSheet.bottomCollapsedView.getRoot().setVisibility(View.GONE);
                        break;
                }
            }
            @Override
            public void onSlide(@NonNull View view, float v) {}
        });
    }

    private void setRecyclerView() {
        trackDetailAdapter = new TrackDetailAdapter(this);
        RecyclerView recyclerView = binding.bottomSheet.trackDetailRecyclerView;
        recyclerView.setAdapter(trackDetailAdapter);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(trackDetailAdapter);
        SnapHelper snapHelper = new LinearSnapHelper();
        snapHelper.attachToRecyclerView(recyclerView);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if(newState == RecyclerView.SCROLL_STATE_IDLE){
                    View centerView = snapHelper.findSnapView(layoutManager);
                    int position = 0;
                    if (centerView != null)
                        position = layoutManager.getPosition(centerView);
                    if(userScroll) {
                        binding.bottomSheet.trackDetailSeekBar.setProgress(0);
                        mSeekBarUpdateHandler.removeCallbacks(mUpdateSeekBar);
                        mediaControllerCompat.getTransportControls().skipToQueueItem(position);
                        userScroll = false;
                    }
                }
            }
        });
    }

    private void setViewPager() {
        CustomPagerAdapter pagerAdapter = new CustomPagerAdapter(getSupportFragmentManager());
        MaterialViewPager materialViewPager = binding.materialViewPager;
        materialViewPager.getViewPager().setAdapter(pagerAdapter);
        materialViewPager.getPagerTitleStrip().setViewPager(materialViewPager.getViewPager());
        materialViewPager.setMaterialViewPagerListener(page -> {
            switch (page) {
                case 0:
                    return HeaderDesign.fromColorResAndDrawable(
                            R.color.blue,
                            ContextCompat.getDrawable(this,R.drawable.music));
                case 1:
                    return HeaderDesign.fromColorResAndDrawable(
                            R.color.green,
                            ContextCompat.getDrawable(this,R.drawable.music));
                case 2:
                    return HeaderDesign.fromColorResAndDrawable(
                            R.color.cyan,
                            ContextCompat.getDrawable(this,R.drawable.music));
                default:
                    return HeaderDesign.fromColorResAndDrawable(
                            R.color.red,
                            ContextCompat.getDrawable(this,R.drawable.music));
            }
        });
        Toolbar toolbar = materialViewPager.getToolbar();
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayShowTitleEnabled(true);
            }
        }
    }

    public void shuffle(View view){
        int mode = mediaControllerCompat.getShuffleMode();
        if(mode== PlaybackStateCompat.SHUFFLE_MODE_ALL)
            mediaControllerCompat.getTransportControls().setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_NONE);
        else if(mode==PlaybackStateCompat.REPEAT_MODE_NONE)
            mediaControllerCompat.getTransportControls().setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_ALL);
    }

    public void repeat(View view){
        int mode = mediaControllerCompat.getRepeatMode();
        if(mode==PlaybackStateCompat.REPEAT_MODE_NONE)
            mediaControllerCompat.getTransportControls().setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ALL);
        else if(mode==PlaybackStateCompat.REPEAT_MODE_ALL)
            mediaControllerCompat.getTransportControls().setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ONE);
        else if(mode== PlaybackStateCompat.REPEAT_MODE_ONE)
            mediaControllerCompat.getTransportControls().setRepeatMode(PlaybackStateCompat.REPEAT_MODE_NONE);
    }

    public void play(View view){
        mediaControllerCompat.dispatchMediaButtonEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
    }

    public void prev(View view){
        mediaControllerCompat.getTransportControls().skipToPrevious();
    }

    public void next(View view){
        mediaControllerCompat.getTransportControls().skipToNext();
    }

    public void toggleBottomSheet(View view){
        if(bottomSheetBehavior.getState()==BottomSheetBehavior.STATE_COLLAPSED)
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        else if(bottomSheetBehavior.getState()==BottomSheetBehavior.STATE_EXPANDED)
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }

    public static class CreatePlayListAsyncTask extends AsyncTask<Void,Void,Void> {
        private WeakReference<Context> contextRef;
        private String name;

        public CreatePlayListAsyncTask(Context context, String name) {
            this.contextRef = new WeakReference<>(context);
            this.name = name;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            MediaUtils.createNewPlayList(contextRef.get(),name);
            return null;
        }
    }
}