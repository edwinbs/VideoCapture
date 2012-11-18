package com.nus.edu.cs5248.pipeline;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import android.os.AsyncTask;
import android.util.Log;

public enum DashServer {

	INSTANCE; // Singleton

	DashServer() {
		// Only one server-related task may run at a time (avoid network hog),
		// but it can execute along with other tasks like segmenting.
		this.executor = new SerialExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	public void createVideo(String title, VideoUploaderCallback callback) {
		new CreateVideoTask().executeOnExecutor(this.executor,
				CreateVideoTaskParam.create(title, callback));
	}

	public void createVideoStreamlet(String filePath, int videoId,
			boolean isFinalStreamlet, boolean deleteFile,
			VideoUploaderCallback callback) {
		new CreateVideoStreamletTask().executeOnExecutor(this.executor,
				CreateVideoStreamletTaskParam.create(filePath, videoId,
						isFinalStreamlet, deleteFile, callback));
	}

	public void listStreamletsForVideo(int videoId,
			ListVideoStreamletsCallback callback) {
		new ListStreamletsTask().executeOnExecutor(this.executor,
				ListStreamletsTaskParam.create(videoId, callback));

	}

	public static String urlFor(String restAction) {
		return BASE_URL + restAction;
	}
	
	Executor executor;

	public static final String CREATE_VIDEO 			= "videos_create.php";
	public static final String CREATE_VIDEO_STREAMLET 	= "video_streamlets_create.php";
	public static final String VIDEO_STREAMLETS_INDEX 	= "video_streamlets_index.php?video_id=";

	public static final String VIDEO_TITLE 			= "title";
	public static final String ID 					= "id";
	public static final String RESULT 				= "result";
	public static final String IS_FINAL_STREAMLET 	= "is_final_streamlet";
	public static final String VIDEO_ID 			= "video_id";
	public static final String FILE 				= "file";
	public static final String VIDEO_STREAMLETS 	= "video_streamlets";
	public static final String FILENAME 			= "filename";
	public static final String ENCODE_TIME			= "encoding_time";

	private static final String BASE_URL = "http://pilatus.d1.comp.nus.edu.sg/~a0082245/";
}

class CreateVideoTaskParam {

	static CreateVideoTaskParam create(String videoTitle,
			VideoUploaderCallback callback) {
		CreateVideoTaskParam param = new CreateVideoTaskParam();
		param.videoTitle = videoTitle;
		param.callback = callback;
		return param;
	}

	String videoTitle;
	boolean deleteFile;
	VideoUploaderCallback callback;

}

class CreateVideoTask extends AsyncTask<CreateVideoTaskParam, Integer, Integer> {

	@Override
	protected Integer doInBackground(CreateVideoTaskParam... param) {
		final String FN = "CreateVideoTask::doInBackground()";

		this.callback = param[0].callback;
		this.result = DashResult.FAIL;

		try {
			HttpPost post = new HttpPost(
					DashServer.urlFor(DashServer.CREATE_VIDEO));

			List<NameValuePair> postParams = new ArrayList<NameValuePair>();
			postParams.add(new BasicNameValuePair(DashServer.VIDEO_TITLE,
					param[0].videoTitle));

			UrlEncodedFormEntity entity = new UrlEncodedFormEntity(postParams,
					HTTP.UTF_8);
			post.setEntity(entity);

			HttpClient client = new DefaultHttpClient();
			HttpResponse postResponse = client.execute(post);
			HttpEntity responseEntity = postResponse.getEntity();
			if (responseEntity != null) {
				this.response = new JSONObject(
						EntityUtils.toString(responseEntity));
				this.result = this.response.getInt(DashServer.RESULT);
			}
		} catch (UnsupportedEncodingException e) {
			Log.e(FN, "Unsupported encoding exception: " + e.getMessage());
		} catch (ClientProtocolException e) {
			Log.e(FN, "Client protocol exception: " + e.getMessage());
		} catch (IOException e) {
			Log.e(FN, "IO exception: " + e.getMessage());
		} catch (ParseException e) {
			Log.e(FN, "JSON parse exception: " + e.getMessage());
		} catch (JSONException e) {
			Log.e(FN, "JSON exception: " + e.getMessage());
		} catch (Exception e) {
			Log.e(FN, "Unexpected exception: " + e.getMessage());
			e.printStackTrace();
		}

		return this.result;
	}

	@Override
	protected void onPostExecute(Integer result) {
		if (this.callback != null) {
			int videoId = 0;
			if (this.response != null) {
				try {
					videoId = this.response.getInt(DashServer.ID);
				} catch (JSONException e) {
					videoId = -1;
					result = DashResult.MALFORMED_RESPONSE;
				}
			}

			this.callback.createVideoDidFinish(result, videoId);
		}
	}

	protected VideoUploaderCallback callback;
	protected JSONObject response;
	protected int result;

}

class CreateVideoStreamletTaskParam {

	static CreateVideoStreamletTaskParam create(String filePath, int videoId,
			boolean isFinalStreamlet, boolean deleteFile,
			VideoUploaderCallback callback) {
		CreateVideoStreamletTaskParam param = new CreateVideoStreamletTaskParam();
		param.filePath = filePath;
		param.videoId = videoId;
		param.isFinalStreamlet = isFinalStreamlet;
		param.deleteFile = deleteFile;
		param.callback = callback;
		return param;
	}

	String filePath;
	int videoId;
	boolean isFinalStreamlet;
	boolean deleteFile;
	VideoUploaderCallback callback;

}

class CreateVideoStreamletTaskResult {
	public CreateVideoStreamletTaskResult(int resultCode, long totalTime, long encodeTime) {
		this.resultCode = resultCode;
		this.totalTime = totalTime;
		this.encodeTime = encodeTime;
	}
	
	int resultCode;
	long totalTime;
	long encodeTime;
}

class CreateVideoStreamletTask extends
		AsyncTask<CreateVideoStreamletTaskParam, Integer, CreateVideoStreamletTaskResult> {

	@Override
	protected CreateVideoStreamletTaskResult doInBackground(CreateVideoStreamletTaskParam... param) {
		final String FN = "CreateVideoStreamTask::doInBackground()";

		this.callback = param[0].callback;
		this.filePath = param[0].filePath;
		this.videoId = param[0].videoId;
		this.isFinalStreamlet = param[0].isFinalStreamlet;
		this.result = DashResult.FAIL;
		
		long encodeTime = 0;

		Log.d(FN, "Start uploading " + this.filePath);
		
		long startTime = System.currentTimeMillis();
		
		publishProgress(0);

		try {
			HttpPost post = new HttpPost(
					DashServer.urlFor(DashServer.CREATE_VIDEO_STREAMLET));

			MultipartEntity reqEntity = new MultipartEntity(
					HttpMultipartMode.BROWSER_COMPATIBLE);
			File videoFile = new File(param[0].filePath);

			reqEntity.addPart(DashServer.VIDEO_ID,
					new StringBody(Integer.toString(this.videoId)));
			reqEntity.addPart(DashServer.IS_FINAL_STREAMLET, new StringBody(
					this.isFinalStreamlet ? "1" : "0"));
			reqEntity.addPart(DashServer.FILE, new FileBody(videoFile));

			post.setEntity(reqEntity);

			HttpClient client = new DefaultHttpClient();
			HttpResponse postResponse = client.execute(post);
			HttpEntity responseEntity = postResponse.getEntity();
			if (responseEntity != null) {
				String responseString = EntityUtils.toString(responseEntity);
				Log.d(FN, "Response=" + responseString);
				this.response = new JSONObject(responseString);
				this.result = this.response.getInt(DashServer.RESULT);
				encodeTime = this.response.getLong(DashServer.ENCODE_TIME);
			}

			if (param[0].deleteFile && this.result == DashResult.OK) {
				boolean isDeleted = videoFile.delete();
				if (!isDeleted) {
					Log.e(FN, "Failed to delete streamlet file");
				}
			}
		} catch (UnsupportedEncodingException e) {
			Log.e(FN, "Unsupported encoding exception: " + e.getMessage());
		} catch (ClientProtocolException e) {
			Log.e(FN, "Client protocol exception: " + e.getMessage());
		} catch (IOException e) {
			Log.e(FN, "IO exception: " + e.getMessage());
		} catch (ParseException e) {
			Log.e(FN, "JSON parse exception: " + e.getMessage());
		} catch (JSONException e) {
			Log.e(FN, "JSON exception: " + e.getMessage());
		} catch (Exception e) {
			Log.e(FN, "Exception: " + e.getMessage());
			e.printStackTrace();
		}
		
		long endTime = System.currentTimeMillis();
		long totalTime = endTime - startTime;

		return new CreateVideoStreamletTaskResult(this.result, totalTime, encodeTime);
	}

	@Override
	protected void onPostExecute(CreateVideoStreamletTaskResult taskResult) {
		if (this.callback != null) {
			this.callback.createVideoStreamletDidFinish(taskResult.resultCode, (new File(this.filePath)).getName(), 
					isFinalStreamlet, taskResult.totalTime, taskResult.encodeTime);
		}
	}
	
	protected void onProgressUpdate(Integer... progress) {
		if (this.callback != null) {
			this.callback.createVideoStreamletWillStart(this.filePath);
		}
	}

	protected VideoUploaderCallback callback;
	protected JSONObject response;
	protected int result;
	protected int videoId;
	protected boolean isFinalStreamlet;
	protected String filePath;

}

class ListStreamletsTaskParam {

	static ListStreamletsTaskParam create(int videoId,
			ListVideoStreamletsCallback callback) {
		ListStreamletsTaskParam param = new ListStreamletsTaskParam();
		param.videoId = videoId;
		param.callback = callback;
		return param;
	}

	protected int videoId;
	protected ListVideoStreamletsCallback callback;
}

class ListStreamletsTask extends
		AsyncTask<ListStreamletsTaskParam, Integer, Integer> {

	@Override
	protected Integer doInBackground(ListStreamletsTaskParam... param) {
		final String FN = "ListStreamletsTask::doInBackground()";

		this.callback = param[0].callback;
		this.videoId = param[0].videoId;

		try {
			HttpClient client = new DefaultHttpClient();
			String getURL = DashServer
					.urlFor(DashServer.VIDEO_STREAMLETS_INDEX) + this.videoId;
			HttpGet get = new HttpGet(getURL);
			HttpResponse getResponse = client.execute(get);
			HttpEntity responseEntity = getResponse.getEntity();
			if (responseEntity != null) {
				JSONObject response = new JSONObject(
						EntityUtils.toString(responseEntity));

				this.result = response.getInt(DashServer.RESULT);

				this.streamlets = new HashSet<String>();
				JSONArray arr = response
						.getJSONArray(DashServer.VIDEO_STREAMLETS);
				for (int i = 0; i < arr.length(); ++i) {
					this.streamlets.add(arr.getJSONObject(i).getString(
							DashServer.FILENAME));
				}
			}
		} catch (ClientProtocolException e) {

			Log.e(FN, "Client protocol exception: " + e.getMessage());
		} catch (IOException e) {
			Log.e(FN, "IO exception: " + e.getMessage());
		} catch (ParseException e) {
			Log.e(FN, "JSON parse exception: " + e.getMessage());
		} catch (JSONException e) {
			Log.e(FN, "JSON exception: " + e.getMessage());
		} catch (Exception e) {
			Log.e(FN, "Unexpected exception: " + e.getMessage());
			e.printStackTrace();
		}

		return null;
	}

	@Override
	protected void onPostExecute(Integer result) {
		if (this.callback != null) {
			callback.videoStreamletsListRetrieved(this.result, this.videoId,
					this.streamlets);
		}
	}

	protected ListVideoStreamletsCallback callback;
	protected int result;
	protected int videoId;
	protected Set<String> streamlets;

}