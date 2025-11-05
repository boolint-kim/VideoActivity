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

    // ✅ 추가: 고정 해제 딜레이를 위한 타임스탬프
    private long lockTimestamp = 0;
    private static final long LOCK_DELAY_MS = 1000; // 1초 딜레이

    // ✅ 추가: 센서 범위를 더 엄격하게
    private static final int LANDSCAPE_MIN = 80;  // 70 → 80
    private static final int LANDSCAPE_MAX = 100; // 110 → 100
    private static final int LANDSCAPE_MIN_REVERSE = 260; // 250 → 260
    private static final int LANDSCAPE_MAX_REVERSE = 280; // 290 → 280

    private static final int PORTRAIT_MIN = 350;  // 340 → 350
    private static final int PORTRAIT_MAX = 10;   // 20 → 10
    private static final int PORTRAIT_MIN_REVERSE = 170; // 160 → 170
    private static final int PORTRAIT_MAX_REVERSE = 190; // 200 → 190

    private ContentObserver rotationObserver;
    private OrientationEventListener orientationListener; // ✅ 센서 리스너

    private View mainView;

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

    private Surface videoSurface;  // ✅ Surface 객체 저장

    // ✅ 비디오 크기 저장용 변수 추가
    private int currentVideoWidth = 0;
    private int currentVideoHeight = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        //getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_video);
        mainView = findViewById(R.id.main);

        imgScreen = findViewById(R.id.img_screen);
        llProgress = findViewById(R.id.llProgress);

        tvTitle =  findViewById(R.id.tv_title);
        imgCctvType =  findViewById(R.id.imgCctvType);
        tvCopyRight =  findViewById(R.id.tvCopyRight);

        textureView = findViewById(R.id.textureView);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                // 세로모드: 상태바/내비게이션바 공간을 모두 확보
                v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            } else {
                // 가로모드: 상태바 숨김, 네비게이션바 공간만 확보
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

        // ✅ 1. 액션바 뷰 참조
        View layoutActionBar = findViewById(R.id.layout_actionbar);
        ivHome = findViewById(R.id.iv_home);
        ivHome.setOnClickListener(v -> onBackPressed());

        // ✅ 5. WindowInsets: 상태바 높이만큼의 절반 정도 아래로 살짝 띄우기
        ViewCompat.setOnApplyWindowInsetsListener(layoutActionBar, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            int offset = (int) (systemBars.top * 1.2f);
            if (offset == 0) {
                offset = dpToPx(this, 10);
            }
            // ConstraintLayout 기준으로 실제 위치 조정
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
                // ✅ 이전 Surface 해제
                if (videoSurface != null) {
                    videoSurface.release();
                }
                // ✅ 새 Surface 생성 및 저장
                videoSurface = new Surface(surface);
                exoPlayer.setVideoSurface(videoSurface);

                // ✅ Surface 준비 시 저장된 비디오 크기로 피팅
                applyVideoFit();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                // ✅ 크기 변경 시 새로운 Surface를 만들 필요 없음
                // ExoPlayer가 자동으로 처리함
                Log.d("ttt", "onSurfaceTextureSizeChanged: ");

                applyVideoFit();
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                // ✅ 중요: ExoPlayer에서 먼저 Surface 제거
                try {
                    if (exoPlayer != null) {
                        exoPlayer.clearVideoSurface();
                    }
                } catch (Exception e) {
                    Log.e("VideoActivity", "Error clearing video surface", e);
                }

                // ✅ 그 다음 Surface 해제
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

        // ✅ TextureView가 이미 사용 가능한 경우 처리
        if (textureView.isAvailable()) {
            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            if (surfaceTexture != null) {
                videoSurface = new Surface(surfaceTexture);
                exoPlayer.setVideoSurface(videoSurface);
            }
        }

        // 타임아웃 시간을 설정 (예: 10초)
        final int TIMEOUT_MS = 10000;

        // 재생 준비 상태를 확인하기 위한 핸들러와 Runnable 설정
        Handler timeoutHandler = new Handler(Looper.getMainLooper());
        Runnable timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (exoPlayer.getPlaybackState() == Player.STATE_BUFFERING) {
                    // 타임아웃 발생 시 필요한 작업 (예: 에러 메시지 표시, 재시도 등)
                    llProgress.setVisibility(View.GONE);
                    Toast.makeText(VideoActivity.this, "Playback timed out", Toast.LENGTH_SHORT).show();
                    // 플레이어를 중지하거나 재시도
                    exoPlayer.stop();
                }
            }
        };

        // 비디오 크기가 변경되면 원본 비율로 표시
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onVideoSizeChanged(VideoSize videoSize) {
                // ✅ 비디오 크기 저장
                currentVideoWidth = videoSize.width;
                currentVideoHeight = videoSize.height;

                // 비디오 크기 결정 후
                fitVideoToView(videoSize);
                // 크기가 조정된 후에 TextureView를 보이도록 함
                textureView.setVisibility(View.VISIBLE);

                llProgress.animate()
                        .alpha(0f)  // 투명도 0으로 설정
                        .setDuration(500)  // 애니메이션 지속 시간 (밀리초 단위)
                        .withEndAction(new Runnable() {  // 애니메이션 종료 시 실행될 작업
                            @Override
                            public void run() {
                                llProgress.setVisibility(View.GONE);  // 애니메이션 종료 후 GONE 설정
                            }
                        });
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) {
                    // 버퍼링 상태에서 타임아웃 설정
                    timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MS);
                } else {
                    // 타임아웃 취소
                    timeoutHandler.removeCallbacks(timeoutRunnable);
                }

                if (state == Player.STATE_READY) {
                    // 플레이어가 준비되면 TextureView를 보이도록 설정
                    textureView.setVisibility(View.VISIBLE);
                } else if (state == Player.STATE_ENDED) {
                    Log.d("ttt", "STATE_ENDED: ");
                } else {
                    // 준비 상태 이전에는 TextureView를 숨김
                    textureView.setVisibility(View.INVISIBLE);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                // 오류 발생 시 실행할 코드
                llProgress.setVisibility(View.GONE);
                Toast.makeText(VideoActivity.this, "cctv connection error", Toast.LENGTH_SHORT).show();
            }

        });

        // ScaleGestureDetector 초기화
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float scaleFactorDelta = detector.getScaleFactor();
                scaleFactor *= scaleFactorDelta;
                scaleFactor = Math.max(0.5f, Math.min(scaleFactor, 3.0f)); // 최소 0.5배, 최대 3배로 제한

                focusX = detector.getFocusX();
                focusY = detector.getFocusY();

                matrix.postScale(scaleFactorDelta, scaleFactorDelta, focusX, focusY);
                textureView.setTransform(matrix);
                return true;
            }
        });

        // GestureDetector 초기화
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                matrix.postTranslate(-distanceX, -distanceY);
                textureView.setTransform(matrix);
                return true;
            }
        });

        // Touch 이벤트 리스너 설정
        textureView.setOnTouchListener((v, event) -> {
            scaleGestureDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);
            return true;
        });

        try {
            //아이콘
            imgCctvType.setImageResource(R.drawable.cctv2634);
            //카메라이름
            tvTitle.setText(cctvName);

            if ("jeju".equals(cctvType)) {
                tvCopyRight.setText(getString(R.string.info_cctv_copyright));
                //tvCopyRight.setText(getString(R.string.info_cctv_copyright_jeju)); 감추기
                startJejuCctvVideo();
            } else {
                tvCopyRight.setText(getString(R.string.info_cctv_copyright));
                startCctvVideo();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // ✅ 저장된 비디오 크기로 피팅 적용하는 헬퍼 메서드
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
        imgScreen.setImageResource(isLandscape
                ? R.drawable.full_screen_off
                : R.drawable.full_screen_on);

        Matrix matrix1 = new Matrix();
        float scaleX, scaleY;
        float cx = viewWidth / 2f;
        float cy = viewHeight / 2f;

        // 비율 계산
        float videoAspect = (float) videoWidth / videoHeight;
        float viewAspect = (float) viewWidth / viewHeight;

        // Step 1: 비율 맞추기
        if (videoAspect > viewAspect) {
            scaleX = 1.0f;
            scaleY = viewAspect / videoAspect;
        } else {
            scaleX = videoAspect / viewAspect;
            scaleY = 1.0f;
        }
        matrix1.setScale(scaleX, scaleY, cx, cy);
        //textureView 에는 이미 비디오가 fit 으로 찌그러진 상태로 표현되어 있음.
        //찌그러진 상태를 원본 비디오 비율로 textureView에 잘리지 않고 꽉차게 매트릭스 변환.

        Point point = DeviceHelper.getDisplaySize(VideoActivity.this);
        int deviceWidth = point.x;
        int deviceHeight = point.y;

        float paddingRatio = ADHelper.getBottomPaddingRatio(VideoActivity.this);
        float padding = deviceHeight * paddingRatio;
        padding = padding * 0.5f;
        float margin = padding * 0.1f;

        RectF srcViewRectF = new RectF(0, 0, deviceWidth, deviceHeight);
        RectF targetViewRectF = new RectF(0, 0, deviceWidth, deviceHeight - padding - 0);

        Matrix matrix2 = new Matrix();
        if (isLandscape) {
            matrix2.setRectToRect(srcViewRectF, targetViewRectF, Matrix.ScaleToFit.CENTER);
        }

        matrix.set(matrix1);
        matrix.postConcat(matrix2);
        // 세로일땐 그냥두고
        // 가로일땐 광고가 있으면 하단 광고영역 제외하고 다시 크기 맞춤

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
                    // uri and play
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

                // ✅ 고정한 지 LOCK_DELAY_MS 이내면 무시
                long currentTime = System.currentTimeMillis();
                if (currentTime - lockTimestamp < LOCK_DELAY_MS) {
                    return;
                }

                // ✅ 더 엄격한 범위로 기기 방향 감지
                boolean deviceIsLandscape =
                        (orientation >= LANDSCAPE_MIN && orientation <= LANDSCAPE_MAX) ||
                                (orientation >= LANDSCAPE_MIN_REVERSE && orientation <= LANDSCAPE_MAX_REVERSE);

                boolean deviceIsPortrait =
                        (orientation >= 0 && orientation <= PORTRAIT_MAX) ||
                                (orientation >= PORTRAIT_MIN && orientation <= 360) ||
                                (orientation >= PORTRAIT_MIN_REVERSE && orientation <= PORTRAIT_MAX_REVERSE);

                // ✅ 가로 고정 상태에서 기기를 가로로 돌리면 센서모드 복귀
                if (isLockedLandscape && deviceIsLandscape) {
                    runOnUiThread(() -> {
                        Log.d("VideoActivity", "Unlocking landscape mode - returning to sensor");
                        isLockedLandscape = false;
                        if (isAutoRotationEnabled()) {
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                        }
                    });
                }
                // ✅ 세로 고정 상태에서 기기를 세로로 세우면 센서모드 복귀
                else if (isLockedPortrait && deviceIsPortrait) {
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

    private void goToLandscape() {
        isLandscape = true;
        isLockedLandscape = true;
        isLockedPortrait = false;

        // ✅ 고정 타임스탬프 기록
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

        // ✅ 고정 타임스탬프 기록
        lockTimestamp = System.currentTimeMillis();

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // ✅ 딜레이 제거 - 즉시 적용
        applyBarsByOrientation(Configuration.ORIENTATION_PORTRAIT);
        updateButtonIcon();

        Log.d("VideoActivity", "Locked to portrait mode");
    }

    // ✅ 추가: 센서 모드로 복귀할 때 호출되는 메서드
    private void returnToSensorMode() {
        if (isAutoRotationEnabled()) {
            // UNSPECIFIED 대신 SENSOR 사용
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        }
    }

    private void setupRotationObserver() {
        rotationObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                // 현재는 역할 없음. 유저 자동회전 설정 변경시 즉시 처리 가능
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

//    private void goToLandscape() {
//        isLandscape = true;
//        isLockedLandscape = true;  // ✅ 가로 고정
//        isLockedPortrait = false;
//        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
//        applyBarsByOrientation(Configuration.ORIENTATION_LANDSCAPE);
//        updateButtonIcon();
//    }
//
//    private void goToPortrait() {
//        isLandscape = false;
//        isLockedLandscape = false;
//        isLockedPortrait = true;  // ✅ 세로 고정
//        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
//
//        new Handler(Looper.getMainLooper()).postDelayed(() -> {
//            applyBarsByOrientation(Configuration.ORIENTATION_PORTRAIT);
//            updateButtonIcon();
//        }, 100);
//    }

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

        // ✅ 고정 모드가 아닐 때만 isLandscape 업데이트
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
            // 가로모드면 세로로 전환
            goToPortrait();
        } else {
            // 세로모드면 액티비티 종료
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (exoPlayer != null) {
            //exoPlayer.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("ttt", "onDestroy");

        // ✅ 올바른 해제 순서: ExoPlayer → Surface
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
            orientationListener.disable(); // ✅ 센서 리스너 해제
        }

    }

    private static int dpToPx(Context c, int dp) {
        return Math.round(dp * c.getResources().getDisplayMetrics().density);
    }

}
