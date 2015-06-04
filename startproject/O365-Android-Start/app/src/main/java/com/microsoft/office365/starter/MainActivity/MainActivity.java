/*
 *  Copyright (c) Microsoft. All rights reserved. Licensed under the MIT license. See full license at the bottom of this file.
 */

package com.microsoft.office365.starter.MainActivity;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.VideoView;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.microsoft.office365.starter.Calendar.CalendarEventListActivity;
import com.microsoft.office365.starter.Email.MailItemListActivity;
import com.microsoft.office365.starter.FilesFolders.FileListActivity;
import com.microsoft.office365.starter.O365APIsStart_Application;
import com.microsoft.office365.starter.R;
import com.microsoft.office365.starter.helpers.AuthenticationController;
import com.microsoft.office365.starter.helpers.Constants;
import com.microsoft.office365.starter.helpers.AsyncController;
import com.microsoft.office365.starter.helpers.ProgressDialogHelper;
import com.microsoft.office365.starter.interfaces.MainActivityCoordinator;
import com.microsoft.office365.starter.interfaces.OnServicesDiscoveredListener;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Callable;

import java.net.URI;
import java.util.UUID;

public class MainActivity extends Activity implements MainActivityCoordinator,
        OnServicesDiscoveredListener {

	private O365APIsStart_Application mApplication;
	//private MainButtonsFragment mButtonsFragment;
	private Menu mMenu;
    private ProgressDialog mDialogSignIn;
    private ProgressDialog mDialogDiscoverServices;



    // Activity request codes
    private static final int CAMERA_CAPTURE_IMAGE_REQUEST_CODE = 100;
    private static final int CAMERA_CAPTURE_VIDEO_REQUEST_CODE = 200;
    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;

    // directory name to store captured images and videos
    private static final String IMAGE_DIRECTORY_NAME = "Bool_obs";

    private Uri fileUri; // file url to store image/video

    private ImageView imgPreview;
    private VideoView videoPreview;
    private Button btnCapturePicture, btnRecordVideo;


    /**
     * Checking device has camera hardware or not
     * */
    private boolean isDeviceSupportCamera() {
        if (getApplicationContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_CAMERA)) {
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

    /**
     * Capturing Camera Image will lauch camera app requrest image capture
     */
    private void captureImage() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        fileUri = getOutputMediaFileUri(MEDIA_TYPE_IMAGE);

        intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);

        // start the image capture Intent
        startActivityForResult(intent, CAMERA_CAPTURE_IMAGE_REQUEST_CODE);
    }

    /**
     * Here we store the file url as it will be null after returning from camera
     * app
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // save file url in bundle as it will be null on scren orientation
        // changes
        outState.putParcelable("file_uri", fileUri);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        // get the file url
        fileUri = savedInstanceState.getParcelable("file_uri");
    }

    /**
     * Recording video
     */
    private void recordVideo() {
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);

        fileUri = getOutputMediaFileUri(MEDIA_TYPE_VIDEO);

        // set video quality
        intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);

        intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri); // set the image file
        // name

        // start the video capture Intent
        startActivityForResult(intent, CAMERA_CAPTURE_VIDEO_REQUEST_CODE);
    }

    /**
     * Receiving activity result method will be called after closing the camera
     * */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // if the result is capturing Image
        if (requestCode == CAMERA_CAPTURE_IMAGE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // successfully captured the image
                // display it in image view
                previewCapturedImage();
            } else if (resultCode == RESULT_CANCELED) {
                // user cancelled Image capture
                Toast.makeText(getApplicationContext(),
                        "User cancelled image capture", Toast.LENGTH_SHORT)
                        .show();
            } else {
                // failed to capture image
                Toast.makeText(getApplicationContext(),
                        "Sorry! Failed to capture image", Toast.LENGTH_SHORT)
                        .show();
            }
        } else if (requestCode == CAMERA_CAPTURE_VIDEO_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // video successfully recorded
                // preview the recorded video
                previewVideo();
            } else if (resultCode == RESULT_CANCELED) {
                // user cancelled recording
                Toast.makeText(getApplicationContext(),
                        "User cancelled video recording", Toast.LENGTH_SHORT)
                        .show();
            } else {
                // failed to record video
                Toast.makeText(getApplicationContext(),
                        "Sorry! Failed to record video", Toast.LENGTH_SHORT)
                        .show();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
        AuthenticationController
                .getInstance()
                .getAuthenticationContext()
                .onActivityResult(
                        requestCode
                        , resultCode
                        , data);
    }

    /**
     * Display image from a path to ImageView
     */
    private void previewCapturedImage() {
        try {
            // hide video preview
            videoPreview.setVisibility(View.GONE);

            imgPreview.setVisibility(View.VISIBLE);

            // bimatp factory
            BitmapFactory.Options options = new BitmapFactory.Options();

            // downsizing image as it throws OutOfMemory Exception for larger
            // images
            options.inSampleSize = 8;

            final Bitmap bitmap = BitmapFactory.decodeFile(fileUri.getPath(),
                    options);

            imgPreview.setImageBitmap(bitmap);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    /**
     * Previewing recorded video
     */
    private void previewVideo() {
        try {
            // hide image preview
            imgPreview.setVisibility(View.GONE);

            videoPreview.setVisibility(View.VISIBLE);
            videoPreview.setVideoPath(fileUri.getPath());
            // start playing
            videoPreview.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * ------------ Helper Methods ----------------------
     * */

    /**
     * Creating file uri to store image/video
     */
    public Uri getOutputMediaFileUri(int type) {
        return Uri.fromFile(getOutputMediaFile(type));
    }

    /**
     * returning image / video
     */
    private static File getOutputMediaFile(int type) {

        // External sdcard location
        File mediaStorageDir = new File(
                Environment
                        .getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                IMAGE_DIRECTORY_NAME);

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d(IMAGE_DIRECTORY_NAME, "Oops! Failed create "
                        + IMAGE_DIRECTORY_NAME + " directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator
                    + "IMG_" + timeStamp + ".jpg");
        } else if (type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator
                    + "VID_" + timeStamp + ".mp4");
        } else {
            return null;
        }

        return mediaFile;
    }





    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imgPreview = (ImageView) findViewById(R.id.imgPreview);
        videoPreview = (VideoView) findViewById(R.id.videoPreview);
        btnCapturePicture = (Button) findViewById(R.id.btnCapturePicture);
        btnRecordVideo = (Button) findViewById(R.id.btnRecordVideo);

        /**
         * Capture image button click event
         */
        btnCapturePicture.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // capture picture
                captureImage();
            }
        });

        /**
         * Record video button click event
         */
        btnRecordVideo.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // record video
                recordVideo();
            }
        });

        // Checking camera availability
        if (!isDeviceSupportCamera()) {
            Toast.makeText(getApplicationContext(),
                    "Sorry! Your device doesn't support camera",
                    Toast.LENGTH_LONG).show();
            // will close the app if the device does't have camera
            finish();
        }
		mApplication = (O365APIsStart_Application) getApplication();
		mApplication.setOnServicesDiscoveredResultListener(this);

		// When the app starts, the buttons should be disabled until the user
		// signs in to Office 365
        /*
		FragmentManager fragmentManager = getFragmentManager();
		mButtonsFragment = (MainButtonsFragment) fragmentManager
				.findFragmentById(R.id.buttonsFragment);
		mButtonsFragment.setButtonsEnabled(mApplication.userIsAuthenticated());
		*/

        setupApp();
        setupHandlers();
	}

    private void setupApp() {

        // date


        // spinner
        ArrayAdapter adapter = ArrayAdapter.createFromResource(
                this, R.array.classify_entries, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        Spinner sp = (Spinner)findViewById(R.id.spinnerClassify);
        sp.setAdapter(adapter);


    }

    private void setupHandlers() {


        View.OnClickListener btnCreateClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Toast.makeText(MainActivity.this, "Getting data...", Toast.LENGTH_LONG).show();

                //TextView txt = (TextView)findViewById(R.id.textViewOutput);
                //txt.setText("Hello\nBool");

                Log.d("andpoint","Hej!");
            }
        };

        Button btn = (Button)findViewById(R.id.buttonCreate);
        btn.setOnClickListener(btnCreateClick);
    }




    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		mMenu = menu;

		if (mApplication.userIsAuthenticated()) {
            SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
            String displayName = sharedPref.getString("DisplayName", "");

			MenuItem signInMenuItem = mMenu.findItem(R.id.menu_signin);
			signInMenuItem.setIcon(R.drawable.user_default_signedin);
            signInMenuItem.setTitle(displayName);
        }

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		try {
			switch (item.getItemId()) {
			case R.id.menu_clear_credentials:
				clearCredentials();
				return true;
			case R.id.menu_signin:
                //check that client id and redirect have been set correctly
                try
                {
                    UUID.fromString(Constants.CLIENT_ID);
                    URI.create(Constants.REDIRECT_URI);
                }
                catch (IllegalArgumentException e)
                {
                    Toast.makeText(
                            this
                            , getString(R.string.warning_clientid_redirecturi_incorrect)
                            , Toast.LENGTH_LONG).show();
                    return true;
                }

				signIn_OnClick();
				return true;
			default:
				return super.onOptionsItemSelected(item);
			}

		} catch (Throwable t) {
            if (t.getMessage() == null)
			    Log.e("Asset", " ");
            else
                Log.e("Asset", t.getMessage());
		}
		return true;
	}

	private void clearCredentials() {
		mApplication.clearClientObjects();
		mApplication.clearCookies();
        mApplication.clearTokens();
		userSignedOut();
	}

	protected void userSignedOut() {
		//mButtonsFragment.setButtonsEnabled(false);

		MenuItem signInMenuItem = mMenu.findItem(R.id.menu_signin);
		signInMenuItem.setIcon(R.drawable.user_signedout);

		signInMenuItem.setTitle(R.string.MainActivity_SignInButtonText);
	}


	public void signIn_OnClick() {
        mDialogSignIn = ProgressDialogHelper.showProgressDialog(
                MainActivity.this, "Authenticating to Office 365...",
                "Please wait.");

        AuthenticationController.getInstance().setContextActivity(this);
		SettableFuture<Boolean> authenticated = AuthenticationController.getInstance().initialize();

		Futures.addCallback(authenticated, new FutureCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                Log.i("MainActivity", "Authentication successful");

                AsyncController.getInstance().postAsyncTask(new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (mDialogSignIn.isShowing()) {
                                    mDialogSignIn.dismiss();
                                }
                                Toast.makeText(
                                        MainActivity.this,
                                        "Authentication successful",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });

                        return null;
                    }
                });

                // Discover services
                mDialogDiscoverServices = ProgressDialogHelper.showProgressDialog(
                        MainActivity.this, "Discovering Services...",
                        "Please wait.");
                mApplication.discoverServices(MainActivity.this);
            }
			@Override
			public void onFailure(final Throwable t) {
                Log.e("MainActivity", t.getMessage());

                AsyncController.getInstance().postAsyncTask(new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (mDialogSignIn.isShowing()) {
                                    mDialogSignIn.dismiss();
                                }
                                Toast.makeText(
                                        MainActivity.this,
                                        "Authentication failed",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });

                        return null;
                    }
                });
			}
		});
	}

	@Override
	public void onServiceSelected(String capability) {
		Intent intentToActivate = null;
		if (capability.equals(Constants.MYFILES_CAPABILITY)) {
			intentToActivate = new Intent(this, FileListActivity.class);
		}
		if (capability.equals(Constants.CALENDAR_CAPABILITY)) {
			intentToActivate = new Intent(this, CalendarEventListActivity.class);
		}
        if (capability.equals(Constants.MAIL_CAPABILITY)) {
            intentToActivate = new Intent(this, MailItemListActivity.class);
        }

		startActivity(intentToActivate);
	}

	@Override
	public void onServicesDiscoveredEvent(Event event) {
		if (event.servicesAreDiscovered()) {
            Log.i("MainActivity", "Services discovered");
            SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
            final String displayName = sharedPref.getString("DisplayName", "");
            final MenuItem signInMenuItem = mMenu.findItem(R.id.menu_signin);

            AsyncController.getInstance().postAsyncTask(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // User was signed in so activate the buttons.
                            //mButtonsFragment.setButtonsEnabled(true);
                            signInMenuItem.setIcon(R.drawable.user_default_signedin);
                            signInMenuItem.setTitle(displayName);

                            if (mDialogDiscoverServices.isShowing()) {
                                mDialogDiscoverServices.dismiss();
                            }
                            Toast.makeText(
                                    MainActivity.this,
                                    "Services discovered",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });

                    return null;
                }
            });
		} else{
            Log.e("MainActivity", "Failed to discover services");
            AsyncController.getInstance().postAsyncTask(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mDialogDiscoverServices.isShowing()) {
                                mDialogDiscoverServices.dismiss();
                            }
                            Toast.makeText(
                                    MainActivity.this,
                                    "Failed to discover services",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });

                    return null;
                }
            });
        }
	}
}
// *********************************************************
//
// O365-Android-Start, https://github.com/OfficeDev/O365-Android-Start
//
// Copyright (c) Microsoft Corporation
// All rights reserved.
//
// MIT License:
// Permission is hereby granted, free of charge, to any person obtaining
// a copy of this software and associated documentation files (the
// "Software"), to deal in the Software without restriction, including
// without limitation the rights to use, copy, modify, merge, publish,
// distribute, sublicense, and/or sell copies of the Software, and to
// permit persons to whom the Software is furnished to do so, subject to
// the following conditions:
//
// The above copyright notice and this permission notice shall be
// included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
// EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
// NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
// LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
// OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
// WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
//
// *********************************************************
