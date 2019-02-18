package com.example.admindeveloper.seismometer;


import android.annotation.SuppressLint;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.widget.Toast;

import com.example.admindeveloper.seismometer.DataAcquisition.DataService;
import com.example.admindeveloper.seismometer.RealTimeServices.RealTimeController;
import com.example.admindeveloper.seismometer.UploadServices.ZipManager;

import net.gotev.uploadservice.MultipartUploadRequest;
import net.gotev.uploadservice.ServerResponse;
import net.gotev.uploadservice.UploadInfo;
import net.gotev.uploadservice.UploadNotificationConfig;
import net.gotev.uploadservice.UploadStatusDelegate;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.UUID;

public class Background extends Service {

    RecordSaveData recordSaveData1;
    RealTimeController realTimeController;
    Handler handler;
    ZipManager zipManager;
    String UPLOAD_URL;

    ArrayList<String> csvnames;
    FileObserver fileObservercsv;
    FileObserver fileObserverzip;

    boolean compressionflag = false;
    boolean append = false;
    int iappendctr = 0;
    final int limitappend = 1;
    int sec;

    long StartTime;
    String time;

    String fileName;

    String ipaddress;

    String longitude;
    String latitutde;
    String compass;

    private LocationManager locationManager;
    private LocationListener locationListener;

    Runnable runnable;
    long resettime=0;

    private final BroadcastReceiver mBroadcastReceiver=new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(DataService.DATA)){
                time = ""+(SystemClock.uptimeMillis()-resettime);

                compass=intent.getStringExtra(DataService.GET_COMPASS);
                realTimeController.updateXYZ(intent.getFloatExtra(DataService.GET_X,0),
                                                intent.getFloatExtra(DataService.GET_Y,0),
                                                intent.getFloatExtra(DataService.GET_Z,0));
                recordSaveData1.recordData(realTimeController.getX(), realTimeController.getY(), realTimeController.getZ(), time);

            }
        }
    };

    @SuppressLint("MissingPermission")
    @Override
    public void onCreate() {
        locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                longitude = String.valueOf(location.getLongitude());
                latitutde = String.valueOf(location.getLatitude());
            }

            @Override
            public void onStatusChanged(String s, int i, Bundle bundle) {

            }

            @Override
            public void onProviderEnabled(String s) {

            }

            @Override
            public void onProviderDisabled(String s) {
                Intent i = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        };
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 0, locationListener);

        IntentFilter intentFilter=new IntentFilter(DataService.DATA);
        registerReceiver(mBroadcastReceiver,intentFilter);

    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, final int startId) {
        //region ---------Initialization ------------------
        StartTime = SystemClock.uptimeMillis();
        ipaddress = intent.getStringExtra("ipaddress");

        Toast.makeText(getApplication(), ipaddress, Toast.LENGTH_SHORT).show();
        Toast.makeText(getApplication(), intent.getStringExtra("location"), Toast.LENGTH_SHORT).show();
        Toast.makeText(getApplication(), intent.getStringExtra("device"), Toast.LENGTH_SHORT).show();
        csvnames = new ArrayList<>();
        zipManager = new ZipManager();
        recordSaveData1 = new RecordSaveData();
        realTimeController = new RealTimeController();
        handler = new Handler();
        //endregion
        Toast.makeText(getApplication(), "Services Enabled", Toast.LENGTH_SHORT).show();
        //region ------------------- Set up for Delay / Start Up --------------------
        Calendar settime1 = Calendar.getInstance();
        sec = (60 - settime1.get(Calendar.SECOND)) * 1000;
        resettime = SystemClock.uptimeMillis();
        //endregion

        //region ---------------------(HANDLER) Special Delay Call (Infinite Loop in an definite delay)--------------------

                runnable = new Runnable() {
                    @Override
                    public void run() {
                        // ------------- Set Up -----------
                        String status;
                        resettime = SystemClock.uptimeMillis();
                       // Toast.makeText(getApplicationContext(), "Saving in Progress", Toast.LENGTH_SHORT).show();
                        if(iappendctr == 0 && !append) {
                            compressionflag = false;
                            Calendar setnamedate = Calendar.getInstance();
                            fileName = setnamedate.get(Calendar.YEAR) + "-" + (setnamedate.get(Calendar.MONTH)+1)  + "-" + setnamedate.get(Calendar.DATE)  + "-" + setnamedate.get(Calendar.HOUR_OF_DAY)  + "-" + setnamedate.get(Calendar.MINUTE)  + "-" + setnamedate.get(Calendar.SECOND)  + ".csv";
                        }
                        // -------------- Save / Clear -------------
                        if(iappendctr+1 >= limitappend) {
                            compressionflag = true;
                        }
                            status = recordSaveData1.saveEarthquakeData("0", fileName, longitude, latitutde, compass, append , iappendctr, limitappend);      // saving Data to a specific Location (Samples)


                        Toast.makeText(getApplicationContext(), status, Toast.LENGTH_SHORT).show();
                        switch (status){
                            case "Success":{
                                recordSaveData1.clearData();          // deleting recorded data
                                append = true;
                                iappendctr++;
                                if(iappendctr >= limitappend) {
                                    csvnames.add(fileName);
                                    iappendctr = 0;
                                    append = false;
                                }

                                //------------------ Initialize Delay for the next Call -----------------
                                Calendar settime = Calendar.getInstance();
                                 sec = (60 - settime.get(Calendar.SECOND)) * 1000; // seconds delay for minute

                                // ----------------- Recursive Call --------------------------
                                handler.postDelayed(this, sec);
                                break;
                            }
                            case "Error":{
                                handler.postDelayed(this, 0);
                                break;
                            }
                            default:{
                                break;
                            }
                        }
                    }
                };
                handler.postDelayed(runnable, sec); // calling handler for infinite loop
        //endregion

        //region --------- FileObserver for Compression -------
       final String csvpath = android.os.Environment.getExternalStorageDirectory().toString() + "/Samples/";
       fileObservercsv = new FileObserver(csvpath,FileObserver.ALL_EVENTS) {
           @Override
           public void onEvent(int event, final String file) {
               if (event == FileObserver.CLOSE_WRITE && compressionflag) {
                  // Log.d("MediaListenerService", "File created [" + csvpath + file + "]");
                   new Handler(Looper.getMainLooper()).post(new Runnable() {
                       @Override
                       public void run() {
                          // Toast.makeText(getBaseContext(), file + " was saved!", Toast.LENGTH_SHORT).show();
                           zipManager.compressGzipFile("Samples/" + file,  file + ".gz");  // Compressing Data

                       }
                   });
               }
           }
       };
       fileObservercsv.startWatching();
        //endregion

        //region  -------- FileObserver for Sending Data to Database -------------
        final String zippath = android.os.Environment.getExternalStorageDirectory().toString() + "/Zip/";
        fileObserverzip = new FileObserver(zippath,FileObserver.ALL_EVENTS) {
            @Override
            public void onEvent(int event, final String file) {
                if (event == FileObserver.CREATE) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                           // Toast.makeText(getBaseContext(), file + " was compressed!", Toast.LENGTH_SHORT).show();
                            for(int ictr=0 ; ictr<csvnames.size() ; ictr++) {
                               uploadMultipart("/storage/emulated/0/Zip/" + csvnames.get(ictr) + ".gz", csvnames.get(ictr),ictr);
                             }

                        }
                    });
                }
            }
        };
        fileObserverzip.startWatching();
        //endregion


        return START_STICKY;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mBroadcastReceiver);
        Toast.makeText(this,"Service Stopped",Toast.LENGTH_SHORT).show();
        handler.removeCallbacks(runnable);
        if(locationManager != null){
            locationManager.removeUpdates(locationListener);
        }

    }

    public void uploadMultipart(String path, final String name, final int index) {
        //getting name for the image
        UPLOAD_URL = "http://"+ipaddress+"/data/api/uploaddata.php";
        //String name=(currentTime.getYear()+1900)+"-"+(currentTime.getMonth()+1)+"-"+currentTime.getDate()+"-"+currentTime.getHours()+currentTime.getMinutes()+"-"+currentTime.getSeconds()+".csv";
        String[] separated = name.split("[-|.]");
        String location = "Lapulapu";
        String year = separated[0];
        String month = separated[1];
        String day = separated[2];
        String hour = separated[3];
        String minute = separated[4];

        //getting the actual path of the image
        //  String path = FilePath.getPath(getActivity(), filePath);

        if (path == null) {

            Toast.makeText(this, "NULL PATH", Toast.LENGTH_LONG).show();
        } else {
            //Uploading code

            try {
                final String uploadId = UUID.randomUUID().toString();


                //Creating a multi part request
                new MultipartUploadRequest(getApplicationContext(), uploadId, UPLOAD_URL)
                        .addFileToUpload(path, "gz") //Adding file
                        .addParameter("name", name) //Adding text parameter to the request
                        .addParameter("location", location)
                        .addParameter("month", month)
                        .addParameter("day", day)
                        .addParameter("year", year)
                        .addParameter("hour", hour)
                        .addParameter("minute", minute)
                        .setNotificationConfig(new UploadNotificationConfig())
                        .setMaxRetries(2)
                        .setDelegate(new UploadStatusDelegate() {
                            @Override
                            public void onProgress(Context context, UploadInfo uploadInfo) {
                                Toast.makeText(getApplicationContext(), "Uploading to Server", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onError(Context context, UploadInfo uploadInfo, ServerResponse serverResponse, Exception exception) {
                                Toast.makeText(getApplicationContext(), "Server Connection Failed", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onCompleted(Context context, UploadInfo uploadInfo, ServerResponse serverResponse) {
                                Toast.makeText(getApplicationContext(), serverResponse.getBodyAsString(), Toast.LENGTH_SHORT).show();
                                if(serverResponse.getBodyAsString().equals("Successfully Uploaded yehey")) {
                                    File file1 = new File("/storage/emulated/0/Samples/", name);
                                    boolean deleted1 = file1.delete();
                                    File file2 = new File("/storage/emulated/0/Zip/", name + ".gz");
                                    boolean deleted2 = file2.delete();
                                    csvnames.remove(index);
                                }

                            }

                            @Override
                            public void onCancelled(Context context, UploadInfo uploadInfo) {
                                Toast.makeText(getApplicationContext(), "Uploading Cancelled", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .startUpload(); //Starting the upload

            } catch (Exception exc) {
                Toast.makeText(getApplicationContext(), exc.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
}

