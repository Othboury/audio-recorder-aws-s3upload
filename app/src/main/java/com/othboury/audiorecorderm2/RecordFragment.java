package com.othboury.audiorecorderm2;


import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.TextView;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferNetworkLossHandler;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;

import java.io.File;
/**
 * A simple {@link Fragment} subclass.
 */
public class RecordFragment extends Fragment implements View.OnClickListener {

    private NavController navController;

    private ImageButton listBtn;
    private ImageButton recordBtn;
    private TextView filenameText;

    private boolean isRecording = false;

    private String recordPermission = Manifest.permission.RECORD_AUDIO;
    private int PERMISSION_CODE = 21;

    private MediaRecorder mediaRecorder;
    private String recordFile;

    private Chronometer timer;

    String bucketName = "mosquitoes-classification-model-m2";
    String keyName = "records/";
    AmazonS3 s3;
    String recordPath = "";
    TransferUtility transferUtility;

    public RecordFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        credentialsProvider();
        setTransferUtility();

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_record, container, false);

    }


    public void credentialsProvider(){
        // Initialiser le fournisseur d'informations d'identification Amazon Cognito
        CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
                getContext(),
                "eu-west-3:a72e4f50-257e-4f43-8fe7-dfff9305037b", // ID du groupe d'identités
                Regions.EU_WEST_3 // Région
        );
        setAmazonS3Client(credentialsProvider);
    }

    public void setAmazonS3Client(CognitoCachingCredentialsProvider credentialsProvider ){
        s3 = new AmazonS3Client(credentialsProvider);

        s3.setRegion(Region.getRegion(Regions.EU_WEST_3));
    }

    public void setTransferUtility(){
        transferUtility = new TransferUtility(s3, getContext());
    }

    public void UploadtoS3(File fileToUpload, String filename){
        TransferNetworkLossHandler.getInstance(getContext());
        TransferObserver transferObserver = transferUtility.upload(
                bucketName,
                keyName = keyName + filename,
                fileToUpload
        );
        transferObserverListener(transferObserver);
    }

    public void transferObserverListener(TransferObserver transferObserver){
        transferObserver.setTransferListener(new TransferListener() {
            @Override
            public void onStateChanged(int id, TransferState state) {
                Log.e("stateChange", state+"");
                // If upload error, failed or network disconnect
                if(state == TransferState.FAILED || state == TransferState.WAITING_FOR_NETWORK){
                    Log.e("stateChange", state+"");
                }
            }

            @Override
            public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                    int percentage = (int) (bytesCurrent/bytesTotal * 100);
                    Log.e("percentage", percentage+"");
            }

            @Override
            public void onError(int id, Exception ex) {

            }
        });
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //Intitialize Variables
        navController = Navigation.findNavController(view);
        listBtn = view.findViewById(R.id.record_list_btn);
        recordBtn = view.findViewById(R.id.record_btn);
        timer = view.findViewById(R.id.record_timer);
        filenameText = view.findViewById(R.id.record_filename);

        /* Setting up on click listener
           - Class must implement 'View.OnClickListener' and override 'onClick' method
        */

        listBtn.setOnClickListener(this);
        recordBtn.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        /*  Check, which button is pressed and do the task accordingly */
        switch (v.getId()) {
            case R.id.record_list_btn:
                /*
                Navigation Controller
                Part of Android Jetpack, used for navigation between both fragments
                 */
                if(isRecording){
                    AlertDialog.Builder alertDialog = new AlertDialog.Builder(getContext());
                    alertDialog.setPositiveButton("OKAY", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            navController.navigate(R.id.action_recordFragment_to_audioListFragment);
                            isRecording = false;
                        }
                    });
                    alertDialog.setNegativeButton("CANCEL", null);
                    alertDialog.setTitle("Audio Still recording");
                    alertDialog.setMessage("Are you sure, you want to stop the recording?");
                    alertDialog.create().show();
                } else {
                    navController.navigate(R.id.action_recordFragment_to_audioListFragment);
                }
                break;

            case R.id.record_btn:
                if(isRecording) {
                    //Stop Recording
                    stopRecording();

                    // Change button image and set Recording state to false
                    recordBtn.setImageDrawable(getResources().getDrawable(R.drawable.
                            record_btn_stopped, null));
                    isRecording = false;
                } else {
                    //Check permission to record audio
                    if(checkPermissions()) {
                        //Start Recording
                        startRecording();

                        // Change button image and set Recording state to false
                        recordBtn.setImageDrawable(getResources().getDrawable(R.drawable.
                                record_btn_recording, null));
                        isRecording = true;
                    }
                }
                break;
        }
    }

    private void stopRecording() {
        //Stop Timer, very obvious
        timer.stop();

        //Change text on page to file saved
        filenameText.setText("Recording Stopped, File Saved : " + recordFile);

        //Upload to S3 Bucket
        String filename = recordFile;
        Log.e("filepath",recordPath + "/" + recordFile);
        File filepath = new File(recordPath + "/" + recordFile);

        UploadtoS3(filepath, filename);


        //Stop media recorder and set it to null for further use to record new audio
        mediaRecorder.stop();
        mediaRecorder.release();
        mediaRecorder = null;
    }

    private void startRecording() {
        //Start timer from 0
        timer.setBase(SystemClock.elapsedRealtime());
        timer.start();

        //Get app external directory path
        recordPath = getActivity().getExternalFilesDir("/").getAbsolutePath();

        //Get current date and time
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_hh_mm_ss", Locale.FRANCE);
        Date now = new Date();

        //initialize filename variable with date and time at the end to ensure the new file wont overwrite previous file
        recordFile = "Recording_" + formatter.format(now) + ".wav";

        filenameText.setText("Recording, File Name : " + recordFile);

        //Setup Media Recorder for recording
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setOutputFile(recordPath + "/" + recordFile);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //Start Recording
        mediaRecorder.start();
    }

    private boolean checkPermissions() {
        //Check permission
        if (ActivityCompat.checkSelfPermission(getContext(), recordPermission) == PackageManager.PERMISSION_GRANTED) {
            //Permission Granted
            return true;
        } else {
            //Permission not granted, ask for permission
            ActivityCompat.requestPermissions(getActivity(), new String[]{recordPermission}, PERMISSION_CODE);
            return false;
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if(isRecording){
            stopRecording();
        }
    }
}
