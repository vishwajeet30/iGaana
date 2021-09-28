package com.example.musicapp

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.PowerManager
import java.lang.IllegalArgumentException
import java.lang.IndexOutOfBoundsException
import java.sql.Time
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class MediaPlayerHolder(private val mMusicService: MusicService?) : PlayerAdapter,
    MediaPlayer.OnCompletionListener, MediaPlayer.OnPreparedListener {

    private val mContext: Context

    private val mAudioManager: AudioManager

    private var mMediaPlayer: MediaPlayer? = null

    private var mPlaybackInfoListener: PlaybackInfoListener? = null

    private var mSelectedSong: Song? = null

    private var mSongs: List<Song>? = null

    private var sReplaySong = false

    @PlaybackInfoListener.State
    private var mState: Int = 0

    private var mCurrentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK

    private var mPlayOnFocusGain: Boolean = false

    private var mExecutor: ScheduledExecutorService?= null
    private var mSeekBarPositionUpdateTask:Runnable?= null


    private var mMusicNotificationManager:MusicNotificationManager? =null
    private var mNotificationActionRecevier: NotificationRecevier? = null


    init {
        mContext = mMusicService!!.applicationContext
        mAudioManager = mContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }






    private val mOnAudioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->

        when(focusChange){
            AudioManager.AUDIOFOCUS_GAIN-> mCurrentAudioFocusState = AUDIO_FOCUSED
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK-> mCurrentAudioFocusState = AUDIO_NO_FOCUS_CAN_DUCK
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT->{

                mCurrentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
                mPlayOnFocusGain = isMediaPlayer() && mState == PlaybackInfoListener.State.PLAYING || mState==PlaybackInfoListener.State.RESUMED
            }

            AudioManager.AUDIOFOCUS_LOSS->
                mCurrentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
        } // end of when

        if (mMediaPlayer != null){

            configurePlayerState()
        }


    }








    private fun tryToGetAudioFocus(){
        val result = mAudioManager.requestAudioFocus(
            mOnAudioFocusChangeListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
            mCurrentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
        }else{
            mCurrentAudioFocusState = AUDIO_NO_FOCUS_CAN_DUCK
        }
    }

    private fun giveUpAudioFocus(){
        if (mAudioManager.abandonAudioFocus(mOnAudioFocusChangeListener)
            == AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
            mCurrentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
        }
    }

    private fun startUpdateCallbackWithPosition(){

        if (mExecutor == null){
            mExecutor = Executors.newSingleThreadScheduledExecutor()
        }
        if (mSeekBarPositionUpdateTask == null){
            mSeekBarPositionUpdateTask = Runnable { updateProgressCallbackTask() }
        }
        mExecutor!!.scheduleAtFixedRate(
            mSeekBarPositionUpdateTask,
            0,
            1000,
            TimeUnit.MILLISECONDS
        )

    }

    private fun updateProgressCallbackTask() {

        if (isMediaPlayer() && mMediaPlayer!!.isPlaying){
            val currentPosition = mMediaPlayer!!.currentPosition
            if (mPlaybackInfoListener!=null){
                mPlaybackInfoListener!!.onPositionChanged(currentPosition)
            }
        }
    }


    private fun stopUpdateCallbackWithPosition(){

        if (mExecutor !=null){
            mExecutor!!.shutdownNow()
            mExecutor = null
            mSeekBarPositionUpdateTask = null
        }
    }


    override fun onPauseActivity() {

        stopUpdateCallbackWithPosition()

    }

    override fun onResumeActivity() {
        startUpdateCallbackWithPosition()
    }


    override fun release() {

        if (isMediaPlayer()){
            mMediaPlayer!!.release()
            mMediaPlayer = null
            giveUpAudioFocus()

            unregisterActionRecvier()
        }

    }



    private fun configurePlayerState() {

        if (mCurrentAudioFocusState == AUDIO_NO_FOCUS_NO_DUCK){
            pauseMediaPlayer()
        }else{
            if (mCurrentAudioFocusState == AUDIO_NO_FOCUS_CAN_DUCK){
                mMediaPlayer!!.setVolume(VOLUME_DUCK, VOLUME_DUCK);
            }else{
                mMediaPlayer!!.setVolume(VOLUME_NORMAL, VOLUME_NORMAL)
            }

            if (mPlayOnFocusGain){
                resumeMediaPlayer()
                mPlayOnFocusGain = false
            }

        }


    }

    override fun onPrepared(mediaPlayer: MediaPlayer) {

        startUpdateCallbackWithPosition()

        setStatus(PlaybackInfoListener.State.PLAYING)
    }


    override fun onCompletion(mediaPlayer: MediaPlayer) {
        if (mPlaybackInfoListener != null) {
            mPlaybackInfoListener!!.onStateChanged(PlaybackInfoListener.State.COMPLETED)
            mPlaybackInfoListener!!.onPlaybackCompleted()
        }

        if (sReplaySong) {
            if (isMediaPlayer()) {

                //resetSong() we will call
            }
            sReplaySong = false
        } else {

            skip(true)

        }
    }

    override fun initMediaPlayer() {

        try {
            if (mMediaPlayer != null) {
                mMediaPlayer!!.reset()
            } else {
                mMediaPlayer = MediaPlayer()

                mMediaPlayer!!.setOnPreparedListener(this)
                mMediaPlayer!!.setOnCompletionListener(this)
                mMediaPlayer!!.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK)
                mMediaPlayer!!.setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())


                mMusicNotificationManager = mMusicService!!.musicNotificationManager

            }
            tryToGetAudioFocus()
            mMediaPlayer!!.setDataSource(mSelectedSong!!.path)
            mMediaPlayer!!.prepare()

        } catch (e: Exception) {
            e.printStackTrace()
            skip(true)
        }
    }



    override fun isMediaPlayer(): Boolean {
        return mMediaPlayer != null
    }

    override fun isPlaying(): Boolean {
        return isMediaPlayer() && mMediaPlayer!!.isPlaying
    }

    override fun isReset(): Boolean {
        return sReplaySong
    }

    @PlaybackInfoListener.State
    override fun getState(): Int {
        return mState
    }
    override fun getPlayerPosition(): Int {
        return mMediaPlayer!!.currentPosition
    }

    override fun getMediaPlayer(): MediaPlayer? {
        return mMediaPlayer
    }

    override fun reset() {
        sReplaySong = !sReplaySong
    }

    override fun resumeOrPause() {

        if (isPlaying()) {
            pauseMediaPlayer()
        } else {
            resumeMediaPlayer()
        }
    }


    override fun setPlaybackInfoListener(listener: PlaybackInfoListener) {
        mPlaybackInfoListener = listener
    }

    override fun getCurrentSong(): Song? {
        return mSelectedSong
    }


    override fun setCurrentSong(song: Song, songs: List<Song>) {
        mSelectedSong = song
        mSongs = songs
    }

    private fun setStatus(@PlaybackInfoListener.State state: Int) {
        mState = state
        if (mPlaybackInfoListener != null) {
            mPlaybackInfoListener!!.onStateChanged(state)
        }
    }


    private fun resumeMediaPlayer() {
        if (!isPlaying()) {
            mMediaPlayer!!.start()
            setStatus(PlaybackInfoListener.State.RESUMED)

            mMusicService!!.startForeground(MusicNotificationManager.NOTIFICATION_ID
                ,mMusicNotificationManager!!.createNotification())

        }
    }

    private fun pauseMediaPlayer() {
        setStatus(PlaybackInfoListener.State.PAUSED)
        mMediaPlayer!!.pause()
        mMusicService!!.stopForeground(false)

        mMusicNotificationManager!!.notificationManager.notify(MusicNotificationManager.NOTIFICATION_ID,
            mMusicNotificationManager!!
                .createNotification())

    }


    override fun instantReset() {

        if(isMediaPlayer()){
            if (mMediaPlayer!!.currentPosition<5000){
                skip(false)
            }else{
                resetSong()
            }
        }

    }

    private fun resetSong() {
        mMediaPlayer!!.seekTo(0)
        mMediaPlayer!!.start()
        setStatus(PlaybackInfoListener.State.PLAYING)
    }

    override fun skip(isNext: Boolean) {

        getSkipSong(isNext)

    }

    private fun getSkipSong(isNext: Boolean) {

        val currentIndex = mSongs!!.indexOf(mSelectedSong)

        val index:Int

        try{

            index = if (isNext) currentIndex + 1 else currentIndex - 1
            mSelectedSong = mSongs!![index]


        }catch (e: IndexOutOfBoundsException){

            mSelectedSong = if (currentIndex!=0) mSongs!![0] else mSongs!![mSongs!!.size - 1]
            e.printStackTrace()
        }

        initMediaPlayer()

    }

    override fun seekTo(position: Int) {

        if (isMediaPlayer())
        {
            mMediaPlayer!!.seekTo(position)
        }
    }


    private fun registerActionRecevier(){

        mNotificationActionRecevier = NotificationRecevier()
        val intentFilter = IntentFilter()

        intentFilter.addAction(MusicNotificationManager.PREV_ACTION)
        intentFilter.addAction(MusicNotificationManager.PLAY_PAUSE_ACTION)
        intentFilter.addAction(MusicNotificationManager.NEXT_ACTION)
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        intentFilter.addAction(Intent.ACTION_HEADSET_PLUG)
        intentFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)

        mMusicService!!.registerReceiver(mNotificationActionRecevier,intentFilter)


    }

    private fun unregisterActionRecvier(){

        if (mMusicService != null && mNotificationActionRecevier != null){
            try {

                mMusicService.unregisterReceiver(mNotificationActionRecevier)

            }catch (e:IllegalArgumentException){
                e.printStackTrace()
            }
        }

    }




    private inner class NotificationRecevier : BroadcastReceiver(){


        override fun onReceive(context: Context?, intent: Intent) {

            val action = intent.action

            if (action != null){

                when(action){

                    MusicNotificationManager.PREV_ACTION-> instantReset()
                    MusicNotificationManager.PLAY_PAUSE_ACTION-> resumeOrPause()
                    MusicNotificationManager.NEXT_ACTION-> skip(true)


                    BluetoothDevice.ACTION_ACL_DISCONNECTED->if (mSelectedSong !=null){
                        pauseMediaPlayer()
                    }
                    BluetoothDevice.ACTION_ACL_CONNECTED->if (mSelectedSong != null && isPlaying()){
                        resumeMediaPlayer()
                    }

                    Intent.ACTION_HEADSET_PLUG->if (mSelectedSong!=null){
                        when(intent.getIntExtra("state",-1)){

                            0-> pauseMediaPlayer()
                            1-> if (isPlaying()){
                                resumeMediaPlayer()
                            }

                        }
                    }


                    AudioManager.ACTION_AUDIO_BECOMING_NOISY->if (isPlaying()){
                        pauseMediaPlayer()
                    }

                }

            }
        }
    }





    override fun registerNotificationActionsReceiver(isRecevier: Boolean) {
        if (isRecevier){
            registerActionRecevier()
        }else{
            unregisterActionRecvier()
        }

    }



    companion object {

        private val VOLUME_DUCK = 0.2f

        private val VOLUME_NORMAL = 1.0f

        private val AUDIO_NO_FOCUS_NO_DUCK = 0

        private val AUDIO_NO_FOCUS_CAN_DUCK = 1

        private val AUDIO_FOCUSED = 2
    }

}
