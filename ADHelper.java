package com.boolint.satpic.helper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.Rect;
import android.media.AudioManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowMetrics;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.boolint.satpic.MainActivity;
import com.boolint.satpic.R;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;

import java.util.Arrays;
import java.util.List;

public class ADHelper {

    //static String ADMOB_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"; // test

    static String ADMOB_BANNER_ID = "ca-app-pub-4191984691121942/";
    static String ADMOB_INTERSTITIAL_ID = "ca-app-pub-4191984691121942/";
    static String ADMOB_OPEN_ID = "ca-app-pub-4191984691121942/";

    public static long lastAdShownTime = 0;
    final static long LIMIT_TIME_MIN = 10;

    private static InterstitialAd interstitial = null;

    public static void initMobileAds(Activity act) {
        MobileAds.initialize(act, new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(@NonNull InitializationStatus initializationStatus) {

            }
        });

        List<String> testDeviceIds = Arrays.asList(
                "771E5F90B8D911D7F6778376028F3747",
                "98FABAD660CA5A44758C3C70104E84C6",
                "3636C1F270E4B447DBD9ED18F196A72D", //폴더블
                "E0C58B420D7FCAB7F16A1AC672729AA1");
        RequestConfiguration configuration = new RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build();
        MobileAds.setRequestConfiguration(configuration);
    }

    public static void loadAppOpenAd(final Activity _act) {
        AdRequest request = new AdRequest.Builder().build();
        AppOpenAd.load(
                _act, ADMOB_OPEN_ID, request,
                new AppOpenAd.AppOpenAdLoadCallback() {
                    @Override
                    public void onAdLoaded(AppOpenAd ad) {
                        Log.d("ttt", "onAdLoaded");
                        ad.setImmersiveMode(true);

                        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                            @Override
                            public void onAdDismissedFullScreenContent() {
                                super.onAdDismissedFullScreenContent();
                                Intent intent = new Intent(_act, MainActivity.class);
                                _act.startActivity(intent);
                                _act.finish();
                            }
                        });
                        ad.show(_act);
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError loadAdError) {
                        Log.d("ttt", "onAdFailedToLoad");
                        Log.d("ttt", loadAdError.toString());

                        Intent intent = new Intent(_act, MainActivity.class);
                        _act.startActivity(intent);
                        _act.finish();
                    }
                }
        );
    }

    public static void updateAdVisibilityForDeviceConfiguration(Activity activity) {
        if (activity == null) return;

        FrameLayout adContainerView = activity.findViewById(R.id.fl_banner);
        if (adContainerView == null) return;

        boolean shouldShowAd = shouldShowAd(activity);
        setAdVisibility(adContainerView, shouldShowAd);
    }

    public static void settingAdEx(Activity act) {
        final FrameLayout flBanner = act.findViewById(R.id.fl_banner);
        flBanner.removeAllViews();

        final AdView adView = new AdView(act);
        adView.setAdUnitId(ADMOB_BANNER_ID);
        adView.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                super.onAdLoaded();
                Log.d("ttt", "onAdLoaded - 광고 로드 완료");
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                super.onAdFailedToLoad(loadAdError);
                try {
                    Log.d("ttt", "onAdFailedToLoad: " + String.format("%s", loadAdError.toString()));

                    adView.destroy();
                    adView.setVisibility(View.GONE);
                    flBanner.setVisibility(View.GONE);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        });

        flBanner.addView(adView);

        flBanner.post(() -> {  // 레이아웃 완료 후 폭 계산
            AdSize adSize = getAdSize(act, flBanner);
            adView.setAdSize(adSize);
            AdRequest adRequest = new AdRequest.Builder().build();
            adView.loadAd(adRequest);
        });
    }

    private static AdSize getAdSize(Activity activity, FrameLayout adContainerView) {
        int adWidthPx = adContainerView.getWidth();

        if (adWidthPx <= 0) {
            // 레이아웃 전이면 WindowMetrics 기준으로 계산
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowMetrics metrics = activity.getWindowManager().getCurrentWindowMetrics();
                Rect bounds = metrics.getBounds();
                WindowInsets wi = metrics.getWindowInsets();
                Insets insets = wi.getInsetsIgnoringVisibility(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                );
                adWidthPx = bounds.width() - insets.left - insets.right;
            } else {
                DisplayMetrics dm = new DisplayMetrics();
                activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
                adWidthPx = dm.widthPixels;
            }
        }

        // 패딩, 마진 제거
        adWidthPx -= adContainerView.getPaddingLeft() + adContainerView.getPaddingRight();
        ViewGroup.LayoutParams lp = adContainerView.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
            adWidthPx -= (mlp.leftMargin + mlp.rightMargin);
        }

        if (adWidthPx <= 0) {
            adWidthPx = activity.getResources().getDisplayMetrics().widthPixels;
        }

        float density = activity.getResources().getDisplayMetrics().density;
        int adWidthDp = (int) Math.round(adWidthPx / density);

        Log.d("ttt", "Ad width dp: " + adWidthDp);

        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidthDp);
    }

    public static void loadAdMobInterstitialAd(final Activity _act) {
        AdRequest adRequest = new AdRequest.Builder().build();

        InterstitialAd.load(_act, ADMOB_INTERSTITIAL_ID, adRequest,
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                        interstitial = interstitialAd;
                        interstitial.setImmersiveMode(true);
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        interstitial = null;
                    }
                });
    }

    public static void displayInterstitial(final Activity _act) {
        long now = System.currentTimeMillis();
        if (now - lastAdShownTime < LIMIT_TIME_MIN * 60 * 1000) {
            // 마지막 광고로부터 LIMIT_TIME_MIN 분 이내라면 광고 생략
            return;
        }
        if (interstitial != null) {
            interstitial.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdClicked() {
                    // Called when a click is recorded for an ad.
                    Log.d("ttt", "Ad was clicked.");
                }

                @Override
                public void onAdDismissedFullScreenContent() {
                    // Called when ad is dismissed.
                    // Set the ad reference to null so you don't show the ad a second time.
                    lastAdShownTime = System.currentTimeMillis();
                    Log.d("ttt", "Ad dismissed fullscreen content.");
                    unMuteSound(_act);
                    interstitial = null;
                }

                @Override
                public void onAdFailedToShowFullScreenContent(AdError adError) {
                    // Called when ad fails to show.
                    Log.d("ttt", "Ad failed to show fullscreen content.");
                    interstitial = null;
                }

                @Override
                public void onAdImpression() {
                    // Called when an impression is recorded for an ad.
                    Log.d("ttt", "Ad recorded an impression.");
                }

                @Override
                public void onAdShowedFullScreenContent() {
                    // Called when ad is shown.
                    muteSound(_act);
                    Log.d("ttt", "Ad showed fullscreen content.");
                }
            });
            interstitial.show(_act);
        }
    }

    private static void unMuteSound(Activity _act) {
        AudioManager aManager = (AudioManager) _act.getSystemService(Context.AUDIO_SERVICE);
        aManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
    }

    private static void muteSound(Activity _act) {
        AudioManager aManager = (AudioManager) _act.getSystemService(Context.AUDIO_SERVICE);
        aManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
    }

    public static AdView getRectangleAd(Activity _act){
        AdRequest req = new AdRequest.Builder().build();
        AdView adRectangleView = new AdView(_act);
        adRectangleView.setAdUnitId(ADMOB_BANNER_ID);

        // 현재 화면 방향 확인
        int orientation = _act.getResources().getConfiguration().orientation;

        // 화면 크기 확인 (dp 단위)
        DisplayMetrics displayMetrics = _act.getResources().getDisplayMetrics();
        float widthDp = displayMetrics.widthPixels / displayMetrics.density;
        float heightDp = displayMetrics.heightPixels / displayMetrics.density;
        float smallestWidthDp = Math.min(widthDp, heightDp);

        // 작은 화면(폰, 접힌 폴더블)의 가로모드만 LARGE_BANNER
        if (orientation == Configuration.ORIENTATION_LANDSCAPE && smallestWidthDp < 600) {
            adRectangleView.setAdSize(AdSize.LARGE_BANNER); // 320x100
        } else {
            // 나머지 모든 경우 MEDIUM_RECTANGLE
            adRectangleView.setAdSize(AdSize.MEDIUM_RECTANGLE); // 300x250
        }

        adRectangleView.loadAd(req);
        return adRectangleView;
    }


    // 광고 하단 패딩 비율 상수
    // 상수 작성할때 폴더블(Z6) 디바이스를 기준으로 테스트 함
    private static final float AD_PADDING_TABLET_LANDSCAPE = 0.18f;
    private static final float AD_PADDING_TABLET_PORTRAIT = 0.11f;
    private static final float AD_PADDING_PHONE_LANDSCAPE = 0.0f;
    private static final float AD_PADDING_PHONE_PORTRAIT = 0.05f;

    /**
     * 현재 디바이스 설정에서 광고를 표시해야 하는지 판단
     */
    public static boolean shouldShowAd(Configuration config) {
        boolean isLandscape = DeviceHelper.isLandscapeOrientation(config);
        boolean isTablet = DeviceHelper.isTabletDevice(config);

        // 태블릿이 아닌 기기의 가로 모드에서는 광고를 숨김
        return !(isLandscape && !isTablet);
    }

    public static boolean shouldShowAd(Context context) {
        Configuration config = context.getResources().getConfiguration();
        return shouldShowAd(config);
    }

    /**
     * 광고 컨테이너의 가시성을 설정
     */
    public static void setAdVisibility(View adContainerView, boolean shouldShow) {
        if (adContainerView == null) return;
        int visibility = shouldShow ? View.VISIBLE : View.GONE;
        adContainerView.setVisibility(visibility);
    }

    /**
     * Activity에서 광고 컨테이너 뷰를 가져옴
     */
    public static FrameLayout getAdContainerView(Activity activity) {
        if (activity == null) return null;
        return activity.findViewById(R.id.fl_banner);
    }

    /**
     * 디바이스 설정에 따른 광고 하단 패딩 비율 반환
     */
    public static float getBottomPaddingRatio(Configuration config) {
        boolean isTablet = DeviceHelper.isTabletDevice(config);
        boolean isLandscape = DeviceHelper.isLandscapeOrientation(config);

        if (isTablet) {
            return isLandscape ? AD_PADDING_TABLET_LANDSCAPE : AD_PADDING_TABLET_PORTRAIT;
        } else {
            return isLandscape ? AD_PADDING_PHONE_LANDSCAPE : AD_PADDING_PHONE_PORTRAIT;
        }
    }

    public static float getBottomPaddingRatio(Context context) {
        Configuration config = context.getResources().getConfiguration();
        return getBottomPaddingRatio(config);
    }

    /**
     * 광고 하단 패딩을 픽셀 값으로 계산
     * @param screenHeight 화면 높이 (픽셀)
     */
    public static int calculateBottomPadding(Context context, int screenHeight) {
        float ratio = getBottomPaddingRatio(context);
        return (int) (screenHeight * ratio);
    }

}
