package com.moust.cordova.videoplayer;

import android.annotation.TargetApi;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.MediaPlayer.OnVideoSizeChangedListener;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.LinearLayout;
import android.widget.VideoView;
import android.view.View;
import android.view.SurfaceView;
import android.view.MotionEvent;
import android.widget.RelativeLayout;
import android.widget.FrameLayout;
import android.os.SystemClock;
import android.graphics.PixelFormat;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaResourceApi;
import org.apache.cordova.PluginResult;
import org.json.JSONException;
import org.json.JSONObject;

public class VideoPlayer extends CordovaPlugin 
	implements OnCompletionListener, 
	           OnPreparedListener, 
			   OnErrorListener,
			   OnVideoSizeChangedListener,
			   SurfaceHolder.Callback {

    protected static final String LOG_TAG = "VideoPlayer ------------------>";
    protected static final String ASSETS = "/android_asset/";
	private static boolean executeVideoTouchFix = ( android.os.Build.DEVICE == "rk3288" && android.os.Build.MODEL == "UIM200B-B21-HW01-4.4" );
    private CallbackContext callbackContext = null;
    private MediaPlayer mediaPlayer_;
	private FrameLayout mainLayout_;
	private String currentPath_;
	private SurfaceView videoSurface_;
	private RelativeLayout videoFrameLayout_;
	private int origWidth, origHeight;

	
    /**
     * Initializes the plugin
     *
     * @param cordovaInterface    An interface to Cordova
     * @param webView             The cordova web view
     */
	@Override
	public void initialize(CordovaInterface cordovaInterface, CordovaWebView webView) {
		super.initialize(cordovaInterface, webView);
		
		cordova.getActivity().runOnUiThread(new Runnable() {
			public void run() {
				initVideoDialog();
			}
		});
	}

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action        The action to execute.
     * @param args          JSONArray of arguments for the plugin.
     * @param callbackId    The callback id used when calling back into JavaScript.
     * @return              A PluginResult object with a status and message.
     */
    public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) throws JSONException {
		this.callbackContext = callbackContext;
        if (action.equals("play") || action.equals("prepare")) {
			
            CordovaResourceApi resourceApi = webView.getResourceApi();
            String target = args.getString(0);
            final JSONObject options = args.getJSONObject(1);
			final boolean prepare = action.equals("prepare");

            String fileUriStr;
            try {
                Uri targetUri = resourceApi.remapUri(Uri.parse(target));
                fileUriStr = targetUri.toString();
            } catch (IllegalArgumentException e) {
                fileUriStr = target;
            }

            Log.v(LOG_TAG, fileUriStr);

            final String path = stripFileProtocol(fileUriStr);

            // Create dialog in new thread
            cordova.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    openVideoDialog(path, options, prepare);
                }
            });

            return true;
        } else if (action.equals("close")) {
            cordova.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    closeVideoDialog();
                }
            });
			sendCallback(new PluginResult(PluginResult.Status.NO_RESULT), true);
            return true;
        }
        return false;
    }

    /**
     * Removes the "file://" prefix from the given URI string, if applicable.
     * If the given URI string doesn't have a "file://" prefix, it is returned unchanged.
     *
     * @param uriString the URI string to operate on
     * @return a path without the "file://" prefix
     */
    public static String stripFileProtocol(String uriString) {
        if (uriString.startsWith("file://")) {
            return Uri.parse(uriString).getPath();
        }
        return uriString;
    }
	
    /**
     * Initializes the video dialog and sets up all parameters in order
     * to get things to work.
     */
	protected void initVideoDialog() {
		mainLayout_ = (FrameLayout) webView.getView().getParent();
		
        videoFrameLayout_ = new RelativeLayout(cordova.getActivity());
		videoFrameLayout_.setGravity(Gravity.CENTER);
		
		videoSurface_ = new SurfaceView(cordova.getActivity());
		
		videoSurface_.post(new Runnable()
		{
			public void run()
			{
				origWidth = videoSurface_.getWidth();
				origHeight = videoSurface_.getHeight();
				videoSurface_.getLayoutParams().width = origWidth;
				videoSurface_.getLayoutParams().height = origHeight;
			}
		});
		
		SurfaceHolder videoHolder = videoSurface_.getHolder();
        videoHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        videoHolder.addCallback(this);
		
		mainLayout_.addView(videoFrameLayout_);
        videoFrameLayout_.addView(videoSurface_);
		videoFrameLayout_.setVisibility(View.INVISIBLE);
		Log.i(LOG_TAG, "Init Done!");
	}

    /**
     * Open the dialog and start playing the video
     * @param path Path to the video file to play
     * @param options Custom options
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    protected void openVideoDialog(String path, JSONObject options, boolean prepareOnly) {
		try {
			Log.i(LOG_TAG, "openVideoDialog(): "+path);
			if (prepareOnly) {
				throw new Exception("Prepare is currently not supported");
			}
			
			if (path.startsWith(ASSETS)) {
				// TODO: This is not verified to work!
				path = path.substring(15);
			}
			
					Log.d("origsize", "" + this.origWidth + "," + this.origHeight);
			
			// Reset surface size.
			if (videoSurface_ != null)
			{
				videoSurface_.getLayoutParams().width = this.origWidth;
				videoSurface_.getLayoutParams().height = this.origHeight;
			}

			freeMediaPlayer();
			
			mediaPlayer_ = new MediaPlayer();
			mediaPlayer_.setOnErrorListener(this);
			mediaPlayer_.setOnPreparedListener(this);
			mediaPlayer_.setOnCompletionListener(this);
			mediaPlayer_.setOnVideoSizeChangedListener(this);
			FileInputStream fis = new FileInputStream(path);
			FileDescriptor fd = fis.getFD();
			if (fd != null && fd.valid()) {
				mediaPlayer_.setDataSource(fd);
				mediaPlayer_.setDisplay(videoSurface_.getHolder());
				mediaPlayer_.prepare();
				mediaPlayer_.start();
				showVideoSurface();
				fis.close();
			} else {
				fis.close();
				throw new Exception("Failed to open file");
			}
		} catch (Exception e) {
			sendCallback(new PluginResult(PluginResult.Status.ERROR, e.getLocalizedMessage()), false);
		}
    }
	
    /**
     * Close the dialog and stop playing the video
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	protected void closeVideoDialog() {
		try {
			hideVideoSurface();
			freeMediaPlayer();
			sendCallback(new PluginResult(PluginResult.Status.OK), false);
		} catch (Exception e) {
			sendCallback(new PluginResult(PluginResult.Status.ERROR, e.getLocalizedMessage()), false);
		}
	}
	
	protected void sendCallback(PluginResult result, boolean keepCallback) {
		if (callbackContext != null) {
			Log.d(LOG_TAG, "sendCallback");
			result.setKeepCallback(keepCallback);
			callbackContext.sendPluginResult(result);
			if (!keepCallback) {
				callbackContext = null;
			}
		}
	}

	protected void freeMediaPlayer() {	
		if (mediaPlayer_ != null) {
			Log.d(LOG_TAG, "freeMediaPlayer");
			if (mediaPlayer_.isPlaying()) {
				mediaPlayer_.stop();
			}
			mediaPlayer_.setOnErrorListener(null);
			mediaPlayer_.setOnPreparedListener(null);
			mediaPlayer_.setOnCompletionListener(null);
			mediaPlayer_.setOnVideoSizeChangedListener(null);
			mediaPlayer_.release();
			mediaPlayer_ = null;
		}
	}
	
	@Override
	public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
		Log.i(LOG_TAG, "onVideoSizeChanged " + String.valueOf(width) + " " + String.valueOf(height));
		setFitToFillAspectRatio(mp, width, height);
	}
	
	
	private void setFitToFillAspectRatio(MediaPlayer mp, int videoWidth, int videoHeight)
	{
		if(mp != null)
		{       
			Integer screenWidth = videoSurface_.getLayoutParams().width;//videoSurface_.getWidth();
			Integer screenHeight = videoSurface_.getLayoutParams().height;//videoSurface_.getHeight();	
			android.view.ViewGroup.LayoutParams videoParams = videoSurface_.getLayoutParams();
			
			if (videoWidth > videoHeight)
			{
				videoParams.width = screenWidth;
				videoParams.height = screenWidth * videoHeight / videoWidth;
				
			}
			else
			{
				videoParams.width = screenHeight * videoWidth / videoHeight;
				videoParams.height = screenHeight;
			}
			
			Log.i(LOG_TAG, "onVideoSizeChanged new size " + String.valueOf(videoParams.width) + "x" + String.valueOf(videoParams.height));
			
			videoSurface_.setLayoutParams(videoParams);
		}
	}	  


    /**
     * Callback on error
     * http://developer.android.com/reference/android/media/MediaPlayer.OnErrorListener.html
     */
    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
		String msg = "MediaPlayer.onError(" + what + ", " + extra + ")";
        Log.e(LOG_TAG, msg);
		sendCallback(new PluginResult(PluginResult.Status.ERROR, msg), false);
        return false;
    }

    /**
     * Callback when the video is prepared
     * http://developer.android.com/reference/android/media/MediaPlayer.OnPreparedListener.html
     */
    @Override
    public void onPrepared(MediaPlayer mp) {
    }

    /**
     * Callback when the video is done
     * http://developer.android.com/reference/android/media/MediaPlayer.OnCompletionListener.html
     */
    @Override
    public void onCompletion(MediaPlayer mp) {
        Log.d(LOG_TAG, "MediaPlayer completed");
		hideVideoSurface();
        sendCallback(new PluginResult(PluginResult.Status.OK), false);
    }

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		Log.d(LOG_TAG, "surfaceCreated called");
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		Log.d(LOG_TAG, "surfaceChanged called");
		if (videoSurface_.getHolder() == holder && mediaPlayer_ != null) {
			mediaPlayer_.setDisplay(videoSurface_.getHolder());
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		Log.d(LOG_TAG, "surfaceDestroyed called");
	}

	public SurfaceView getVideoSurface() {
		return videoSurface_;
	}
	
	public void showVideoSurface() {
		if (mediaPlayer_!=null && videoSurface_.getHolder().getSurface().isValid()) {
			mediaPlayer_.setDisplay(videoSurface_.getHolder());
		}

        cordova.getActivity().runOnUiThread(new Runnable() {
			public void run() {
				videoFrameLayout_.setVisibility(View.VISIBLE);
			}
		});
		}
	
	public void hideVideoSurface() {
        cordova.getActivity().runOnUiThread(new Runnable() {
			public void run() {
				videoFrameLayout_.setVisibility(View.INVISIBLE);
				videoFrameLayout_.setVisibility(View.GONE);
			}
		});
	}
}
