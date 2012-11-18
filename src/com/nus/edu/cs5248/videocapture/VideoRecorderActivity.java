package com.nus.edu.cs5248.videocapture;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import com.nus.edu.cs5248.pipeline.Storage;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

public class VideoRecorderActivity extends Activity {
	private static final int MEDIA_TYPE_IMAGE = 1;
	private static final int MEDIA_TYPE_VIDEO = 2;
	private static final String APP_NAME = "CS5248";

	private Camera myCamera;
	private SurfaceView surfaceView;
	private MediaRecorder mediaRecorder;
	
	boolean recording;
	private TextView statusView;
	private Button startButton;
	private Timer	recordingTimer;
	private long	recordingLength;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, 
        		WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

		recording = false;
		setContentView(R.layout.activity_main);
		statusView = (TextView) findViewById(R.id.textViewStatus);

		// Get Camera for preview
		myCamera = getCameraInstance();
		if (myCamera == null) {
			statusView.setText("Error getting Camera");
		}

		surfaceView = new CameraSurfaceView(this, myCamera);
		surfaceView.setKeepScreenOn(true);
		FrameLayout frameLayout = (FrameLayout) findViewById(R.id.videoView);
		frameLayout.addView(surfaceView);

		startButton = (Button) findViewById(R.id.buttonStrat);
	}

	@Override
	protected void onPause() {
		super.onPause();
		
		if(recording){
			stopRecording();
		}else{
			statusView.setText("OnPause");
			releaseMediaRecorder(); // if you are using MediaRecorder, release it
			 // release the camera immediately on pause event
			startButton.setText("Record");
		}
		releaseCamera();
	}
	
	@Override
	protected void onResume(){
		super.onResume();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	public void startButtonClicked(View view) {
		if (!recording) {
			releaseCamera();

			if (!prepareMediaRecorder()) {
				Log.d(APP_NAME, "fail in prepareMediaRecorder()");
				finish();
			}

			mediaRecorder.start();
			startRecordingTimer();
			recording = true;
			statusView.setText("Recording video...");
			startButton.setText("Stop");
		} else {
			stopRecording();
		}
	}
	
	public void PreViewButtonClicked(View view){
		 Intent i = new Intent(VideoRecorderActivity.this, VideoPreview.class);
         startActivity(i);
	}
	
	private void stopRecording(){
		if (recording) {
			mediaRecorder.stop(); // stop the recording
			releaseMediaRecorder(); // release the MediaRecorder object
			
			if (this.recordingTimer != null) {
				this.recordingTimer.cancel();
				this.recordingTimer = null;
			}
			
			recording = false;

			statusView.setText("Recording stoped...");
			startButton.setText("Record");
		}
	}

	private Camera getCameraInstance() {
		Camera c = null;
		try {
			c = Camera.open(); // attempt to get a Camera instance
		} catch (Exception e) {
			System.out.println(e);
			// Camera is not available (in use or does not exist)
		}
		return c; // returns null if camera is unavailable
	}

	private boolean prepareMediaRecorder() {
		myCamera = getCameraInstance();
		mediaRecorder = new MediaRecorder();

		myCamera.unlock();
		mediaRecorder.setCamera(myCamera);

		mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
		mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
		mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));
		mediaRecorder.setOutputFile(getOutputMediaFile(MEDIA_TYPE_VIDEO).toString());
		mediaRecorder.setPreviewDisplay(surfaceView.getHolder().getSurface());

		try {
			mediaRecorder.prepare();
		} catch (IllegalStateException e) {
			releaseMediaRecorder();
			Log.d(APP_NAME, e.getMessage());
			return false;
		} catch (IOException e) {
			releaseMediaRecorder();
			Log.d(APP_NAME, e.getMessage());
			return false;
		}
		return true;
	}

	private void releaseMediaRecorder() {
		if (mediaRecorder != null) {
			mediaRecorder.reset(); // clear recorder configuration
			mediaRecorder.release(); // release the recorder object
			mediaRecorder = null;
			myCamera.lock(); // lock camera for later use
		}
	}

	private void releaseCamera() {

		if (myCamera != null) {
			myCamera.release(); // release the camera for other applications
			myCamera = null;
		}
	}

	private static File getOutputMediaFile(int type) {
		// To be safe, you should check that the SDCard is mounted
		// using Environment.getExternalStorageState() before doing this.

		File mediaStorageDir = Storage.getMediaFolder(true);

		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss")
				.format(new Date());
		File mediaFile;
		if (type == MEDIA_TYPE_IMAGE) {
			mediaFile = new File(mediaStorageDir.getPath() + File.separator
					+ "IMG_" + timeStamp + ".jpg");
		} else if (type == MEDIA_TYPE_VIDEO) {
			mediaFile = new File(mediaStorageDir.getPath() + File.separator
					+ APP_NAME + "_" + timeStamp + ".mp4");
		} else {
			return null;
		}

		return mediaFile;
	}
	
	private void startRecordingTimer() {
		if (this.recordingTimer != null)
			return;
		
		this.recordingLength = 0;
		this.recordingTimer = new Timer(true);
		this.recordingTimer.schedule(new TimerTask() {

			@Override
			public void run() {
				onRecordingTimerTick();
			}
			
		}, 0, 1000);
	}
	
	private void onRecordingTimerTick() {
		final long h = TimeUnit.SECONDS.toHours(this.recordingLength);
		final long m = TimeUnit.SECONDS.toMinutes(this.recordingLength) - (TimeUnit.SECONDS.toHours(this.recordingLength)* 60);
		final long s = TimeUnit.SECONDS.toSeconds(this.recordingLength) - (TimeUnit.SECONDS.toMinutes(this.recordingLength) *60);
		++this.recordingLength;
		
		runOnUiThread(new Runnable() {

			public void run() {
				updateRecordingLength(h, m, s);
			}
			
		});
	}
	
	//UI THREAD ONLY
	private void updateRecordingLength(final long h, final long m, final long s) {
		if (h > 0) {
			this.statusView.setText(String.format("Recording %02d:%02d:%02d", h, m, s));
		} else {
			this.statusView.setText(String.format("Recording %02d:%02d", m, s));
		}
	}
}
