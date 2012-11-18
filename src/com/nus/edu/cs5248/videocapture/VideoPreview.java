package com.nus.edu.cs5248.videocapture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import com.nus.edu.cs5248.pipeline.DashResult;
import com.nus.edu.cs5248.pipeline.DashServer;
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
	private TextView uploadFilenameTextView;
	private ProgressBar uploadProgressBar;
	private ProgressBar indeterminateProgressBar;

	private static final String TAG = "VideoPreview";
	private String[] fileList;
	private String currentSelectedFileName;
	private String currentSelectedFilePath;
	
	private int segmentCount = 0;

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
				
				initProgressViews();
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
					segmentCount = 0;
				}
				else { //Already segmented
					String[] segments = Storage.getMP4FileList(segmentsFolder);
					
					if (segments != null && segments.length > 0) { //Not fully uploaded
						Arrays.sort(segments);
						segmentCount = getIdOfSegmentFile(segments[segments.length - 1]) + 1;
						
						Log.i(TAG, "Selected video partially uploaded.");
						uploadStatusTextView.setText("Partially uploaded");
						segmentUploadButton.setVisibility(View.VISIBLE);
						segmentUploadButton.setEnabled(true);
					} 
					else { //Uploaded
						segmentCount = 0;
						
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
		this.indeterminateProgressBar		= (ProgressBar) findViewById(R.id.indeterminate_progress);
		this.uploadFilenameTextView			= (TextView)	findViewById(R.id.upload_filename_text);
	}
	
	private void initProgressViews() {
		this.segmentationProgressBar.setMax(100);
		this.segmentationProgressBar.setProgress(0);
		this.uploadProgressBar.setMax(100);
		this.uploadProgressBar.setProgress(0);
		
		this.segmentationProgressTextView.setVisibility(View.INVISIBLE);
		this.segmentationProgressBar.setVisibility(View.INVISIBLE);
		this.uploadProgressTextView.setVisibility(View.INVISIBLE);
		this.uploadProgressBar.setVisibility(View.INVISIBLE);
		this.indeterminateProgressBar.setVisibility(View.INVISIBLE);
	}

	public void ButtonSpiltUploadClicked(View view) {
		this.segmentUploadButton.setEnabled(false);
		
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
				segmentCount = Math.max(segmentCount, getIdOfSegmentFile(segmentFilePath) + 1);
				
				Log.i("VideoPreview::onSegmentCreated", "Segment created: "
						+ segmentFilePath + (isFinalSegment ? " (final)" : ""));
				
				statusTextView.setText("Segment created: " + segmentFilePath
						+ (isFinalSegment ? " (final)" : ""));
				
				segmentationProgressTextView.setVisibility(View.VISIBLE);
				
				if (!isFinalSegment) {
					segmentationProgressBar.setVisibility(View.VISIBLE);
					indeterminateProgressBar.setVisibility(View.VISIBLE);
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

			public void createVideoStreamletDidFinish(int result, String streamletFilename, 
					boolean isFinalStreamlet, long totalTime, long encodeTime) {

				Log.i("VideoPreview::createVideoStreamletDidFinish",
						"Create streamlet, result=" + result + 
						(isFinalStreamlet ? "(final)" : ""));
				
				statusTextView.setText("Create streamlet, result=" + result
						+ (isFinalStreamlet ? "(final)" : "")
						+ " total time=" + totalTime + "ms"
						+ " encode time=" + encodeTime + "ms");
				
				if (result == DashResult.OK) {
					int streamletId = getIdOfSegmentFile(streamletFilename);
					int progress = (streamletId + 1) * 100 / segmentCount;
					
					if (progress > 100) {
						progress = 100;
					}
					
					uploadProgressTextView.setVisibility(View.VISIBLE);
					
					if (!isFinalStreamlet) {
						uploadProgressBar.setVisibility(View.VISIBLE);
						indeterminateProgressBar.setVisibility(View.VISIBLE);
						uploadProgressBar.setProgress(progress);
						uploadProgressTextView.setText(String.format("Uploading (%d%%)...", progress));
						uploadStatusTextView.setText("Partially uploaded");
					}
					else {
						uploadFilenameTextView.setVisibility(View.INVISIBLE);
						uploadProgressBar.setVisibility(View.INVISIBLE);
						indeterminateProgressBar.setVisibility(View.INVISIBLE);
						segmentUploadButton.setVisibility(View.INVISIBLE);
						uploadProgressTextView.setText("Upload finished.");
						uploadStatusTextView.setText("Uploaded");
					}
				}
				else {
					uploadFilenameTextView.setVisibility(View.INVISIBLE);
					uploadProgressBar.setVisibility(View.INVISIBLE);
					indeterminateProgressBar.setVisibility(View.INVISIBLE);
					uploadProgressTextView.setText("Upload failed (" + result + ")");
					segmentUploadButton.setEnabled(true);
				}
			}

			public void createVideoStreamletWillStart(String streamletFilename) {
				int streamletId = getIdOfSegmentFile(streamletFilename);
				int progress = streamletId * 100 / segmentCount;
				
				uploadProgressTextView.setVisibility(View.VISIBLE);
				uploadProgressBar.setVisibility(View.VISIBLE);
				indeterminateProgressBar.setVisibility(View.VISIBLE);
				uploadFilenameTextView.setVisibility(View.VISIBLE);
				uploadProgressTextView.setText(String.format("Uploading (%d%%)...", progress));
				uploadProgressBar.setProgress(progress);
				uploadFilenameTextView.setText(streamletFilename);
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
	
	protected static int getIdOfSegmentFile(String filename) {
		int startPos = filename.lastIndexOf('_') + 1;
		int endPos = filename.lastIndexOf('.');
		String indexString = filename.substring(startPos, endPos);
		
		int id = 0;
		try {
			id = Integer.parseInt(indexString);
		} catch (NumberFormatException e) {
			Log.e(TAG, "Failed to parse integer: " + id);
		}
		
		return id;
	}

}
