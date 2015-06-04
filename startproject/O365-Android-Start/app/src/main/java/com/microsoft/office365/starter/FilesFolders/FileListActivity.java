/*
 *  Copyright (c) Microsoft. All rights reserved. Licensed under the MIT license. See full license at the bottom of this file.
 */

package com.microsoft.office365.starter.FilesFolders;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.io.File;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.microsoft.office365.starter.O365APIsStart_Application;
import com.microsoft.office365.starter.R;
import com.microsoft.office365.starter.helpers.File_UI_State;
import com.microsoft.office365.starter.helpers.ProgressDialogHelper;
import com.microsoft.office365.starter.interfaces.BaseDialogListener;
import com.microsoft.office365.starter.interfaces.OnOperationCompleteListener;
import com.microsoft.office365.starter.helpers.DeleteDialogFragment;

/**
 * An activity representing a list of Files. This activity has different
 * presentations for handset and tablet-size devices. On handsets, the activity
 * presents a list of items, which when touched, lead to a
 * {@link com.microsoft.office365.starter.FilesFolders.FileDetailActivity} representing item details. On tablets, the
 * activity presents the list of items and item details side-by-side using two
 * vertical panes.
 * <p>
 * The activity makes heavy use of fragments. The list of items is a
 * {@link FileListFragment} and the item details (if present) is a
 * {@link com.microsoft.office365.starter.FilesFolders.FileDetailFragment}.
 * <p>
 * This activity also implements the required {@link FileListFragment.Callbacks}
 * interface to listen for item selections.
 */
public class FileListActivity extends Activity implements
        FileListFragment.Callbacks, FileUpdateFragment.Callbacks,
		BaseDialogListener, OnOperationCompleteListener {

	private Menu mMenu = null;
	private File_UI_State mUIState;
	private static final String UPDATE_FRAGMENT_STACK_STATE = "updateFragment";
	private O365APIsStart_Application mApplication;
	private int mSelectedFileItem = ListView.INVALID_POSITION;
	private DeleteDialogFragment mDeleteDialog;
    private boolean pendingFileDownload = false;
    private static final int PICKFILE_RESULT_CODE = 1;
	private ProgressDialog mDialog;
	/**
	 * Whether or not the activity is in two-pane mode, i.e. running on a tablet
	 * device.
	 */
	private boolean mTwoPane;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_file_list);
		mApplication = (O365APIsStart_Application) getApplication();
		FileListFragment listFragment = (FileListFragment) getFragmentManager()
				.findFragmentById(R.id.file_list);
		// list items are given the 'activated' state when touched for both
		// phone and tablet layouts
		listFragment.setActivateOnItemClick(true);

		if (findViewById(R.id.file_detail_container) != null) {
			// The detail container view will be present only in the
			// large-screen layouts (res/values-large and
			// res/values-sw600dp). If this view is present, then the
			// activity should be in two-pane mode.
			mTwoPane = true;

			// Load the overview text into the WebView
			WebView introView = (WebView) findViewById(R.id.fileStarterTextWebView);
			introView.setBackgroundColor(getResources().getColor(
					R.color.ApplicationPageBackgroundThemeBrush));
			String introHTML = getResources().getString(
					R.string.files_view_intro);
			introView.loadData(introHTML, "text/html", "UTF-8");
			introView.setVisibility(View.VISIBLE);

		}
		initializeUIState();

		if (savedInstanceState != null) {
			listFragment.setListAdapter(mApplication.getFileAdapterList());
			mSelectedFileItem = savedInstanceState.getInt("listPosition");
			mUIState.setListSelectedMode(savedInstanceState
					.getBoolean("isListItemSelected"));
			mUIState.setFileDisplayMode(savedInstanceState
					.getBoolean("isFileContentsDisplayed"));
			mUIState.setEditMode(savedInstanceState.getBoolean("isEditing"));
			listFragment.setSelection(mSelectedFileItem);
		} else {
			mApplication.setFileListViewState(new O365FileListModel(
					mApplication));
			// Initialize the list adapter in fragment

			ArrayList<O365FileModel> starterList = new ArrayList<O365FileModel>();
			ArrayAdapter<O365FileModel> listAdapter = new ArrayAdapter<O365FileModel>(
					this, android.R.layout.simple_list_item_activated_1, starterList);
			listFragment.setListAdapter(listAdapter);
			mApplication.setFileAdapterList(listAdapter);
			// Retrieve the files from the server
			actionGetFiles(null);

		}

	}

	private void initializeUIState() {
		mUIState = new File_UI_State();
		if (mTwoPane) {
			mUIState.btnCreate = (Button) findViewById(R.id.button_filecreate);
			mUIState.btnDelete = (Button) findViewById(R.id.button_filedelete);
			mUIState.btnGet = (Button) findViewById(R.id.button_fileget);
			mUIState.btnRead = (Button) findViewById(R.id.button_fileread);
			mUIState.btnUpdate = (Button) findViewById(R.id.button_fileupdate);
		}
		if (mMenu != null) {
			mUIState.itemCreate = mMenu.findItem(R.id.action_file_create);
			mUIState.itemDelete = mMenu.findItem(R.id.action_file_delete);
			mUIState.itemGet = mMenu.findItem(R.id.action_file_get);
			mUIState.itemRead = mMenu.findItem(R.id.action_file_read);
			mUIState.itemUpdate = mMenu.findItem(R.id.action_file_update);
		}
		mUIState.setEditMode(false);
		mUIState.setFileDisplayMode(false);
		mUIState.setListSelectedMode(false);
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {

		savedInstanceState.putInt("listPosition", mSelectedFileItem);
		savedInstanceState.putBoolean("isEditing", mUIState.isEditing);
		savedInstanceState.putBoolean("isListItemSelected",
				mUIState.isListItemSelected);
		savedInstanceState.putBoolean("isFileContentsDisplayed",
				mUIState.isFileContentsDisplayed);

		super.onSaveInstanceState(savedInstanceState);
	}

	/**
	 * Callback method from {@link FileListFragment.Callbacks} indicating that
	 * the item with the given ID was selected.
	 */
	@Override
	public void onItemSelected(int id) {
		mSelectedFileItem = id;
		mUIState.setListSelectedMode(true);
	}

	public void actionGetFiles(View view) {
		initializeUIState();
		mDialog = ProgressDialogHelper.showProgressDialog(
				FileListActivity.this,
				"Getting folders and files from server...", "Please wait.");
		mApplication.getFileListViewState().setEventOperationCompleteListener(
				FileListActivity.this);

		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
		mApplication.getFileListViewState().getFilesAndFoldersFromService(
				FileListActivity.this, mApplication.getFileClient());
	}

    public void actionUploadFile(View view)
    {
        try
        {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("file/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, PICKFILE_RESULT_CODE);
        }
        catch (ActivityNotFoundException anfe)
        {
            //Inform user via toast about the results of the operation
            Toast.makeText(FileListActivity.this, "No activity matches the intent",
                    Toast.LENGTH_LONG).show();

            Log.e(
                   "Activity was not found on upload file picker"
                   ,"No activity found for picking file "
                           + anfe.getMessage());
        }
    }

    //Callback from the FilePicker intent that returns the object that the user picked
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        try
        {
            switch (requestCode)
            {

                case PICKFILE_RESULT_CODE:
                    if (resultCode == RESULT_OK)
                    {

                        //Get the folder and file name to the file picked
                        //by the user
                        String folderPath = data.getData().getPath();
                        String fileName = data.getData().getLastPathSegment();
                        int lastPos = folderPath.length() - fileName.length();
                        String folder = folderPath.substring(0, lastPos);

                        //Create a new file object corresponding to the
                        //physical file picked by user
                        File fileToUpload = new File(folder,fileName);

                        //Read the contents of the file into a byte array to be
                        //added as content to an O365 file Item object for upload
                        FileInputStream fileIn = new FileInputStream(fileToUpload);

                        int fileLength = (int)fileToUpload.length();
                        final byte[] bBuffer = new byte[fileLength];
                        fileIn.read(bBuffer,0,fileLength);
                        fileIn.close();

                        //Create the O365 item, add the content, and upload to
                        //O365 OneDrive endpoint
                        mDialog = ProgressDialogHelper.showProgressDialog(this,
                                "Uploading the file on server...", "Please wait.");
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

                        mApplication.getFileListViewState().setEventOperationCompleteListener(
                                FileListActivity.this);
                        mApplication.getFileListViewState().postNewFileToServer(mApplication,
                                FileListActivity.this
                                ,fileName
                                ,bBuffer
                                ,mApplication.getFileClient());
                    }
                    break;
            }
        }
        catch (Exception ex)
        {
            Toast.makeText(
                    FileListActivity.this
                    ,"Exception after picking a file: "  + ex.getMessage()
                    ,Toast.LENGTH_LONG).show();
        }
    }

    public void actionDownloadAction(View view)
    {
        getFileFromServer(true);
    }
    public void actionReadAction(View view)
    {
        getFileFromServer(false);

    }

    private void getFileFromServer(boolean downloadFlag)
    {

		if (mSelectedFileItem == ListView.INVALID_POSITION)
			return;

		mApplication.getFileListViewState().setEventOperationCompleteListener(
				FileListActivity.this);

        String actionDescription = "Getting file contents from server...";
        if (downloadFlag)
            actionDescription = "Downloading file from server...";

		mDialog = ProgressDialogHelper.showProgressDialog(
				FileListActivity.this, actionDescription,
				"Please wait.");
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

		ArrayAdapter<O365FileModel> adapter = mApplication.getFileAdapterList();
		O365FileModel fileItem = adapter.getItem(mSelectedFileItem);

		// When the getFileContentsFromServer completes, it will call the
		// onOperationComplete method
		// which will then call displayFileContents to launch the UI for the
		// file.
		O365FileModel fileModel = mApplication.getFileListViewState()
				.getFileContentsFromServer(FileListActivity.this, fileItem);

        mApplication.setDisplayedFile(fileModel);
        pendingFileDownload = downloadFlag;


	}

    private void saveFileInExternalStorage(final O365FileModel fileModel)
    {

        String externalStorageState = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(externalStorageState))
        {
            File fileDocDir = new File(Environment
                    .getExternalStorageDirectory().getPath(),"OneDriveDocs");

            if (!fileDocDir.exists()&&(!fileDocDir.mkdirs()))
                    Log.e("IO error", "Directory not created");


            String downloadFileNameString = fileModel.getName();
            final File downloadedFile = new File(fileDocDir.getPath(), downloadFileNameString);

            if (downloadedFile.exists())
            {
                // Use the Builder class for convenient dialog construction
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(downloadedFile.getName() + " exists. Do you want to overwrite it?")
                        .setPositiveButton(
                                R.string.Ok, new DialogInterface.OnClickListener()
                                {
                                    public void onClick(DialogInterface dialog, int id)
                                    {
                                        downloadedFile.delete();
                                        WriteFileOut(downloadedFile, fileModel);
                                    }
                                }
                        )
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        Toast.makeText(
                                                FileListActivity.this
                                                ,"The file download was canceled"
                                                ,Toast.LENGTH_LONG).show();
                                    }
                                });
                // Create the AlertDialog object and return it
                builder.create();
                builder.show();
            }
            else
                WriteFileOut(downloadedFile, fileModel);
        }
        else
        {
            Toast.makeText(
                    FileListActivity.this
                    ,"External storage is unavailable. The storage state is "
                            + externalStorageState
                            + ". Cannot save downloaded file",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void WriteFileOut(File downloadedFile, O365FileModel fileModel)
    {
        try
        {

            FileOutputStream outputStream;
            outputStream = new FileOutputStream(downloadedFile);
            outputStream.write(fileModel
                            .getContents()
                            .getBytes());
            outputStream.flush();
            outputStream.close();
            Toast.makeText(
                    FileListActivity.this
                    ,fileModel.getName()
                            + " is saved to "
                            + downloadedFile.getPath()
                    ,Toast.LENGTH_LONG).show();

        }
        catch (IOException io)
        {
            Log.e(
                    "File IO ERROR"
                    , "The file was not saved "
                            + io.getMessage());
        }
    }
	// When read button is pressed and file contents are retrieved, this will
	// display them on the UI
	public void displayFileContents() {
		if (mTwoPane)
        {
			FragmentManager fm = getFragmentManager();
			FileDetailFragment fragment = (FileDetailFragment) fm
					.findFragmentById(R.id.file_detail_container);

			// If fragment is already displayed, reuse it, otherwise create a
			// new one
			if (fragment == null)
            {
				fragment = new FileDetailFragment();
				getFragmentManager().beginTransaction()
						.replace(R.id.file_detail_container, fragment)
						.commit();
			}
            else
				fragment.refresh(mApplication.getDisplayedFile());
		}
        else
        {
			// In single-pane mode, simply start the detail activity
			// for the selected item ID.
			android.content.Intent detailIntent = new android.content.Intent(
					FileListActivity.this, FileDetailActivity.class);

			detailIntent.putExtra(FileDetailFragment.ARG_ITEM_ID,
					mSelectedFileItem);
			startActivity(detailIntent);
		}
	}

	public void actionCreateFile(View view) {

		DateFormat dateFormat = DateFormat.getDateTimeInstance(
				DateFormat.MEDIUM, DateFormat.MEDIUM);
		Date date = new Date();
		final String fileContents = "Created at " + dateFormat.format(date);

		mDialog = ProgressDialogHelper.showProgressDialog(this,
				"Adding the new file on server...", "Please wait.");
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

		mApplication.getFileListViewState().setEventOperationCompleteListener(
				FileListActivity.this);
		mApplication.getFileListViewState().postNewFileToServer(mApplication,
				FileListActivity.this, "demo.txt", fileContents,
				mApplication.getFileClient());
	}

	public void actionDeleteFile(View view) {
		if (mSelectedFileItem == ListView.INVALID_POSITION)
			return;
		Bundle arguments = new Bundle();

		O365FileModel itemToRemove = mApplication.getFileAdapterList().getItem(
				mSelectedFileItem);

		arguments.putString("MessageString", "Delete " + itemToRemove.getName()
				+ "?");
		mDeleteDialog = new DeleteDialogFragment();
		mDeleteDialog.setArguments(arguments);
		mDeleteDialog.show(getFragmentManager(), "Delete this file?");
	}

	public void actionUpdateFile(View view) {
		mUIState.setEditMode(true);
		// This method is not called in small device layout mode
		// but handled by the FileDetailActivity instead
		if (mTwoPane == true)

		{
			FileUpdateFragment updateFragment = new FileUpdateFragment();
			FragmentManager fragmentManager = getFragmentManager();
			android.app.FragmentTransaction ft = fragmentManager
					.beginTransaction();
			ft.replace(R.id.file_detail_container, updateFragment,
					UPDATE_FRAGMENT_STACK_STATE);
			ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
			ft.addToBackStack("updateFragment");
			ft.commit();
		}
	}

	// Callback called by delete dialog fragment when user clicks the
	// Done button
	@Override
	public void onDialogPositiveClick(Fragment dialog) {
		mDialog = ProgressDialogHelper.showProgressDialog(this,
				"Deleting selected file from server...", "Please wait.");
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

		mApplication.getFileListViewState().setEventOperationCompleteListener(
				this);
		mApplication.getFileListViewState().postDeleteSelectedFileFromServer(
                FileListActivity.this, mApplication.getFileClient(),
                mSelectedFileItem
        );
	}

	@Override
	public void onDialogNegativeClick(Fragment dialog) {
		// no action needed
	}

	@Override
	public void onOperationComplete(final OperationResult opResult) {
		this.runOnUiThread(
                new Runnable()
                {

                    @Override
                    public void run()
                    {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);

                        if (mDialog.isShowing())
                            mDialog.dismiss();

                        Toast.makeText(
                                FileListActivity.this,
                                opResult.getOperationResult(), Toast.LENGTH_LONG
                        ).show();
                        if (opResult.getId().equals("FileContentsRetrieved"))
                        {
                            // In the case of file contents being retrieved, we need to
                            // launch UI to display the file contents
                            if (pendingFileDownload)
                            {
                                saveFileInExternalStorage(mApplication.getDisplayedFile());
                                pendingFileDownload = false;
                            }

                            displayFileContents();
                            mUIState.setFileDisplayMode(true);
                        }
                        if (opResult.getId().equals("FileContentsUpdate"))
                        {
                            // refresh the display to reflect new file contents
                            FragmentManager fm = getFragmentManager();
                            FileDetailFragment fragment = (FileDetailFragment) fm
                                    .findFragmentById(R.id.file_detail_container);
                            if (fragment != null)
                                fragment.refresh(mApplication.getDisplayedFile());
                        }
                        if (opResult.getId().equals("FileDeleted"))
                        {
                            // File displayed may be the one just deleted.
                            // so erase the displayed contents just to be safe.
                            mApplication.setDisplayedFile(null);
                            mSelectedFileItem = ListView.INVALID_POSITION;
                            mUIState.setFileDisplayMode(false);
                            mUIState.setListSelectedMode(false);
                            FragmentManager fm = getFragmentManager();
                            FileDetailFragment fragment = (FileDetailFragment) fm
                                    .findFragmentById(R.id.file_detail_container);
                            if (fragment != null)
                                fragment.refresh(null);

                        }
                    }
                }
        );
	}

	// Update Fragment passes the updated content that needs to be posted to
	// server
	@Override
	public void onContentsUpdated(String updatedContents) {
		// Start progress dialog and post updated contents to server
		mDialog = ProgressDialogHelper.showProgressDialog(this,
				"Updating file contents on server...", "Please wait.");
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

		mApplication.getFileListViewState().setEventOperationCompleteListener(
				FileListActivity.this);
		mApplication.getFileListViewState().postUpdatedFileContents(
				mApplication, FileListActivity.this,
				mApplication.getFileClient(), updatedContents);
		// Remove update fragment and restore detail view of file contents
		this.getFragmentManager()
                .popBackStack();
		mUIState.setEditMode(false);
	}

	// Update fragment was cancelled, so remove it from UI
	@Override
	public void onFileUpdateCancelled() {
		mUIState.setEditMode(false);
		FragmentManager fm = getFragmentManager();
		fm.popBackStack(
                UPDATE_FRAGMENT_STACK_STATE,
                FragmentManager.POP_BACK_STACK_INCLUSIVE
        );

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.file_menu, menu);
		mMenu = menu;
		mUIState.itemCreate = mMenu.findItem(R.id.action_file_create);
		mUIState.itemDelete = mMenu.findItem(R.id.action_file_delete);
		mUIState.itemGet = mMenu.findItem(R.id.action_file_get);
		mUIState.itemRead = mMenu.findItem(R.id.action_file_read);
		mUIState.itemUpdate = mMenu.findItem(R.id.action_file_update);
        mUIState.itemUpload = mMenu.findItem(R.id.action_pick_file);
        mUIState.itemDownload = mMenu.findItem(R.id.action_download_file);

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle presses on the action bar items
		switch (item.getItemId()) {
		case R.id.action_file_get:
			actionGetFiles(null);
			return true;
		case R.id.action_file_read:
			actionReadAction(null);
			return true;
		case R.id.action_file_create:
			actionCreateFile(null);
			return true;
		case R.id.action_file_delete:
			actionDeleteFile(null);
			return true;
		case R.id.action_file_update:
			if (mTwoPane)
				actionUpdateFile(null);
			return true;
        case R.id.action_pick_file:
            actionUploadFile(null);
            return true;
        case R.id.action_download_file:
            actionDownloadAction(null);
            return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onBackPressed()
    {
		// reset edit mode in case the user backed out of the edit fragment
		mUIState.setEditMode(false);
		super.onBackPressed();
	}
    private class filename
    {

        String filename_Without_Ext = "";
        String ext = "";

        filename(String file)
        {
            int dotposition= file.lastIndexOf(".");
            filename_Without_Ext = file.substring(0,dotposition);
            ext = file.substring(dotposition + 1, file.length());
        }

        String getFilename_Without_Ext()
        {
            return filename_Without_Ext;
        }

        String getExt()
        {
            return ext;
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
