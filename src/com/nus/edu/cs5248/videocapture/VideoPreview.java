package com.nus.edu.cs5248.videocapture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.nus.edu.cs5248.pipeline.SegmenterCallback;
import com.nus.edu.cs5248.pipeline.Storage;
import com.nus.edu.cs5248.pipeline.UploadPipeline;
import com.nus.edu.cs5248.pipeline.VideoUploaderCallback;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class VideoPreview extends Activity {
	private ListView listView;
	private TextView statusTextView;
	private TextView selectedFileTextView;
	private TextView sizeTextView;
	private TextView dateModifiedTextView;
	private TextView uploadStatusTextView;
	private Button	 segmentUploadButton;
	
	private TextView segmentationProgressTextView;
	private ProgressBar segmentationProgressBar;
	private TextView uploadProgressTextView;
	private ProgressBar uploadProgressBar;

	private static final String TAG = "VideoPreview";
	private String[] fileList;
	private String currentSelectedFileName;
	private String currentSelectedFilePath;

	public VideoPreview() {
		// TODO Auto-generated constructor stub
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.video_preview);
		this.setupOutlets();
		
		this.initProgressViews();

		if (listView.getAdapter() == null) {
			// Assign adapter to ListView
			fillListView();
		}
		listView.setOnItemClickListener(new OnItemClickListener() {

			public void onItemClick(AdapterView adapter, View v, int position,
					long id) {
				currentSelectedFileName = fileList[position];
				selectedFileTextView.setText(currentSelectedFileName);
				
				File videoFile = new File(Storage.getMediaFolder(true), currentSelectedFileName);
				currentSelectedFilePath = videoFile.getAbsolutePath();
				
				sizeTextView.setText(VideoPreview.humanReadableByteCount(videoFile.length(), true));
				dateModifiedTextView.setText(VideoPreview.formatTimestamp(videoFile.lastModified()));
				
				File segmentsFolder = Storage.getSegmentFolder(videoFile, false);
				if (!segmentsFolder.exists()) { //Not segmented and not uploaded
					Log.i(TAG, "Selected video has not been segmented.");
					uploadStatusTextView.setText("Not uploaded");
					segmentUploadButton.setVisibility(View.VISIBLE);
				}
				else { //Already segmented
					String[] segments = Storage.getMP4FileList(segmentsFolder);
					if (segments != null && segments.length > 0) { //Not fully uploaded
						Log.i(TAG, "Selected video partially uploaded.");
						uploadStatusTextView.setText("Partially uploaded");
						segmentUploadButton.setVisibility(View.VISIBLE);
					} 
					else { //Uploaded
						Log.i(TAG, "Selected video has been fully uploaded.");
						uploadStatusTextView.setText("Uploaded");
						segmentUploadButton.setVisibility(View.INVISIBLE);
					}
				}
			}
		});
	}
	
	private void setupOutlets() {
		this.listView 				= (ListView) findViewById(R.id.listViewVideos);
		this.statusTextView 		= (TextView) findViewById(R.id.textViewSummary);
		this.selectedFileTextView 	= (TextView) findViewById(R.id.selectedFileText);
		this.sizeTextView 			= (TextView) findViewById(R.id.size_text);
		this.dateModifiedTextView 	= (TextView) findViewById(R.id.date_modified_text);
		this.uploadStatusTextView 	= (TextView) findViewById(R.id.upload_status_text);
		this.segmentUploadButton	= (Button) findViewById(R.id.buttonSpiltUpload);
		
		this.segmentationProgressTextView 	= (TextView) 	findViewById(R.id.segmentation_progress_text);
		this.segmentationProgressBar 		= (ProgressBar) findViewById(R.id.segmentation_progress_bar);
		this.uploadProgressTextView 		= (TextView) 	findViewById(R.id.upload_progress_text);
		this.uploadProgressBar 				= (ProgressBar) findViewById(R.id.upload_progress_bar);
	}
	
	private void initProgressViews() {
		this.segmentationProgressBar.setMax(100);
		this.uploadProgressBar.setMax(100);
		
		this.segmentationProgressTextView.setVisibility(View.INVISIBLE);
		this.segmentationProgressBar.setVisibility(View.INVISIBLE);
		this.uploadProgressTextView.setVisibility(View.INVISIBLE);
		this.uploadProgressBar.setVisibility(View.INVISIBLE);
	}

	public void ButtonSpiltUploadClicked(View view) {
		String videoTitle = "";

		int start = currentSelectedFilePath.lastIndexOf('/') + 1;
		int end = currentSelectedFilePath.indexOf(".mp4");
		videoTitle = currentSelectedFilePath.substring(start, end);

		Log.i("VideoPreview::ButtonSplitUploadClicked", "file path=["
				+ currentSelectedFilePath + "]" + " video title=[" + videoTitle + "]");

		UploadPipeline.INSTANCE.segmentAndUpload(videoTitle, currentSelectedFilePath,

		new SegmenterCallback() {

			public void onSegmentCreated(String segmentFilePath,
					boolean isFinalSegment, int progress) {
				Log.i("VideoPreview::onSegmentCreated", "Segment created: "
						+ segmentFilePath + (isFinalSegment ? " (final)" : ""));
				
				statusTextView.setText("Segment created: " + segmentFilePath
						+ (isFinalSegment ? " (final)" : ""));
				
				segmentationProgressTextView.setVisibility(View.VISIBLE);
				
				if (!isFinalSegment) {
					segmentationProgressBar.setVisibility(View.VISIBLE);
					segmentationProgressBar.setProgress(progress);
					segmentationProgressTextView.setText(String.format("Segmenting (%d%%)...", progress));
				}
				else {
					segmentationProgressBar.setVisibility(View.INVISIBLE);
					segmentationProgressTextView.setText("Segmentation finished.");
				}
			}

		},

		new VideoUploaderCallback() {

			public void createVideoDidFinish(int result, int videoId) {
			}

			public void createVideoStreamletDidFinish(int result, int videoId,
					int streamletId, boolean isFinalStreamlet, long totalTime, long encodeTime) {

				Log.i("VideoPreview::createVideoStreamletDidFinish",
						"Create streamlet, result=" + result + 
						" video id=" + videoId +
						" streamlet id=" + streamletId + 
						(isFinalStreamlet ? "(final)" : ""));
				
				statusTextView.setText("Create streamlet, result=" + result
						+ " video id=" + videoId + " streamlet id="
						+ streamletId + (isFinalStreamlet ? "(final)" : "")
						+ " total time=" + totalTime + "ms"
						+ " encode time=" + encodeTime + "ms");
			}

		});
	}

	public void GoToRecordingButtonClicked(View view) {
		Intent i = new Intent(VideoPreview.this, VideoRecorderActivity.class);
		startActivity(i);
	}

	private void fillListView() {
		fileList = getVideoFileList();
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_2, android.R.id.text1,
				fileList);
		listView.setAdapter(adapter);
	}

	private String[] getVideoFileList() {
		File mediaStorageDir = Storage.getMediaFolder(true);
		return Storage.getMP4FileList(mediaStorageDir);
	}
	
	public static String humanReadableByteCount(long bytes, boolean si) {
	    int unit = si ? 1000 : 1024;
	    if (bytes < unit) return bytes + " B";
	    int exp = (int) (Math.log(bytes) / Math.log(unit));
	    String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
	    return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
	}
	
	public static String formatTimestamp(long timestamp) {
		SimpleDateFormat dateFormat = new SimpleDateFormat("M/d/yyyy h:mm a");
		return dateFormat.format(new Date(timestamp));
	}

}
