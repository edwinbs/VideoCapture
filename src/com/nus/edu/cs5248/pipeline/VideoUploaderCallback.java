package com.nus.edu.cs5248.pipeline;

public interface VideoUploaderCallback {
	
	public void createVideoDidFinish(int result, int videoId);
	
	public void createVideoStreamletWillStart(String streamletFilename);
	
	public void createVideoStreamletDidFinish(int result, String streamletFilename, boolean isFinalStreamlet, long totalTime, long encodingTime);
	
}
