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
				if (videoId <= 0)
					return;
				
				if (result == DashResult.OK) { //New upload
					segmentVideo(filePath, videoId, segmenterCallback, uploaderCallback);
				}
				else if (result == DashResult.ALREADY_EXIST) { //Resuming upload
					resumeUpload(videoTitle, videoId, uploaderCallback);
				}
			}

			public void createVideoStreamletDidFinish(int result,
					String streamletFilename, boolean isFinalStreamlet,
					long totalTime, long encodeTime) { }

			public void createVideoStreamletWillStart(String streamletFilename) { }
    	});
			
	}
	
	private void segmentVideo(
			final String filePath, 
			final int videoId, 
			final SegmenterCallback segmenterCallback, 
			final VideoUploaderCallback uploaderCallback) {
		
		//Stage 2: segment the video
		Segmenter.INSTANCE.segmentVideo(filePath, 3.0, new SegmenterCallback() {
			
			public void onSegmentCreated(String segmentFilePath, boolean isFinalSegment, int progress) {
				if (segmenterCallback != null)
					segmenterCallback.onSegmentCreated(segmentFilePath, isFinalSegment, progress);
				
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
					String streamletFilename, boolean isFinalStreamlet, long totalTime, long encodeTime) {
				
				if (uploaderCallback != null)
					uploaderCallback.createVideoStreamletDidFinish(result, streamletFilename, isFinalStreamlet, totalTime, encodeTime);
				
				Log.d("UploadPipeline::uploadVideo()", "Uploaded segment: " + segmentFilePath + " result=" + result + (isFinalStreamlet ? " (final)" : ""));
			}

			public void createVideoStreamletWillStart(String streamletFilename) {
				
				if (uploaderCallback != null)
					uploaderCallback.createVideoStreamletWillStart(streamletFilename);
			}
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
		
		File segmentsFolder = Storage.getSegmentFolder(videoTitle, false);
		String[] filenames = Storage.getMP4FileList(segmentsFolder);
		for (int i = 0; i < filenames.length; ++i) {
			if (excludeList != null && excludeList.contains(filenames[i]))
				continue;
			
			boolean isFinalSegment = (i == filenames.length - 1);
			String filePath = (new File(segmentsFolder, filenames[i])).getPath();
			uploadVideo(filePath, videoId, isFinalSegment, callback);
		}
	}
	
}
