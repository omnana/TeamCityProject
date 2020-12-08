// by shanggjm: 目前只完成竖屏的适配，模屏适配需要0.5d工作日进行部分代码处理，需要时沟通
// 大致原理：
//   利用 mediaPlayer 将视频播放到 textureView 上
//   将 textureView 加到 unityPlayer 上
//   移除视频的两个条件要都满足：C#主动调用移除接口，播放视频完成
//   在C#初步化比较久的情况下视频播放完成后会一直停留在最后一帧的画面上，直到C#通知关闭
// 其他内容：
//   目前处理了竖屏情况下，视频的长宽比适配的问题，横屏还没处理
// 用法：
//   在需要时调用 Create 方法
//   在C#调用结束时，在安卓UI线程调用 CSharpEnd, 这条要特别注意

package com.jzyx.acmeis;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.widget.FrameLayout;
import com.unity3d.player.UnityPlayer;

import java.io.IOException;

public class CustomSplash {

    public static CustomSplash mInstance;
    UnityPlayer unityPlayer; // 视频的TextureView是加在这个player上的
    FrameLayout bgView; // 播放视频时在视频没填满的情况下的白底(目前直接白色没给配置）
    TextureView textureView; // 播放视频用的View，会保留视频的长宽比
    MediaPlayer mediaPlayer; // 真正播放视频的类

    boolean isEndCallByCSharp = false; // C# 是否调用过结束接口
    boolean isVideoPlayEnd = false; // 视频是否播放完成

    public static void Create(Activity acitivity, Bundle savedInstanceState, UnityPlayer unityPlayer)
    {
        mInstance = new CustomSplash();
        mInstance.Init(acitivity,savedInstanceState,unityPlayer);
    }

    public void Init(Activity acitivity, Bundle savedInstanceState, UnityPlayer unityPlayer)
    {
//        Log.d("JZXYAndroid:","========================================================CustomSplash Init:" + Thread.currentThread().getId());
        this.unityPlayer = unityPlayer;
        this.mediaPlayer = new MediaPlayer();

        // 背景图

        bgView = new FrameLayout(acitivity);
        bgView.setBackgroundColor(Color.WHITE);
        this.unityPlayer.addView(bgView);

        // 视频视图创建（在创建好了才去正式加载视频）
        this.textureView = new TextureView(acitivity.getApplicationContext());
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                mediaPlayer.setSurface(new Surface(surface));
                try {
                    mediaPlayer.prepare();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}
            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {return false;}
            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) { }
        });

        bgView.addView(textureView);
        textureView.requestFocus();

        // 处理视频路径
        Resources res = acitivity.getApplicationContext().getResources();
        String packageName = acitivity.getApplicationContext().getPackageName();
        int splashVideoId = res.getIdentifier("splash_video", "raw", packageName);
        Uri video = Uri.parse("android.resource://" + packageName + "/" + splashVideoId);
        try {
            // 初始化播放器，增加视频加载完的回调（在Prepared事件中处理）
            mediaPlayer.setDataSource(acitivity.getApplicationContext(),video);
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.start();
                    mp.setLooping(false);

                    //===================================视频尺寸适配代码===================================
                    //获取屏幕高度
                    DisplayMetrics metrics = new DisplayMetrics();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        acitivity.getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
                    } else {
                        acitivity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
                    }

                    // 处理视频屏幕适配（这里才有视频的相关信息，设置为上下剧中）
                    int width = metrics.widthPixels;
                    int height = mp.getVideoHeight() * width / mp.getVideoWidth();
                    FrameLayout.LayoutParams param  = new FrameLayout.LayoutParams(width, height);
                    param.topMargin = (metrics.heightPixels - height) / 2;
                    textureView.setLayoutParams(param);
                    //===================================视频尺寸适配代码===================================
                }
            });
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
//                    Log.d("JZXYAndroid:","========================================================CustomSplash Play end:" + Thread.currentThread().getId());
                    isVideoPlayEnd = true;
                    if (isEndCallByCSharp) { // 播放完要等C#接口调用了才能销毁
                        ClearLogo();
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 要在安卓UI线程上执行，调用方如果不能保证是在UI线程，那就要自行 activity.runOnUIThread
    public void CSharpEnd() {
        // 要等播放完才能销毁
        if (!isEndCallByCSharp) {// 防重复调用的问题
//            Log.d("JZXYAndroid:","=================================================CustomSplash CSharp end:" + Thread.currentThread().getId());
            isEndCallByCSharp = true;
            if (isVideoPlayEnd) {
                ClearLogo();
            }
        }
    }

    private void ClearLogo() {
        // 防重复调用
        if(textureView == null) { return; }
//        Log.d("JZXYAndroid:","=================================================CustomSplash Clear Begin:" + Thread.currentThread().getId());
        // 清理并销毁 mediaPlayer
        mediaPlayer.setOnCompletionListener(null);
        mediaPlayer.setOnPreparedListener(null);
        mediaPlayer.release();
        mediaPlayer = null;

        // 移除掉 view
        unityPlayer.removeView(bgView);
        bgView.removeView(textureView);
        bgView = null;
        textureView = null;

        // 清理自己
        mInstance = null;
//        Log.d("JZXYAndroid:","=================================================CustomSplash Clear End:" + Thread.currentThread().getId());
    }
}