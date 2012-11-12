package com.nus.edu.cs5248.videocapture;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import com.googlecode.mp4parser.authoring.builder.Mp4Builder;
import com.nus.edu.cs5248.pipeline.SegmenterCallback;
import com.nus.edu.cs5248.pipeline.UploadPipeline;
import com.nus.edu.cs5248.pipeline.VideoUploaderCallback;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class VideoPreview extends Activity {
	private ListView listView;
	private TextView statusTextView;
	private TextView selectedFileTextView;
	private TextView sizeTextView;
	private TextView dateModifiedTextView;

	private static final String APP_NAME = "CS5248";
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
		setContentView(R.layout.video_preview);
		listView = (ListView) findViewById(R.id.listViewVideos);
		statusTextView = (TextView) findViewById(R.id.textViewSummary);
		selectedFileTextView = (TextView) findViewById(R.id.selectedFileText);
		sizeTextView = (TextView) findViewById(R.id.size_text);
		dateModifiedTextView = (TextView) findViewById(R.id.date_modified_text);

		if (listView.getAdapter() == null) {
			// Assign adapter to ListView
			fillListView();
		}
		listView.setOnItemClickListener(new OnItemClickListener() {

			public void onItemClick(AdapterView adapter, View v, int position,
					long id) {
				currentSelectedFileName = fileList[position];
				selectedFileTextView.setText(currentSelectedFileName);
				
				File file1 = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), APP_NAME);
				File file2 = new File(file1, currentSelectedFileName);
				currentSelectedFilePath = file2.getAbsolutePath();
				
				sizeTextView.setText(VideoPreview.humanReadableByteCount(file2.length(), true));
				dateModifiedTextView.setText(VideoPreview.formatTimestamp(file2.lastModified()));
			}
		});
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
					boolean isFinalSegment) {
				Log.i("VideoPreview::onSegmentCreated", "Segment created: "
						+ segmentFilePath + (isFinalSegment ? " (final)" : ""));
				statusTextView.setText("Segment created: " + segmentFilePath
						+ (isFinalSegment ? " (final)" : ""));
			}

		},

		new VideoUploaderCallback() {

			public void createVideoDidFinish(int result, int videoId) {
			}

			public void createVideoStreamletDidFinish(int result, int videoId,
					int streamletId, boolean isFinalStreamlet) {

				Log.i("VideoPreview::createVideoStreamletDidFinish",
						"Create streamlet, result=" + result + " video id="
								+ videoId + " streamlet id=" + streamletId
								+ (isFinalStreamlet ? "(final)" : ""));
				statusTextView.setText("Create streamlet, result=" + result
						+ " video id=" + videoId + " streamlet id="
						+ streamletId + (isFinalStreamlet ? "(final)" : ""));
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
		File mediaStorageDir = new File(
				Environment
						.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
				APP_NAME);
		FilenameFilter filter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(".mp4");
			}
		};
		String[] fileList = mediaStorageDir.list(filter);
		return fileList;
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
