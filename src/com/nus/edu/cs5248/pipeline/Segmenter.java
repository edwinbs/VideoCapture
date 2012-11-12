package com.nus.edu.cs5248.pipeline;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.Executor;

import android.os.AsyncTask;
import android.util.Log;

import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.TimeToSampleBox;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.CroppedTrack;

public enum Segmenter {
	
	INSTANCE; //Singleton
	Executor executor;
	
	Segmenter() {
		this.executor = new SerialExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}
	
	public void segmentVideo(String filePath, double targetSegmentLength, SegmenterCallback callback) {
		new SegmentVideoTask().executeOnExecutor(this.executor,
				SegmentVideoTaskParam.create(filePath, targetSegmentLength, callback));
	}
	
}

class SegmentVideoTaskParam {
	
	static SegmentVideoTaskParam create(String filePath, double targetSegmentLength, SegmenterCallback callback) {
		SegmentVideoTaskParam param = new SegmentVideoTaskParam();
		param.filePath = filePath;
		param.targetSegmentLength = targetSegmentLength;
		param.callback = callback;
		return param;
	}
	
	String filePath;
	double targetSegmentLength;
	SegmenterCallback callback;
	
}

class SegmentVideoTaskProgress {
	static SegmentVideoTaskProgress create(String segmentFilePath, boolean isFinalSegment) {
		SegmentVideoTaskProgress inst = new SegmentVideoTaskProgress();
		inst.segmentFilePath = segmentFilePath;
		inst.isFinalSegment = isFinalSegment;
		return inst;
	}
	
	String segmentFilePath;
	boolean isFinalSegment;
}

class SegmentVideoTask extends AsyncTask <SegmentVideoTaskParam, SegmentVideoTaskProgress, Integer> {

	@Override
	protected Integer doInBackground(SegmentVideoTaskParam... param) {
		this.targetSegmentLength = param[0].targetSegmentLength;
		this.callback = param[0].callback;
		this.filePath = param[0].filePath;
		
		return segmentVideo();
	}
	
	protected void onProgressUpdate(SegmentVideoTaskProgress... progress) {
		if (this.callback != null) {
			callback.onSegmentCreated(progress[0].segmentFilePath, progress[0].isFinalSegment);
		}
	}
	
	protected Integer segmentVideo() {
		FileInputStream movieStream = null;
		
		try {
			movieStream = new FileInputStream(this.filePath);
			this.movie = MovieCreator.build(movieStream.getChannel());
			this.tracks = this.movie.getTracks();
			this.videoTrack = findVideoTrack();
			if (this.videoTrack == null)
				return DashResult.INVALID_FORMAT;
			
			double startTime = correctTimeToSyncSample(this.videoTrack, 0.0, false);;
			double endTime   = correctTimeToSyncSample(this.videoTrack, startTime + this.targetSegmentLength, true);
			int segmentId = 0;
			
			while (startTime < endTime) {
				Log.d("SegmentVideoTask::cropTracks()", "adjusted start=" + startTime + ", end=" + endTime);
				
				this.movie.setTracks(new LinkedList<Track>());
				cropNextSegmentWithAdjustedTime(startTime, endTime);
				
				String segmentFilePath = this.filePathForSegment(segmentId);
				writeMovieFile(segmentFilePath);
				
				//Find next segment's start time and end time
				//If next segment's start is equal to its end (duration=0), then this is the final segment
				startTime = endTime;
				endTime   = correctTimeToSyncSample(this.videoTrack, startTime + this.targetSegmentLength, true);
		        
		        publishProgress(SegmentVideoTaskProgress.create(segmentFilePath, startTime == endTime));
		        
				++segmentId;
			}
		} catch (FileNotFoundException e) {
			Log.e("Segmenter::segment()", "File not found: " + e.getMessage());
			return DashResult.FILE_NOT_FOUND;
		} catch (IOException e) {
			Log.e("Segmenter::segment()", "IO error: " + e.getMessage());
			return DashResult.IO_ERROR;
		} finally {
			try { movieStream.close(); } catch (Exception e) {}
		}
		
		return DashResult.OK;
	}
	
	private void cropNextSegmentWithAdjustedTime(double adjustedStartTime, double adjustedEndTime) {
		this.movie.setTracks(new LinkedList<Track>());
		
		for (Track track : this.tracks) {
			long currentSample = 0;
			double currentTime = 0;
			long startSample = -1;
			long endSample = -1;
			
			for (TimeToSampleBox.Entry entry : track.getDecodingTimeEntries()) {
				for (int i = 0; i < entry.getCount(); i++) {
					if (currentTime <= adjustedStartTime) {
						// current sample is still before the new start time
						startSample = currentSample;
					}
					
					if (currentTime <= adjustedEndTime) {
						// current sample is after the new start time and still before the new end time
						endSample = currentSample;
					} else {
						// current sample is after the end of the cropped video
						break;
					}
					currentTime += (double) entry.getDelta() / (double) track.getTrackMetaData().getTimescale();
                    currentSample++;
				}
			}
			this.movie.addTrack(new CroppedTrack(track, startSample, endSample));
		}
	}
	
    protected String filePathForSegment(int segmentId) {
    	return this.filePath.replace(".mp4", String.format("_%05d.mp4", segmentId));
    }
	
	protected void writeMovieFile(String filePath) {
		try {
			IsoFile out = new DefaultMp4Builder().build(this.movie);
			FileOutputStream fos = new FileOutputStream(filePath);
			FileChannel fc = fos.getChannel();
	        out.getBox(fc);
	        fc.close();
	        fos.close();
		} catch (IOException e) {
			Log.d("Segmenter::writeMovieFile()", "IO error: " + e.getMessage());
		}
	}
	
	protected Track findVideoTrack() {
		for (Track track : this.tracks) {
			if (isVideoTrack(track))
				return track;
		}
		return null;
	}
	
	protected static boolean isVideoTrack(Track track) {
		return (track.getMediaHeaderBox().getType().equals("vmhd"));
	}
	
	protected static long getDuration(Track track) {
        long duration = 0;
        for (TimeToSampleBox.Entry entry : track.getDecodingTimeEntries()) {
            duration += entry.getCount() * entry.getDelta();
        }
        return duration;
    }
	
    private static double correctTimeToSyncSample(Track track, double cutHere, boolean next) {
        double[] timeOfSyncSamples = new double[track.getSyncSamples().length];
        long currentSample = 0;
        double currentTime = 0;
        for (int i = 0; i < track.getDecodingTimeEntries().size(); i++) {
            TimeToSampleBox.Entry entry = track.getDecodingTimeEntries().get(i);
            for (int j = 0; j < entry.getCount(); j++) {
                if (Arrays.binarySearch(track.getSyncSamples(), currentSample + 1) >= 0) {
                    // samples always start with 1 but we start with zero therefore +1
                    timeOfSyncSamples[Arrays.binarySearch(track.getSyncSamples(), currentSample + 1)] = currentTime;
                }
                currentTime += (double) entry.getDelta() / (double) track.getTrackMetaData().getTimescale();
                currentSample++;
            }
        }
        double previous = 0;
        for (double timeOfSyncSample : timeOfSyncSamples) {
            if (timeOfSyncSample > cutHere) {
                if (next) {
                    return timeOfSyncSample;
                } else {
                    return previous;
                }
            }
            previous = timeOfSyncSample;
        }
        return timeOfSyncSamples[timeOfSyncSamples.length - 1];
    }
    
	protected String 			filePath;
	protected double 			targetSegmentLength;
	protected SegmenterCallback callback;
	
	private Movie				movie;
	private List<Track> 		tracks;
	private Track				videoTrack;
	
}
