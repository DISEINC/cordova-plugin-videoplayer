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

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaResourceApi;
import org.apache.cordova.PluginResult;
import org.json.JSONException;
import org.json.JSONObject;

public class VideoPlayer extends CordovaPlugin implements OnCompletionListener, OnPreparedListener, OnErrorListener, OnDismissListener {

    protected static final String LOG_TAG = "VideoPlayer";
    protected static final String ASSETS = "/android_asset/";
    private CallbackContext callbackContext = null;
    private Dialog dialog_;
    private VideoView videoView_;
    private MediaPlayer player_;
	private LinearLayout mainLayout_;
	private String currentPath_;
	
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
        if (action.equals("play") || action.equals("prepare")) {
            this.callbackContext = callbackContext;
			
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

            // Don't return any result now
            PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
            pluginResult.setKeepCallback(true);
            callbackContext.sendPluginResult(pluginResult);
            callbackContext = null;

            return true;
        } else if (action.equals("close")) {
            cordova.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    closeVideoDialog();
                }
            });
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
		LinearLayout mainLayout_ = new LinearLayout(cordova.getActivity());
		mainLayout_.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		mainLayout_.setOrientation(LinearLayout.VERTICAL);
		mainLayout_.setHorizontalGravity(Gravity.CENTER_HORIZONTAL);
		mainLayout_.setVerticalGravity(Gravity.CENTER_VERTICAL);

		videoView_ = new VideoView(cordova.getActivity());
		videoView_.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		videoView_.setOnPreparedListener(this);
		videoView_.setOnCompletionListener(this);
		videoView_.setOnErrorListener(this);
		mainLayout_.addView(videoView_);
		
		dialog_ = new Dialog(cordova.getActivity(), android.R.style.Theme_NoTitleBar);
		dialog_.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog_.setCancelable(true);
		dialog_.setOnDismissListener(this);
		
		WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
		lp.copyFrom(dialog_.getWindow().getAttributes());
		lp.width = WindowManager.LayoutParams.MATCH_PARENT;
		lp.height = WindowManager.LayoutParams.MATCH_PARENT;
		dialog_.setContentView(mainLayout_);
		
		// Hide system UI
		int version = android.os.Build.VERSION.SDK_INT; // Device OS version
		int uiOptions = 0;
		if (version >= 14) { // (need API v.14)
			uiOptions |= 0x00000002; // - View.SYSTEM_UI_FLAG_HIDE_NAVIGATION - hides nav bar
		}
		if (version >= 16) { // (need API v.16)
			uiOptions |= 0x00000004 // - View.SYSTEM_UI_FLAG_FULLSCREEN - hides status bar
					  |  0x00000100 // - View.SYSTEM_UI_FLAG_LAYOUT_STABLE
					  |  0x00000200 // - View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
					  |  0x00000400 // - View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
			;
		}
		if (version >= 19) { // (need API v.19)
			uiOptions |= 0x00000800 // - View.SYSTEM_UI_FLAG_IMMERSIVE
					  |  0x00001000 // - View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
			;
		}
		View decor = dialog_.getWindow().getDecorView();
		decor.setSystemUiVisibility(uiOptions);
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
			if (path.startsWith(ASSETS)) {
				// TODO: This is not verified to work!
				path = path.substring(15);
			}
			
			if ((prepareOnly && !videoView_.isPlaying()) || !prepareOnly) {
				if (currentPath_ != path) {
					// Not the same video as before, set this new path
					videoView_.setVideoPath(path);
					currentPath_ = path;
				} else {
					// We want to play the same video as before, 
					// just reset the position o the start
					videoView_.seekTo(0);
				}
			}

			if (!prepareOnly) {
				// Start the video and show the window
				dialog_.show();
				videoView_.start();
			}
		} catch (Exception e) {
			PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getLocalizedMessage());
			result.setKeepCallback(false); // release status callback in JS side
			callbackContext.sendPluginResult(result);
			callbackContext = null;
			return;
		}
    }
	

    /**
     * Close the dialog and stop playing the video
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	protected void closeVideoDialog() {
		try {
			if (dialog_ != null) {
				if(videoView_.isPlaying()) {
					videoView_.stopPlayback();
				}
				dialog_.dismiss();
			}

			if (callbackContext != null) {
				PluginResult result = new PluginResult(PluginResult.Status.OK);
				result.setKeepCallback(false); // release status callback in JS side
				callbackContext.sendPluginResult(result);
				callbackContext = null;
			}
		} catch (Exception e) {
			PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getLocalizedMessage());
			result.setKeepCallback(false); // release status callback in JS side
			callbackContext.sendPluginResult(result);
			callbackContext = null;
			return;
		}
	}

    /**
     * Callback on error
     * http://developer.android.com/reference/android/media/MediaPlayer.OnErrorListener.html
     */
    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.e(LOG_TAG, "MediaPlayer.onError(" + what + ", " + extra + ")");
        dialog_.dismiss();
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
        dialog_.dismiss();
    }

    /**
     * Callback when the dialog is dismissed
     * http://developer.android.com/reference/android/content/DialogInterface.OnDismissListener.html
     */
    @Override
    public void onDismiss(DialogInterface dialog) {
        Log.d(LOG_TAG, "Dialog dismissed");
        if (callbackContext != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK);
            result.setKeepCallback(false); // release status callback in JS side
            callbackContext.sendPluginResult(result);
            callbackContext = null;
        }
    }
}
