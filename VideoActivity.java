package com.boolint.satpic;

import androidx.activity.EdgeToEdge;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.AnimationDrawable;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.boolint.satpic.helper.ADHelper;
import com.boolint.satpic.helper.DeviceHelper;
import com.boolint.satpic.helper.JejuCctvVideoOpenApiHelper;

public class VideoActivity extends AppCompatActivity {
    private boolean isLandscape = false;
    private boolean isLockedLandscape = false;
    private boolean isLockedPortrait = false;

    // 고정 해제 딜레이를 위한 타임스탬프
    private long lockTimestamp = 0;
    private static final long LOCK_DELAY_MS = 1000; // 1초 딜레이

    // 센서 범위를 더 엄격하게
    private static final int LANDSCAPE_MIN = 80;
    private static final int LANDSCAPE_MAX = 100;
    private static final int LANDSCAPE_MIN_REVERSE = 260;
    private static final int LANDSCAPE_MAX_REVERSE = 280;

    private static final int PORTRAIT_MIN = 350;
    private static final int PORTRAIT_MAX = 10;
    private static final int PORTRAIT_MIN_REVERSE = 170;
    private static final int PORTRAIT_MAX_REVERSE = 190;

    private ContentObserver rotationObserver;
    private OrientationEventListener orientationListener;

    private TextureView textureView;
    private ExoPlayer exoPlayer;
    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;
    private Matrix matrix = new Matrix();
    private float scaleFactor = 1.0f;
    private float focusX = 0f, focusY = 0f;
    MediaItem mediaItem;
    TextView tvTitle;
    ImageView imgCctvType;
    TextView tvCopyRight;

    ImageView imgScreen;
    private ImageView ivHome;

    String cctvType = "";
    String cctvName = "";
    String cctvUrl = "";
    String cctvRoadSectionId = "";

    ImageView ivLoading;
    LinearLayout llProgress;

    private Surface videoSurface;

    // 비디오 크기 저장용 변수
    private int currentVideoWidth = 0;
    private int currentVideoHeight = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_video);

        imgScreen = findViewById(R.id.img_screen);
        llProgress = findViewById(R.id.llProgress);

        tvTitle = findViewById(R.id.tv_title);
        imgCctvType = findViewById(R.id.imgCctvType);
        tvCopyRight = findViewById(R.id.tvCopyRight);

        textureView = findViewById(R.id.textureView);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            } else {
                v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            }

            return insets;
        });

        // 초기 방향 확인
        int currentOrientation = getResources().getConfiguration().orientation;
        isLandscape = (currentOrientation == Configuration.ORIENTATION_LANDSCAPE);

        applyBarsByOrientation(currentOrientation);
        updateButtonIcon();

        imgScreen.setOnClickListener(v -> toggleOrientation());

        setupRotationObserver();
        setupOrientationListener();

        llProgress.setVisibility(View.VISIBLE);
        ivLoading = findViewById(R.id.iv_loading);
        ivLoading.setBackgroundResource(R.drawable.ani_loading_sat);
        ((AnimationDrawable) ivLoading.getBackground()).start();

        Intent intent = getIntent();
        cctvType = intent.getStringExtra("cctv_type");
        cctvName = intent.getStringExtra("cctv_name");
        cctvUrl = intent.getStringExtra("cctv_url");
        cctvRoadSectionId = intent.getStringExtra("cctv_road_section_id");
        Log.d("ttt", "onCreate: " + cctvUrl);

        View layoutActionBar = findViewById(R.id.layout_actionbar);
        ivHome = findViewById(R.id.iv_home);
        ivHome.setOnClickListener(v -> onBackPressed());

        ViewCompat.setOnApplyWindowInsetsListener(layoutActionBar, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            int offset = (int) (systemBars.top * 1.2f);
            if (offset == 0) {
                offset = dpToPx(this, 10);
            }
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.topMargin = offset;
            v.setLayoutParams(lp);

            return insets;
        });

        ADHelper.updateAdVisibilityForDeviceConfiguration(this);
        ADHelper.settingAdEx(this);

        exoPlayer = new ExoPlayer.Builder(this).build();
        exoPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                if (videoSurface != null) {
                    videoSurface.release();
                }
                videoSurface = new Surface(surface);
                exoPlayer.setVideoSurface(videoSurface);
                applyVideoFit();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                Log.d("ttt", "onSurfaceTextureSizeChanged: ");
                applyVideoFit();
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                try {
                    if (exoPlayer != null) {
                        exoPlayer.clearVideoSurface();
                    }
                } catch (Exception e) {
                    Log.e("VideoActivity", "Error clearing video surface", e);
                }

                if (videoSurface != null) {
                    try {
                        videoSurface.release();
                    } catch (Exception e) {
                        Log.e("VideoActivity", "Error releasing surface", e);
                    } finally {
                        videoSurface = null;
                    }
                }
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        });

        if (textureView.isAvailable()) {
            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            if (surfaceTexture != null) {
                videoSurface = new Surface(surfaceTexture);
                exoPlayer.setVideoSurface(videoSurface);
            }
        }

        final int TIMEOUT_MS = 10000;
        Handler timeoutHandler = new Handler(Looper.getMainLooper());
        Runnable timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (exoPlayer.getPlaybackState() == Player.STATE_BUFFERING) {
                    llProgress.setVisibility(View.GONE);
                    Toast.makeText(VideoActivity.this, "Playback timed out", Toast.LENGTH_SHORT).show();
                    exoPlayer.stop();
                }
            }
        };

        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onVideoSizeChanged(VideoSize videoSize) {
                currentVideoWidth = videoSize.width;
                currentVideoHeight = videoSize.height;

                fitVideoToView(videoSize);
                textureView.setVisibility(View.VISIBLE);

                llProgress.animate()
                        .alpha(0f)
                        .setDuration(500)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                llProgress.setVisibility(View.GONE);
                            }
                        });
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) {
                    timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MS);
                } else {
                    timeoutHandler.removeCallbacks(timeoutRunnable);
                }

                if (state == Player.STATE_READY) {
                    textureView.setVisibility(View.VISIBLE);
                } else if (state == Player.STATE_ENDED) {
                    Log.d("ttt", "STATE_ENDED: ");
                } else {
                    textureView.setVisibility(View.INVISIBLE);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                llProgress.setVisibility(View.GONE);
                Toast.makeText(VideoActivity.this, "cctv connection error", Toast.LENGTH_SHORT).show();
            }
        });

        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float scaleFactorDelta = detector.getScaleFactor();
                scaleFactor *= scaleFactorDelta;
                scaleFactor = Math.max(0.5f, Math.min(scaleFactor, 3.0f));

                focusX = detector.getFocusX();
                focusY = detector.getFocusY();

                matrix.postScale(scaleFactorDelta, scaleFactorDelta, focusX, focusY);
                textureView.setTransform(matrix);
                return true;
            }
        });

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                matrix.postTranslate(-distanceX, -distanceY);
                textureView.setTransform(matrix);
                return true;
            }
        });

        textureView.setOnTouchListener((v, event) -> {
            scaleGestureDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);
            return true;
        });

        try {
            imgCctvType.setImageResource(R.drawable.cctv2634);
            tvTitle.setText(cctvName);

            if ("jeju".equals(cctvType)) {
                tvCopyRight.setText(getString(R.string.info_cctv_copyright));
                startJejuCctvVideo();
            } else {
                tvCopyRight.setText(getString(R.string.info_cctv_copyright));
                startCctvVideo();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private void applyVideoFit() {
        if (currentVideoWidth > 0 && currentVideoHeight > 0) {
            VideoSize videoSize = new VideoSize(currentVideoWidth, currentVideoHeight);
            fitVideoToView(videoSize);
        }
    }

    private void fitVideoToView(VideoSize videoSize) {
        int videoWidth = videoSize.width;
        int videoHeight = videoSize.height;
        int viewWidth = textureView.getWidth();
        int viewHeight = textureView.getHeight();

        if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) {
            return;
        }

        boolean isLandscape = (getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE);

        Matrix matrix1 = new Matrix();
        float scaleX, scaleY;
        float cx = viewWidth / 2f;
        float cy = viewHeight / 2f;

        float videoAspect = (float) videoWidth / videoHeight;
        float viewAspect = (float) viewWidth / viewHeight;

        if (videoAspect > viewAspect) {
            scaleX = 1.0f;
            scaleY = viewAspect / videoAspect;
        } else {
            scaleX = videoAspect / viewAspect;
            scaleY = 1.0f;
        }
        matrix1.setScale(scaleX, scaleY, cx, cy);

        Point point = DeviceHelper.getDisplaySize(VideoActivity.this);
        int deviceWidth = point.x;
        int deviceHeight = point.y;

        float paddingRatio = ADHelper.getBottomPaddingRatio(VideoActivity.this);
        float padding = deviceHeight * paddingRatio * 0.5f;

        RectF srcViewRectF = new RectF(0, 0, deviceWidth, deviceHeight);
        RectF targetViewRectF = new RectF(0, 0, deviceWidth, deviceHeight - padding);

        Matrix matrix2 = new Matrix();
        if (isLandscape) {
            matrix2.setRectToRect(srcViewRectF, targetViewRectF, Matrix.ScaleToFit.CENTER);
        }

        matrix.set(matrix1);
        matrix.postConcat(matrix2);

        textureView.setTransform(matrix);
    }

    private void startCctvVideo() {
        new Thread() {
            public void run() {
                try {
                    Message msg = handler.obtainMessage();
                    msg.what = 100;
                    handler.sendMessage(msg);
                } catch (Exception e) {
                    e.printStackTrace();
                    Message msg = handler.obtainMessage();
                    msg.what = -1;
                    handler.sendMessage(msg);
                }
            }
        }.start();
    }

    private void startJejuCctvVideo() {
        new Thread() {
            public void run() {
                try {
                    String url1 = JejuCctvVideoOpenApiHelper.getCctvInfoAndSetCookie(cctvRoadSectionId);
                    cctvUrl = JejuCctvVideoOpenApiHelper.getCctvStreamUrl(url1);

                    Message msg = handler.obtainMessage();
                    msg.what = 100;
                    handler.sendMessage(msg);
                } catch (Exception e) {
                    e.printStackTrace();
                    Message msg = handler.obtainMessage();
                    msg.what = -1;
                    handler.sendMessage(msg);
                }
            }
        }.start();
    }

    Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            if (msg.what == 100) {
                try {
                    Uri videoUri = Uri.parse(cctvUrl);
                    if (videoUri.getLastPathSegment() != null
                            && (videoUri.getLastPathSegment().contains(".m3u") || videoUri.getLastPathSegment().contains(".m3u8"))) {
                        mediaItem = new MediaItem.Builder().setUri(videoUri).setMimeType(MimeTypes.APPLICATION_M3U8).build();
                    } else {
                        mediaItem = MediaItem.fromUri(videoUri);
                    }
                    exoPlayer.setMediaItem(mediaItem);
                    exoPlayer.prepare();
                    exoPlayer.play();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    };

    private void setupOrientationListener() {
        orientationListener = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation == ORIENTATION_UNKNOWN) return;

                long currentTime = System.currentTimeMillis();
                if (currentTime - lockTimestamp < LOCK_DELAY_MS) {
                    return;
                }

                boolean deviceIsLandscape =
                        (orientation >= LANDSCAPE_MIN && orientation <= LANDSCAPE_MAX) ||
                                (orientation >= LANDSCAPE_MIN_REVERSE && orientation <= LANDSCAPE_MAX_REVERSE);

                boolean deviceIsPortrait =
                        (orientation >= 0 && orientation <= PORTRAIT_MAX) ||
                                (orientation >= PORTRAIT_MIN && orientation <= 360) ||
                                (orientation >= PORTRAIT_MIN_REVERSE && orientation <= PORTRAIT_MAX_REVERSE);

                if (isLockedLandscape && deviceIsLandscape) {
                    runOnUiThread(() -> {
                        Log.d("VideoActivity", "Unlocking landscape mode - returning to sensor");
                        isLockedLandscape = false;
                        if (isAutoRotationEnabled()) {
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                        }
                    });
                } else if (isLockedPortrait && deviceIsPortrait) {
                    runOnUiThread(() -> {
                        Log.d("VideoActivity", "Unlocking portrait mode - returning to sensor");
                        isLockedPortrait = false;
                        if (isAutoRotationEnabled()) {
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                        }
                    });
                }
            }
        };

        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable();
        }
    }

    private void setupRotationObserver() {
        rotationObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                boolean autoRotateEnabled = isAutoRotationEnabled();
                Log.d("VideoActivity", "Auto rotation changed: " + autoRotateEnabled);

                // 자동회전이 꺼졌고, 현재 고정 모드가 아니면
                if (!autoRotateEnabled && !isLockedLandscape && !isLockedPortrait) {
                    // 현재 방향으로 고정
                    int currentOrientation = getResources().getConfiguration().orientation;
                    if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                    } else {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    }
                }
                // 자동회전이 켜졌고, 고정 모드가 아니면
                else if (autoRotateEnabled && !isLockedLandscape && !isLockedPortrait) {
                    // 센서 모드로 복귀
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                }
            }
        };

        getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
                true,
                rotationObserver
        );
    }

    private boolean isAutoRotationEnabled() {
        try {
            return Settings.System.getInt(
                    getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION
            ) == 1;
        } catch (Settings.SettingNotFoundException e) {
            return true;
        }
    }

    private void toggleOrientation() {
        if (isLandscape) {
            goToPortrait();
        } else {
            goToLandscape();
        }
    }

    private void goToLandscape() {
        isLandscape = true;
        isLockedLandscape = true;
        isLockedPortrait = false;

        lockTimestamp = System.currentTimeMillis();

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        applyBarsByOrientation(Configuration.ORIENTATION_LANDSCAPE);
        updateButtonIcon();

        Log.d("VideoActivity", "Locked to landscape mode");
    }

    private void goToPortrait() {
        isLandscape = false;
        isLockedLandscape = false;
        isLockedPortrait = true;

        lockTimestamp = System.currentTimeMillis();

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        applyBarsByOrientation(Configuration.ORIENTATION_PORTRAIT);
        updateButtonIcon();

        Log.d("VideoActivity", "Locked to portrait mode");
    }

    private void updateButtonIcon() {
        if (isLandscape) {
            imgScreen.setImageResource(R.drawable.full_screen_off);
        } else {
            imgScreen.setImageResource(R.drawable.full_screen_on);
        }
    }

    private void applyBarsByOrientation(int orientation) {
        WindowInsetsControllerCompat controller =
                ViewCompat.getWindowInsetsController(getWindow().getDecorView());

        if (controller == null) return;

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            controller.hide(WindowInsetsCompat.Type.statusBars());
            controller.show(WindowInsetsCompat.Type.navigationBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            controller.show(WindowInsetsCompat.Type.systemBars());

            int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            boolean isLightMode = (nightMode != Configuration.UI_MODE_NIGHT_YES);
            controller.setAppearanceLightStatusBars(isLightMode);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        ADHelper.updateAdVisibilityForDeviceConfiguration(this);

        if (!isLockedLandscape && !isLockedPortrait) {
            isLandscape = (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE);
        }

        applyBarsByOrientation(newConfig.orientation);
        updateButtonIcon();

        textureView.post(new Runnable() {
            @Override
            public void run() {
                applyVideoFit();
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (isLandscape) {
            goToPortrait();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // ExoPlayer는 자동으로 백그라운드에서 일시정지됨
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("ttt", "onDestroy");

        try {
            if (exoPlayer != null) {
                exoPlayer.clearVideoSurface();
                exoPlayer.release();
                exoPlayer = null;
            }
        } catch (Exception e) {
            Log.e("VideoActivity", "Error releasing player", e);
        }

        if (videoSurface != null) {
            try {
                videoSurface.release();
            } catch (Exception e) {
                Log.e("VideoActivity", "Error releasing surface", e);
            } finally {
                videoSurface = null;
            }
        }

        if (rotationObserver != null) {
            getContentResolver().unregisterContentObserver(rotationObserver);
        }
        if (orientationListener != null) {
            orientationListener.disable();
        }
    }

    private static int dpToPx(Context c, int dp) {
        return Math.round(dp * c.getResources().getDisplayMetrics().density);
    }
}
