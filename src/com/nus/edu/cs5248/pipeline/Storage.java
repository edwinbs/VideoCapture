package com.nus.edu.cs5248.pipeline;

import java.io.File;
import java.io.FilenameFilter;

import android.os.Environment;
import android.util.Log;

public class Storage {
	
	public static final String APP_NAME = "CS5248";
	public static final String TAG = "Storage";

	public static File getSegmentFolder(final File videoFile, final boolean createIfNotExist) {
		String filename = videoFile.getName();
		String title = filename.substring(0, filename.lastIndexOf('.'));
		
		return getSegmentFolder(title, createIfNotExist);
	}
	
	public static File getSegmentFolder(final String videoTitle, final boolean createIfNotExist) {
		File segmentsDir = new File(getMediaFolder(createIfNotExist), "segments");
    	File currentVideoDir = new File(segmentsDir, videoTitle);
    	
    	if (createIfNotExist) {
			if (!currentVideoDir.exists()) {
				if (!currentVideoDir.mkdirs()) {
					Log.d(TAG, "failed to create directory: " + currentVideoDir.getPath());
					return null;
				}
			}
    	}
		
		return currentVideoDir;
	}
	
	public static File getMediaFolder(final boolean createIfNotExist) {
		File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), APP_NAME);
		
		if (createIfNotExist) {
			if (!mediaStorageDir.exists()) {
				if (!mediaStorageDir.mkdirs()) {
					Log.d(TAG, "failed to create directory: " + mediaStorageDir.getPath());
					return null;
				}
			}
		}
		
		return mediaStorageDir;
	}
	
	public static File getFileForSegment(final File videoFile, final int segmentIndex) {
		String filename = videoFile.getName();
		String segmentFilename = filename.replace(".mp4", String.format("_%05d.mp4", segmentIndex));
		return new File(getSegmentFolder(videoFile, true), segmentFilename);
	}
	
	public static String[] getMP4FileList(final File folder) {
		FilenameFilter filter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(".mp4");
			}
		};
		return folder.list(filter);
	}
	
}
