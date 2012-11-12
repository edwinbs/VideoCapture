package com.nus.edu.cs5248.pipeline;

import java.io.File;
import java.util.Set;

import android.util.Log;

public enum UploadPipeline {
	
	INSTANCE;
	
	public void segmentAndUpload(
			final String videoTitle, 
			final String filePath, 
			final SegmenterCallback segmenterCallback, 
			final VideoUploaderCallback uploaderCallback) {
		
		//Stage 1: get a new video ID from the server
		DashServer.INSTANCE.createVideo(videoTitle, new VideoUploaderCallback() {

			public void createVideoDidFinish(int result, int videoId) {
				if (uploaderCallback != null)
    				uploaderCallback.createVideoDidFinish(result, videoId);
				
				//If we cannot get a new video ID from the server, abort
				if (videoId <= 0 || result != DashResult.OK)
					return;
				
				segmentVideo(filePath, videoId, segmenterCallback, uploaderCallback);
			}

			public void createVideoStreamletDidFinish(int result,
					int videoId, int streamletId, boolean isFinalStreamlet) { }
    	});
			
	}
	
	private void segmentVideo(
			final String filePath, 
			final int videoId, 
			final SegmenterCallback segmenterCallback, 
			final VideoUploaderCallback uploaderCallback) {
		
		//Stage 2: segment the video
		Segmenter.INSTANCE.segmentVideo(filePath, 3.0, new SegmenterCallback() {
			
			public void onSegmentCreated(String segmentFilePath, boolean isFinalSegment) {
				if (segmenterCallback != null)
					segmenterCallback.onSegmentCreated(segmentFilePath, isFinalSegment);
				
				Log.d("UploadPipeline::segmentVideo()", "Segment: " + segmentFilePath + (isFinalSegment ? " (final)" : ""));
		    	uploadVideo(segmentFilePath, videoId, isFinalSegment, uploaderCallback);
			}
		});
	}
	
	private void uploadVideo(
			final String segmentFilePath, 
			final int videoId, 
			final boolean isFinalSegment,
			final VideoUploaderCallback uploaderCallback) {
		
		//Stage 3: upload the video
		DashServer.INSTANCE.createVideoStreamlet(segmentFilePath, videoId, isFinalSegment, true, new VideoUploaderCallback() {

			public void createVideoDidFinish(int result, int videoId) { }

			public void createVideoStreamletDidFinish(int result,
					int videoId, int streamletId, boolean isFinalStreamlet) {
				
				if (uploaderCallback != null)
					uploaderCallback.createVideoStreamletDidFinish(result, videoId, streamletId, isFinalStreamlet);
				
				Log.d("UploadPipeline::uploadVideo()", "Uploaded segment: " + segmentFilePath + " result=" + result + (isFinalStreamlet ? " (final)" : ""));
			}
		});
	}
	
	public void uploadSegmentedVideo(final String videoTitle, final VideoUploaderCallback callback) {
		//Get a new or existing video ID from the server
		//then decide whether to start from beginning or resume
		DashServer.INSTANCE.createVideo(videoTitle, new VideoUploaderCallback() {

			public void createVideoDidFinish(int result, int videoId) {
				if (result == DashResult.ALREADY_EXIST && videoId != 0) {
					resumeUpload(videoTitle, videoId, callback);
				} else if (result == DashResult.OK && videoId != 0) {
					queueSegmentsForUpload(videoTitle, videoId, null, callback);
				}
			}

			public void createVideoStreamletDidFinish(int result, int videoId,
					int streamletId, boolean isFinalStreamlet) { }
			
		});
	}
	
	private void resumeUpload(final String videoTitle, final int videoId, final VideoUploaderCallback callback) {
		DashServer.INSTANCE.listStreamletsForVideo(videoId, new ListVideoStreamletsCallback() {

			public void videoStreamletsListRetrieved(int result, int videoId,
					Set<String> streamlets) {
				
				if (result != DashResult.OK) {
					Log.e("UploadPipeline::resumeUpload", "Failed to retrieve uploaded streamlets list, error=" + result);
					return;
				}
				
				queueSegmentsForUpload(videoTitle, videoId, streamlets, callback);
			}
			
		});
	}
	
	private void queueSegmentsForUpload(final String videoTitle, final int videoId, final Set<String> excludeList, final VideoUploaderCallback callback) {
		
		Log.d("UploadPipeline::queueSegmentsForUpload", "Exclude list size=" + ((excludeList != null) ? excludeList.size() : 0));
		
		boolean isFinalSegment = false;
		for (int i = 0; !isFinalSegment; i++) {
			String fileName = String.format("%s_%05d.mp4", videoTitle, i);
			
			if (excludeList != null && excludeList.contains(fileName))
				continue;
			
			String filePath = "/sdcard/test/" + fileName;
			isFinalSegment = !(new File(String.format("/sdcard/test/%s_%05d.mp4", videoTitle, i+1)).exists());
			
			uploadVideo(filePath, videoId, isFinalSegment, callback);
		}
	}
	
}
