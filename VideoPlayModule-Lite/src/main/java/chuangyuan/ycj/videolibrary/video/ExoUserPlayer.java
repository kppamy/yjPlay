package chuangyuan.ycj.videolibrary.video;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.CallSuper;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.Size;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.Util;

import java.lang.reflect.Constructor;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import chuangyuan.ycj.videolibrary.listener.BasePlayerListener;
import chuangyuan.ycj.videolibrary.listener.DataSourceListener;
import chuangyuan.ycj.videolibrary.listener.DefaultMediaSourceEvent;
import chuangyuan.ycj.videolibrary.listener.ExoPlayerListener;
import chuangyuan.ycj.videolibrary.listener.ExoPlayerViewListener;
import chuangyuan.ycj.videolibrary.listener.ItemVideo;
import chuangyuan.ycj.videolibrary.listener.VideoInfoListener;
import chuangyuan.ycj.videolibrary.listener.VideoWindowListener;
import chuangyuan.ycj.videolibrary.utils.VideoPlayUtils;
import chuangyuan.ycj.videolibrary.widget.VideoPlayerView;

/**
 * The type Exo user player.
 * author yangc   date 2017/2/28
 * E-Mail:1007181167@qq.com
 * Description：积累
 */
public class ExoUserPlayer {
    /*** 记录视频进度缓存map  **/
    private static WeakHashMap<Integer, Long> tags = new WeakHashMap<>();
    /*** 记录视频当前窗口缓存map **/
    private static WeakHashMap<Integer, Integer> tags2 = new WeakHashMap<>();
    private static final String TAG = ExoUserPlayer.class.getName();
    /***当前活动*/
    protected Activity activity;
    /*** 播放view实例***/
    private VideoPlayerView videoPlayerView;
    /*** 获取网速大小,获取最后的时间戳,获取当前进度 ***/
    private Long lastTotalRxBytes = 0L, lastTimeStamp = 0L, resumePosition = 0L;
    /*** 是否循环播放  0 不开启,获取当前视频窗口位置***/
    private int resumeWindow = 0;
    /*** 是否手动暂停,是否已经在停止恢复,,已经加载,*/
    boolean handPause, isPause, isLoad;
    /**
     * 播放结束,是否选择多分辨率
     **/
    private boolean isEnd, isSwitch;
    /*** 定时任务类 ***/
    private ScheduledExecutorService timer;
    /*** 网络状态监听***/
    private NetworkBroadcastReceiver mNetworkBroadcastReceiver;
    /*** view交互回调接口 ***/
    private PlayComponent playComponentListener;
    /*** 视频回调信息接口 ***/
    private final CopyOnWriteArraySet<VideoInfoListener> videoInfoListeners;
    /*** 多个窗口***/
    private final CopyOnWriteArraySet<VideoWindowListener> videoWindowListeners;
    /*** base接口***/
    private final CopyOnWriteArraySet<BasePlayerListener> basePlayerListeners;
    /*** 视频加载准备接口***/
    private final CopyOnWriteArraySet<OnPreparedListener> mOnPreparedListeners;
    /*** 播放view交互接口 ***/
    private ExoPlayerViewListener mPlayerViewListener;
    /*** 内核播放控制*/
    SimpleExoPlayer player;
    /***数据源管理类*/
    private MediaSourceBuilder mediaSourceBuilder;
    /*** 设置播放参数***/
    private PlaybackParameters playbackParameters;
    /*** 如果DRM得到保护，可能是null ***/
    private DrmSessionManager<FrameworkMediaCrypto> drmSessionManager;
    private int position;
    private final DefaultMediaSourceEvent mediaSourceEvent;

    /****
     * @param activity 活动对象
     * @param reId 播放控件id
     * @deprecated Use {@link VideoPlayerManager.Builder} instead.
     */
    public ExoUserPlayer(@NonNull Activity activity, @IdRes int reId) {
        this(activity, reId, null);
    }

    /****
     * 初始化
     * @param activity 活动对象
     * @param playerView 播放控件
     * @deprecated Use {@link VideoPlayerManager.Builder} instead.
     */
    public ExoUserPlayer(@NonNull Activity activity, @NonNull VideoPlayerView playerView) {
        this(activity, playerView, null);
    }

    /****
     * 初始化
     * @param activity 活动对象
     * @param reId 播放控件id
     * @param listener 自定义数据源类
     * @deprecated Use {@link VideoPlayerManager.Builder} instead.
     */
    public ExoUserPlayer(@NonNull Activity activity, @IdRes int reId, @Nullable DataSourceListener listener) {
        this(activity, (VideoPlayerView) activity.findViewById(reId), listener);
    }

    /***
     * 初始化
     * @param activity 活动对象
     * @param playerView 播放控件
     * @param listener 自定义数据源类
     * @deprecated Use {@link VideoPlayerManager.Builder} instead.
     */
    public ExoUserPlayer(@NonNull Activity activity, @NonNull VideoPlayerView playerView, @Nullable DataSourceListener listener) {
        this.activity = activity;
        this.videoPlayerView = playerView;
        videoInfoListeners = new CopyOnWriteArraySet<>();
        videoWindowListeners = new CopyOnWriteArraySet<>();
        basePlayerListeners = new CopyOnWriteArraySet<>();
        mOnPreparedListeners = new CopyOnWriteArraySet<>();
        mediaSourceEvent = new DefaultMediaSourceEvent(mOnPreparedListeners);
        playComponentListener = new PlayComponent(this);
        try {
            Class<?> clazz = Class.forName("chuangyuan.ycj.videolibrary.whole.WholeMediaSource");
            Constructor<?> constructor = clazz.getConstructor(Context.class, DataSourceListener.class);
            this.mediaSourceBuilder = (MediaSourceBuilder) constructor.newInstance(activity, listener);
        } catch (Exception e) {
            this.mediaSourceBuilder = new MediaSourceBuilder(activity, listener);
        } finally {
            initView();
        }
    }

    /****
     * 初始化
     * @param activity 活动对象
     * @param mediaSourceBuilder 自定义数据源类
     * @param playerView 播放控件
     * @deprecated Use {@link VideoPlayerManager.Builder} instead.
     */
    public ExoUserPlayer(@NonNull Activity activity, @NonNull MediaSourceBuilder mediaSourceBuilder, @NonNull VideoPlayerView playerView) {
        this.activity = activity;
        this.videoPlayerView = playerView;
        this.mediaSourceBuilder = mediaSourceBuilder;
        videoInfoListeners = new CopyOnWriteArraySet<>();
        videoWindowListeners = new CopyOnWriteArraySet<>();
        basePlayerListeners = new CopyOnWriteArraySet<>();
        mOnPreparedListeners = new CopyOnWriteArraySet<>();
        mediaSourceEvent = new DefaultMediaSourceEvent(mOnPreparedListeners);
        playComponentListener = new PlayComponent(this);
        initView();
    }


    private void initView() {
        videoPlayerView.setExoPlayerListener(playComponentListener);
        getPlayerViewListener().setPlayerBtnOnTouch(true);
        player = createFullPlayer();
    }

    /*****
     * 设置视频控件view  主要用来列表进入详情播放使用
     * @param videoPlayerView videoPlayerView
     * **/
    void setVideoPlayerView(@NonNull VideoPlayerView videoPlayerView) {
        if (this.videoPlayerView == videoPlayerView) {
            return;
        }
        mPlayerViewListener = null;
        if (player != null) {
            player.removeListener(componentListener);
        }
        this.videoPlayerView = videoPlayerView;
        videoPlayerView.setExoPlayerListener(playComponentListener);
        if (player == null) {
            player = createFullPlayer();
        } else {
            getPlayerViewListener().setPlayer(player);
        }
        player.addListener(componentListener);
        getPlayerViewListener().toggoleController(false, false);
        getPlayerViewListener().setControllerHideOnTouch(true);
        isEnd = false;
        isLoad = true;
    }

    /***
     * 获取交互view接口实例
     * @return ExoPlayerViewListener player view listener
     */
    @NonNull
    ExoPlayerViewListener getPlayerViewListener() {
        if (mPlayerViewListener == null) {
            mPlayerViewListener = videoPlayerView.getComponentListener();
        }
        return mPlayerViewListener;
    }

    /***
     * 页面恢复处理
     */
    public void onResume() {
        boolean is = (Util.SDK_INT <= Build.VERSION_CODES.M || null == player) && isLoad && !isEnd;
        if (is) {
            if (getPlayerViewListener().isList()) {
                getPlayerViewListener().setPlayerBtnOnTouch(true);
            } else {
                createPlayers();
            }
        }
    }

    /***
     * 页面暂停处理
     */
    @CallSuper
    public void onPause() {
        isPause = true;
        if (player != null) {
            handPause = !player.getPlayWhenReady();
            releasePlayers();
        }
    }

    @CallSuper
    public void onStop() {
        onPause();
    }

    /**
     * 页面销毁处理
     */
    @CallSuper
    public void onDestroy() {
        for (BasePlayerListener basePlayerListener : basePlayerListeners) {
            basePlayerListener.onDestroy();
        }
        releasePlayers();
        tags.clear();
        tags2.clear();
    }

    /**
     * 列表暂停
     *
     * @param reset 是否重置的 true  重置 false
     */
    void onListPause(boolean reset) {
        if (reset) {
            isPause = true;
            if (player != null) {
                handPause = !player.getPlayWhenReady();
                reset();
            }
        }
    }

    /***
     * 释放资源
     */
    public void releasePlayers() {
        updateResumePosition();
        unNetworkBroadcastReceiver();
        if (player != null) {
            player.removeListener(componentListener);
            player.stop();
            player.release();
            player = null;
        }
        if (timer != null && !timer.isShutdown()) {
            timer.shutdown();
        }
        if (activity == null || activity.isDestroyed()) {
            if (mediaSourceBuilder != null) {
                mediaSourceBuilder.destroy();
            }
            videoInfoListeners.clear();
            videoWindowListeners.clear();
            basePlayerListeners.clear();
            mOnPreparedListeners.clear();
            isEnd = false;
            isPause = false;
            handPause = false;
            timer = null;
            activity = null;
            mPlayerViewListener = null;
            mediaSourceBuilder = null;
            componentListener = null;
            playComponentListener = null;
        }
    }

    /****
     * 初始化播放实例
     */
    public <R extends ExoUserPlayer> R startPlayer() {
        if (getPlayerViewListener().isList()) {
            handPause = false;
            VideoPlayerManager.getInstance().setCurrentVideoPlayer(ExoUserPlayer.this);
            if (tags.get(position) != null && tags2.get(position) != null) {
                int positions = tags.get(position).intValue();
                int index = tags2.get(position);
                setPosition(index, positions);
                tags.remove(position);
                tags2.remove(position);
            }
        }
        getPlayerViewListener().setPlayerBtnOnTouch(false);
        createPlayers();
        registerReceiverNet();
        return (R) this;
    }

    /****
     * 创建播放
     */
    protected void createPlayers() {
        startVideo();
    }

    /***
     * 播放视频
     **/
    private void startVideo() {
        boolean iss = VideoPlayUtils.isWifi(activity) || VideoPlayerManager.getInstance().isClick() || isPause||isPlaying();
        if (iss) {
            onPlayNoAlertVideo();
        } else {
            getPlayerViewListener().showAlertDialog();
        }
    }


    /***
     * 创建实例播放实例，并不开始缓冲
     **/
    private SimpleExoPlayer createFullPlayer() {
        TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory();
        TrackSelector trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);
        setDefaultLoadModel();
        DefaultRenderersFactory rf = new DefaultRenderersFactory(activity, DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON);
        SimpleExoPlayer player = ExoPlayerFactory.newSimpleInstance(activity,rf, trackSelector, new DefaultLoadControl(), drmSessionManager);
        getPlayerViewListener().setPlayer(player);
        return player;
    }


    /***
     * 创建实例播放实例，开始缓冲
     */
    void onPlayNoAlertVideo() {
        if (player == null) {
            player = createFullPlayer();
        }
        boolean haveResumePosition = resumeWindow != C.INDEX_UNSET;
        if (handPause) {
            player.setPlayWhenReady(false);
        } else {
            player.setPlayWhenReady(true);
        }
        player.setPlaybackParameters(playbackParameters);
        if (mPlayerViewListener != null) {
            mPlayerViewListener.showPreview(View.GONE, true);
            getPlayerViewListener().toggoleController(false, false);
            mPlayerViewListener.setControllerHideOnTouch(true);
        }
        player.removeListener(componentListener);
        player.addListener(componentListener);
        if (haveResumePosition) {
            player.seekTo(resumeWindow, resumePosition);
        }
        player.prepare(mediaSourceBuilder.getMediaSource(), !haveResumePosition, false);
        isEnd = false;
        isLoad = true;
        for (BasePlayerListener basePlayerListener : basePlayerListeners) {
            basePlayerListener.onPlayNoAlertVideo();
        }
    }

     void land(){
         boolean haveResumePosition = resumeWindow != C.INDEX_UNSET;
         if (handPause) {
             player.setPlayWhenReady(false);
         } else {
             player.setPlayWhenReady(true);
         }
         player.prepare(mediaSourceBuilder.getMediaSource(), !haveResumePosition, false);
     }
    /****
     * 重置进度
     */
    private void updateResumePosition() {
        if (player != null) {
            resumeWindow = player.getCurrentWindowIndex();
            resumePosition = Math.max(0, player.getContentPosition());
        }
    }

    /**
     * 清除进度
     ***/
    protected void clearResumePosition() {
        resumeWindow = C.INDEX_UNSET;
        resumePosition = C.TIME_UNSET;
    }

    /***
     * 网络变化任务
     **/
    private final Runnable task = new Runnable() {
        @Override
        public void run() {
            if (getPlayerViewListener().isLoadingShow()) {
                getPlayerViewListener().showNetSpeed(getNetSpeed());
            }
        }
    };

    /****
     * 获取当前网速
     *
     * @return String 返回当前网速字符
     **/
    private String getNetSpeed() {
        String netSpeed;
        long nowTotalRxBytes = VideoPlayUtils.getTotalRxBytes(activity);
        long nowTimeStamp = System.currentTimeMillis();
        long calculationTime = (nowTimeStamp - lastTimeStamp);
        if (calculationTime == 0) {
            netSpeed = String.valueOf(1) + " kb/s";
            return netSpeed;
        }
        //毫秒转换
        long speed = ((nowTotalRxBytes - lastTotalRxBytes) * 1000 / calculationTime);
        lastTimeStamp = nowTimeStamp;
        lastTotalRxBytes = nowTotalRxBytes;
        if (speed > 1024) {
            DecimalFormat df = new DecimalFormat("######0.0");
            netSpeed = String.valueOf(df.format(VideoPlayUtils.getM(speed))) + " MB/s";
        } else {
            netSpeed = String.valueOf(speed) + " kb/s";
        }
        return netSpeed;
    }


    /****
     * 监听返回键 true 可以正常返回处理，false 切换到竖屏
     *
     * @return boolean boolean
     */
    public boolean onBackPressed() {
        if (activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            getPlayerViewListener().exitFull();
            return false;
        } else {
            return true;
        }
    }

    /***
     * 设置默认加载
     * **/
    private void setDefaultLoadModel() {
        if (null == timer) {
            timer = Executors.newScheduledThreadPool(2);
            /*1s后启动任务，每1s执行一次**/
            timer.scheduleWithFixedDelay(task, 400, 300, TimeUnit.MILLISECONDS);
        }
    }

    public void reset() {
        if (getPlayerViewListener().isList()) {
            if (player != null) {
                unNetworkBroadcastReceiver();
                if (position == -1) {
                    clearResumePosition();
                } else {
                    tags.put(position, player.getCurrentPosition());
                    tags2.put(position, player.getCurrentWindowIndex());
                }
                player.stop();
                player.removeListener(componentListener);
                player.release();
                player = null;
                resetInit();
            }
        } else {
            releasePlayers();
        }

    }

    /***
     * 重置点击事件
     * **/
    void resetInit() {
        getPlayerViewListener().setPlayerBtnOnTouch(true);
        getPlayerViewListener().reset();
    }

    /***
     * 多种分辨率点击播放
     * @param uri uri
     * ***/
    void setSwitchPlayer(@NonNull String uri) {
        handPause = false;
        updateResumePosition();
        if (mediaSourceBuilder.getMediaSource() instanceof ConcatenatingMediaSource) {
            ConcatenatingMediaSource source = (ConcatenatingMediaSource) mediaSourceBuilder.getMediaSource();
            source.getMediaSource(source.getSize() - 1).releaseSource(null);
            source.addMediaSource(mediaSourceBuilder.initMediaSource(Uri.parse(uri)));
            isSwitch = true;
        } else {
            mediaSourceBuilder.setMediaUri(Uri.parse(uri));
            onPlayNoAlertVideo();
        }
    }

    /***
     * 是否播放中
     * @return boolean boolean
     */
    public boolean isPlaying() {
        if (player == null) return false;
        int playbackState = player.getPlaybackState();
        return playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED
                && player.getPlayWhenReady();
    }
    /*****************设置参数方法*************************/

    /****
     * 设置tag 标记 防止列表复用进度导致,
     * @param position  position
     * **/
    public void setTag(int position) {
        this.position = position;
    }

    /***
     * 设置播放路径
     * @param drmSessionManager 一个可选的 {@link DrmSessionManager}. 如果DRM得到保护，可能是null
     */
    public void setDrmSessionManager(DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
        this.drmSessionManager = drmSessionManager;
    }

    /****
     *  支持视频源动态添加
     *
     * @param videoUri videoUri
     */
    public void addMediaUri(@NonNull Uri videoUri) {
        mediaSourceBuilder.addMediaUri(videoUri);
    }

    /***
     * 设置播放路径
     * @param uri 路径
     */
    public void setPlayUri(@NonNull String uri) {
        setPlayUri(Uri.parse(uri));
    }

    /****
     * @param indexType 设置当前索引视频屏蔽进度
     * @param firstVideoUri 预览的视频
     * @param secondVideoUri 第二个视频
     */
    public void setPlayUri(@Size(min = 0) int indexType, @NonNull String firstVideoUri, @NonNull String secondVideoUri) {
        setPlayUri(indexType, Uri.parse(firstVideoUri), Uri.parse(secondVideoUri));

    }

    /***
     * 设置多线路播放
     * @param index 选中播放线路
     * @param videoUri 视频地址
     * @param name 清清晰度显示名称
     */
    public void setPlaySwitchUri(int index, @NonNull String[] videoUri, @NonNull String[] name) {
        setPlaySwitchUri(index, Arrays.asList(videoUri), Arrays.asList(name));
    }


    /***
     * 设置多线路播放
     * @param switchIndex 选中播放线路索引
     * @param videoUri 视频地址
     * @param name 清清晰度显示名称
     */
    public void setPlaySwitchUri(int switchIndex, @NonNull List<String> videoUri, @NonNull List<String> name) {
        mediaSourceBuilder.setMediaSwitchUri(videoUri, switchIndex);
        getPlayerViewListener().setSwitchName(name, switchIndex);
    }

    /**
     * 设置自定义键唯一标识原始流。用于缓存索引。*默认值是{ null }。 不支持流式媒体
     *
     * @param customCacheKey 唯一标识原始流的自定义密钥。用于缓存索引。
     *
     * @throws IllegalStateException If one of the {@code create} methods has already been called.
     */
    public void setCustomCacheKey(@NonNull String customCacheKey) {
        mediaSourceBuilder.setCustomCacheKey(customCacheKey);
    }
    /****
     * @param indexType 设置当前索引视频屏蔽进度
     * @param switchIndex the switch index
     * @param firstVideoUri 预览视频
     * @param secondVideoUri 内容视频多线路设置
     * @param name the name
     */
    public void setPlaySwitchUri(@Size(min = 0) int indexType, @Size(min = 0) int switchIndex, @NonNull String firstVideoUri, String[] secondVideoUri, @NonNull String[] name) {
        setPlaySwitchUri(indexType, switchIndex, firstVideoUri, Arrays.asList(secondVideoUri), Arrays.asList(name));

    }

    /****
     * @param indexType 设置当前索引视频屏蔽进度
     * @param switchIndex the switch index
     * @param firstVideoUri 预览视频
     * @param secondVideoUri 内容视频多线路设置
     * @param name the name
     */
    public void setPlaySwitchUri(@Size(min = 0) int indexType, @Size(min = 0) int switchIndex, @NonNull String firstVideoUri, List<String> secondVideoUri, @NonNull List<String> name) {
        mediaSourceBuilder.setMediaUri(indexType, switchIndex, Uri.parse(firstVideoUri), secondVideoUri);
        getPlayerViewListener().setSwitchName(name, switchIndex);
    }

    /**
     * 设置播放路径
     *
     * @param uri 路径
     */
    public void setPlayUri(@NonNull Uri uri) {
        mediaSourceBuilder.setMediaUri(uri);
    }

    /****
     * 设置视频列表播放
     * @param indexType 设置当前索引视频屏蔽进度
     * @param firstVideoUri 预览的视频
     * @param secondVideoUri 第二个视频
     */
    public void setPlayUri(@Size(min = 0) int indexType, @NonNull Uri firstVideoUri, @NonNull Uri secondVideoUri) {
        mediaSourceBuilder.setMediaUri(indexType, firstVideoUri, secondVideoUri);
    }


    /****
     * 设置视频列表播放
     * @param <T>     你的实体类
     * @param uris 集合
     */
    public <T extends ItemVideo> void setPlayUri(@NonNull List<T> uris) {
        mediaSourceBuilder.setMediaUri(uris);
    }


    /***
     * 设置进度
     * @param resumePosition 毫秒
     */
    public void setPosition(long resumePosition) {
        this.resumePosition = resumePosition;
    }

    /***
     * 设置进度
     * @param currWindowIndex 视频索引
     * @param currPosition 毫秒
     */
    public void setPosition(int currWindowIndex, long currPosition) {
        this.resumeWindow = currWindowIndex;
        this.resumePosition = currPosition;
    }

    /***
     * 设置进度
     * @param  positionMs  positionMs
     */
    public void seekTo(long positionMs) {
        if (player != null) {
            player.seekTo(positionMs);
        }
    }

    /***
     * 设置进度
     * @param  windowIndex  windowIndex
     * @param  positionMs  positionMs
     */
    public void seekTo(int windowIndex, long positionMs) {
        if (player != null) {
            player.seekTo(windowIndex, positionMs);
        }
    }


    /***
     * 设置倍数播放创建新的回放参数
     *
     * @param speed 播放速度加快   1f 是正常播放 小于1 慢放
     * @param pitch 音高被放大  1f 是正常播放 小于1 慢放
     */
    public void setPlaybackParameters(@Size(min = 0) float speed, @Size(min = 0) float pitch) {
        playbackParameters = new PlaybackParameters(speed, pitch);
    }

    /***
     * 设置播放或暂停
     * @param value true 播放  false  暂停
     */
    public void setStartOrPause(boolean value) {
        if (player != null) {
            player.setPlayWhenReady(value);
        }
    }

    /***
     * 设置显示多线路图标
     * @param showVideoSwitch true 显示 false 不显示
     */
    public void setShowVideoSwitch(boolean showVideoSwitch) {
        getPlayerViewListener().setShowWitch(showVideoSwitch);
    }

    /***
     * 设置进度进度条拖拽
     * @param isOpenSeek true 启用 false 不启用
     */
    public void setSeekBarSeek(boolean isOpenSeek) {
        getPlayerViewListener().setSeekBarOpenSeek(isOpenSeek);
    }

    /***
     * 设置事件分发
     * @param basePlayerListener 实例
     */
    public void addBasePlayerListener(@NonNull BasePlayerListener basePlayerListener) {
        basePlayerListeners.add(basePlayerListener);
    }

    /***
     * 设置视频信息回调
     * @param videoInfoListener 实例
     */
    public void addVideoInfoListener(@NonNull VideoInfoListener videoInfoListener) {
        videoInfoListeners.add(videoInfoListener);
    }

    /***
     *移除视频信息回调
     * @param videoInfoListener 实例
     */
    public void removeVideoInfoListener(@NonNull VideoInfoListener videoInfoListener) {
        videoInfoListeners.remove(videoInfoListener);
    }

    /****
     * 设置点击播放按钮回调, 交给用户处理
     * @param onClickListener 回调实例
     */
    public void setOnPlayClickListener(@Nullable View.OnClickListener onClickListener) {
        playComponentListener.setOnPlayClickListener(onClickListener);
    }

    /***
     * 添加多个视频窗口状态
     * @param windowListener 实例
     */
    public void addOnWindowListener(@NonNull VideoWindowListener windowListener) {
        videoWindowListeners.add(windowListener);
    }

    /***
     * 移除多个视频窗口状态
     * @param windowListener 实例
     */
    public void removeOnWindowListener(@NonNull VideoWindowListener windowListener) {
        videoWindowListeners.remove(windowListener);
    }

    /***
     * 添加视频播放准备状态
     * @param preparedListener 实例
     */
    public void addOnPreparedListeners(@NonNull OnPreparedListener preparedListener) {
        mOnPreparedListeners.add(preparedListener);
    }

    /***
     * 移除多个视频窗口准备状态
     * @param preparedListener 实例
     */
    public void removeOnPreparedListeners(@NonNull OnPreparedListener preparedListener) {
        mOnPreparedListeners.remove(preparedListener);
    }
/********************下面主要获取和操作方法*****************************************************************/


    /***
     * 返回视频总数
     * @return int window count
     */
    public int getWindowCount() {
        if (player == null) {
            return 0;
        }
        return player.getCurrentTimeline().isEmpty() ? 1 : player.getCurrentTimeline().getWindowCount();
    }

    /***
     * 下跳转下一个视频
     */
    public void next() {
        getPlayerViewListener().next();
    }

    /***
     * 下跳转上一个视频
     */
    public void previous() {
        getPlayerViewListener().previous();
    }

    /***
     * 隐藏控制布局
     */
    public void hideControllerView() {
        hideControllerView(false);
    }

    /***
     * 隐藏控制布局
     */
    public void showControllerView() {
        getPlayerViewListener().toggoleController(false, true);
    }

    /***
     * 隐藏控制布局
     * @param isShowFull 是否显示全屏按钮
     */
    public void hideControllerView(boolean isShowFull) {
        getPlayerViewListener().toggoleController(isShowFull, false);
    }

    /***
     * 隐藏控制布局
     * @param isShowFull 是否显示全屏按钮
     */
    public void showControllerView(boolean isShowFull) {
        getPlayerViewListener().toggoleController(isShowFull, true);
    }


    /****
     * 横竖屏切换
     *
     * @param configuration 旋转
     */
    public void onConfigurationChanged(Configuration configuration) {
        getPlayerViewListener().onConfigurationChanged(configuration.orientation);
    }

    /***
     * 获取内核播放实例
     * @return SimpleExoPlayer player
     */
    public SimpleExoPlayer getPlayer() {
        return player;
    }

    /**
     * 返回视频总进度  以毫秒为单位
     *
     * @return long duration
     */
    public long getDuration() {
        return player == null ? 0 : player.getDuration();
    }

    /**
     * 返回视频当前播放进度  以毫秒为单位
     *
     * @return long current position
     */
    public long getCurrentPosition() {
        return player == null ? 0 : player.getCurrentPosition();
    }

    /**
     * 返回视频当前播放d缓冲进度  以毫秒为单位
     *
     * @return long buffered position
     */
    public long getBufferedPosition() {
        return player == null ? 0 : player.getBufferedPosition();
    }


    public VideoPlayerView getVideoPlayerView() {
        return videoPlayerView;
    }

    public MediaSourceBuilder getMediaSourceBuilder() {
        return mediaSourceBuilder;
    }

    /***
     * 注册广播监听
     */
    private void registerReceiverNet() {
        if (mNetworkBroadcastReceiver == null) {
            IntentFilter intentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
            mNetworkBroadcastReceiver = new NetworkBroadcastReceiver();
            activity.registerReceiver(mNetworkBroadcastReceiver, intentFilter);
        }
    }

    /***
     * 取消广播监听
     */
    protected void unNetworkBroadcastReceiver() {
        if (mNetworkBroadcastReceiver != null) {
            activity.unregisterReceiver(mNetworkBroadcastReceiver);
        }
        mNetworkBroadcastReceiver = null;
    }

    /***
     * 网络监听类
     ***/
    private final class NetworkBroadcastReceiver extends BroadcastReceiver {
        long is = 0;

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (null != action && action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                ConnectivityManager mConnectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                assert mConnectivityManager != null;
                NetworkInfo netInfo = mConnectivityManager.getActiveNetworkInfo();
                if (netInfo != null && netInfo.isAvailable()) {
                    if (netInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
                        /////////3g网络
                        if (System.currentTimeMillis() - is > 500) {
                            is = System.currentTimeMillis();
                            if (VideoPlayerManager.getInstance().isClick()) {
                                return;
                            }
                            if (!isPause) {
                                getPlayerViewListener().showAlertDialog();
                            }
                        }
                    }
                }
            }

        }
    }

    /***
     * view 给控制类 回调类
     */
    Player.EventListener componentListener = new Player.EventListener() {
        boolean isRemove;

        @Override
        public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {
            if (isSwitch) {
                isSwitch = false;
                isRemove = true;
                player.seekTo(player.getNextWindowIndex(), resumePosition);
            }
        }

        @Override
        public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
            // Log.d(TAG, "onTracksChanged:" + currentWindowIndex + "_:" + player.getCurrentTimeline().getWindowCount());
            //   Log.d(TAG, "onTracksChanged:" + player.getNextWindowIndex() + "_:" + player.getCurrentTimeline().getWindowCount());
            if (getWindowCount() > 1) {
                if (isRemove) {
                    isRemove = false;
                    mediaSourceBuilder.removeMedia(resumeWindow);
                    return;
                }
                if (!videoWindowListeners.isEmpty()) {
                    for (VideoWindowListener videoWindowListener : videoWindowListeners) {
                        videoWindowListener.onCurrentIndex(player.getCurrentWindowIndex(), getWindowCount());
                    }
                }
                if (mediaSourceBuilder.getIndexType() < 0) {
                    return;
                }
                boolean setOpenSeek = !(mediaSourceBuilder.getIndexType() == player.getCurrentWindowIndex() && mediaSourceBuilder.getIndexType() > 0);
                for (BasePlayerListener basePlayerListener : basePlayerListeners) {
                    basePlayerListener.setPlayerGestureOnTouch(setOpenSeek);
                }
                getPlayerViewListener().setOpenSeek(setOpenSeek);
            }
        }

        @Override
        public void onPositionDiscontinuity(int reason) {
        }

        /**
         * 视频的播放状态
         * STATE_IDLE 播放器空闲，既不在准备也不在播放
         * STATE_PREPARING 播放器正在准备
         * STATE_BUFFERING 播放器已经准备完毕，但无法立即播放。此状态的原因有很多，但常见的是播放器需要缓冲更多数据才能开始播放
         * STATE_ENDED 播放已完毕
         */
        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            if (playWhenReady) {
                //防锁屏
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                //解锁屏
                activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
            for (VideoInfoListener videoInfoListener : videoInfoListeners) {
                videoInfoListener.isPlaying(player.getPlayWhenReady());
            }
            Log.d(TAG, "onPlayerStateChanged:" + playbackState + "+playWhenReady:" + playWhenReady);
            switch (playbackState) {
                case Player.STATE_BUFFERING:
                    if (playWhenReady) {
                        getPlayerViewListener().showLoadStateView(View.VISIBLE);
                    }
                    for (VideoInfoListener videoInfoListener : videoInfoListeners) {
                        videoInfoListener.onLoadingChanged();
                    }
                    break;
                case Player.STATE_ENDED:
                    Log.d(TAG, "onPlayerStateChanged:ended。。。");
                    isEnd = true;
                    clearResumePosition();
                    getPlayerViewListener().showReplayView(View.VISIBLE);
                    for (VideoInfoListener videoInfoListener : videoInfoListeners) {
                        videoInfoListener.onPlayEnd();
                    }
                    break;
                case Player.STATE_IDLE:
                    Log.d(TAG, "onPlayerStateChanged::网络状态差，请检查网络。。。");
                    getPlayerViewListener().showErrorStateView(View.VISIBLE);
                    break;
                case Player.STATE_READY:
                    mPlayerViewListener.showPreview(View.GONE, false);
                    getPlayerViewListener().showLoadStateView(View.GONE);
                    if (playWhenReady) {
                        Log.d(TAG, "onPlayerStateChanged:准备播放");
                        isPause = false;
                        for (VideoInfoListener videoInfoListener : videoInfoListeners) {
                            videoInfoListener.onPlayStart(getCurrentPosition());
                        }
                    }
                    break;
                default:
            }
        }

        @Override
        public void onRepeatModeChanged(int repeatMode) {
        }

        public void onPlayerError(ExoPlaybackException e) {
            Log.e(TAG, "onPlayerError:" + e.getMessage());
            updateResumePosition();
            if (VideoPlayUtils.isBehindLiveWindow(e)) {
                clearResumePosition();
                startVideo();
            } else {
                getPlayerViewListener().showErrorStateView(View.VISIBLE);
                for (VideoInfoListener videoInfoListener : videoInfoListeners) {
                    videoInfoListener.onPlayerError(player.getPlaybackError());
                }
            }
        }

        @Override
        public void onLoadingChanged(boolean isLoading) {

        }

        @Override
        public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {

        }

        @Override
        public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
        }

        @Override
        public void onSeekProcessed() {
            Log.e(TAG, "onSeekProcessed:");
        }
    };


    public interface OnPreparedListener {
        /**
         * Called when the media file is ready for playback.
         *
         * @param player the MediaPlayer that is ready for playback
         */
        void onPrepared(SimpleExoPlayer player);
    }

}

